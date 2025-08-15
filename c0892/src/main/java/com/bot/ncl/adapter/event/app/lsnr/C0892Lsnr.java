/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C0892;
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
@Component("C0892Lsnr")
@Scope("prototype")
public class C0892Lsnr extends BatchListenerCase<C0892> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private String fileInputName; // 讀檔檔名
    private static final String FILE_NAME = "CL-BH-089-2"; // 檔名
    private static final String ANALY_FILE_PATH = "CLBAF2"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC0892Contents; // 檔案內容
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
    private BigDecimal wkCntps = BigDecimal.ZERO;
    private BigDecimal wkAmtps = BigDecimal.ZERO;
    private BigDecimal wkTempCnt = BigDecimal.ZERO;
    private DecimalFormat amt2Format = new DecimalFormat("##0.0;-##0.0"); // 數字格式2

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C0892 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C0892 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr run()");

        init(event);

        // 014700     IF  ATTRIBUTE  RESIDENT   OF FD-CLBAF IS =  VALUE(TRUE)
        // 014900       PERFORM  0000-MAIN-RTN  THRU 0000-MAIN-EXIT.
        if (textFile.exists(inputFilePath)) {
            toWriteC0892File();
            try {
                textFile.writeFileContent(outputFilePath, fileC0892Contents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " SYM/CL/BH/RPT/C089/2  COMPLETED ");
    }

    private void init(C0892 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr init ....");
        //// 清變數
        // 015500     MOVE    0                   TO     WK-RPT-COUNT  ,
        // 015600                                        WK-RECCNT     ,
        // 015700                                        WK-SUB1-CNT ,WK-SUB2-CNT ,
        // 015800                                        WK-SUB1-AMT ,WK-SUB2-CNT ,
        // 015900                                        WK-PRE-PBRNO  .
        wkRptCount = 0;
        wkReccnt = 0;
        wkSub1Cnt = BigDecimal.ZERO;
        wkSub2Cnt = BigDecimal.ZERO;
        wkSub1Amt = BigDecimal.ZERO;
        wkSub2Amt = BigDecimal.ZERO;
        wkPrePbrno = 0;

        // 抓批次營業日
        // 014400     MOVE   WK-TASK-DATE       TO       WK-KDATE    ,
        // 014500                                        WK-RPT-PDATE.

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        batchDate = getrocdate(parse.string2Integer(textMap.get("DATE7"))); // TODO: 待確認BATCH參數名稱
        batchDateYM = batchDate.substring(0, 5);

        // 讀檔路徑
        ////
        // 014600     CHANGE ATTRIBUTE FILENAME OF FD-CLBAF TO WK-CLBAFDIR.
        // 003100  01 WK-CLBAFDIR.
        // 003200     03 FILLER                          PIC X(18)
        // 003300                         VALUE "DATA/CL/BH/CLBAF2/".
        // 003400     03 WK-KDATE                        PIC 9(07).
        // 003500     03 FILLER                          PIC X(01)
        // 003600                                        VALUE ".".
        fileInputName = batchDate;
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + fileInputName;
        // 產檔路徑
        outputFilePath = fileDir + FILE_NAME;
        // 刪除舊檔
        textFile.deleteFile(outputFilePath);
        // 檔案內容
        fileC0892Contents = new ArrayList<>();
    }

    private void toWriteC0892File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr toWriteC0892File ....");

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
            // 016800     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
            pageSWH();
            //// 若第一筆開始累加筆數金額
            //// 否則寫報表明細
            // 016900     IF FD-CLBAF-ENTPNO = WK-SUB1-ENTPNO
            // 017000     OR WK-SUB1-ENTPNO = SPACES
            if (fdClbafEntpno.equals(wkSubEntpno) || wkSubEntpno.trim().isEmpty()) {
                // 017100        PERFORM 3333-COUNT-RTN   THRU   3333-COUNT-EXIT
                count();
            } else {
                // 017300        PERFORM 5000-DTLIN-RTN   THRU   5000-DTLIN-EXIT.
                dtlin();
            }

            // 017500     MOVE     FD-CLBAF-PBRNO      TO     WK-PRE-PBRNO.
            wkPrePbrno = fdClbafPbrno;

            // 016300     READ    FD-CLBAF  AT END    GO TO  0000-MAIN-LFAST   .
            if (cnt == lines.size()) {
                // 檔尾,寫明細、表尾
                // 017900     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT.
                // 018000     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT.
                dtlin();
                subTail();
                // 018100 0000-MAIN-EXIT.
            }

            // 017700     GO TO   0000-MAIN-LOOP.
        }
    }

    private void pageSWH() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr pageSWH ....");
        // 020100 3000-PAGESWH-RTN.

        // 020200*  換主辦行或 50 筆換頁
        // 020300     IF    (    WK-PRE-PBRNO        =      0        )
        if (wkPrePbrno == 0) {
            // 020400       ADD      1                   TO     WK-RPT-COUNT
            // 020500       MOVE     FD-CLBAF-PBRNO      TO     WK-RPT-PBRNO
            // 020600       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            wkRptCount = wkRptCount + 1;
            wkRptPbrno = fdClbafPbrno;
            title(PAGE_SEPARATOR);

            // 020700       GO TO 3000-PAGESWH-EXIT.
            return;
        }
        // 020900     IF    (    FD-CLBAF-PBRNO         NOT =  WK-PRE-PBRNO  )
        // 021000       AND (    WK-PRE-PBRNO        NOT =  0             )
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C0892Lsnr fdClbafPbrno = {},wkPrePbrno = {}",
                fdClbafPbrno,
                wkPrePbrno);

        if (fdClbafPbrno != wkPrePbrno && wkPrePbrno != 0) {
            // 021100       PERFORM 5000-DTLIN-RTN       THRU   5000-DTLIN-EXIT
            dtlin();

            // 021200       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subTail();

            // 021300       MOVE     SPACES              TO     REPORT-LINE
            // 021400       WRITE    REPORT-LINE         AFTER  PAGE
            // 021500       MOVE     FD-CLBAF-PBRNO      TO     WK-RPT-PBRNO
            // 021600       MOVE     1                   TO     WK-RPT-COUNT
            wkRptPbrno = fdClbafPbrno;
            wkRptCount = wkRptCount + 1;

            // 021700       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title(PAGE_SEPARATOR);
            // 021800       GO TO 3000-PAGESWH-EXIT.
            return;
        }
        // 022000     IF    (    WK-RECCNT           >      50       )
        if (wkReccnt > 50) {
            // 022100       MOVE     SPACES              TO     REPORT-LINE
            // 022200       WRITE    REPORT-LINE         AFTER  PAGE
            // 022300       MOVE     1                   TO     WK-RECCNT
            // 022400       ADD      1                   TO     WK-RPT-COUNT
            wkReccnt = 1;
            wkRptCount = wkRptCount + 1;

            // 022500       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title(PAGE_SEPARATOR);

            // 022600       GO TO 3000-PAGESWH-EXIT.
            return;
        }
    }

    private void title(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr title ....");
        // 018400 2000-TITLE-RTN.
        //
        /// 寫報表表頭
        //
        // 018500     MOVE       SPACES              TO     REPORT-LINE.
        // 018600     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(45) VALUE SPACE.
        // 004300    02 FILLER                          PIC X(60) VALUE
        // 004400       " 代收類別使用趨勢統計報表－依特定客戶 ".
        // 004500    02 FILLER                          PIC X(22) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        sb.append(formatUtil.padX(" ", 45));
        sb.append(formatUtil.padX(" 代收類別使用趨勢統計報表－依特定客戶 ", 60));
        sb.append(formatUtil.padX(" ", 22));
        fileC0892Contents.add(sb.toString());

        // 018700     MOVE       SPACES              TO     REPORT-LINE.
        // 018800     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileC0892Contents.add("");

        // 018900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004700 01 WK-TITLE-LINE2.
        // 004800    02 FILLER                          PIC X(2) VALUE SPACE.
        // 004900    02 FILLER                          PIC X(18) VALUE
        // 005000       " 報表名稱： C089-2".
        // 005100    02 FILLER                          PIC X(100) VALUE SPACE.
        // 005200    02 FILLER                          PIC X(12) VALUE
        // 005300       " 頁　　次： ".
        // 005400    02 WK-RPT-COUNT                    PIC 9(03).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 報表名稱： C089-2", 18));
        sb.append(formatUtil.padX(" ", 100));
        sb.append(formatUtil.padX(" 頁　　次： ", 12));
        sb.append(formatUtil.pad9("" + wkRptCount, 1));
        fileC0892Contents.add(sb.toString());

        // 019000     MOVE       SPACES              TO     REPORT-LINE.
        // 019100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
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
        fileC0892Contents.add(sb.toString());

        // 019200     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        // 006700 01 WK-TITLE-LINE4.
        // 006800    02 FILLER                          PIC X(2) VALUE SPACE.
        // 006900    02 FILLER                          PIC X(12) VALUE
        // 007000       " 印表日期： ".
        // 007100    02 WK-RPT-PDATE                    PIC Z99/99/99.
        // 007200    02 FILLER                          PIC X(97) VALUE SPACE.
        // 007300    02 FILLER                          PIC X(16) VALUE
        // 007400       " 保存年限：５年 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(reportUtil.customFormat(batchDate, "Z99/99/99"));
        sb.append(formatUtil.padX(" ", 97));
        sb.append(formatUtil.padX(" 保存年限：５年 ", 16));
        fileC0892Contents.add(sb.toString());

        // 019300     MOVE       SPACES              TO     REPORT-LINE.
        // 019400     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileC0892Contents.add("");

        // 019500     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE5.
        // 007600 01 WK-TITLE-LINE5.
        // 007700    02 FILLER              PIC X(01) VALUE SPACES      .
        // 007900    02 FILLER              PIC X(10) VALUE "   統編   ".
        // 008000    02 FILLER              PIC X(03) VALUE SPACES      .
        // 008100    02 FILLER              PIC X(20) VALUE " 戶名 ".
        // 008200    02 FILLER              PIC X(12) VALUE " 上半年筆數 ".
        // 008300    02 FILLER              PIC X(12) VALUE " 本半年筆數 ".
        // 008400    02 FILLER   PIC X(20) VALUE " 半年筆數增減比 ".
        // 008500    02 FILLER              PIC X(08) VALUE SPACES      .
        // 008600    02 FILLER              PIC X(12) VALUE " 上半年金額 ".
        // 008700    02 FILLER              PIC X(06) VALUE SPACES      .
        // 008800    02 FILLER              PIC X(12) VALUE " 本半年金額 ".
        // 008900    02 FILLER              PIC X(04) VALUE SPACES       .
        // 009000    02 FILLER   PIC X(20) VALUE " 半年金額增減比 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX("   統編   ", 10));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 戶名 ", 22));
        sb.append(formatUtil.padX(" 上半年筆數 ", 12));
        sb.append(formatUtil.padX(" 本半年筆數 ", 12));
        sb.append(formatUtil.padX(" 半年筆數增減比 ", 20));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(formatUtil.padX(" 上半年金額 ", 12));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX(" 本半年金額 ", 12));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(" 半年金額增減比 ", 20));
        fileC0892Contents.add(sb.toString());

        // 019600     MOVE       SPACES              TO     REPORT-LINE.
        // 019700     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        // 011300 01 WK-GATE-LINE.
        // 011400    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileC0892Contents.add(sb.toString());

        // 019800 2000-TITLE-EXIT.
    }

    private void count() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr count ....");
        // 030200 3333-COUNT-RTN.
        //// 如果統編不是空白,搬統編資料

        // 030400     IF  WK-SUB1-ENTPNO = SPACES
        if (wkSubEntpno.trim().isEmpty()) {
            // 030500         MOVE     FD-CLBAF-ENTPNO    TO    WK-SUB1-ENTPNO.
            wkSubEntpno = fdClbafEntpno;
        }
        //// 累加筆數金額,搬單位中文
        // 030900     IF FD-CLBAF-DATE(2:5)   NOT=    WK-TASK-DATE(1:5)
        if (!fdClbafDateYM.equals(batchDateYM)) {
            // 031000        ADD       FD-CLBAF-CNT       TO    WK-SUB1-CNT
            // 031100        ADD       FD-CLBAF-AMT       TO    WK-SUB1-AMT
            // 031200        MOVE      FD-CLBAF-CNAME     TO    WK-SUB1-CNAME
            wkSub1Cnt = wkSub1Cnt.add(fdClbafCnt);
            wkSub1Amt = wkSub1Amt.add(fdClbafAmt);
            wkSubCName = fdClbafCName;
        } else {
            // 031400        ADD       FD-CLBAF-CNT       TO    WK-SUB2-CNT
            // 031500        ADD       FD-CLBAF-AMT       TO    WK-SUB2-AMT
            // 031600        MOVE      FD-CLBAF-CNAME     TO    WK-SUB1-CNAME
            wkSub2Cnt = wkSub2Cnt.add(fdClbafCnt);
            wkSub2Amt = wkSub2Amt.add(fdClbafAmt);
            wkSubCName = fdClbafCName;
            // 031700     END-IF.
        }

        // 032500 3333-COUNT-EXIT.
    }

    private void dtlin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr dtlin ....");
        // 024100 5000-DTLIN-RTN.
        // 寫報表明細

        // 024300* 一般狀況
        // 024400     IF    WK-SUB1-CNT >  0
        if (wkSub1Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 024500       COMPUTE   WK-RPT-CNTP =   WK-SUB2-CNT / WK-SUB1-CNT * 100
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
        // 024600     ELSE  IF WK-SUB2-CNT >  0
        else if (wkSub2Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 024700       MOVE    999.9          TO    WK-RPT-CNTP.
            wkRptCntP = new BigDecimal(999.99);
        }
        // 024800     IF    WK-SUB1-AMT >  0
        if (wkSub1Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 024900       COMPUTE   WK-RPT-AMTP =   WK-SUB2-AMT / WK-SUB1-AMT * 100
            if (wkSub2Amt.compareTo(BigDecimal.ZERO) == 0
                    || wkSub1Amt.compareTo(BigDecimal.ZERO) == 0) {
                wkRptAmtP = BigDecimal.ZERO;
            } else {
                wkRptAmtP =
                        wkSub2Amt
                                .divide(wkSub1Amt, 1, RoundingMode.DOWN)
                                .multiply(new BigDecimal(100));
            }
        }
        // 025000     ELSE   IF WK-SUB2-AMT >  0
        else if (wkSub2Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 025100       MOVE    999.9          TO    WK-RPT-AMTP.
            wkRptAmtP = new BigDecimal(999.99);
        }
        // 025200* 修正減少時的狀況
        // 025300     IF   (WK-SUB2-CNT < WK-SUB1-CNT) OR WK-SUB1-CNT > 0
        if (wkSub2Cnt.compareTo(wkSub1Cnt) < 0 || wkSub1Cnt.compareTo(BigDecimal.ZERO) > 0) {
            // 025400       MOVE WK-RPT-CNTP    TO   WK-TEMP-CNTP
            // 025500       COMPUTE WK-RPT-CNTP  =  WK-TEMP-CNTP - 100
            wkTempCntP = wkRptCntP;
            wkRptCntP = wkTempCntP.subtract(new BigDecimal(100));
            // 025600     END-IF.
        }
        // 025700     IF   (WK-SUB2-AMT < WK-SUB1-AMT) OR WK-SUB1-AMT > 0
        if (wkSub2Amt.compareTo(wkSub1Amt) < 0 || wkSub1Amt.compareTo(BigDecimal.ZERO) > 0) {
            // 025800       MOVE WK-RPT-AMTP    TO   WK-TEMP-AMTP
            // 025900       COMPUTE WK-RPT-AMTP  =  WK-TEMP-AMTP - 100
            wkTempAmtP = wkRptAmtP;
            wkRptAmtP = wkTempAmtP.subtract(new BigDecimal(100));
            // 026000     END-IF.
        }
        // 026100* 修正增加時最大值
        // 026200     COMPUTE WK-TEMP-CNT  =  WK-SUB2-CNT / 10
        if (wkSub2Cnt.compareTo(BigDecimal.ZERO) == 0) {
            wkTempCnt = BigDecimal.ZERO;
        } else {
            wkTempCnt = wkSub2Cnt.divide(new BigDecimal(10), 1, RoundingMode.DOWN);
        }
        // 026300     IF WK-TEMP-CNT    NOT<  WK-SUB1-CNT
        if (wkTempCnt.compareTo(wkSub1Cnt) >= 0) {
            // 026400        MOVE    999.9          TO    WK-RPT-CNTP.
            wkRptCntP = new BigDecimal(999.99);
        }
        // 026500     COMPUTE WK-TEMP-AMT  =  WK-SUB2-AMT / 10
        if (wkSub2Amt.compareTo(BigDecimal.ZERO) == 0) {
            wkTempAmt = BigDecimal.ZERO;
        } else {
            wkTempAmt = wkSub2Amt.divide(new BigDecimal(10));
        }
        // 026600     IF WK-TEMP-AMT    NOT<  WK-SUB1-AMT
        if (wkTempAmt.compareTo(wkSub1Amt) >= 0) {
            // 026700        MOVE    999.9          TO    WK-RPT-AMTP.
            wkRptAmtP = new BigDecimal(999.99);
        }
        // 026800* 取絕對值，大於百分之３０或小於－３０
        // 026900     MOVE  WK-RPT-CNTP     TO    WK-CNTPS.
        // 027000     MOVE  WK-RPT-AMTP     TO    WK-AMTPS.
        wkCntps = wkRptCntP;
        wkAmtps = wkRptAmtP;
        // 027200     IF WK-CNTPS  >  29.9  OR  WK-AMTPS > 29.9
        if (wkCntps.compareTo(new BigDecimal(29.9)) > 0
                || wkCntps.compareTo(new BigDecimal(-29.9)) < 0
                || wkAmtps.compareTo(new BigDecimal(29.9)) > 0
                || wkAmtps.compareTo(new BigDecimal(-29.9)) < 0) {
            // 027500        CONTINUE
        } else {
            // 027700        GO  TO  5000-DTLIN-EXIT.
            return;
        }
        // 027800     MOVE      WK-SUB1-CNAME   TO    WK-RPT-CNAME   .
        // 027900     MOVE      WK-SUB1-ENTPNO  TO    WK-RPT-ENTPNO  .
        // 028000     MOVE      WK-SUB1-CNT     TO    WK-RPT-CNT1    .
        // 028100     MOVE      WK-SUB1-AMT     TO    WK-RPT-AMT1    .
        // 028200     MOVE      WK-SUB2-CNT     TO    WK-RPT-CNT2    .
        // 028300     MOVE      WK-SUB2-AMT     TO    WK-RPT-AMT2    .
        // 028400     WRITE     REPORT-LINE     FROM  WK-DETAIL-LINE.
        // 009200 01 WK-DETAIL-LINE.
        // 009300    02 FILLER                          PIC X(01) VALUE SPACE.
        // 009400    02 WK-RPT-ENTPNO                   PIC 9(08).
        // 009500    02 FILLER                          PIC X(01) VALUE SPACE.
        // 009600    02 WK-RPT-CNAME                    PIC X(22).
        // 009700    02 FILLER                          PIC X(02) VALUE SPACE.
        // 009800    02 WK-RPT-CNT1                     PIC ZZZ,ZZZ,ZZ9.
        // 009900    02 FILLER                          PIC X(01) VALUE SPACES.
        // 010000    02 WK-RPT-CNT2                     PIC ZZZ,ZZZ,ZZ9.
        // 010100    02 FILLER                          PIC X(08) VALUE SPACE.
        // 010200    02 WK-RPT-CNTP                     PIC -ZZ9.9.
        // 010300    02 FILLER                          PIC X(01) VALUE "%".
        // 010400    02 FILLER                          PIC X(08) VALUE SPACE.
        // 010500    02 WK-RPT-AMT1                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        // 010600    02 FILLER                          PIC X(01) VALUE SPACES.
        // 010700    02 WK-RPT-AMT2                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        // 010800    02 FILLER                          PIC X(12) VALUE SPACE.
        // 010900    02 WK-RPT-AMTP                     PIC -ZZ9.9.
        // 011000    02 FILLER                          PIC X(01) VALUE "%".
        // 011100    02 FILLER                          PIC X(02) VALUE SPACE.
        // 011200    02 FILLER                          PIC X(02) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.pad9(wkSubEntpno, 8));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(wkSubCName.trim(), 22));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(reportUtil.customFormat("" + wkSub1Cnt, "ZZZ,ZZZ,ZZ9"));
        //        sb.append(String.format("%11s", amtFormat.format(wkSub1Cnt)));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(reportUtil.customFormat("" + wkSub2Cnt, "ZZZ,ZZZ,ZZ9"));
        //        sb.append(String.format("%11s", amtFormat.format(wkSub2Cnt)));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(String.format("%6s", amt2Format.format(wkRptCntP)));
        sb.append(formatUtil.padX("%", 1));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(reportUtil.customFormat("" + wkSub1Amt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(reportUtil.customFormat("" + wkSub2Amt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(String.format("%6s", amt2Format.format(wkRptAmtP)));
        sb.append(formatUtil.padX("%", 1));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" ", 2));
        fileC0892Contents.add(sb.toString());

        // 028500     MOVE      SPACES          TO    WK-SUB1-ENTPNO  .
        // 028600     MOVE      0               TO    WK-SUB1-CNT ,WK-SUB2-CNT
        // 028700                                     WK-SUB1-AMT ,WK-SUB2-AMT.
        // 028800     ADD       1               TO    WK-RECCNT      .
        wkSubEntpno = "";
        wkSub1Cnt = BigDecimal.ZERO;
        wkSub2Cnt = BigDecimal.ZERO;
        wkSub1Amt = BigDecimal.ZERO;
        wkSub2Amt = BigDecimal.ZERO;
        wkReccnt = wkReccnt + 1;

        // 028900     PERFORM 3333-COUNT-RTN   THRU   3333-COUNT-EXIT.
        count();
        // 029000 5000-DTLIN-EXIT.
    }

    private void subTail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0892Lsnr subTail ....");
        // 023100 4000-SUBTAIL-RTN.
        //// 寫報表表尾

        // 023200     MOVE       WK-RECCNT      TO    WK-RPT-SUBCNT .

        // 023300     MOVE       SPACES         TO    REPORT-LINE   .
        // 023400     WRITE      REPORT-LINE    FROM  WK-GATE-LINE  .
        // 011300 01 WK-GATE-LINE.
        // 011400    02 FILLER                   PIC X(135) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 135));
        fileC0892Contents.add(sb.toString());

        // 023500     MOVE       SPACES         TO    REPORT-LINE   .
        // 023600     WRITE      REPORT-LINE    FROM  WK-SUBTOTAL-LINE .
        // 011500 01 WK-SUBTOTAL-LINE.
        // 011600    02 FILLER                   PIC X(02) VALUE SPACES.
        // 011700    02 FILLER                   PIC X(11) VALUE " 分行小計 :".
        // 011800    02 WK-RPT-SUBCNT            PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 分行小計 ", 11));
        sb.append(reportUtil.customFormat("" + wkReccnt, "ZZZ,ZZZ,ZZ9"));
        fileC0892Contents.add(sb.toString());

        // 023700     MOVE       0              TO    WK-RECCNT.
        wkReccnt = 0;

        // 023800 4000-SUBTAIL-EXIT.
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
