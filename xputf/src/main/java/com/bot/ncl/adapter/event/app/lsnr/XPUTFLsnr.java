/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.XPUTF;
import com.bot.ncl.dto.entities.CldtlbyCodeListRangeEntdyBus;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("XPUTFLsnr")
@Scope("prototype")
public class XPUTFLsnr extends BatchListenerCase<XPUTF> {

    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private CldtlService cldtlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String FILE_OUTPUT_NAME = "XPUTF"; // 檔名
    private static final String CONVF_DATA = "DATA";
    private String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileXPUTFContents; //  檔案內容

    private XPUTF event;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private ClmrBus tClmr = new ClmrBus();
    private CltmrBus tCltmr = new CltmrBus();
    private ClmcBus tClmc = new ClmcBus();
    private String processDate = "";
    private String tbsdy;
    private String wkTaskCode = "";
    private String wkTaskCodeX = "";
    private int wkRtn = 0;
    private int wkTaskBdate = 0;
    private int wkTaskEdate = 0;
    private int wkTaskNdate = 0;
    private int wkTaskBdateN = 0;
    private int wkTaskEdateN = 0;
    private int wkFdate = 0;
    private int wkPuttype = 0;
    private int wkTaskNdateN = 0;
    private String wkPutname = "";
    private String wkPutfile = "";
    private String wkTaskPutfile = "";
    private int wkTaskPbrno = 0;
    private int wkTotcnt = 0;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private List<String> codeList = new ArrayList<>();
    private String xputfCtl = "";
    private String xputfCode = "";
    private BigDecimal xputfTotamt = BigDecimal.ZERO;
    private int xputfTotcnt = 0;
    private int xputfEdate = 0;
    private int xputfBdate = 0;
    private String xputfRcptid = "";
    private int xputfDate = 0;
    private int xputfTime = 0;
    private BigDecimal xputfOldamt = BigDecimal.ZERO;
    private int xputfCllbr = 0;
    private int xputfLmtdate = 0;
    private String xputfUserdata = "";
    private int xputfSitdate = 0;
    private String xputfTxtype = "";
    private BigDecimal xputfAmt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(XPUTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(XPUTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr run()");
        init(event);

        // 009300     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.
        mainRtn();
        // 009500*    DISPLAY "SYM/CL/BH/XPUTF GENERATE DATA/CL/BH/XPUTF OK".
        //// 關檔、結束程式
        // 009600     CLOSE   BOTSRDB.
        // 009700       CLOSE   FD-XPUTF    WITH SAVE.
        try {
            textFile.writeFileContent(outputFilePath, fileXPUTFContents, CHARSET);
            upload(outputFilePath, "DATA", "XPUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 009800     STOP RUN.

        ApLogHelper.info(
                log, false, LogType.APLOG.getCode(), "XPUTF  WK_TASK_PUTFILE = {}", wkTaskPutfile);
        ApLogHelper.info(
                log, false, LogType.APLOG.getCode(), "XPUTF  WK_TASK_PBRNO = {}", wkTaskPbrno);
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("WK_TASK_PUTFILE", wkTaskPutfile);
        responseTextMap.put("WK_TASK_PBRNO", "" + wkTaskPbrno);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", responseTextMap);
    }

    private void init(XPUTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 批次日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // REMOVE  DATA/CL/BH/XPUTF/=
        textFile.deleteDir(
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "XPUTF");

        // 接收參數:WK-TASK-CODE  X(06)                 代收類別
        //          WK-TASK-BDATE 9(07) BINARY EXTENDED 本營業日
        //          WK-TASK-EDATE 9(07) BINARY EXTENDED 下營業日
        //          WK-TASK-NDATE 9(07) BINARY EXTENDED 下下營業日
        // 回傳參數:WK-TASK-PUTFILE X(10) PUTTYPE+PUTNAME
        //          WK-TASK-PBRNO   X(03) 主辦行
        wkTaskCode = textMap.get("WK_TASK_CODE"); // TODO: 待確認BATCH參數名稱
        wkTaskBdate = parse.string2Integer(textMap.get("WK_TASK_BDATE")); // TODO: 待確認BATCH參數名稱
        wkTaskEdate = parse.string2Integer(textMap.get("WK_TASK_EDATE")); // TODO: 待確認BATCH參數名稱
        wkTaskNdate = parse.string2Integer(textMap.get("WK_TASK_NDATE")); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLDT wkTaskBdate={}", wkTaskBdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLDT wkTaskEdate={}", wkTaskEdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLDT wkTaskNdate={}", wkTaskNdate);
        // 008600 0000-START.

        //// 開啟檔案
        // 008700     OPEN INQUIRY BOTSRDB.

        //// DISPLAY訊息，包含在系統訊息中
        // 008800     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        //
        //// 搬接收參數給WK-...
        //// 接收參數-代收類別"111961"改成"121961"
        //
        // 008900     MOVE    WK-TASK-BDATE  TO      WK-TASK-BDATE-N.
        wkTaskBdateN = wkTaskBdate;
        // 009000     MOVE    WK-TASK-EDATE  TO      WK-TASK-EDATE-N.
        wkTaskEdateN = wkTaskEdate;
        // 009010     MOVE    WK-TASK-NDATE  TO      WK-TASK-NDATE-N.
        wkTaskNdateN = wkTaskNdate;
        // 009020     IF      WK-TASK-CODE= "111961" THEN
        if ("111961".equals(wkTaskCode)) {
            // 009040             MOVE  "121961" TO      WK-TASK-CODE.
            wkTaskCode = "121961";
        }
        // 009100     MOVE    WK-TASK-CODE   TO      WK-TASK-CODE-X.
        wkTaskCodeX = wkTaskCode;
        //
        //// 筆數、金額清0
        //
        // 009200     MOVE    0              TO      WK-TOTCNT,WK-TOTAMT.
        wkTotcnt = 0;
        wkTotamt = new BigDecimal(0);
        //
        //// 執行0000-MAIN-RTN，主程式
        //
        // 009300     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.

        // 009400 0000-END-RTN.
        fileXPUTFContents = new ArrayList<>();
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr mainRtn");
        // 010000 0000-MAIN-RTN.

        //// 執行5000-FINDCLMR-RTN，依接收之代收類別FIND DB-CLMR-IDX1事業單位基本資料檔，設定WK-RTN

        // 010100     PERFORM 5000-FINDCLMR-RTN  THRU  5000-FINDCLMR-EXIT.
        findclmrRtn();
        //// WK-RTN挑檔記號(0.挑檔 1.不挑檔)
        //// 若WK-RTN=1，結束本段落

        // 010200     IF      WK-RTN           =       1
        if (wkRtn == 1) {
            // 010300         GO TO  0000-MAIN-EXIT.
            return;
        }

        //// 設定FD-XPUTF檔名變數值
        ////  WK-PUTDIR="DATA/CL/BH/XPUTF/"+WK-FDATE+"/"+WK-PUTFILE+".".
        //// 搬DB-CLMR-...給輸出參數

        // 010400     MOVE    WK-TASK-EDATE-N     TO   WK-FDATE.
        wkFdate = parse.string2Integer(formatUtil.pad9("" + wkTaskEdateN, 8).substring(2));
        // 010500     MOVE    DB-CLMR-PUTTYPE     TO   WK-PUTTYPE.
        wkPuttype = tClmc.getPuttype();
        // 010600     MOVE    DB-CLMR-PUTNAME     TO   WK-PUTNAME.
        wkPutname = tCltmr.getPutname();
        wkPutfile = formatUtil.padX("" + wkPuttype, 2) + formatUtil.padX(wkPutname, 8);
        // 010620     MOVE    WK-PUTFILE          TO   WK-TASK-PUTFILE.
        wkTaskPutfile = wkPutfile; // TODO: 待確認BATCH參數名稱
        // 010640     MOVE    DB-CLMR-PBRNO       TO   WK-TASK-PBRNO.
        wkTaskPbrno = tClmr.getPbrno(); // TODO: 待確認BATCH參數名稱

        //// 設定FD-XPUTF檔名
        // 010700     CHANGE  ATTRIBUTE FILENAME OF FD-XPUTF TO WK-PUTDIR.
        // 005900 01 WK-PUTDIR.
        // 006000  03 FILLER                            PIC X(17)
        // 006100                         VALUE "DATA/CL/BH/XPUTF/".
        // 006200  03 WK-FDATE                          PIC 9(06).
        // 006300  03 FILLER                            PIC X(01)
        // 006400                            VALUE "/".
        // 006450  03 WK-PUTFILE.
        // 006500   05 WK-PUTTYPE                       PIC X(02).
        // 006600   05 WK-PUTNAME                       PIC X(08).
        // 006700  03 FILLER                            PIC X(01)
        // 006800                            VALUE ".".
        outputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_OUTPUT_NAME
                        + PATH_SEPARATOR
                        + formatUtil.pad9("" + wkFdate, 6)
                        + PATH_SEPARATOR
                        + wkPutfile;
        //// 開新檔FD-XPUTF

        // 010800     OPEN    OUTPUT    FD-XPUTF.

        //// 將DB-CLDTL-IDX3指標移至開始
        // 010850     SET   DB-CLDTL-IDX3  TO    BEGINNING.
        // 010900 0000-FINDCLDTL-LOOP.

        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 接收參數-代收類別&代收日>=接收參數-下營業日&代收日<=接收參數-下下營業日 FIND NEXT 收付明細檔，若有誤
        ////  若找不到
        ////    若WK-TOTCNT=0，結束本段落
        ////    若WK-TOTCNT>0，A.執行3000-WXPUTF2-RTN，寫檔FD-XPUTF(彙總 CTL=12)、B.結束本段落
        ////  其他資料庫錯誤，A.顯示錯誤訊息、B.結束本段落
        //// 若接收參數-代收類別="330022"要加挑"710293"的CLDTL
        codeList.add(wkTaskCodeX);
        if ("330022".equals(wkTaskCodeX)) {
            codeList.add("710293");
        }
        // 011000     FIND  NEXT  DB-CLDTL-IDX3  AT DB-CLDTL-CODE = WK-TASK-CODE-X
        // 011100                          AND DB-CLDTL-DATE NOT < WK-TASK-EDATE-N
        // 011200                          AND DB-CLDTL-DATE NOT > WK-TASK-NDATE-N
        List<CldtlbyCodeListRangeEntdyBus> lCldtl =
                cldtlService.findbyCodeListRangeEntdy(
                        codeList, wkTaskEdateN, wkTaskNdateN, 0, 0, Integer.MAX_VALUE);

        // 011300       ON EXCEPTION
        if (Objects.isNull(lCldtl)) {
            // 012300            DISPLAY "THERE IS DB ERROR OCCURS AT FIND CLDTL"
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "THERE IS DB ERROR OCCURS AT FIND CLDTL");
            // 012500            GO TO   0000-MAIN-EXIT.
            return;
        }
        int cnt = 0;
        for (CldtlbyCodeListRangeEntdyBus tCldtl : lCldtl) {
            cnt++;
            // 012520** 保單借款因上次營業日時間晚上十點之後才挑檔（屬本日帳）。
            // 012530     IF  ( WK-TASK-CODE   =   "121961"                )
            // 012540     AND (DB-CLDTL-DATE   =   WK-TASK-EDATE-N         )
            // 012560     AND (DB-CLDTL-TIME   < 220000
            // 012565          AND WK-TASK-BDATE-N = DB-CLDTL-SITDATE      )
            if ("121961".equals(wkTaskCode)
                    && tCldtl.getEntdy() == wkTaskEdateN
                    && tCldtl.getTime() < 220000
                    && wkTaskBdateN == tCldtl.getSitdate()) {
                // 尾筆處理
                if (cnt == lCldtl.size()) {
                    // 012090                 PERFORM 3000-WXPUTF2-RTN   THRU 3000-WXPUTF2-EXIT
                    wxputf2Rtn();
                    // 012100                 GO TO 0000-MAIN-EXIT
                }
                // 012570     GO   TO    0000-FINDCLDTL-LOOP.
                continue;
            }

            //// 執行4000-WXPUTF1-RTN，A.搬DB-CLDTL-...給XPUTF-REC...、B.寫檔FD-XPUTF(明細 CTL=11)
            // 012600     PERFORM     4000-WXPUTF1-RTN     THRU   4000-WXPUTF1-EXIT
            wxputf1Rtn(tCldtl);
            // 012700     ADD         DB-CLDTL-AMT        TO     WK-TOTAMT
            wkTotamt = wkTotamt.add(tCldtl.getAmt());
            // 012800     ADD         1                   TO     WK-TOTCNT
            wkTotcnt = wkTotcnt + 1;

            // 尾筆處理
            if (cnt == lCldtl.size()) {
                // 012090                 PERFORM 3000-WXPUTF2-RTN   THRU 3000-WXPUTF2-EXIT
                wxputf2Rtn();
                // 012100                 GO TO 0000-MAIN-EXIT
            }
            // 012900     GO   TO    0000-FINDCLDTL-LOOP.
        }
        // 013000 0000-MAIN-EXIT.
    }

    private void wxputf1Rtn(CldtlbyCodeListRangeEntdyBus tCldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr wxputf1Rtn");

        // 014400 4000-WXPUTF1-RTN.
        //// 搬DB-CLDTL-...給XPUTF-REC...
        //// 寫檔FD-XPUTF(明細 CTL=11)

        // 014500     MOVE      SPACES            TO      XPUTF-REC.
        // 014600     MOVE      11                TO      XPUTF-CTL.
        xputfCtl = "11";
        // 014700     MOVE      DB-CLDTL-CODE     TO      XPUTF-CODE.
        xputfCode = tCldtl.getCode();
        // 014800     MOVE      DB-CLDTL-RCPTID   TO      XPUTF-RCPTID.
        xputfRcptid = tCldtl.getRcptid();
        // 014900     MOVE      DB-CLDTL-DATE     TO      XPUTF-DATE.
        xputfDate = tCldtl.getEntdy();
        // 015000     MOVE      DB-CLDTL-TIME     TO      XPUTF-TIME.
        xputfTime = tCldtl.getTime();
        // 015100     MOVE      DB-CLDTL-AMT      TO      XPUTF-OLDAMT.
        xputfOldamt = tCldtl.getAmt();
        // 015200     MOVE      DB-CLDTL-CLLBR    TO      XPUTF-CLLBR.
        xputfCllbr = tCldtl.getCllbr();
        // 015300     MOVE      DB-CLDTL-LMTDATE  TO      XPUTF-LMTDATE.
        xputfLmtdate = tCldtl.getLmtdate();
        // 015400     MOVE      DB-CLDTL-USERDATA TO      XPUTF-USERDATA.
        xputfUserdata = tCldtl.getUserdata();
        // 015450     MOVE      DB-CLDTL-SITDATE  TO      XPUTF-SITDATE.
        xputfSitdate = tCldtl.getSitdate();
        // 015460     MOVE      DB-CLDTL-TXTYPE   TO      XPUTF-TXTYPE.
        xputfTxtype = tCldtl.getTxtype();
        // 015480     MOVE      DB-CLDTL-AMT      TO      XPUTF-AMT.
        xputfAmt = tCldtl.getAmt();
        // 015500     WRITE     XPUTF-REC.
        // 002200 01  XPUTF-REC.
        // 002300     03  XPUTF-CTL                         PIC 9(02).
        // 002400     03  XPUTF-CODE                        PIC X(06).
        // 002500     03  XPUTF-DATA.
        // 002600      05 XPUTF-RCPTID                      PIC X(16).
        // 002700      05 XPUTF-DATE                        PIC 9(06).
        // 002800      05 XPUTF-TIME                        PIC 9(06).
        // 002900      05 XPUTF-CLLBR                       PIC 9(03).
        // 003000      05 XPUTF-LMTDATE                     PIC 9(06).
        // 003100      05 XPUTF-OLDAMT                      PIC 9(08).
        // 003200      05 XPUTF-USERDATA                    PIC X(40).
        // 003250      05 XPUTF-SITDATE                     PIC 9(06).
        // 003260      05 XPUTF-TXTYPE                      PIC X(01).
        // 003280      05 XPUTF-AMT                         PIC 9(10).
        // 003300      05 FILLER                           PIC X(10).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(xputfCtl, 2));
        sb.append(formatUtil.padX(xputfCode, 6));
        sb.append(formatUtil.padX(xputfRcptid, 16));
        sb.append(formatUtil.pad9("" + xputfDate, 6));
        sb.append(formatUtil.pad9("" + xputfTime, 6));
        sb.append(formatUtil.pad9("" + xputfCllbr, 3));
        sb.append(formatUtil.pad9("" + xputfLmtdate, 6));
        sb.append(formatUtil.pad9("" + xputfOldamt, 8));
        sb.append(formatUtil.padX(xputfUserdata, 40));
        sb.append(formatUtil.pad9("" + xputfSitdate, 6));
        sb.append(formatUtil.padX(xputfTxtype, 1));
        sb.append(formatUtil.pad9("" + xputfAmt, 10));
        sb.append(formatUtil.padX("", 10));
        fileXPUTFContents.add(sb.toString());

        // 015600 4000-WXPUTF1-EXIT.
    }

    private void wxputf2Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr wxputf2Rtn");
        // 013300 3000-WXPUTF2-RTN.

        //// 搬相關欄位給XPUTF-REC...
        //// 寫檔FD-XPUTF(彙總 CTL=12)
        // 013400     MOVE      SPACES            TO      XPUTF-REC.
        // 013500     MOVE      12                TO      XPUTF-CTL.
        xputfCtl = "12";
        //// 代收類別"710293"為當"330022"時加挑的資料，所以媒體檔寫回"330022"
        // 013550     IF WK-TASK-CODE-X = "710293"
        if ("710293".equals(wkTaskCodeX)) {
            // 013570        MOVE "330022" TO WK-TASK-CODE-X.
            wkTaskCodeX = "330022";
        }
        // 013600     MOVE      WK-TASK-CODE-X    TO      XPUTF-CODE.
        xputfCode = wkTaskCodeX;
        // 013700     MOVE      WK-TASK-BDATE-N   TO      XPUTF-BDATE.
        xputfBdate = wkTaskBdateN;
        // 013800     MOVE      WK-TASK-EDATE-N   TO      XPUTF-EDATE.
        xputfEdate = wkTaskEdateN;
        // 013900     MOVE      WK-TOTCNT         TO      XPUTF-TOTCNT.
        xputfTotcnt = wkTotcnt;
        // 014000     MOVE      WK-TOTAMT         TO      XPUTF-TOTAMT.
        xputfTotamt = wkTotamt;
        // 014100     WRITE     XPUTF-REC.
        // 002200 01  XPUTF-REC.
        // 002300     03  XPUTF-CTL                         PIC 9(02).
        // 002400     03  XPUTF-CODE                        PIC X(06).
        // 002500     03  XPUTF-DATA.
        // 003400     03  XPUTF-DATA-R       REDEFINES  XPUTF-DATA.
        // 003500      05 XPUTF-BDATE                       PIC 9(06).
        // 003600      05 XPUTF-EDATE                       PIC 9(06).
        // 003700      05 XPUTF-TOTCNT                      PIC 9(06).
        // 003800      05 XPUTF-TOTAMT                      PIC 9(13).
        // 003900      05 FILLER                           PIC X(81).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(xputfCtl, 2));
        sb.append(formatUtil.padX(xputfCode, 6));
        sb.append(formatUtil.pad9("" + xputfBdate, 6));
        sb.append(formatUtil.pad9("" + xputfEdate, 6));
        sb.append(formatUtil.pad9("" + xputfTotcnt, 6));
        sb.append(formatUtil.pad9("" + xputfTotamt, 13));
        sb.append(formatUtil.padX("", 81));
        fileXPUTFContents.add(sb.toString());

        // 014200 3000-WXPUTF2-EXIT.
    }

    private void findclmrRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "XPUTFLsnr findclmr5000Rtn");
        // 015800 5000-FINDCLMR-RTN.
        //// WK-RTN挑檔記號(0.挑檔 1.不挑檔)

