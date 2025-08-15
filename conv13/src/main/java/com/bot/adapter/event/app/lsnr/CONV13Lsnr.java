/* (C) 2024 */
package com.bot.adapter.event.app.lsnr;

import com.bot.adapter.event.app.evt.CONV13;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
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
import java.math.RoundingMode;
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
@Component("CONV13Lsnr")
@Scope("prototype")
public class CONV13Lsnr extends BatchListenerCase<CONV13> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateutil;
    @Autowired private ClmrService clmrService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV13 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String PATH_DOT = ".";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String STRING_410532 = "410532";
    private static final String STRING_033 = "CL-BH-033";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private final List<String> fileReportflContents = new ArrayList<>();
    private final List<String> file410532Contents = new ArrayList<>();
    private final String STRING_27Z1410532 = "27Z1410532";
    private String wkPutdir; // 讀檔路徑
    private String wkConvdir; // 產檔路徑
    private String reportflPath;
    private StringBuilder sb = new StringBuilder();
    private ClmrBus tClmr = new ClmrBus();
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkYYMMDD;
    private int wkBdateP;
    private int wkEdateP;
    private int wkTptcntP;
    private String wkCdate;
    private String wkFdate;
    private int wkDateP;
    private int wkStknoP;
    private int wkStkno;
    private int wkCllbrP;
    private int wkNoP;
    private int wkNo;
    private int wkPage;
    private int wkSerino;
    private int wkPctl;
    private int wkV1;
    private int wkV2;
    private int putfCtl;
    private int putfBdate;
    private int putfDate;
    private int putfEdate;
    private int putfTotcnt;
    private int putfCllbr;
    private int fdBhdateTbsdy;
    private int serino410532;
    private int date410532;
    private int no410532;
    private int stkno410532;
    private int stknum410532;
    private String processDate;
    private String tbsdy;
    private String wkPdate;
    private String wkPbrnoP;
    private String wkRcptid;
    private String wkTotcntP;
    private String wkSerinoP;
    private String cllbr410532;
    private String putfRcptid;
    private String filler410532;
    private BigDecimal wkTotamtP;
    private BigDecimal wkStkamtP;
    private BigDecimal putfTotamt;
    private BigDecimal putfAmt;
    private BigDecimal stkamt410532;
    private BigDecimal dbClmrUnit;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    @Override
    public void onApplicationEvent(CONV13 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV13Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV13 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV13Lsnr run");
        this.event = event;
        // 開啟檔案
        // 014500     OPEN INQUIRY BOTSRDB.
        // 014600     OPEN INPUT   FD-BHDATE.

        // DISPLAY訊息，包含在系統訊息中
        // 014700     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 014800     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 014900          STOP RUN.
        // 設定本營業日、檔名日期變數值
        // WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        // WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // 015000     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        // 015100     MOVE        WK-YYMMDD           TO   WK-FDATE,WK-CDATE.
        wkYYMMDD = processDate;
        wkFdate = formatUtil.pad9(wkYYMMDD, 7).substring(1, 7);
        wkCdate = formatUtil.pad9(wkYYMMDD, 7).substring(1, 7);
        reportflPath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + STRING_033;
        // CALL "DATE_TIME OF DTLIB"
        // 015200     CHANGE ATTRIBUTE TITLE OF "DTLIB" TO "*SYSTEM1/DTLIB.".
        // 015300     CALL "DATE_TIME OF DTLIB" USING PARA-DTREC
        // 015301     END-CALL/ PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期.

        // 015400     MOVE        PARA-YYMMDD         TO   WK-PDATE.
        wkPdate = dateutil.getNowStringRoc();

        // 015500*410532

        // 設定檔名變數值
        // 015600     MOVE        "27Z1410532"        TO   WK-PUTFILE,WK-CONVFILE.
        // 設定檔名
        // WK-PUTDIR  <-"DATA/CL/BH/PUTF/" +WK-FDATE+"/27Z1410532."
        // WK-CONVDIR <-"DATA/CL/BH/CONVF/"+WK-CDATE+"/27Z1410532."
        // 015700     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 015800     CHANGE  ATTRIBUTE FILENAME OF FD-410532 TO WK-CONVDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putDir + PATH_SEPARATOR + STRING_27Z1410532;
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
                        + STRING_27Z1410532; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        wkConvdir =
                fileDir
                        + "CONVF"
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + STRING_27Z1410532
                        + PATH_DOT;

        // 若FD-PUTF檔案存在，1.依代收類別讀取事業單位基本資料檔、2.執行410532-RTN
        // 015900     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 016000       FIND       DB-CLMR-IDX1  AT  DB-CLMR-CODE = "410532"
            tClmr = clmrService.findById(STRING_410532);
            // 016100       PERFORM    410532-RTN  THRU  410532-EXIT.
            code410532();
        }

        // 顯示訊息、關閉檔案、結束程式
        // 016400     DISPLAY "SYM/CL/BH/CONV13 GENERATE DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV13 GENERATE DATA/CL/BH/PUTF OK");
        // 016500     CLOSE   BOTSRDB.
        // 016600     CLOSE   FD-BHDATE  WITH SAVE.
        // 016700     STOP RUN.

        batchResponse();
    }

    private void code410532() {
        // 開啟輸出檔FD-410532、開啟輸入檔FD-PUTF、開啟輸出檔REPORTFL
        // 017000     OPEN      OUTPUT    FD-410532.
        // 017100     OPEN      INPUT     FD-PUTF.
        // 017200     OPEN      OUTPUT    REPORTFL.

        // WK-PBRNO-P:分行別 (WK-TITLE-LINE2'S變數)
        // 017300     MOVE      " 台銀忠孝 "      TO      WK-PBRNO-P.
        wkPbrnoP = " 台銀忠孝 ";

        // WK-PAGE,WK-PCTL,WK-SERINO清0
        // 017400     MOVE      0    TO   WK-PAGE,WK-PCTL,WK-SERINO.
        wkPage = 0;
        wkPctl = 0;
        wkSerino = 0;

        // 執行410532-WTIT-RTN，寫REPORTFL表頭
        // 017500     PERFORM   410532-WTIT-RTN   THRU   410532-WTIT-EXIT.
        wtit410532(PAGE_SEPARATOR);
        // 017600***  DETAIL  RECORD  *****
        // 017700 410532-NEXT.

        // 循序讀取FD-PUTF，直到檔尾，跳到410532-CLOSE
        // 017800     READ   FD-PUTF    AT  END  GO TO  410532-CLOSE.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);

        int cnt = 0;
        for (String detail : lines) {
            if (detail.length() < 120) {
                detail = formatUtil.padX(detail, 120);
            }
            cnt++;
            /*
            01 PUTF-REC TOTAL 120 BYTES
               03  PUTF-CTL	9(02)  0-2
               03  PUTF-CTL-R	REDEFINES PUTF-CTL
                  05 PUTF-CTL1	9(01)  0-1
                  05 PUTF-CTL2	9(01)  1-2
               03  PUTF-CODE	X(06)	代收類別  2-8
               03  PUTF-DATA	GROUP
                  05 PUTF-RCPTID	X(16)	銷帳號碼  8-24
                  05 PUTF-DATE	9(06)	代收日  24-30
                  05 PUTF-TIME	9(06)	代收時間  30-36
                  05 PUTF-CLLBR	9(03)	代收行  36-39
                  05 PUTF-LMTDATE	9(06)	繳費期限  39-45
                  05 PUTF-OLDAMT	9(08)	繳費金額  45-53
                  05 PUTF-USERDATA	X(40)	備註資料  53-93
                  05 PUTF-SITDATE	9(06)	原代收日  93-99
                  05 PUTF-TXTYPE	X(01)	帳務別  99-100
                  05 PUTF-AMT	9(10)	繳費金額  100-110
                  05 PUTF-FILLER	X(10)  110-120
               03  PUTF-DATA-R	REDEFINES PUTF-DATA
                  05 PUTF-BDATE	9(06)	挑檔起日  8-14
                  05 PUTF-EDATE	9(06)	挑檔迄日  14-20
                  05 PUTF-TOTCNT	9(06)	彙總筆數  20-26
                  05 PUTF-TOTAMT	9(13)	彙總金額  26-39
                  05 FILLER	X(81)  39-120
               03  PUTF-DATA-NODATA	REDEFINES PUTF-DATA
                  05 PUTF-NODATA	X(16)  8-24
                  05 PUTF-FILLER1	X(96)  24-120
             */
            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "0");
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
            putfCllbr =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(36, 39))
                                    ? detail.substring(36, 39)
                                    : "0");
            putfDate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(24, 30))
                                    ? detail.substring(24, 30)
                                    : "0");
            putfTotamt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(26, 39))
                                    ? detail.substring(26, 39)
                                    : "0");
            putfRcptid = parse.isNumeric(detail.substring(8, 24)) ? detail.substring(8, 24) : "0";
            putfTotcnt =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(20, 26))
                                    ? detail.substring(20, 26)
                                    : "0");
            putfAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(100, 110))
                                    ? detail.substring(100, 110)
                                    : "0");

            // PUTF-CTL=12 OR =22 彙總資料，GO TO 410532-LAST
            // 017900     IF        PUTF-CTL        = 12   OR = 22
            if (putfCtl == 12 || putfCtl == 22) {
                // 018000       GO TO   410532-LAST.
                _410532_Last();
                if (cnt == lines.size()) {
                    _410532_Close();
                    return;
                }
                // LOOP讀下一筆FD-PUTF
                // 019900     GO TO     410532-NEXT.
                continue;
            }

            // 累加行數
            // 018100     ADD       1               TO     WK-PCTL.
            wkPctl++;

            // IF  WK-PCTL > 50 執行410532-WTIT-RTN
            // 018200     IF        WK-PCTL         >      50
            if (wkPctl > 50) {
                // 換頁
                // 018300       MOVE    1               TO     WK-PCTL
                wkPctl = 1;
                // 018400       PERFORM 410532-WTIT-RTN THRU   410532-WTIT-EXIT.
                wtit410532(PAGE_SEPARATOR);
            }

            // 若PUTF-CTL = 11，執行410532-FILE-RTN，寫檔FD-410532 & REPORTFL報表明細
            // 若PUTF-CTL = 21，執行410532-RPT-RTN，寫REPORTFL報表明細
            // 018500     IF        PUTF-CTL        =      11
            // 018600       PERFORM 410532-FILE-RTN  THRU  410532-FILE-EXIT
            // 018700     ELSE
            // 018800       PERFORM 410532-RPT-RTN   THRU  410532-RPT-EXIT.
            if (putfCtl == 11) {
                file410532();
            } else {
                rpt410532();
            }

            // LOOP讀下一筆FD-PUTF
            // 018900     GO     TO     410532-NEXT.
            // 019000***  LAST    RECORD  *****
            if (cnt == lines.size()) {
                _410532_Close();
            }
        }
    }

    private void _410532_Last() {
        // 019100 410532-LAST.
        // 寫REPORTFL表尾(TOTAL:WK-TOTAL-LINE)
        // 019200     MOVE      PUTF-BDATE           TO   WK-BDATE-P.
        wkBdateP = putfBdate;
        // 019300     MOVE      PUTF-EDATE           TO   WK-EDATE-P.
        wkEdateP = putfEdate;
        // 019400     MOVE      PUTF-TOTCNT          TO   WK-TOTCNT-P.
        wkTotcntP = "" + putfTotcnt;
        // 019500     MOVE      PUTF-TOTAMT          TO   WK-TOTAMT-P.
        wkTotamtP = putfTotamt;
        // 019600     MOVE      SPACES               TO   REPORT-LINE.
        // 019700     WRITE     REPORT-LINE         AFTER  2 LINE.
        fileReportflContents.add("");
        fileReportflContents.add("");
        // 019800     WRITE     REPORT-LINE         FROM  WK-TOTAL-LINE.
        // 012700 01 WK-TOTAL-LINE.
        sb = new StringBuilder();
        fileReportflContents.add(sb.toString());
        // 012800    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 012900    02 FILLER                   PIC X(13) VALUE " 資料起日  : ".
        sb.append(formatUtil.padX(" 資料起日  : ", 13));
        // 013000    02 WK-BDATE-P               PIC 99/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(wkBdateP), "99/99/99"));
        // 013100    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 013200    02 FILLER                   PIC X(11) VALUE " 資料迄日 :".
        sb.append(formatUtil.padX(" 資料迄日 :", 11));
        // 013300    02 WK-EDATE-P               PIC 99/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(wkEdateP), "99/99/99"));
        // 013400    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 013500    02 FILLER                   PIC X(08) VALUE "  筆數 :".
        sb.append(formatUtil.padX("  筆數 :", 8));
        // 013600    02 WK-TOTCNT-P              PIC ZZZ,ZZ9.B.
        sb.append(reportUtil.customFormat(wkTotcntP, "ZZZ,ZZ9.9"));
        // 013700    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 013800    02 FILLER                   PIC X(13) VALUE "  代收金額 : ".
        sb.append(formatUtil.padX("  代收金額 : ", 13));
        // 013900    02 WK-TOTAMT-P              PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.B.
        sb.append(reportUtil.customFormat(String.valueOf(wkTotamtP), "Z,ZZZ,ZZZ,ZZZ,ZZ9.9"));
        fileReportflContents.add(sb.toString());
    }

    private void _410532_Close() {
        // 020000 410532-CLOSE.
        // 關閉檔案
        // 020100     CLOSE  FD-PUTF    WITH  SAVE.
        // 020200     CHANGE ATTRIBUTE FILENAME OF FD-410532 TO WK-PUTDIR.
        // 020300     CLOSE  FD-410532  WITH  SAVE.
        // 020400     CLOSE  REPORTFL   WITH  SAVE.
        // 020500 410532-EXIT.
        textFile.deleteDir(wkPutdir); // 刪除檔案
        try {
            textFile.writeFileContent(wkPutdir, file410532Contents, CHARSET);
            upload(wkPutdir, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(reportflPath, fileReportflContents, CHARSET_BIG5);
            upload(reportflPath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void file410532() {
        // 從PUTF-REC...搬資料到410532-REC...
        // 從PUTF-REC...搬資料到WK-DETAIL-LINE for REPORTFL
        // 022800     MOVE      SPACES             TO    410532-REC.
        // 022900     MOVE      PUTF-CLLBR         TO    410532-CLLBR,WK-CLLBR-P.
        cllbr410532 = String.valueOf(putfCllbr);
        wkCllbrP = putfCllbr;
        // 023000     ADD       1                  TO    WK-SERINO.
        wkSerino++;
        // 023100     MOVE      WK-SERINO          TO    410532-SERINO,WK-SERINO-P.
        serino410532 = wkSerino;
        wkSerinoP = String.valueOf(wkSerino);
        // 023200     MOVE      PUTF-DATE          TO    410532-DATE,WK-DATE-P.
        date410532 = putfDate;
        wkDateP = putfDate;
        // 023300     MOVE      PUTF-RCPTID        TO    WK-RCPTID.
        wkRcptid = String.valueOf(putfRcptid);
        // 023400     MOVE      WK-NO              TO    410532-NO,WK-NO-P.
        no410532 = wkNo;
        wkNoP = wkNo;
        // 023500     MOVE      WK-STKNO           TO    410532-STKNO,WK-STKNO-P.
        stkno410532 = wkStkno;
        wkStknoP = wkStkno;
        // PUTF-AMT除以DB-CLMR-UNIT單位金額(每股金額)，商數放WK-V1，餘數放WK-V2
        // 023520     DIVIDE    PUTF-AMT  BY  DB-CLMR-UNIT  GIVING   WK-V1
        // 023540                                           REMAINDER  WK-V2.
        dbClmrUnit = tClmr.getUnit();
        wkV1 = putfAmt.divide(dbClmrUnit, 0, RoundingMode.DOWN).intValue();
        wkV2 = putfAmt.remainder(dbClmrUnit).intValue();

        // 023560     IF        WK-V2           NOT  =   0
        if (wkV2 != 0) {
            // 023600       COMPUTE 410532-STKNUM  =   WK-V1 + 1
            stknum410532 = wkV1 + 1;
        } else {
            // 023620     ELSE
            // 023640       MOVE    WK-V1    TO    410532-STKNUM.
            stknum410532 = wkV1;
        }

        // 023700     MOVE      PUTF-AMT           TO    410532-STKAMT,WK-STKAMT-P.
        stkamt410532 = putfAmt;
        wkStkamtP = putfAmt;

        // 寫檔FD-410532(明細資料)
        // 023800     WRITE     410532-REC.
        write410532();
        // 023900     MOVE      SPACE              TO    REPORT-LINE.
        // 寫REPORTFL報表明細(WK-DETAIL-LINE)
        // 024000     WRITE     REPORT-LINE     FROM     WK-DETAIL-LINE.
        wkDetailLine();
        // 024100 410532-FILE-EXIT.
    }

    private void write410532() {
        // 003400 01  410532-REC.
        sb = new StringBuilder();
        // 003500    03  410532-CLLBR                   PIC X(04).
        sb.append(formatUtil.padX(cllbr410532, 4));
        // 003600    03  410532-SERINO                  PIC 9(06).
        sb.append(formatUtil.pad9(String.valueOf(serino410532), 6));
        // 003700    03  410532-DATE                    PIC 9(06).
        sb.append(formatUtil.pad9(String.valueOf(date410532), 6));
        // 003800    03  410532-NO                      PIC 9(08).
        sb.append(formatUtil.pad9(String.valueOf(no410532), 8));
        // 003900    03  410532-STKNO                   PIC 9(06).
        sb.append(formatUtil.pad9(String.valueOf(stkno410532), 6));
        // 004000    03  410532-STKNUM                  PIC 9(11)V99.
        sb.append(formatUtil.pad9(String.valueOf(stknum410532), 11));
        // 004100    03  410532-STKAMT                  PIC 9(11)V99.
        sb.append(formatUtil.pad9(String.valueOf(stkamt410532), 11));
        // 004200    03  410532-FILLER                  PIC X(24).
        sb.append(formatUtil.padX("", 24));
        file410532Contents.add(sb.toString());
    }

    private void rpt410532() {
        // 搬相關欄位到WK-DETAIL-LINE for REPORTFL
        // 024500     MOVE      PUTF-CLLBR         TO    WK-CLLBR-P.
        wkCllbrP = putfCllbr;
        // 024600     ADD       1                  TO    WK-SERINO.
        wkSerino++;
        // 024700     MOVE      WK-SERINO          TO    WK-SERINO-P.
        wkSerinoP = String.valueOf(wkSerino);
        // 024800     MOVE      PUTF-DATE          TO    WK-DATE-P.
        wkDateP = putfDate;
        // 024900     MOVE      PUTF-RCPTID        TO    WK-RCPTID.
        wkRcptid = String.valueOf(putfRcptid);
        // 025000     MOVE      WK-NO              TO    WK-NO-P.
        wkNoP = wkNo;
        // 025100     MOVE      WK-STKNO           TO    WK-STKNO-P.
        wkStknoP = wkStkno;
        // 025300     MOVE      PUTF-AMT           TO    WK-STKAMT-P.
        wkStkamtP = putfAmt;
        // 025400     MOVE      SPACE              TO    REPORT-LINE.
        // 寫REPORTFL報表明細(WK-DETAIL-LINE)
        // 025500     WRITE     REPORT-LINE     FROM     WK-DETAIL-LINE.
        wkDetailLine();
        // 025600 410532-RPT-EXIT.
    }

    private void wtit410532(String pageFg) {
        // 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE4)
        // 020900     ADD        1                   TO     WK-PAGE.
        wkPage++;
        // 021000     MOVE       SPACES              TO     REPORT-LINE.
        // 021100     WRITE      REPORT-LINE         AFTER  PAGE.
        sb = new StringBuilder();
        sb.append(pageFg);
        fileReportflContents.add(sb.toString());
        // 021200     MOVE       SPACES              TO     REPORT-LINE.
        // 021300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 007300 01 WK-TITLE-LINE1.
        sb = new StringBuilder();
        // 007400    02 FILLER                          PIC X(52) VALUE SPACE.
        sb.append(formatUtil.padX("", 52));
        // 007500    02 FILLER                          PIC X(32) VALUE
        // 007600       "   代　收　股　款　明　細　表   ".
        sb.append(formatUtil.padX("   代　收　股　款　明　細　表   ", 32));
        fileReportflContents.add(sb.toString());
        // 021400     MOVE       SPACES              TO     REPORT-LINE.
        // 021500     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileReportflContents.add("");
        // 021600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 007700 01 WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 007800    02 FILLER                          PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 007900    02 FILLER                          PIC X(12) VALUE
        // 008000       " 主辦分行： ".
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        // 008100    02 WK-PBRNO-P                      PIC X(10).
        sb.append(formatUtil.padX(wkPbrnoP, 10));
        // 008200    02 FILLER                          PIC X(69) VALUE SPACE.
        sb.append(formatUtil.padX("", 69));
        // 008300    02 FILLER                          PIC X(12) VALUE
        // 008400       "FORM : C033 ".
        sb.append(formatUtil.padX("FORM : C033 ", 12));
        fileReportflContents.add(sb.toString());
        // 021700     MOVE       SPACES              TO     REPORT-LINE.
        // 021800     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 008500 01 WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 008600    02 FILLER                          PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 008700    02 FILLER                          PIC X(12) VALUE
        // 008800       " 印表日期： ".
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        // 008900    02 FILLER                          PIC X(01) VALUE SPACE.
        sb.append(formatUtil.padX("", 1));
        // 009000    02 WK-PDATE                        PIC 99/99/99.
        sb.append(reportUtil.customFormat(wkPdate, "99/99/99"));
        // 009100    02 FILLER                          PIC X(69) VALUE SPACE.
        sb.append(formatUtil.padX("", 69));
        // 009200    02 FILLER                          PIC X(08) VALUE
        // 009300       " 頁次  :".
        sb.append(formatUtil.padX(" 頁次  :", 8));
        // 009400    02 WK-PAGE                         PIC 9(04).
        sb.append(formatUtil.pad9(String.valueOf(wkPage), 4));
        fileReportflContents.add(sb.toString());
        // 021900     MOVE       SPACES              TO     REPORT-LINE.
        // 022000     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileReportflContents.add("");
        // 022100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        // 009500 01 WK-TITLE-LINE4.
        sb = new StringBuilder();
        // 009600    02 FILLER              PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX("", 3));
        // 009700    02 FILLER              PIC X(10) VALUE " 代收行別 ".
        sb.append(formatUtil.padX(" 代收行別 ", 10));
        // 009800    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 009900    02 FILLER              PIC X(10) VALUE " 繳款序號 ".
        sb.append(formatUtil.padX(" 繳款序號 ", 10));
        // 010000    02 FILLER              PIC X(06) VALUE SPACES.
        sb.append(formatUtil.padX("", 6));
        // 010100    02 FILLER              PIC X(10) VALUE " 繳款日期 ".
        sb.append(formatUtil.padX(" 繳款日期 ", 10));
        // 010200    02 FILLER              PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX("", 6));
        // 010300    02 FILLER              PIC X(12) VALUE " 繳款書編號 ".
        sb.append(formatUtil.padX(" 繳款書編號 ", 12));
        // 010400    02 FILLER              PIC X(10) VALUE " 股東戶號 ".
        sb.append(formatUtil.padX(" 股東戶號 ", 10));
        // 010500    02 FILLER              PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX("", 6));
        // 010600    02 FILLER              PIC X(14) VALUE " 實際繳款金額 ".
        sb.append(formatUtil.padX(" 實際繳款金額 ", 14));
        // 010700    02 FILLER              PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX("", 6));
        // 010800    02 FILLER              PIC X(10) VALUE " 備　　註 ".
        sb.append(formatUtil.padX(" 備　　註 ", 10));
        fileReportflContents.add(sb.toString());
        // 022200     MOVE       SPACES              TO     REPORT-LINE.
        // 022300     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 012400 01 WK-GATE-LINE.
        sb = new StringBuilder();
        // 012500    02 FILLER                   PIC X(03) VALUE SPACE.
        sb.append(formatUtil.padX("", 3));
        // 012600    02 FILLER                   PIC X(101) VALUE ALL "-".
        sb.append(reportUtil.makeGate("-", 101));
        fileReportflContents.add(sb.toString());
    }

    private void wkDetailLine() {
        // 010900 01 WK-DETAIL-LINE.
        // 011000    02 FILLER                          PIC X(05) VALUE SPACE.
        sb = new StringBuilder();
        // 011100    02 WK-CLLBR-P                      PIC 9(03).
        sb.append(formatUtil.pad9(String.valueOf(wkCllbrP), 9));
        // 011200    02 FILLER                          PIC X(09) VALUE SPACE.
        sb.append(formatUtil.padX("", 9));
        // 011300    02 WK-SERINO-P                     PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(wkSerinoP, "ZZZ,ZZ9"));
        // 011400    02 FILLER                          PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX("", 10));
        // 011500    02 WK-DATE-P                       PIC 9(06).
        sb.append(formatUtil.pad9(String.valueOf(wkDateP), 6));
        // 011600    02 FILLER                          PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX("", 10));
        // 011700    02 WK-NO-P                         PIC 9(08).
        sb.append(formatUtil.pad9(String.valueOf(wkNoP), 8));
        // 011800    02 FILLER                          PIC X(02) VALUE ALL "-".
        sb.append(reportUtil.makeGate("-", 2));
        // 011900    02 WK-STKNO-P                      PIC 9(06).
        sb.append(formatUtil.pad9(String.valueOf(wkStknoP), 6));
        // 012000    02 FILLER                          PIC X(09) VALUE SPACE.
        sb.append(formatUtil.padX("", 9));
        // 012100    02 WK-STKAMT-P                     PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(wkStkamtP), "Z,ZZZ,ZZZ,ZZ9"));
        // 012200    02 FILLER                          PIC X(08) VALUE SPACE.
        sb.append(formatUtil.padX("", 8));
        // 012300    02 FILLER                          PIC X(08) VALUE ALL "_".
        sb.append(reportUtil.makeGate("-", 8));
        fileReportflContents.add(sb.toString());
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

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", STRING_033);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
