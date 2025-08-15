/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTRPTDTL;
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
import java.math.BigDecimal;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTRPTDTLLsnr")
@Scope("prototype")
public class OUTRPTDTLLsnr extends BatchListenerCase<OUTRPTDTL> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private OUTRPTDTL event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Value("${fsapFile.gn.dwl.directory}")
    private String fsapfileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CL012_FILE_PATH = "CL012"; // 目錄
    private static final String _003_FILE_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_KPUTH = "KPUTH."; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME_053 = "CL-BH-053"; // 產檔檔名
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private Map<String, String> textMap;
    private String[] wkParamL;
    private List<String> fileOUTRPTDTLContents; //  檔案內容
    private final String PAGE_SEPARATOR = "\u000C";
    private Boolean isNewPage = false;
    private String wkTaskDate;
    private String wkKdate;
    private String wkKdate7;
    private String wkRptPdate;
    private int wkRptTotpage;
    private int wkRptPage;
    private int wkRptCount;
    private int wkReccnt;
    private int wkSubcnt;
    private int wkRptSubcnt;
    private int wkTotcnt;
    private int wkPrePbrno;
    private String wkRptCode;
    private int wkRptUdate;
    private String wkRptRcptid;
    private BigDecimal wkRptAmt;
    private int wkRptLdate;
    private int wkRptSitdate;
    private String wkRptTxtype;
    private String wkRptUserdata;
    private int wkRptPbrno;
    private int wkRptTotcnt;

    // ----KPUTH----
    private int kputhPbrno;
    private String kputhCode;
    private int kputhDate;
    private String kputhRcptid;
    private BigDecimal kputhAmt;
    private int kputhUpldate;
    private int kputhSitdate;
    private String kputhTxtype;
    private String kputhUserData;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTRPTDTL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTRPTDTL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr run");
        // 004100 0000-START-RTN.
        if (!init(event)) {

            batchResponse();
            return;
        }
        // FD-KPUTH檔案存在，執行0000-MAIN-RTN
        if (textFile.exists(inputFilePath)) {
            main();
            try {
                textFile.writeFileContent(outputFilePath, fileOUTRPTDTLContents, CHARSET_BIG5);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        batchResponse();
    }

    private Boolean init(OUTRPTDTL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2007Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkTaskDate = getrocdate(parse.string2Integer(textMap.get("DATE")));
        String filename = textMap.get("FILENAME"); // TODO: 待確認BATCH參數名稱

        // 011600     MOVE   WK-TASK-DATE       TO       WK-KDATE    ,
        // 011700                                        WK-RPT-PDATE.
        wkKdate = formatUtil.pad9(wkTaskDate, 8);
        wkKdate7 = wkTaskDate;
        wkRptPdate = wkTaskDate;

        String sourceFtpPath =
                "NCL" + File.separator + wkKdate + File.separator + filename; // 來源檔在FTP的位置
        File restFile = downloadFromSftp(sourceFtpPath);
        inputFilePath = getLocalPath(restFile);
        copyData();
        // 讀檔路徑
        // 011800     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO WK-KPUTHDIR.
        // 003100  01 WK-KPUTHDIR.
        // 003200     03 FILLER                          PIC X(22)
        // 003300                         VALUE "DATA/GN/DWL/CL012/003/".
        // 003400     03 WK-KDATE                        PIC 9(07).
        // 003500     03 FILLER                          PIC X(07)
        // 003600                                  VALUE "/KPUTH.".
        //        inputFilePath =
        //                fsapfileDir
        //                        + CL012_FILE_PATH
        //                        + PATH_SEPARATOR
        //                        + _003_FILE_PATH
        //                        + PATH_SEPARATOR
        //                        + wkKdate
        //                        + PATH_SEPARATOR
        //                        + FILE_INPUT_NAME_KPUTH;
        // 產檔路徑
        // 002200      VALUE  OF  TITLE  IS  "BD/CL/BH/053."
        outputFilePath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + wkKdate7
                        + PATH_SEPARATOR
                        + FILE_OUTPUT_NAME_053;
        // 檔案內容
        fileOUTRPTDTLContents = new ArrayList<>();
        return true;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr main");
        // 012600     MOVE    0                   TO     WK-RPT-TOTPAGE,
        // 012700                                        WK-RPT-PAGE   .
        // 012800     MOVE    0                   TO     WK-RPT-COUNT  ,
        // 012900                                        WK-RECCNT     ,
        // 013000                                        WK-SUBCNT     ,
        // 013100                                        WK-TOTCNT     ,
        // 013200                                        WK-PRE-PBRNO  .
        wkRptTotpage = 0;
        wkRptPage = 0;
        wkRptCount = 0;
        wkReccnt = 0;
        wkSubcnt = 0;
        wkTotcnt = 0;
        wkPrePbrno = 0;

        // SORT  OBJ/CL/BH/OUTING/SORT/CL012
        File tmpFile = new File(inputFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(119, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(17, 16, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET_UTF8);

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET_UTF8);

        int cnt = 0;
        for (String detail : lines) {
            // 013500 0000-MAIN-LOOP.
            // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LFAST
            // 013600     READ    FD-KPUTH  AT END    GO TO  0000-MAIN-LFAST   .

            cnt++;

            // 03 KPUTH-PUTFILE	GROUP 10
            //  05 KPUTH-PUTTYPE	9(02) 0-2
            //  05 KPUTH-PUTNAME	X(08) 2-10
            // 03 KPUTH-PUTFILE-R1 REDEFINES KPUTH-PUTFILE
            //  05 KPUTH-ENTPNO	X(10) 0-10
            // 03 KPUTH-CODE	X(06) 10-16
            // 03 KPUTH-RCPTID	X(16) 16-32
            // 03 KPUTH-DATE	9(07) 32-39
            // 03 KPUTH-TIME	9(06) 39-45
            // 03 KPUTH-CLLBR	9(03) 45-48
            // 03 KPUTH-LMTDATE	9(06) 48-54
            // 03 KPUTH-AMT	9(10) 54-64
            // 03 KPUTH-USERDATA	X(40) 64-104
            // 03 KPUTH-USERDATE-R1 REDEFINES KPUTH-USERDATA
            //  05 KPUTH-SMSERNO	X(03) 64-67
            //  05 KPUTH-RETAILNO	X(08) 67-75
            //  05 KPUTH-BARCODE3	X(15) 75-90
            //  05 KPUTH-FILLER	X(14) 90-104
            // 03 KPUTH-SITDATE	9(07) 104-111
            // 03 KPUTH-TXTYPE	X(01) 111-112
            // 03 KPUTH-SERINO	9(06) 112-118
            // 03 KPUTH-PBRNO	9(03) 118-121
            // 03 KPUTH-UPLDATE	9(07) 121-128
            // 03 KPUTH-FEETYPE	9(01) 128-129
            // 03 KPUTH-FEEO2L	9(05)V99

            kputhPbrno =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(118, 121))
                                    ? detail.substring(118, 121)
                                    : "0");
            kputhCode = detail.substring(10, 16);
            kputhDate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(32, 39))
                                    ? detail.substring(32, 39)
                                    : "0");
            kputhRcptid = detail.substring(16, 32);
            kputhAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(54, 64))
                                    ? detail.substring(54, 64)
                                    : "0");
            kputhUpldate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(121, 128))
                                    ? detail.substring(121, 128)
                                    : "0");
            kputhSitdate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(104, 111))
                                    ? detail.substring(104, 111)
                                    : "0");
            kputhTxtype = detail.substring(111, 112);
            kputhUserData = detail.substring(64, 104);

            // 累加頁數筆數
            // 013700     ADD     1                   TO     WK-RPT-COUNT      ,
            // 013800                                        WK-RECCNT         ,
            // 013900                                        WK-TOTCNT         .
            wkRptCount = wkRptCount + 1;
            wkReccnt = wkReccnt + 1;
            wkTotcnt = wkTotcnt + 1;

            // 換頁
            // 014000     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
            pageswh();

            // 寫報表明細
            // 014100     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT   .
            dtlin();

            // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LFAST
            if (cnt == lines.size()) {
                // 014300 0000-MAIN-LFAST.
                // 寫報表表尾

                // 014400     PERFORM 6000-TAIL-RTN       THRU   6000-TAIL-EXIT.
                tail();

                // 014500 0000-MAIN-EXIT.
            }
            // 014200     GO TO   0000-MAIN-LOOP.
        }
    }

    private void pageswh() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr pageswh");
        // 016200 3000-PAGESWH-RTN.

        // 016300*   換主辦行或  50  筆換頁
        // 016400     IF    (    WK-PRE-PBRNO        =      0        )
        if (wkPrePbrno == 0) {
            // 016500       ADD      1                   TO     WK-RPT-TOTPAGE
            // 016600       ADD      1                   TO     WK-RPT-PAGE
            // 016700       MOVE     KPUTH-PBRNO         TO     WK-RPT-PBRNO
            // 016800       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            // 016900       GO TO 3000-PAGESWH-EXIT.
            wkRptTotpage = wkRptTotpage + 1;
            wkRptPage = wkRptPage + 1;
            wkRptPbrno = kputhPbrno;
            title();
            return;
        }

        // 017100     IF    (    KPUTH-PBRNO         NOT =  WK-PRE-PBRNO  )
        // 017200       AND (    WK-PRE-PBRNO        NOT =  0             )
        if (kputhPbrno != wkPrePbrno && wkPrePbrno != 0) {
            // 017300       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail();

            // 017400       MOVE     SPACES              TO     REPORT-LINE
            // 017500       WRITE    REPORT-LINE         AFTER  PAGE
            fileOUTRPTDTLContents.add("\u000c");

            // 017600       MOVE     KPUTH-PBRNO         TO     WK-RPT-PBRNO
            // 017700       MOVE     1                   TO     WK-RECCNT
            // 017800       MOVE     1                   TO     WK-RPT-COUNT
            // 017900       ADD      1                   TO     WK-RPT-TOTPAGE
            // 018000       MOVE     1                   TO     WK-RPT-PAGE
            wkRptPbrno = kputhPbrno;
            wkReccnt = 1;
            wkRptCount = 1;
            wkRptTotpage = wkRptTotpage + 1;
            wkRptPage = 1;

            // 018100       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title();

            // 018200       GO TO 3000-PAGESWH-EXIT.
            return;
        }
        // 018400     IF    (    WK-RECCNT           >      50       )
        if (wkReccnt > 50) {
            // 018500       MOVE     SPACES              TO     REPORT-LINE
            // 018600       WRITE    REPORT-LINE         AFTER  PAGE
            fileOUTRPTDTLContents.add("\u000c");

            // 018700       MOVE     1                   TO     WK-RECCNT
            // 018800       ADD      1                   TO     WK-RPT-TOTPAGE
            // 018900       ADD      1                   TO     WK-RPT-PAGE
            wkReccnt = 1;
            wkRptTotpage = wkRptTotpage + 1;
            wkRptPage = wkRptPage + 1;

            // 019000       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title();

            // 019100       GO TO 3000-PAGESWH-EXIT.
            return;
        }
    }

    private void dtlin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr title");
        // 020600 5000-DTLIN-RTN.
        // 寫報表明細

        // 020700     ADD       1               TO    WK-SUBCNT      .
        // 020800     MOVE      KPUTH-PBRNO     TO    WK-PRE-PBRNO   .
        // 020900     MOVE      KPUTH-CODE      TO    WK-RPT-CODE    .
        // 021000     MOVE      KPUTH-DATE      TO    WK-RPT-UDATE   .
        // 021100     MOVE      KPUTH-RCPTID    TO    WK-RPT-RCPTID  .
        // 021200     MOVE      KPUTH-AMT       TO    WK-RPT-AMT     .
        // 021300     MOVE      KPUTH-UPLDATE   TO    WK-RPT-LDATE   .
        // 021400     MOVE      KPUTH-SITDATE   TO    WK-RPT-SITDATE .
        // 021500     MOVE      KPUTH-TXTYPE    TO    WK-RPT-TXTYPE  .
        // 021600     MOVE      KPUTH-USERDATA  TO    WK-RPT-USERDATA.
        wkSubcnt = wkSubcnt + 1;
        wkPrePbrno = kputhPbrno;
        wkRptCode = kputhCode;
        wkRptUdate = kputhDate;
        wkRptRcptid = kputhRcptid;
        wkRptAmt = kputhAmt;
        wkRptLdate = kputhUpldate;
        wkRptSitdate = kputhSitdate;
        wkRptTxtype = kputhTxtype;
        wkRptUserdata = kputhUserData;

        // 021700     WRITE     REPORT-LINE     FROM  WK-DETAIL-LINE.
        // 007600 01 WK-DETAIL-LINE.
        // 007700    02 FILLER                          PIC X(01) VALUE SPACE.
        // 007800    02 WK-RPT-COUNT                    PIC 9(06).
        // 007900    02 FILLER                          PIC X(02) VALUE SPACE.
        // 008000    02 WK-RPT-CODE                     PIC X(06).
        // 008100    02 FILLER                          PIC X(02) VALUE SPACE.
        // 008200    02 WK-RPT-RCPTID                   PIC X(16).
        // 008300    02 FILLER                          PIC X(02) VALUE SPACE.
        // 008400    02 WK-RPT-UDATE                    PIC Z99/99/99.
        // 008500    02 FILLER                          PIC X(02) VALUE SPACE.
        // 008600    02 WK-RPT-LDATE                    PIC Z99/99/99.
        // 008700    02 FILLER                          PIC X(02) VALUE SPACE.
        // 008800    02 WK-RPT-SITDATE                  PIC Z99/99/99.
        // 008900    02 FILLER                          PIC X(06) VALUE SPACE.
        // 009000    02 WK-RPT-TXTYPE                   PIC X(01).
        // 009100    02 FILLER                          PIC X(02) VALUE SPACE.
        // 009200    02 WK-RPT-AMT                      PIC Z,ZZZ,ZZZ,ZZ9.
        // 009300    02 FILLER                          PIC X(02) VALUE SPACE.
        // 009400    02 WK-RPT-USERDATA                 PIC X(40).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9("" + wkRptCount, 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkRptCode, 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkRptRcptid, 16));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptUdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptLdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptSitdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(wkRptTxtype, 1));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptAmt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkRptUserdata, 40));
        fileOUTRPTDTLContents.add(sb.toString());

        // 021800 5000-DTLIN-EXIT.
    }

    private void title() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr title");
        // 014800 2000-TITLE-RTN.

        // 寫報表表頭
        // 014900     MOVE       SPACES              TO     REPORT-LINE.
        // 015000     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(45) VALUE SPACE.
        // 004300    02 FILLER                          PIC X(54) VALUE
        // 004400       "  外部代收明細報表  ".
        // 004500    02 FILLER                          PIC X(22) VALUE SPACE.
        // 004600    02 FILLER                          PIC X(12) VALUE
        // 004700       "FORM : C053 ".
        sb = new StringBuilder();
        sb.append(isNewPage ? PAGE_SEPARATOR : " "); // 預留換頁符號
        sb.append(formatUtil.padX(" ", 45));
        sb.append(formatUtil.padX("  外部代收明細報表  ", 54));
        sb.append(formatUtil.padX(" ", 22));
        sb.append(formatUtil.padX("FORM : C053 ", 12));
        fileOUTRPTDTLContents.add(sb.toString());

        // 015100     MOVE       SPACES              TO     REPORT-LINE.
        // 015200     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileOUTRPTDTLContents.add("");

        // 015300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004800 01 WK-TITLE-LINE2.
        // 004900    02 FILLER                          PIC X(10) VALUE
        // 005000       "  主辦行：  ".
        // 005100    02 WK-RPT-PBRNO                    PIC 9(03).
        // 005200    02 FILLER                          PIC X(04) VALUE SPACE.
        // 005300    02 FILLER                          PIC X(13) VALUE
        // 005400       "  印表日期：   ".
        // 005500    02 WK-RPT-PDATE                    PIC Z99/99/99.
        // 005600    02 FILLER                          PIC X(65) VALUE SPACE.
        // 005700    02 FILLER                          PIC X(09) VALUE
        // 005800       "  總頁次  :".
        // 005900    02 WK-RPT-TOTPAGE                  PIC 9(04).
        // 006000    02 FILLER                          PIC X(11) VALUE
        // 006100       "    分頁次  :".
        // 006200    02 WK-RPT-PAGE                     PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("  主辦行：  ", 10));
        sb.append(formatUtil.pad9("" + wkRptPbrno, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("  印表日期：   ", 13));
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        sb.append(formatUtil.padX(" ", 65));
        sb.append(formatUtil.padX("  總頁次  :", 9));
        sb.append(formatUtil.pad9("" + wkRptTotpage, 4));
        sb.append(formatUtil.padX("    分頁次  :", 11));
        sb.append(formatUtil.pad9("" + wkRptPage, 4));
        fileOUTRPTDTLContents.add(sb.toString());

        // 015400     MOVE       SPACES              TO     REPORT-LINE.
        // 015500     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileOUTRPTDTLContents.add("");

        // 015600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 006300 01 WK-TITLE-LINE3.
        // 006400    02 FILLER              PIC X(01) VALUE SPACES      .
        // 006500    02 FILLER              PIC X(06) VALUE "  序號  "    .
        // 006600    02 FILLER              PIC X(10) VALUE "  代收類別  ".
        // 006700    02 FILLER              PIC X(03) VALUE SPACES      .
        // 006800    02 FILLER              PIC X(15) VALUE "  銷帳編號  ".
        // 006900    02 FILLER              PIC X(11) VALUE "  入帳日期  ".
        // 007000    02 FILLER              PIC X(11) VALUE "  上傳日期  ".
        // 007100    02 FILLER              PIC X(12) VALUE "  外部代收日  ".
        // 007200    02 FILLER              PIC X(10) VALUE "  繳款方式  ".
        // 007300    02 FILLER              PIC X(04) VALUE SPACES      .
        // 007400    02 FILLER              PIC X(06) VALUE "  金額  ".
        // 007500    02 FILLER              PIC X(16) VALUE "  備註資料  ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("  序號  ", 6));
        sb.append(formatUtil.padX("  代收類別  ", 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX("  銷帳編號  ", 15));
        sb.append(formatUtil.padX("  入帳日期  ", 11));
        sb.append(formatUtil.padX("  上傳日期  ", 11));
        sb.append(formatUtil.padX("  外部代收日  ", 12));
        sb.append(formatUtil.padX("  繳款方式  ", 10));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("  金額  ", 6));
        sb.append(formatUtil.padX("  備註資料  ", 16));
        fileOUTRPTDTLContents.add(sb.toString());

        // 015700     MOVE       SPACES              TO     REPORT-LINE.
        // 015800     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 009500 01 WK-GATE-LINE.
        // 009600    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileOUTRPTDTLContents.add(sb.toString());

        isNewPage = true;
        // 015900 2000-TITLE-EXIT.
    }

    private void subtail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr subtail");
        // 019600 4000-SUBTAIL-RTN.

        // 寫報表表尾(小計)
        // 019700     MOVE       WK-SUBCNT      TO    WK-RPT-SUBCNT .
        wkRptSubcnt = wkSubcnt;

        // 019800     MOVE       SPACES         TO    REPORT-LINE   .
        // 019900     WRITE      REPORT-LINE    FROM  WK-GATE-LINE  .
        // 009500 01 WK-GATE-LINE.
        // 009600    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileOUTRPTDTLContents.add(sb.toString());

        // 020000     MOVE       SPACES         TO    REPORT-LINE   .
        // 020100     WRITE      REPORT-LINE    FROM  WK-SUBTOTAL-LINE .
        // 009700 01 WK-SUBTOTAL-LINE.
        // 009800    02 FILLER                   PIC X(02) VALUE SPACES.
        // 009900    02 FILLER                   PIC X(11) VALUE "  分行小計  :".
        // 010000    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX("  分行小計  :", 11));
        sb.append(reportUtil.customFormat("" + wkRptSubcnt, "ZZZ,ZZZ,ZZ9"));
        fileOUTRPTDTLContents.add(sb.toString());

        // 020200     MOVE       0              TO    WK-SUBCNT.
        wkSubcnt = 0;

        // 020300 4000-SUBTAIL-EXIT.
    }

    private void tail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTDTLLsnr tail");
        // 022100 6000-TAIL-RTN.
        // 寫報表表尾(總筆數)

        // 022200     MOVE       WK-TOTCNT      TO    WK-RPT-TOTCNT .
        wkRptTotcnt = wkTotcnt;

        // 022300     MOVE       SPACES         TO    REPORT-LINE   .
        // 022400     WRITE      REPORT-LINE    FROM  WK-GATE-LINE  .
        // 009500 01 WK-GATE-LINE.
        // 009600    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileOUTRPTDTLContents.add(sb.toString());

        // 022500     MOVE       SPACES         TO    REPORT-LINE   .
        // 022600     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE .
        // 010100 01 WK-TOTAL-LINE.
        // 010200    02 FILLER                   PIC X(04) VALUE SPACES.
        // 010300    02 FILLER                   PIC X(09) VALUE "  總筆數  :".
        // 010400    02 WK-RPT-TOTCNT            PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX("  總筆數  :", 9));
        sb.append(reportUtil.customFormat("" + wkRptTotcnt, "ZZZ,ZZZ,ZZ9"));
        fileOUTRPTDTLContents.add(sb.toString());

        // 022700 6000-TAIL-EXIT.
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

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }

    private void batchResponse() {

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }

    private Map<String, String> getG2007Param(String lParam) {
        String[] paramL;
        if (lParam.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lParam is null");
            return null;
        }
        paramL = lParam.split(";");
        if (paramL == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "paramL is null");
            return null;
        }
        // G2007:
        //  BRNO(;),
        //  APSEQ(;),
        //  PARAM1(;),
        //  PARAM2(;),
        //  PARAM3(;),
        //  PARAM4(;),
        //  PARAM5(;),
        //  PARAM6(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("BRNO", paramL[0]); // 對應 BRNO
        if (paramL.length > 1) map.put("APSEQ", paramL[1]); // 對應 APSEQ
        if (paramL.length > 2) map.put("PARAM1", paramL[2]); // 對應 PARAM1
        if (paramL.length > 3) map.put("PARAM2", paramL[3]); // 對應 PARAM2
        if (paramL.length > 4) map.put("PARAM3", paramL[4]); // 對應 PARAM3
        if (paramL.length > 5) map.put("PARAM4", paramL[5]); // 對應 PARAM4
        if (paramL.length > 6) map.put("PARAM5", paramL[6]); // 對應 PARAM5
        if (paramL.length > 7) map.put("PARAM6", paramL[7]); // 對應 PARAM6
        if (map.size() == 0) {
            return null;
        }
        int i = 0;
        for (String key : map.keySet()) {
            i++;
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "map KEY = {} ,VALUE = {}",
                    key,
                    map.get(key));
        }
        return map;
    }

    private File downloadFromSftp(String fileFtpPath) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            //            fsapBatchUtil.response(event, "E999", "檔案不存在(" + fileFtpPath + ")");
            throw new LogicException("GE999", "檔案不存在(" + fileFtpPath + ")");
        }
        return file;
    }

    private void copyData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "copyData()");
        List<String> kputhList = textFile.readFileContent(inputFilePath, CHARSET_UTF8);
        String kputh =
                fsapfileDir
                        + CL012_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkKdate7
                        + PATH_SEPARATOR
                        + "KPUTH";

        textFile.deleteFile(kputh);
        textFile.writeFileContent(kputh, kputhList, CHARSET_UTF8);
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }
}