        //// 依接收之代收類別FIND DB-CLMR-IDX1事業單位基本資料檔，若有誤
        ////  若NOTFOUND：A.顯示錯誤訊息、B.WK-RTN設為1、C.結束本段落
        ////  否則CALL SYSTEM DMTERMINATE，異常，結束程式

        // 015900     FIND      DB-CLMR-IDX1      AT  DB-CLMR-CODE=WK-TASK-CODE-X
        tClmr = clmrService.findById(wkTaskCodeX);
        // 016000      ON EXCEPTION  IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(tClmr)) {
            // 016100                       DISPLAY "FINDCLMR NOT FOUND"
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FINDCLMR NOT FOUND");
            // 016300                       MOVE    1   TO      WK-RTN
            wkRtn = 1;
            // 016400                       GO TO   5000-FINDCLMR-EXIT
            return;
        } else {
            // 016500                    ELSE
            // 016600                       CALL SYSTEM DMTERMINATE.
        }
        tCltmr = cltmrService.findById(tClmr.getCode());
        if (Objects.isNull(tCltmr)) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "cltmr is null");
            wkRtn = 1;
            return;
        }
        tClmc = clmcService.findById(tCltmr.getPutname());
        if (Objects.isNull(tClmc)) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "clmc is null, cltmr.getPutname() = {}",
                    tClmc.getPutname());
            wkRtn = 1;
            return;
        }

        //// WK-RTN設為0
        //
        // 017200       MOVE    0                 TO      WK-RTN.
        wkRtn = 0;
        // 017300 5000-FINDCLMR-EXIT.
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
