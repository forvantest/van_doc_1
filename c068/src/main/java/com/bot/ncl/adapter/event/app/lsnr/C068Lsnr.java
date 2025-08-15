/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.C068;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C068Lsnr")
@Scope("prototype")
public class C068Lsnr extends BatchListenerCase<C068> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private StringBuilder sb = new StringBuilder();
    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String CHARSET_BIG5 = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_TMP_NAME = "TMPC068"; // tmp檔名
    private static final String FILE_NAME = "CL-BH-C068"; // 檔名
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔路徑
    private String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // 產檔路徑
    private String inputFilePath; // 讀檔路徑
    private String sortTmpFilePath; // C068路徑
    private List<String> sortTmpFileContents; // Tmp檔案內容
    private List<String> fileC068Contents; // 檔案內容
    private int wkSubCnt;
    private BigDecimal wkSubAmt;
    private int wkTotCnt;
    private BigDecimal wkTotAmt;
    private int wkPbrno;
    private int wkRptPbrno;
    private String wkDate;
    private int wkTbsDy;
    private String wkTbsDyYY;
    private String wkTbsDyMM;
    private String wkTbsDyDD;
    private DecimalFormat tAmtFormat = new DecimalFormat("#,###,###,###,##0"); // 總計金額格式
    private DecimalFormat dAmtFormat = new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式
    private Boolean isBlankFile = false;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C068 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C068Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C068 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C068Lsnr run ....");
        init(event);
        sortFileContent();
        if (isBlankFile) {
            toWriteC068BlankFile();
        } else {
            sortOutRtn();
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC068Contents, CHARSET_BIG5);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    // 初始化INITIAL-RTN.
    private void init(C068 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C068Lsnr init ....");
        //    MOVE              0     TO     WK-SUBCNT.
        //    MOVE              0     TO     WK-SUBAMT.
        //    MOVE              0     TO     WK-TOTCNT.
        //    MOVE              0     TO     WK-TOTAMT.
        //    MOVE              0     TO     WK-PBRNO.
        wkSubCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkTotCnt = 0;
        wkTotAmt = BigDecimal.ZERO;
        wkPbrno = 0;
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        outputFilePath = fileDir + FILE_NAME;
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpFileContents = new ArrayList<>();
        fileC068Contents = new ArrayList<>();
        // 015100     MOVE  FD-BHDATE-TBSDY  TO   WK-TEMP-DATE.
        // 015200     MOVE  WK-TEMP-DATE     TO   WK-DATE-R.
        wkTbsDy = event.getAggregateBuffer().getTxCom().getTbsdy();
        wkTbsDyYY = String.format("%07d", wkTbsDy).substring(0, 3);
        wkTbsDyMM = String.format("%07d", wkTbsDy).substring(3, 5);
        wkTbsDyDD = String.format("%07d", wkTbsDy).substring(5, 7);
    }

    private void sortFileContent() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC068File sortFileContent ....");
        //        File inputFile = new File(inputFilePath);
        //     IF  KPUTH-CODE NOT = "121444"
        //     GO  TO  SORT-IN-RTN .
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            if ("121444".equals(detail.substring(10, 16))) {
                cnt++;
                sortTmpFileContents.add(detail);
            }
        }
        if (cnt == 0) {
            isBlankFile = true;
            return;
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpFileContents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        File sortTmpFile = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        //  SD-PBRNO主辦行, SD-DATE代收日, SD-RCPTID銷帳號碼 由小至大排序
        keyRanges.add(new KeyRange(21, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(33, 7, SortBy.ASC));
        keyRanges.add(new KeyRange(17, 16, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges, CHARSET);
    }

    private void sortOutRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC068File sortOutRtn ....");

        // SORT-OUT-RTN
        List<String> lines = textFile.readFileContent(sortTmpFilePath, CHARSET);

        for (String detail : lines) {

            int sdPbrno = parse.string2Integer(detail.substring(20, 23));
            BigDecimal sdAmt = parse.string2BigDecimal(detail.substring(54, 64));
            // IF WK-PBRNO                  =    0
            if (wkPbrno == 0) {
                // 025900       MOVE    SD-PBRNO             TO   WK-PBRNO,RPT064-PBRNO-R
                // 026100       MOVE    SD-DATE              TO   WK-DATE
                // 026300       ADD     SD-AMT               TO   WK-SUBAMT,
                // 026400                                         WK-TOTAMT
                // 026500       ADD     1                    TO   WK-SUBCNT,
                // 026600                                         WK-TOTCNT
                // 026650       PERFORM RPT064-WTIT-RTN    THRU RPT064-WTIT-EXIT
                // 026700       GO  TO  SORT-OUT-RTN.
                wkPbrno = parse.string2Integer(detail.substring(20, 23));
                wkRptPbrno = parse.string2Integer(detail.substring(20, 23));
                wkDate = detail.substring(32, 39);
                wkSubAmt = wkSubAmt.add(sdAmt);
                wkTotAmt = wkTotAmt.add(sdAmt);
                wkSubCnt = wkSubCnt + 1;
                wkTotCnt = wkTotCnt + 1;
                rpt064WtitRtn();
            } else if (sdPbrno == wkPbrno) {
                // * 相同代付主辦行相同則續作加總
                //     IF        SD-PBRNO           =    WK-PBRNO
                //       PERFORM DTL-SUM-RTN        THRU DTL-SUM-EXIT
                dtlSumRtn(sdAmt);
            } else {
                //     ELSE
                // * 不同主辦行，則列印上述的加總後，再繼續找統計下個主辦行
                //       PERFORM RPT-DTL-RTN        THRU RPT-DTL-EXIT
                //       MOVE    SD-PBRNO           TO   WK-PBRNO.
                rptDtlRtn(detail, sdAmt);
                wkPbrno = sdPbrno;
            }
        }
        //     RETURN    SORTFL  AT  END
        //       GO  TO  SORT-OUT-LAST.
        lastRecodeRtn();
    }

    // 計算小計總計DTL-SUM-RTN.
    private void dtlSumRtn(BigDecimal sdAmt) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC068File dtlSumRtn ....");
        //    ADD               1     TO     WK-SUBCNT,
        //                                   WK-TOTCNT.
        //    ADD       SD-AMT        TO     WK-SUBAMT,
        //                                   WK-TOTAMT.
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);
    }

    private void rpt064WtitRtn() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC068File rpt064WtitRtn ....");
        // RPT064-WTIT-RTN
        //    MOVE     WK-YEAR                 TO      WK-YEAR-P.
        //    MOVE     WK-MONTH                TO      WK-MONTH-P.
        //    MOVE     WK-YEAR                 TO      WK-YYY-R.
        //    MOVE     WK-MONTH                TO      WK-MM-R.
        //    MOVE     WK-DD                   TO      WK-DD-R.
        //    init裡做
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC068Contents.add("");
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC068Contents.add("");
        //    WRITE    REPORT-LINE           FROM      RPT064-TIT1.
        // 01   RPT064-TIT1.
        //  03  FILLER                              PIC X(12) VALUE SPACES.
        //  03  FILLER                              PIC X(40) VALUE
        //      " 綜所稅退稅憑單報表－依主辦行統計兌付 ".
        //  03  FILLER                              PIC X(06) VALUE SPACES.
        //  03  FILLER                              PIC X(10) VALUE
        //      " 印表日： ".
        //  03  WK-YYY-R                            PIC 999.
        //  03  FILLER                              PIC X(01) VALUE "/".
        //  03  WK-MM-R                             PIC 99.
        //  03  FILLER                              PIC X(01) VALUE "/".
        //  03  WK-DD-R                             PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 綜所稅退稅憑單報表－依主辦行統計兌付 ", 40));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 印表日： ", 10));
        sb.append(formatUtil.pad9(wkTbsDyYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(wkTbsDyMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(wkTbsDyDD, 2));
        fileC068Contents.add(sb.toString());
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC068Contents.add("");
        //    WRITE    REPORT-LINE           FROM      RPT064-TIT2.
        // 01   RPT064-TIT2.
        //  03  FILLER                              PIC X(02) VALUE SPACES.
        //  03  FILLER                              PIC X(12) VALUE
        //      " 代付月份： ".
        //  03  WK-YEAR-P                        PIC 999.
        //  03  FILLER                           PIC X(04) VALUE " 年 ".
        //  03  WK-MONTH-P                       PIC 99.
        //  03  FILLER                           PIC X(04) VALUE " 月 ".
        //  03  FILLER                           PIC X(31) VALUE SPACES.
        //  03  FILLER                           PIC X(10) VALUE
        //      " 頁　次： ".
        //  03  WK-PAGE-R                           PIC Z9    VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 代付月份： ", 12));
        sb.append(formatUtil.pad9(wkTbsDyYY, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.pad9(wkTbsDyMM, 2));
        sb.append(formatUtil.padX(" 月 ", 4));
        sb.append(formatUtil.padX(" ", 31));
        sb.append(formatUtil.padX(" 頁　次： ", 10));
        sb.append(formatUtil.padX(" 1", 2));
        fileC068Contents.add(sb.toString());
        //    MOVE     SPACES                  TO      REPORT-LINE.
        // 01   RPT064-TIT3.
        //  03  FILLER                              PIC X(19) VALUE SPACES.
        //  03  FILLER                              PIC X(39) VALUE SPACES.
        //  03  FILLER                              PIC X(12) VALUE
        //      " 報表名稱： ".
        //  03  WK-FORM-R                           PIC X(04) VALUE "C068".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 19));
        sb.append(formatUtil.padX(" ", 39));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX("C068", 4));
        fileC068Contents.add(sb.toString());
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC068Contents.add("");
        //    MOVE     SPACES                  TO      REPORT-LINE.
        // 01   RPT064-TIT4.
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  FILLER                              PIC X(12) VALUE
        //      " 縣市主辦行 ".
        //  03  FILLER                              PIC X(08) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 總金額 ".
        //  03  FILLER                              PIC X(05) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 總件數 ".
        //  03  FILLER                              PIC X(14) VALUE SPACES.
        //  03  FILLER                              PIC X(06) VALUE
        //      " 備註 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 縣市主辦行 ", 12));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX(" ", 14));
        sb.append(formatUtil.padX(" 備註 ", 6));
        fileC068Contents.add(sb.toString());
        //   MOVE     SPACES                  TO      REPORT-LINE.
        //   WRITE    REPORT-LINE           FROM      RPT064-GATE-LINE.
        // 01   RPT064-GATE-LINE.
        //  03  FILLER                              PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC068Contents.add(sb.toString());
    }

    private void rptDtlRtn(String detail, BigDecimal sdAmt) { // 明細內容
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC068File rptDtlRtn ....");

        // RPT-DTL-RTN.
        //       MOVE    SPACES            TO      REPORT-LINE.
        //       MOVE    WK-PBRNO          TO      RPT064-PBRNO-R.
        //       MOVE    WK-SUBCNT         TO      RPT064-CNT-R.
        //       MOVE    WK-SUBAMT         TO      RPT064-AMT-R.
        //       WRITE   REPORT-LINE       FROM    RPT064-DTL.
        // 01   RPT064-DTL.
        //  03  FILLER                              PIC X(04) VALUE SPACES.
        //  03  RPT064-PBRNO-R                      PIC 9(03)  .
        //  03  FILLER                              PIC X(09) VALUE SPACES.
        //  03  RPT064-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  RPT064-CNT-R                        PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.pad9("" + wkPbrno, 3));
        sb.append(formatUtil.padX(" ", 9));
        sb.append(String.format("%14s", dAmtFormat.format(wkSubAmt)));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%10s", cntFormat.format(wkSubCnt)));
        fileC068Contents.add(sb.toString());
        //       MOVE    0                 TO      WK-SUBCNT,WK-SUBAMT.
        //       MOVE    SD-DATE           TO      WK-DATE.
        //       MOVE    SD-PBRNO          TO      RPT064-PBRNO-R.
        //       ADD     SD-AMT            TO      WK-SUBAMT,
        //                                         WK-TOTAMT.
        //       ADD     1                 TO      WK-SUBCNT,
        //                                         WK-TOTCNT.
        wkSubCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkDate = detail.substring(32, 39);
        wkRptPbrno = parse.string2Integer(detail.substring(20, 23));
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;
    }

    // LAST-RECODE-RTN
    private void lastRecodeRtn() { // 頁尾
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC068File lastRecodeRtn ....");
        // 021000 LAST-RECODE-RTN.
        // 021100    MOVE    SPACES            TO      REPORT-LINE.
        sb = new StringBuilder();
        // 021200    MOVE    WK-SUBCNT         TO      RPT064-CNT-R.
        // 021300    MOVE    WK-SUBAMT         TO      RPT064-AMT-R.
        // 021320* 無筆數印 0 筆
        // 021350    IF      WK-TOTCNT > 0
        if (wkTotCnt > 0) {
            // 021400      WRITE   REPORT-LINE       FROM    RPT064-DTL
            // 01   RPT064-DTL.
            //  03  FILLER                              PIC X(04) VALUE SPACES.
            //  03  RPT064-PBRNO-R                      PIC 9(03)  .
            //  03  FILLER                              PIC X(09) VALUE SPACES.
            //  03  RPT064-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
            //  03  FILLER                              PIC X(03) VALUE SPACES.
            //  03  RPT064-CNT-R                        PIC ZZ,ZZZ,ZZ9.
            sb.append(formatUtil.padX(" ", 4));
            sb.append(formatUtil.pad9("" + wkPbrno, 3));
            sb.append(formatUtil.padX(" ", 9));
            sb.append(String.format("%14s", dAmtFormat.format(wkSubAmt)));
            sb.append(formatUtil.padX(" ", 3));
            sb.append(String.format("%10s", cntFormat.format(wkSubCnt)));
            fileC068Contents.add(sb.toString());
        } else {
            // 021420    ELSE
            // 021440      PERFORM RPT064-WTIT-RTN    THRU RPT064-WTIT-EXIT.
            rpt064WtitRtn();
        }
        // 021500    MOVE    WK-TOTCNT         TO      RPT064-TOTCNT-R.
        // 021600    MOVE    WK-TOTAMT         TO      RPT064-TOTAMT-R.
        // 021700    MOVE    SPACE             TO      REPORT-LINE.
        // 021750    PERFORM RPT064-WTOT-RTN   THRU    RPT064-WTOT-EXIT.
        rpt064WtotRtn();
        // 021800 LAST-RECODE-EXIT.
    }

    private void rpt064WtotRtn() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC068File rpt064WtotRtn ....");
        // 030800 RPT064-WTOT-RTN.
        // 030900    MOVE     SPACES                  TO      REPORT-LINE.
        // 031100    WRITE    REPORT-LINE           FROM      RPT064-GATE-LINE.
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC068Contents.add(sb.toString());

        // 031200    WRITE    REPORT-LINE           FROM      RPT064-TOT.
        // 01   RPT064-TOT.
        //  03  FILLER                              PIC X(05) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 總　計 ".
        //  03  RPT064-TOTAMT-R                  PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        //  03  FILLER                           PIC X(03) VALUE SPACES.
        //  03  RPT064-TOTCNT-R                  PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 總　計 ", 8));
        sb.append(String.format("%17s", tAmtFormat.format(wkTotAmt)));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%10s", cntFormat.format(wkTotCnt)));
        fileC068Contents.add(sb.toString());

        // 031300    MOVE     SPACES                  TO      REPORT-LINE.
        // 031400    WRITE    REPORT-LINE          AFTER      1.
        fileC068Contents.add("");

        // 031500 RPT064-WTOT-EXIT.
    }

    private void toWriteC068BlankFile() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC068File toWriteC068BlankFile ....");

        lastRecodeRtn();
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }
}
