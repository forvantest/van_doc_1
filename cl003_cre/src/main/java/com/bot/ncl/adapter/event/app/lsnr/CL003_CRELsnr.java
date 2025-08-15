/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CL003_CRE;
import com.bot.ncl.dto.entities.ClbafbyCllbr999Bus;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyBus;
import com.bot.ncl.dto.entities.ClmrbyPbrnoBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileBsctlDetail;
import com.bot.ncl.util.fileVo.FileBsctlHeader;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CL003_CRELsnr")
@Scope("prototype")
public class CL003_CRELsnr extends BatchListenerCase<CL003_CRE> {

    // 參考原COBOL程式SYM/CL/BH/CRE/CL003
    // 代收軋帳後，產生整批入帳檔，提供櫃員直接入扣帳
    // 只挑 MSG1=1 且 DB-CLMR-AFCBV=1

    @Autowired private Parse parse;

    @Autowired private FormatUtil formatUtil;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private ClmrService clmrService;

    @Autowired private CltmrService cltmrService;

    @Autowired private ClbafService clbafService;

    @Autowired private CldtlService cldtlService;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String GE032 = "E032";
    private static final int PAGE_LIMIT = 2000;
    private static final String CHKTYPE_X = "X";
    private static final String BIG5 = "BIG5";

    private int tbsday; // 營業日(民國)
    private int lbsdy;
    private int inputBrno; // 分行別
    private int inputRday; // 日期

    private List<ClmrbyPbrnoBus> clmrList;

    private String bsctlFileName;

    private BigDecimal totalAmt;
    private int totalCnt;
    private BigDecimal totalKfee;
    private int crCnt;
    private BigDecimal crAmt;
    private int dbCnt;
    private BigDecimal dbAmt;
    private int actnoFlg;
    private int wkSeq;
    private int depositMachineCnt;

    private List<Map<String, String>> titaList;
    private List<Map<String, String>> c004RptFasList;
    private Map<String, String> c004RptFasMap = new HashMap<>();

    private String c004RptFasFileName;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CL003_CRE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CL003_CRELsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CL003_CRE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CL003_CRELsnr run()");
        // Step 1 交易初始化
        initParam(event);

        // Step 2 取得本營業日日期
        tbsday = event.getAggregateBuffer().getTxCom().getTbsdy(); // 取得營業日(民國)

        // Step 3 組整批入帳檔(FD-BSCTL)檔名
        bsctlFileName = settingBsctlFileName();

        // Step 4 檢核須為軋帳後才能產生檔案
        clmrChk(0);

        // Step 5 讀取收付累計檔
        cntdtl();

        // Step 6 取得總計資料
        writeHeader();

        // Step 7 產生G6613檔案
        createG6613TitaFile();
        // Step 8 產生C004_RPT_FAS檔案
        createC004_Rpt_FasTitaFile();

