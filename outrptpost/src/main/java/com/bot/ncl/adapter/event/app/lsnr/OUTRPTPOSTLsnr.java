/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTRPTPOST;
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
@Component("OUTRPTPOSTLsnr")
@Scope("prototype")
public class OUTRPTPOSTLsnr extends BatchListenerCase<OUTRPTPOST> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    private OUTRPTPOST event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // File related
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CL022_PATH = "CL022"; // 讀檔目錄
    private static final String _003_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_KPUTH = "KPUTH."; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME_057 = "CL-BH-057"; // 產檔檔名
    private String wkKputhdir; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private String PATH_SEPARATOR = File.separator;
    private StringBuilder sb = new StringBuilder();
    private List<String> fileRPTPOSTContents;
    private String PAGE_SEPARATOR = "\u000C";

    private Map<String, String> textMap;
    // ----------- WK  int (9) -----------
    private int wkKdate;
    private int wkRptPage;
    private int wkRptCount;
    private int wkReccnt;
    private int wkSubcnt;
    private int wkPrePbrno;
    private int wkCnt1;
    private int wkCnt2;
    private int wkCnt3;
    private int wkCnt4;
    private int wkCnt5;
    private int wkRptCnt1;
    private int wkRptCnt2;
    private int wkRptCnt3;
    private int wkRptCnt4;
    private int wkRptCnt5;
    private int wkRptPbrno;
    private int wkRptCode;
    private int wkRptSubcnt;

    // ---------- WK  String (X) ----------
    private String wkTaskRdate;
    private String wkRptPdate;
    private String wkTaskDate;
    private String wkPreCode;

    // ---------- WK  BigDecimal ----------
    private BigDecimal wkSubamt;
    private BigDecimal wkAmt1;
    private BigDecimal wkAmt2;
    private BigDecimal wkAmt3;
    private BigDecimal wkAmt4;
    private BigDecimal wkAmt5;
    private BigDecimal wkRptAmt1 = new BigDecimal(0);
    private BigDecimal wkRptAmt2;
    private BigDecimal wkRptAmt3;
    private BigDecimal wkRptAmt4;
    private BigDecimal wkRptAmt5;
    private BigDecimal wkRptSubamt;

    // ----------- KPUTH  int (9) -----------
    private int kputhPbrno;

    // ---------- KPUTH  String (X) ----------
    private String kputhTxtype;
    private String kputhCode;
    private String kputhSmserno;

    // ---------- KPUTH  BigDecimal ----------
    private BigDecimal kputhAmt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTRPTPOST event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTPOSTLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTRPTPOST event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTPOSTLsnr run");
        init(event);

        // FD-KPUTH檔案存在，執行0000-MAIN-RTN
        // 015400     IF  ATTRIBUTE  RESIDENT   OF FD-KPUTH IS =  VALUE(TRUE)
        if (textFile.exists(wkKputhdir)) {
            // 015500       PERFORM  0000-MAIN-RTN  THRU 0000-MAIN-EXIT.
            main();
            // 015600 0000-END-RTN.
        }
    }

    private void init(OUTRPTPOST event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTPOSTLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = textMap.get("WK_TASK_DATE");
        wkTaskRdate = textMap.get("RGDAY");

        // 設定日期
        // 015100     MOVE   WK-TASK-RDATE      TO       WK-KDATE    .
        // 015200     MOVE   WK-TASK-DATE       TO       WK-RPT-PDATE.
        wkKdate = parse.string2Integer(wkTaskRdate);
        wkRptPdate = wkTaskDate;
        fileRPTPOSTContents = new ArrayList<>();

        // 設定檔名
        // 015300     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO WK-KPUTHDIR.
        // 003100  01 WK-KPUTHDIR.
        // 003200     03 FILLER                          PIC X(22)
        // 003300                         VALUE "DATA/GN/DWL/CL022/003/".
        // 003400     03 WK-KDATE                        PIC 9(07).
        // 003500     03 FILLER                          PIC X(07)
        // 003600                                  VALUE "/KPUTH.".
        wkKputhdir =
                fileDir
                        + CL022_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkKdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME_KPUTH;
        // 002100  FD  REPORTFL
        // 002200      VALUE  OF  TITLE  IS  "BD/CL/BH/057."
        outputFilePath = fileDir + FILE_OUTPUT_NAME_057;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTPOSTLsnr main");
        // 清變數
        // 016100     MOVE    0   TO    WK-RPT-PAGE ,WK-RPT-COUNT ,WK-RECCNT ,
        // 016200                       WK-SUBCNT   ,WK-SUBAMT    ,WK-PRE-PBRNO ,
        // 016300                       WK-CNT1,WK-AMT1,WK-CNT2,WK-AMT2,
        // 016400                       WK-CNT3,WK-AMT3,WK-CNT4,WK-AMT4,
        // 016500                       WK-CNT5,WK-AMT5.
        wkRptPage = 0;
        wkRptCount = 0;
        wkReccnt = 0;
        wkSubcnt = 0;
        wkSubamt = new BigDecimal(0);
        wkPrePbrno = 0;
        wkCnt1 = 0;
        wkAmt1 = new BigDecimal(0);
        wkCnt2 = 0;
        wkAmt2 = new BigDecimal(0);
        wkCnt3 = 0;
        wkAmt3 = new BigDecimal(0);
        wkCnt4 = 0;
        wkAmt4 = new BigDecimal(0);
        wkCnt5 = 0;
        wkAmt5 = new BigDecimal(0);

        // 016600     MOVE    SPACES              TO     WK-PRE-CODE  .
        wkPreCode = "";

        // 開啟檔案
        // 016700     OPEN    OUTPUT    REPORTFL.
        // 016800     OPEN    INPUT     FD-KPUTH.
        List<String> lines = textFile.readFileContent(wkKputhdir, CHARSET_UTF8);
        // 016900 0000-MAIN-LOOP.

        int cnt = 0;
        for (String detail : lines) {
            if (detail.length() < 140) {
                detail = formatUtil.padX(detail, 140);
            }
            cnt++;

            // 01 KPUTH-REC TOTAL 140 BYTES
            // 03 KPUTH-PUTFILE	GROUP
            //  05 KPUTH-PUTTYPE	9(02)	媒體種類  0-2
            //  05 KPUTH-PUTNAME	X(08)	媒體檔名  2-10
            // 03 KPUTH-PUTFILE-R1 REDEFINES KPUTH-PUTFILE
            //  05 KPUTH-ENTPNO	X(10)	統編  0-10
            // 03 KPUTH-CODE	X(06)	代收類別  10-16
            // 03 KPUTH-RCPTID	X(16)	銷帳號碼  16-32
            // 03 KPUTH-DATE	9(07)	代收日  32-39
            // 03 KPUTH-TIME	9(06)	代收時間  39-45
            // 03 KPUTH-CLLBR	9(03)	代收行  45-48
            // 03 KPUTH-LMTDATE	9(06)	繳費期限  48-54
            // 03 KPUTH-AMT	9(10)	繳費金額 54-64
            // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
            // 03 KPUTH-USERDATE-R1 REDEFINES KPUTH-USERDATA
            //  05 KPUTH-SMSERNO	X(03)  64-67
            //  05 KPUTH-RETAILNO	X(08)  67-75
            //  05 KPUTH-BARCODE3	X(15)  75-90
            //  05 KPUTH-FILLER	X(14)  90-104
            // 03 KPUTH-SITDATE	9(07)	原代收日  104-111
            // 03 KPUTH-TXTYPE	X(01)	帳務別  111-112
            // 03 KPUTH-SERINO	9(06)	交易明細流水序號  112-118
            // 03 KPUTH-PBRNO	9(03)	主辦分行  118-121
            // 03 KPUTH-UPLDATE	9(07)  121-128
            // 03 KPUTH-FEETYPE	9(01)  128-129
            // 03 KPUTH-FEEO2L	9(05)V99  129-136
            // 03 FILLER	X(02)  136-138
            // 03 FILLER	X(02)  138-140
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
            kputhTxtype = detail.substring(111, 112);
            kputhSmserno = detail.substring(64, 67);

            // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LFAST
            // 017000     READ    FD-KPUTH  AT END    GO TO  0000-MAIN-LFAST   .
            // 如果帳務別不等於"U"
            // 017200     IF      KPUTH-TXTYPE        NOT =  "U"
            if (!"U".equals(kputhTxtype)) {
                if (cnt == lines.size()) {
                    // 017900 0000-MAIN-LFAST.
                    // 寫REPORTFL報表
                    // 018000     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT .
                    subtail4000();

                    try {
                        textFile.writeFileContent(
                                outputFilePath, fileRPTPOSTContents, CHARSET_BIG5);
                    } catch (LogicException e) {
                        moveErrorResponse(e);
                    }
                }
                // 017300       GO TO 0000-MAIN-LOOP                               .
                continue;
            }
            // 判斷換頁
            // 017500     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
            pageSwh3000();
            // 判斷代收類別
            // 017600     PERFORM 3100-CODESWH-RTN    THRU   3100-CODESWH-EXIT .
            codeSwh3100();
            // 累加金額筆數
            // 017700     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT   .
            // 017800     GO TO   0000-MAIN-LOOP.
            dtlin5000();
            if (cnt == lines.size()) {
                // 017900 0000-MAIN-LFAST.
                // 寫REPORTFL報表
                // 018000     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT .
                subtail4000();

                try {
                    textFile.writeFileContent(outputFilePath, fileRPTPOSTContents, CHARSET_BIG5);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
            }
        }
    }

    private void pageSwh3000() {
        // 021000     IF    (    WK-PRE-PBRNO        =      000      )
        if (wkPrePbrno == 0) {
            // 021100       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
            // 021200                                           WK-RPT-PBRNO
            wkPrePbrno = kputhPbrno;
            wkRptPbrno = kputhPbrno;
            // 021300       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
            // 021400       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000(PAGE_SEPARATOR);
        }

        // 021900     IF    (    KPUTH-PBRNO         NOT =  WK-PRE-PBRNO )
        if (wkPrePbrno != kputhPbrno) {
            // 022000       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail4000();
            // 022100       MOVE     KPUTH-CODE          TO     WK-PRE-CODE ,
            // 022200                                           WK-RPT-CODE
            wkPreCode = kputhCode;
            wkRptCode = parse.string2Integer(kputhCode);
            // 022300       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
            // 022400                                           WK-RPT-PBRNO
            wkPrePbrno = kputhPbrno;
            wkRptPbrno = kputhPbrno;
            // 022500       MOVE     SPACES              TO     REPORT-LINE
            // 022600       WRITE    REPORT-LINE         AFTER  PAGE

            // 022700       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000(PAGE_SEPARATOR);
            // 022800       GO TO 3000-PAGESWH-EXIT.
        }
    }

    private void codeSwh3100() {
        // 判斷代收類別有無寫過
        // 023600     IF    (    KPUTH-CODE          NOT =  WK-PRE-CODE  )
        // 023700       AND (    WK-PRE-CODE         NOT =  SPACES       )
        if (!kputhCode.equals(wkPreCode) && !wkPreCode.trim().isEmpty()) {
            // 023800       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail4000();
            // 023900       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
        }
    }

    private void dtlin5000() {
        // 累加各代收帳號筆數金額
        // KPUTH-SMSERNO代收簡碼(郵局198、501)
        // 029700     MOVE   KPUTH-CODE        TO    WK-RPT-CODE.
        wkRptCode = parse.string2Integer(kputhCode);

        // 029900     IF    ( KPUTH-AMT     < 101  )
        // 030000       AND ( KPUTH-SMSERNO = "198")
        if (kputhAmt.compareTo(new BigDecimal(101)) < 0 && "198".equals(kputhSmserno)) {
            // 030100       ADD     1               TO    WK-CNT1
            wkCnt1++;
            // 030200       ADD     KPUTH-AMT       TO    WK-AMT1.
            wkAmt1 = wkAmt1.add(kputhAmt);
        }

        // 030300     IF    ( KPUTH-AMT     > 100  ) AND ( KPUTH-AMT < 1001 )
        // 030400       AND ( KPUTH-SMSERNO = "198")
        if (kputhAmt.compareTo(new BigDecimal(100)) > 0
                && kputhAmt.compareTo(new BigDecimal(1001)) < 0
                && "198".equals(kputhSmserno)) {
            // 030500       ADD     1               TO    WK-CNT2
            wkCnt2++;
            // 030600       ADD     KPUTH-AMT       TO    WK-AMT2.
            wkAmt2 = wkAmt2.add(kputhAmt);
        }

        // 030700     IF    ( KPUTH-AMT     > 1000 )
        // 030800       AND ( KPUTH-SMSERNO = "198")
        if (kputhAmt.compareTo(new BigDecimal(1000)) > 0 && "198".equals(kputhSmserno)) {
            // 030900       ADD     1               TO    WK-CNT3
            wkCnt3++;
            // 031000       ADD     KPUTH-AMT       TO    WK-AMT3.
            wkAmt3 = wkAmt3.add(kputhAmt);
        }

        // 031100     IF    ( KPUTH-AMT     < 20001)
        // 031200       AND ( KPUTH-SMSERNO = "501")
        if (kputhAmt.compareTo(new BigDecimal(20001)) < 0 && "501".equals(kputhSmserno)) {
            // 031300       ADD     1               TO    WK-CNT4
            wkCnt4++;
            // 031400       ADD     KPUTH-AMT       TO    WK-AMT4.
            wkAmt4 = wkAmt4.add(kputhAmt);
        }

        // 031500     IF    ( KPUTH-AMT     > 20000)
        // 031600       AND ( KPUTH-SMSERNO = "501")
        if (kputhAmt.compareTo(new BigDecimal(20000)) > 0 && "501".equals(kputhSmserno)) {
            // 031700       ADD     1               TO    WK-CNT5
            wkCnt5++;
            // 031800       ADD     KPUTH-AMT       TO    WK-AMT5.
            wkAmt5 = wkAmt5.add(kputhAmt);
        }
    }

    private void subtail4000() {
        // 寫報表
        // 024600     PERFORM    2100-TITLE-RTN  THRU 2100-TITLE-EXIT
        title2100();
        // 024700     MOVE       WK-CNT1         TO   WK-RPT-CNT1  .
        wkRptCnt1 = wkCnt1;
        // 024800     ADD        WK-CNT1         TO   WK-SUBCNT    .
        wkSubcnt += wkCnt1;
        // 024900     MOVE       WK-AMT1         TO   WK-RPT-AMT1  .
        wkRptAmt1 = wkRptAmt1.add(wkAmt1);
        // 025000     ADD        WK-AMT1         TO   WK-SUBAMT    .
        wkSubamt = wkSubamt.add(wkAmt1);
        // 025100     MOVE       WK-CNT2         TO   WK-RPT-CNT2  .
        wkRptCnt2 = wkCnt2;
        // 025200     ADD        WK-CNT2         TO   WK-SUBCNT    .
        wkSubcnt += wkCnt2;
        // 025300     MOVE       WK-AMT2         TO   WK-RPT-AMT2  .
        wkRptAmt2 = wkAmt2;
        // 025400     ADD        WK-AMT2         TO   WK-SUBAMT    .
        wkSubamt = wkSubamt.add(wkAmt2);
        // 025500     MOVE       WK-CNT3         TO   WK-RPT-CNT3  .
        wkRptCnt3 = wkCnt3;
        // 025600     ADD        WK-CNT3         TO   WK-SUBCNT    .
        wkSubcnt += wkCnt3;
        // 025700     MOVE       WK-AMT3         TO   WK-RPT-AMT3  .
        wkRptAmt3 = wkAmt3;
        // 025800     ADD        WK-AMT3         TO   WK-SUBAMT    .
        wkSubamt = wkSubamt.add(wkAmt3);
        // 025900     MOVE       WK-CNT4         TO   WK-RPT-CNT4  .
        wkRptCnt4 = wkCnt4;
        // 026000     ADD        WK-CNT4         TO   WK-SUBCNT    .
        wkSubcnt += wkCnt4;
        // 026100     MOVE       WK-AMT4         TO   WK-RPT-AMT4  .
        wkRptAmt4 = wkAmt4;
        // 026200     ADD        WK-AMT4         TO   WK-SUBAMT    .
        wkSubamt = wkSubamt.add(wkAmt4);
        // 026300     MOVE       WK-CNT5         TO   WK-RPT-CNT5  .
        wkRptCnt5 = wkCnt5;
        // 026400     ADD        WK-CNT5         TO   WK-SUBCNT    .
        wkSubcnt += wkCnt5;
        // 026500     MOVE       WK-AMT5         TO   WK-RPT-AMT5  .
        wkRptAmt5 = wkAmt5;
        // 026600     ADD        WK-AMT5         TO   WK-SUBAMT    .
        wkSubamt = wkSubamt.add(wkAmt5);
        // 026700     MOVE       WK-SUBCNT       TO   WK-RPT-SUBCNT.
        wkRptSubcnt = wkSubcnt;
        // 026800     MOVE       WK-SUBAMT       TO   WK-RPT-SUBAMT.
        wkRptSubamt = wkSubamt;

        // 027000     IF         WK-SUBCNT       <    1 GO TO 4000-SUBTAIL-EXIT.
        if (wkSubcnt < 1) {
            return;
        }
        // 027200     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 027300     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE1.
        // 007500 01 WK-DETAIL-LINE1.
        // 007600    02 FILLER              PIC X(01) VALUE SPACE.
        sb.append(formatUtil.padX("", 1));
        // 007700    02 WK-RPT-CODE         PIC X(06).
        sb.append(formatUtil.padX("" + wkRptCode, 6));
        // 007800    02 FILLER              PIC X(63) VALUE SPACE.
        sb.append(formatUtil.padX("", 63));
        fileRPTPOSTContents.add(sb.toString());

        // 027400     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 027500     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE2.
        // 007900 01 WK-DETAIL-LINE2.
        // 008000    02 FILLER              PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 008100    02 WK-RPT-SMCODE       PIC X(11) VALUE "19834251   ".
        sb.append(formatUtil.padX("19834251   ", 11));
        // 008200    02 FILLER              PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 008300    02 WK-RPT-AMTTYPE      PIC X(13) VALUE "    6~  100".
        sb.append(formatUtil.padX("    6~  100", 13));
        // 008400    02 WK-RPT-CNT1         PIC ZZZ,ZZZ.
        sb.append(reportUtil.customFormat("" + wkRptCnt1, "ZZZ,ZZZ"));
        // 008500    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 008600    02 WK-RPT-AMT1         PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptAmt1, "ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());

        // 027600     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 027700     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE3.
        // 008700 01 WK-DETAIL-LINE3.
        // 008800    02 FILLER              PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 008900    02 WK-RPT-SMCODE       PIC X(11) VALUE "19834251   ".
        sb.append(formatUtil.padX("19834251   ", 11));
        // 009000    02 FILLER              PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 009100    02 WK-RPT-AMTTYPE      PIC X(13) VALUE "  101~ 1000".
        sb.append(formatUtil.padX("  101~ 1000", 13));
        // 009200    02 WK-RPT-CNT2         PIC ZZZ,ZZZ.
        sb.append(reportUtil.customFormat("" + wkRptCnt2, "ZZZ,ZZZ"));
        // 009300    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 009400    02 WK-RPT-AMT2         PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptAmt2, "ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());

        // 027800     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 027900     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE4.
        // 009500 01 WK-DETAIL-LINE4.
        // 009600    02 FILLER              PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 009700    02 WK-RPT-SMCODE       PIC X(11) VALUE "19834251   ".
        sb.append(formatUtil.padX("19834251   ", 11));
        // 009800    02 FILLER              PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 009900    02 WK-RPT-AMTTYPE      PIC X(13) VALUE " 1001~     ".
        sb.append(formatUtil.padX(" 1001~     ", 13));
        // 010000    02 WK-RPT-CNT3         PIC ZZZ,ZZZ.
        sb.append(reportUtil.customFormat("" + wkRptCnt3, "ZZZ,ZZZ"));
        // 010100    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 010200    02 WK-RPT-AMT3         PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptAmt3, "ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());

        // 028000     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 028100     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE5.
        // 010300 01 WK-DETAIL-LINE5.
        // 010400    02 FILLER              PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 010500    02 WK-RPT-SMCODE       PIC X(11) VALUE "50150412   ".
        sb.append(formatUtil.padX("50150412   ", 11));
        // 010600    02 FILLER              PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 010700    02 WK-RPT-AMTTYPE      PIC X(13) VALUE "    6~20000".
        sb.append(formatUtil.padX("    6~20000", 13));
        // 010800    02 WK-RPT-CNT4         PIC ZZZ,ZZZ.
        sb.append(reportUtil.customFormat("" + wkRptCnt4, "ZZZ,ZZZ"));
        // 010900    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 011000    02 WK-RPT-AMT4         PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptAmt4, "ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());

        // 028200     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 028300     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE6.
        // 011100 01 WK-DETAIL-LINE6.
        // 011200    02 FILLER              PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 011300    02 WK-RPT-SMCODE       PIC X(11) VALUE "50150412   ".
        sb.append(formatUtil.padX("50150412   ", 11));
        // 011400    02 FILLER              PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 011500    02 WK-RPT-AMTTYPE      PIC X(13) VALUE "20001~     ".
        sb.append(formatUtil.padX("20001~     ", 13));
        // 011600    02 WK-RPT-CNT5         PIC ZZZ,ZZZ.
        sb.append(reportUtil.customFormat("" + wkRptCnt5, "ZZZ,ZZZ"));
        // 011700    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 011800    02 WK-RPT-AMT5         PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptAmt5, "ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());

        // 028400     MOVE       SPACES         TO    REPORT-LINE    .
        // 028500     WRITE      REPORT-LINE    FROM  WK-GATE-LINE   .
        // 011900 01 WK-GATE-LINE.
        // 012000    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileRPTPOSTContents.add(sb.toString());
        // 028600     MOVE       SPACES         TO    REPORT-LINE    .
        sb = new StringBuilder();
        // 028700     WRITE      REPORT-LINE    FROM  WK-SUBTOTAL-LINE .
        // 012100 01 WK-SUBTOTAL-LINE.
        // 012200    02 FILLER                   PIC X(11) VALUE "  筆數共計  :".
        sb.append(formatUtil.padX("  筆數共計  :", 11));
        // 012300    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptSubcnt, "ZZZ,ZZZ,ZZ9"));
        // 012400    02 FILLER                   PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 012500    02 FILLER                   PIC X(11) VALUE "  金額共計  :".
        sb.append(formatUtil.padX("  金額共計  :", 11));
        // 012600    02 WK-RPT-SUBAMT            PIC ZZ,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptSubamt, "ZZ,ZZZ,ZZZ,ZZ9"));
        fileRPTPOSTContents.add(sb.toString());
        // 028800     MOVE       SPACES         TO    REPORT-LINE    .
        // 028900     WRITE      REPORT-LINE    AFTER 1              .
        fileRPTPOSTContents.add("");
        // 029000     MOVE       0  TO  WK-SUBCNT,WK-SUBAMT,WK-CNT1,WK-AMT1,
        // 029100                       WK-CNT2  ,WK-AMT2  ,WK-CNT3,WK-AMT3,
        // 029200                       WK-CNT4  ,WK-AMT4  ,WK-CNT5,WK-AMT5.
        wkSubcnt = 0;
        wkSubamt = new BigDecimal(0);
        wkCnt1 = 0;
        wkAmt1 = new BigDecimal(0);
        wkCnt2 = 0;
        wkAmt2 = new BigDecimal(0);
        wkCnt3 = 0;
        wkAmt3 = new BigDecimal(0);
        wkCnt4 = 0;
        wkAmt4 = new BigDecimal(0);
        wkCnt5 = 0;
        wkAmt5 = new BigDecimal(0);
        fileRPTPOSTContents.add(sb.toString());
    }

    private void title2000(String pageFg) {
        // 寫報表表頭(LINE1、2)
        // 018500     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        sb.append(pageFg);
        // 018600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX("", 10));
        // 004300    02 FILLER                          PIC X(54) VALUE
        // 004400       "  外部代收－中華郵政代收款級距統計表  ".
        sb.append(formatUtil.padX("  外部代收－中華郵政代收款級距統計表  ", 54));
        // 004500    02 FILLER                          PIC X(22) VALUE SPACE.
        sb.append(formatUtil.padX("", 22));
        // 004600    02 FILLER                          PIC X(12) VALUE
        // 004700       "FORM : C057 ".
        sb.append(formatUtil.padX("FORM : C057 ", 12));
        fileRPTPOSTContents.add(sb.toString());

        // 018700     MOVE       SPACES              TO     REPORT-LINE   .
        // 018800     WRITE      REPORT-LINE         AFTER  1             .
        fileRPTPOSTContents.add("");
        // 018900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004800 01 WK-TITLE-LINE2.
        // 004900    02 FILLER                          PIC X(11) VALUE
        // 005000       "   主辦行：  ".
        sb.append(formatUtil.padX("   主辦行：  ", 11));
        // 005100    02 WK-RPT-PBRNO                    PIC 9(03).
        sb.append(formatUtil.pad9("" + wkRptPbrno, 3));
        // 005200    02 FILLER                          PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 005300    02 FILLER                          PIC X(13) VALUE
        // 005400       "  印表日期：   ".
        sb.append(formatUtil.padX("  印表日期：   ", 13));
        // 005500    02 WK-RPT-PDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        // 005600    02 FILLER                          PIC X(65) VALUE SPACE.
        sb.append(formatUtil.padX("", 65));
        // 006000    02 FILLER                          PIC X(13) VALUE SPACE.
        sb.append(formatUtil.padX("", 13));
        // 006100    02 FILLER                          PIC X(11) VALUE
        // 006200       "      頁次  :".
        sb.append(formatUtil.padX("      頁次  ", 11));
        // 006300    02 WK-RPT-PAGE                     PIC 9(04).
        sb.append(formatUtil.pad9("" + wkRptPage, 4));
    }

    private void title2100() {
        // 寫報表表頭(LINE3)
        // 019600     MOVE       SPACES              TO     REPORT-LINE.
        sb = new StringBuilder();
        // 019700     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 006400 01 WK-TITLE-LINE3.
        // 006500    02 FILLER              PIC X(01) VALUE SPACES      .
        sb.append(formatUtil.padX("", 1));
        // 006600    02 FILLER              PIC X(10) VALUE "  代收類別  ".
        sb.append(formatUtil.padX("  代收類別  ", 10));
        // 006700    02 FILLER              PIC X(01) VALUE SPACES      .
        sb.append(formatUtil.padX("", 1));
        // 006800    02 FILLER              PIC X(15) VALUE "  劃撥帳號種類  ".
        sb.append(formatUtil.padX("  劃撥帳號種類  ", 15));
        // 006900    02 FILLER              PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 007000    02 FILLER              PIC X(10) VALUE "  金額級距  ".
        sb.append(formatUtil.padX("  金額級距  ", 10));
        // 007100    02 FILLER              PIC X(03) VALUE SPACE.
        sb.append(formatUtil.padX("", 3));
        // 007200    02 FILLER              PIC X(06) VALUE "  筆數  ".
        sb.append(formatUtil.padX("  筆數  ", 6));
        // 007300    02 FILLER              PIC X(07) VALUE SPACE.
        sb.append(formatUtil.padX("", 7));
        // 007400    02 FILLER              PIC X(06) VALUE "  金額  ".
        sb.append(formatUtil.padX("  金額  ", 6));

        fileRPTPOSTContents.add(sb.toString());
        // 019800     MOVE       SPACES              TO     REPORT-LINE.
        // 019900     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 011900 01 WK-GATE-LINE.
        // 012000    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileRPTPOSTContents.add(sb.toString());
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }
}
