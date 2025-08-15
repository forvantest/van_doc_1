/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C0891;
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
import java.math.RoundingMode;
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
@Component("C0891Lsnr")
@Scope("prototype")
public class C0891Lsnr extends BatchListenerCase<C0891> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private String fileInputName; // 讀檔檔名
    private static final String FILE_NAME = "CL-BH-089-1"; // 檔名
    private static final String ANALY_FILE_PATH = "CLBAF"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String tmpDir = "Dir";
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC0891Contents; // 檔案內容
    private String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";
    private String batchDate = ""; // 批次日期(民國年yyyymmdd)
    private String batchDateYM = "";
    private int wkRptCount;
    private int wkReccnt;
    private int wkTotCnt = 0;
    private BigDecimal wkSub1Cnt;
    private BigDecimal wkSub2Cnt;
    private int wkPrePbrno = 0;
    private int wkRptPbrno = 0;
    private String wkSubEntpno = "";
    private String wkSubCName = "";
    private BigDecimal fdClbafCnt = BigDecimal.ZERO;
    private BigDecimal fdClbafAmt = BigDecimal.ZERO;
    private int fdClbafPbrno = 0;
    private String fdClbafCName = "";
    private String fdClbafEntpno = "";
    private String fdClbafDateYM = "";
    private BigDecimal wkSub1Amt;
    private BigDecimal wkSub2Amt;
    private BigDecimal wkRptAmtP = BigDecimal.ZERO;
    private BigDecimal wkTempAmtP = BigDecimal.ZERO;
    private BigDecimal wkTempCntP = BigDecimal.ZERO;
    private BigDecimal wkRptCntP = BigDecimal.ZERO;
    private BigDecimal wkTempAmt = BigDecimal.ZERO;
    private BigDecimal wkTempCnt = BigDecimal.ZERO;
    private DecimalFormat amtFormat = new DecimalFormat("###,###,###,##0"); // 數字格式
    private DecimalFormat amt2Format = new DecimalFormat("##0.0;-##0.0"); // 數字格式2
    private DecimalFormat amt3Format = new DecimalFormat("###,###,##0"); // 數字格式3

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C0891 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C0891 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr run()");
        init(event);

        if (textFile.exists(inputFilePath)) {
            toWriteC0891File();
            try {
                textFile.writeFileContent(outputFilePath, fileC0891Contents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/RPT/C089/1  COMPLETED ");
    }

    private void init(C0891 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr init ....");
        //// 清變數
        // 015000     MOVE    0                   TO     WK-RPT-COUNT  ,
        // 015100                                        WK-RECCNT     ,
        // 015200                                        WK-SUB1-CNT ,WK-SUB2-CNT ,
        // 015300                                        WK-SUB1-AMT ,WK-SUB2-CNT ,
        // 015400                                        WK-PRE-PBRNO  .
        wkRptCount = 0;
        wkReccnt = 0;
        wkSub1Cnt = BigDecimal.ZERO;
        wkSub2Cnt = BigDecimal.ZERO;
        wkSub1Amt = BigDecimal.ZERO;
        wkSub2Amt = BigDecimal.ZERO;
        wkPrePbrno = 0;
        // 018300 INITIAL-EXIT.
        // 抓批次營業日
        // 013900     MOVE   WK-TASK-DATE       TO       WK-KDATE    ,
        // 014000                                        WK-RPT-PDATE.
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        batchDate = getrocdate(parse.string2Integer(textMap.get("DATE7"))); // TODO: 待確認BATCH參數名稱
        batchDateYM = batchDate.substring(0, 5);
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C0891Lsnr batchDate = {} YM = {} ",
                batchDate,
                batchDateYM);

        // 讀檔路徑
        ////
        // 014100     CHANGE ATTRIBUTE FILENAME OF FD-CLBAF TO WK-CLBAFDIR.
        // 003100  01 WK-CLBAFDIR.
        // 003200     03 FILLER                          PIC X(17)
        // 003300                         VALUE "DATA/CL/BH/CLBAF/".
        // 003400     03 WK-KDATE                        PIC 9(07).
        // 003500     03 FILLER                          PIC X(01)
        // 003600                                        VALUE ".".
        // 015600     OPEN    INPUT     FD-CLBAF.
        fileInputName = batchDate;
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + fileInputName;
        // 產檔路徑
        outputFilePath = fileDir + FILE_NAME;
        // 刪除舊檔
        textFile.deleteFile(outputFilePath);
        // 檔案內容
        fileC0891Contents = new ArrayList<>();
    }

    private void toWriteC0891File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr toWriteC0891File ....");

        // SORT OBJ/CL/BH/SORT/CLBAF
        File tmpFile = new File(inputFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 11, SortBy.ASC));
        keyRanges.add(new KeyRange(12, 8, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);

        int cnt = 0;
        for (String detail : lines) {
            //  03  FD-CLBAF-PBRNO	9(03)	主辦分行 0-3
            //  03  FD-CLBAF-ENTPNO	X(08)	統一編號 3-11
            //  03  FD-CLBAF-DATE	9(08)	代收日 11-19
            //  03  FD-CLBAF-CODE	X(06)	代收類別 19-25
            //  03  FD-CLBAF-CNT	9(06)	該業務在當日之累計代收筆數 25-31
            //  03  FD-CLBAF-AMT	9(13)	該業務在當日之累計代收金額 31-44
            //  03  FD-CLBAF-CLLBR	9(03)	代收行 44-47
            //  03  FD-CLBAF-TXTYPE	X(01)	交易類型 47-48
            //  03  FD-CLBAF-CNAME	X(22)	單位中文名 48-70
            //  03  FD-CLBAF-FILLER1	X(02)	 70-72
            cnt++;
            fdClbafPbrno = parse.string2Integer(detail.substring(0, 3));
            fdClbafEntpno = detail.substring(3, 11);
            fdClbafCName = detail.substring(48, 70);
            fdClbafDateYM = detail.substring(12, 17);
            fdClbafCnt = parse.string2BigDecimal(detail.substring(25, 31));
            fdClbafAmt = parse.string2BigDecimal(detail.substring(31, 44));
            // 016300     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
            pageSWH();
            // 016400     IF FD-CLBAF-ENTPNO = WK-SUB1-ENTPNO
            // 016500     OR WK-SUB1-ENTPNO = SPACES
            if (fdClbafEntpno.equals(wkSubEntpno) || wkSubEntpno.trim().isEmpty()) {
                // 016600        PERFORM 3333-COUNT-RTN   THRU   3333-COUNT-EXIT
                count();
            } else {
                // 016800        PERFORM 5000-DTLIN-RTN   THRU   5000-DTLIN-EXIT.
                dtlin();
            }
            // 017000     MOVE     FD-CLBAF-PBRNO      TO     WK-PRE-PBRNO.
            wkPrePbrno = fdClbafPbrno;

            // 017200     GO TO   0000-MAIN-LOOP.

            if (cnt == lines.size()) {
                // 017300 0000-MAIN-LFAST.
                //
                //// 檔尾,寫明細、表尾
                //
                // 017350     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT.
                // 017400     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT.
                dtlin();
                subTail();

                // 017420     MOVE    WK-TOTCNT           TO     WK-RPT-TOTCNT.
                // 017440     WRITE   REPORT-LINE         FROM   WK-TOTAL-LINE.
                // 011600 01 WK-TOTAL-LINE.
                // 011700    02 FILLER                   PIC X(02) VALUE SPACES.
                // 011800    02 FILLER                   PIC X(11) VALUE " 分行總計 :".
                // 011900    02 WK-RPT-TOTCNT            PIC ZZZ,ZZZ,ZZ9.
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 2));
                sb.append(formatUtil.padX(" 分行總計 ", 11));
                sb.append(String.format("%11s", amt3Format.format(wkTotCnt)));
                fileC0891Contents.add(sb.toString());

                // 017500 0000-MAIN-EXIT.

            }
        }
    }

    private void pageSWH() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr pageSWH ....");
        // 019500 3000-PAGESWH-RTN.
        // 019600*  換主辦行或 50 筆換頁
        // 019700     IF    (    WK-PRE-PBRNO        =      0        )
        if (wkPrePbrno == 0) {
            // 019800       ADD      1                   TO     WK-RPT-COUNT
            // 019900       MOVE     FD-CLBAF-PBRNO      TO     WK-RPT-PBRNO
            // 020000       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            // 020100       GO TO 3000-PAGESWH-EXIT.
            wkRptCount = wkRptCount + 1;
            wkRptPbrno = fdClbafPbrno;
            title(PAGE_SEPARATOR);
            return;
        }
        // 020300     IF    (    FD-CLBAF-PBRNO         NOT =  WK-PRE-PBRNO  )
        // 020400       AND (    WK-PRE-PBRNO        NOT =  0             )
        if (fdClbafPbrno != wkPrePbrno && wkPrePbrno != 0) {
            // 020500       PERFORM 5000-DTLIN-RTN       THRU   5000-DTLIN-EXIT
            dtlin();

            // 020600       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subTail();

            // 020620       MOVE     WK-TOTCNT           TO     WK-RPT-TOTCNT
            // 020640       WRITE    REPORT-LINE         FROM   WK-TOTAL-LINE
            // 011600 01 WK-TOTAL-LINE.
            // 011700    02 FILLER                   PIC X(02) VALUE SPACES.
            // 011800    02 FILLER                   PIC X(11) VALUE " 分行總計 :".
            // 011900    02 WK-RPT-TOTCNT            PIC ZZZ,ZZZ,ZZ9.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 分行總計 ", 11));
            sb.append(String.format("%11s", amt3Format.format(wkTotCnt)));
            fileC0891Contents.add(sb.toString());

            // 020700       MOVE     SPACES              TO     REPORT-LINE
            // 020800       WRITE    REPORT-LINE         AFTER  PAGE
            // 020850       MOVE     0                   TO     WK-TOTCNT
            // 020900       MOVE     FD-CLBAF-PBRNO      TO     WK-RPT-PBRNO
            // 021000       MOVE     1                   TO     WK-RPT-COUNT
            wkTotCnt = 0;
            wkRptPbrno = fdClbafPbrno;
            wkRptCount = wkRptCount + 1;

            // 021100       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title(PAGE_SEPARATOR);

            // 021200       GO TO 3000-PAGESWH-EXIT.
            return;
        }

        // 021400     IF    (    WK-RECCNT           >      49       )
        if (wkReccnt > 49) {
            // 021500       MOVE     SPACES              TO     REPORT-LINE
            // 021520       WRITE    REPORT-LINE         FROM   WK-GATE-LINE
            sb = new StringBuilder();
            sb.append(
                    formatUtil.padX(
                            "---------------------------------------------------------------------------------------------------------------------------------------",
                            135));
            fileC0891Contents.add(sb.toString());

            // 021540       MOVE     SPACES              TO     REPORT-LINE
            // 021560       MOVE     WK-RECCNT           TO     WK-RPT-SUBCNT
            // 021590       WRITE    REPORT-LINE         FROM   WK-SUBTOTAL-LINE
            // 011200 01 WK-SUBTOTAL-LINE.
            // 011300    02 FILLER                   PIC X(02) VALUE SPACES.
            // 011400    02 FILLER                   PIC X(11) VALUE " 分行小計 :".
            // 011500    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 分行小計 ", 11));
            sb.append(String.format("%11s", amt3Format.format(wkReccnt)));
            fileC0891Contents.add(sb.toString());

            // 021595       MOVE     SPACES              TO     REPORT-LINE
            // 021600       WRITE    REPORT-LINE         AFTER  PAGE
            // 021650       ADD      WK-RECCNT           TO     WK-TOTCNT
            // 021700       MOVE     0                   TO     WK-RECCNT
            // 021800       ADD      1                   TO     WK-RPT-COUNT
            // 021900       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            wkTotCnt = wkTotCnt + 1;
            wkReccnt = 0;
            wkRptCount = wkRptCount + 1;
            title(PAGE_SEPARATOR);

            // 022000       GO TO 3000-PAGESWH-EXIT.
            return;
        }
    }

    private void title(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr title ....");
        // 017800 2000-TITLE-RTN.
        //// 寫報表表頭
        // 017900     MOVE       SPACES              TO     REPORT-LINE.
        // 018000     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(45) VALUE SPACE.
        // 004300    02 FILLER                          PIC X(54) VALUE
        // 004400       " 代收類別使用趨勢統計報表－依統編 ".
        // 004500    02 FILLER                          PIC X(22) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        sb.append(formatUtil.padX(" ", 45));
        sb.append(formatUtil.padX(" 代收類別使用趨勢統計報表－依統編 ", 54));
        sb.append(formatUtil.padX(" ", 22));
        fileC0891Contents.add(sb.toString());

        // 018100     MOVE       SPACES              TO     REPORT-LINE.
        // 018200     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileC0891Contents.add("");

        // 018300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004700 01 WK-TITLE-LINE2.
        // 004800    02 FILLER                          PIC X(2) VALUE SPACE.
        // 004900    02 FILLER                          PIC X(18) VALUE
        // 005000       " 報表名稱： C089-1".
        // 005100    02 FILLER                          PIC X(100) VALUE SPACE.
        // 005200    02 FILLER                          PIC X(12) VALUE
        // 005300       " 頁　　次： ".
        // 005400    02 WK-RPT-COUNT                    PIC 9(03).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 報表名稱： C089-1", 18));
        sb.append(formatUtil.padX(" ", 100));
        sb.append(formatUtil.padX(" 頁　　次： ", 12));
        sb.append(formatUtil.pad9("" + wkRptCount, 1));
        fileC0891Contents.add(sb.toString());

        // 018400     MOVE       SPACES              TO     REPORT-LINE.
        // 018500     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 005600 01 WK-TITLE-LINE3.
        // 005700    02 FILLER                          PIC X(2) VALUE SPACE.
        // 005800    02 FILLER                          PIC X(12) VALUE
        // 005900       " 主辦分行： ".
        // 006000    02 WK-RPT-PBRNO                    PIC 9(03).
        // 006100    02 FILLER                          PIC X(103) VALUE SPACE.
        // 006200    02 FILLER                          PIC X(16) VALUE
        // 006300       " 用　　途：參考 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        sb.append(formatUtil.pad9("" + wkRptPbrno, 3));
        sb.append(formatUtil.padX(" ", 103));
        sb.append(formatUtil.padX(" 用　　途：參考 ", 16));
        fileC0891Contents.add(sb.toString());

        // 018600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        // 006700 01 WK-TITLE-LINE4.
        // 006800    02 FILLER                          PIC X(2) VALUE SPACE.
        // 006900    02 FILLER                          PIC X(12) VALUE
        // 007000       " 印表日期： ".
        // 007100    02 WK-RPT-PDATE                    PIC Z99/99/99.
        // 007200    02 FILLER                          PIC X(97) VALUE SPACE.
        // 007300    02 FILLER                          PIC X(16) VALUE
        // 007400       " 保存年限：５年 ".
        int batchDateYY = parse.string2Integer(batchDate.substring(0, 3));
        String batchDateMM = batchDate.substring(3, 5);
        String batchDateDD = batchDate.substring(5, 7);
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(formatUtil.padX(batchDateYY + "/" + batchDateMM + "/" + batchDateDD, 9));
        sb.append(formatUtil.padX(" ", 97));
        sb.append(formatUtil.padX(" 保存年限：５年 ", 16));
        fileC0891Contents.add(sb.toString());

        // 018700     MOVE       SPACES              TO     REPORT-LINE.
        // 018800     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileC0891Contents.add("");

        // 018900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE5.
        // 007600 01 WK-TITLE-LINE5.
        // 007700    02 FILLER              PIC X(01) VALUE SPACES      .
        // 007900    02 FILLER              PIC X(10) VALUE "   統編   ".
        // 008000    02 FILLER              PIC X(03) VALUE SPACES      .
        // 008100    02 FILLER              PIC X(22) VALUE " 戶名 ".
        // 008200    02 FILLER              PIC X(10) VALUE " 上月筆數 ".
        // 008250    02 FILLER              PIC X(10) VALUE " 本月筆數 ".
        // 008300    02 FILLER   PIC X(22) VALUE " 本月筆數與上月增減比 ".
        // 008400    02 FILLER              PIC X(06) VALUE SPACES      .
        // 008500    02 FILLER              PIC X(10) VALUE " 上月金額 ".
        // 008520    02 FILLER              PIC X(06) VALUE SPACES      .
        // 008550    02 FILLER              PIC X(10) VALUE " 本月金額 ".
        // 008600    02 FILLER              PIC X(04) VALUE SPACES       .
        // 008700    02 FILLER   PIC X(22) VALUE " 本月金額與上月增減比 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX("   統編   ", 10));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 戶名 ", 22));
        sb.append(formatUtil.padX(" 上月筆數 ", 10));
        sb.append(formatUtil.padX(" 本月筆數 ", 10));
        sb.append(formatUtil.padX(" 本月筆數與上月增減比 ", 22));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 上月金額 ", 10));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 本月金額 ", 10));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(" 本月金額與上月增減比 ", 22));
        fileC0891Contents.add(sb.toString());

        // 019000     MOVE       SPACES              TO     REPORT-LINE.
        // 019100     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 011000 01 WK-GATE-LINE.
        // 011100    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "---------------------------------------------------------------------------------------------------------------------------------------",
                        135));
        fileC0891Contents.add(sb.toString());

        // 019200 2000-TITLE-EXIT.

    }

    private void dtlin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr dtlin ....");
        // 023500 5000-DTLIN-RTN.
        //// 報表明細

        // 023600
        // 023700* 一般狀況
        // 023800     IF    WK-SUB1-CNT >  0
        if (wkSub1Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 023900       COMPUTE   WK-RPT-CNTP =   WK-SUB2-CNT / WK-SUB1-CNT * 100
            if (wkSub2Cnt.compareTo(BigDecimal.ZERO) == 0
                    || wkSub1Cnt.compareTo(BigDecimal.ZERO) == 0) {
                wkRptCntP = BigDecimal.ZERO;
            } else {
                wkRptCntP =
                        wkSub2Cnt
                                .divide(wkSub1Cnt, 3, RoundingMode.DOWN)
                                .multiply(new BigDecimal(100));
            }
        }
        // 024000     ELSE  IF WK-SUB2-CNT >  0
        else if (wkSub2Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 024100       MOVE    999.99         TO    WK-RPT-CNTP.
            wkRptCntP = new BigDecimal(999.99);
        }
        // 024200     IF    WK-SUB1-AMT >  0
        if (wkSub1Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 024300       COMPUTE   WK-RPT-AMTP =   WK-SUB2-AMT / WK-SUB1-AMT * 100
            if (wkSub2Amt.compareTo(BigDecimal.ZERO) == 0
                    || wkSub1Amt.compareTo(BigDecimal.ZERO) == 0) {
                wkRptAmtP = BigDecimal.ZERO;
            } else {
                wkRptAmtP = wkSub2Amt.divide(wkSub1Amt).multiply(new BigDecimal(100));
            }

        }
        // 024400     ELSE   IF WK-SUB2-AMT >  0
        else if (wkSub2Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 024500       MOVE    999.99         TO    WK-RPT-AMTP.
            wkRptAmtP = new BigDecimal(999.99);
        }
        // 024600* 修正減少時的狀況
        // 024700     IF   (WK-SUB2-CNT < WK-SUB1-CNT) OR WK-SUB1-CNT > 0
        if (wkSub2Cnt.compareTo(wkSub1Cnt) < 0 || wkSub1Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 024800       MOVE WK-RPT-CNTP    TO   WK-TEMP-CNTP
            // 024900       COMPUTE WK-RPT-CNTP  =  WK-TEMP-CNTP - 100
            wkTempCntP = wkRptCntP;
            wkRptCntP = wkTempCntP.subtract(new BigDecimal(100));
            // 025300     END-IF.
        }
        // 025400     IF   (WK-SUB2-AMT < WK-SUB1-AMT) OR WK-SUB1-AMT > 0
        if (wkSub2Amt.compareTo(wkSub1Amt) < 0 || wkSub1Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 025500       MOVE WK-RPT-AMTP    TO   WK-TEMP-AMTP
            // 025600       COMPUTE WK-RPT-AMTP  =  WK-TEMP-AMTP - 100
            wkTempAmtP = wkRptAmtP;
            wkRptAmtP = wkTempAmtP.subtract(new BigDecimal(100));
            // 025700     END-IF.
        }
        // 025710* 修正增加時最大值
        // 025720     COMPUTE WK-TEMP-CNT  =  WK-SUB2-CNT / 10
        if (wkSub2Cnt.compareTo(BigDecimal.ZERO) == 0) {
            wkTempCnt = BigDecimal.ZERO;
        } else {
            wkTempCnt = wkSub2Cnt.divide(new BigDecimal(10));
        }
        // 025730     IF WK-TEMP-CNT    NOT<  WK-SUB1-CNT
        if (wkTempCnt.compareTo(wkSub1Cnt) >= 0) {
            // 025740        MOVE    999.99         TO    WK-RPT-CNTP.
            wkRptCntP = new BigDecimal(999.99);
        }
        // 025750     COMPUTE WK-TEMP-AMT  =  WK-SUB2-AMT / 10
        if (wkSub2Amt.compareTo(BigDecimal.ZERO) == 0) {
            wkTempAmt = BigDecimal.ZERO;
        } else {
            wkTempAmt = wkSub2Amt.divide(new BigDecimal(10));
        }
        // 025760     IF WK-TEMP-AMT    NOT<  WK-SUB1-AMT
        if (wkTempAmt.compareTo(wkSub1Amt) >= 0) {
            // 025770        MOVE    999.99         TO    WK-RPT-AMTP.
            wkRptAmtP = new BigDecimal(999.99);
        }
        // 025800     MOVE      WK-SUB1-CNAME   TO    WK-RPT-CNAME   .
        // 025900     MOVE      WK-SUB1-ENTPNO  TO    WK-RPT-ENTPNO  .
        // 026000     MOVE      WK-SUB1-CNT     TO    WK-RPT-CNT1    .
        // 026100     MOVE      WK-SUB1-AMT     TO    WK-RPT-AMT1    .
        // 026200     MOVE      WK-SUB2-CNT     TO    WK-RPT-CNT2    .
        // 026300     MOVE      WK-SUB2-AMT     TO    WK-RPT-AMT2    .
        // 026400     WRITE     REPORT-LINE     FROM  WK-DETAIL-LINE.
        // 008900 01 WK-DETAIL-LINE.
        // 009000    02 FILLER                          PIC X(01) VALUE SPACE.
        // 009100    02 WK-RPT-ENTPNO                   PIC 9(08).
        // 009200    02 FILLER                          PIC X(01) VALUE SPACE.
        // 009300    02 WK-RPT-CNAME                    PIC X(22).
        // 009400    02 FILLER                          PIC X(02) VALUE SPACE.
        // 009500    02 WK-RPT-CNT1                     PIC ZZ,ZZZ,ZZ9.
        // 009600    02 FILLER                          PIC X(01) VALUE SPACES.
        // 009700    02 WK-RPT-CNT2                     PIC ZZ,ZZZ,ZZ9.
        // 009800    02 FILLER                          PIC X(08) VALUE SPACE.
        // 009900    02 WK-RPT-CNTP                     PIC -ZZ9.9.
        // 010000    02 FILLER                          PIC X(01) VALUE "%".
        // 010100    02 FILLER                          PIC X(08) VALUE SPACE.
        // 010200    02 WK-RPT-AMT1                     PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 010300    02 FILLER                          PIC X(01) VALUE SPACES.
        // 010400    02 WK-RPT-AMT2                     PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 010500    02 FILLER                          PIC X(12) VALUE SPACE.
        // 010600    02 WK-RPT-AMTP                     PIC -ZZ9.9.
        // 010700    02 FILLER                          PIC X(01) VALUE "%".
        // 010800    02 FILLER                          PIC X(02) VALUE SPACE.
        // 010900    02 FILLER                          PIC X(02) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.pad9(wkSubEntpno, 8));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(wkSubCName.trim(), 22));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(String.format("%10s", amtFormat.format(wkSub1Cnt)));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(String.format("%10s", amtFormat.format(wkSub2Cnt)));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(String.format("%6s", amt2Format.format(wkRptCntP)));
        sb.append(formatUtil.padX("%", 1));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(String.format("%15s", amtFormat.format(wkSub1Amt)));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(String.format("%15s", amtFormat.format(wkSub2Amt)));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(String.format("%6s", amt2Format.format(wkRptAmtP)));
        sb.append(formatUtil.padX("%", 1));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" ", 2));
        fileC0891Contents.add(sb.toString());

        // 026500     MOVE      SPACES          TO    WK-SUB1-ENTPNO  .
        // 026600     MOVE      0               TO    WK-SUB1-CNT ,WK-SUB2-CNT
        // 026700                                     WK-SUB1-AMT ,WK-SUB2-AMT.
        // 026800     ADD       1               TO    WK-RECCNT      .
        wkSubEntpno = "";
        wkSub1Cnt = BigDecimal.ZERO;
        wkSub2Cnt = BigDecimal.ZERO;
        wkSub1Amt = BigDecimal.ZERO;
        wkSub2Amt = BigDecimal.ZERO;
        wkReccnt = wkReccnt + 1;

        // 026850     PERFORM 3333-COUNT-RTN   THRU   3333-COUNT-EXIT.
        count();
        // 026900 5000-DTLIN-EXIT.
    }

    private void count() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr count ....");
        // 028100 3333-COUNT-RTN.
        //// 如果統編不是空白,搬統編資料
        // 028300     IF  WK-SUB1-ENTPNO = SPACES
        if (wkSubEntpno.trim().isEmpty()) {
            // 028400         MOVE     FD-CLBAF-ENTPNO    TO    WK-SUB1-ENTPNO.
            wkSubEntpno = fdClbafEntpno;
        }

        //// 累加筆數金額,搬單位中文(本月非本月)
        // 028700     IF FD-CLBAF-DATE(2:5) NOT= WK-TASK-DATE(1:5)
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C0891Lsnr ClbarDateYN = {} batchDateYM = {} ",
                fdClbafDateYM,
                batchDateYM);
        if (!fdClbafDateYM.equals(batchDateYM)) {
            // 028900        ADD       FD-CLBAF-CNT       TO    WK-SUB1-CNT
            // 029000        ADD       FD-CLBAF-AMT       TO    WK-SUB1-AMT
            // 029100        MOVE      FD-CLBAF-CNAME     TO    WK-SUB1-CNAME
            wkSub1Cnt = wkSub1Cnt.add(fdClbafCnt);
            wkSub1Amt = wkSub1Amt.add(fdClbafAmt);
            wkSubCName = fdClbafCName;
        } else {
            // 029300        ADD       FD-CLBAF-CNT       TO    WK-SUB2-CNT
            // 029400        ADD       FD-CLBAF-AMT       TO    WK-SUB2-AMT
            // 029500        MOVE      FD-CLBAF-CNAME     TO    WK-SUB1-CNAME
            wkSub2Cnt = wkSub2Cnt.add(fdClbafCnt);
            wkSub2Amt = wkSub2Amt.add(fdClbafAmt);
            wkSubCName = fdClbafCName;

            // 029600     END-IF.
        }
        // 029200     ELSE
    }

    private void subTail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0891Lsnr subTail ....");
        // 022500 4000-SUBTAIL-RTN.
        //// 寫報表表尾

        // 022600     MOVE       WK-RECCNT      TO    WK-RPT-SUBCNT .

        // 022700     MOVE       SPACES         TO    REPORT-LINE   .
        // 022800     WRITE      REPORT-LINE    FROM  WK-GATE-LINE  .
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        "---------------------------------------------------------------------------------------------------------------------------------------",
                        135));
        fileC0891Contents.add(sb.toString());

        // 022900     MOVE       SPACES         TO    REPORT-LINE   .
        // 023000     WRITE      REPORT-LINE    FROM  WK-SUBTOTAL-LINE .
        // 011200 01 WK-SUBTOTAL-LINE.
        // 011300    02 FILLER                   PIC X(02) VALUE SPACES.
        // 011400    02 FILLER                   PIC X(11) VALUE " 分行小計 :".
        // 011500    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 分行小計 ", 11));
        sb.append(String.format("%11s", amt3Format.format(wkReccnt)));
        fileC0891Contents.add(sb.toString());

        // 023050     ADD        WK-RECCNT      TO    WK-TOTCNT.
        // 023100     MOVE       0              TO    WK-RECCNT.
        wkTotCnt = wkTotCnt + wkReccnt;
        wkReccnt = 0;

        // 023200 4000-SUBTAIL-EXIT.
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