        // TODO: call FSAP-BATCH BT200 上傳檔案 c004TitaFilePath
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001",
                        "CL",
                        "C004_FAS",
                        c004RptFasFileName,
                        c004RptFasFileName,
                        "2",
                        "SRC");

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "result={}", result);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }

    private void initParam(CL003_CRE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initParam()");

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        inputBrno = parse.string2Integer(textMap.get("KINBR")); // 分行別
        inputRday = parse.string2Integer(textMap.get("RGDAY")); // 日期

        clmrList = new ArrayList<>();
        wkSeq = 0;
        crCnt = 0;
        crAmt = BigDecimal.ZERO;
        dbCnt = 0;
        dbAmt = BigDecimal.ZERO;
        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        titaList = new ArrayList<>();
        c004RptFasList = new ArrayList<>();
    }

    private String settingBsctlFileName() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "settingBsctlFileName()");
        // 011100** 組媒體檔的檔名                                                 01/05/08
        // 011200     MOVE WK-RDAY         TO  WK-DWL-DATE  .                      06/07/14
        // 011300     MOVE WK-BRNO         TO  WK-DWL-BRNO  WK-CLBRNO.             01/05/08
        // 011400     MOVE WK-RDAY(4:4)    TO  WK-DWL-MMDD  .                      06/07/14
        // 011500     CHANGE    ATTRIBUTE TITLE OF FD-BSCTL TO WK-BSCTL-UPL.       01/05/08
        // 011600     OPEN      OUTPUT     FD-BSCTL.                               01/05/08
        // 011700                                                                  01/05/08

        // ref
        // 007300 01  WK-BSCTL-UPL.                                                01/05/08
        // 007400   03 FILLER               PIC X(12)  VALUE "DATA/BS/DWL/".       01/05/08
        // 007500   03 WK-DWL-DATE          PIC X(07).                             01/05/08
        // 007600   03 FILLER               PIC X(01)  VALUE "/".                  01/05/08
        // 007700   03 WK-DWL-BRNO          PIC X(03).                             01/05/08
        // 007800   03 FILLER               PIC X(01)  VALUE "/".                  01/05/08
        // 007900   03 WK-DWL-MMDD          PIC X(04).                             01/05/08
        // 008000   03 FILLER               PIC X(09)  VALUE "891099/3.".          01/05/08
        // 008500                                                                  01/05/08
        String wkBsctlUpl = fileDir + "BS/DWL/";
        wkBsctlUpl += parse.decimal2String(inputRday, 7, 0) + "_";
        wkBsctlUpl += parse.decimal2String(inputBrno, 3, 0) + "_";
        wkBsctlUpl += parse.decimal2String(inputRday, 4, 0) + "891099_3";
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkBsctlUpl={}", wkBsctlUpl);
        textFileUtil.deleteFile(wkBsctlUpl);
        return wkBsctlUpl;
    }

    private void clmrChk(int clmrIndex) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmrChk()");
        List<ClmrbyPbrnoBus> clmrBusList =
                clmrService.findbyPbrno(inputBrno, clmrIndex, PAGE_LIMIT);
        if (!Objects.isNull(clmrBusList) && !clmrBusList.isEmpty()) {
            // 015200* 只要有一個代收類別沒關帳就不用產生                              01/05/08
            // 015300      IF      DB-CLMR-AFCBV   =    0                              01/05/08
            // 015350      AND     WK-RDAY         =    WK-TBSDY                       06/07/14
            // 015400        MOVE  0               TO   WK-AFCBV                       01/05/08
            // 015500        GO TO 1000-PBRNO-EXIT                                     01/05/08
            // 015600      ELSE                                                        01/05/08
            // 015700        MOVE  1               TO   WK-AFCBV.                      01/05/08
            // 015800                                                                  01/05/08
            for (ClmrbyPbrnoBus clmrBus : clmrBusList) {
                if (clmrBus.getAfcbv() == 0 && inputRday == tbsday) {
                    ApLogHelper.info(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "clmrBus.getCode()={}",
                            clmrBus.getCode());
                    ApLogHelper.info(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "clmrBus.getAfcbv()={}",
                            clmrBus.getAfcbv());
                    // TODO: 待調整錯誤代號及訊息
                    throw new LogicException(
                            GE032, "事業單位主檔分行別" + inputBrno + "未軋帳!!"); // 因例外處理未寫先押一個錯誤代號
                }
                clmrList.add(clmrBus);
            }
            if (clmrBusList.size() == PAGE_LIMIT) {
                clmrIndex++;
                clmrChk(clmrIndex);
            }
        }
    }

    private void cntdtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "countClbaf()");
        for (ClmrbyPbrnoBus clmr : clmrList) {
            // 015900      MOVE    DB-CLMR-CODE    TO   WK-TCODE.                      01/05/08
            // 016000      PERFORM 2000-CNTDTL-RTN THRU 2000-CNTDTL-EXIT.              01/05/08
            String wkTcode = clmr.getCode();
            // 016900* 整批入扣帳記號未設１不用記算                                    02/06/21
            // 017000     IF DB-CLMR-MSG1 NOT = 1 GO TO 2000-CNTDTL-EXIT.              02/06/21
            if (clmr.getMsg1() != 1) {
                continue;
            }
            // 017200* 從 CLBAF 挑出今日的總金額                                       01/05/08
            // 017300     PERFORM 2100-999SUM-RTN  THRU 2100-999SUM-EXIT.              01/05/08
            // 018400 2100-999SUM-RTN.                                                 01/05/08
            // 018500     SET  DB-CLBAF-IDX1   OF  DB-CLBAF-DDS TO BEGINNING.          01/05/08
            // 018600     MOVE 0               TO  WK-TASK-AMT       .                 01/05/08
            // 018700     MOVE 0               TO  WK-TASK-CNT       .                 01/05/08
            // 018750     MOVE 0               TO  WK-TASK-KFEE      .                 01/06/25
            totalAmt = BigDecimal.ZERO;
            totalCnt = 0;
            totalKfee = BigDecimal.ZERO;
            sumClbaf(wkTcode, 0);

            // 017500* 今天在 CLBAF 中找不到該代收類別任何交易金額                     01/05/08
            // 017600     IF WK-TASK-AMT = 0 GO TO 2000-CNTDTL-EXIT.                   01/05/08
            // 017700                                                                  01/05/08
            // 017800     PERFORM 2000-WRTDTL-RTN THRU 2000-WRTDTL-EXIT.               01/05/08
            if (totalAmt.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            // 020500 2000-WRTDTL-RTN.                                                 01/05/08
            // 020600     MOVE  SPACES  TO  WK-ACTNO, WK-DPACTNO, WK-CLSACTNO.         01/05/08
            // 020700                                                                  01/06/20
            // 020800* 把是入帳帳號或掛會計科目準備好                                  01/05/08
            // 020900     PERFORM 5000-ACTNO-RTN       THRU 5000-ACTNO-EXIT .          01/05/08
            String actno = settingActno(clmr);
            // 021000* 該代收類別因為外部代收關係　如果入帳戶須將部份金額              01/05/08
            // 021100* 轉掛電子化收款手續費收入                                        01/05/08
            // 021200     PERFORM 6000-KFEE-RTN        THRU 6000-KFEE-EXIT  .          01/05/08
            settingKfee(wkTcode);
            // 021220* 入金機入帳要拆成兩筆入                                          05/10/24
            // 021240     PERFORM 7000-INAMT-RTN       THRU 7000-INAMT-EXIT .          05/10/24
            depositMachine(clmr, actno);
            writeDetail(actno, clmr);
        }
    }

    private void sumClbaf(String code, int clbafIndex) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sumClbaf()");
        // 018800 2100-999SUM-LOOP.                                                01/05/08
        // 018900     FIND NEXT DB-CLBAF-IDX1  OF  DB-CLBAF-DDS  AT                01/05/08
        // 019000          DB-CLBAF-CLLBR  OF  DB-CLBAF-DDS  =  999       AND      01/05/08
        // 019100          DB-CLBAF-DATE   OF  DB-CLBAF-DDS  =  WK-RDAY   AND      06/07/14
        // 019200          DB-CLBAF-CODE   OF  DB-CLBAF-DDS  =  WK-TCODE           01/05/08
        // 019300     ON EXCEPTION                                                 01/05/08
        // 019400        IF DMSTATUS(NOTFOUND)                                     01/05/08
        // 019500          GO TO  2100-999SUM-EXIT.                                01/05/08
        List<ClbafbyCllbr999Bus> clbafList =
                clbafService.findbyCllbr999(inputRday, code, clbafIndex, PAGE_LIMIT);
        if (!Objects.isNull(clbafList) && !clbafList.isEmpty()) {
            for (ClbafbyCllbr999Bus clbaf : clbafList) {
                BigDecimal amt = clbaf.getAmt();
                int cnt = clbaf.getCnt();
                BigDecimal kfee = clbaf.getKfee();
                // 019700     COMPUTE WK-TASK-AMT  = WK-TASK-AMT  + DB-CLBAF-AMT .         01/06/25
                // 019800     COMPUTE WK-TASK-CNT  = WK-TASK-CNT  + DB-CLBAF-CNT .         01/06/25
                // 019900     COMPUTE WK-TASK-KFEE = WK-TASK-KFEE + DB-CLBAF-KFEE.         01/06/25
                totalAmt = totalAmt.add(amt);
                totalCnt += cnt;
                totalKfee = totalKfee.add(kfee);
            }
            if (clbafList.size() == PAGE_LIMIT) {
                clbafIndex++;
                sumClbaf(code, clbafIndex);
            }
        }
    }

    private void writeDetail(String actno, ClmrbyPbrnoBus clmr) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeDetail()");
        // 021300     ADD   1                      TO  WK-SEQ           .          01/06/20
        wkSeq++;
        // 021400     MOVE  LOW-VALUE              TO  FD-BSCTL-REC     .          01/05/08
        FileBsctlDetail bsctlDetail = new FileBsctlDetail();
        // 021500     MOVE  2                      TO  FD-BSCTL-DATYPE  .          01/05/08
        bsctlDetail.setDatype("2");
        // 021600     MOVE  WK-SEQ                 TO  FD-BSCTL-SEQNO   .          01/05/08
        bsctlDetail.setSeqno(parse.decimal2String(wkSeq, 6, 0));
        // 021700     MOVE  SPACES                 TO  FD-BSCTL-FILLER1 .          01/05/08
        // 021800     MOVE  WK-ACTNO               TO  FD-BSCTL-ACTNO3  .          01/05/08
        bsctlDetail.setActno1(actno); // FD-BSCTL-ACTNO3 是 FD-BSCTL-ACTNO1的redefine
        if (actno.length() < 16
                || (actno.length() == 16 && actno.substring(12, 16).trim().isEmpty())) {
            if (!c004RptFasMap.containsValue(actno.substring(0, 12))) {
                c004RptFasMap.put("ACTNO", actno.substring(0, 12));
                c004RptFasMap.put("ACTNAME", "");
            }
        }
        // 021900     MOVE  WK-TBSDY               TO  FD-BSCTL-RDAY1   .          01/05/08
        bsctlDetail.setRday1(parse.decimal2String(tbsday, 7, 0));
        // 022000     MOVE  ZEROS                  TO  FD-BSCTL-ERDAY   .          01/05/08
        // 022010* 入機金備註特殊擺放 1050601CNT000002                             05/10/24
        // 022020     IF  DB-CLMR-CHKTYPE = "X"                                    05/10/24
        // 022030         MOVE  WK-TBSDY               TO  WK-REMARK(1:7)          05/10/24
        // 022040         MOVE  "CNT"                  TO  WK-RTEXT(2:3)           05/10/24
        // 022050         COMPUTE  WK-TASK-LBYCNT = WK-TASK-CNT - WK-TASK-LBYCNT   05/10/24
        // 022070         MOVE  WK-TASK-LBYCNT         TO  WK-RTCNT                05/10/24
        // 022080     ELSE                                                         05/10/24
        // 022100         MOVE  DB-CLMR-CODE           TO  WK-RTCODE               05/10/24
        // 022200         MOVE  ",CNT"                 TO  WK-RTEXT                05/10/24
        // 022300         MOVE  WK-TASK-CNT            TO  WK-RTCNT.               05/10/24
        // ref:
        // 004500   03  WK-REMARK           PIC X(26).                             01/05/08
        // 004600   03  WK-REMARK-R REDEFINES WK-REMARK.                           01/05/08
        // 004700       05  WK-RTCODE       PIC X(06).                             01/05/08
        // 004800       05  WK-RTEXT        PIC X(04).                             01/05/08
        // 004900       05  WK-RTCNT        PIC X(06).                             01/05/08
        // 005000       05  FILLER          PIC X(10).                             01/05/08
        String wkRemark = "";
        if (clmr.getChktype().trim().equals(CHKTYPE_X)) {
            wkRemark = parse.decimal2String(tbsday, 7, 0);
            wkRemark += "CNT";
            depositMachineCnt = totalCnt - depositMachineCnt;
            wkRemark += parse.decimal2String(depositMachineCnt, 6, 0);
        } else {
            wkRemark = clmr.getCode();
            wkRemark += ",CNT";
            wkRemark += parse.decimal2String(totalCnt, 6, 0);
        }
        // 022400     MOVE  WK-REMARK              TO  FD-BSCTL-REMARK1 .          01/05/08
        bsctlDetail.setRemark1(wkRemark);
        // 022500     MOVE  SPACES                 TO  FD-BSCTL-ID                 01/05/08
        // 022600                                      FD-BSCTL-STCD2              01/05/08
        // 022700                                      FD-BSCTL-PNO1               01/05/08
        // 022800                                      FD-BSCTL-PNO2    .          01/05/08
        // 022900     MOVE  WK-TBSDY               TO  FD-BSCTL-USRDATA .          01/05/08
        // 022950     MOVE  DB-CLMR-CODE           TO  FD-BSCTL-USRDATA(8:6).      05/10/24
        String wkUsrdata = parse.decimal2String(tbsday, 7, 0);
        wkUsrdata += clmr.getCode();
        bsctlDetail.setUsrdata(wkUsrdata);
        // 023000     MOVE  0                      TO  FD-BSCTL-CHKFG1  .          01/05/08
        // 023100     MOVE  0                      TO  FD-BSCTL-CHKID   .          01/05/08
        // 023200     MOVE  SPACES                 TO  FD-BSCTL-FILLER2 .          01/05/08
        // 023300* 專戶　　：０－借．１－貸                                        01/05/08
        // 023400* 會計帳號：８：借．９－貸                                        01/05/08
        // 023500     IF WK-ACTNOFLG = 1 AND WK-TCODE(1:2) NOT ="12"               01/05/08
        // 023600       MOVE    1                  TO  FD-BSCTL-CRDB               01/05/08
        // 023700       MOVE    WK-TASK-AMT        TO  FD-BSCTL-TXAMT              01/05/08
        // 023800       ADD     1                  TO  WK-CRCNT                    01/05/08
        // 023900       ADD     WK-TASK-AMT        TO  WK-CRAMT      .             01/05/08
        String wkTcode_1_2 = clmr.getCode().substring(0, 2);
        if (actnoFlg == 1 && !wkTcode_1_2.equals("12")) {
            bsctlDetail.setCrdb("1");
            bsctlDetail.setTxamt(parse.decimal2String(totalAmt, 11, 2));
            crCnt++;
            crAmt = crAmt.add(totalAmt);
        }
        // 024000     IF WK-ACTNOFLG = 2 AND WK-TCODE(1:2) NOT ="12"               01/05/08
        // 024100       MOVE    9                  TO  FD-BSCTL-CRDB               01/05/08
        // 024200       MOVE    WK-TASK-AMT        TO  FD-BSCTL-TXAMT              01/05/08
        // 024300       ADD     1                  TO  WK-CRCNT                    01/05/08
        // 024400       ADD     WK-TASK-AMT        TO  WK-CRAMT      .             01/05/08
        if (actnoFlg == 2 && !wkTcode_1_2.equals("12")) {
            bsctlDetail.setCrdb("9");
            bsctlDetail.setTxamt(parse.decimal2String(totalAmt, 11, 2));
            crCnt++;
            crAmt = crAmt.add(totalAmt);
        }
        // 024500     IF WK-ACTNOFLG = 1 AND WK-TCODE(1:2) ="12"                   01/05/08
        // 024600       MOVE    0                  TO  FD-BSCTL-CRDB               01/05/08
        // 024700       MOVE    WK-TASK-AMT        TO  FD-BSCTL-TXAMT              01/05/08
        // 024800       ADD     1                  TO  WK-DBCNT                    01/05/08
        // 024900       ADD     WK-TASK-AMT        TO  WK-DBAMT      .             01/05/08
        if (actnoFlg == 1 && wkTcode_1_2.equals("12")) {
            bsctlDetail.setCrdb("0");
            bsctlDetail.setTxamt(parse.decimal2String(totalAmt, 11, 2));
            dbCnt++;
            dbAmt = dbAmt.add(totalAmt);
        }
        // 025000     IF WK-ACTNOFLG = 2 AND WK-TCODE(1:2) ="12"                   01/05/08
        // 025100       MOVE    8                  TO  FD-BSCTL-CRDB               01/05/08
        // 025200       MOVE    WK-TASK-AMT        TO  FD-BSCTL-TXAMT              01/05/08
        // 025300       ADD     1                  TO  WK-DBCNT                    01/05/08
        // 025400       ADD     WK-TASK-AMT        TO  WK-DBAMT      .             01/05/08
        if (actnoFlg == 2 && wkTcode_1_2.equals("12")) {
            bsctlDetail.setCrdb("8");
            bsctlDetail.setTxamt(parse.decimal2String(totalAmt, 11, 2));
            dbCnt++;
            dbAmt = dbAmt.add(totalAmt);
        }

        // 連動帳號科目檢核
        String apno = actno.substring(3, 6);
        boolean isS41 = wkTcode_1_2.equals("12");
        createTita(apno, isS41, actnoFlg, totalAmt, actno, wkRemark);

        // 025500                                                                  01/05/08
        // 025600     WRITE FD-BSCTL-REC.                                          01/05/08
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(bsctlDetail, false));
        textFileUtil.writeFileContent(bsctlFileName, dataList, BIG5);
        // 025700                                                                  01/05/08
        // 025800 2000-WRTDTL-EXIT.                                                01/05/08
        // 025900      EXIT.                                                       01/05/08
    }

    private String settingActno(ClmrbyPbrnoBus clmr) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "settingActno()");
        // 029200 5000-ACTNO-RTN.                                                  01/05/08
        // 029300* １：存款帳號．２：會計帳號                                      01/05/08
        // 029400     MOVE      0                   TO    WK-ACTNOFLG.             01/05/08
        // 029500                                                                  01/05/08
        // 029600     IF        DB-CLMR-ACTNO       >     0                        01/05/08
        // 029700       MOVE    1                   TO    WK-ACTNOFLG              01/05/08
        // 029800       MOVE    SPACES              TO    WK-DPACTNO               01/05/08
        // 029900       MOVE    DB-CLMR-ACTNO       TO    WK-PBACTNO               01/05/08
        // 030000       MOVE    WK-DPACTNO          TO    WK-ACTNO                 01/05/08
        // 030100     ELSE                                                         01/05/08
        // 030200       MOVE    2                   TO    WK-ACTNOFLG              01/05/08
        // 030300       MOVE    SPACES              TO    WK-CLSACTNO              01/05/08
        // 030400       MOVE    DB-CLMR-CLSACNO     TO    WK-CLSACNO               01/05/08
        // 030500       MOVE    DB-CLMR-CLSSBNO     TO    WK-CLSSBNO               01/05/08
        // 030600       MOVE    DB-CLMR-CLSDTLNO    TO    WK-CLSDTLNO              01/05/08
        // 030700       MOVE    WK-CLSACTNO         TO    WK-ACTNO   .             01/05/08
        if (clmr.getActno() != 0L) {
            actnoFlg = 1;
            return String.valueOf(clmr.getActno());
        } else {
            actnoFlg = 2;
            CltmrBus cltmr = cltmrService.findById(clmr.getCode());
            String clsacno = cltmr.getClsacno();
            String clssbno = cltmr.getClssbno();
            String clsdtlno = cltmr.getClsdtlno();
            String wkClsactno = Objects.isNull(clsacno) ? "" : clsacno;
            wkClsactno += Objects.isNull(clssbno) ? "" : clssbno;
            wkClsactno += Objects.isNull(clsdtlno) ? "" : clsdtlno;
            return wkClsactno;
        }
    }

    private void settingKfee(String wkTcode) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "settingKfee()");
        // 031200 6000-KFEE-RTN.                                                   01/05/08
        // 031300* 要核算因外部代收主辦行產生的 451600-0145-0007  電子商務手續費   01/05/08
        // 031400                                                                  01/05/08
        // 031600* 如果是代付類別都不用變動入款金額                                02/10/22
        // 031800*    IF   (WK-ACTNOFLG              =       2                  )  02/10/22
        // 032000     IF   (WK-TCODE  >  "121000"  AND  WK-TCODE  <  "129999"   )  02/10/22
        // 032100          PERFORM   6200-KFEE-RTN  THRU    6200-KFEE-EXIT         01/05/08
        // 032200     ELSE                                                         01/05/08
        // 032300          PERFORM   6100-KFEE-RTN  THRU    6100-KFEE-EXIT.        01/05/08
        if (wkTcode.compareTo("121000") > 0 && wkTcode.compareTo("129999") < 0) {
            settingKfee2();
        } else {
            settingKfee1(wkTcode);
        }
        // 032400                                                                  01/05/08
        // 032500 6000-KFEE-EXIT.                                                  01/05/08
        // 032600     EXIT.                                                        01/05/08
    }

    private void settingKfee1(String wkTcode) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "settingKfee1()");
        // 033300 6100-KFEE-RTN.                                                   01/05/08
        // 034500* 如果手續費金額不為零就要多寫一筆入會計分錄                      01/05/08
        // 034600* 也要從本金扣除                                                  01/05/08
        // 034700     IF WK-TASK-KFEE          NOT = 0                             01/06/25
        // 034800        PERFORM 6150-WRTKFEE-RTN THRU  6150-WRTKFEE-EXIT          01/05/08
        // 034900        COMPUTE WK-TASK-AMT   =  WK-TASK-AMT - WK-TASK-KFEE.      01/06/25
        if (totalKfee.compareTo(BigDecimal.ZERO) != 0) {
            writeKfee(wkTcode);
        }
        // 035000                                                                  01/05/08
        // 035200 6100-KFEE-EXIT.                                                  01/05/08
        // 035300     EXIT.                                                        01/05/08
    }

    private void settingKfee2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "settingKfee2()");
        // 032800 6200-KFEE-RTN.                                                   01/05/08
        // 032900     MOVE   0           TO         WK-TASK-KFEE.                  01/06/25
        totalKfee = BigDecimal.ZERO;
        // 033000 6200-KFEE-EXIT.                                                  01/05/08
        // 033100     EXIT.                                                        01/05/08
    }

    private void writeKfee(String wkTcode) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeKfee()");
        // 035500 6150-WRTKFEE-RTN.                                                01/05/08
        // 035600     ADD   1                      TO  WK-SEQ           .          01/05/08
        wkSeq++;
        // 035700                                                                  01/05/08
        // 035800     MOVE  LOW-VALUE              TO  FD-BSCTL-REC     .          01/05/08
        FileBsctlDetail bsctlDetail = new FileBsctlDetail();
        // 035900     MOVE  2                      TO  FD-BSCTL-DATYPE  .          01/05/08
        bsctlDetail.setDatype("2");
        // 036000     MOVE  WK-SEQ                 TO  FD-BSCTL-SEQNO   .          01/05/08
        bsctlDetail.setSeqno(parse.decimal2String(wkSeq, 6, 0));
        // 036100     MOVE  SPACES                 TO  FD-BSCTL-FILLER1 .          01/05/08
        // 036150*IFRSCN 102/03/11 CSCHEN                                          02/06/06
        // 036200     MOVE  "41030501450007"       TO  FD-BSCTL-ACTNO3(1:14).      02/06/06
        bsctlDetail.setActno1("41030501450007");
        // 036250*IFRSCN End                                                       02/06/06
        // 036300     MOVE  WK-TBSDY               TO  FD-BSCTL-RDAY1   .          01/05/08
        bsctlDetail.setRday1(parse.decimal2String(tbsday, 7, 0));
        // 036400     MOVE  ZEROS                  TO  FD-BSCTL-ERDAY   .          01/05/08
        // 036500     MOVE  WK-TCODE               TO  WK-RTCODE        .          01/05/08
        // 036600     MOVE  ",CNT"                 TO  WK-RTEXT         .          01/05/08
        // 036700     MOVE  WK-TASK-CNT            TO  WK-RTCNT         .          01/05/08
        String wkRemark = wkTcode;
        wkRemark += ",cnt";
        wkRemark += totalCnt;
        // 036800     MOVE  WK-REMARK              TO  FD-BSCTL-REMARK1 .          01/05/08
        bsctlDetail.setRemark1(wkRemark);
        // 036900     MOVE  SPACES                 TO  FD-BSCTL-ID      ,          01/05/08
        // 037000                                      FD-BSCTL-STCD2   ,          01/05/08
        // 037100                                      FD-BSCTL-PNO1    ,          01/05/08
        // 037200                                      FD-BSCTL-PNO2    .          01/05/08
        // 037300     MOVE  WK-TBSDY               TO  FD-BSCTL-USRDATA .          01/05/08
        // 037350     MOVE  DB-CLMR-CODE           TO  FD-BSCTL-USRDATA(8:6).      05/10/24
        String wkUsrdata = String.valueOf(tbsday);
        wkUsrdata += wkTcode;
        bsctlDetail.setUsrdata(wkUsrdata);
        // 037400     MOVE  0                      TO  FD-BSCTL-CHKFG1  .          01/05/08
        // 037500     MOVE  0                      TO  FD-BSCTL-CHKID   .          01/05/08
        // 037600     MOVE  SPACES                 TO  FD-BSCTL-FILLER2 .          01/05/08
        // 037700     MOVE  9                      TO  FD-BSCTL-CRDB    .          01/05/08
        bsctlDetail.setCrdb("9");
        // 037800     MOVE  WK-TASK-KFEE           TO  FD-BSCTL-TXAMT   .          01/06/25
        bsctlDetail.setTxamt(parse.decimal2String(totalKfee, 11, 2));

        // 連動帳號科目檢核
        String apno = "41030501450007".substring(3, 6);
        createTita(apno, false, actnoFlg, totalAmt, "41030501450007", wkRemark);

        // 037900     WRITE FD-BSCTL-REC                                .          01/05/08
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(bsctlDetail, false));
        textFileUtil.writeFileContent(bsctlFileName, dataList, BIG5);
        // 038000     ADD   1                      TO  WK-CRCNT         .          01/05/08
        // 038100     ADD   WK-TASK-KFEE           TO  WK-CRAMT         .          01/06/25
        crCnt++;
        crAmt = crAmt.add(totalKfee);
        // 038200                                                                  01/05/08
        // 038300 6150-WRTKFEE-EXIT.                                               01/05/08
        // 038400     EXIT.                                                        01/05/08
    }

    private void depositMachine(ClmrbyPbrnoBus clmr, String actno) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "depositMachine()");
        // 038700 7000-INAMT-RTN.                                                  05/10/24
        // 038800* 找出入金機，日期小於本日的筆數，並加總金額                      05/10/24
        // 038900                                                                  05/10/24
        // 039000     IF DB-CLMR-CHKTYPE = "X"                                     05/10/24
        // 039010        MOVE  0               TO WK-TASK-LBYCNT                   05/10/24
        depositMachineCnt = 0;
        // 039020        SET  DB-CLDTL-IDX3 OF DB-CLDTL-DDS TO BEGINNING           05/10/24
        // 039050        PERFORM READ-CLDTL-RTN    THRU  READ-CLDTL-EXIT.          05/10/24
        BigDecimal depositMachineAmt = BigDecimal.ZERO;
        if (clmr.getChktype().trim().equals(CHKTYPE_X)) {
            depositMachineAmt = readCldtl(clmr.getCode(), depositMachineAmt, 0);
        }
        // 039070     IF WK-TASK-INAMT > 0                                         05/10/24
        // 039100        PERFORM 7100-WRTINAMT-RTN THRU  7100-WRTINAMT-EXIT        05/10/24
        // 039200        COMPUTE WK-TASK-AMT   =  WK-TASK-AMT - WK-TASK-INAMT      05/10/24
        // 039300        MOVE  0               TO WK-TASK-INAMT.                   05/10/24
        if (depositMachineAmt.compareTo(BigDecimal.ZERO) > 0) {
            writeDepositMachine(actno, depositMachineAmt, clmr.getCode());
            totalAmt = totalAmt.subtract(depositMachineAmt);
        }
        // 039400 7000-INAMT-EXIT.                                                 05/10/24
        // 039500     EXIT.                                                        05/10/24
        // 039600                                                                  05/10/24
    }

    private BigDecimal readCldtl(String code, BigDecimal depositMachineAmt, int cldtlIndex) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readCldtl()");
        // 043000 READ-CLDTL-RTN.                                                  05/10/24
        // 043100                                                                  05/10/24
        // 043200                                                                  05/10/24
        // 043300     FIND NEXT DB-CLDTL-IDX3  OF  DB-CLDTL-DDS                    05/10/24
        // 043400     AT   DB-CLDTL-CODE   OF  DB-CLDTL-DDS  =  WK-TCODE           05/10/24
        // 043500     AND  DB-CLDTL-DATE   OF  DB-CLDTL-DDS  =  WK-RDAY            06/07/14
        // 043600     ON EXCEPTION                                                 05/10/24
        // 043700        IF DMSTATUS(NOTFOUND)                                     05/10/24
        // 043800          GO TO  READ-CLDTL-EXIT.                                 05/10/24
        List<CldtlbyCodeEntdyBus> cldtlList =
                cldtlService.findbyCodeEntdy(code, inputRday, cldtlIndex, PAGE_LIMIT);
        if (!Objects.isNull(cldtlList) && !cldtlList.isEmpty()) {
            for (CldtlbyCodeEntdyBus cldtl : cldtlList) {
                // 043900* 找到實際交易日小於本營業日                                      05/10/24
                // 044000     IF DB-CLDTL-SITDATE < DB-CLDTL-DATE                          05/10/24
                // 044100        COMPUTE WK-TASK-INAMT  =  WK-TASK-INAMT + DB-CLDTL-AMT    05/10/24
                // 044150        ADD     1              TO WK-TASK-LBYCNT.                 05/10/24
                // 044200     GO TO READ-CLDTL-RTN.                                        05/10/24
                int sitDate = cldtl.getSitdate();
                if (sitDate < inputRday) {
                    BigDecimal cldtlAmt = cldtl.getAmt();
                    depositMachineAmt = depositMachineAmt.add(cldtlAmt);
                    depositMachineCnt++;
                }
            }
            if (cldtlList.size() == PAGE_LIMIT) {
                cldtlIndex++;
                depositMachineAmt = readCldtl(code, depositMachineAmt, cldtlIndex);
            }
        }
        return depositMachineAmt;
    }

    private void writeDepositMachine(String actno, BigDecimal depositMachineAmt, String wkTcode) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeDepositMachine()");
        // 039700 7100-WRTINAMT-RTN.                                               05/10/24
        // 039800     ADD   1                      TO  WK-SEQ           .          05/10/24
        wkSeq++;
        // 039900                                                                  05/10/24
        // 040000     MOVE  LOW-VALUE              TO  FD-BSCTL-REC     .          05/10/24
        FileBsctlDetail bsctlDetail = new FileBsctlDetail();
        // 040100     MOVE  2                      TO  FD-BSCTL-DATYPE  .          05/10/24
        bsctlDetail.setDatype("2");
        // 040200     MOVE  WK-SEQ                 TO  FD-BSCTL-SEQNO   .          05/10/24
        bsctlDetail.setSeqno(parse.decimal2String(wkSeq, 6, 0));
        // 040300     MOVE  SPACES                 TO  FD-BSCTL-FILLER1 .          05/10/24
        // 040400                                                                  05/10/24
        // 040500     MOVE  WK-ACTNO               TO  FD-BSCTL-ACTNO3  .          05/10/24
        bsctlDetail.setActno1(actno);
        if (actno.length() < 16
                || (actno.length() == 16 && actno.substring(12, 16).trim().isEmpty())) {
            if (!c004RptFasMap.containsValue(actno.substring(0, 12))) {
                c004RptFasMap.put("ACTNO", actno.substring(0, 12));
                c004RptFasMap.put("ACTNAME", "");
            }
        }
        // 040600                                                                  05/10/24
        // 040700     MOVE  WK-TBSDY               TO  FD-BSCTL-RDAY1   .          05/10/24
        bsctlDetail.setRday1(parse.decimal2String(tbsday, 7, 0));
        // 040800     MOVE  ZEROS                  TO  FD-BSCTL-ERDAY   .          05/10/24
        // 040900     MOVE  WK-TASK-LBSDY          TO  WK-REMARK(1:7)  .           05/10/24
        // 041000     MOVE  "CNT"                  TO  WK-RTEXT(2:3)    .          05/10/24
        // 041100     MOVE  WK-TASK-LBYCNT         TO  WK-RTCNT         .          05/10/24
        // 041200     MOVE  WK-REMARK              TO  FD-BSCTL-REMARK1 .          05/10/24
        String wkRemark = parse.decimal2String(lbsdy, 7, 0);
        wkRemark += "CNT";
        wkRemark += totalCnt;
        bsctlDetail.setRemark1(wkRemark);
        // 041300     MOVE  SPACES                 TO  FD-BSCTL-ID      ,          05/10/24
        // 041400                                      FD-BSCTL-STCD2   ,          05/10/24
        // 041500                                      FD-BSCTL-PNO1    ,          05/10/24
        // 041600                                      FD-BSCTL-PNO2    .          05/10/24
        // 041700     MOVE  WK-TBSDY               TO  FD-BSCTL-USRDATA .          05/10/24
        // 041750     MOVE  DB-CLMR-CODE           TO  FD-BSCTL-USRDATA(8:6).      05/10/24
        String wkUsrdata = parse.decimal2String(tbsday, 7, 0);
        wkUsrdata += wkTcode;
        bsctlDetail.setUsrdata(wkUsrdata);
        // 041800     MOVE  0                      TO  FD-BSCTL-CHKFG1  .          05/10/24
        // 041900     MOVE  0                      TO  FD-BSCTL-CHKID   .          05/10/24
        // 042000     MOVE  SPACES                 TO  FD-BSCTL-FILLER2 .          05/10/24
        // 042100     MOVE  1                      TO  FD-BSCTL-CRDB    .          05/10/24
        bsctlDetail.setCrdb("1");
        // 042200     MOVE  WK-TASK-INAMT          TO  FD-BSCTL-TXAMT   .          05/10/24
        bsctlDetail.setTxamt(parse.decimal2String(depositMachineAmt, 11, 2));

        // 連動帳號科目檢核
        String apno = actno.substring(3, 6);
        boolean isS41 = wkTcode.startsWith("12");
        createTita(apno, isS41, actnoFlg, totalAmt, actno, wkRemark);

        // 042300     WRITE FD-BSCTL-REC                                .          05/10/24
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(bsctlDetail, false));
        textFileUtil.writeFileContent(bsctlFileName, dataList, BIG5);
        // 042400     ADD   1                      TO  WK-CRCNT         .          05/10/24
        // 042500     ADD   WK-TASK-INAMT          TO  WK-CRAMT         .          05/10/24
        crCnt++;
        crAmt = crAmt.add(depositMachineAmt);
        // 042600                                                                  05/10/24
        // 042700 7100-WRTINAMT-EXIT.                                              05/10/24
        // 042800     EXIT.                                                        05/10/24
    }

    private void writeHeader() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeHeader()");
        // 014600              IF WK-SEQ >  0                                      01/05/08
        // 014700                PERFORM 4000-WRTHDR-RTN THRU 4000-WRTHDR-EXIT     01/05/08
        if (wkSeq > 0) {
            // 026100 4000-WRTHDR-RTN.                                                 01/05/08
            // 026200     MOVE  LOW-VALUE              TO  FD-BSCTL-REC     .          01/05/08
            FileBsctlHeader bsctlHeader = new FileBsctlHeader();
            // 026300     MOVE  1                      TO  FD-BSCTL-DATYPE  .          01/05/08
            bsctlHeader.setDatype("1");
            // 026400     MOVE  WK-BRNO                TO  WK-BRANCH        .          01/05/08
            // 026500     MOVE  WK-CUSTID              TO  FD-BSCTL-CODE    .          01/05/08
            // ref:
            // 005400   03  WK-CUSTID .                                                01/05/08
            // 005500     05  WK-BRANCH         PIC X(03).                             01/05/08
            // 005600     05  FILLER            PIC X(04) VALUE "0891".                01/05/08
            String wkCustid = parse.decimal2String(inputBrno, 3, 0);
            wkCustid += "0891";
            bsctlHeader.setCode(wkCustid);
            // 026600     MOVE  "Z99"                  TO  FD-BSCTL-MTYPE   .          01/05/08
            bsctlHeader.setMtype("Z99");
            // 026700     MOVE  SPACES                 TO  FD-BSCTL-ENTPNO  .          01/05/08
            // 026800     MOVE  WK-TBSDY               TO  FD-BSCTL-RDAY    .          01/05/08
            bsctlHeader.setRday(parse.decimal2String(tbsday, 7, 0));
            // 026900     MOVE  2                      TO  FD-BSCTL-CDFLG   .          01/05/08
            bsctlHeader.setCdflg("2");
            // 027000     MOVE  SPACES                 TO  FD-BSCTL-REMARK  .          01/05/08
            // 027100     MOVE  00                     TO  FD-BSCTL-CURCD   .          01/05/08
            bsctlHeader.setCurcd("00");
            // 027200     MOVE  WK-DBCNT               TO  FD-BSCTL-ADTOTCNT.          01/05/08
            bsctlHeader.setAdtotcnt(parse.decimal2String(dbCnt, 6, 0));
            // 027300     MOVE  WK-DBAMT               TO  FD-BSCTL-ADTOTAMT.          01/05/08
            bsctlHeader.setAdtotamt(parse.decimal2String(dbAmt, 11, 2));
            // 027400     MOVE  WK-CRCNT               TO  FD-BSCTL-ACTOTCNT.          01/05/08
            bsctlHeader.setActotcnt(parse.decimal2String(crCnt, 6, 0));
            // 027500     MOVE  WK-CRAMT               TO  FD-BSCTL-ACTOTAMT.          01/05/08
            bsctlHeader.setActotamt(parse.decimal2String(crAmt, 11, 2));
            // 027600     MOVE  ZEROS                  TO  FD-BSCTL-FDTOTCNT           01/05/08
            // 027700                                      FD-BSCTL-FDTOTAMT           01/05/08
            // 027800                                      FD-BSCTL-FCTOTCNT           01/05/08
            // 027900                                      FD-BSCTL-FCTOTAMT.          01/05/08
            // 028000     MOVE  9                      TO  FD-BSCTL-STATUS  .          01/05/08
            bsctlHeader.setStatus("9");
            // 028100     MOVE  SPACES                 TO  FD-BSCTL-USERDATA           01/05/08
            // 028200                                      FD-BSCTL-UPLBHSEQ.          01/05/08
            // 028300     MOVE  SPACES                 TO  FD-BSCTL-MDTYPE  .          01/05/08
            // 028400     MOVE  5                      TO  FD-BSCTL-CHANNEL .          01/05/08
            bsctlHeader.setChannel("5");
            // 028500     MOVE  SPACES                 TO  FD-BSCTL-CHKFG   .          01/05/08
            // 028600     MOVE  SPACES                 TO  FD-BSCTL-FILLER  .          01/05/08
            // 028700     WRITE FD-BSCTL-REC .                                         01/05/08
            List<String> dataList = new ArrayList<>();
            dataList.add(vo2TextFormatter.formatRS(bsctlHeader, false));
            textFileUtil.writeFileContent(bsctlFileName, dataList, BIG5);
            // 028800                                                                  01/05/08
            // 028900 4000-WRTHDR-EXIT.                                                01/05/08
            // 029000      EXIT.                                                       01/05/08
        }
    }

    private void createTita(
            String apno,
            boolean isS41,
            int actnoFlg,
            BigDecimal amt,
            String actno,
            String wkRemark) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createTita()");
        int entyp = 0;
        if (actnoFlg == 1) {
            switch (apno) {
                    // 活存帳號
                case "001", "002", "003", "004", "005", "008", "010", "020", "025" -> {
                    if (isS41) entyp = 1; // 連動p10
                    else entyp = 2; // 連動p20
                }
                    // 支存帳號
                case "031", "032", "033", "034", "035", "036", "037", "038", "039", "045" -> {
                    if (isS41) entyp = 3; // 連動k10
                    else entyp = 4; // 連動k20
                }
                default -> {
                    ApLogHelper.error(
                            log, false, LogType.NORMAL.getCode(), "unexpected apno={}", apno);
                    // TODO: 待調整錯誤代號及訊息
                    throw new LogicException(GE032, "(" + actno + ")"); // 因例外處理未寫先押一個錯誤代號
                }
            }
        } else {
            entyp = 5;
        }
        Map<String, String> tita = new HashMap<>();
        tita.put("PBRNO", parse.decimal2String(inputBrno, 3, 0));
        tita.put("TXCODE", "G6613");
        tita.put("DSCPT", "S13");
        tita.put("MRKEY", actno);
        tita.put("CRDB", isS41 ? "0" : "1");
        tita.put("TXAMT", parse.decimal2String(amt, 11, 2));
        tita.put("FEPDD", parse.decimal2String(tbsday % 100, 2, 0));
        tita.put("IT_G6613_ENTYP", parse.decimal2String(entyp, 1, 0));
        tita.put("IT_G6613_ACTNO", actno);
        tita.put("IT_G6613_REMARK", wkRemark);
        tita.put("IT_G6613_RDAY1", parse.decimal2String(tbsday, 7, 0));

        titaList.add(tita);
    }

    private void createG6613TitaFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createG6613TitaFile()");
        if (!titaList.isEmpty()) {
            List<String> dataList = new ArrayList<>();
            for (Map<String, String> tita : titaList) {
                String data = "";
                data += tita.get("PBRNO");
                data += ",";
                data += tita.get("TXCODE");
                data += ",";
                data += tita.get("DSCPT");
                data += ",";
                data += tita.get("MRKEY");
                data += ",";
                data += tita.get("CRDB");
                data += ",";
                data += tita.get("TXAMT");
                data += ",";
                data += tita.get("FEPDD");
                data += ",";
                data += tita.get("IT_G6613_ENTYP");
                data += ",";
                data += tita.get("IT_G6613_ACTNO");
                data += ",";
                data += tita.get("IT_G6613_REMARK");
                data += ",";
                data += tita.get("IT_G6613_RDAY1");
                dataList.add(data);
            }
            String g6613TitaFilePath = fileDir;
            g6613TitaFilePath += "G6613_";
            g6613TitaFilePath += parse.decimal2String(inputRday, 7, 0);
            g6613TitaFilePath += "_";
            g6613TitaFilePath += parse.decimal2String(tbsday, 7, 0);
            g6613TitaFilePath += "_";
            g6613TitaFilePath += parse.decimal2String(inputBrno, 3, 0);
            g6613TitaFilePath += ".txt";
            textFileUtil.deleteFile(g6613TitaFilePath);
            textFileUtil.writeFileContent(g6613TitaFilePath, dataList, BIG5);

            upload(g6613TitaFilePath);
        }
    }

    private void createC004_Rpt_FasTitaFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createC004_Rpt_FasTitaFile()");

        if (!c004RptFasMap.isEmpty()) {
            c004RptFasList.add(c004RptFasMap);
            List<String> dataList = new ArrayList<>();
            for (Map<String, String> tita : c004RptFasList) {
                String data = "";
                data += tita.get("ACTNO");
                data += ",";
                data += tita.get("ACTNAME");
                dataList.add(data);
            }
            String c004TitaFilePath = fileDir;
            c004RptFasFileName = "";
            c004RptFasFileName += "C004_RPT_FAS_";
            c004RptFasFileName += parse.decimal2String(inputRday, 7, 0);
            c004RptFasFileName += "_";
            c004RptFasFileName += parse.decimal2String(inputBrno, 3, 0);
            c004RptFasFileName += ".txt";
            c004TitaFilePath += c004RptFasFileName;
            textFileUtil.deleteFile(c004TitaFilePath);
            textFileUtil.writeFileContent(c004TitaFilePath, dataList, BIG5);

            upload(c004TitaFilePath);
        }
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = "/" + tbsday + "/2FSAP";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }
}
