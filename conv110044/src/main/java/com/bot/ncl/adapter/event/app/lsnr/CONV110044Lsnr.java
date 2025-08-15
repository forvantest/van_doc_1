/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV110044;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
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
@Component("CONV110044Lsnr")
@Scope("prototype")
public class CONV110044Lsnr extends BatchListenerCase<CONV110044> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV110044 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private String wkPutdir;
    private String wkReportdir;
    private String wkConvdir;
    private String sortPath;
    private final List<String> fileCONV110044Contents = new ArrayList<>(); // 檔內容
    private final List<String> fileReportContents = new ArrayList<>(); // 表內容
    private StringBuilder sb;

    private String processDate;
    private String tbsdy;
    private String wkYYYYMMDD;
    private String wkFdate;
    private String wkCdate;
    private String wkPdate;
    private String wkPutfile;
    private String wkConvfile;
    private String wkRcptid;
    private int wkPreTime;
    private String wkTime;
    private int wkCount;
    private int wkCountP;
    private int wkPctl = 0;
    private int wkPage = 0;
    private String wkCtl;
    private String wkCode;
    private String wkDate;
    private String wkPreRcptid = "";
    private String wkCllbr;
    private String wkLmtdate;
    private String wkAmt;
    private String wkUserdata;
    private String wkTxtype;
    private String wkBdate;
    private String wkEdate;
    private String wkTotcnt;
    private String wkTotamt;

    // --PUTFN--
    private String putfnCtl;
    private String putfnCode;
    private String putfnBdate;
    private String putfnEdate;
    private String putfnTotcnt;
    private String putfnTotamt;
    private String putfnDate;
    private String putfnRcptid;
    private String putfnTime;
    private String putfnCllbr;
    private String putfnLmtdate;
    private String putfnAmt;
    private String putfnUserdata;
    private String putfnSitdate;
    private String putfnTxtype;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV110044 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV110044 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr run()");
        this.event = event;
        _0000_Start();
        batchResponse();
    }

    public void _0000_Start() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr _0000_Start ....");
        //// 讀批次日期檔；若讀不到，顯示訊息，結束程式

        // 016300     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 016400          STOP RUN.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 作業日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        //// 關閉批次日期檔

        // 016500     CLOSE   FD-BHDATE  WITH SAVE.
        //// 設定本營業日、檔名日期變數值、印表日期

        // 016600     MOVE    FD-BHDATE-TBSDY           TO WK-YYYYMMDD      ,
        // 016700                                          WK-FDATE,WK-CDATE,
        // 016800                                          WK-PDATE         .
        wkYYYYMMDD = processDate;
        wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        wkCdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        wkPdate = processDate;
        //// 設定檔名變數值
        //// WK-PUTDIR :"DATA/CL/BH/PUTFN/"+WK-FDATE+"/27X4110044."
        //// WK-CONVDIR:"DATA/CL/BH/CONVF/"+WK-CDATE+"/27X4110044."
        // 017000     MOVE        "27X4110044"        TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = "27X4110044";
        wkConvfile = "27X4110044";
        //// 設定檔名
        // 017100     CHANGE  ATTRIBUTE FILENAME OF FD-PUTFN  TO WK-PUTDIR .
        // 017200     CHANGE  ATTRIBUTE FILENAME OF FD-110044 TO WK-CONVDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTFN"
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
                        + "PUTFN"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        wkConvdir = fileDir + "CONVF" + PATH_SEPARATOR + wkCdate + PATH_SEPARATOR + wkPutfile;
        // 002300 FD  REPORTFL
        // 002400     VALUE  OF  TITLE  IS  "BD/CL/BH/030."
        // 002500                USERBACKUPNAME IS TRUE
        // 002600                SAVEPRINTFILE  IS TRUE
        // 002700                SECURITYTYPE   IS PUBLIC.
        // 002800 01   REPORT-LINE              PIC X(165).
        wkReportdir =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-030";
        //// 若FD-PUTFN檔案存在，執行0000-MAIN-RTN 主程式
        //
        // 017300     IF  ATTRIBUTE  RESIDENT  OF FD-PUTFN IS = VALUE(TRUE)

        sortPath = fileDir + "SPUTF";

        if (textFile.exists(wkPutdir)) {
            checkSortPath();

            // 017400        PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.
            _0000_Main();
        }
        // 017600 0000-END-RTN.
        //// 顯示訊息、結束程式
        // 017700     DISPLAY "SYM/CL/BH/CONV110044 GENERATE DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV110044 GENERATE DATA/CL/BH/PUTF OK");

        // 017800     STOP RUN.
    }

    private void checkSortPath() {
        if (textFile.exists(sortPath)) {
            sort110044();
        }
    }

    private void sort110044() {
        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 16, SortBy.ASC));
        keyRanges.add(new KeyRange(45, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
    }

    private void _0000_Main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr _0000_Main ....");
        // 018000 0000-MAIN-RTN.
        // 018100     MOVE      0             TO   WK-PRE-TIME,WK-COUNT.
        wkPreTime = 0;
        wkCount = 0;
        //// 開啟檔案

        // 018200     OPEN      INPUT         FD-PUTFN .
        // 018300     OPEN      OUTPUT        FD-110044.
        // 018400     OPEN      OUTPUT        REPORTFL .

        //// 執行RPT-TITLE-RTN，寫REPORTFL表頭
        // 018500     PERFORM   RPT-TITLE-RTN THRU RPT-TITLE-EXIT.
        rpt_Title(PAGE_SEPARATOR); // 第一頁不需換頁記號

        // 018600 0000-MAIN-LOOP.

        //// 循序讀取FD-PUTFN，直到檔尾，跳到0000-MAIN-LAST
        // 018700     READ      FD-PUTFN  AT  END  GO TO  0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        int cnt = 0; // 當前資料筆數
        int lCnt = lines.size(); // 讀檔資料總筆數
        boolean isLast = false;
        for (String detail : lines) {
            cnt++;
            if (cnt == lCnt) {
                isLast = true;
            }
            // 01 PUTFN-REC TOTAL 160 BYTES
            // 03  PUTFN-CTL	9(02)	0-2
            // 03  PUTFN-CTL-R	REDEFINES PUTFN-CTL
            //  05 PUTFN-CTL1	9(01)	0-1
            //  05 PUTFN-CTL2	9(01)	1-2
            // 03  PUTFN-CODE	X(06)	代收類別 2-8
            // 03  PUTFN-DATA.	GROUP
            //  05 PUTFN-RCPTID	X(26)	銷帳號碼 8-34
            //  05 PUTFN-DATE	9(08)	代收日 34-42
            //  05 PUTFN-TIME	9(06)	代收時間 42-48
            //  05 PUTFN-CLLBR	9(03)	代收行 48-51
            //  05 PUTFN-LMTDATE	9(08)	繳費期限 51-59
            //  05 PUTFN-OLDAMT	9(10)	 59-69
            //  05 PUTFN-USERDATA	X(40)	備註資料 69-109
            //  05 PUTFN-SITDATE	9(08)	原代收日 109-117
            //  05 PUTFN-TXTYPE	X(01)	帳務別 117-118
            //  05 PUTFN-AMT	9(12)	繳費金額 118-130
            //  05 PUTFN-FILLER	X(30)	 130-160
            // 03  PUTFN-DATA-R
            //	REDEFINES PUTFN-DATA
            //  05 PUTFN-BDATE	9(08)	挑檔起日 8-16
            //  05 PUTFN-EDATE	9(08)	挑檔迄日 16-24
            //  05 PUTFN-TOTCNT	9(06)	彙總筆數 24-30
            //  05 PUTFN-TOTAMT	9(13)	彙總金額 30-43
            //  05 FILLER	X(117)	 43-160
            // 03  PUTFN-DATA-NODATA	REDEFINES PUTFN-DATA
            //  05 PUTFN-NODATA	X(26)	8-34
            //  05 PUTFN-FILLER1	X(126)	34-160
            putfnCtl = parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "00";
            putfnCode = detail.substring(2, 8);
            putfnDate = detail.substring(34, 42);
            putfnRcptid = detail.substring(8, 34);
            putfnTime =
                    parse.isNumeric(detail.substring(42, 48)) ? detail.substring(42, 48) : "000000";
            putfnCllbr = detail.substring(48, 51);
            putfnLmtdate = detail.substring(51, 59);
            putfnAmt = detail.substring(118, 130);
            putfnUserdata = detail.substring(69, 109);
            putfnSitdate = detail.substring(109, 117);
            putfnTxtype = detail.substring(117, 118);
            putfnBdate = detail.substring(8, 16);
            putfnEdate = detail.substring(16, 24);
            putfnTotcnt = detail.substring(24, 30);
            putfnTotamt = detail.substring(30, 43);
            //// PUTFN-CTL=11 明細資料
            ////  A.筆數、行數加1
            ////  B.執行PAGE-SWH-RTN，換頁判斷
            ////  C.執行REC-DTL-RTN，搬PUTFN-REC...到110044-REC... & 報表、寫檔FD-110044(明細資料 CTL=11)
            ////  D.執行RPT-DTL-RTN，寫REPORTFL明細

            // 018900     IF        PUTFN-CTL     =    11
            if (parse.string2Integer(putfnCtl) == 11) {
                // 019000       ADD     1             TO   WK-COUNT , WK-PCTL
                wkCount = wkCount + 1;
                wkPctl = wkPctl + 1;
                // 019100       PERFORM PAGE-SWH-RTN  THRU PAGE-SWH-EXIT
                page_Swh();
                // 019200       PERFORM REC-DTL-RTN   THRU REC-DTL-EXIT
                rec_Dtl();
                // 019300       PERFORM RPT-DTL-RTN   THRU RPT-DTL-EXIT.
                rpt_Dtl();
                // 019400
            }
            //// PUTFN-CTL=12 彙總資料
            ////  A.執行REC-LAST-RTN，搬PUTFN-REC...到110044-REC... & 報表、寫檔FD-110044(彙總資料CTL=12)
            ////  B.執行RPT-LAST-RTN，寫REPORTFL表尾
            ////  C.LOOP讀下一筆FD-PUTFN
            //
            // 019500     IF        PUTFN-CTL     =    12
            if (parse.string2Integer(putfnCtl) == 12) {
                // 019600       PERFORM REC-LAST-RTN  THRU REC-LAST-EXIT
                rec_Last();
                // 019700       PERFORM RPT-LAST-RTN  THRU RPT-LAST-EXIT.
                rpt_Last();
                // 019800       GO TO   0000-MAIN-LOOP.
                if (isLast) {
                    _0000_Main_Last();
                }
                continue;
            }
            //// LOOP讀下一筆FD-PUTFN
            if (isLast) {
                _0000_Main_Last();
            }
            // 020000     GO TO 0000-MAIN-LOOP.
        }
    }

    private void rpt_Last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr rec_Last ....");
        // 026300 RPT-LAST-RTN.

        //// 寫REPORTFL表尾 (WK-TOTAL-LINE)
        // 026400     MOVE    SPACES               TO   REPORT-LINE.
        // 026500     WRITE   REPORT-LINE          FROM WK-TOTAL-LINE.
        // 014400 01 WK-TOTAL-LINE.
        // 014500    02 FILLER                   PIC X(04) VALUE SPACES.
        // 014600    02 FILLER                   PIC X(13) VALUE " 資料起日  : ".
        // 014700    02 WK-BDATE                 PIC Z99/99/99.
        // 014800    02 FILLER                   PIC X(04) VALUE SPACES.
        // 014900    02 FILLER                   PIC X(11) VALUE " 資料迄日 :".
        // 015000    02 WK-EDATE                 PIC Z99/99/99.
        // 015100    02 FILLER                   PIC X(04) VALUE SPACES.
        // 015200    02 FILLER                   PIC X(08) VALUE "  筆數 :".
        // 015300    02 WK-TOTCNT                PIC ZZZ,ZZ9.B.
        // 015400    02 FILLER                   PIC X(04) VALUE SPACES.
        // 015500    02 FILLER                   PIC X(13) VALUE "  代收金額 : ".
        // 015600    02 WK-TOTAMT                PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.B.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 資料起日  : ", 13));
        sb.append(reportUtil.customFormat(wkBdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 資料迄日 :", 11));
        sb.append(reportUtil.customFormat(wkEdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("  筆數 :", 11));
        sb.append(reportUtil.customFormat(wkTotcnt, "ZZZ,ZZ9.B"));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("  代收金額 : ", 11));
        sb.append(reportUtil.customFormat(wkTotamt, "Z,ZZZ,ZZZ,ZZZ,ZZ9.B"));
        fileReportContents.add(sb.toString());
        // 026600 RPT-LAST-EXIT.
    }

    private void rec_Last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr rec_Last ....");
        // 025100 REC-LAST-RTN.

        //// 搬PUTFN-REC...到110044-REC... & 報表
        // 025200     MOVE    SPACES               TO   110044-REC .
        // 025300     MOVE    12                   TO   110044-CTL .
        // 025400     MOVE    PUTFN-CODE           TO   110044-CODE.
        // 025500     MOVE    PUTFN-BDATE          TO   110044-BDATE,WK-BDATE  .
        // 025600     MOVE    PUTFN-EDATE          TO   110044-EDATE,WK-EDATE  .
        // 025700     MOVE    PUTFN-TOTCNT         TO   110044-TOTCNT,WK-TOTCNT.
        // 025800     MOVE    PUTFN-TOTAMT         TO   110044-TOTAMT,WK-TOTAMT.
        String _110044Ctl = "12";
        String _110044Code = putfnCode;
        String _110044Bdate = putfnBdate;
        wkBdate = putfnBdate;
        String _110044Edate = putfnEdate;
        wkEdate = putfnEdate;
        String _110044Totcnt = putfnTotcnt;
        wkTotcnt = putfnTotcnt;
        String _110044Totamt = putfnTotamt;
        wkTotamt = putfnTotamt;

        //// 寫檔FD-110044(彙總資料 CTL=12)
        // 025900     WRITE   110044-REC.
        // 003800 01  110044-REC.
        // 003900      03  110044-CTL                   PIC 9(02).
        // 004000      03  110044-CODE                  PIC X(06).
        // 004100      03  110044-DATA.
        // 005500      03  110044-DATA-R REDEFINES  110044-DATA.
        // 005600       05 110044-BDATE                 PIC 9(08).
        // 005700       05 110044-EDATE                 PIC 9(08).
        // 005800       05 110044-TOTCNT                PIC 9(06).
        // 005900       05 110044-TOTAMT                PIC 9(13).
        // 006000       05 FILLER                       PIC X(81).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(_110044Ctl, 2));
        sb.append(formatUtil.padX(_110044Code, 6));
        sb.append(formatUtil.pad9(_110044Bdate, 8));
        sb.append(formatUtil.pad9(_110044Edate, 8));
        sb.append(formatUtil.pad9(_110044Totcnt, 6));
        sb.append(formatUtil.pad9(_110044Totamt, 13));
        sb.append(formatUtil.padX("", 81));
        fileCONV110044Contents.add(sb.toString());
        // 026000 REC-LAST-EXIT.
    }

    private void rpt_Dtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr rpt_Dtl ....");
        // 024500 RPT-DTL-RTN.
        //// 寫REPORTFL明細 (WK-DETAIL-LINE)
        // 024600     MOVE    SPACES        TO   REPORT-LINE.
        // 024700     WRITE   REPORT-LINE   FROM WK-DETAIL-LINE.
        // 011800 01 WK-DETAIL-LINE.
        // 011900    02 FILLER                          PIC X(02) VALUE SPACE.
        // 012000    02 WK-COUNT-P                      PIC ZZ,ZZZ.
        // 012100    02 FILLER                          PIC X(06) VALUE SPACE.
        // 012200    02 WK-CTL                          PIC 9(02).
        // 012300    02 FILLER                          PIC X(06) VALUE SPACE.
        // 012400    02 WK-CODE                         PIC X(06).
        // 012500    02 FILLER                          PIC X(04) VALUE SPACE.
        // 012600    02 WK-RCPTID                       PIC X(16).
        // 012700    02 FILLER                          PIC X(02) VALUE SPACE.
        // 012800    02 WK-DATE                         PIC Z99/99/99.
        // 012900    02 FILLER                          PIC X(02) VALUE SPACE.
        // 013000    02 WK-TIME                         PIC 99:99:99.
        // 013100    02 FILLER                          PIC X(05) VALUE SPACE.
        // 013200    02 WK-CLLBR                        PIC 9(03).
        // 013300    02 FILLER                          PIC X(04) VALUE SPACE.
        // 013400    02 WK-LMTDATE                      PIC Z99/99/99.
        // 013500    02 FILLER                          PIC X(01) VALUE SPACE.
        // 013600    02 WK-AMT                          PIC Z,ZZZ,ZZZ,ZZ9.
        // 013700    02 FILLER                          PIC X(07) VALUE SPACE.
        // 013800    02 WK-TXTYPE                       PIC X(01).
        // 013900    02 FILLER                          PIC X(10) VALUE SPACE.
        // 014000    02 WK-USERDATA                     PIC X(40).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkCountP, "ZZ,ZZZ"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.pad9(wkCtl, 2));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(wkCode, 6));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(wkRcptid, 16));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat(wkDate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 2));
        String wkTimeHH = formatUtil.pad9(wkTime, 6).substring(0, 2);
        String wkTimemm = formatUtil.pad9(wkTime, 6).substring(2, 4);
        String wkTimess = formatUtil.pad9(wkTime, 6).substring(4, 6);
        String wkTimeX = wkTimeHH + ":" + wkTimemm + ":" + wkTimess;
        sb.append(formatUtil.padX(wkTimeX, 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.pad9(wkCllbr, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat(wkLmtdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat(wkAmt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(wkTxtype, 1));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(wkUserdata, 40));
        fileReportContents.add(sb.toString());
        // 024800 RPT-DTL-EXIT.
    }

    private void time_Twist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr time_Twist ....");
        // 029700 TIME-TWIST-RTN.
        String wkPreTime_4_6 = formatUtil.pad9("" + wkPreTime, 6).substring(4, 6);
        String putfnTime_2_4 = formatUtil.pad9(putfnTime, 6).substring(2, 4);
        // 029800     IF  WK-PRE-TIME(5:2)           =    60
        if (parse.string2Integer(wkPreTime_4_6) == 60) {
            // 029900         ADD   40                   TO   WK-PRE-TIME  .
            wkPreTime = wkPreTime + 40;
        }

        // 030100     IF  PUTFN-TIME(3:2)             =    60
        if (parse.string2Integer(putfnTime_2_4) == 60) {
            // 030200         ADD   4000                 TO   WK-PRE-TIME  .
            wkPreTime = wkPreTime + 4000;
        }
        // 030300 TIME-TWIST-EXIT.
    }

    private void rec_Dtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr rec_Dtl ....");
        // 021700 REC-DTL-RTN.

        //// 搬PUTFN-REC...到110044-REC... & 報表
        // 021800     MOVE    WK-COUNT        TO   WK-COUNT-P                .
        // 021900     MOVE    PUTFN-CTL       TO   110044-CTL     , WK-CTL   .
        // 022000     MOVE    PUTFN-CODE      TO   110044-CODE    , WK-CODE  .
        // 022100*    PERFORM DATE-TWIST-RTN  THRU DATE-TWIST-RTN            .
        // 022150     MOVE    PUTFN-DATE      TO   110044-DATE,WK-DATE       .
        wkCountP = wkCount;
        String _110044Ctl = putfnCtl;
        wkCtl = putfnCtl;
        String _110044Code = putfnCode;
        wkCode = putfnCode;
        String _110044Date = putfnDate;
        wkDate = putfnDate;

        //// 若與上一筆銷帳編號相同時
        ////  A.以上一筆的時間加1
        ////  B.執行TIME-TWIST-RTN，調整正確時間格式
        //// 若與上一筆銷帳編號不同時
        ////  A.保留銷帳編號到WK-PRE-RCPTID
        ////  B.保留時間到WK-PRE-TIME
        //
        // 022200     IF      PUTFN-RCPTID    =    WK-PRE-RCPTID
        String _110044Rcptid = "";
        String _110044Time = "";
        if (wkPreRcptid.equals(putfnRcptid)) {
            // 022300       MOVE  PUTFN-RCPTID    TO   110044-RCPTID  , WK-RCPTID,
            // 022400       ADD   1               TO   WK-PRE-TIME
            _110044Rcptid = putfnRcptid;
            wkRcptid = putfnRcptid;
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPreTime = {} ", wkPreTime);

            wkPreTime = wkPreTime + 1;
            // 022500       PERFORM TIME-TWIST-RTN THRU TIME-TWIST-EXIT
            time_Twist();
            // 022600       MOVE  WK-PRE-TIME     TO   110044-TIME    , WK-TIME
            _110044Time = "" + wkPreTime;
            wkTime = formatUtil.pad9("" + wkPreTime, 6);
        } else {
            // 022700     ELSE
            // 022800       MOVE  PUTFN-RCPTID    TO   110044-RCPTID  , WK-RCPTID,
            // 022900                                  WK-PRE-RCPTID
            // 023000       MOVE  PUTFN-TIME      TO   110044-TIME    , WK-TIME  ,
            // 023100                                  WK-PRE-TIME               .
            _110044Rcptid = putfnRcptid;
            wkRcptid = putfnRcptid;
            wkPreRcptid = putfnRcptid;
            _110044Time = putfnTime;
            wkTime = putfnTime;
            wkPreTime = parse.string2Integer(putfnTime);
        }
        // 023300     MOVE    PUTFN-CLLBR     TO   110044-CLLBR   , WK-CLLBR   .
        // 023400     MOVE    PUTFN-LMTDATE   TO   110044-LMTDATE , WK-LMTDATE .
        // 023500     MOVE    PUTFN-AMT       TO   110044-AMT1    , WK-AMT     ,
        // 023600                                  110044-AMT2                 .
        // 023700     MOVE    PUTFN-USERDATA  TO   110044-USERDATA, WK-USERDATA.
        // 023800     MOVE    PUTFN-SITDATE   TO   110044-SITDATE .
        // 023900     MOVE    PUTFN-TXTYPE    TO   110044-TXTYPE  , WK-TXTYPE  .
        String _110044Cllbr = putfnCllbr;
        wkCllbr = putfnCllbr;
        String _110044Lmtdate = putfnLmtdate;
        wkLmtdate = putfnLmtdate;
        String _110044Amt1 = putfnAmt;
        wkAmt = putfnAmt;
        String _110044Amt2 = putfnAmt;
        String _110044Userdata = putfnUserdata;
        wkUserdata = putfnUserdata;
        String _110044Sitdate = putfnSitdate;
        String _110044Txtype = putfnTxtype;
        wkTxtype = putfnTxtype;
        //// 寫檔FD-110044(明細資料 CTL=11)
        //
        // 024000     WRITE   110044-REC.
        // 003800 01  110044-REC.
        // 003900      03  110044-CTL                   PIC 9(02).
        // 004000      03  110044-CODE                  PIC X(06).
        // 004100      03  110044-DATA.
        // 004200       05 110044-RCPTID                PIC X(16).
        // 004400       05 110044-DATE                  PIC 9(08).
        // 004500       05 110044-TIME                  PIC 9(06).
        // 004600       05 110044-CLLBR                 PIC 9(03).
        // 004700       05 110044-LMTDATE               PIC 9(06).
        // 004800       05 110044-AMT1                  PIC 9(10).
        // 004900       05 110044-USERDATA              PIC X(40).
        // 005100       05 110044-SITDATE               PIC 9(08).
        // 005200       05 110044-TXTYPE                PIC X(01).
        // 005300       05 110044-AMT2                  PIC 9(10).
        // 005400       05 FILLER                       PIC X(08).
        // 005500      03  110044-DATA-R REDEFINES  110044-DATA.
        // 005600       05 110044-BDATE                 PIC 9(08).
        // 005700       05 110044-EDATE                 PIC 9(08).
        // 005800       05 110044-TOTCNT                PIC 9(06).
        // 005900       05 110044-TOTAMT                PIC 9(13).
        // 006000       05 FILLER                       PIC X(81).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(_110044Ctl, 2));
        sb.append(formatUtil.padX(_110044Code, 6));
        sb.append(formatUtil.padX(_110044Rcptid, 16));
        sb.append(formatUtil.pad9(_110044Date, 8));
        sb.append(formatUtil.pad9(_110044Time, 6));
        sb.append(formatUtil.pad9(_110044Cllbr, 3));
        sb.append(formatUtil.pad9(_110044Lmtdate, 6));
        sb.append(formatUtil.pad9(_110044Amt1, 10));
        sb.append(formatUtil.padX(_110044Userdata, 40));
        sb.append(formatUtil.pad9(_110044Sitdate, 8));
        sb.append(formatUtil.pad9(_110044Txtype, 1));
        sb.append(formatUtil.pad9(_110044Amt2, 10));
        sb.append(formatUtil.padX("", 8));
        fileCONV110044Contents.add(sb.toString());
        // 024100
        // 024200 REC-DTL-EXIT.
    }

    private void page_Swh() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr page_Swh ....");
        // 021000 PAGE-SWH-RTN.

        //// WK-PCTL行數控制>50時
        ////  A.執行RPT-TITLE-RTN，寫REPORTFL表頭
        ////  B.搬1給WK-PCTL行數控制
        // 021100     IF        WK-PCTL         >      50
        if (wkPctl > 50) {
            // 021200       PERFORM RPT-TITLE-RTN   THRU   RPT-TITLE-EXIT
            // 021300       MOVE    1               TO     WK-PCTL.
            rpt_Title(PAGE_SEPARATOR);
            wkPctl = 1;
        }
        // 021400 PAGE-SWH-EXIT.
    }

    private void rpt_Title(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr rpt_Title ....");
        // 026900 RPT-TITLE-RTN.

        //// 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE3)
        // 027000     ADD        1                   TO     WK-PAGE.
        wkPage = wkPage + 1;

        // 027100     MOVE       SPACES              TO     REPORT-LINE.
        // 027200     WRITE      REPORT-LINE         AFTER  PAGE.
        sb = new StringBuilder();
        sb.append(pageFg);
        fileReportContents.add(sb.toString());

        // 027300     MOVE       SPACES              TO     REPORT-LINE.
        // 027400     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 008600 01 WK-TITLE-LINE1.
        // 008700    02 FILLER                          PIC X(30) VALUE SPACE.
        // 008800    02 FILLER                          PIC X(54) VALUE
        // 008900       " 代　收　明　細　表　－　中　華　電　信　公　司 ".
        // 009000    02 FILLER                          PIC X(25) VALUE SPACE.
        // 009100    02 FILLER                          PIC X(12) VALUE
        // 009200       "FORM : C030 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 30));
        sb.append(formatUtil.padX(" 代　收　明　細　表　－　中　華　電　信　公　司 ", 54));
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX("FORM : C030 ", 12));
        fileReportContents.add(sb.toString());

        // 027500     MOVE       SPACES              TO     REPORT-LINE.
        // 027600     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileReportContents.add("");

        // 027700     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 009300 01 WK-TITLE-LINE2.
        // 009400    02 FILLER                          PIC X(10) VALUE
        // 009500       " 分行別： ".
        // 009600    02 WK-PBRNO-P                      PIC X(03) VALUE "054".
        // 009700    02 FILLER                          PIC X(05) VALUE SPACE.
        // 009800    02 FILLER                          PIC X(13) VALUE
        // 009900       " 印表日期：  ".
        // 010000    02 WK-PDATE                        PIC 999/99/99.
        // 010100    02 FILLER                          PIC X(68) VALUE SPACE.
        // 010200    02 FILLER                          PIC X(08) VALUE
        // 010300       " 頁次  :".
        // 010400    02 WK-PAGE                         PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.padX("054", 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 印表日期：  ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "999/99/99"));
        sb.append(formatUtil.padX("", 68));
        sb.append(formatUtil.padX(" 頁次  :", 8));
        sb.append(formatUtil.pad9("" + wkPage, 4));
        fileReportContents.add(sb.toString());

        // 027800     MOVE       SPACES              TO     REPORT-LINE.
        // 027900     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileReportContents.add("");

        // 028000     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 010500 01 WK-TITLE-LINE3.
        // 010600    02 FILLER              PIC X(02) VALUE SPACE.
        // 010700    02 FILLER              PIC X(08) VALUE "  序號  ".
        // 010800    02 FILLER              PIC X(10) VALUE " 媒體格式 ".
        // 010900    02 FILLER              PIC X(10) VALUE " 代收類別 ".
        // 011000    02 FILLER              PIC X(19) VALUE "  銷帳編號         ".
        // 011100    02 FILLER              PIC X(11) VALUE "  日期     ".
        // 011200    02 FILLER              PIC X(11) VALUE "  時間     ".
        // 011300    02 FILLER              PIC X(09) VALUE " 代收行  ".
        // 011400    02 FILLER              PIC X(10) VALUE " 繳款期限 ".
        // 011500    02 FILLER              PIC X(18) VALUE "        金額      ".
        // 011600    02 FILLER              PIC X(10) VALUE " 繳款方式 ".
        // 011700    02 FILLER              PIC X(16) VALUE "    備註資料    ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX("  序號  ", 8));
        sb.append(formatUtil.padX(" 媒體格式 ", 10));
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX("  銷帳編號         ", 19));
        sb.append(formatUtil.padX("054", 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 印表日期：  ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "999/99/99"));
        sb.append(formatUtil.padX("", 68));
        sb.append(formatUtil.padX(" 頁次  :", 8));
        sb.append(formatUtil.pad9("" + wkPage, 4));
        fileReportContents.add(sb.toString());

        // 028100     MOVE       SPACES              TO     REPORT-LINE.
        // 028200     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 014100 01 WK-GATE-LINE.
        // 014200    02 FILLER                   PIC X(02) VALUE SPACE.
        // 014300    02 FILLER                   PIC X(163) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.makeGate("-", 163));
        fileReportContents.add(sb.toString());

        // 028300 RPT-TITLE-EXIT.
    }

    private void _0000_Main_Last() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV110044Lsnr _0000_Main_Last ....");
        // 020200 0000-MAIN-LAST.

        //// 關檔
        // 020300     CLOSE     FD-PUTFN      WITH SAVE.
        // 020400     CHANGE    ATTRIBUTE FILENAME OF FD-110044 TO WK-PUTDIR.
        // 020500     CLOSE     FD-110044     WITH SAVE.
        // 020600     CLOSE     REPORTFL      WITH SAVE.
        textFile.deleteFile(wkPutdir);
        try {
            textFile.writeFileContent(wkPutdir, fileCONV110044Contents, CHARSET);
            upload(wkPutdir, "DATA", "PUTFN");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(wkReportdir, fileReportContents, CHARSET_BIG5);
            upload(wkReportdir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 020700 0000-MAIN-EXIT.
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

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", "CL-BH-030");

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
