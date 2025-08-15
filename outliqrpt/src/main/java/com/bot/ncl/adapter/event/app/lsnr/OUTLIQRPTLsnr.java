/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTLIQRPT;
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
@Component("OUTLIQRPTLsnr")
@Scope("prototype")
public class OUTLIQRPTLsnr extends BatchListenerCase<OUTLIQRPT> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    private OUTLIQRPT event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    // File related
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CL020_PATH = "CL020"; // 讀檔目錄
    private static final String _003_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_UPDRPT = "UPDRPT."; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME_UPDRPT = "CL-BH-055"; // 產檔檔名
    private static final String STRING_FCL0 = "FCL0";
    private String PATH_SEPARATOR = File.separator;
    private String PATH_DOT = ".";
    private String wkUpldir; // 讀檔路徑
    private String wkUplbaf; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileLIQRPTContents; // 檔案內容

    // String define (X)
    private String wkFilename;
    private String dirPutdir;
    private String wkUplcname;
    private String wkUplfilename;
    private String uplbafFeetype;
    private String wkUplfeetype;

    // int define (9)
    private int wkRdate;
    private int wkBdate;
    private int wkUdate;
    private int wkPdate1;
    private int wkPdate2;
    private int uplbafUpldate;
    private int uplbafCnt;
    private int wkUpldate;
    private int wkCnt;

    // Use BigDecimal type for amount
    private BigDecimal wkSubpbramt;
    private BigDecimal wkTotpbramt;
    private BigDecimal wkSubliqamt;
    private BigDecimal wkTotliqamt;
    private BigDecimal wkSubopfee;
    private BigDecimal wkTotopfee;
    private BigDecimal wkRptTotliqamt;
    private BigDecimal wkRptTotpbramt;
    private BigDecimal wkRptTotopfee;
    private BigDecimal wkSubLiqamt;
    private BigDecimal wkSubUplamt;
    private BigDecimal wkSubOpfee;
    private BigDecimal wkTopfee;
    private BigDecimal uplbafLiqamt;
    private BigDecimal uplbafPbramt;
    private BigDecimal wkLiqamt;
    private BigDecimal wkUplamt;
    private BigDecimal wkOpfee;
    private BigDecimal wkFeecost;
    private BigDecimal wkTotfeecost;
    private BigDecimal wkRptTotfeecost;
    private BigDecimal uplbafFee;
    private BigDecimal uplbafFeecost;
    private BigDecimal wkSubfeecost;
    private BigDecimal wkSubFeecost;

    //  @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTLIQRPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTLIQRPTLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTLIQRPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTLIQRPTLsnr run");
        init(event);

        //// FD-UPLDIR檔案存在，執行0000-MAIN-RTN
        //// 否則，結束程式
        // 017200     IF ATTRIBUTE RESIDENT OF FD-UPLDIR IS = VALUE(TRUE)
        // 017300       PERFORM 0000-MAIN-RTN    THRU    0000-MAIN-EXIT
        if (textFile.exists(wkUpldir)) {
            main();
        }
    }

    private void init(OUTLIQRPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTLIQRPTLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        // 016200 PROCEDURE        DIVISION  USING   WK-RDATE .
        wkRdate = parse.string2Integer(textMap.get("WK-RDATE")); // TODO: 待確認BATCH參數名稱
        //// 設定日期
        // 016700     MOVE    WK-RDATE           TO      WK-BDATE         ,
        // 016800                                        WK-UDATE         .
        // 016900     MOVE    PARA-YMD           TO      WK-PDATE2        ,
        // 017000                                        WK-PDATE1        .
        wkBdate = wkRdate;
        wkUdate = wkRdate;
        wkPdate1 = wkRdate;
        wkPdate2 = wkRdate;

        //// 設定檔名
        // 017100     CHANGE  ATTRIBUTE FILENAME  OF FD-UPLDIR TO WK-UPLDIR.
        // 005300  01 WK-UPLDIR.
        // 005400     03 FILLER                      PIC X(22)
        // 005500                              VALUE "DATA/GN/DWL/CL020/003/".
        // 005600     03 WK-UDATE                    PIC 9(07).
        // 005700     03 FILLER                      PIC X(08)
        // 005800                              VALUE "/UPDRPT.".
        // 讀檔路徑
        wkUpldir =
                fileDir
                        + CL020_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkUdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME_UPDRPT;
        // 產檔路徑
        // 002500 FD  REPORTFL
        // 002600     VALUE  OF  TITLE  IS  "BD/CL/BH/055."
        outputFilePath = fileDir + FILE_OUTPUT_NAME_UPDRPT;

        fileLIQRPTContents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTLIQRPTLsnr main");
        // 018000 0000-MAIN-RTN.
        // 開啟檔案
        // 018100     OPEN    INPUT             FD-UPLDIR.
        // 018200     OPEN    OUTPUT            REPORTFL .

        // 018300     PERFORM 1000-TITLE-RTN    THRU   1000-TITLE-EXIT.
        titleRTN();

        List<String> lines = textFile.readFileContent(wkUpldir, CHARSET_UTF8);

        int cnt = 0;
        for (String detail : lines) {
            // 018400 0000-MAIN-LOOP.
            // 循序讀取FD-UPLDIR，直到檔尾，跳到0000-MAIN-CLOSE
            // 018500     READ    FD-UPLDIR AT END  GO TO  0000-MAIN-CLOSE.

            cnt++;

            dirPutdir = detail.substring(9, 52);
            // 018600* 首筆或非相關檔案排除
            // 018700     IF     DIR-PUTDIR(31:4)   NOT =  "FCL0"
            if (!STRING_FCL0.equals(dirPutdir.substring(30, 34))) {
                // 018800       GO TO 0000-MAIN-LOOP.

                if (cnt == lines.size()) {
                    mainClose();
                }
                continue;
            }

            // 019000     MOVE DIR-PUTDIR(31:12)    TO     WK-FILENAME .
            wkFilename = dirPutdir.substring(30, 42);

            // 019015     IF  DIR-PUTDIR(37:1)   =      "5"
            if ("5".equals(dirPutdir.substring(36, 37))) {
                // 019020       MOVE    SPACES          TO     REPORT-LINE ,
                // 019025                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 019030       MOVE    " 悠遊付 "      TO     WK-UPLCNAME
                wkUplcname = " 悠遊付 ";

                // 019035       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 019040       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 019045       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 019055     IF  DIR-PUTDIR(37:1)   =      "6"
            if ("6".equals(dirPutdir.substring(36, 37))) {
                // 019060       MOVE    SPACES          TO     REPORT-LINE ,
                // 019065                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 019070       MOVE    " 愛金卡 "      TO     WK-UPLCNAME
                wkUplcname = " 愛金卡 ";

                // 019075       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 019080       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 019085       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 019110     IF  DIR-PUTDIR(37:1)   =      "7"
            if ("7".equals(dirPutdir.substring(36, 37))) {
                // 019120       MOVE    SPACES          TO     REPORT-LINE ,
                // 019130                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 019140       MOVE    "JKOS 街口 "    TO     WK-UPLCNAME
                wkUplcname = "JKOS 街口 ";

                // 019150       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 019160       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 019170       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 019200    IF  DIR-PUTDIR(37:1)   =      "8"
            if ("8".equals(dirPutdir.substring(36, 37))) {
                // 019210      MOVE    SPACES          TO     REPORT-LINE ,
                // 019220                                     WK-DTL-LINE1
                sb = new StringBuilder();

                // 019230      MOVE    " 全支付 "        TO     WK-UPLCNAME
                wkUplcname = " 全支付 ";

                // 019240      MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 019250      WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 019260      PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 019290     IF  DIR-PUTDIR(37:1)   =      "K"
            if ("K".equals(dirPutdir.substring(36, 37))) {
                // 019300       MOVE    SPACES          TO     REPORT-LINE ,
                // 019400                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 019500       MOVE    " 統一超商 "    TO     WK-UPLCNAME
                wkUplcname = " 統一超商 ";

                // 019600       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 019700       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 019800       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 020000     IF  DIR-PUTDIR(37:1)   =      "N"
            if ("N".equals(dirPutdir.substring(36, 37))) {
                // 020100       MOVE    SPACES          TO     REPORT-LINE ,
                // 020200                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 020300       MOVE    " 全家超商 "    TO     WK-UPLCNAME
                wkUplcname = " 全家超商 ";

                // 020400       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 020500       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 020600       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 020800     IF  DIR-PUTDIR(37:1)   =      "O"
            if ("O".equals(dirPutdir.substring(36, 37))) {
                // 020900       MOVE    SPACES          TO     REPORT-LINE ,
                // 021000                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 021100       MOVE    " OK 超商 "     TO     WK-UPLCNAME
                wkUplcname = " OK 超商 ";

                // 021200       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 021300       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 021400       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 021600     IF  DIR-PUTDIR(37:1)   =      "L"
            if ("L".equals(dirPutdir.substring(36, 37))) {
                // 021700       MOVE    SPACES          TO     REPORT-LINE ,
                // 021800                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 021900       MOVE    " 萊爾富 "      TO     WK-UPLCNAME
                wkUplcname = " 萊爾富 ";

                // 022000       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 022100       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 022200       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 022400     IF  DIR-PUTDIR(37:1)   =      "T"
            if ("T".equals(dirPutdir.substring(36, 37))) {
                // 022500       MOVE    SPACES          TO     REPORT-LINE ,
                // 022600                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 022700       MOVE    " 中國信託 "    TO     WK-UPLCNAME
                wkUplcname = " 中國信託 ";
                // 022800       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 022900       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 023000       PERFORM 2200-CTFH-RTN   THRU   2200-CTFH-EXIT .
                sevenEleven2200RTN(); // 2200-711
            }

            // 023200     IF  DIR-PUTDIR(37:1)   =      "Q"
            if ("Q".equals(dirPutdir.substring(36, 37))) {
                // 023300       MOVE    SPACES          TO     REPORT-LINE ,
                // 023400                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 023500       MOVE    " ｅ政府 "      TO     WK-UPLCNAME
                wkUplcname = " ｅ政府 ";

                // 023600       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 023700       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 023800       PERFORM 2200-CTFH-RTN   THRU   2200-CTFH-EXIT.
                sevenEleven2200RTN(); // 2200-711
            }

            // 024000     IF  DIR-PUTDIR(37:1)   =      "X"
            if ("X".equals(dirPutdir.substring(36, 37))) {
                // 024100       MOVE    SPACES          TO     REPORT-LINE ,
                // 024200                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 024300       MOVE    " 農業金庫 "    TO     WK-UPLCNAME
                wkUplcname = " 農業金庫 ";

                // 024400       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 024500       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 024600       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 024800     IF  DIR-PUTDIR(37:1)   =      "U"
            if ("U".equals(dirPutdir.substring(36, 37))) {
                // 024900       MOVE    SPACES          TO     REPORT-LINE ,
                // 025000                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025100       MOVE    " 中華郵政 "    TO     WK-UPLCNAME
                wkUplcname = " 中華郵政 ";

                // 025200       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025300       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025400       PERFORM 2300-POST-RTN   THRU   2300-POST-EXIT    .
                sevenEleven2300RTN(); // 2300-711
            }

            // 025510     IF  DIR-PUTDIR(37:1)   =      "Y"
            if ("Y".equals(dirPutdir.substring(36, 37))) {
                // 025520       MOVE    SPACES          TO     REPORT-LINE ,
                // 025530                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025540       MOVE    " 財金 "        TO     WK-UPLCNAME
                wkUplcname = " 財金 ";

                // 025550       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025560       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025570       PERFORM 2200-CTFH-RTN   THRU   2200-CTFH-EXIT    .
                sevenEleven2200RTN(); // 2200-711
            }

            // 025572     IF  DIR-PUTDIR(37:1)   =      "Z"
            if ("Z".equals(dirPutdir.substring(36, 37))) {
                // 025573       MOVE    SPACES          TO     REPORT-LINE ,
                // 025574                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025575       MOVE    " 行動支付 "    TO     WK-UPLCNAME
                wkUplcname = " 行動支付 ";

                // 025576       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025577       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025578       PERFORM 2200-CTFH-RTN   THRU   2200-CTFH-EXIT    .
                sevenEleven2200RTN(); // 2200-711
            }

            // 025582     IF  DIR-PUTDIR(37:1)   =      "2"
            if ("2".equals(dirPutdir.substring(36, 37))) {
                // 025584       MOVE    SPACES          TO     REPORT-LINE ,
                // 025586                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025588       MOVE    " 跨境支付 "    TO     WK-UPLCNAME
                wkUplcname = " 跨境支付 ";

                // 025590       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025592       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025594       PERFORM 2200-CTFH-RTN   THRU   2200-CTFH-EXIT    .
                sevenEleven2200RTN(); // 2200-711
            }

            // 025598     IF  DIR-PUTDIR(37:1)   =      "3"
            if ("3".equals(dirPutdir.substring(36, 37))) {
                // 025600       MOVE    SPACES          TO     REPORT-LINE ,
                // 025610                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025620       MOVE    " ＥＦＣＳ "    TO     WK-UPLCNAME
                wkUplcname = " ＥＦＣＳ ";

                // 025630       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025640       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025650       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            // 025652     IF  DIR-PUTDIR(37:1)   =      "4"
            if ("4".equals(dirPutdir.substring(36, 37))) {
                // 025653       MOVE    SPACES          TO     REPORT-LINE ,
                // 025654                                      WK-DTL-LINE1
                sb = new StringBuilder();

                // 025655       MOVE    " 一卡通 "      TO     WK-UPLCNAME
                wkUplcname = " 一卡通 ";

                // 025656       MOVE    DIR-PUTDIR(31:12) TO   WK-UPLFILENAME
                wkUplfilename = dirPutdir.substring(30, 42);

                // 025657       WRITE   REPORT-LINE     FROM   WK-DTL-LINE1
                sb.append(formatUtil.padX(wkUplcname, 10));
                sb.append(formatUtil.padX(wkUplfilename, 12));
                fileLIQRPTContents.add(sb.toString());

                // 025658       PERFORM 2100-711-RTN    THRU   2100-711-EXIT     .
                sevenElevenRTN(); // 2100-711
            }

            if (cnt == lines.size()) {
                mainClose();
            }
        }
    }

    private void mainClose() {
        // 寫表尾
        // 025800     MOVE    WK-TOTPBRAMT      TO     WK-RPT-TOTPBRAMT.
        wkRptTotpbramt = wkTotpbramt;
        // 025900     MOVE    WK-TOTLIQAMT      TO     WK-RPT-TOTLIQAMT.
        wkRptTotliqamt = wkTotliqamt;
        // 026000     MOVE    WK-TOTOPFEE       TO     WK-RPT-TOTOPFEE .
        wkRptTotopfee = wkTotopfee;
        // 026100     MOVE    WK-TOTFEECOST     TO     WK-RPT-TOTFEECOST.
        wkRptTotfeecost = wkTotfeecost;

        // 026200     WRITE   REPORT-LINE       FROM   WK-GATE-LINE  .
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 104));
        fileLIQRPTContents.add(sb.toString());
        // 026300     WRITE   REPORT-LINE       FROM   WK-TAIL1-LINE .
        // 013100 01 WK-TAIL1-LINE.
        // 013200    02 FILLER                       PIC X(04) VALUE SPACES  .
        // 013300    02 FILLER                       PIC X(14)
        // 013400                                      VALUE " 總清算金額： ".
        // 013500    02 WK-RPT-TOTLIQAMT             PIC Z,ZZZ,ZZZ,ZZ9       .
        // 013600    02 FILLER                       PIC X(10) VALUE SPACE   .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 總清算金額： ", 14));
        sb.append(reportUtil.customFormat("" + wkRptTotliqamt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        fileLIQRPTContents.add(sb.toString());

        // 026400     WRITE   REPORT-LINE       FROM   WK-TAIL2-LINE .
        // 013700 01 WK-TAIL2-LINE.
        // 013800    02 FILLER                       PIC X(04) VALUE SPACES  .
        // 013900    02 FILLER                       PIC X(14)
        // 014000                                      VALUE " 總入帳金額： ".
        // 014100    02 WK-RPT-TOTPBRAMT             PIC Z,ZZZ,ZZZ,ZZ9       .
        // 014200    02 FILLER                       PIC X(10) VALUE SPACE   .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 總入帳金額： ", 14));
        sb.append(reportUtil.customFormat("" + wkRptTotpbramt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        fileLIQRPTContents.add(sb.toString());

        // 026500     WRITE   REPORT-LINE       FROM   WK-TAIL3-LINE .
        // 014300 01 WK-TAIL3-LINE.
        // 014400    02 FILLER                       PIC X(04) VALUE SPACES  .
        // 014500    02 FILLER                       PIC X(16)
        // 014600                                    VALUE " 總分潤金額： "  .
        // 014700    02 WK-RPT-TOTOPFEE              PIC ZZZ,ZZZ,ZZ9         .
        // 014800    02 FILLER                       PIC X(10) VALUE SPACE   .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 總分潤金額： ", 16));
        sb.append(reportUtil.customFormat("" + wkRptTotopfee, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        fileLIQRPTContents.add(sb.toString());

        // 026600     WRITE   REPORT-LINE       FROM   WK-TAIL4-LINE .
        // 014900 01 WK-TAIL4-LINE.
        // 015000    02 FILLER                       PIC X(04) VALUE SPACES  .
        // 015100    02 FILLER                       PIC X(16)
        // 015200                                    VALUE " 總手續費用： "  .
        // 015300    02 WK-RPT-TOTFEECOST            PIC ZZZ,ZZZ,ZZ9         .
        // 015400    02 FILLER                       PIC X(10) VALUE SPACE   .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 總手續費用： ", 16));
        sb.append(reportUtil.customFormat("" + wkRptTotfeecost, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        fileLIQRPTContents.add(sb.toString());

        // 026700     CLOSE   REPORTFL          WITH   SAVE.
        try {
            textFile.writeFileContent(outputFilePath, fileLIQRPTContents, CHARSET_BIG5);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    public void titleRTN() {
        // 寫表頭
        // 027200     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        /// 006200 01 WK-TITLE-LINE1.
        sb = new StringBuilder();
        // 006300    02 FILLER                       PIC X(31) VALUE SPACE.
        sb.append(formatUtil.padX("", 31));
        // 006400    02 TITLE-LABEL                  PIC X(34)
        // 006500                    VALUE " 全行代理收付外部代收當日清算清單 ".
        sb.append(formatUtil.padX(" 全行代理收付外部代收當日清算清單 ", 34));
        // 006600    02 FILLER                       PIC X(25) VALUE SPACE.
        sb.append(formatUtil.padX("", 25));
        fileLIQRPTContents.add(sb.toString());

        // 027300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 006700  01 WK-TITLE-LINE2.
        // 006800     02 FILLER                      PIC X(79) VALUE SPACE.
        sb.append(formatUtil.padX("", 79));
        // 006900     02 FILLER                      PIC X(12)
        // 007000                                      VALUE " 印表日期： ".
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        // 007100     02 WK-PDATE1                   PIC Z99/99/99 .
        sb.append(reportUtil.customFormat("" + wkPdate1, "Z99/99/99"));
        fileLIQRPTContents.add(sb.toString());

        // 027400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 007200 01 WK-TITLE-LINE3.
        // 007300    02 FILLER                       PIC X(10)
        // 007400                              VALUE " 分行別： ".
        sb.append(formatUtil.padX(" 分行別： ", 10));
        // 007500    02 WK-PBRNO                     PIC X(03) VALUE "003".
        sb.append(formatUtil.padX("003", 3));
        // 007600    02 FILLER                       PIC X(12)
        // 007700                              VALUE " 清算日期： ".
        sb.append(formatUtil.padX(" 清算日期： ", 12));
        // 007800    02 WK-PDATE2                    PIC Z99/99/99  .
        sb.append(reportUtil.customFormat("" + wkPdate2, "Z99/99/99"));
        // 007900    02 FILLER                       PIC X(55) VALUE SPACE.
        sb.append(formatUtil.padX("", 55));
        // 008000    02 FILLER                       PIC X(12)
        // 008100                              VALUE "FORM : C055 ".
        sb.append(formatUtil.padX("FORM : C055 ", 12));
        fileLIQRPTContents.add(sb.toString());

        // 027500     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        sb = new StringBuilder();
        // 008200 01 WK-TITLE-LINE4.
        // 008300    02 FILLER                 PIC X(10) VALUE " 代收機構 "   .
        sb.append(formatUtil.padX(" 代收機構 ", 10));
        // 008400    02 FILLER                 PIC X(03) VALUE SPACES         .
        sb.append(formatUtil.padX("", 3));
        // 008500    02 FILLER                 PIC X(10) VALUE " 檔名     ".
        sb.append(formatUtil.padX(" 檔名     ", 10));
        // 008600    02 FILLER                 PIC X(13) VALUE " 手續費種類 ".
        sb.append(formatUtil.padX(" 手續費種類 ", 13));
        // 008700    02 FILLER                 PIC X(02) VALUE SPACES         .
        sb.append(formatUtil.padX("", 2));
        // 008800    02 FILLER                 PIC X(08) VALUE " 上傳日 "     .
        sb.append(formatUtil.padX(" 上傳日 ", 8));
        // 008900    02 FILLER                 PIC X(05) VALUE SPACES         .
        sb.append(formatUtil.padX("", 5));
        // 009000    02 FILLER                 PIC X(10) VALUE " 清算金額 "   .
        sb.append(formatUtil.padX(" 清算金額 ", 10));
        // 009100    02 FILLER                 PIC X(04) VALUE SPACES         .
        sb.append(formatUtil.padX("", 4));
        // 009200    02 FILLER                 PIC X(10) VALUE " 入帳金額 "   .
        sb.append(formatUtil.padX(" 入帳金額 ", 10));
        // 009300    02 FILLER                 PIC X(01) VALUE SPACES         .
        sb.append(formatUtil.padX("", 1));
        // 009400    02 FILLER                 PIC X(10) VALUE " 手續費用 "   .
        sb.append(formatUtil.padX(" 手續費用 ", 10));
        // 009500*   02 FILLER                 PIC X(01) VALUE SPACES         .
        sb.append(formatUtil.padX("", 1));
        // 009600    02 FILLER                 PIC X(10) VALUE " 分潤金額 "   .
        sb.append(formatUtil.padX(" 分潤金額 ", 10));
        // 009700    02 FILLER                 PIC X(01) VALUE SPACES         .
        sb.append(formatUtil.padX("", 1));
        // 009800    02 FILLER                 PIC X(06) VALUE " 筆數 "       .
        sb.append(formatUtil.padX(" 筆數 ", 6));
        fileLIQRPTContents.add(sb.toString());

        // 027600     WRITE      REPORT-LINE       FROM    WK-GATE-LINE  .
        sb = new StringBuilder();
        // 012900 01 WK-GATE-LINE.
        // 013000    02 FILLER                       PIC X(104) VALUE ALL "-".
        sb.append(formatUtil.padX("-", 104));
        fileLIQRPTContents.add(sb.toString());
    }

    // 2100-711-RTN
    public void sevenElevenRTN() {
        // 清變數
        // 028200     MOVE       0                 TO      WK-SUBPBRAMT   .
        // 028300     MOVE       0                 TO      WK-SUBLIQAMT   .
        // 028400     MOVE       0                 TO      WK-SUBFEECOST  .
        wkSubpbramt = new BigDecimal(0);
        wkSubliqamt = new BigDecimal(0);
        wkSubfeecost = new BigDecimal(0);

        // 設定檔名
        // 028500     CHANGE  ATTRIBUTE FILENAME OF FD-UPLBAF TO WK-UPLBAF.
        // 004400  01 WK-UPLBAF.
        // 004500     03 FILLER                      PIC X(22)
        // 004600                              VALUE "DATA/GN/DWL/CL020/003/".
        // 004700     03 WK-BDATE                    PIC 9(07).
        // 004800     03 FILLER                      PIC X(01)
        // 004900                              VALUE "/".
        // 005000     03 WK-FILENAME                 PIC X(12).
        // 005100     03 FILLER                      PIC X(01)
        // 005200                              VALUE ".".

        wkUpldir =
                fileDir
                        + CL020_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkBdate
                        + PATH_SEPARATOR
                        + wkFilename
                        + PATH_DOT;

        // FD-UPLBAF檔案不存在，跳到2100-711-EXIT
        // 028600     IF ATTRIBUTE RESIDENT OF FD-UPLBAF IS NOT = VALUE(TRUE)
        if (!textFile.exists(wkUpldir)) {
            // 028700       GO TO 2100-711-EXIT.
            return;
        }
        // 開啟檔案
        // 028800     OPEN INPUT  FD-UPLBAF.
        // 028900 2100-711-LOOP.

        // 循序讀取FD-UPLBAF，直到檔尾，跳到2100-711-LAST
        // 029000     READ    FD-UPLBAF AT END      GO TO  2100-711-LAST .

        List<String> lines = textFile.readFileContent(wkUpldir, CHARSET_UTF8);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            uplbafFeetype = detail.substring(29, 32);
            // 寫手續費明細
            // 029100     PERFORM 2110-711-RTN          THRU   2110-711-EXIT .
            sevenEleven2110RTN();
            // 029200     GO TO 2100-711-LOOP.
            if (cnt == lines.size()) {
                // 029300 2100-711-LAST.
                // 寫小計
                // 029400     PERFORM 2110-SUBTAIL-RTN      THRU   2110-SUBTAIL-EXIT.
                subtail();

                // 關閉檔案
                // 029500     CLOSE FD-UPLBAF WITH SAVE .
            }
        }
        // 029600 2100-711-EXIT.
        // 029700     EXIT.
    }

    private void sevenEleven2110RTN() {
        // 寫手續費明細
        // 030000     MOVE    SPACES                TO     WK-UPLCNAME    ,
        // 030100                                          WK-UPLFILENAME .
        wkUplcname = "";
        wkUplfilename = "";
        // 030200     IF      UPLBAF-FEETYPE        =      "634"
        // 030300       MOVE  "634 外加  6 元 "     TO     WK-UPLFEETYPE.
        // 030400     IF      UPLBAF-FEETYPE        =      "6D1"
        // 030500       MOVE  "6D1 內含  6 元 "     TO     WK-UPLFEETYPE.
        // 030600     IF      UPLBAF-FEETYPE        =      "63A"
        // 030700       MOVE  "63A 外加 10 元 "     TO     WK-UPLFEETYPE.
        // 030800     IF      UPLBAF-FEETYPE        =      "63D"
        // 030900       MOVE  "63D 外加  8 元 "     TO     WK-UPLFEETYPE.
        // 031000     IF      UPLBAF-FEETYPE        =      "63F"
        // 031100       MOVE  "63F 內含  8 元 "     TO     WK-UPLFEETYPE.
        // 031200     IF      UPLBAF-FEETYPE        =      "63E"
        // 031300       MOVE  "63E 外加 12 元 "     TO     WK-UPLFEETYPE.
        // 031400     IF      UPLBAF-FEETYPE        =      "63G"
        // 031500       MOVE  "63G 內含 12 元 "     TO     WK-UPLFEETYPE.
        // 031600     IF      UPLBAF-FEETYPE        =      "6T3"
        // 031700       MOVE  "6T3 外加 18 元 "     TO     WK-UPLFEETYPE.
        // 031800     IF      UPLBAF-FEETYPE        =      "6T4"
        // 031900       MOVE  "6T4 內含 18 元 "     TO     WK-UPLFEETYPE.
        // 032000     IF      UPLBAF-FEETYPE        =      "AG0"
        // 032100       MOVE  "AG0 外加  8 元 "     TO     WK-UPLFEETYPE.
        // 032200     IF      UPLBAF-FEETYPE        =      "AG1"
        // 032300       MOVE  "AG1 外加 10 元 "     TO     WK-UPLFEETYPE.
        // 032400     IF      UPLBAF-FEETYPE        =      "AG2"
        // 032500       MOVE  "AG2 內含  8 元 "     TO     WK-UPLFEETYPE.
        // 032600     IF      UPLBAF-FEETYPE        =      "AG3"
        // 032700       MOVE  "AG3 內含 10 元 "     TO     WK-UPLFEETYPE.
        // 032800     IF      UPLBAF-FEETYPE        =      "6II"
        // 032900       MOVE  "6II 外加 10 元 "     TO     WK-UPLFEETYPE.
        // 033000     IF      UPLBAF-FEETYPE        =      "6IJ"
        // 033100       MOVE  "6IJ 內含 10 元 "     TO     WK-UPLFEETYPE.
        // 033200     IF      UPLBAF-FEETYPE        =      "MP1"
        // 033300       MOVE  "MP1 外加 13 元 "     TO     WK-UPLFEETYPE.
        // 033400     IF      UPLBAF-FEETYPE        =      "MP5"
        // 033500       MOVE  "MP5 外加 26 元 "     TO     WK-UPLFEETYPE.
        // 033600     IF      UPLBAF-FEETYPE        =      "C69"
        // 033700       MOVE  "C69 內含 10 元 "     TO     WK-UPLFEETYPE.
        // 033800     IF      UPLBAF-FEETYPE        =      "63B"
        // 033900       MOVE  "63B 內含 15 元 "     TO     WK-UPLFEETYPE.
        // 034000     IF      UPLBAF-FEETYPE        =      "63P"
        // 034100       MOVE  "63P 內含 18 元 "     TO     WK-UPLFEETYPE.
        // 034102     IF      UPLBAF-FEETYPE        =      "MU0"
        // 034104       MOVE  "MU0 內含 15 元 "     TO     WK-UPLFEETYPE.
        // 034106     IF      UPLBAF-FEETYPE        =      "MU1"
        // 034108       MOVE  "MU1 外加 16 元 "     TO     WK-UPLFEETYPE.
        // 034110     IF      UPLBAF-FEETYPE        =      "MU2"
        // 034112       MOVE  "MU2 外加 17 元 "     TO     WK-UPLFEETYPE.
        // 034114     IF      UPLBAF-FEETYPE        =      "MU3"
        // 034116       MOVE  "MU3 外加 18 元 "     TO     WK-UPLFEETYPE.
        // 034118     IF      UPLBAF-FEETYPE        =      "MU4"
        // 034120       MOVE  "MU4 外加 19 元 "     TO     WK-UPLFEETYPE.
        // 034122     IF      UPLBAF-FEETYPE        =      "MU5"
        // 034124       MOVE  "MU5 外加 20 元 "     TO     WK-UPLFEETYPE.
        // 034126     IF      UPLBAF-FEETYPE        =      "MU6"
        // 034128       MOVE  "MU6 外加 21 元 "     TO     WK-UPLFEETYPE.
        // 034130     IF      UPLBAF-FEETYPE        =      "MU7"
        // 034132       MOVE  "MU7 外加 22 元 "     TO     WK-UPLFEETYPE.
        // 034134     IF      UPLBAF-FEETYPE        =      "MU8"
        // 034136       MOVE  "MU8 外加 23 元 "     TO     WK-UPLFEETYPE.
        // 034138     IF      UPLBAF-FEETYPE        =      "MU9"
        // 034140       MOVE  "MU9 外加 24 元 "     TO     WK-UPLFEETYPE.
        // 034142     IF      UPLBAF-FEETYPE        =      "MV0"
        // 034152       MOVE  "MV0 內含 20 元 "     TO     WK-UPLFEETYPE.
        // 034162     IF      UPLBAF-FEETYPE        =      "MV1"
        // 034172       MOVE  "MV1 外加 25 元 "     TO     WK-UPLFEETYPE.
        // 034174     IF      UPLBAF-FEETYPE        =      "AG4"
        // 034176       MOVE  "AG4 外加 10 元 "     TO     WK-UPLFEETYPE.
        // 034178     IF      UPLBAF-FEETYPE        =      "AG5"
        // 034180       MOVE  "AG5 內含 10 元 "     TO     WK-UPLFEETYPE.
        // 034182     IF      UPLBAF-FEETYPE        =      "63V"
        // 034184       MOVE  "63V 外加 12 元 "     TO     WK-UPLFEETYPE.
        // 034186     IF      UPLBAF-FEETYPE        =      "63R"
        // 034188       MOVE  "63R 內含 12 元 "     TO     WK-UPLFEETYPE.
        // 034190     IF      UPLBAF-FEETYPE        =      "63S"
        // 034192       MOVE  "63S 外加  6 元 "     TO     WK-UPLFEETYPE.
        // 034194     IF      UPLBAF-FEETYPE        =      "63T"
        // 034196       MOVE  "63T 內含 15 元 "     TO     WK-UPLFEETYPE.
        // 034197     IF      UPLBAF-FEETYPE        =      "A06"
        // 034198       MOVE  "A06 外加  6 元 "     TO     WK-UPLFEETYPE.
        // 034200     IF      UPLBAF-FEETYPE        =      "A08"
        // 034220       MOVE  "A08 外加  8 元 "     TO     WK-UPLFEETYPE.

        switch (uplbafFeetype) {
            case "634":
                wkUplfeetype = "634 外加 6 元";
                break;
            case "6D1":
                wkUplfeetype = "6D1 內含 6 元";
                break;
            case "63A":
                wkUplfeetype = "63A 外加 10 元";
                break;
            case "63D":
                wkUplfeetype = "63D 外加 8 元";
                break;
            case "63F":
                wkUplfeetype = "63F 內含 8 元";
                break;
            case "63E":
                wkUplfeetype = "63E 外加 12 元";
                break;
            case "63G":
                wkUplfeetype = "63G 內含 12 元";
                break;
            case "6T3":
                wkUplfeetype = "6T3 外加 18 元";
                break;
            case "6T4":
                wkUplfeetype = "6T4 內含 18 元";
                break;
            case "AG0":
                wkUplfeetype = "AG0 外加 8 元";
                break;
            case "AG1":
                wkUplfeetype = "AG1 外加 10 元";
                break;
            case "AG2":
                wkUplfeetype = "AG2 內含 8 元";
                break;
            case "AG3":
                wkUplfeetype = "AG3 內含 10 元";
                break;
            case "6II":
                wkUplfeetype = "6II 外加 10 元";
                break;
            case "6IJ":
                wkUplfeetype = "6IJ 內含 10 元";
                break;
            case "MP1":
                wkUplfeetype = "MP1 外加 13 元";
                break;
            case "MP5":
                wkUplfeetype = "MP5 外加 26 元";
                break;
            case "C69":
                wkUplfeetype = "C69 內含 10 元";
                break;
            case "63B":
                wkUplfeetype = "63B 內含 15 元";
                break;
            case "63P":
                wkUplfeetype = "63P 內含 18 元";
                break;
            case "MU0":
                wkUplfeetype = "MU0 內含 15 元";
                break;
            case "MU1":
                wkUplfeetype = "MU1 外加 16 元";
                break;
            case "MU2":
                wkUplfeetype = "MU2 外加 17 元";
                break;
            case "MU3":
                wkUplfeetype = "MU3 外加 18 元";
                break;
            case "MU4":
                wkUplfeetype = "MU4 外加 19 元";
                break;
            case "MU5":
                wkUplfeetype = "MU5 外加 20 元";
                break;
            case "MU6":
                wkUplfeetype = "MU6 外加 21 元";
                break;
            case "MU7":
                wkUplfeetype = "MU7 外加 22 元";
                break;
            case "MU8":
                wkUplfeetype = "MU8 外加 23 元";
                break;
            case "MU9":
                wkUplfeetype = "MU9 外加 24 元";
                break;
            case "MV0":
                wkUplfeetype = "MV0 內含 20 元";
                break;
            case "MV1":
                wkUplfeetype = "MV1 外加 25 元";
                break;
            case "AG4":
                wkUplfeetype = "AG4 外加 10 元";
                break;
            case "AG5":
                wkUplfeetype = "AG5 內含 10 元";
                break;
            case "63V":
                wkUplfeetype = "63V 外加 12 元";
                break;
            case "63R":
                wkUplfeetype = "63R 內含 12 元";
                break;
            case "63S":
                wkUplfeetype = "63S 外加 6 元";
                break;
            case "63T":
                wkUplfeetype = "63T 內含 15 元";
                break;
            case "A06":
                wkUplfeetype = "A06 外加 6 元";
                break;
            case "A08":
                wkUplfeetype = "A08 外加 8 元";
                break;
            default:
                wkUplfeetype = "Unknown FEETYPE";
                break;
        }

        // 034240     MOVE    UPLBAF-UPLDATE        TO     WK-UPLDATE   .
        wkUpldate = uplbafUpldate;
        // 034300     MOVE    UPLBAF-LIQAMT         TO     WK-LIQAMT    .
        wkLiqamt = uplbafLiqamt;
        // 034400     MOVE    UPLBAF-PBRAMT         TO     WK-UPLAMT    .
        wkUplamt = uplbafPbramt;
        // 034500     MOVE    UPLBAF-FEE            TO     WK-OPFEE     .
        wkOpfee = uplbafFee;
        // 034600     MOVE    UPLBAF-FEECOST        TO     WK-FEECOST   .
        wkFeecost = uplbafFeecost;
        // 034700     ADD     UPLBAF-PBRAMT         TO     WK-SUBPBRAMT ,
        // 034800                                          WK-TOTPBRAMT .
        wkSubpbramt = wkSubpbramt.add(uplbafPbramt);
        wkTotpbramt = wkTotpbramt.add(uplbafPbramt);
        // 034900     ADD     UPLBAF-LIQAMT         TO     WK-SUBLIQAMT ,
        // 035000                                          WK-TOTLIQAMT .
        wkSubliqamt = wkSubliqamt.add(uplbafLiqamt);
        wkTotliqamt = wkTotliqamt.add(uplbafLiqamt);
        // 035100     ADD     UPLBAF-FEE            TO     WK-SUBOPFEE  ,
        // 035200                                          WK-TOTOPFEE  .
        wkSubopfee = wkSubopfee.add(uplbafFee);
        wkTotopfee = wkTotopfee.add(uplbafFee);
        // 035300     ADD     UPLBAF-FEECOST        TO     WK-SUBFEECOST,
        // 035400                                          WK-TOTFEECOST.
        wkSubfeecost = wkSubfeecost.add(uplbafFeecost);
        wkTotfeecost = wkTotfeecost.add(uplbafFeecost);
        // 035500     MOVE    UPLBAF-CNT            TO     WK-CNT       .
        wkCnt = uplbafCnt;
        // 035600     MOVE    SPACES                TO     REPORT-LINE  .
        sb = new StringBuilder();
        // 035700     WRITE   REPORT-LINE           FROM   WK-DTL-LINE1 .
    }

    private void sevenEleven2200RTN() {

        //// 清變數
        //
        // 037400     MOVE    0                     TO     WK-SUBPBRAMT   .
        // 037500     MOVE    0                     TO     WK-SUBLIQAMT   .
        // 037600     MOVE    0                     TO     WK-TOPFEE      .
        wkSubpbramt = new BigDecimal(0);
        wkSubliqamt = new BigDecimal(0);
        wkTopfee = new BigDecimal(0);
        //
        //// 設定檔名
        //
        // 037700     CHANGE  ATTRIBUTE FILENAME OF FD-UPLBAF TO WK-UPLBAF.
        // 004400  01 WK-UPLBAF.
        // 004500     03 FILLER                      PIC X(22)
        // 004600                              VALUE "DATA/GN/DWL/CL020/003/".
        // 004700     03 WK-BDATE                    PIC 9(07).
        // 004800     03 FILLER                      PIC X(01)
        // 004900                              VALUE "/".
        // 005000     03 WK-FILENAME                 PIC X(12).
        // 005100     03 FILLER                      PIC X(01)
        // 005200                              VALUE ".".
        wkUplbaf =
                fileDir
                        + CL020_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkBdate
                        + PATH_SEPARATOR
                        + wkFilename
                        + PATH_DOT;
        //
        //// FD-UPLBAF檔案不存在，跳到2200-CTFH-EXIT
        // 037800     IF ATTRIBUTE RESIDENT OF FD-UPLBAF IS NOT = VALUE(TRUE)
        if (!textFile.exists(wkUplbaf)) {
            // 037900       GO TO 2200-CTFH-EXIT.
            return;
        }

        //
        //// 開啟檔案
        //
        // 038000     OPEN INPUT  FD-UPLBAF.
        // 038100 2200-CTFH-LOOP.
        //
        //// 循序讀取FD-UPLBAF，直到檔尾，跳到2200-CTFH-LAST
        //
        // 038200     READ    FD-UPLBAF AT END      GO TO  2200-CTFH-LAST .
        List<String> lines = textFile.readFileContent(wkUplbaf, CHARSET_UTF8);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03 UPLBAF-FNAME	X(12) 0-12
            // 03 UPLBAF-UPLDATE	9(08) 12-20
            // 03 UPLBAF-UPDDATE	9(08) 20-28
            // 03 UPLBAF-TXTYPE	X(01) 28-29
            // 03 UPLBAF-FEETYPE	X(03) 29-32
            // 03 UPLBAF-AMTDATA	GROUP
            //  05 UPLBAF-AMT	9(13) 32-45
            //  05 UPLBAF-CNT	9(06) 45-51
            //  05 FILLER	X(59) 51-110
            // 03 UPLBAF-AMTDATA-R	REDEFINES UPLBAF-AMTDATA
            //  05 FILLER	9(19) 32-51
            //  05 UPLBAF-LIQAMT	9(13) 51-64
            //  05 UPLBAF-PBRAMT	9(13) 64-77
            //  05 UPLBAF-FEECOST	9(08) 77-85
            //  05 UPLBAF-FEE	9(08) 85-93
            //  05 FILLER	X(17) 93-110
            uplbafUpldate = parse.string2Integer(detail.substring(12, 20));
            uplbafLiqamt = parse.string2BigDecimal(detail.substring(51, 64));
            uplbafPbramt = parse.string2BigDecimal(detail.substring(64, 77));
            uplbafCnt = parse.string2Integer(detail.substring(45, 51));
            //// 寫明細
            //
            // 038300     PERFORM 2210-CTFH-RTN         THRU   2210-CTFH-EXIT .
            ctfh();
            // 038400     GO TO 2200-CTFH-LOOP.
            if (cnt == lines.size()) {
                // 038500 2200-CTFH-LAST.
                //
                //// 關閉檔案
                //
                // 038600     CLOSE FD-UPLBAF WITH SAVE .
            }
        }
    }

    private void ctfh() {
        //// 寫明細
        //
        // 039100     MOVE    SPACES                TO     WK-UPLCNAME    ,
        // 039200                                          WK-UPLFILENAME .
        wkUplcname = "";
        wkUplfilename = "";

        // 039300     MOVE    " N/A "               TO     WK-UPLFEETYPE.
        wkUplfeetype = " N/A ";
        // 039400     MOVE    UPLBAF-UPLDATE        TO     WK-UPLDATE   .
        wkUpldate = uplbafUpldate;
        // 039500     MOVE    UPLBAF-LIQAMT         TO     WK-LIQAMT    .
        wkLiqamt = uplbafLiqamt;
        // 039600     MOVE    UPLBAF-PBRAMT         TO     WK-UPLAMT    .
        wkUplamt = uplbafPbramt;
        // 039700     ADD     UPLBAF-LIQAMT         TO     WK-TOTLIQAMT .
        wkTotliqamt = wkTotliqamt.add(uplbafLiqamt);
        // 039800     ADD     UPLBAF-PBRAMT         TO     WK-TOTPBRAMT .
        wkTotpbramt = wkTotpbramt.add(uplbafPbramt);
        // 039900     MOVE    0                     TO     WK-OPFEE     .
        wkOpfee = new BigDecimal(0);
        // 040000     MOVE    0                     TO     WK-FEECOST   .
        wkFeecost = new BigDecimal(0);
        // 040100     ADD     0                     TO     WK-TOTOPFEE  .
        wkTotopfee = wkTotopfee.add(new BigDecimal(0));
        // 040200     ADD     0                     TO     WK-TOTFEECOST.
        wkTotfeecost = wkTotfeecost.add(new BigDecimal(0));
        // 040300     MOVE    UPLBAF-CNT            TO     WK-CNT       .
        wkCnt = uplbafCnt;
        // 040400     MOVE    SPACES                TO     REPORT-LINE  .
        sb = new StringBuilder();
        // 040500     WRITE   REPORT-LINE           FROM   WK-DTL-LINE1 .
        // 010000 01 WK-DTL-LINE1.
        // 010100    02 WK-UPLCNAME                  PIC X(10) .
        sb.append(formatUtil.padX(wkUplcname, 10));
        // 010200    02 WK-UPLFILENAME               PIC X(12) .
        sb.append(formatUtil.padX(wkUplcname, 12));
        // 010400    02 WK-UPLFEETYPE                PIC X(15) .
        sb.append(formatUtil.padX(wkUplfeetype, 15));
        // 010600    02 WK-UPLDATE                   PIC ZZ99/99/99.
        sb.append(reportUtil.customFormat("" + wkUpldate, "ZZ99/99/99"));
        // 010700    02 FILLER                       PIC X(01) .
        sb.append(formatUtil.padX("", 1));
        // 010800    02 WK-LIQAMT                    PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkLiqamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 010900    02 FILLER                       PIC X(01) .
        sb.append(formatUtil.padX("", 1));
        // 011000    02 WK-UPLAMT                    PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkUplamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 011100    02 FILLER                       PIC X(02) VALUE SPACES  .
        sb.append(formatUtil.padX("", 2));
        // 011200    02 WK-FEECOST                   PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkFeecost, "ZZZ,ZZ9"));
        // 011300    02 FILLER                       PIC X(02) .
        sb.append(formatUtil.padX("", 2));
        // 011400    02 WK-OPFEE                     PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkOpfee, "ZZZ,ZZ9"));
        // 011500    02 FILLER                       PIC X(02) VALUE SPACES  .
        sb.append(formatUtil.padX("", 2));
        // 011600    02 WK-CNT                       PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkCnt, "ZZ,ZZ9"));
        fileLIQRPTContents.add(formatUtil.padX(sb.toString(), 104));
    }

    private void sevenEleven2300RTN() {
        //// 清變數
        //
        // 041000     MOVE    0                     TO     WK-SUBPBRAMT   .
        // 041100     MOVE    0                     TO     WK-SUBLIQAMT   .
        // 041200     MOVE    0                     TO     WK-TOPFEE      .
        wkSubpbramt = new BigDecimal(0);
        wkSubliqamt = new BigDecimal(0);
        wkTopfee = new BigDecimal(0);

        //
        //// 設定檔名
        //
        // 041300     CHANGE  ATTRIBUTE FILENAME OF FD-UPLBAF TO WK-UPLBAF.
        wkUpldir =
                fileDir
                        + CL020_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkBdate
                        + PATH_SEPARATOR
                        + wkFilename
                        + PATH_DOT;

        //// FD-UPLBAF檔案不存在，跳到2300-POST-EXIT.
        // 041400     IF ATTRIBUTE RESIDENT OF FD-UPLBAF IS NOT = VALUE(TRUE)
        if (!textFile.exists(wkUpldir)) {
            // 041500       GO TO 2300-POST-EXIT.
            return;
        }
        //
        //// 開啟檔案
        //
        // 041600     OPEN INPUT  FD-UPLBAF.
        // 041700 2300-POST-LOOP.
        //
        //// 循序讀取FD-UPLBAF，直到檔尾，跳到2300-POST-LAST
        //
        // 041800     READ    FD-UPLBAF AT END      GO TO  2300-POST-LAST .
        List<String> lines = textFile.readFileContent(wkUpldir, CHARSET_UTF8);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;

            // 03 UPLBAF-FNAME	X(12) 0-12
            // 03 UPLBAF-UPLDATE	9(08) 12-20
            // 03 UPLBAF-UPDDATE	9(08) 20-28
            // 03 UPLBAF-TXTYPE	X(01) 28-29
            // 03 UPLBAF-FEETYPE	X(03) 29-32
            // 03 UPLBAF-AMTDATA	GROUP
            //  05 UPLBAF-AMT	9(13) 32-45
            //  05 UPLBAF-CNT	9(06) 45-51
            //  05 FILLER	X(59) 51-110
            // 03 UPLBAF-AMTDATA-R	REDEFINES UPLBAF-AMTDATA
            //  05 FILLER	9(19) 32-51
            //  05 UPLBAF-LIQAMT	9(13) 51-64
            //  05 UPLBAF-PBRAMT	9(13) 64-77
            //  05 UPLBAF-FEECOST	9(08) 77-85
            //  05 UPLBAF-FEE	9(08) 85-93
            //  05 FILLER	X(17) 93-110
            uplbafUpldate = parse.string2Integer(detail.substring(12, 20));
            uplbafLiqamt = parse.string2BigDecimal(detail.substring(51, 64));
            uplbafPbramt = parse.string2BigDecimal(detail.substring(64, 77));
            uplbafCnt = parse.string2Integer(detail.substring(45, 51));
            uplbafFee = parse.string2BigDecimal(detail.substring(85, 93));
            uplbafFeecost = parse.string2BigDecimal(detail.substring(77, 85));
            //// 寫明細
            //
            // 041900     PERFORM 2310-POST-RTN         THRU   2310-POST-EXIT .
            post();
            // 042000     GO TO 2300-POST-LOOP.

            if (cnt == lines.size()) {
                // 042100 2300-POST-LAST.
                //
                //// 關閉檔案
                //
                // 042200     CLOSE FD-UPLBAF WITH SAVE .
            }
        }
    }

    private void post() {
        //// 寫明細
        //
        // 042700     MOVE    SPACES                TO     WK-UPLCNAME    ,
        // 042800                                          WK-UPLFILENAME .
        wkUplcname = "";
        wkUplfilename = "";
        // 042900     IF      UPLBAF-FEETYPE        =      "198"
        // 043000       MOVE  " 劃撥 19834251 "     TO     WK-UPLFEETYPE.
        // 043100     IF      UPLBAF-FEETYPE        =      "501"
        // 043200       MOVE  " 劃撥 50150412 "     TO     WK-UPLFEETYPE.
        switch (uplbafFeetype) {
            case "198":
                wkUplfeetype = " 劃撥 19834251 ";
                break;
            case "501":
                wkUplfeetype = " 劃撥 50150412 ";
                break;
        }
        // 043300     MOVE    UPLBAF-UPLDATE        TO     WK-UPLDATE   .
        wkUpldate = uplbafUpldate;
        // 043400     MOVE    UPLBAF-LIQAMT         TO     WK-LIQAMT    .
        wkLiqamt = uplbafLiqamt;
        // 043500     MOVE    UPLBAF-PBRAMT         TO     WK-UPLAMT    .
        wkUplamt = uplbafPbramt;
        // 043600     ADD     UPLBAF-LIQAMT         TO     WK-TOTLIQAMT .
        wkTotliqamt = wkTotliqamt.add(uplbafLiqamt);
        // 043700     ADD     UPLBAF-PBRAMT         TO     WK-TOTPBRAMT .
        wkTotpbramt = wkTotpbramt.add(uplbafPbramt);
        // 043800     MOVE    UPLBAF-FEE            TO     WK-OPFEE     .
        wkOpfee = uplbafFee;
        // 043900     MOVE    UPLBAF-FEECOST        TO     WK-FEECOST   .
        wkFeecost = uplbafFeecost;
        // 044000     ADD     UPLBAF-FEE            TO     WK-TOTOPFEE  .
        wkTotopfee = wkTotopfee.add(uplbafFee);
        // 044100     ADD     UPLBAF-FEECOST        TO     WK-TOTFEECOST.
        wkTotfeecost = wkTotfeecost.add(uplbafFeecost);
        // 044200     MOVE    UPLBAF-CNT            TO     WK-CNT       .
        wkCnt = uplbafCnt;
        // 044300     MOVE    SPACES                TO     REPORT-LINE  .
        // 044400     WRITE   REPORT-LINE           FROM   WK-DTL-LINE1 .
        // 010000 01 WK-DTL-LINE1.
        // 010100    02 WK-UPLCNAME                  PIC X(10) .
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkUplcname, 10));
        // 010200    02 WK-UPLFILENAME               PIC X(12) .
        sb.append(formatUtil.padX(wkUplcname, 12));
        // 010400    02 WK-UPLFEETYPE                PIC X(15) .
        sb.append(formatUtil.padX(wkUplfeetype, 15));
        // 010600    02 WK-UPLDATE                   PIC ZZ99/99/99.
        sb.append(reportUtil.customFormat("" + wkUpldate, "ZZ99/99/99"));
        // 010700    02 FILLER                       PIC X(01) .
        sb.append(formatUtil.padX("", 1));
        // 010800    02 WK-LIQAMT                    PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkLiqamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 010900    02 FILLER                       PIC X(01) .
        sb.append(formatUtil.padX("", 1));
        // 011000    02 WK-UPLAMT                    PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkUplamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 011100    02 FILLER                       PIC X(02) VALUE SPACES  .
        sb.append(formatUtil.padX("", 2));
        // 011200    02 WK-FEECOST                   PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkFeecost, "ZZZ,ZZ9"));
        // 011300    02 FILLER                       PIC X(02) .
        sb.append(formatUtil.padX("", 2));
        // 011400    02 WK-OPFEE                     PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkOpfee, "ZZZ,ZZ9"));
        // 011500    02 FILLER                       PIC X(02) VALUE SPACES  .
        sb.append(formatUtil.padX("", 2));
        // 011600    02 WK-CNT                       PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkCnt, "ZZ,ZZ9"));
        fileLIQRPTContents.add(formatUtil.padX(sb.toString(), 104));
    }

    private void subtail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTLIQRPTLsnr subtail");
        // 036100 2110-SUBTAIL-RTN.
        // 寫小計
        // 036200     MOVE    WK-SUBLIQAMT          TO     WK-SUB-LIQAMT.
        wkSubLiqamt = wkSubliqamt;
        // 036300     MOVE    WK-SUBPBRAMT          TO     WK-SUB-UPLAMT.
        wkSubUplamt = wkSubpbramt;
        // 036400     MOVE    WK-SUBFEECOST         TO     WK-SUB-FEECOST.
        wkSubFeecost = wkSubfeecost;
        // 036500     MOVE    WK-SUBOPFEE           TO     WK-SUB-OPFEE .
        wkSubOpfee = wkSubopfee;
        // 036600     WRITE   REPORT-LINE           FROM   WK-DTL-LINE2 .
        // 011700 01 WK-DTL-LINE2.
        sb = new StringBuilder();
        // 011800    02 FILLER                       PIC X(21) VALUE SPACES  .
        sb.append(formatUtil.padX("", 21));
        // 011900    02 WK-UPLFEETYPE2               PIC X(08) VALUE " 小計： ".
        sb.append(formatUtil.padX(" 小計： ", 8));
        // 012000    02 FILLER                       PIC X(19) VALUE SPACES  .
        sb.append(formatUtil.padX("", 19));
        // 012100    02 WK-SUB-LIQAMT                PIC Z,ZZZ,ZZZ,ZZ9       .
        sb.append(reportUtil.customFormat("" + wkSubLiqamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 012200    02 FILLER                       PIC X(01) VALUE SPACES  .
        sb.append(formatUtil.padX("", 1));
        // 012300    02 WK-SUB-UPLAMT                PIC Z,ZZZ,ZZZ,ZZ9       .
        sb.append(reportUtil.customFormat("" + wkSubUplamt, "Z,ZZZ,ZZZ,ZZ9"));
        // 012400    02 FILLER                       PIC X(01) VALUE SPACES  .
        sb.append(formatUtil.padX("", 1));
        // 012500    02 WK-SUB-FEECOST               PIC ZZZZ,ZZ9            .
        sb.append(reportUtil.customFormat("" + wkSubFeecost, "ZZZZ,ZZ9"));
        // 012600    02 FILLER                       PIC X(02) VALUE SPACES  .
        sb.append(formatUtil.padX("", 2));
        // 012700    02 WK-SUB-OPFEE                 PIC ZZZ,ZZ9             .
        sb.append(reportUtil.customFormat("" + wkSubOpfee, "ZZZ,ZZ9"));

        // 036700     MOVE    0                     TO     WK-SUBPBRAMT ,
        // 036800                                          WK-SUBLIQAMT ,
        // 036900                                          WK-SUBOPFEE  .
        wkSubpbramt = new BigDecimal(0);
        wkSubliqamt = new BigDecimal(0);
        wkSubopfee = new BigDecimal(0);
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }
}
