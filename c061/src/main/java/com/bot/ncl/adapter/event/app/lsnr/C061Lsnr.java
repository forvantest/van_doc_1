/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C061;
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
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C061Lsnr")
@Scope("prototype")
public class C061Lsnr extends BatchListenerCase<C061> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;
    private C061 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;

    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_TMP_NAME = "TMPC061"; // tmp檔名
    private static final String FILE_NAME_1 = "CL-BH-C061"; // 檔名1
    private static final String FILE_NAME_2 = "1"; // 檔名2
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String sortTmpFilePath; // SortTmp檔路徑
    private String outputFilePath1; // 產檔路徑1
    private String outputFilePath2; // 產檔路徑2
    private StringBuilder sb = new StringBuilder();
    private List<String> sortTmpFileContents; // Tmp檔案內容
    private List<String> fileC061Contents; // 檔案內容
    private List<String> fileC061005Contents; // 檔案內容
    private String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";
    private String batchDate = ""; // 批次日期(民國年yyyymmdd)
    private int wkSubCnt;
    private BigDecimal wkSubAmt;
    private int wkTotCnt;
    private BigDecimal wkTotAmt;
    private int wkPbrno;
    private int wkCllbr = 0;
    private String wkCode = "";
    private String wkDate = "";
    private String wkRemark = "";
    private int wk005Cnt = 0;
    private BigDecimal wk6AEAmt = BigDecimal.ZERO;
    private int wk6AECnt = 0;
    private BigDecimal wk6AGAmt = BigDecimal.ZERO;
    private int wk6AGCnt = 0;
    private int wkEnd = 0;
    private int wk005Page = 0;
    private int sdCllbr = 0;
    private String sdCode = "";
    private String sdDate = "";
    private BigDecimal sdAmt = BigDecimal.ZERO;
    private DecimalFormat dAmtFormat = new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    @Override
    public void onApplicationEvent(C061 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C061 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr run()");
        init(event);
        rpt061_005Titl();
        sortFileContent();
        toWriteC061File();
        rpt061_005Sum();
        try {
            textFile.writeFileContent(outputFilePath1, fileC061Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(outputFilePath2, fileC061005Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(sortTmpFilePath);
    }

    private void init(C061 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr init ....");
        this.event = event;
        // 017600 INITIAL-RTN.
        // 017800    MOVE              0     TO     WK-SUBCNT.
        // 017900    MOVE              0     TO     WK-SUBAMT.
        // 018000    MOVE              0     TO     WK-TOTCNT.
        // 018100    MOVE              0     TO     WK-TOTAMT.
        // 018200    MOVE              0     TO     WK-PBRNO.
        wkSubCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkTotCnt = 0;
        wkTotAmt = BigDecimal.ZERO;
        wkPbrno = 0;
        // 018300 INITIAL-EXIT.
        // 抓批次營業日
        // 015500     MOVE  FD-BHDATE-TBSDY  TO   WK-TEMP-DATE.
        // 015600     MOVE  WK-TEMP-DATE     TO   WK-DATE-R.
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        batchDate = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        // 讀檔路徑
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        // 暫存檔路徑
        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        // 產檔路徑1
        outputFilePath1 = fileDir + FILE_NAME_1;
        // 產檔路徑2
        outputFilePath2 = fileDir + FILE_NAME_1 + "-" + FILE_NAME_2;
        // 刪除舊檔
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath1);
        textFile.deleteFile(outputFilePath2);
        sortTmpFileContents = new ArrayList<>();
        fileC061Contents = new ArrayList<>();
        fileC061005Contents = new ArrayList<>();
    }

    private void sortFileContent() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr sortFileContent ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        // 03 KPUTH-PUTFILE	GROUP
        //  05 KPUTH-PUTTYPE	9(02)	媒體種類 0-2
        //  05 KPUTH-PUTNAME	X(08)	媒體檔名 2-10
        // 03 KPUTH-PUTFILE-R1 	REDEFINES KPUTH-PUTFILE
        //  05 KPUTH-ENTPNO	X(10)
        // 03 KPUTH-CODE	X(06)	代收類別 10-16
        // 03 KPUTH-RCPTID	X(16)	銷帳號碼 16-32
        // 03 KPUTH-DATE	9(07)	代收日 32-39
        // 03 KPUTH-TIME	9(06)	代收時間 39-45
        // 03 KPUTH-CLLBR	9(03)	代收行 45-48
        // 03 KPUTH-LMTDATE	9(06)	繳費期限 48-54
        // 03 KPUTH-AMT	9(10)	繳費金額 54-64
        // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
        // 03 KPUTH-USERDATE-R1 	REDEFINES KPUTH-USERDATA
        //  05 KPUTH-SMSERNO	X(03)
        //  05 KPUTH-RETAILNO	X(08)
        //  05 KPUTH-BARCODE3	X(15)
        //  05 KPUTH-FILLER	X(14)
        // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
        // 03 KPUTH-TXTYPE	X(01)	帳務別 111-112
        // 03 KPUTH-SERINO	9(06)	交易明細流水序號 112-118
        // 03 KPUTH-PBRNO	9(03)	主辦分行 118-121
        // 03 KPUTH-UPLDATE	9(07) 121-128
        // 03 KPUTH-FEETYPE	9(01) 128-129
        // 03 KPUTH-FEEO2L	9(05)V99 129-134
        // 03 FILLER	X(02)
        // 03 FILLER	X(02)
        int cnt = 0;
        for (String detail : lines) {
            // 026100* 只挑國稅、地方稅
            // 026200     IF  KPUTH-CODE NOT = "366AE9" AND NOT = "366AG9"
            // 026300     GO  TO  SORT-IN-RTN .

            if (!("366AE9".equals(detail.substring(10, 16))
                    || "366AG9".equals(detail.substring(10, 16)))) {
                continue;
            }
            cnt++;
            sortTmpFileContents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpFileContents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        File sortTmpFile = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        // 016100     SORT      SORTFL  ON  ASCENDING   KEY   SD-CLLBR, SD-CODE,
        // 016200                                    SD-DATE
        keyRanges.add(new KeyRange(46, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(33, 7, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges);
    }

    private void toWriteC061File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr toWriteC061File ....");

        // SORT-OUT-RTN
        List<String> lines = textFile.readFileContent(sortTmpFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            sdCllbr = parse.string2Integer(detail.substring(45, 48));
            sdCode = detail.substring(10, 16);
            sdDate = detail.substring(32, 39);
            sdAmt = parse.string2BigDecimal(detail.substring(54, 64));
            // 028900* 將各欄位之值搬入
            // 029000     IF WK-PBRNO                  =    0
            if (wkPbrno == 0) {
                // 029100       MOVE    SD-CLLBR             TO   WK-CLLBR-R,WK-PBRNO
                // 029200       MOVE    SD-CODE              TO   RPT061-CODE-R,WK-CODE
                // 029300       MOVE    SD-DATE              TO   WK-DATE
                // 029500       ADD     SD-AMT               TO   WK-SUBAMT,
                // 029600                                         WK-TOTAMT
                // 029700       ADD     1                    TO   WK-SUBCNT,
                // 029800                                         WK-TOTCNT
                wkCllbr = sdCllbr;
                wkPbrno = sdCllbr;
                wkCode = sdCode;
                wkDate = sdDate;
                wkSubAmt = wkSubAmt.add(sdAmt);
                wkTotAmt = wkTotAmt.add(sdAmt);
                wkSubCnt = wkSubCnt + 1;
                wkTotCnt = wkTotCnt + 1;
                // 029900       PERFORM RPT061-WTIT-RTN    THRU RPT061-WTIT-EXIT
                rpt061Wtit(PAGE_SEPARATOR);
                // 030000       GO  TO  SORT-OUT-RTN.
                continue;
            }
            // 030100* 相同代收行相同則續作加總
            // 030200     IF        SD-CLLBR           =    WK-PBRNO
            if (sdCllbr == wkPbrno) {
                // 030300       PERFORM DTL-SUM-RTN        THRU DTL-SUM-EXIT
                dtlSum();
            }
            // 030400     ELSE
            // 030500* 不同代收行，則列印上述的加總後，再繼續找統計下個代收行
            else {
                // 030600       PERFORM RPT-DTL-RTN        THRU RPT-DTL-EXIT
                rptDtl();
                // 030700       MOVE    SD-CLLBR           TO   WK-PBRNO.
                wkPbrno = sdCllbr;
            }

            if (cnt == lines.size()) {
                // 028500     RETURN    SORTFL  AT  END
                // 028600       MOVE 1  TO  WK-END
                // 028700       GO  TO  SORT-OUT-LAST.
                wkEnd = 1;
                // 031000 SORT-OUT-LAST.
                // 031100* 最後還要印最後分行之總筆數金額
                // 031200       PERFORM RPT-DTL-RTN        THRU RPT-DTL-EXIT.
                rptDtl();
                // 031300 SORT-OUT-EXIT.
            }
        }
    }

    private void rpt061Wtit(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr rpt061Wtit ....");
        // 031600 RPT061-WTIT-RTN.
        // 031700    MOVE     WK-YEAR                 TO      WK-YEAR-P.
        // 031800    MOVE     WK-MONTH                TO      WK-MONTH-P.
        // 031900    MOVE     WK-YEAR                 TO      WK-YYY-R.
        // 032000    MOVE     WK-MONTH                TO      WK-MM-R.
        // 032100    MOVE     WK-DD                   TO      WK-DD-R.
        String batchDateYY = batchDate.substring(0, 3);
        String batchDateMM = batchDate.substring(3, 5);
        String batchDateDD = batchDate.substring(5, 7);

        // 032200    MOVE     SPACES                  TO      REPORT-LINE.
        // 032300    WRITE    REPORT-LINE          AFTER      1.
        sb = new StringBuilder();
        sb.append(pageFg); // 換頁符號
        fileC061Contents.add(sb.toString());

        // 032400    MOVE     SPACES                  TO      REPORT-LINE.
        // 032500    WRITE    REPORT-LINE          AFTER      1.
        fileC061Contents.add("");

        // 032600    WRITE    REPORT-LINE           FROM      RPT061-TIT1.
        // 007100 01   RPT061-TIT1.
        // 007200  03  FILLER                              PIC X(12) VALUE SPACES.
        // 007300  03  FILLER                              PIC X(40) VALUE
        // 007400      " 代收稅款月報表－依代收行統計 ".
        // 007500  03  FILLER                              PIC X(06) VALUE SPACES.
        // 007600  03  FILLER                              PIC X(10) VALUE
        // 007700      " 印表日： ".
        // 007800  03  WK-YYY-R                            PIC 999.
        // 007900  03  FILLER                              PIC X(01) VALUE "/".
        // 008000  03  WK-MM-R                             PIC 99.
        // 008100  03  FILLER                              PIC X(01) VALUE "/".
        // 008200  03  WK-DD-R                             PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 代收稅款月報表－依代收行統計 ", 40));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 印表日 ", 10));
        sb.append(formatUtil.pad9(batchDateYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(batchDateMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(batchDateDD, 2));
        fileC061Contents.add(sb.toString());

        // 032700    MOVE     SPACES                  TO      REPORT-LINE.
        // 032800    WRITE    REPORT-LINE          AFTER      1.
        fileC061Contents.add("");

        // 032900    WRITE    REPORT-LINE           FROM      RPT061-TIT2.
        // 008300 01   RPT061-TIT2.
        // 008400  03  FILLER                           PIC X(02) VALUE SPACES.
        // 008500  03  FILLER                           PIC X(12) VALUE
        // 008600      " 代收月份： ".
        // 008700  03  WK-YEAR-P                        PIC 999.
        // 008800  03  FILLER                           PIC X(04) VALUE " 年 ".
        // 008900  03  WK-MONTH-P                       PIC 99.
        // 009000  03  FILLER                           PIC X(04) VALUE " 月 ".
        // 009100  03  FILLER                            PIC X(31) VALUE SPACES.
        // 009200  03  FILLER                            PIC X(10) VALUE
        // 009300      " 頁　次： ".
        // 009400  03  WK-PAGE-R                           PIC Z9    VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 代收月份： ", 12));
        sb.append(formatUtil.pad9(batchDateYY, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.pad9(batchDateMM, 2));
        sb.append(formatUtil.padX(" 月 ", 4));
        sb.append(formatUtil.padX(" ", 31));
        sb.append(formatUtil.padX(" 頁　次： ", 10));
        sb.append(formatUtil.padX(" 1", 2));
        fileC061Contents.add(sb.toString());

        // 033000    MOVE     SPACES                  TO      REPORT-LINE.
        // 033100    WRITE    REPORT-LINE           FROM      RPT061-TIT3.
        // 009500 01   RPT061-TIT3.
        // 009600  03  FILLER                              PIC X(02) VALUE SPACES.
        // 009700  03  FILLER                              PIC X(12) VALUE
        // 009800      " 代收分行： ".
        // 009900  03  WK-CLLBR-R                           PIC X(03).
        // 010000  03  FILLER                              PIC X(41) VALUE SPACES.
        // 010100  03  FILLER                              PIC X(12) VALUE
        // 010200      " 報表名稱： ".
        // 010300  03  WK-FORM-R                           PIC X(04) VALUE "C061".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 代收分行： ", 12));
        sb.append(formatUtil.pad9("" + wkCllbr, 3));
        sb.append(formatUtil.padX(" ", 41));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX("C061", 4));
        fileC061Contents.add(sb.toString());

        // 033200    MOVE     SPACES                  TO      REPORT-LINE.
        // 033300    WRITE    REPORT-LINE          AFTER      1.
        fileC061Contents.add("");

        // 033400    MOVE     SPACES                  TO      REPORT-LINE.
        // 033500    WRITE    REPORT-LINE           FROM      RPT061-TIT4.
        // 010400 01   RPT061-TIT4.
        // 010500* 03  FILLER                              PIC X(01) VALUE SPACES.
        // 010600  03  FILLER                              PIC X(10) VALUE
        // 010700      " 代收類別 ".
        // 010800  03  FILLER                              PIC X(13) VALUE SPACES.
        // 010900  03  FILLER                              PIC X(08) VALUE
        // 011000      " 總金額 ".
        // 011100  03  FILLER                              PIC X(07) VALUE SPACES.
        // 011200  03  FILLER                              PIC X(08) VALUE
        // 011300      " 總件數 ".
        // 011400  03  FILLER                              PIC X(12) VALUE SPACES.
        // 011500  03  FILLER                              PIC X(06) VALUE
        // 011600      " 備註 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" ", 13));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 備註 ", 6));
        fileC061Contents.add(sb.toString());

        // 033600    MOVE     SPACES                  TO      REPORT-LINE.
        // 033700    WRITE    REPORT-LINE           FROM      RPT061-GATE-LINE.
        // 011700 01   RPT061-GATE-LINE.
        // 011800  03  FILLER                              PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC061Contents.add(sb.toString());

        // 033800 RPT061-WTIT-EXIT.
    }

    private void dtlSum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr dtlSum ....");
        // 018600 DTL-SUM-RTN.
        // 018700* 不同代收類別，就將總數馬上印到明細
        // 018800    IF    SD-CODE  NOT=  WK-CODE
        if (!sdCode.equals(wkCode)) {
            // 018900        MOVE    SPACES            TO      REPORT-LINE
            // 019000        PERFORM  RPT-REMARK-RTN   THRU    RPT-REMARK-EXIT
            wkRemark = getRptRemarkX(wkCode);

            // 019100        MOVE    WK-SUBCNT         TO      RPT061-CNT-R
            // 019200        MOVE    WK-SUBAMT         TO      RPT061-AMT-R
            // 019300        WRITE   REPORT-LINE       FROM    RPT061-DTL
            // 011900 01   RPT061-DTL.
            // 012000  03  FILLER                              PIC X(01) VALUE SPACES.
            // 012100  03  RPT061-CODE-R                       PIC X(06)  .
            // 012200  03  FILLER                              PIC X(09) VALUE SPACES.
            // 012300  03  RPT061-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
            // 012400  03  FILLER                              PIC X(03) VALUE SPACES.
            // 012500  03  RPT061-CNT-R                        PIC ZZ,ZZZ,ZZ9.
            // 012600  03  FILLER                              PIC X(15) VALUE SPACES.
            // 012700  03  RPT061-REMARK-R                     PIC X(10) .
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 1)); // 1-2
            sb.append(formatUtil.padX(wkCode, 6)); // 2-8
            sb.append(formatUtil.padX(" ", 9)); // 8-17
            sb.append(String.format("%14s", dAmtFormat.format(wkSubAmt))); // 17-31
            sb.append(formatUtil.padX(" ", 3)); // 31-34
            sb.append(String.format("%10s", cntFormat.format(wkSubCnt))); // 34-44
            sb.append(formatUtil.padX(" ", 15)); // 44-59
            sb.append(formatUtil.padX(wkRemark, 10)); // 59-69
            fileC061Contents.add(sb.toString());

            // 019340        PERFORM   RPT061-005DETL-RTN THRU RPT061-005DETL-EXIT
            rpt061_005Detl(sb.toString());
            // 019400        MOVE    0                 TO      WK-SUBCNT,WK-SUBAMT
            // 019500        MOVE    SD-CODE           TO      WK-CODE ,RPT061-CODE-R.
            wkSubCnt = 0;
            wkSubAmt = BigDecimal.ZERO;
            wkCode = sdCode;
        }
        // 019800    ADD               1     TO     WK-SUBCNT,
        // 019900                                   WK-TOTCNT.
        // 020000    ADD       SD-AMT        TO     WK-SUBAMT,
        // 020100                                   WK-TOTAMT.
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);

        // 020200 DTL-SUM-EXIT.
    }

    private void rpt061_005Detl(String rpt061dtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr rpt061_005detl ....");
        // 037300 RPT061-005DETL-RTN.

        // 037322    MOVE     SPACES                  TO    RPT061-005TIT.
        // 037324    MOVE     WK-CLLBR-R              TO    RPT061-005TIT(2:3).
        // 037326    MOVE     "-"                     TO    RPT061-005TIT(5:1).
        // 037328    MOVE     RPT061-CODE-R           TO    RPT061-005TIT(6:6).
        // 037330    MOVE     RPT061-DTL(12:57)       TO    RPT061-005TIT(12:57).
        String rpt061_005Tit = " ";
        rpt061_005Tit +=
                String.format("%03d", wkCllbr)
                        + "-"
                        + wkCode
                        + rpt061dtl.substring(12, rpt061dtl.length());
        // 037340    WRITE   REPORT-LINEA      FROM    RPT061-005TIT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(rpt061_005Tit, 120)); // 1-2
        fileC061005Contents.add(sb.toString());
        // 037400    ADD     1                 TO      WK-005CNT.
        wk005Cnt = wk005Cnt + 1;
        // 037420    IF      WK-005CNT         >       57
        if (wk005Cnt > 57) {
            // 037440        PERFORM  RPT061-005TITL-RTN THRU RPT061-005TITL-EXIT
            rpt061_005Titl();
            // 037500    END-IF.
        }
        // 037510    IF       RPT061-CODE-R    =      "366AE9"
        if ("366AE9".equals(wkCode)) {
            // 037520        ADD   WK-SUBAMT       TO     WK-6AEAMT
            // 037530        ADD   WK-SUBCNT       TO     WK-6AECNT.
            wk6AEAmt = wk6AEAmt.add(wkSubAmt);
            wk6AECnt = wk6AECnt + wkSubCnt;
        }
        // 037540    IF       RPT061-CODE-R    =      "366AG9"
        if ("366AG9".equals(wkCode)) {
            // 037550        ADD   WK-SUBAMT       TO     WK-6AGAMT
            // 037560        ADD   WK-SUBCNT       TO     WK-6AGCNT.
            wk6AGAmt = wk6AGAmt.add(wkSubAmt);
            wk6AGCnt = wk6AGCnt + wkSubCnt;
        }
        // 037600 RPT061-005DETL-EXIT.
    }

    private void rptDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C061Lsnr rptDtl ....");
        // 020600 RPT-DTL-RTN.
        // 020700       MOVE    SPACES            TO      REPORT-LINE.
        // 020900       PERFORM  RPT-REMARK-RTN   THRU    RPT-REMARK-EXIT.
        wkRemark = getRptRemarkX(wkCode);

        // 021200       MOVE    WK-SUBCNT         TO      RPT061-CNT-R.
        // 021300       MOVE    WK-SUBAMT         TO      RPT061-AMT-R.
        // 021400       WRITE   REPORT-LINE       FROM    RPT061-DTL.
        // 011900 01   RPT061-DTL.
        // 012000  03  FILLER                              PIC X(01) VALUE SPACES.
        // 012100  03  RPT061-CODE-R                       PIC X(06)  .
        // 012200  03  FILLER                              PIC X(09) VALUE SPACES.
        // 012300  03  RPT061-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
        // 012400  03  FILLER                              PIC X(03) VALUE SPACES.
        // 012500  03  RPT061-CNT-R                        PIC ZZ,ZZZ,ZZ9.
        // 012600  03  FILLER                              PIC X(15) VALUE SPACES.
        // 012700  03  RPT061-REMARK-R                     PIC X(10) .
        StringBuilder rpt061Dtl = new StringBuilder();
        rpt061Dtl.append(formatUtil.padX(" ", 1)); // 1-2
        rpt061Dtl.append(formatUtil.padX(wkCode, 6)); // 2-8
        rpt061Dtl.append(formatUtil.padX(" ", 9)); // 8-17
        rpt061Dtl.append(String.format("%14s", dAmtFormat.format(wkSubAmt))); // 17-31
        rpt061Dtl.append(formatUtil.padX(" ", 3)); // 31-34
        rpt061Dtl.append(String.format("%10s", cntFormat.format(wkSubCnt))); // 34-44
        rpt061Dtl.append(formatUtil.padX(" ", 15)); // 44-59
        rpt061Dtl.append(formatUtil.padX(wkRemark, 10)); // 59-69
        fileC061Contents.add(rpt061Dtl.toString());

        // 021500       WRITE   REPORT-LINE       FROM    RPT061-GATE-LINE.
        // 011700 01   RPT061-GATE-LINE.
        // 011800  03  FILLER                              PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC061Contents.add(sb.toString());

        // 021520       PERFORM  RPT061-005DETL-RTN THRU RPT061-005DETL-EXIT.
        rpt061_005Detl(rpt061Dtl.toString());

        // 021530       WRITE   REPORT-LINEA      FROM    RPT061-GATE-LINE.
        // 011700 01   RPT061-GATE-LINE.
        // 011800  03  FILLER                              PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC061005Contents.add(sb.toString());

        // 021535       ADD     1                 TO      WK-005CNT.
        wk005Cnt = wk005Cnt + 1;
        // 021540       IF      WK-005CNT         >       57
        if (wk005Cnt > 57) {
            // 021545           PERFORM  RPT061-005TITL-RTN THRU RPT061-005TITL-EXIT
            rpt061_005Titl();
            // 021550       END-IF.
        }
        // 021600* 最後不需要再換分行，印表頭等動作
        // 021700       IF  WK-END = 1
        if (wkEnd == 1) {
            // 021800           GO  TO  RPT-DTL-EXIT.
            return;
        }
        // 021900       MOVE    SPACES            TO      REPORT-LINE.
        // 022000       WRITE   REPORT-LINE       AFTER   PAGE.
        fileC061Contents.add("\u000c");

        // 022100       MOVE    SD-CODE           TO      WK-CODE ,RPT061-CODE-R.
        // 022200       MOVE    0                 TO      WK-SUBCNT,WK-SUBAMT.
        // 022300       MOVE    SD-DATE           TO      WK-DATE.
        // 022400       MOVE    SD-CLLBR          TO      WK-CLLBR-R.
        // 022500       ADD     SD-AMT            TO      WK-SUBAMT,
        // 022600                                         WK-TOTAMT.
        // 022700       ADD     1                 TO      WK-SUBCNT,
        // 022800                                         WK-TOTCNT.
        wkCode = sdCode;
        wkSubCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkDate = sdDate;
        wkCllbr = sdCllbr;
        wkSubAmt = wkSubAmt.add(sdAmt);
        wkTotAmt = wkTotAmt.add(sdAmt);
        wkSubCnt = wkSubCnt + 1;
        wkTotCnt = wkTotCnt + 1;

        // 023000       PERFORM RPT061-WTIT-RTN    THRU RPT061-WTIT-EXIT.
        rpt061Wtit(PAGE_SEPARATOR);

        // 023100 RPT-DTL-EXIT.
    }

    private void rpt061_005Titl() {
        // 036500 RPT061-005TITL-RTN.
        // 036502    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036504    WRITE    REPORT-LINEA         AFTER      PAGE.
        sb = new StringBuilder();
        if (wk005Cnt == 0) {
            sb.append(PAGE_SEPARATOR); // 換頁符號
        } else {
            sb.append(PAGE_SEPARATOR); // 換頁符號
        }
        fileC061005Contents.add(sb.toString());

        // 036510    MOVE     WK-YEAR                 TO      WK-YEAR-P.
        // 036520    MOVE     WK-MONTH                TO      WK-MONTH-P.
        // 036530    MOVE     WK-YEAR                 TO      WK-YYY-R.
        // 036540    MOVE     WK-MONTH                TO      WK-MM-R.
        // 036550    MOVE     WK-DD                   TO      WK-DD-R.
        // 036555    MOVE     "005"                   TO      WK-CLLBR-R.
        String batchDateYY = batchDate.substring(0, 3);
        String batchDateMM = batchDate.substring(3, 5);
        String batchDateDD = batchDate.substring(5, 7);
        wkCllbr = 5;

        // 036560    MOVE     SPACES                  TO      RPT061-005TIT.
        // 013920 01   RPT061-005TIT.
        // 013940  03  FILLER                           PIC X(120) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 120));
        fileC061005Contents.add(sb.toString());

        // 036570    MOVE     RPT061-TIT1             TO      RPT061-005TIT.
        // 036575    MOVE     " 代收稅款月報表－依代收行統計（全行） "
        // 036580                                     TO   RPT061-005TIT(13:38).
        // 007100 01   RPT061-TIT1.
        // 007200  03  FILLER                              PIC X(12) VALUE SPACES.
        // 007300  03  FILLER                              PIC X(40) VALUE
        // 007400      " 代收稅款月報表－依代收行統計 ".
        // 007500  03  FILLER                              PIC X(06) VALUE SPACES.
        // 007600  03  FILLER                              PIC X(10) VALUE
        // 007700      " 印表日： ".
        // 007800  03  WK-YYY-R                            PIC 999.
        // 007900  03  FILLER                              PIC X(01) VALUE "/".
        // 008000  03  WK-MM-R                             PIC 99.
        // 008100  03  FILLER                              PIC X(01) VALUE "/".
        // 008200  03  WK-DD-R                             PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 代收稅款月報表－依代收行統計（全行） ", 40));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 印表日 ", 10));
        sb.append(formatUtil.pad9(batchDateYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(batchDateMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(batchDateDD, 2));
        fileC061005Contents.add(sb.toString());

        // 036600    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036610    WRITE    REPORT-LINEA         AFTER      1.
        fileC061005Contents.add("");

        // 036620    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036630    WRITE    REPORT-LINEA         AFTER      1.
        fileC061005Contents.add("");

        // 036640    WRITE    REPORT-LINEA          FROM      RPT061-005TIT.
        // 013920 01   RPT061-005TIT.
        // 013940  03  FILLER                           PIC X(120) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 120));
        fileC061005Contents.add(sb.toString());

        // 036650    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036655    MOVE     SPACES                  TO      RPT061-005TIT.
        // 036660    ADD      1                       TO      WK-005PAGE.
        wk005Page = wk005Page + 1;
        // 036670    MOVE     RPT061-TIT2             TO      RPT061-005TIT.
        // 036675    MOVE     WK-005PAGE              TO      WK-005PAGE-RPT.
        // 036680    MOVE     WK-005PAGE-RPT          TO   RPT061-005TIT(69:2).
        // 013920 01   RPT061-005TIT.
        // 013940  03  FILLER                           PIC X(120) VALUE SPACES.
        // 008300 01   RPT061-TIT2.
        // 008400  03  FILLER                           PIC X(02) VALUE SPACES.
        // 008500  03  FILLER                           PIC X(12) VALUE
        // 008600      " 代收月份： ".
        // 008700  03  WK-YEAR-P                        PIC 999.
        // 008800  03  FILLER                           PIC X(04) VALUE " 年 ".
        // 008900  03  WK-MONTH-P                       PIC 99.
        // 009000  03  FILLER                           PIC X(04) VALUE " 月 ".
        // 009100  03  FILLER                            PIC X(31) VALUE SPACES.
        // 009200  03  FILLER                            PIC X(10) VALUE
        // 009300      " 頁　次： ".
        // 009400  03  WK-PAGE-R                           PIC Z9    VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 代收月份： ", 12));
        sb.append(formatUtil.pad9(batchDateYY, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.pad9(batchDateMM, 2));
        sb.append(formatUtil.padX(" 月 ", 4));
        sb.append(formatUtil.padX(" ", 31));
        sb.append(formatUtil.padX(" 頁　次： ", 10));
        sb.append(formatUtil.padX("" + wk005Page, 2));
        fileC061005Contents.add(sb.toString());

        // 036700    WRITE    REPORT-LINEA         AFTER      1.
        fileC061005Contents.add("\u000c");

        // 036710    WRITE    REPORT-LINEA          FROM      RPT061-005TIT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 120));
        fileC061005Contents.add(sb.toString());

        // 036720    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036724    MOVE     SPACES                  TO      RPT061-005TIT.
        // 036726    MOVE     RPT061-TIT3             TO      RPT061-005TIT.
        // 036728    MOVE     "-1"                    TO      RPT061-005TIT(75:2).
        // 036730    WRITE    REPORT-LINEA          FROM      RPT061-005TIT.
        // 009500 01   RPT061-TIT3.
        // 009600  03  FILLER                              PIC X(02) VALUE SPACES.
        // 009700  03  FILLER                              PIC X(12) VALUE
        // 009800      " 代收分行： ".
        // 009900  03  WK-CLLBR-R                           PIC X(03).
        // 010000  03  FILLER                              PIC X(41) VALUE SPACES.
        // 010100  03  FILLER                              PIC X(12) VALUE
        // 010200      " 報表名稱： ".
        // 010300  03  WK-FORM-R                           PIC X(04) VALUE "C061".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 代收分行： ", 12));
        sb.append(formatUtil.pad9("" + wkCllbr, 3));
        sb.append(formatUtil.padX(" ", 41));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX("C061-1", 4));
        fileC061005Contents.add(sb.toString());

        // 036740    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036750    WRITE    REPORT-LINEA         AFTER      1.
        fileC061005Contents.add("");

        // 036760    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036770    WRITE    REPORT-LINEA          FROM      RPT061-TIT4.
        // 010400 01   RPT061-TIT4.
        // 010500* 03  FILLER                              PIC X(01) VALUE SPACES.
        // 010600  03  FILLER                              PIC X(10) VALUE
        // 010700      " 代收類別 ".
        // 010800  03  FILLER                              PIC X(13) VALUE SPACES.
        // 010900  03  FILLER                              PIC X(08) VALUE
        // 011000      " 總金額 ".
        // 011100  03  FILLER                              PIC X(07) VALUE SPACES.
        // 011200  03  FILLER                              PIC X(08) VALUE
        // 011300      " 總件數 ".
        // 011400  03  FILLER                              PIC X(12) VALUE SPACES.
        // 011500  03  FILLER                              PIC X(06) VALUE
        // 011600      " 備註 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" ", 13));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" 備註 ", 6));
        fileC061005Contents.add(sb.toString());

        // 036780    MOVE     SPACES                  TO      REPORT-LINEA.
        // 036790    WRITE    REPORT-LINEA          FROM      RPT061-GATE-LINE.
        // 011700 01   RPT061-GATE-LINE.
        // 011800  03  FILLER                              PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileC061005Contents.add(sb.toString());

        // 036900    MOVE     10                      TO      WK-005CNT.
        wk005Cnt = 10;
        // 037000 RPT061-005TITL-EXIT.
    }

    private void rpt061_005Sum() {
        // 038000 RPT061-005SUM-RTN.
        // 038050    MOVE  SPACES              TO     RPT061-005TIT.
        // 038100    MOVE  " 總計  366AE9"     TO     RPT061-005TIT(1:13).
        // 038110    MOVE  WK-6AEAMT           TO     WK-RPT-6AEAMT.
        // 038120    MOVE  WK-RPT-6AEAMT       TO     RPT061-005TIT(17:14).
        // 038130    MOVE  WK-6AECNT           TO     WK-RPT-6AECNT.
        // 038140    MOVE  WK-RPT-6AECNT       TO     RPT061-005TIT(34:10).
        // 038160    MOVE  " 國稅 "            TO     RPT061-005TIT(59:6).
        // 038200    WRITE REPORT-LINEA        FROM   RPT061-005TIT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總計  366AE9", 13)); // 1-14
        sb.append(formatUtil.padX(" ", 3)); // 14-17
        sb.append(String.format("%14s", dAmtFormat.format(wk6AEAmt))); // 17-31
        sb.append(formatUtil.padX(" ", 3)); // 31-34
        sb.append(String.format("%10s", cntFormat.format(wk6AECnt))); // 34-44
        sb.append(formatUtil.padX(" ", 15)); // 44-59
        sb.append(formatUtil.padX(" 國稅 ", 6)); // 59-65
        fileC061005Contents.add(sb.toString());

        // 038230    MOVE  SPACES              TO     RPT061-005TIT.
        // 038240    MOVE  "366AG9"            TO     RPT061-005TIT(8:6).
        // 038250    MOVE  WK-6AGAMT           TO     WK-RPT-6AGAMT.
        // 038260    MOVE  WK-RPT-6AGAMT       TO     RPT061-005TIT(17:14).
        // 038280    MOVE  WK-6AGCNT           TO     WK-RPT-6AGCNT.
        // 038300    MOVE  WK-RPT-6AGCNT       TO     RPT061-005TIT(34:10).
        // 038320    MOVE  " 地方稅 "          TO     RPT061-005TIT(59:8).
        // 038340    WRITE REPORT-LINEA        FROM   RPT061-005TIT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7)); // 1-8
        sb.append(formatUtil.padX("366AG9", 6)); // 8-14
        sb.append(formatUtil.padX(" ", 3)); // 14-17
        sb.append(String.format("%14s", dAmtFormat.format(wk6AGAmt))); // 17-31
        sb.append(formatUtil.padX(" ", 3)); // 31-34
        sb.append(String.format("%10s", cntFormat.format(wk6AGCnt))); // 34-44
        sb.append(formatUtil.padX(" ", 15)); // 44-59
        sb.append(formatUtil.padX(" 地方稅 ", 8)); // 59-67
        fileC061005Contents.add(sb.toString());

        // 038400 RPT061-005SUM-EXIT.
    }

    private String getRptRemarkX(String code) {
        // 035000 RPT-REMARK-RTN.
        // 035100     MOVE SPACES       TO RPT061-REMARK-R.
        String remark = " ";
        // 035200     IF WK-CODE = "366AE9" THEN
        if ("366AE9".equals(code)) {
            // 035300        MOVE  " 國稅 " TO RPT061-REMARK-R.
            remark = " 國稅 ";
        }
        if ("366AG9".equals(code)) {
            // 035500     IF WK-CODE = "366AG9" THEN
            // 035600        MOVE  " 地方稅 " TO RPT061-REMARK-R.
            remark = " 地方稅 ";
        }
        return remark;
    }

    private String getrocdate(int dateI) {
        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }
}
