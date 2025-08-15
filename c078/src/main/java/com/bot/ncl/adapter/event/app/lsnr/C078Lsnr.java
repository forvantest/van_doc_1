/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.C078;
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
@Component("C078Lsnr")
@Scope("prototype")
public class C078Lsnr extends BatchListenerCase<C078> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String CHARSET_BIG5 = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_TMP_NAME = "TMPC078"; // tmp檔名
    private static final String FILE_NAME = "CL-BH-C078"; // 檔名
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String sortTmpFilePath; // SortTmp檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> sortTmpFileContents; // Tmp檔案內容
    private String outputFilePath; // 產檔路徑
    private List<String> fileC078Contents; // 檔案內容
    private Boolean isBlankFile = false; // 印出空白資料檔案
    private int wkStaSubCnt;
    private BigDecimal wkStaSubAmt;
    private int wkSubCnt;
    private BigDecimal wkSubAmt;
    private int wkTotCnt;
    private BigDecimal wkTotAmt;
    private int wkPbrno;
    private String wkPbrName;
    private int wkStano;
    private String wkStaName;
    private int wkTbsDy;
    private String wkTbsDyYY;
    private String wkTbsDyMM;
    private String wkTbsDyDD;
    private DecimalFormat tAmtFormat = new DecimalFormat("#,###,###,###,##0"); // 總計金額格式
    private DecimalFormat dAmtFormat = new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C078 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C078Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C078 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C078Lsnr run()");
        init(event);
        sortFileContent();
        if (isBlankFile) {
            toWriteC078BlankFile();
        } else {
            toWriteC078File();
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC078Contents, CHARSET_BIG5);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    // 初始化INITIAL-RTN.
    private void init(C078 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C078Lsnr init ....");
        //    MOVE              0     TO     WK-SUBCNT.
        //    MOVE              0     TO     WK-SUBAMT.
        //    MOVE              0     TO     WK-TOTCNT.
        //    MOVE              0     TO     WK-TOTAMT.
        //    MOVE              0     TO     WK-PBRNO.
        wkStaSubCnt = 0;
        wkStaSubAmt = BigDecimal.ZERO;
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
        fileC078Contents = new ArrayList<>();
        //      MOVE  FD-BHDATE-TBSDY  TO   WK-TEMP-DATE.
        //      MOVE  WK-TEMP-DATE     TO   WK-DATE-R.
        wkTbsDy = event.getAggregateBuffer().getTxCom().getTbsdy();
        wkTbsDyYY = String.format("%07d", wkTbsDy).substring(0, 3);
        wkTbsDyMM = String.format("%07d", wkTbsDy).substring(3, 5);
        wkTbsDyDD = String.format("%07d", wkTbsDy).substring(5, 7);
    }

    private void sortFileContent() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC078File sortFileContent ....");

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            //     IF  KPUTH-CODE NOT = "121454"
            //     GO  TO  SORT-IN-RTN .
            if (!"121444".equals(detail.substring(10, 16))) {
                continue;
            }
            cnt++;
            sortTmpFileContents.add(detail);
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
        //     SORT      SORTFL  ON  ASCENDING   KEY   SD-PBRNO,
        //                          SD-STANO, SD-RCPTID,SD-DATE
        keyRanges.add(new KeyRange(21, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(24, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(17, 16, SortBy.ASC));
        keyRanges.add(new KeyRange(33, 7, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges, CHARSET);
    }

    private void toWriteC078File() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC078File toWriteC078File ....");

        // SORT-OUT-RTN
        List<String> lines = textFile.readFileContent(sortTmpFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            int sdPbrno = parse.string2Integer(detail.substring(20, 23));
            int sdStano = parse.string2Integer(detail.substring(23, 25));
            BigDecimal sdAmt = parse.string2BigDecimal(detail.substring(54, 64));
            // IF WK-PBRNO                  =    0
            if (wkPbrno == 0) {
                //       MOVE    SD-PBRNO             TO   WK-PBRNO,RPT064-PBRNO-R
                //       MOVE    SD-PBRNAME           TO   WK-PBRNAME,
                //                                         RPT064-PBRNAME-R
                //       MOVE    SD-STANO             TO   WK-STANO
                //       MOVE    SD-STANAME           TO   WK-STANAME
                //       MOVE    SD-DATE              TO   WK-DATE
                //
                //       ADD     SD-AMT               TO   WK-STA-SUBAMT,WK-SUBAMT,
                //                                         WK-TOTAMT
                //       ADD     1                    TO   WK-STA-SUBCNT,WK-SUBCNT,
                //                                         WK-TOTCNT
                wkPbrno = parse.string2Integer(detail.substring(20, 23));
                wkPbrName = getPbrName(wkPbrno);
                wkStano = parse.string2Integer(detail.substring(23, 25));
                wkStaName = getStaName(wkStano);
                wkStaSubAmt = wkStaSubAmt.add(sdAmt);
                wkSubAmt = wkSubAmt.add(sdAmt);
                wkTotAmt = wkTotAmt.add(sdAmt);
                wkStaSubCnt = wkStaSubCnt + 1;
                wkSubCnt = wkSubCnt + 1;
                wkTotCnt = wkTotCnt + 1;
                //       PERFORM RPT064-WTIT-RTN    THRU RPT064-WTIT-EXIT
                toC078FileHeader();
                if (cnt != lines.size()) {
                    continue;
                } else {
                    rptStaDtl(BigDecimal.ZERO);
                    sortOutLast();
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "toWriteC078File FILE ....");
                    return;
                }
            }
            // *  付款行相同繼續作加總
            //     IF        SD-PBRNO       =    WK-PBRNO
            if (sdPbrno == wkPbrno) {
                //       PERFORM DTL-SUM-RTN        THRU DTL-SUM-EXIT
                dtlSum(sdAmt);

            }
            //     ELSE
            // * 不同主辦行，則列印上述的加總後，再繼續統計下個主辦行
            else {
                //       PERFORM RPT-SUB-RTN        THRU RPT-SUB-EXIT
                rptSub(sdPbrno, sdStano, sdAmt);
                //       MOVE    SD-PBRNO           TO   WK-PBRNO
                //       MOVE    SD-PBRNAME         TO   WK-PBRNAME
                wkPbrno = sdPbrno;
                wkPbrName = getPbrName(wkPbrno);
                //                wkStano = parse.string2Integer(detail.substring(23, 25));
                //                wkStaName = getStaName(wkStano);
                //       GO  TO  SORT-OUT-RTN.
                if (cnt != lines.size()) {
                    continue;
                } else {
                    lastRecode();
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "toWriteC078File FILE ....");
                    return;
                }
            }
            // * 站所別相同繼續作加總
            //     IF        SD-STANO           =    WK-STANO
            if (sdStano == wkStano) {
                //       PERFORM DTL-STASUM-RTN        THRU DTL-STASUM-EXIT
                dtlStaSum(sdAmt);
            }
            //     ELSE
            // * 站所別不同，則列印上述加總後，再繼續統計下個站所別
            else {
                //       PERFORM RPT-STADTL-RTN        THRU RPT-STADTL-EXIT
                rptStaDtl(sdAmt);
                //       MOVE    SD-STANO           TO   WK-STANO
                //       MOVE    SD-STANAME         TO   WK-STANAME.
                wkStano = sdStano;
                wkStaName = getStaName(sdStano);
            }

            //     RETURN    SORTFL  AT  END
            if (cnt == lines.size()) {
                //       PERFORM RPT-STADTL-RTN  THRU RPT-STADTL-EXIT
                rptStaDtl(BigDecimal.ZERO);
                //       GO  TO  SORT-OUT-LAST.
                sortOutLast();
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC078File FILE ....");
        }
    }

    // DTL-STASUM-RTN
    private void dtlStaSum(BigDecimal sdAmt) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dtlStaSum ....");
        // DTL-STASUM-RTN.
        //    ADD       1             TO     WK-STA-SUBCNT.
        //    ADD       SD-AMT        TO     WK-STA-SUBAMT.
        wkStaSubCnt = wkStaSubCnt + 1;
        wkStaSubAmt = wkStaSubAmt.add(sdAmt);

        // DTL-STASUM-EXIT.
    }

    private void dtlSum(BigDecimal sdAmt) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC078File dtlSum ....");
        // DTL-SUM-RTN.
        //    ADD               1     TO     WK-SUBCNT,
        //                                   WK-TOTCNT.
        //    ADD       SD-AMT        TO     WK-SUBAMT,
        //                                   WK-TOTAMT.
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);
    }

    // RPT-STADTL-RTN.
    private void rptStaDtl(BigDecimal sdAmt) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC078File rptStaDtl ....");
        // RPT-STADTL-RTN.
        //       MOVE    SPACES            TO      REPORT-LINE.
        //       MOVE    WK-STANO          TO      RPT064-STANO-R.
        //       MOVE    WK-STANAME        TO      RPT064-STANAME-R.
        //       MOVE    WK-STA-SUBCNT     TO      RPT064-CNT-R.
        //       MOVE    WK-STA-SUBAMT     TO      RPT064-AMT-R.
        //       WRITE   REPORT-LINE       FROM    RPT064-DTL.
        // 01   RPT064-DTL.
        //  03  FILLER                              PIC X(06) VALUE SPACES.
        //  03  RPT064-STANO-R                      PIC X(02).
        //  03  FILLER                              PIC X(06) VALUE SPACES.
        //  03  RPT064-STANAME-R                    PIC X(20).
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  RPT064-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  RPT064-CNT-R                        PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.pad9("" + wkStano, 2));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(wkStaName, 20));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%14s", dAmtFormat.format(wkStaSubAmt)));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%10s", cntFormat.format(wkStaSubCnt)));
        fileC078Contents.add(sb.toString());
        //       MOVE    0                 TO      WK-STA-SUBCNT,
        //                                         WK-STA-SUBAMT.
        //       ADD     SD-AMT            TO      WK-STA-SUBAMT.
        //       ADD     1                 TO      WK-STA-SUBCNT.
        //       MOVE    SD-DATE           TO      WK-DATE.
        wkStaSubCnt = 0;
        wkStaSubAmt = BigDecimal.ZERO;
        wkStaSubAmt = wkStaSubAmt.add(sdAmt);
        wkStaSubCnt = wkStaSubCnt + 1;
    }

    // RPT-SUB-RTN
    private void rptSub(int sdPbrno, int sdStano, BigDecimal sdAmt) { // 明細內容
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC078File toC078FileDetail ....");
        // RPT-SUB-RTN.

        //       IF WK-STANO NOT = SD-STANO
        if (wkStano != sdStano) {
            //          PERFORM RPT-STADTL-RTN THRU RPT-STADTL-EXIT
            rptStaDtl(sdAmt);
            //          MOVE SD-STANO TO WK-STANO
            //          MOVE SD-STANAME TO WK-STANAME.
            wkStano = sdStano;
            wkStaName = getStaName(sdStano);
        }
        //       WRITE   REPORT-LINE       FROM    RPT064-GATE-LINE.
        // 01   RPT064-GATE-LINE.
        //  03  FILLER                              PIC X(90) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "------------------------------------------------------------------------------------------",
                        90));
        fileC078Contents.add(sb.toString());
        //       MOVE    WK-PBRNO          TO      RPT064-PBRNO-R.
        //       MOVE    WK-SUBCNT         TO      RPT064-SUBCNT-R.
        //       MOVE    WK-SUBAMT         TO      RPT064-SUBAMT-R.
        //       WRITE   REPORT-LINE       FROM    RPT064-SUBTOT.

        // 01   RPT064-SUBTOT.
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  FILLER                              PIC X(14) VALUE
        //      " 付款行小計： ".
        //  03  FILLER                              PIC X(17) VALUE SPACES.
        //  03  RPT064-SUBAMT-R                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  RPT064-SUBCNT-R                     PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 付款行小計： ", 14));
        sb.append(formatUtil.padX(" ", 17));
        sb.append(String.format("%17s", tAmtFormat.format(wkSubAmt)));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%10s", cntFormat.format(wkSubCnt)));
        fileC078Contents.add(sb.toString());
        //       MOVE    0                 TO      WK-SUBCNT.
        //       MOVE    0                 TO      WK-SUBAMT.
        //       MOVE    SD-DATE           TO      WK-DATE.
        //       MOVE    SD-PBRNO          TO      RPT064-PBRNO-R.
        //       MOVE    SD-PBRNAME        TO      RPT064-PBRNAME-R.
        wkSubCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkPbrno = sdPbrno;
        wkPbrName = getPbrName(sdPbrno);
        //       MOVE    SPACES            TO      REPORT-LINE.
        //       WRITE   REPORT-LINE       AFTER   2.
        fileC078Contents.add("");
        fileC078Contents.add("");

        //       WRITE   REPORT-LINE       FROM    RPT064-TIT4.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 付款分行： ", 12));
        sb.append(formatUtil.pad9("" + wkPbrno, 3));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(wkPbrName, 10));
        fileC078Contents.add(sb.toString());

        //       WRITE   REPORT-LINE       FROM    RPT064-TIT5.
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "==========================================================================================",
                        90));
        fileC078Contents.add(sb.toString());

        //       WRITE   REPORT-LINE       FROM    RPT064-TIT6.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 站所別 ", 8));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 站所名稱 ", 10));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX(" ", 9));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 備註 ", 6));
        fileC078Contents.add(sb.toString());

        //       WRITE   REPORT-LINE       FROM    RPT064-GATE-LINE.
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "------------------------------------------------------------------------------------------",
                        90));
        fileC078Contents.add(sb.toString());

        //       ADD     SD-AMT            TO      WK-SUBAMT,
        //                                         WK-TOTAMT.
        //       ADD     1                 TO      WK-SUBCNT,
        //                                         WK-TOTCNT.
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;
        // RPT-SUB-EXIT.

    }

    private void toC078FileHeader() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC078File toC078FileHeader ....");
        //    MOVE     WK-YEAR                 TO      WK-YEAR-P.
        //    MOVE     WK-MONTH                TO      WK-MONTH-P.
        //    MOVE     WK-YEAR                 TO      WK-YYY-R.
        //    MOVE     WK-MONTH                TO      WK-MM-R.
        //    MOVE     WK-DD                   TO      WK-DD-R.
        //    init裡做
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC078Contents.add("");
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC078Contents.add("");
        //    WRITE    REPORT-LINE           FROM      RPT064-TIT1.
        // 01   RPT064-TIT1.
        //  03  FILLER                              PIC X(12) VALUE SPACES.
        //  03  FILLER                              PIC X(40) VALUE
        //      " 汽燃費退費憑單報表－依主辦行統計兌付 ".
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
        sb.append(formatUtil.padX(" 汽燃費退費憑單報表－依主辦行統計兌付 ", 40));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 印表日： ", 10));
        sb.append(formatUtil.pad9(wkTbsDyYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(wkTbsDyMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(wkTbsDyDD, 2));
        fileC078Contents.add(sb.toString());
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC078Contents.add("");
        //    WRITE    REPORT-LINE           FROM      RPT064-TIT2.
        // 01   RPT064-TIT2.
        //  03  FILLER                              PIC X(02) VALUE SPACES.
        //  03  FILLER                           PIC X(12) VALUE
        //      " 代付月份： ".
        //  03  WK-YEAR-P                        PIC 999.
        //  03  FILLER                           PIC X(04) VALUE " 年 ".
        //  03  WK-MONTH-P                       PIC 99.
        //  03  FILLER                           PIC X(04) VALUE " 月 ".
        //  03  FILLER                              PIC X(31) VALUE SPACES.
        //  03  FILLER                              PIC X(10) VALUE
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
        fileC078Contents.add(sb.toString());
        //    WRITE    REPORT-LINE           FROM      RPT064-TIT3.
        // 01   RPT064-TIT3.
        //  03  FILLER                              PIC X(19) VALUE SPACES.
        //  03  FILLER                              PIC X(39) VALUE SPACES.
        //  03  FILLER                              PIC X(12) VALUE
        //      " 報表名稱： ".
        //  03  WK-FORM-R                           PIC X(04) VALUE "C078".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 19));
        sb.append(formatUtil.padX(" ", 39));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX("C078", 4));
        fileC078Contents.add(sb.toString());
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC078Contents.add("");
        // 010800 01   RPT064-TIT4.
        // 010900  03  FILLER                              PIC X(12) VALUE
        // 011000      " 付款分行： ".
        // 011100  03  RPT064-PBRNO-R                      PIC X(03).
        // 011200  03  FILLER                              PIC X(02) VALUE SPACES.
        // 011300  03  RPT064-PBRNAME-R                    PIC X(10).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 付款分行： ", 12));
        sb.append(formatUtil.pad9("" + wkPbrno, 3));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(wkPbrName, 10));
        fileC078Contents.add(sb.toString());
        // 01   RPT064-TIT5.
        //  03  FILLER                              PIC X(90) VALUE ALL "=".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "==========================================================================================",
                        90));
        fileC078Contents.add(sb.toString());
        // 01   RPT064-TIT6 .
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 站所別 ".
        //  03  FILLER                              PIC X(03) VALUE SPACES.
        //  03  FILLER                              PIC X(10) VALUE
        //      " 站所名稱 ".
        //  03  FILLER                              PIC X(12) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 總金額 ".
        //  03  FILLER                              PIC X(09) VALUE SPACES.
        //  03  FILLER                              PIC X(08) VALUE
        //      " 總件數 ".
        //  03  FILLER                              PIC X(06) VALUE SPACES.
        //  03  FILLER                              PIC X(06) VALUE
        //      " 備註 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 站所別 ", 8));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 站所名稱 ", 10));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX(" ", 9));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 備註 ", 6));
        fileC078Contents.add(sb.toString());
        //   MOVE     SPACES                  TO      REPORT-LINE.
        // 01   RPT064-GATE-LINE.
        //  03  FILLER                              PIC X(90) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "------------------------------------------------------------------------------------------",
                        90));
        fileC078Contents.add(sb.toString());
    }

    // SORT-OUT-LAST
    private void sortOutLast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC078File sortOutLast ....");
        //       WRITE   REPORT-LINE       FROM    RPT064-GATE-LINE
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "------------------------------------------------------------------------------------------",
                        90));
        fileC078Contents.add(sb.toString());

        //       MOVE    WK-SUBAMT         TO      RPT064-SUBAMT-R
        //       MOVE    WK-SUBCNT         TO      RPT064-SUBCNT-R
        //       WRITE   REPORT-LINE       FROM    RPT064-SUBTOT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 付款行小計： ", 14));
        sb.append(formatUtil.padX(" ", 17));
        sb.append(String.format("%17s", tAmtFormat.format(wkSubAmt)));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(String.format("%10s", cntFormat.format(wkSubCnt)));
        fileC078Contents.add(sb.toString());
        // * 最後還要印最後分行之總筆數金額
        //       PERFORM LAST-RECODE-RTN      THRU LAST-RECODE-EXIT.
        lastRecode();
    }

    // LAST-RECODE-RTN
    private void lastRecode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lastRecode ....");
        // 027200 LAST-RECODE-RTN.
        // 027300    MOVE    SPACES            TO      REPORT-LINE.
        // 027400    MOVE    WK-SUBCNT         TO      RPT064-CNT-R.
        // 027500    MOVE    WK-SUBAMT         TO      RPT064-AMT-R.

        // 027600* 無筆數印 0 筆
        // 027700    IF      WK-TOTCNT = 0
        if (wkTotCnt == 0) {
            // 028000      PERFORM RPT064-WTIT-RTN    THRU RPT064-WTIT-EXIT.
            toC078FileHeader();
        }
        //    MOVE    WK-TOTCNT         TO      RPT064-TOTCNT-R.
        //    MOVE    WK-TOTAMT         TO      RPT064-TOTAMT-R.
        //    MOVE    SPACE             TO      REPORT-LINE.
        //    PERFORM RPT064-WTOT-RTN   THRU    RPT064-WTOT-EXIT.
        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      2.
        fileC078Contents.add("");
        fileC078Contents.add("");

        //    WRITE    REPORT-LINE           FROM      RPT064-TOT.
        // 01   RPT064-TOT.
        //  03  FILLER                              PIC X(03) VALUE ALL "*".
        //  03  FILLER                              PIC X(14) VALUE
        //      " 全行總金額： ".
        //  03  RPT064-TOTAMT-R                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        //  03  FILLER                              PIC X(06) VALUE SPACES.
        //  03  FILLER                              PIC X(14) VALUE
        //      " 全行總筆數： ".
        //  03  RPT064-TOTCNT-R                     PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("***", 3));
        sb.append(formatUtil.padX(" 全行總金額： ", 14));
        sb.append(String.format("%17s", tAmtFormat.format(wkTotAmt)));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 全行總筆數： ", 14));
        sb.append(String.format("%10s", cntFormat.format(wkTotCnt)));
        fileC078Contents.add(sb.toString());

        //    MOVE     SPACES                  TO      REPORT-LINE.
        //    WRITE    REPORT-LINE          AFTER      1.
        fileC078Contents.add("");

        // LAST-RECODE-EXIT.
    }

    private void toWriteC078BlankFile() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "toWriteC078File toWriteC078BlankFile ....");
        lastRecode();
    }

    private String getStaName(int stano) {
        String staName = "";
        if (stano == 40) {
            //    IF      WK-STANO = 40
            //      MOVE " 臺北區監理所 " TO SD-STANAME
            staName = " 臺北區監理所 ";
        } else if (stano == 41) {
            //    ELSE IF WK-STANO = 41
            //      MOVE " 板橋監理站 "   TO SD-STANAME
            staName = " 板橋監理站 ";
        } else if (stano == 46) {
            //    ELSE IF WK-STANO = 46
            //      MOVE " 蘆州監理站 "   TO SD-STANAME
            staName = " 蘆州監理站 ";
        } else if (stano == 42) {
            //    ELSE IF WK-STANO = 42 OR = 33
            //      MOVE " 基隆監理站 "   TO SD-STANAME
            staName = " 基隆監理站 ";
        } else if (stano == 43) {
            //    ELSE IF WK-STANO = 43
            //      MOVE " 宜蘭監理站 "   TO SD-STANAME
            staName = " 宜蘭監理站 ";
        } else if (stano == 44) {
            //    ELSE IF WK-STANO = 44
            //      MOVE " 花蓮監理站 "   TO SD-STANAME
            staName = " 花蓮監理站 ";
        } else if (stano == 45) {
            //    ELSE IF WK-STANO = 45
            //      MOVE " 玉里監理分站 " TO SD-STANAME
            staName = " 玉里監理分站 ";
        } else if (stano == 50) {
            //    ELSE IF WK-STANO = 50
            //      MOVE " 新竹區監理所 " TO SD-STANAME
            staName = " 新竹區監理所 ";
        } else if (stano == 51) {
            //    ELSE IF WK-STANO = 51
            //      MOVE " 新竹市監理站 " TO SD-STANAME
            staName = " 新竹市監理站 ";
        } else if (stano == 52) {
            //    ELSE IF WK-STANO = 52
            //      MOVE " 桃園監理站 "   TO SD-STANAME
            staName = " 桃園監理站 ";
        } else if (stano == 53) {
            //    ELSE IF WK-STANO = 53
            //      MOVE " 中壢監理站 "   TO SD-STANAME
            staName = " 中壢監理站 ";
        } else if (stano == 54) {
            //    ELSE IF WK-STANO = 54
            //      MOVE " 苗栗監理站 "   TO SD-STANAME
            staName = " 苗栗監理站 ";
        } else if (stano == 60) {
            //    ELSE IF WK-STANO = 60
            //      MOVE " 臺中區監理所 " TO SD-STANAME
            staName = " 臺中區監理所 ";
        } else if (stano == 61) {
            //    ELSE IF WK-STANO = 61
            //      MOVE " 臺中市監理站 " TO SD-STANAME
            staName = " 臺中市監理站 ";
        } else if (stano == 62) {
            //    ELSE IF WK-STANO = 62
            //      MOVE " 埔里監理分站 " TO SD-STANAME
            staName = " 埔里監理分站 ";
        } else if (stano == 63) {
            //    ELSE IF WK-STANO = 63
            //      MOVE " 豐原監理站 "   TO SD-STANAME
            staName = " 豐原監理站 ";
        } else if (stano == 64) {
            //    ELSE IF WK-STANO = 64
            //      MOVE " 彰化監理站 "   TO SD-STANAME
            staName = " 彰化監理站 ";
        } else if (stano == 66) {
            //    ELSE IF WK-STANO = 65 OR = 66
            //      MOVE " 南投監理站 "   TO SD-STANAME
            staName = " 南投監理站 ";
        } else if (stano == 70) {
            //    ELSE IF WK-STANO = 70
            //      MOVE " 嘉義區監理所 " TO SD-STANAME
            staName = " 嘉義區監理所 ";
        } else if (stano == 76) {
            //    ELSE IF WK-STANO = 76
            //      MOVE " 嘉義市監理站 " TO SD-STANAME
            staName = " 嘉義市監理站 ";
        } else if (stano == 73) {
            //    ELSE IF WK-STANO = 73
            //      MOVE " 新營監理站 "   TO SD-STANAME
            staName = " 新營監理站 ";
        } else if (stano == 75) {
            //    ELSE IF WK-STANO = 75
            //      MOVE " 麻豆監理站 "   TO SD-STANAME
            staName = " 麻豆監理站 ";
        } else if (stano == 74) {
            //    ELSE IF WK-STANO = 74
            //      MOVE " 臺南監理站 "   TO SD-STANAME
            staName = " 臺南監理站 ";
        } else if (stano == 72) {
            //    ELSE IF WK-STANO = 72
            //      MOVE " 雲林監理站 "   TO SD-STANAME
            staName = " 雲林監理站 ";
        } else if (stano == 71) {
            //    ELSE IF WK-STANO = 71
            //      MOVE " 雲林監理站東勢分站 " TO SD-STANAME
            staName = " 雲林監理站東勢分站 ";
        } else if (stano == 80) {
            //    ELSE IF WK-STANO = 80
            //      MOVE " 高雄區監理所 " TO SD-STANAME
            staName = " 高雄區監理所 ";

        } else if (stano == 82) {

            //    ELSE IF WK-STANO = 82
            //      MOVE " 屏東監理站 "   TO SD-STANAME
            staName = " 屏東監理站 ";
        } else if (stano == 25) {
            //    ELSE IF WK-STANO = 85 OR = 25
            //      MOVE " 旗山監理站 "   TO SD-STANAME
            staName = " 旗山監理站 ";
        } else if (stano == 81) {
            //    ELSE IF WK-STANO = 81
            //      MOVE " 臺東監理站 "   TO SD-STANAME
            staName = " 臺東監理站 ";
        } else if (stano == 83) {
            //    ELSE IF WK-STANO = 83
            //      MOVE " 恆春監理分站 " TO SD-STANAME
            staName = " 恆春監理分站 ";
        } else if (stano == 84) {
            //    ELSE IF WK-STANO = 84
            //      MOVE " 澎湖監理站 "   TO SD-STANAME
            staName = " 澎湖監理站 ";
        } else {
            //    ELSE
            //      MOVE  SPACES          TO SD-STANAME.
            staName = " ";
        }
        return staName;
    }

    private String getPbrName(int pbrno) {
        String pbrName = "";
        if (pbrno == 5) {
            //    IF      WK-PBRNO-1 = 5
            //      MOVE " 公庫部 "     TO SD-PBRNAME
            pbrName = " 公庫部 ";
        } else if (pbrno == 9) {
            //    ELSE IF WK-PBRNO-1 = 9
            //      MOVE " 臺南分行 "   TO SD-PBRNAME
            pbrName = " 臺南分行 ";
        } else if (pbrno == 10) {
            //    ELSE IF WK-PBRNO-1 = 10
            //      MOVE " 臺中分行 "   TO SD-PBRNAME
            pbrName = " 臺中分行 ";
        } else if (pbrno == 11) {
            //    ELSE IF WK-PBRNO-1 = 11
            //      MOVE " 高雄分行 "   TO SD-PBRNAME
            pbrName = " 高雄分行 ";
        } else if (pbrno == 12) {
            //    ELSE IF WK-PBRNO-1 = 12
            //      MOVE " 基隆分行 "   TO SD-PBRNAME
            pbrName = " 基隆分行 ";
        } else if (pbrno == 14) {
            //    ELSE IF WK-PBRNO-1 = 14
            //      MOVE " 嘉義分行 "   TO SD-PBRNAME
            pbrName = " 嘉義分行 ";
        } else if (pbrno == 15) {
            //    ELSE IF WK-PBRNO-1 = 15
            //      MOVE " 新竹分行 "   TO SD-PBRNAME
            pbrName = " 新竹分行 ";
        } else if (pbrno == 16) {
            //    ELSE IF WK-PBRNO-1 = 16
            //      MOVE " 彰化分行 "   TO SD-PBRNAME
            pbrName = " 彰化分行 ";
        } else if (pbrno == 17) {
            //    ELSE IF WK-PBRNO-1 = 17
            //      MOVE " 屏東分行 "   TO SD-PBRNAME
            pbrName = " 屏東分行 ";
        } else if (pbrno == 18) {
            //    ELSE IF WK-PBRNO-1 = 18
            //      MOVE " 花蓮分行 "   TO SD-PBRNAME
            pbrName = " 花蓮分行 ";
        } else if (pbrno == 22) {
            //    ELSE IF WK-PBRNO-1 = 22
            //      MOVE " 宜蘭分行 "   TO SD-PBRNAME
            pbrName = " 宜蘭分行 ";
        } else if (pbrno == 23) {
            //    ELSE IF WK-PBRNO-1 = 23
            //      MOVE " 臺東分行 "   TO SD-PBRNAME
            pbrName = " 臺東分行 ";
        } else if (pbrno == 24) {
            //    ELSE IF WK-PBRNO-1 = 24
            //      MOVE " 澎湖分行 "   TO SD-PBRNAME
            pbrName = " 澎湖分行 ";
        } else if (pbrno == 25) {
            //    ELSE IF WK-PBRNO-1 = 25
            //      MOVE " 鳳山分行 "   TO SD-PBRNAME
            pbrName = " 鳳山分行 ";
        } else if (pbrno == 26) {
            //    ELSE IF WK-PBRNO-1 = 26
            //      MOVE " 桃園分行 "   TO SD-PBRNAME
            pbrName = " 桃園分行 ";
        } else if (pbrno == 27) {
            //    ELSE IF WK-PBRNO-1 = 27
            //      MOVE " 板橋分行 "   TO SD-PBRNAME
            pbrName = " 板橋分行 ";
        } else if (pbrno == 28) {
            //    ELSE IF WK-PBRNO-1 = 28
            //      MOVE " 新營分行 "   TO SD-PBRNAME
            pbrName = " 新營分行 ";
        } else if (pbrno == 29) {
            //    ELSE IF WK-PBRNO-1 = 29
            //      MOVE " 苗栗分行 "   TO SD-PBRNAME
            pbrName = " 苗栗分行 ";
        } else if (pbrno == 30) {
            //    ELSE IF WK-PBRNO-1 = 30
            //      MOVE " 豐原分行 "   TO SD-PBRNAME
            pbrName = " 豐原分行 ";
        } else if (pbrno == 31) {
            //    ELSE IF WK-PBRNO-1 = 31
            //      MOVE " 斗六分行 "   TO SD-PBRNAME
            pbrName = " 斗六分行 ";
        } else if (pbrno == 32) {
            //    ELSE IF WK-PBRNO-1 = 32
            //      MOVE " 南投分行 "   TO SD-PBRNAME
            pbrName = " 南投分行 ";
        } else if (pbrno == 42) {
            //    ELSE IF WK-PBRNO-1 = 42
            //      MOVE " 三重分行 "   TO SD-PBRNAME
            pbrName = " 三重分行 ";
        } else if (pbrno == 67) {
            //    ELSE IF WK-PBRNO-1 = 67
            //      MOVE " 太保分行 "   TO SD-PBRNAME
            pbrName = " 太保分行 ";
        } else if (pbrno == 68) {
            //    ELSE IF WK-PBRNO-1 = 68
            //      MOVE " 竹北分行 "   TO SD-PBRNAME
            pbrName = " 竹北分行 ";
        } else if (pbrno == 88) {
            //    ELSE IF WK-PBRNO-1 = 88
            //      MOVE " 潮洲分行 "   TO SD-PBRNAME
            pbrName = " 潮洲分行 ";
        } else {
            //    ELSE
            //      MOVE  SPACES        TO SD-PBRNAME.
            pbrName = " ";
        }
        return pbrName;
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }
}
