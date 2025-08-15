/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV16;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV16Lsnr")
@Scope("prototype")
public class CONV16Lsnr extends BatchListenerCase<CONV16> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    private CONV16 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String REPORT_NAME = "CL-BH-035";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private StringBuilder sb = new StringBuilder();
    private String wkPutdir;
    private String wkGetdir;
    private String reportFilePath;
    private String fd113164Pedi;

    private List<String> fileContents = new ArrayList<>(); //  檔案內容
    private List<String> fileContents_PEDI = new ArrayList<>(); //  檔案內容
    private List<String> reportContents = new ArrayList<>(); //  報表內容

    private Map<String, String> labelMap;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private int processDateInt = 0;
    private int nbsdy;
    private int[] wkCnv9 = new int[10]; // WK-CNV-9類似的陣列
    private int[] wkCnvKg9 = new int[10]; // WK-CNV-KG9類似的陣列
    private String wkHaveData;
    private String wkYYMMDD;
    private String wkFdate;
    private String wkCdate;
    private String wkGdate;
    private String wkPdate;
    private String wkPutfile;
    private String wkConvfile;
    private String wkCnvKgdata;
    private String wkDateP;
    private String wkAmtP;
    private String wkRcptid;
    private String wkRcptid1;
    private String wkCnvData;
    private String wkCnvChkdig;
    private String wkUserdata;
    private String wkTrobank;
    private String wkTromach;
    private String wkCnvRcptid2;
    private String wkCnvRcptid1;
    private String wkCnvRcptid;
    private String wkCnvRcptidP;
    private String wkKinbr;
    private String wkCllbr;
    private String wkBdateP;
    private String wkEdateP;
    private String wkTotcntP;
    private String wkTotamtP;
    private String wkCllbrP;
    private String wkTxtypeP;
    private String wkTxdateP;
    private String wkRcptidP;
    private int wkTempCnt = 0;
    private int wkCnvIdx;
    private int wkCnvTot;
    private String wkCnvTotl;
    private int wkPctl;
    private int wkPage;
    private BigDecimal wkTempAmt = BigDecimal.ZERO;
    private String _113164_Type;
    private String _113164_Actno;
    private String _113164_Entday;
    private String _113164_Crdb;
    private String _113164_Hcode;
    private String _113164_Sign;
    private String _113164_Txamt;
    private String _113164_Code;
    private String _113164_Udata;
    private String _113164_Kinbranch;
    private String _113164_Dscpt;
    private String _113164_Type_Ctl;
    private String _113164_Bank;
    private String _113164_Groupid;
    private String _113164_Trec;
    private String _113164_Sign_Ctl;
    private String _113164_Tamt;
    private String _113164_Pdate;
    private String _113164_Ptime;
    private String pedi_113164_Sndid;
    private String pedi_113164_Recid;
    private String pedi_113164_Bankcd;
    private String pedi_113164_Banknm;
    private int pedi_113164_Sndt;
    private int pedi_113164_Snseq;
    private String pedi_113164_Apfld;
    private int pedi_113164_Procdt;
    private String pedi_113164_Dot;
    private String pedi_113164_Bankcd1;
    private String pedi_113164_Path;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV16 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV16 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr run()");
        init(event);
        //// FD-PUTF檔案存在，往下
        //// 若不存在，GO TO 0000-END-RTN，結束程式
        // 024800     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 024900       NEXT   SENTENCE
        // 025000     ELSE
        // 025100       GO    TO   0000-END-RTN.
        if (!textFile.exists(wkPutdir)) {
            batchResponse();
            return;
        }
        //// WK-GETDIR  <-"DATA/DP/BH/CUSTTAPE/TRADENW/"+WK-GDATE+"."
        //// 設定FD-TRADENW檔名
        // 025200     CHANGE  ATTRIBUTE FILENAME OF FD-TRADENW TO WK-GETDIR.
        wkGetdir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CUSTTAPE"
                        + PATH_SEPARATOR
                        + "TRADENW"
                        + PATH_SEPARATOR
                        + wkGdate;
        //// FD-TRADENW檔案存在
        ////  A.WK-HAVE-DATA設為"Y"(預設為"N")
        ////  B.執行TRADENW-RTN；讀FD-TRADENW，寫FD-113164
        ////  C.執行113164-RTN；讀FD-PUTF，寫FD-113164 & REPORTFL
        ////  D.執行113164-PEDIASEM-RTN；寫FD-113164-PEDI
        //// FD-TRADENW檔案不存在
        ////  A.執行113164-RTN；讀FD-PUTF，寫FD-113164 & REPORTFL
        ////  B.執行113164-PEDIASEM-RTN；寫FD-113164-PEDI
        // 025300     IF  ATTRIBUTE  RESIDENT  OF  FD-TRADENW  IS = VALUE(TRUE)
        if (textFile.exists(wkGetdir)) {
            // 025400         MOVE      "Y"               TO      WK-HAVE-DATA
            wkHaveData = "Y";
            // 025500         PERFORM  TRADENW-RTN      THRU     TRADENW-EXIT
            tradenw();
            // 025600         PERFORM  113164-RTN       THRU     113164-EXIT
            _113164();
            // 025700         PERFORM  113164-PEDIASEM-RTN THRU 113164-PEDIASEM-EXIT
            _113164_Pediasem();
        } else {
            // 025800     ELSE
            // 025900        PERFORM    113164-RTN       THRU     113164-EXIT
            _113164();
            // 026000        PERFORM 113164-PEDIASEM-RTN THRU 113164-PEDIASEM-EXIT.
            _113164_Pediasem();
        }

        checkPath();

        batchResponse();
    }

    private void init(CONV16 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        //// 設定本營業日、檔名日期變數值
        //// WK-YYMMDD 2位的民國年
        //// WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        //// WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        //// WK-GDATE PIC 9(06) <-WK-GETDIR'S變數
        // 023800     MOVE    FD-BHDATE-TBSDY    TO    WK-YYMMDD.
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        processDateInt = parse.string2Integer(processDate);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY")); // 待中菲APPLE提供正確名稱
        // 023900     MOVE        WK-YYMMDD      TO    WK-FDATE,WK-CDATE,WK-GDATE.
        wkYYMMDD = formatUtil.pad9(processDate, 7).substring(1, 7);
        wkFdate = wkYYMMDD;
        wkCdate = wkYYMMDD;
        wkGdate = wkYYMMDD;
        //// CALL "DATE_TIME OF DTLIB"

        // 024000     CHANGE ATTRIBUTE TITLE OF "DTLIB" TO "*SYSTEM1/DTLIB.".
        // 024100     CALL "DATE_TIME OF DTLIB" USING PARA-DTREC
        // 024101     END-CALL

        //// PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        // 024200     MOVE        PARA-YYMMDD         TO   WK-PDATE.
        wkPdate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7).substring(1, 7);

        //// 設定檔名變數值
        //// WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        //// WK-CONVFILE PIC X(10) <--WK-CONVDIR'S變數
        // 024400     MOVE        "27Y1113164"        TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = "27Y1113164";
        wkConvfile = "27Y1113164";
        //// 設定計算檢查碼之權數
        // 024500     MOVE        "567891234"         TO   WK-CNV-KGDATA.
        wkCnvKgdata = "567891234";
        for (int i = 0; i < wkCnvKgdata.length(); i++) {
            wkCnvKg9[i] = wkCnvKgdata.charAt(i);
        }
        //// 設定檔名
        // 024600     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        //// WK-PUTDIR  <-"DATA/CL/BH/PUTF/"+WK-FDATE+"/27Y1113164."
        // 024700     CHANGE  ATTRIBUTE FILENAME OF FD-113164 TO WK-CONVDIR.
        //// WK-CONVDIR <-"DATA/CL/BH/CONVF/"+WK-CDATE+"/27Y1113164."
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(wkPutdir);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        // 006700 FD  FD-113164-PEDI
        //        006800     RECORD       CONTAINS     166      CHARACTERS
        //        006900     BLOCK        CONTAINS     10       RECORDS
        //        007000     VALUE  OF  FILENAME  IS   "DATA/DP/BH/CUSTTAPE/TRADENW/HDR"
        fd113164Pedi =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CUSTTAPE"
                        + PATH_SEPARATOR
                        + "TRADENW"
                        + PATH_SEPARATOR
                        + "HDR";
        reportFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME;
    }

    private void _113164_Pediasem() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr _113164_Pediasem");
        // 042200 113164-PEDIASEM-RTN.
        //// 開啟輸出檔FD-113164-PEDI

        // 042300     OPEN    OUTPUT    FD-113164-PEDI.
        //// 寫檔FD-113164-PEDI(FIRST RECORD)
        // 042400     MOVE "* FOR TRADENW CUSTTAPE "   TO PEDI-113164-REC.
        // 042500     WRITE   PEDI-113164-REC.
        fileContents_PEDI.add("* FOR TRADENW CUSTTAPE ");

        //// 搬相關資料到PEDI-113164-REC...
        // 042600     MOVE  SPACES                     TO PEDI-113164-REC.
        // 042700     MOVE  SPACES                     TO PEDI-113164-SNDID.
        // 042800     MOVE "TVCBBATWTPE00055"          TO PEDI-113164-SNDID.
        // 042900     MOVE  SPACES                     TO PEDI-113164-RECID.
        // 043000     MOVE "TVCBTVTWTPE00002-FTP-0001" TO PEDI-113164-RECID.
        // 043100     MOVE "004"                       TO PEDI-113164-BANKCD.
        // 043200     MOVE "BOT"                       TO PEDI-113164-BANKNM.
        // 043300     MOVE FD-BHDATE-NBSDY             TO PEDI-113164-SNDT.
        // 043400     MOVE 01                          TO PEDI-113164-SNSEQ.
        // 043500     MOVE "T0001 "                    TO PEDI-113164-APFLD.
        // 043600     MOVE FD-BHDATE-TBSDY             TO PEDI-113164-PROCDT.
        // 043700     MOVE "."                         TO PEDI-113164-DOT.
        // 043800     MOVE "004"                       TO PEDI-113164-BANKCD1.
        // 043900     MOVE "C:TRADENW.DAT"             TO PEDI-113164-PATH.
        pedi_113164_Sndid = "TVCBBATWTPE00055";
        pedi_113164_Recid = "TVCBTVTWTPE00002-FTP-0001";
        pedi_113164_Bankcd = "004";
        pedi_113164_Banknm = "BOT";
        pedi_113164_Sndt = nbsdy;
        pedi_113164_Snseq = 1;
        pedi_113164_Apfld = "T0001";
        pedi_113164_Procdt = processDateInt;
        pedi_113164_Dot = ".";
        pedi_113164_Bankcd1 = "004";
        pedi_113164_Path = "C:TRADENW.DAT";

        //// 寫檔FD-113164-PEDI(SECOND RECORD)
        // 044000     WRITE PEDI-113164-REC.
        fileContents_PEDI.add(pedi_113164_Rec());
        //// 寫檔FD-113164-PEDI(LAST RECORD)

        // 044100     MOVE SPACES TO PEDI-113164-REC.
        // 044200     WRITE PEDI-113164-REC.
        fileContents_PEDI.add("");

        //// 關閉檔案
        // 044300     CLOSE FD-113164-PEDI WITH SAVE.
        try {
            textFile.writeFileContent(fd113164Pedi, fileContents_PEDI, CHARSET_BIG5);
            upload(fd113164Pedi, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 044400 113164-PEDIASEM-EXIT.
    }

    private String pedi_113164_Rec() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr pedi_113164_Rec");
        // 007200 01      PEDI-113164-REC.
        // 007300     03  PEDI-113164-SNDID                 PIC X(39).
        // 007400     03  PEDI-113164-RECID                 PIC X(39).
        // 007500     03  PEDI-113164-CTLNO.
        // 007600      05 PEDI-113164-BANKCD                PIC X(03).
        // 007700      05 PEDI-113164-BANKNM                PIC X(03).
        // 007800      05 PEDI-113164-SNDT                  PIC 9(06).
        // 007900      05 PEDI-113164-SNSEQ                 PIC 9(02).
        // 008000     03  PEDI-113164-APFLD                 PIC X(06).
        // 008100     03  PEDI-113164-COMMENT.
        // 008200      05 PEDI-113164-PROCDT                PIC 9(06).
        // 008300      05 PEDI-113164-DOT                   PIC X(01).
        // 008400      05 PEDI-113164-BANKCD1               PIC X(03).
        // 008500      05 FILLER                            PIC X(10).
        // 008600     03  PEDI-113164-PATH                  PIC X(48).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(pedi_113164_Sndid, 39));
        sb.append(formatUtil.padX(pedi_113164_Recid, 39));
        sb.append(formatUtil.padX(pedi_113164_Bankcd, 3));
        sb.append(formatUtil.padX(pedi_113164_Banknm, 3));
        sb.append(formatUtil.pad9("" + pedi_113164_Sndt, 6));
        sb.append(formatUtil.pad9("" + pedi_113164_Snseq, 2));
        sb.append(formatUtil.padX(pedi_113164_Apfld, 6));
        sb.append(formatUtil.pad9("" + pedi_113164_Procdt, 6));
        sb.append(formatUtil.padX(pedi_113164_Dot, 1));
        sb.append(formatUtil.padX(pedi_113164_Bankcd1, 3));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(pedi_113164_Path, 48));
        return sb.toString();
    }

    private void tradenw() { // todo: 不需要
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr tradenw");
        // 036300 TRADENW-RTN.
        //// 讀FD-TRADENW，挑WK-TRADENW-CODE<>"1316"資料，寫FD-113164
        //// 開啟輸入檔FD-TRADENW、開啟輸出檔FD-113164
        // 036400     OPEN      OUTPUT    FD-113164.
        // 036500     OPEN      INPUT     FD-TRADENW.
        // 036600 TRADENW-NEXT.
    }

    private void _113164() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr _113164");
        // 026700 113164-RTN.
        //// 讀FD-PUTF，挑PUTF-CTL=11資料，寫FD-113164 & REPORTFL
        //// 若FD-TRADENW檔案不存在，開啟輸出檔FD-113164
        //// (若FD-TRADENW檔案存在，會在TRADENW-RTN開啟輸出檔FD-113164)
        // 026800     IF        WK-HAVE-DATA      =       "N"
        // 026900       OPEN    OUTPUT    FD-113164.
        //// 開啟輸入檔FD-PUTF、開啟輸出檔REPORTFL
        // 027000     OPEN      INPUT     FD-PUTF.
        // 027100     OPEN      OUTPUT    REPORTFL.
        //// WK-PAGE,WK-PCTL清0
        // 027200     MOVE      0    TO   WK-PAGE,WK-PCTL.
        wkPage = 0;
        wkPctl = 0;
        //// 執行113164-WTIT-RTN，寫REPORTFL表頭
        // 027300     PERFORM   113164-WTIT-RTN   THRU   113164-WTIT-EXIT.
        _113164_Wtit();
        // 027400***  DETAIL  RECORD  *****
        // 027500 113164-NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到113164-CLOSE
        // 027600     READ   FD-PUTF    AT  END  GO TO  113164-CLOSE.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            //// 挑 PUTF-CTL=11明細資料
            //// PUTF-CTL<>11，GO TO 113164-NEXT，LOOP讀下一筆FD-PUTF
            //
            // 027700     IF        PUTF-CTL       NOT =      11
            if (!"11".equals(filePutf.getCtl())) {
                // 027800       GO TO   113164-NEXT.
                continue;
            }
            //// WK-PCTL行數控制加1
            // 027900     ADD       1               TO     WK-PCTL.
            wkPctl = wkPctl + 1;
            //// WK-PCTL行數控制>50時，A.搬1給WK-PCTL，B.執行113164-WTIT-RTN，寫REPORTFL表頭
            // 028000     IF        WK-PCTL         >      50
            if (wkPctl > 50) {
                // 028100       MOVE    1               TO     WK-PCTL
                wkPctl = 1;
                // 028200       PERFORM 113164-WTIT-RTN THRU   113164-WTIT-EXIT.
                _113164_Wtit();
            }
            //// 搬相關資料到113164-REC... & WK-DETAIL-LINE...
            // 028300     MOVE      SPACES          TO     113164-REC.
            // 028400     MOVE      "D"             TO     113164-TYPE.
            // 028500     MOVE      "108001006969"  TO     113164-ACTNO.
            // 028600     MOVE      PUTF-DATE       TO     113164-ENTDAY,
            // 028700                                      WK-DATE-P.
            // 028800     MOVE      "1"             TO     113164-CRDB.
            // 028900     MOVE      "0"             TO     113164-HCODE.
            // 029000     MOVE      "+"             TO     113164-SIGN.
            // 029100     MOVE      PUTF-AMT        TO     113164-TXAMT,
            // 029200                                      WK-AMT-P.
            // 029300     MOVE      "1001"          TO     113164-CODE.
            _113164_Type = "D";
            _113164_Actno = "108001006969";
            _113164_Entday = filePutf.getEntdy();
            wkDateP = filePutf.getEntdy();
            _113164_Crdb = "1";
            _113164_Hcode = "0";
            _113164_Sign = "+";
            _113164_Txamt = filePutf.getAmt();
            wkAmtP = filePutf.getAmt();
            _113164_Code = "1001";

            //// 執行113164-CNV-RTN，計算檢查碼
            // 029400     PERFORM   113164-CNV-RTN  THRU   113164-CNV-EXIT.
            _113164_Cnv();
            // 029500     MOVE      WK-CNV-CHKDIG   TO     WK-CNV-RCPTID2.
            // 029600     MOVE      WK-RCPTID1      TO     WK-CNV-RCPTID1.
            wkCnvRcptid2 = wkCnvChkdig;
            wkCnvRcptid1 = formatUtil.padX(wkRcptid1, 9);
            wkCnvRcptid = wkCnvRcptid1 + wkCnvRcptid2;
            //
            //// WK-CNV-RCPTID帳單編號=PUTF-RCPTID(5:9)+WK-CNV-CHKDIG
            //
            // 029700     MOVE      WK-CNV-RCPTID   TO     113164-UDATA,
            // 029800                                      WK-CNV-RCPTID-P.
            _113164_Udata = wkCnvRcptid;
            wkCnvRcptidP = wkCnvRcptid;
            // 029900     MOVE      PUTF-CLLBR      TO     WK-KINBR.
            wkKinbr = filePutf.getCllbr();
            //// WK-CLLBR="004"+PUTF-CLLBR+" "
            wkCllbr = "004" + wkKinbr;
            // 030000     MOVE      WK-CLLBR        TO     113164-KINBRANCH,
            // 030100                                      WK-CLLBR-P.
            _113164_Kinbranch = wkCllbr;
            wkCllbrP = wkCllbr;

            //// 執行113164-DSCPT-RTN，轉換 繳款方式
            // 030200     PERFORM   113164-DSCPT-RTN  THRU  113164-DSCPT-EXIT.
            _113164_Dscpt_Rtn();
            // 030300     MOVE      113164-DSCPT    TO     WK-TXTYPE-P.
            // 030400     MOVE      PUTF-SITDATE    TO     WK-TXDATE-P.
            // 030500     MOVE      PUTF-RCPTID     TO     WK-RCPTID-P.
            wkTxtypeP = _113164_Dscpt;
            wkTxdateP = filePutf.getSitdate();
            wkRcptidP = filePutf.getRcptid();

            //// 寫檔FD-113164(明細)
            // 030600     WRITE     113164-REC.
            fileContents.add(_113164_Rec("1"));

            //// 累計筆數、金額
            // 030700     ADD       1               TO     WK-TEMP-CNT.
            // 030800     ADD       PUTF-AMT        TO     WK-TEMP-AMT.
            wkTempCnt = wkTempCnt + 1;
            wkTempAmt =
                    wkTempAmt.add(
                            parse.string2BigDecimal(
                                    filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt()));

            //// 寫REPORTFL報表明細(WK-DETAIL-LINE)
            // 030900     MOVE      SPACES          TO     REPORT-LINE.
            // 031000     WRITE     REPORT-LINE    FROM    WK-DETAIL-LINE.
            reportContents.add(wkDetail());
            //// LOOP讀下一筆FD-PUTF
            // 031100     GO TO     113164-NEXT.
        }
        // 031200 113164-CLOSE.
        //
        //// 搬相關資料到113164-REC...
        //
        // 031300     MOVE      SPACES              TO    113164-REC.
        // 031400     MOVE      "C"                 TO    113164-TYPE-CTL.
        // 031500     MOVE      "004"               TO    113164-BANK.
        // 031600     MOVE      "004108"            TO    113164-GROUPID.
        // 031700     MOVE      WK-TEMP-CNT         TO    113164-TREC.
        // 031800     MOVE      "+"                 TO    113164-SIGN-CTL.
        // 031900     MOVE      WK-TEMP-AMT         TO    113164-TAMT.
        // 032000     MOVE      WK-YYMMDD           TO    113164-PDATE.
        // 032100     MOVE      "000000"            TO    113164-PTIME.
        _113164_Type_Ctl = "C";
        _113164_Bank = "004";
        _113164_Groupid = "004108";
        _113164_Trec = "" + wkTempCnt;
        _113164_Sign_Ctl = "+";
        _113164_Tamt = "" + wkTempAmt;
        _113164_Pdate = wkYYMMDD;
        _113164_Ptime = "000000";

        //// 寫檔FD-113164(LAST RECORD)
        // 032200     WRITE     113164-REC.
        fileContents.add(_113164_Rec("2"));
        //// 寫REPORTFL報表表尾(WK-TOT-LINE1~WK-TOT-LINE2)
        // 032300     MOVE      PUTF-BDATE          TO    WK-BDATE-P.
        wkBdateP = fileSumPUTF.getBdate();
        // 032400     MOVE      PUTF-EDATE          TO    WK-EDATE-P.
        wkEdateP = fileSumPUTF.getEdate();

        // 032500     MOVE      SPACES              TO    REPORT-LINE.
        // 032600     WRITE     REPORT-LINE         AFTER  1 LINE.
        reportContents.add("");
        // 032700     WRITE     REPORT-LINE         FROM  WK-TOT-LINE1.
        reportContents.add(wk_Tot_Line1());

        // 032800     MOVE      PUTF-TOTCNT         TO    WK-TOTCNT-P.
        wkTotcntP = fileSumPUTF.getTotcnt();
        // 032900     MOVE      PUTF-TOTAMT         TO    WK-TOTAMT-P.
        wkTotamtP = fileSumPUTF.getTotamt();
        // 033000     MOVE      SPACES              TO    REPORT-LINE.
        // 033100     WRITE     REPORT-LINE         AFTER  1 LINE.
        reportContents.add("");
        // 033200     WRITE     REPORT-LINE         FROM  WK-TOT-LINE2.
        reportContents.add(wk_Tot_Line2());

        //// 關閉檔案
        // 033300     CLOSE     FD-PUTF    WITH  SAVE.
        // 033400     IF        WK-HAVE-DATA    =   "N"
        //        if ("N".equals(wkHaveData)) {
        //            // 033500       CHANGE ATTRIBUTE FILENAME OF FD-113164 TO WK-PUTDIR
        //            // 033600       CLOSE  FD-113164  WITH  SAVE
        //            textFile.deleteFile(wkPutdir);
        //            try {
        //                textFile.writeFileContent(wkPutdir, fileContents, CHARSET);
        //            } catch (LogicException e) {
        //                moveErrorResponse(e);
        //            }
        //        } else {
        //            // 033700     ELSE
        //            // 033800       CHANGE ATTRIBUTE FILENAME OF FD-113164 TO WK-GETDIR
        //            // 033900       CLOSE  FD-113164  WITH  SAVE.
        //            try {
        //                textFile.writeFileContent(wkGetdir, fileContents, CHARSET);
        //            } catch (LogicException e) {
        //                moveErrorResponse(e);
        //            }
        //        }
        // 034000     CLOSE  REPORTFL   WITH  SAVE.
        try {
            textFile.writeFileContent(reportFilePath, reportContents, CHARSET_BIG5);
            upload(reportFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 034100 113164-EXIT.
    }

    private void _113164_Wtit() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr _113164_Wtit");
        // 034400 113164-WTIT-RTN.
        //// 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE4)

        // 034500     ADD        1                   TO     WK-PAGE.
        wkPage = wkPage + 1;
        // 034600     MOVE       SPACES              TO     REPORT-LINE.
        // 034700     WRITE      REPORT-LINE         AFTER  PAGE.
        reportContents.add(PAGE_SEPARATOR);

        // 034800     MOVE       SPACES              TO     REPORT-LINE.
        // 034900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        reportContents.add(wk_Title_Line1());

        // 035000     MOVE       SPACES              TO     REPORT-LINE.
        // 035100     WRITE      REPORT-LINE         AFTER  1 LINE.
        reportContents.add("");
        // 035200     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        reportContents.add(wk_Title_Line2());

        // 035300     MOVE       SPACES              TO     REPORT-LINE.
        // 035400     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        reportContents.add(wk_Title_Line3());

        // 035500     MOVE       SPACES              TO     REPORT-LINE.
        // 035600     WRITE      REPORT-LINE         AFTER  1 LINE.
        reportContents.add("");
        // 035700     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        reportContents.add(wk_Title_Line4());

        // 035800     MOVE       SPACES              TO     REPORT-LINE.
        // 035900     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        reportContents.add(wk_Gate_Line());

        // 036000 113164-WTIT-EXIT.
    }

    private void _113164_Cnv() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr _113164_Cnv");
        // 038600 113164-CNV-RTN.
        //// 計算檢查碼
        //// WK-CNV-CHKDIG=10-( (PUTF-RCPTID(5:9) 與 "567891234" 逐一欄位相乘合計)+5，取個位數)

        // 038700     MOVE   PUTF-RCPTID         TO      WK-RCPTID.
        // 038800     MOVE   WK-RCPTID1          TO      WK-CNV-DATA.
        // 038900     MOVE   1                   TO      WK-CNV-IDX.
        // 039000     MOVE   5                   TO      WK-CNV-TOT.
        wkRcptid = filePutf.getRcptid();
        // 010200 01 WK-RCPTID.
        // 010300  03 WK-VRCODE                         PIC X(04).
        // 010400  03 WK-RCPTID1                        PIC X(09).
        // 010500  03 WK-RCPTID2                        PIC X(03).
        wkRcptid1 = wkRcptid.substring(4, 13);
        wkCnvData = wkRcptid1;
        for (int i = 0; i < wkCnvData.length(); i++) {
            wkCnv9[i] = (int) wkCnvData.charAt(i);
        }
        wkCnvIdx = 1;
        wkCnvTot = 5;
        // 039100 113164-CNV-LOOP.
        // 039200     COMPUTE   WK-CNV-TOT  =  WK-CNV-TOT + WK-CNV-9(WK-CNV-IDX)
        // 039300                                      *  WK-CNV-KG9(WK-CNV-IDX).
        // 039400     ADD       1            TO  WK-CNV-IDX.
        // 039500     IF        WK-CNV-IDX    =  10
        // 039600       COMPUTE  WK-CNV-CHKDIG  =  10   -  WK-CNV-TOTL
        // 039700       GO  TO  113164-CNV-EXIT.
        // 039800     GO   TO  113164-CNV-LOOP.
        while (true) {
            // 計算總和
            wkCnvTot = wkCnvTot + (wkCnv9[wkCnvIdx] * wkCnvKg9[wkCnvIdx]);
            wkCnvTotl = formatUtil.pad9("" + wkCnvTot, 3).substring(2, 3);
            wkCnvIdx++;
            if (wkCnvIdx == 10) {
                wkCnvChkdig = "" + (10 - parse.string2Integer(wkCnvTotl));
                break; // 跳出循環
            }
        }
        // 039900 113164-CNV-EXIT.
    }

    private void _113164_Dscpt_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV16Lsnr _113164_Dscpt_Rtn");
        // 040200 113164-DSCPT-RTN.

        //// 轉換 繳款方式
        // 040300   IF       PUTF-TXTYPE         =       "C"
        if ("C".equals(filePutf.getTxtype())) {
            // 040400     MOVE   "C"                 TO      113164-DSCPT
            _113164_Dscpt = "C";
            // 040500     GO     TO    113164-DSCPT-EXIT.
            return;
        }
        // 040600   IF       PUTF-TXTYPE         =       "M"
        if ("M".equals(filePutf.getTxtype())) {
            // 040700     MOVE   "M"                 TO      113164-DSCPT
            _113164_Dscpt = "M";
            // 040800      GO     TO    113164-DSCPT-EXIT.
            return;
        }
        // 040900   IF       PUTF-TXTYPE         =       "R"
        if ("R".equals(filePutf.getTxtype())) {
            // 041000     MOVE   "MR"                TO      113164-DSCPT
            _113164_Dscpt = "MR";
            // 041100      GO     TO    113164-DSCPT-EXIT.
            return;
        }
        // 041200   IF       PUTF-TXTYPE         =       "A"  OR  = "X"
        if ("A".equals(filePutf.getTxtype())) {
            // 041300     MOVE   PUTF-USERDATA       TO      WK-USERDATA
            wkUserdata = filePutf.getUserdata();
            wkTrobank = wkUserdata.substring(0, 3);
            wkTromach = wkUserdata.substring(19, 22);
        }

        //// PUTF-USERDATA(1:3)="004" & PUTF-USERDATA(1:3)=PUTF-USERDATA(20:3)
        // 041400     IF     WK-TROBANK          =       "004"
        // 041500       AND  WK-TROBANK          =       WK-TROMACH
        if ("004".equals(wkTrobank) && wkTrobank.equals(wkTromach)) {
            // 041600        MOVE   "MA"             TO      113164-DSCPT
            _113164_Dscpt = "MA";
            // 041700        GO     TO    113164-DSCPT-EXIT.
            return;
        }
        // 041800   MOVE     "MC"                TO      113164-DSCPT.
        _113164_Dscpt = "MC";
        // 041900 113164-DSCPT-EXIT.
    }

    private String wk_Title_Line1() {
        // 016000 01 WK-TITLE-LINE1.
        // 016100    02 FILLER                          PIC X(16) VALUE SPACE.
        // 016200    02 FILLER                          PIC X(36) VALUE
        // 016300       " 台灣銀行代收關貿網路公司交易明細表 ".
        // 016400    02 FILLER                          PIC X(17) VALUE SPACE.
        // 016500    02 FILLER                          PIC X(12) VALUE
        // 016600       "FORM : C035 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 16));
        sb.append(formatUtil.padX(" 台灣銀行代收關貿網路公司交易明細表 ", 36));
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.padX("FORM : C035 ", 12));
        return sb.toString();
    }

    private String wk_Title_Line2() {
        // 016700 01 WK-TITLE-LINE2.
        // 016800    02 FILLER                          PIC X(10) VALUE
        // 016900       " 分行別： ".
        // 017000    02 WK-PBRNO-P                      PIC X(03) VALUE "108".
        // 017100    02 FILLER                          PIC X(55) VALUE SPACE.
        // 017200    02 FILLER                          PIC X(08) VALUE
        // 017300       " 頁次  :".
        // 017400    02 WK-PAGE                         PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.padX("108", 3));
        sb.append(formatUtil.padX("", 55));
        sb.append(formatUtil.padX(" 頁次  :", 8));
        sb.append(formatUtil.pad9("" + wkPage, 4));
        return sb.toString();
    }

    private String wk_Title_Line3() {
        // 017500 01 WK-TITLE-LINE3.
        // 017600    02 FILLER                          PIC X(13) VALUE
        // 017700       " 印表日期：  ".
        // 017800    02 WK-PDATE                        PIC 99/99/99.
        // 017900    02 FILLER                          PIC X(35) VALUE SPACE.
        // 018000    02 FILLER                          PIC X(12) VALUE
        // 018100       " 入帳帳號： ".
        // 018200    02 WK-ACTNO-P                      PIC X(12) VALUE
        // 018300                                                 "108001006969".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 印表日期：  ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "99/99/99"));
        sb.append(formatUtil.padX("", 35));
        sb.append(formatUtil.padX(" 入帳帳號： ", 12));
        sb.append(formatUtil.padX("108001006969", 12));
        return sb.toString();
    }

    private String wk_Title_Line4() {
        // 018400 01 WK-TITLE-LINE4.
        // 018500    02 FILLER              PIC X(08) VALUE " 入帳日 ".
        // 018600    02 FILLER              PIC X(02) VALUE SPACE.
        // 018700    02 FILLER              PIC X(10) VALUE " 帳單編號 ".
        // 018800    02 FILLER              PIC X(02) VALUE SPACE.
        // 018900    02 FILLER              PIC X(10) VALUE " 繳款方式 ".
        // 019000    02 FILLER              PIC X(02) VALUE SPACE.
        // 019100    02 FILLER              PIC X(10) VALUE " 繳費金額 ".
        // 019200    02 FILLER              PIC X(02) VALUE SPACE.
        // 019300    02 FILLER              PIC X(08) VALUE " 代收行 ".
        // 019400    02 FILLER              PIC X(02) VALUE SPACE.
        // 019500    02 FILLER              PIC X(08) VALUE " 繳款日 ".
        // 019600    02 FILLER              PIC X(02) VALUE SPACE.
        // 019700    02 FILLER              PIC X(14) VALUE " 台銀銷帳編號 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 入帳日 ", 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 帳單編號 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 繳款方式 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 繳費金額 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 代收行 ", 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 繳款日 ", 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 台銀銷帳編號 ", 14));
        return sb.toString();
    }

    private String wk_Gate_Line() {
        // 021200 01 WK-GATE-LINE.
        // 021300    02 FILLER                   PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        return sb.toString();
    }

    private String wkDetail() {
        // 019800 01 WK-DETAIL-LINE.
        // 019900    02 WK-DATE-P                PIC 99/99/99.
        // 020000    02 FILLER                   PIC X(02) VALUE SPACES.
        // 020100    02 WK-CNV-RCPTID-P          PIC 9(10).
        // 020200    02 FILLER                   PIC X(03) VALUE SPACES.
        // 020300    02 WK-TXTYPE-P              PIC X(05).
        // 020400    02 FILLER                   PIC X(02) VALUE SPACES.
        // 020500    02 WK-AMT-P                 PIC Z,ZZZ,ZZZ,ZZ9.
        // 020600    02 FILLER                   PIC X(04) VALUE SPACES.
        // 020700    02 WK-CLLBR-P               PIC X(07).
        // 020800    02 FILLER                   PIC X(02) VALUE SPACES.
        // 020900    02 WK-TXDATE-P              PIC 99/99/99.
        // 021000    02 FILLER                   PIC X(02) VALUE SPACES.
        // 021100    02 WK-RCPTID-P              PIC X(14).
        sb = new StringBuilder();
        sb.append(reportUtil.customFormat(wkDateP, "99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.pad9(wkCnvRcptidP, 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(wkTxtypeP, 5));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat(wkAmtP, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(wkCllbrP, 7));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat(wkTxdateP, "99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkRcptidP, 14));
        return sb.toString();
    }

    private String _113164_Rec(String fg) {
        sb = new StringBuilder();
        switch (fg) {
            case "1":
                // 004400   03  113164-DETAIL.
                // 004500     05  113164-TYPE                    PIC X(01).
                // 004600     05  113164-ACTNO                   PIC X(12).
                // 004700     05  113164-ENTDAY                  PIC 9(06).
                // 004800     05  113164-CRDB                    PIC 9(01).
                // 004900     05  113164-HCODE                   PIC 9(01).
                // 005000     05  113164-SIGN                    PIC X(01).
                // 005100     05  113164-TXAMT                   PIC 9(11)V99.
                // 005200     05  113164-CODE                    PIC X(04).
                // 005300     05  113164-UDATA                   PIC X(60).
                // 005400     05  113164-KINBRANCH               PIC X(07).
                // 005500     05  113164-DSCPT                   PIC X(05).
                // 005600     05  FILLER                         PIC X(69).
                sb.append(formatUtil.padX(_113164_Type, 1));
                sb.append(formatUtil.padX(_113164_Actno, 12));
                sb.append(formatUtil.pad9(_113164_Entday, 6));
                sb.append(formatUtil.pad9(_113164_Crdb, 1));
                sb.append(formatUtil.pad9(_113164_Hcode, 1));
                sb.append(formatUtil.padX(_113164_Sign, 1));
                sb.append(formatUtil.pad9(_113164_Txamt, 11) + "00");
                sb.append(formatUtil.padX(_113164_Code, 4));
                sb.append(formatUtil.padX(_113164_Udata, 60));
                sb.append(formatUtil.padX(_113164_Kinbranch, 7));
                sb.append(formatUtil.padX(_113164_Dscpt, 5));
                sb.append(formatUtil.padX("", 69));
                break;
            case "2":
                // 005700   03  113164-CTL   REDEFINES  113164-DETAIL.
                // 005800     05  113164-TYPE-CTL                PIC X(01).
                // 005900     05  113164-BANK                    PIC 9(03).
                // 006000     05  113164-GROUPID                 PIC X(07).
                // 006100     05  113164-TREC                    PIC 9(06).
                // 006200     05  113164-SIGN-CTL                PIC X(01).
                // 006300     05  113164-TAMT                    PIC 9(11)V99.
                // 006400     05  113164-PDATE                   PIC 9(06).
                // 006500     05  113164-PTIME                   PIC 9(06).
                // 006600     05  FILLER                         PIC X(137).
                sb.append(formatUtil.padX(_113164_Type_Ctl, 1));
                sb.append(formatUtil.pad9(_113164_Bank, 3));
                sb.append(formatUtil.padX(_113164_Groupid, 7));
                sb.append(formatUtil.pad9(_113164_Trec, 6));
                sb.append(formatUtil.padX(_113164_Sign_Ctl, 1));
                sb.append(formatUtil.pad9(_113164_Tamt, 11) + "00");
                sb.append(formatUtil.pad9(_113164_Pdate, 6));
                sb.append(formatUtil.pad9(_113164_Ptime, 6));
                sb.append(formatUtil.pad9("", 137));
                break;
        }
        return sb.toString();
    }

    private String wk_Tot_Line1() {
        // 021400 01 WK-TOT-LINE1.
        // 021500    02 FILLER                   PIC X(13) VALUE " 資料起日  : ".
        // 021600    02 WK-BDATE-P               PIC 99/99/99.
        // 021700    02 FILLER                   PIC X(06) VALUE SPACES.
        // 021800    02 FILLER                   PIC X(13) VALUE " 資料迄日  : ".
        // 021900    02 WK-EDATE-P               PIC 99/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 資料起日  : ", 13));
        sb.append(reportUtil.customFormat(wkBdateP, "99/99/99"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 資料迄日  : ", 13));
        sb.append(reportUtil.customFormat(wkEdateP, "99/99/99"));
        return sb.toString();
    }

    private String wk_Tot_Line2() {
        // 022000 01 WK-TOT-LINE2.
        // 022100    02 FILLER                   PIC X(13) VALUE " 代收筆數  : ".
        // 022200    02 WK-TOTCNT-P              PIC ZZZ,ZZ9.B.
        // 022300    02 FILLER                   PIC X(05) VALUE SPACES.
        // 022400    02 FILLER                   PIC X(13) VALUE " 代收金額  : ".
        // 022500    02 WK-TOTAMT-P              PIC ZZ,ZZZ,ZZZ,ZZ9.B.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收筆數  : ", 13));
        sb.append(reportUtil.customFormat(wkTotcntP, "ZZZ,ZZ9.9"));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 代收金額  : ", 13));
        sb.append(reportUtil.customFormat(wkTotamtP, "ZZ,ZZZ,ZZZ,ZZ9.9"));
        return sb.toString();
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
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

    private void checkPath() {
        if (textFile.exists(wkPutdir)) {
            upload(wkPutdir, "", "");
            forFsap();
            forFsapPedi();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        "27Y1113164", // 來源檔案名稱(20碼長)
                        "27Y1113164", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV16", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapPedi() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        "27Y1113164", // 來源檔案名稱(20碼長)
                        "HDR", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV16_PEDI", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT PEDI = " + result);
    }

    private File downloadFromSftp(String fileFtpPath, String tarDir) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath, tarDir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            return null;
        }
        return file;
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
