/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTRPTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTRPTFLsnr")
@Scope("prototype")
public class OUTRPTFLsnr extends BatchListenerCase<OUTRPTF> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    private OUTRPTF event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // File related
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CL022_003_PATH = "CL022\\003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_KPUTH = "KPUTH."; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME_058 = "058."; // 產檔檔名
    private static final String SPACE = "";
    private String outputFilePath; // 產檔路徑
    private String PATH_SEPARATOR = File.separator;
    private String wkKputhdir; // 讀檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileRPTFContents;
    private String PAGE_SEPARATOR = "\u000C";

    private Map<String, String> textMap;
    // int define (9)
    private int wkTaskRdate;
    private int wkTaskDate;
    private int wkRptPage;
    private int wkRptCount;
    private int wkKdate;
    private int wkReccnt;
    private int wkSubcnt;
    private int wkRptSubcnt;
    private int wkRptPbrno;
    private int wkRptUdate;
    private int wkRptLdate;
    private int wkRptSitdate;
    private int kputhPbrno;
    private int kputhDate;
    private int kputhUpldate;
    private int kputhSitdate;
    private int wkRptPdate;

    // String define (X)
    private String wkPreCode;
    private String kputhCode;
    private String wkRptCode;
    private String wkRptRcptid;
    private String wkRptTxtype;
    private String wkRptUserdate;
    private String kputhRcptid;
    private String kputhTxtype;
    private String kputhUserdata;

    // Use BigDecimal type for amt and fee
    private BigDecimal wkSubamt;
    private BigDecimal wkRptSubamt;
    private BigDecimal wkRptAmt = BigDecimal.ZERO;
    private BigDecimal kputhAmt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTRPTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTRPTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTFLsnr run()");
        init(event);

        // FD-KPUTH檔案存在，執行0000-MAIN-RTN
        // 011900     IF  ATTRIBUTE  RESIDENT   OF FD-KPUTH IS =  VALUE(TRUE)
        if (textFile.exists(wkKputhdir)) {
            // 012000       PERFORM  0000-MAIN-RTN  THRU 0000-MAIN-EXIT.
            main();
            // 012100 0000-END-RTN.
        }
    }

    private void init(OUTRPTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTFLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = parse.string2Integer(textMap.get("WK_TASK_DATE"));
        wkTaskRdate = parse.string2Integer(textMap.get("RGDAY"));
        // 設定日期
        // 011600     MOVE   WK-TASK-RDATE      TO       WK-KDATE    .
        // 011700     MOVE   WK-TASK-DATE       TO       WK-RPT-PDATE.
        wkKdate = wkTaskRdate;
        wkRptPdate = wkTaskDate;

        // 設定檔名
        // 011800     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO WK-KPUTHDIR.
        // 003100  01 WK-KPUTHDIR.
        // 003200     03 FILLER                          PIC X(22)
        // 003300                         VALUE "DATA/GN/DWL/CL022/003/".
        // 003400     03 WK-KDATE                        PIC 9(07).
        // 003500     03 FILLER                          PIC X(07)
        // 003600                                  VALUE "/KPUTH.".
        wkKputhdir =
                fileDir
                        + CL022_003_PATH
                        + PATH_SEPARATOR
                        + wkKdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME_KPUTH;
        // 002100  FD  REPORTFL
        // 002200      VALUE  OF  TITLE  IS  "BD/CL/BH/058."
        outputFilePath = fileDir + FILE_OUTPUT_NAME_058;

        fileRPTFContents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTFLsnr main");
        // 清變數
        // 012600     MOVE    0                   TO     WK-RPT-PAGE   ,
        // 012700                                        WK-RPT-COUNT  ,
        // 012800                                        WK-RECCNT     ,
        // 012900                                        WK-SUBCNT     ,
        // 013000                                        WK-SUBAMT     .
        wkRptPage = 0;
        wkRptCount = 0;
        wkReccnt = 0;
        wkSubcnt = 0;
        wkSubamt = BigDecimal.valueOf(0);

        // 013100     MOVE    SPACES              TO     WK-PRE-CODE  .
        wkPreCode = SPACE;

        List<String> lines = textFile.readFileContent(wkKputhdir, CHARSET_UTF8);
        // 013400 0000-MAIN-LOOP.
        int cnt = 0;
        for (String detail : lines) {
            if (detail.length() < 140) {
                detail = formatUtil.padX(detail, 140);
            }
            cnt++;

            kputhPbrno =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(118, 121))
                                    ? detail.substring(118, 121)
                                    : "0");
            kputhCode = detail.substring(10, 16);
            kputhAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(detail.substring(54, 64))
                                    ? detail.substring(54, 64)
                                    : "0");
            kputhDate =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(32, 39))
                                    ? detail.substring(32, 39)
                                    : "0");
            kputhRcptid = detail.substring(16, 32);
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
            kputhUserdata = detail.substring(64, 104);

            // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LFAST
            // 013500     READ    FD-KPUTH  AT END    GO TO  0000-MAIN-LFAST   .

            // 累加序號筆數
            // 013900     ADD     1                   TO     WK-RPT-COUNT      ,
            // 014000                                        WK-RECCNT         .
            wkRptCount++;
            wkReccnt++;

            // 換頁
            // 014100     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
            pageSwh3000();
            // 寫報表明細
            // 014200     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT   .
            dtlin5000();
            // 014300     GO TO   0000-MAIN-LOOP.
            if (cnt == lines.size()) {
                // 014400 0000-MAIN-LFAST.
                // 寫報表表尾
                // 014500     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT .
                subtail4000();

                try {
                    textFile.writeFileContent(outputFilePath, fileRPTFContents, CHARSET_BIG5);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
            }
        }
    }

    private void pageSwh3000() {
        // 016400*   換主辦行或  50  筆換頁
        // 016500     IF    (    WK-PRE-CODE         =      SPACES   )
        if (SPACE.equals(wkPreCode)) {
            // 016600*      ADD      1                   TO     WK-RPT-TOTPAGE
            // 016700       ADD      1                   TO     WK-RPT-PAGE
            wkRptPage++;
            // 016800       MOVE     KPUTH-PBRNO         TO     WK-RPT-PBRNO
            wkRptPbrno = kputhPbrno;
            // 016900       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
            // 017000       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000(PAGE_SEPARATOR);
            // 017100       GO TO 3000-PAGESWH-EXIT.
        }

        // 017300     IF    (    KPUTH-CODE          NOT =  WK-PRE-CODE  )
        // 017400     AND (    WK-PRE-CODE         NOT =  SPACES       )
        if (!kputhCode.equals(wkPreCode) && !wkPreCode.trim().isEmpty()) {
            // 017500       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail4000();
            // 017600       MOVE     SPACES              TO     REPORT-LINE
            // 017700       WRITE    REPORT-LINE         AFTER  PAGE
            fileRPTFContents.add("\u000c");
            // 017800       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
            // 017900       MOVE     KPUTH-PBRNO         TO     WK-RPT-PBRNO
            wkRptPbrno = kputhPbrno;
            // 018000       MOVE     1                   TO     WK-RECCNT
            wkReccnt = 1;
            // 018100       MOVE     1                   TO     WK-RPT-COUNT
            wkRptCount = 1;
            // 018200*      ADD      1                   TO     WK-RPT-TOTPAGE
            // 018300       MOVE     1                   TO     WK-RPT-PAGE
            wkRptPage = 1;
            // 018400       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000(PAGE_SEPARATOR);
            // 018500       GO TO 3000-PAGESWH-EXIT.
        }

        // 018700     IF    (    WK-RECCNT           >      50       )
        if (wkReccnt > 50) {
            // 018800       MOVE     SPACES              TO     REPORT-LINE
            // 018900       WRITE    REPORT-LINE         AFTER  PAGE
            fileRPTFContents.add("\u000c");
            // 019000       MOVE     1                   TO     WK-RECCNT
            wkReccnt = 1;
            // 019100*      ADD      1                   TO     WK-RPT-TOTPAGE
            // 019200       ADD      1                   TO     WK-RPT-PAGE
            wkRptPage++;
            // 019300       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000(PAGE_SEPARATOR);
            // 019400       GO TO 3000-PAGESWH-EXIT.
        }
    }

    private void dtlin5000() {
        // 寫報表明細
        // 021200     ADD       1               TO    WK-SUBCNT      .
        wkSubcnt++;
        // 021300     ADD       KPUTH-AMT       TO    WK-SUBAMT      .
        wkSubamt = wkSubamt.add(kputhAmt);
        // 021400     MOVE      KPUTH-PBRNO     TO    WK-RPT-PBRNO   .
        wkRptPbrno = kputhPbrno;
        // 021500     MOVE      KPUTH-CODE      TO    WK-RPT-CODE    .
        wkRptCode = kputhCode;
        // 021600     MOVE      KPUTH-DATE      TO    WK-RPT-UDATE   .
        wkRptUdate = kputhDate;
        // 021700     MOVE      KPUTH-RCPTID    TO    WK-RPT-RCPTID  .
        wkRptRcptid = kputhRcptid;
        // 021800     MOVE      KPUTH-AMT       TO    WK-RPT-AMT     .
        wkRptAmt = wkRptAmt.add(kputhAmt);
        // 021900     MOVE      KPUTH-UPLDATE   TO    WK-RPT-LDATE   .
        wkRptLdate = kputhUpldate;
        // 022000     MOVE      KPUTH-SITDATE   TO    WK-RPT-SITDATE .
        wkRptSitdate = kputhSitdate;
        // 022100     MOVE      KPUTH-TXTYPE    TO    WK-RPT-TXTYPE  .
        wkRptTxtype = kputhTxtype;
        // 022200     MOVE      KPUTH-USERDATA  TO    WK-RPT-USERDATA.
        wkRptUserdate = kputhUserdata;
        // 022300     WRITE     REPORT-LINE     FROM  WK-DETAIL-LINE.
        // 007700 01 WK-DETAIL-LINE.
        sb = new StringBuilder();
        // 007800    02 FILLER                          PIC X(01) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 1));
        // 007900    02 WK-RPT-COUNT                    PIC 9(06).
        sb.append(formatUtil.pad9(SPACE + wkRptCount, 6));
        // 008000    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 008100    02 WK-RPT-CODE                     PIC X(06).
        sb.append(formatUtil.padX(wkRptCode, 6));
        // 008200    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 008300    02 WK-RPT-RCPTID                   PIC X(16).
        sb.append(formatUtil.padX(wkRptRcptid, 16));
        // 008400    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 008500    02 WK-RPT-UDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(SPACE + wkRptUdate, "Z99/99/99"));
        // 008600    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 008700    02 WK-RPT-LDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(SPACE + wkRptLdate, "Z99/99/99"));
        // 008800    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 008900    02 WK-RPT-SITDATE                  PIC Z99/99/99.
        sb.append(reportUtil.customFormat(SPACE + wkRptSitdate, "Z99/99/99"));
        // 009000    02 FILLER                          PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 6));
        // 009100    02 WK-RPT-TXTYPE                   PIC X(01).
        sb.append(formatUtil.padX(wkRptTxtype, 1));
        // 009200    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 009300    02 WK-RPT-AMT                      PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(SPACE + wkRptAmt, "Z,ZZZ,ZZZ,ZZ9"));
        // 009400    02 FILLER                          PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 2));
        // 009500    02 WK-RPT-USERDATA                 PIC X(40).
        sb.append(formatUtil.padX(wkRptUserdate, 40));
        fileRPTFContents.add(sb.toString());
    }

    private void subtail4000() {
        // 寫報表表尾
        // 020000     MOVE       WK-SUBCNT      TO    WK-RPT-SUBCNT .
        wkRptSubcnt = wkSubcnt;
        // 020100     MOVE       WK-SUBAMT      TO    WK-RPT-SUBAMT .
        wkRptSubamt = wkSubamt;
        // 020200     MOVE       SPACES         TO    REPORT-LINE   .
        // 020300     WRITE      REPORT-LINE    FROM  WK-GATE-LINE  .
        // 009600 01 WK-GATE-LINE.
        // 009700    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileRPTFContents.add(sb.toString());

        // 020400     MOVE       SPACES         TO    REPORT-LINE   .
        sb = new StringBuilder();
        // 020500     WRITE      REPORT-LINE    FROM  WK-SUBTOTAL-LINE .
        // 009800 01 WK-SUBTOTAL-LINE.
        // 009900    02 FILLER                   PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 2));
        // 010000    02 FILLER                   PIC X(11) VALUE "  筆數共計  :".
        sb.append(formatUtil.padX("  筆數共計  :", 11));
        // 010100    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(wkRptSubcnt), "ZZZ,ZZZ,ZZ9"));
        // 010200    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 010300    02 FILLER                   PIC X(11) VALUE "  金額共計  :".
        sb.append(formatUtil.padX("  金額共計  :", 11));
        // 010400    02 WK-RPT-SUBAMT            PIC ZZ,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(wkRptSubamt), "ZZ,ZZZ,ZZZ,ZZ9"));
        fileRPTFContents.add(sb.toString());

        // 020600     MOVE       0              TO    WK-SUBCNT     .
        wkSubcnt = 0;
        // 020700     MOVE       0              TO    WK-SUBAMT     .
        wkSubamt = BigDecimal.valueOf(0);
    }

    private void title2000(String pageFg) {
        // 寫報表表頭
        // 015000     MOVE       SPACES              TO     REPORT-LINE.
        sb = new StringBuilder();
        sb.append(pageFg);
        // 015100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(45) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 45));
        // 004300    02 FILLER                          PIC X(54) VALUE
        // 004400       "  外部代收入帳細報表            ".
        sb.append(formatUtil.padX("  外部代收入帳細報表            ", 54));
        // 004500    02 FILLER                          PIC X(22) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 22));
        // 004600    02 FILLER                          PIC X(12) VALUE
        // 004700       "FORM : C058 ".
        sb.append(formatUtil.padX("FORM : C058 ", 12));
        fileRPTFContents.add(sb.toString());

        // 015200     MOVE       SPACES              TO     REPORT-LINE.
        // 015300     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileRPTFContents.add("");
        // 015400     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004800 01 WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 004900    02 FILLER                          PIC X(10) VALUE
        // 005000       "  主辦行：  ".
        sb.append(formatUtil.padX("  主辦行：  ", 10));
        // 005100    02 WK-RPT-PBRNO                    PIC 9(03).
        sb.append(formatUtil.pad9(String.valueOf(wkRptPbrno), 3));
        // 005200    02 FILLER                          PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 4));
        // 005300    02 FILLER                          PIC X(13) VALUE
        // 005400       "  印表日期：   ".
        sb.append(formatUtil.padX("  印表日期：   ", 13));
        // 005500    02 WK-RPT-PDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(wkRptPdate), "Z99/99/99"));
        // 005600    02 FILLER                          PIC X(65) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 65));

        // 006000    02 FILLER                          PIC X(13) VALUE SPACE.
        sb.append(formatUtil.padX(SPACE, 13));
        // 006100    02 FILLER                          PIC X(11) VALUE
        // 006200       "      頁次  :".
        sb.append(formatUtil.padX("      頁次  :", 11));
        // 006300    02 WK-RPT-PAGE                     PIC 9(04).
        sb.append(formatUtil.pad9(String.valueOf(wkRptPage), 4));
        fileRPTFContents.add(sb.toString());

        // 015500     MOVE       SPACES              TO     REPORT-LINE.
        // 015600     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileRPTFContents.add("");

        // 015700     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 006400 01 WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 006500    02 FILLER              PIC X(01) VALUE SPACES      .
        sb.append(formatUtil.padX(SPACE, 1));
        // 006600    02 FILLER              PIC X(06) VALUE "  序號  "    .
        sb.append(formatUtil.padX("  序號  ", 6));
        // 006700    02 FILLER              PIC X(10) VALUE "  代收類別  ".
        sb.append(formatUtil.padX("  代收類別  ", 10));
        // 006800    02 FILLER              PIC X(03) VALUE SPACES      .
        sb.append(formatUtil.padX(SPACE, 3));
        // 006900    02 FILLER              PIC X(15) VALUE "  銷帳編號  ".
        sb.append(formatUtil.padX("  銷帳編號  ", 15));
        // 007000    02 FILLER              PIC X(11) VALUE "  入帳日期  ".
        sb.append(formatUtil.padX("  入帳日期  ", 11));
        // 007100    02 FILLER              PIC X(11) VALUE "  上傳日期  ".
        sb.append(formatUtil.padX("  上傳日期  ", 11));
        // 007200    02 FILLER              PIC X(12) VALUE "  外部代收日  ".
        sb.append(formatUtil.padX("  外部代收日  ", 12));
        // 007300    02 FILLER              PIC X(10) VALUE "  繳款方式  ".
        sb.append(formatUtil.padX("  繳款方式  ", 10));
        // 007400    02 FILLER              PIC X(04) VALUE SPACES      .
        sb.append(formatUtil.padX(SPACE, 4));
        // 007500    02 FILLER              PIC X(06) VALUE "  金額  ".
        sb.append(formatUtil.padX("  金額  ", 6));
        // 007600    02 FILLER              PIC X(16) VALUE "  備註資料  ".
        sb.append(formatUtil.padX("  備註資料  ", 16));
        fileRPTFContents.add(sb.toString());

        // 015800     MOVE       SPACES              TO     REPORT-LINE.
        // 015900     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 009600 01 WK-GATE-LINE.
        // 009700    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileRPTFContents.add(sb.toString());
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }
}
