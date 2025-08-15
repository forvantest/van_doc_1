/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV11;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
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
@Component("CONV11Lsnr")
@Scope("prototype")
public class CONV11Lsnr extends BatchListenerCase<CONV11> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV11 event;
    @Autowired private DateUtil dateutil;
    @Autowired private ClmrService clmrService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    // Define
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_27Z1112034 = "27Z1112034."; // 讀檔檔名
    private static final String FILE_INPUT_NAME_27Z1112044 = "27Z1112044."; // 讀檔檔名
    private static final String STRING_012 = "012";
    private static final String STRING_067 = "067";
    private static final String STRING_112034 = "112034";
    private static final String STRING_112044 = "112044";
    private static final String PATH_DOT = ".";
    private static final String STRING_Y = "Y";
    private static final String STRING_N = "N";
    private static final String STRING_001 = "001";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String STRING_031 = "CL-BH-031-001";

    private String wkFileNameList = "";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final int INT_19110000 = 19110000;
    private final List<String> fileCONV11Contents = new ArrayList<>();
    private final List<String> file112034Contents = new ArrayList<>();
    private StringBuilder sb = new StringBuilder();
    private String wkPutdir; // 產檔路徑
    private ClmrBus tClmr = new ClmrBus();

    // ---------- WK int    (9) ----------
    private String wkCdate;
    private int wkDate;
    private String wkFdate;
    private int wkYYMMDD;
    private int wkPage;
    private int wkPctl;
    private int wkSitdate;
    private int wkTrndateP;

    // ---------- WK String (X) ----------
    private String wkPutfile;
    private String wkConvfile;
    private String wkPbrnoP;
    private String wkAtmcodeP;
    private String wkChtnoP;
    private String wkTxntimeP;
    private String wkTrobankP;
    private String wkTroaccnoP;
    private String wkTxnseqP;
    private String wkTxnseqSelf;
    private String wkBdbrno;
    private String wkBdate;
    private String wkEdate;
    private String wkPdate;
    private String wkAtmcode;
    private String wkAtmcodeBank;
    private String wkTotcnt;
    private String wkBddir;
    private String file031Name = "";
    private String wkHaverpt = "N";
    private String wkRcptid;
    private String wkRcptid2;
    private String wkTrobank;
    private String wkTroaccno;
    private String wkTriaccnoP;
    private String wkTromach;
    private String wkTxnseq1;
    private String wkTxnseqOthBank;
    private String wkTxnseqOthTxno;
    private String wkOthcardTxno;
    private String wkTxnseqOth;
    private String wkAtmcodeBranch;
    private String wkSelfcardBranch;
    private String wkTxnseqSelfBrn;
    private String wkSelfcardMach;
    private String wkTxnseqSelfMach;
    private String wkTxnseqSelfTxno;
    private String wkSelfcardTxno;
    private String wkOthcardBranch;
    private String wkOthcardMach;
    private String wkUserdata;
    private String wkTxntimeP_HH;
    private String wkTxntimeP_MM;
    private String wkTxntimeP_SS;
    private String wkTxndateP;
    private String wkAtmcodeMach;

    // ---------- other int    (9) ----------
    private int trndate112034;
    private int txndate112034;
    private int txntime112034;

    // ---------- other String (X) ----------
    private String processDate;
    private String tbsdy;
    private int processDateInt = 0;
    private String atmcode112034;
    private String chtno112034;
    private String triaccno112034;
    private String troaccno112034;
    private String trobank112034;
    private String txnseq112034;

    // ----------  BigDecimal  ----------
    private BigDecimal wkTotamt;
    private BigDecimal wkTxnamtP;
    private BigDecimal putfAmt;
    private BigDecimal putfTotamt;
    private BigDecimal txnamt112034;

    // ---------- PUTF  int  (9) ----------
    private int putfBdate;
    private int putfEdate;
    private int putfDate;
    private int putfSitdate;
    private int putfCtl;
    private int putfTime;

    // ---------- PUTF String (X) ----------
    private String putfRcptid;
    private String putfTotcnt;
    private String putfUserdata;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV11 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV11Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV11 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV11Lsnr run");
        init(event);
        // 設定檔名變數值
        // WK-PBRNO-P分行別:WK-TITLE-LINE2'S變數

        // 020100*112034
        // 020200     MOVE        "27Z1112034"        TO   WK-PUTFILE,WK-CONVFILE.
        // 020300     MOVE        "012"               TO   WK-PBRNO-P.
        // 020400     MOVE        "012"               TO   WK-BDBRNO.
        wkPutfile = FILE_INPUT_NAME_27Z1112034;
        wkConvfile = FILE_INPUT_NAME_27Z1112034;
        wkPbrnoP = STRING_012;
        wkBdbrno = STRING_012;

        // 設定檔名
        // WK-PUTDIR="DATA/CL/BH/PUTF/"+WK-FDATE+"/27Z1112034."
        // WK-CONVDIR="DATA/CL/BH/CONVF/"+WK-CDATE+"/27Z1112034."
        // 020500     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 020600     CHANGE  ATTRIBUTE FILENAME OF FD-112034 TO WK-CONVDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
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
                        + CONVF_PATH_PUTF
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }
        // 若FD-PUTF檔案存在，
        //  A.WK-HAVERPT有無資料註記設為"Y"("N","Y")
        //  B.依代收類別"112034"讀取事業單位基本資料檔
        //  C.執行112034-RTN
        // 020700     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 020800       MOVE   "Y"    TO     WK-HAVERPT
            wkHaverpt = STRING_Y;
            // 020900       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "112034"
            tClmr = clmrService.findById(STRING_112034);
            // 021000       PERFORM    112034-RTN  THRU  112034-EXIT.
            code112034(STRING_112034);
        }
        // 設定檔名變數值
        // WK-PBRNO-P分行別:WK-TITLE-LINE2'S變數

        // 021100*112044
        // 021200     MOVE        "27Z1112044"        TO   WK-PUTFILE,WK-CONVFILE.
        // 021300     MOVE        "067"               TO   WK-PBRNO-P.
        // 021400     MOVE        "067"               TO   WK-BDBRNO.
        wkPutfile = FILE_INPUT_NAME_27Z1112044;
        wkConvfile = FILE_INPUT_NAME_27Z1112044;
        wkPbrnoP = STRING_067;
        wkBdbrno = STRING_067;

        // 設定檔名
        // WK-PUTDIR="DATA/CL/BH/PUTF/"+WK-FDATE+"/27Z1112044."
        // WK-CONVDIR="DATA/CL/BH/CONVF/"+WK-CDATE+"/27Z1112044."
        // 021500     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 021600     CHANGE  ATTRIBUTE FILENAME OF FD-112034 TO WK-CONVDIR.
        putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(wkPutdir);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        // 若FD-PUTF檔案存在，
        //  A.WK-HAVERPT有無資料註記設為"Y"("N","Y")
        //  B.依代收類別"112044"讀取事業單位基本資料檔
        //  C.執行112034-RTN
        // 021700     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 021800       MOVE   "Y"    TO     WK-HAVERPT
            wkHaverpt = STRING_Y;
            // 021900       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "112044"
            tClmr = clmrService.findById(STRING_112044);
            // 022000       PERFORM    112034-RTN  THRU  112034-EXIT.
            code112034(STRING_112044);
        }

        // 022200 0000-END-RTN.                                                    86/11/04

        // 若FD-PUTF均不存在
        //  A.設定檔名變數值
        //  B.設定REPORTFL檔名
        //     WK-BDDIR="BD/CL/BH/031/001."
        //  C.開啟檔案REPORTFL
        //  D.關閉檔案REPORTFL

        // 022300     IF      WK-HAVERPT        =        "N"
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkHaverpt={}", wkHaverpt);
        if (STRING_N.equals(wkHaverpt)) {
            // 022400       MOVE  "001"            TO        WK-BDBRNO
            // 022500       CHANGE  ATTRIBUTE FILENAME OF REPORTFL TO WK-BDDIR
            // WK-BDDIR="BD/CL/BH/031/001."
            file031Name = STRING_031;
            wkBddir =
                    fileDir
                            + CONVF_RPT
                            + PATH_SEPARATOR
                            + processDate
                            + PATH_SEPARATOR
                            + file031Name;
            // 022600       OPEN    OUTPUT REPORTFL
            // 022700       CLOSE   REPORTFL WITH SAVE.
            sb = new StringBuilder();
            fileCONV11Contents.add(sb.toString());
            try {
                textFile.writeFileContent(wkBddir, fileCONV11Contents, CHARSET_BIG5);
                upload(wkBddir, "RPT", "");
                wkFileNameList += file031Name + ",";
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }

        // 顯示訊息、關閉批次日期檔、結束程式
        // 022800     DISPLAY "SYM/CL/BH/CONV11 GENERATE DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV11 GENERATE DATA/CL/BH/PUTF OK");

        // 022900     CLOSE   BOTSRDB.
        // 023000     CLOSE   FD-BHDATE  WITH SAVE.
        // 023100     STOP RUN.

        batchResponse();
    }

    private void init(CONV11 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV11Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 批次(作業)日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);
        // 設定本營業日、檔名日期變數值
        // 019600     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        wkYYMMDD = processDateInt;
        // 019700     MOVE        WK-YYMMDD           TO   WK-FDATE,WK-CDATE.
        wkFdate = formatUtil.pad9("" + wkYYMMDD, 7).substring(1, 7);
        wkCdate = formatUtil.pad9("" + wkYYMMDD, 7).substring(1, 7);

        // PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        // 020000     MOVE        PARA-YYMMDD         TO   WK-PDATE.
        wkPdate = dateutil.getNowStringRoc();
    }

    // 112034-RTN
    private void code112034(String code) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV11Lsnr code112034()");
        // 開啟檔案
        // 023400     OPEN      OUTPUT    FD-112034.
        // 023500     OPEN      INPUT     FD-PUTF.
        // 開啟 FD-112034
        // 檔名:
        // "DATA/CL/BH/CONVF/"+WK-CDATE+"/"+WK-CONVFILE+"."

        // 設定REPORTFL檔名
        //  if CODE=112034 then WK-BDDIR="BD/CL/BH/031/012."
        //  if CODE=112044 then WK-BDDIR="BD/CL/BH/031/067."
        // 023600     CHANGE  ATTRIBUTE FILENAME OF REPORTFL TO WK-BDDIR.
        if ("112034".equals(code)) {
            file031Name = "CL-BH-031-012";
        }
        if ("112044".equals(code)) {
            file031Name = "CL-BH-031-067";
        }
        wkBddir = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + file031Name;
        // 開啟檔案REPORTFL
        // 023700     OPEN      OUTPUT    REPORTFL.

        // 頁數、行數控制變數清0
        // 023800     MOVE      0    TO   WK-PAGE,WK-PCTL.
        wkPage = 0;
        wkPctl = 0;

        // 執行112034-WTIT-RTN，寫表頭
        // 023900     PERFORM   112034-WTIT-RTN   THRU   112034-WTIT-EXIT.
        wtit112034(PAGE_SEPARATOR);
        // 024000***  DETAIL  RECORD  *****
        // 024100 112034-NEXT.

        // 循序讀取FD-PUTF，直到檔尾，跳到112034-CLOSE
        // 024200     READ   FD-PUTF    AT  END  GO TO  112034-CLOSE.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            if (detail.length() < 120) {
                detail = formatUtil.padX(detail, 120);
            }
            cnt++;
            /*
            01 PUTF-REC TOTAL 120 BYTES
              03  PUTF-CTL	9(02)   0~2
              03  PUTF-CTL-R	REDEFINES PUTF-CTL
                05 PUTF-CTL1	9(01)   0~1
                05 PUTF-CTL2	9(01)   1~2
              03  PUTF-CODE	X(06)	代收類別   2~8
              03  PUTF-DATA	GROUP
                05 PUTF-RCPTID	X(16)	銷帳號碼   8~24
                05 PUTF-DATE	9(06)	代收日   24~30
                05 PUTF-TIME	9(06)	代收時間   30~36
                05 PUTF-CLLBR	9(03)	代收行   36~39
                05 PUTF-LMTDATE	9(06)	繳費期限   39~45
                05 PUTF-OLDAMT	9(08)	繳費金額   45~53
                05 PUTF-USERDATA	X(40)	備註資料   53~93
                05 PUTF-SITDATE	9(06)	原代收日   93~99
                05 PUTF-TXTYPE	X(01)	帳務別   99~100
                05 PUTF-AMT	9(10)	繳費金額   100~110
                05 PUTF-FILLER	X(10)   110~120
              03  PUTF-DATA-R	REDEFINES PUTF-DATA
                05 PUTF-BDATE	9(06)	挑檔起日   8~14
                05 PUTF-EDATE	9(06)	挑檔迄日   14~20
                05 PUTF-TOTCNT	9(06)	彙總筆數   20~26
                05 PUTF-TOTAMT	9(13)	彙總金額   26~39
                05 FILLER	X(81)   39~120
              03  PUTF-DATA-NODATA	REDEFINES PUTF-DATA
                05 PUTF-NODATA	X(16)   8~24
                05 PUTF-FILLER1	X(96)   24~120
            */
            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "0");
            putfRcptid = detail.substring(8, 24);
            putfDate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(24, 30))
                                    ? detail.substring(24, 30)
                                    : "0");
            putfSitdate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(93, 99))
                                    ? detail.substring(93, 99)
                                    : "0");
            putfAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(100, 110))
                                    ? detail.substring(100, 110)
                                    : "0");
            putfUserdata = detail.substring(53, 93);
            putfTime =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(30, 36))
                                    ? detail.substring(30, 36)
                                    : "0");
            putfTotcnt = detail.substring(20, 26);
            putfTotamt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(26, 39))
                                    ? detail.substring(26, 39)
                                    : "0");
            putfBdate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(8, 14))
                                    ? detail.substring(8, 14)
                                    : "0");
            putfEdate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(14, 20))
                                    ? detail.substring(14, 20)
                                    : "0");

            // 若PUTF-CTL <> 11 銷帳明細資料，跳到112034-LAST
            // 024300     IF        PUTF-CTL       NOT =      11
            if (11 != putfCtl) {
                // 024400       GO TO   112034-LAST.
                // 027200 112034-LAST.
                _112034_Last();
                if (cnt == lines.size()) {
                    _112034_Close();
                    return;
                }
                // LOOP讀下一筆FD-PUTF
                // 028000     GO TO     112034-NEXT.
                continue;
            }

            // WK-PCTL行數控制加1
            // 024500     ADD       1               TO     WK-PCTL.
            wkPctl++;
            // WK-PCTL行數控制>50時
            //  A.換頁
            //  B.執行112034-WTIT-RTN，寫表頭
            // 024600     IF        WK-PCTL         >      50
            if (wkPctl > 50) {
                // 024700       MOVE    1               TO     WK-PCTL
                wkPctl = 1;
                // 024800       PERFORM 112034-WTIT-RTN THRU   112034-WTIT-EXIT.
                wtit112034(PAGE_SEPARATOR);
            }

            // 搬資料到112034-REC... & WK-DETAIL-LINE...
            //  01WK-DETAIL-LINE.
            //            wkDetailLine();
            // 024900     MOVE      SPACES          TO     112034-REC.

            // 025000     COMPUTE   WK-DATE         =      19110000 + PUTF-DATE.
            wkDate = INT_19110000 + putfDate;
            // 025100     MOVE      WK-DATE         TO     112034-TRNDATE,WK-TRNDATE-P.
            trndate112034 = wkDate;
            wkTrndateP = wkDate;
            // 025200     MOVE      PUTF-RCPTID     TO     WK-RCPTID.
            wkRcptid = putfRcptid;
            wkRcptid2 = wkRcptid.substring(8, 16);
            // 025300     MOVE      WK-RCPTID2      TO     112034-CHTNO,WK-CHTNO-P.
            chtno112034 = wkRcptid2;
            wkChtnoP = wkRcptid2;
            // 025400     COMPUTE   WK-SITDATE      =      19110000 + PUTF-SITDATE.
            wkSitdate = INT_19110000 + putfSitdate;
            // 025500     MOVE      WK-SITDATE      TO     112034-TXNDATE,WK-TXNDATE-P.
            txndate112034 = wkSitdate;
            wkTxndateP = "" + wkSitdate;

            // 025600     MOVE      PUTF-TIME       TO     112034-TXNTIME,WK-TXNTIME-P.
            txntime112034 = putfTime;
            wkTxntimeP = String.valueOf(putfTime); // 111850
            wkTxntimeP_HH = wkTxntimeP.substring(0, 2); // 11
            wkTxntimeP_MM = wkTxntimeP.substring(2, 4); // 18
            wkTxntimeP_SS = wkTxntimeP.substring(4, 6); // 50
            // 025700     MOVE      PUTF-USERDATA   TO     WK-USERDATA.
            wkUserdata = putfUserdata;
            wkTrobank = wkUserdata.substring(0, 3);
            wkTroaccno = wkUserdata.substring(3, 19);
            wkTromach = wkUserdata.substring(19, 22);
            wkTxnseq1 = wkUserdata.substring(22, 34);
            wkOthcardTxno = wkTxnseq1.substring(0, 7);
            wkSelfcardBranch = wkTxnseq1.substring(7, 10);
            wkSelfcardMach = wkTxnseq1.substring(0, 2);
            wkSelfcardTxno = wkTxnseq1.substring(2, 7);
            wkOthcardBranch = wkTxnseq1.substring(7, 10);
            wkOthcardMach = wkTxnseq1.substring(10, 12);
            // 025800     MOVE      WK-TROBANK      TO     112034-TROBANK,WK-TROBANK-P.
            trobank112034 = wkTrobank;
            wkTrobankP = wkTrobank;
            // 025900     MOVE      WK-TROACCNO     TO   112034-TROACCNO,WK-TROACCNO-P.
            troaccno112034 = wkTroaccno;
            wkTroaccnoP = wkTroaccno;
            // 026000     MOVE      DB-CLMR-ACTNO   TO   112034-TRIACCNO,WK-TRIACCNO-P.
            triaccno112034 = "" + tClmr.getActno();
            wkTriaccnoP = "" + tClmr.getActno();
            // 026100     MOVE      PUTF-AMT        TO   112034-TXNAMT.
            txnamt112034 = putfAmt;
            // 026200     MOVE      PUTF-AMT        TO   WK-TXNAMT-P.
            wkTxnamtP = putfAmt;

            // 自行ATM，執行112034-SELF-RTN，搬ＡＴＭ代號、交易序號
            // 026300     IF        WK-TROBANK      =    WK-TROMACH
            if (wkTrobank.equals(wkTromach)) {
                // 026400       PERFORM 112034-SELF-RTN   THRU   112034-SELF-EXIT
                self112034();
            } else {
                // 026500     ELSE
                // 跨行ATM，執行112034-OTHER-RTN，搬ＡＴＭ代號、交易序號
                // 026600       PERFORM 112034-OTHER-RTN  THRU   112034-OTHER-EXIT.
                other112034();
            }

            // 寫檔FD-112034
            // 026700     WRITE     112034-REC.
            write112034();

            // 寫REPORTFL報表明細(WK-DETAIL-LINE)
            // 026800     MOVE      SPACES          TO     REPORT-LINE.
            // 026900     WRITE     REPORT-LINE    FROM    WK-DETAIL-LINE.
            // 014900 01 WK-DETAIL-LINE.
            wkDetailLine();

            // LOOP讀下一筆FD-PUTF
            // 027000     GO TO     112034-NEXT.

            if (cnt == lines.size()) {
                _112034_Close();
            }
        }
    }

    private void write112034() {
        // 003400 01  112034-REC.
        sb = new StringBuilder();
        // 003500    03  112034-TRNDATE                 PIC 9(08).
        sb.append(formatUtil.pad9("" + trndate112034, 8));
        // 003600    03  112034-CHTNO                   PIC X(12).
        sb.append(formatUtil.padX(chtno112034, 12));
        // 003700    03  112034-TXNDATE                 PIC 9(08).
        sb.append(formatUtil.pad9("" + txndate112034, 8));
        // 003800    03  112034-TXNTIME                 PIC 9(06).
        sb.append(formatUtil.pad9("" + txntime112034, 6));
        // 003900    03  112034-TROBANK                 PIC X(03).
        sb.append(formatUtil.padX(trobank112034, 3));
        // 004000    03  112034-TROACCNO                PIC X(16).
        sb.append(formatUtil.padX(troaccno112034, 16));
        // 004100    03  112034-TRIACCNO                PIC X(16).
        sb.append(formatUtil.padX(triaccno112034, 16));
        // 004200    03  112034-TXNAMT                  PIC 9(08)V99.
        sb.append(formatUtil.pad9("" + txnamt112034, 10));
        // 004300    03  112034-ATMCODE                 PIC X(08).
        sb.append(formatUtil.padX(atmcode112034, 8));
        // 004400    03  112034-TXNSEQ                  PIC X(11).
        sb.append(formatUtil.padX(txnseq112034, 11));
        file112034Contents.add(sb.toString());
    }

    private void _112034_Last() {
        // 寫REPORTFL報表表尾(WK-TOTAL-LINE)
        // 027300     COMPUTE   WK-BDATE    =  19110000 + PUTF-BDATE.
        wkBdate = String.valueOf(INT_19110000 + putfBdate);
        // 027400     COMPUTE   WK-EDATE    =  19110000 + PUTF-EDATE.
        wkEdate = String.valueOf(INT_19110000 + putfEdate);
        // 027500     MOVE      PUTF-TOTCNT          TO   WK-TOTCNT.
        wkTotcnt = putfTotcnt;
        // 027600     MOVE      PUTF-TOTAMT          TO   WK-TOTAMT.
        wkTotamt = putfTotamt;
        // 027700     MOVE      SPACES               TO   REPORT-LINE.
        // 027800     WRITE     REPORT-LINE         AFTER  1 LINE.
        fileCONV11Contents.add("");
        // 027900     WRITE     REPORT-LINE         FROM  WK-TOTAL-LINE.
        // 017300 01 WK-TOTAL-LINE.
        sb = new StringBuilder();
        // 017400    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 017500    02 FILLER                   PIC X(13) VALUE " 資料起日  : ".
        sb.append(formatUtil.padX(" 資料起日  : ", 13));
        // 017600    02 WK-BDATE                 PIC 9999/99/99.
        sb.append(reportUtil.customFormat(wkBdate, "9999/99/99"));
        // 017700    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 017800    02 FILLER                   PIC X(11) VALUE " 資料迄日 :".
        sb.append(formatUtil.padX(" 資料迄日 :", 11));
        // 017900    02 WK-EDATE                 PIC 9999/99/99.
        sb.append(reportUtil.customFormat(wkEdate, "9999/99/99"));
        // 018000    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 018100    02 FILLER                   PIC X(08) VALUE "  筆數 :".
        sb.append(formatUtil.padX("  筆數 :", 8));
        // 018200    02 WK-TOTCNT                PIC ZZZ,ZZ9.B.
        sb.append(reportUtil.customFormat(wkTotcnt, "ZZZ,ZZ9.9"));
        // 018300    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 018400    02 FILLER                   PIC X(13) VALUE "  代收金額 : ".
        sb.append(formatUtil.padX("  代收金額 : ", 13));
        // 018500    02 WK-TOTAMT                PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.B.
        sb.append(reportUtil.customFormat(String.valueOf(wkTotamt), "Z,ZZZ,ZZZ,ZZZ,ZZ9.9"));
        fileCONV11Contents.add(sb.toString());
    }

    private void _112034_Close() {
        // 關閉檔案
        // 028200     CLOSE  FD-PUTF    WITH  SAVE.
        // 028300     CHANGE ATTRIBUTE FILENAME OF FD-112034 TO WK-PUTDIR.
        // 028400     CLOSE  FD-112034  WITH  SAVE.
        // 028500     CLOSE  REPORTFL   WITH  SAVE.
        textFile.deleteFile(wkPutdir);
        try {
            textFile.writeFileContent(wkPutdir, file112034Contents, CHARSET);
            upload(wkPutdir, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(wkBddir, fileCONV11Contents, CHARSET_BIG5);
            upload(wkBddir, "RPT", "");
            wkFileNameList += file031Name + ",";
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    // 112034-SELF-RTN
    private void self112034() {
        // 搬自行ＡＴＭ代號、交易序號到112034-REC... & WK-DETAIL-LINE...
        // 030500     MOVE      WK-TROMACH           TO   WK-ATMCODE-BANK.
        wkAtmcodeBank = wkTromach;
        // 030600     MOVE      WK-SELFCARD-BRANCH   TO   WK-ATMCODE-BRANCH.
        wkAtmcodeBranch = wkSelfcardBranch;
        // 030700     MOVE      WK-SELFCARD-MACH     TO   WK-ATMCODE-MACH.
        wkAtmcodeMach = wkSelfcardMach;
        wkAtmcode = wkAtmcodeBank + wkAtmcodeBranch + wkAtmcodeMach;
        // 030800     MOVE      WK-ATMCODE           TO   112034-ATMCODE.
        atmcode112034 = wkAtmcode;
        // 030900     MOVE      WK-ATMCODE           TO   WK-ATMCODE-P.
        wkAtmcodeP = wkAtmcode;
        // 031000     MOVE      WK-SELFCARD-BRANCH   TO   WK-TXNSEQ-SELF-BRH.
        wkTxnseqSelfBrn = wkSelfcardBranch;
        // 031100     MOVE      WK-SELFCARD-MACH     TO   WK-TXNSEQ-SELF-MACH.
        wkTxnseqSelfMach = wkSelfcardMach;
        // 031200     MOVE      WK-SELFCARD-TXNO     TO   WK-TXNSEQ-SELF-TXNO.
        wkTxnseqSelfTxno = wkSelfcardTxno;
        wkTxnseqSelf = wkTxnseqSelfBrn + wkTxnseqSelfMach + wkTxnseqSelfTxno;
        // 031300     MOVE      WK-TXNSEQ-SELF       TO   112034-TXNSEQ.
        txnseq112034 = formatUtil.padX(wkTxnseqSelf, 11);
        // 031400     MOVE      WK-TXNSEQ-SELF       TO   WK-TXNSEQ-P.
        wkTxnseqP = wkTxnseqSelf;
    }

    // 112034-OTHER-RTN
    private void other112034() {
        // 搬跨行ＡＴＭ代號、交易序號到112034-REC... & WK-DETAIL-LINE...
        // 031800     MOVE      WK-TROMACH           TO   WK-ATMCODE-BANK.
        wkAtmcodeBank = wkTromach;
        // 031900     MOVE      WK-OTHCARD-BRANCH    TO   WK-ATMCODE-BRANCH.
        wkAtmcodeBranch = wkOthcardBranch;
        // 032000     MOVE      WK-OTHCARD-MACH      TO   WK-ATMCODE-MACH.
        wkAtmcodeMach = wkOthcardMach;
        // 032100     MOVE      WK-ATMCODE           TO   112034-ATMCODE.
        atmcode112034 = wkAtmcode;
        // 032200     MOVE      WK-ATMCODE           TO   WK-ATMCODE-P.
        wkAtmcodeP = wkAtmcode;
        // 032300     MOVE      WK-TROMACH           TO   WK-TXNSEQ-OTH-BANK.
        wkTxnseqOthBank = wkTromach;
        // 032400     MOVE      WK-OTHCARD-TXNO      TO   WK-TXNSEQ-OTH-TXNO.
        wkTxnseqOthTxno = wkOthcardTxno;
        // 007900 01 WK-TXNSEQ-OTH.
        // 008000  03 WK-TXNSEQ-OTH-BANK                PIC X(03).
        // 008100  03 WK-TXNSEQ-OTH-TXNO                PIC X(07).
        wkTxnseqOth = formatUtil.padX(wkTxnseqOthBank, 3) + formatUtil.padX(wkTxnseqOthTxno, 7);
        // 032500     MOVE      WK-TXNSEQ-OTH        TO   112034-TXNSEQ.
        txnseq112034 = formatUtil.padX(wkTxnseqOth, 11);
        // 032600     MOVE      WK-TXNSEQ-OTH        TO   WK-TXNSEQ-P.
        wkTxnseqP = wkTxnseqOth;
    }

    // WK-DETAIL-LINE
    private void wkDetailLine() {
        sb = new StringBuilder();
        //   02 WK-TRNDATE-P   PIC 9999/99/99.	<-" 帳務日期 "
        sb.append(reportUtil.customFormat("" + wkTrndateP, "9999/99/99"));
        //   02 WK-CHTNO-P     PIC X(12).				<-" 病歷號碼 "
        sb.append(formatUtil.padX(wkChtnoP, 12));
        //   02 WK-TXNDATE-P   PIC 9999/99/99.	<-" 交易日期 "
        sb.append(reportUtil.customFormat(wkTxndateP, "9999/99/99"));
        //   02 WK-TXNTIME-P   PIC 99:99:99.		<-" 交易時間 "
        sb.append(
                formatUtil.padX(
                        wkTxntimeP_HH + ":" + wkTxntimeP_MM + ":" + wkTxntimeP_SS, 8)); // 99:99:99
        //   02 WK-TROBANK-P   PIC X(03).				<-" 轉出銀行 "
        sb.append(formatUtil.padX(wkTrobankP, 3));
        //   02 WK-TROACCNO-P  PIC X(16).				<-" 轉出帳號 "
        sb.append(formatUtil.padX(wkTroaccnoP, 16));
        //   02 WK-TRIACCNO-P  PIC X(16).				<-" 轉入帳號 "
        sb.append(formatUtil.padX(wkTriaccnoP, 16));
        //   02 WK-TXNAMT-P    PIC ZZ,ZZZ,ZZZ.	<-" 繳費金額 "
        sb.append(reportUtil.customFormat("" + wkTxnamtP, "ZZ,ZZZ,ZZZ"));
        //   02 WK-ATMCODE-P   PIC X(08).				<-" ＡＴＭ代號 "
        sb.append(formatUtil.padX(wkAtmcodeP, 8));
        //   02 WK-TXNSEQ-P    PIC X(11).				<-" 交易序號  "
        sb.append(formatUtil.padX(wkTxnseqP, 11));
        fileCONV11Contents.add(sb.toString());
    }

    // 112034-WTIT-RTN
    private void wtit112034(String pageFg) {
        // 寫REPORTFL報表表頭(WK-TITLE-LINE1~WK-TITLE-LINE3)
        // 028900     ADD        1                   TO     WK-PAGE.
        wkPage++;
        // 029000     MOVE       SPACES              TO     REPORT-LINE.
        // 029100     WRITE      REPORT-LINE         AFTER  PAGE.
        sb = new StringBuilder();
        sb.append(pageFg); // 是否新頁
        fileCONV11Contents.add(sb.toString());

        // 029200     MOVE       SPACES              TO     REPORT-LINE.
        // 029300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 010900 01 WK-TITLE-LINE1.
        sb = new StringBuilder();
        // 011000    02 FILLER                          PIC X(30) VALUE SPACE.
        sb.append(formatUtil.padX("", 30));
        // 011100    02 FILLER                          PIC X(42) VALUE
        // 011200       "   ＡＴＭ轉帳長庚醫院醫療費用交易明細表   ".
        sb.append(formatUtil.padX("   ＡＴＭ轉帳長庚醫院醫療費用交易明細表   ", 42));
        // 011300    02 FILLER                          PIC X(37) VALUE SPACE.
        sb.append(formatUtil.padX("", 37));
        // 011400    02 FILLER                          PIC X(12) VALUE
        // 011500       "FORM : C031 ".
        sb.append(formatUtil.padX("FORM : C031 ", 12));
        fileCONV11Contents.add(sb.toString());

        // 029400     MOVE       SPACES              TO     REPORT-LINE.
        // 029500     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileCONV11Contents.add("");
        // 029600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 011600 01 WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 011700    02 FILLER                          PIC X(10) VALUE
        // 011800       " 分行別： ".
        sb.append(formatUtil.padX(" 分行別： ", 10));
        // 011900    02 WK-PBRNO-P                      PIC X(03).
        sb.append(formatUtil.padX(wkPbrnoP, 3));
        // 012000    02 FILLER                          PIC X(05) VALUE SPACE.
        sb.append(formatUtil.padX("", 5));
        // 012100    02 FILLER                          PIC X(13) VALUE
        // 012200       " 印表日期：  ".
        sb.append(formatUtil.padX(" 印表日期：  ", 13));
        // 012300    02 WK-PDATE                        PIC 99/99/99.
        sb.append(reportUtil.customFormat(wkPdate, "99/99/99"));
        // 012400    02 FILLER                          PIC X(69) VALUE SPACE.
        sb.append(formatUtil.padX("", 69));
        // 012500    02 FILLER                          PIC X(08) VALUE
        // 012600       " 頁次  :".
        sb.append(formatUtil.padX(" 頁次  :", 8));
        // 012700    02 WK-PAGE                         PIC 9(04).
        sb.append(formatUtil.pad9("" + wkPage, 4));
        fileCONV11Contents.add(sb.toString());
        // 029700     MOVE       SPACES              TO     REPORT-LINE.
        // 029800     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileCONV11Contents.add("");

        // 029900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 012800 01 WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 012900    02 FILLER              PIC X(03) VALUE SPACE.
        sb.append(formatUtil.padX("", 3));
        // 013000    02 FILLER              PIC X(10) VALUE " 帳務日期 ".
        sb.append(formatUtil.padX(" 帳務日期 ", 10));
        // 013100    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 013200    02 FILLER              PIC X(13) VALUE " 病歷號碼    ".
        sb.append(formatUtil.padX(" 病歷號碼    ", 13));
        // 013300    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 013400    02 FILLER              PIC X(10) VALUE " 交易日期 ".
        sb.append(formatUtil.padX(" 交易日期 ", 10));
        // 013500    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 013600    02 FILLER              PIC X(10) VALUE " 交易時間 ".
        sb.append(formatUtil.padX(" 交易時間 ", 10));
        // 013700    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 013800    02 FILLER              PIC X(10) VALUE " 轉出銀行 ".
        sb.append(formatUtil.padX(" 轉出銀行 ", 10));
        // 013900    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 014000    02 FILLER              PIC X(16) VALUE " 轉　出　帳　號 ".
        sb.append(formatUtil.padX(" 轉　出　帳　號 ", 16));
        // 014100    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 014200    02 FILLER              PIC X(16) VALUE " 轉　入　帳　號 ".
        sb.append(formatUtil.padX(" 轉　入　帳　號 ", 16));
        // 014300    02 FILLER              PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX("", 6));
        // 014400    02 FILLER              PIC X(10) VALUE " 繳費金額 ".
        sb.append(formatUtil.padX(" 繳費金額 ", 10));
        // 014500    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 014600    02 FILLER              PIC X(12) VALUE " ＡＴＭ代號 ".
        sb.append(formatUtil.padX(" ＡＴＭ代號 ", 12));
        // 014700    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 014800    02 FILLER              PIC X(11) VALUE " 交易序號  ".
        sb.append(formatUtil.padX(" 交易序號  ", 11));
        fileCONV11Contents.add(sb.toString());
        // 030000     MOVE       SPACES              TO     REPORT-LINE.
        // 030100     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 017000 01 WK-GATE-LINE.
        sb = new StringBuilder();
        // 017100    02 FILLER                   PIC X(03) VALUE SPACE.
        sb.append(formatUtil.padX("", 3));
        // 017200    02 FILLER                   PIC X(150) VALUE ALL "-".
        sb.append(reportUtil.makeGate("-", 150));
        fileCONV11Contents.add(sb.toString());
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
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

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", wkFileNameList);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
