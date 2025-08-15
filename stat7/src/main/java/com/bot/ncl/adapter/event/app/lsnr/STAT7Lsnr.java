/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT7;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("STAT7Lsnr")
@Scope("prototype")
public class STAT7Lsnr extends BatchListenerCase<STAT7> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private TextFileUtil textFileSTAT7;
    @Autowired private TextFileUtil textFileSORTTMP;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmrService clmrService;
    @Autowired private ExternalSortUtil externalSortUtil;

    private Map<String, String> textMap;
    // 批次日期
    private String wkYYYMM;
    int wkSDate = 0;
    int wkEDate = 0;
    private int entdy = 0;
    private STAT7 event;

    private static final String CHARSET = "Big5";
    private String fileNameSTAT7 = "CL-BH-057";
    private String fileNameKPUTH = "KPUTH";
    private String fileNameTmp = "tmpKPUTH";

    private String fileNameTmp0 = "tmpKPUTH0";
    private String readFilePath = "";

    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT7;

    private List<String> fileContentsSORTTMP;

    private List<String> lines = new ArrayList<String>();

    private int wkRptCount = 0;
    private int wkRecCnt = 0;
    private int wkTotCnt = 0;
    private BigDecimal wkTotAmt = BigDecimal.ZERO;

    private int wkRptPbrno = 0;
    private int wkPrePbrno = 999;
    int wkRpt_C_Cnt = 0;
    private int wk_K_Cnt = 0;
    private BigDecimal wk_K_Amt = BigDecimal.ZERO;
    private int wk_T_Cnt = 0;
    private BigDecimal wk_T_Amt = BigDecimal.ZERO;
    private int wk_UA06_Cnt = 0;
    private BigDecimal wk_UA06_Amt = BigDecimal.ZERO;
    private int wk_UA15_Cnt = 0;
    private BigDecimal wk_UA15_Amt = BigDecimal.ZERO;
    private int wk_UB05_Cnt = 0;
    private BigDecimal wk_UB05_Amt = BigDecimal.ZERO;
    private int wk_UB10_Cnt = 0;
    private BigDecimal wk_UB10_Amt = BigDecimal.ZERO;
    private int wk_UB15_Cnt = 0;
    private BigDecimal wk_UB15_Amt = BigDecimal.ZERO;
    private int wk_C_Cnt = 0;
    private BigDecimal wk_C_Amt = BigDecimal.ZERO;
    private int wk_EFCS_Cnt = 0;
    private BigDecimal wk_EFCS_Amt = BigDecimal.ZERO;
    private int wk_LINE_Cnt = 0;
    private BigDecimal wk_LINE_Amt = BigDecimal.ZERO;
    private String wkPreCode = "";

    private String wkRptCode = "";
    private String wkRptSmserno = "";

    private BigDecimal wkRpt_C_Amt = BigDecimal.ZERO;

    private int wkRpt_K_Cnt = 0;

    private BigDecimal wkRpt_K_Amt = BigDecimal.ZERO;

    private int wkRpt_T_Cnt = 0;

    private BigDecimal wkRpt_T_Amt = BigDecimal.ZERO;

    private int wkRpt_EFCS_Cnt = 0;

    private BigDecimal wkRpt_EFCS_Amt = BigDecimal.ZERO;

    private int wkRpt_LINE_Cnt = 0;

    private BigDecimal wkRpt_LINE_Amt = BigDecimal.ZERO;

    private int wkRpt_UB05_Cnt = 0;

    private BigDecimal wkRpt_UB05_Amt = BigDecimal.ZERO;

    private int wkRpt_UB10_Cnt = 0;

    private BigDecimal wkRpt_UB10_Amt = BigDecimal.ZERO;

    private int wkRpt_UB15_Cnt = 0;

    private BigDecimal wkRpt_UB15_Amt = BigDecimal.ZERO;

    private int wkRpt_UA06_Cnt = 0;

    private BigDecimal wkRpt_UA06_Amt = BigDecimal.ZERO;

    private int wkRpt_UA15_Cnt = 0;

    private BigDecimal wkRpt_UA15_Amt = BigDecimal.ZERO;

    private int wkRptTotCnt = 0;
    private BigDecimal wkRptTotAmt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT7 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT7Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT7 event) {
        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT7Lsnr run()");

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkYYYMM = textMap.get("WK-TASK-DATE"); // TODO: 待確認BATCH參數名稱
        entdy = parse.string2Integer(wkYYYMM);

        sortFileBefore();
        // 將檔案 KPUTH 排序放到暫存檔
        sortFile();

        // 讀取暫存檔
        lines = textFileSTAT7.readFileContent(fileDir + fileNameTmp, "UTF-8");
        if (Objects.isNull(lines)) {
            return;
        }

        fileContentsSTAT7 = new ArrayList<String>();

        _0000_MAIN(lines);

        writeFile();
    }

    // 還沒寫
    private void initWK() {

        // 018200     MOVE    0   TO                 WK-RPT-COUNT,WK-RECCNT   ,
        // 018300                       WK-TOTCNT   ,WK-TOTAMT   ,WK-PRE-PBRNO,
        // 018400                       WK-K-CNT    ,WK-T-CNT    ,WK-UA06-CNT ,
        // 018500                       WK-K-AMT    ,WK-T-AMT    ,WK-UA06-AMT ,
        // 018600                       WK-UA15-CNT ,WK-UB05-CNT ,WK-UB10-CNT ,
        // 018700                       WK-UA15-AMT ,WK-UB05-AMT ,WK-UB10-AMT ,
        // 018800                       WK-UB15-CNT ,WK-C-CNT    ,WK-EFCS-CNT ,
        // 018900                       WK-UB15-AMT ,WK-C-AMT    ,WK-EFCS-AMT ,
        // 018950                       WK-LINE-CNT ,WK-LINE-AMT .

        wkRptCount = 0;
        wkRecCnt = 0;
        wkTotCnt = 0;
        wkTotAmt = BigDecimal.ZERO;
        wkPrePbrno = 999;
        wk_K_Cnt = 0;
        wk_K_Amt = BigDecimal.ZERO;
        wk_T_Cnt = 0;
        wk_T_Amt = BigDecimal.ZERO;
        wk_UA06_Cnt = 0;
        wk_UA06_Amt = BigDecimal.ZERO;
        wk_UA15_Cnt = 0;
        wk_UA15_Amt = BigDecimal.ZERO;
        wk_UB05_Cnt = 0;
        wk_UB05_Amt = BigDecimal.ZERO;
        wk_UB10_Cnt = 0;
        wk_UB10_Amt = BigDecimal.ZERO;
        wk_UB15_Cnt = 0;
        wk_UB15_Amt = BigDecimal.ZERO;
        wk_C_Cnt = 0;
        wk_C_Amt = BigDecimal.ZERO;
        wk_EFCS_Cnt = 0;
        wk_EFCS_Amt = BigDecimal.ZERO;
        wk_LINE_Cnt = 0;
        wk_LINE_Amt = BigDecimal.ZERO;

        // 019100     MOVE    SPACES              TO     WK-PRE-CODE  .
        wkPreCode = "";
        //// 開啟檔案
        //
        // 019200     OPEN    OUTPUT    REPORTFL.
        // 019300     OPEN    INPUT     FD-KPUTH.
    }

    private void sortFileBefore() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortFileBefore");

        fileContentsSORTTMP = new ArrayList<String>();

        lines = textFileSORTTMP.readFileContent(fileDir + fileNameKPUTH, "UTF-8");
        for (String r : lines) {
            this.log("R.subSTRING", r.substring(r.length() - 22, r.length() - 19));
            sb = new StringBuilder();
            sb.append(r, r.length() - 22, r.length() - 19); // 0~3
            sb.append(r, 10, 16); // 3~9

            sb.append(r);
            fileContentsSORTTMP.add(sb.toString());
        }

        textFileSORTTMP.deleteFile(fileDir + fileNameTmp0);
        try {
            textFileSORTTMP.writeFileContent(fileDir + fileNameTmp0, fileContentsSORTTMP, "UTF-8");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void sortFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortFile");
        File tmpFile = new File(fileDir + fileNameTmp0);
        File tmpFileOut = new File(fileDir + fileNameTmp);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(3, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFileOut, keyRanges, "UTF-8");
    }

    private void _0000_MAIN(List<String> lines) {
        initWK();
        for (String r : lines) {
            _3000_PAGESWH(r);
            _3100_CODESWH(r);
            _5000_DTLIN(r);
        }
        _4000_SUBTAIL(true);
    }

    private void _3000_PAGESWH(String r) {
        // 022600 3000-PAGESWH-RTN.
        // 022700*  換主辦行或滿 50 行換頁
        // 022800*    DISPLAY  KPUTH-PBRNO .
        // 022900*    DISPLAY  WK-PRE-PBRNO.
        // 023000*    WAIT(02).
        //
        //// 首筆
        ////  A.搬KPUTH-PBRNO、KPUTH-CODE給控制變數WK-PRE-... & 報表
        ////  B.執行2000-TITLE-RTN，寫表頭
        ////  C.執行2100-TITLE-RTN，寫表頭
        ////  D.結束本段落
        // 023100     IF    (    WK-PRE-PBRNO        =      000      )
        // 023200       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
        // 023300                                           WK-RPT-PBRNO
        // 023400       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
        // 023500       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
        // 023600       PERFORM  2100-TITLE-RTN      THRU   2100-TITLE-EXIT
        // 023700       GO TO 3000-PAGESWH-EXIT.

        this.log("Pbrno", r.substring(0, 3));
        this.log("wkPreCode", r.substring(3, 9));

        if ("999".equals(wkPrePbrno + "")) {

            wkPrePbrno = parse.string2Integer(r.substring(0, 3));
            wkRptPbrno = wkPrePbrno;
            wkRptCode = r.substring(3, 9);
            wkPreCode = r.substring(3, 9);

            _2000_TITLE();
            _2100_TITLE();

        }
        // 023800
        // 023900
        // 024000

        //// 主辦行不同時
        ////  A.執行4000-SUBTAIL-RTN，寫報表明細
        ////  B.搬KPUTH-PBRNO、KPUTH-CODE給控制變數WK-PRE-... & 報表
        ////  C.換頁
        ////  D.執行2000-TITLE-RTN，寫表頭
        ////  E.執行2100-TITLE-RTN，寫表頭
        ////  F.結束本段落

        // 024100     IF    (    KPUTH-PBRNO         NOT =  WK-PRE-PBRNO )
        // 024200       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
        // 024300       MOVE     KPUTH-CODE          TO     WK-PRE-CODE ,
        // 024400                                           WK-RPT-CODE
        // 024500       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
        // 024600                                           WK-RPT-PBRNO
        // 024700       MOVE     SPACES              TO     REPORT-LINE
        // 024800       WRITE    REPORT-LINE         AFTER  PAGE
        // 024900       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
        // 025000       PERFORM  2100-TITLE-RTN      THRU   2100-TITLE-EXIT
        // 025100       GO TO 3000-PAGESWH-EXIT.
        else if (wkRptPbrno != parse.string2Integer(r.substring(0, 3))) {
            _4000_SUBTAIL(false);

            wkPreCode = r.substring(3, 9);
            wkRptCode = r.substring(3, 9);
            wkPrePbrno = parse.string2Integer(r.substring(0, 3));
            wkRptPbrno = wkPrePbrno;

            sb = new StringBuilder();
            sb.append("\u000c");
            fileContentsSTAT7.add(sb.toString());

            _2000_TITLE();
            _2100_TITLE();
        }
        // 025200
        // 025300 3000-PAGESWH-EXIT.
    }

    private void _2000_TITLE() {
        // 020600 2000-TITLE-RTN.
        //
        //// 寫REPORTFL報表表頭(WK-TITLE-LINE1~WK-TITLE-LINE3)
        //
        // 020700     MOVE       SPACES              TO     REPORT-LINE   .
        // 020800     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX("", 85));
        sb.append(formatUtil.padX(" 外部代收－代收類別管道彙記月報表 ", 36));
        sb.append(formatUtil.padX("", 91));
        sb.append(formatUtil.padX("FORM : C057 ", 12));
        fileContentsSTAT7.add(sb.toString());
        // 020900     MOVE       SPACES              TO     REPORT-LINE   .
        // 021000     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        sb = new StringBuilder();
        String today = reportUtil.customFormat(entdy + "", "Z99/99/99");
        sb.append(formatUtil.padX(" ", 207));
        sb.append(formatUtil.padX("印表日期 :", 12));
        sb.append(today);
        fileContentsSTAT7.add(sb.toString());
        // 021100     MOVE       SPACES              TO     REPORT-LINE   .
        // 021200     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 主辦行： ", 10));
        sb.append(formatUtil.pad9(wkRptPbrno + "", 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 年月份： ", 10));
        sb.append(formatUtil.padX(today.substring(0, 6), 7));
        sb.append(formatUtil.padX("", 65));
        fileContentsSTAT7.add(sb.toString());
        // 021300 2000-TITLE-EXIT.

    }

    private void _2100_TITLE() {
        // 021600 2100-TITLE-RTN.
        //
        //// 寫REPORTFL報表表頭(WK-TITLE-LINE4~WK-TITLE-LINE5)
        //
        // 021700     MOVE       SPACES              TO     REPORT-LINE   .
        // 021800     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收 ", 6));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 行內平台 ", 10));
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.padX(" 超商 ", 6));
        sb.append(formatUtil.padX("", 22));
        sb.append(formatUtil.padX(" 信用卡 ", 8));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" ＥＦＣＳ ", 10));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("ThirdParty", 10));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 郵局一  6 元 ", 14));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 郵局一 15 元 ", 14));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 郵局二  5 元 ", 14));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 郵局二 10 元 ", 14));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 郵局二 15 元 ", 14));
        fileContentsSTAT7.add(sb.toString());
        // 021900     MOVE       SPACES              TO     REPORT-LINE   .
        // 022000     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE5.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 類別 ", 6));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 筆數           金額 ", 21));
        sb.append(formatUtil.padX(" 代號 ", 6));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 筆數　　　　　金額 ", 20));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX(" 筆數        金額 ", 18));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 總筆數 ", 8));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        fileContentsSTAT7.add(sb.toString());
        // 022100     MOVE       SPACES              TO     REPORT-LINE   .
        // 022200     WRITE      REPORT-LINE         FROM   WK-GATE-LINE  .
        sb = new StringBuilder();
        sb.append("-".repeat(230));
        fileContentsSTAT7.add(sb.toString());
        // 022300 2100-TITLE-EXIT.

    }

    private void _3100_CODESWH(String r) {
        // 025600 3100-CODESWH-RTN.
        // 025700*    DISPLAY "KPUTH-CODE " KPUTH-CODE.
        // 025800*    WAIT(02).
        //
        //// 代收類別不同 且 WK-PRE-CODE<>空白 時
        ////  A.執行4000-SUBTAIL-RTN，寫報表明細
        ////  B.搬KPUTH-CODE給控制變數WK-PRE-CODE
        ////  C.結束本段落
        //
        // 025900     IF    (    KPUTH-CODE          NOT =  WK-PRE-CODE  )
        // 026000       AND (    WK-PRE-CODE         NOT =  SPACES       )
        // 026100       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
        // 026200       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
        // 026300       GO TO 3100-CODESWH-EXIT.

        if (!wkPreCode.equals(r.substring(3, 9))) {
            _4000_SUBTAIL(true);
            wkPreCode = r.substring(3, 9);
        }

        // 026400 3100-CODESWH-EXIT.
    }

    private void _4000_SUBTAIL(boolean isChangeCode) {
        // 026700 4000-SUBTAIL-RTN.
        //// 搬小計到報表
        //// 累計小計到合計
        //// 搬合計到報表
        // 026800     MOVE       WK-C-CNT        TO   WK-RPT-C-CNT .
        // 026900     ADD        WK-C-CNT        TO   WK-TOTCNT    .
        // 027000     MOVE       WK-C-AMT        TO   WK-RPT-C-AMT .
        // 027100     ADD        WK-C-AMT        TO   WK-TOTAMT    .
        // 027200     MOVE       WK-K-CNT        TO   WK-RPT-K-CNT .
        // 027300     ADD        WK-K-CNT        TO   WK-TOTCNT    .
        // 027400     MOVE       WK-K-AMT        TO   WK-RPT-K-AMT .
        // 027500     ADD        WK-K-AMT        TO   WK-TOTAMT    .
        // 027600     MOVE       WK-T-CNT        TO   WK-RPT-T-CNT .
        // 027700     ADD        WK-T-CNT        TO   WK-TOTCNT    .
        // 027800     MOVE       WK-T-AMT        TO   WK-RPT-T-AMT .
        // 027900     ADD        WK-T-AMT        TO   WK-TOTAMT    .
        // 027902     MOVE       WK-EFCS-CNT     TO   WK-RPT-EFCS-CNT.
        // 027904     ADD        WK-EFCS-CNT     TO   WK-TOTCNT    .
        // 027906     MOVE       WK-EFCS-AMT     TO   WK-RPT-EFCS-AMT.
        // 027908     ADD        WK-EFCS-AMT     TO   WK-TOTAMT    .
        // 027920     MOVE       WK-LINE-CNT     TO   WK-RPT-LINE-CNT.
        // 027940     ADD        WK-LINE-CNT     TO   WK-TOTCNT    .
        // 027960     MOVE       WK-LINE-AMT     TO   WK-RPT-LINE-AMT.
        // 027980     ADD        WK-LINE-AMT     TO   WK-TOTAMT    .
        // 028000     MOVE       WK-UB05-CNT     TO   WK-RPT-UB05-CNT.
        // 028100     ADD        WK-UB05-CNT     TO   WK-TOTCNT    .
        // 028200     MOVE       WK-UB05-AMT     TO   WK-RPT-UB05-AMT.
        // 028300     ADD        WK-UB05-AMT     TO   WK-TOTAMT    .
        // 028400     MOVE       WK-UB10-CNT     TO   WK-RPT-UB10-CNT.
        // 028500     ADD        WK-UB10-CNT     TO   WK-TOTCNT    .
        // 028600     MOVE       WK-UB10-AMT     TO   WK-RPT-UB10-AMT.
        // 028700     ADD        WK-UB10-AMT     TO   WK-TOTAMT    .
        // 028800     MOVE       WK-UB15-CNT     TO   WK-RPT-UB15-CNT.
        // 028900     ADD        WK-UB15-CNT     TO   WK-TOTCNT    .
        // 029000     MOVE       WK-UB15-AMT     TO   WK-RPT-UB15-AMT.
        // 029100     ADD        WK-UB15-AMT     TO   WK-TOTAMT    .
        // 029200     MOVE       WK-UA06-CNT     TO   WK-RPT-UA06-CNT.
        // 029300     ADD        WK-UA06-CNT     TO   WK-TOTCNT    .
        // 029400     MOVE       WK-UA06-AMT     TO   WK-RPT-UA06-AMT.
        // 029500     ADD        WK-UA06-AMT     TO   WK-TOTAMT    .
        // 029600     MOVE       WK-UA15-CNT     TO   WK-RPT-UA15-CNT.
        // 029700     ADD        WK-UA15-CNT     TO   WK-TOTCNT    .
        // 029800     MOVE       WK-UA15-AMT     TO   WK-RPT-UA15-AMT.
        // 029900     ADD        WK-UA15-AMT     TO   WK-TOTAMT    .
        // 030000
        // 030100     MOVE       WK-TOTCNT       TO   WK-RPT-TOTCNT.
        // 030200     MOVE       WK-TOTAMT       TO   WK-RPT-TOTAMT.
        // 030300
        wkRpt_C_Cnt = wk_C_Cnt;
        wkTotCnt = wkTotCnt + wk_C_Cnt;
        wkRpt_C_Amt = wk_C_Amt;
        wkTotAmt = wkTotAmt.add(wk_C_Amt);

        wkRpt_K_Cnt = wk_K_Cnt;
        wkTotCnt = wkTotCnt + wk_K_Cnt;
        wkRpt_K_Amt = wk_K_Amt;
        wkTotAmt = wkTotAmt.add(wk_K_Amt);

        wkRpt_T_Cnt = wk_T_Cnt;
        wkTotCnt = wkTotCnt + wk_T_Cnt;
        wkRpt_T_Amt = wk_T_Amt;
        wkTotAmt = wkTotAmt.add(wk_T_Amt);

        wkRpt_EFCS_Cnt = wk_EFCS_Cnt;
        wkTotCnt = wkTotCnt + wk_EFCS_Cnt;
        wkRpt_EFCS_Amt = wk_EFCS_Amt;
        wkTotAmt = wkTotAmt.add(wk_EFCS_Amt);

        wkRpt_LINE_Cnt = wk_LINE_Cnt;
        wkTotCnt = wkTotCnt + wk_LINE_Cnt;
        wkRpt_LINE_Amt = wk_LINE_Amt;
        wkTotAmt = wkTotAmt.add(wk_LINE_Amt);

        wkRpt_UB05_Cnt = wk_UB05_Cnt;
        wkTotCnt = wkTotCnt + wk_UB05_Cnt;
        wkRpt_UB05_Amt = wk_UB05_Amt;
        wkTotAmt = wkTotAmt.add(wk_UB05_Amt);

        wkRpt_UB10_Cnt = wk_UB10_Cnt;
        wkTotCnt = wkTotCnt + wk_UB10_Cnt;
        wkRpt_UB10_Amt = wk_UB10_Amt;
        wkTotAmt = wkTotAmt.add(wk_UB10_Amt);

        wkRpt_UB15_Cnt = wk_UB15_Cnt;
        wkTotCnt = wkTotCnt + wk_UB15_Cnt;
        wkRpt_UB15_Amt = wk_UB15_Amt;
        wkTotAmt = wkTotAmt.add(wk_UB15_Amt);

        wkRpt_UA06_Cnt = wk_UA06_Cnt;
        wkTotCnt = wkTotCnt + wk_UA06_Cnt;
        wkRpt_UA06_Amt = wk_UA06_Amt;
        wkTotAmt = wkTotAmt.add(wk_UA06_Amt);

        wkRpt_UA15_Cnt = wk_UA15_Cnt;
        wkTotCnt = wkTotCnt + wk_UA15_Cnt;
        wkRpt_UA15_Amt = wk_UA15_Amt;
        wkTotAmt = wkTotAmt.add(wk_UA15_Amt);

        wkRptTotCnt = wkTotCnt;
        wkRptTotAmt = wkTotAmt;

        //// 寫REPORTFL報表明細(WK-DTL-LINE)
        //
        // 030400     MOVE       SPACES          TO   REPORT-LINE    .
        // 030500     WRITE      REPORT-LINE     FROM WK-DTL-LINE    .
        if (isChangeCode) {
            _WK_DTL_LINE();
        }
        //
        //// 清變數值
        //
        // 030600     MOVE       0  TO  WK-K-CNT    ,WK-T-CNT    ,WK-UA06-CNT ,
        // 030700                       WK-K-AMT    ,WK-T-AMT    ,WK-UA06-AMT ,
        // 030800                       WK-UA15-CNT ,WK-UB05-CNT ,WK-UB10-CNT ,
        // 030900                       WK-UA15-AMT ,WK-UB05-AMT ,WK-UB10-AMT ,
        // 031000                       WK-UB15-CNT ,WK-TOTCNT   ,WK-C-CNT    ,
        // 031100                       WK-UB15-AMT ,WK-TOTAMT   ,WK-C-AMT    ,
        // 031120                       WK-EFCS-CNT ,WK-EFCS-AMT ,WK-LINE-CNT ,
        // 031150                       WK-LINE-AMT .
        // 031200     MOVE       SPACES          TO  WK-RPT-SMSERNO  .

        // 031300 4000-SUBTAIL-EXIT.
        wk_C_Cnt = 0;
        wk_C_Amt = BigDecimal.ZERO;
        wk_EFCS_Cnt = 0;
        wk_EFCS_Amt = BigDecimal.ZERO;
        wk_LINE_Cnt = 0;
        wk_LINE_Amt = BigDecimal.ZERO;
        wk_K_Cnt = 0;
        wk_K_Amt = BigDecimal.ZERO;
        wk_T_Cnt = 0;
        wk_T_Amt = BigDecimal.ZERO;
        wk_UA06_Cnt = 0;
        wk_UA06_Amt = BigDecimal.ZERO;
        wk_UA15_Cnt = 0;
        wk_UA15_Amt = BigDecimal.ZERO;
        wk_UB05_Cnt = 0;
        wk_UB05_Amt = BigDecimal.ZERO;
        wk_UB10_Cnt = 0;
        wk_UB10_Amt = BigDecimal.ZERO;
        wk_UB15_Cnt = 0;
        wk_UB15_Amt = BigDecimal.ZERO;
        wkRptSmserno = "";
        // 019100     MOVE    SPACES              TO     WK-PRE-CODE  .
        wkPreCode = "";
    }

    private void _5000_DTLIN(String r) {
        // 031600 5000-DTLIN-RTN.
        //
        //// 搬KPUTH-CODE給報表
        //
        // 031700     MOVE   KPUTH-CODE        TO    WK-RPT-CODE.
        // 031800
        // ??????
        //// 依帳務別累計筆數、金額
        //
        // 031900     IF   KPUTH-TXTYPE = "C" OR = "M" OR = "A" OR = "R" OR
        // 032000                       = "E" OR = "V" OR = "B" OR = "I" OR
        // 032100                       = "W" OR = "H" OR = "G" OR = "F" OR
        // 032200                       = "P" OR = "J" OR = "G" OR = "D" OR
        // 032300                       = "S" OR = "1"
        // 032400        ADD     1               TO    WK-C-CNT
        // 032500        ADD     KPUTH-AMT       TO    WK-C-AMT.
        this.log("kputh1SmserNo", r.substring(73, 76));
        String kputh1SmserNo = r.substring(73, 76).trim().isEmpty() ? "" : r.substring(73, 76);
        this.log("kputh1Amt", r.substring(63, 73));
        BigDecimal kputh1Amt = new BigDecimal(r.substring(63, 73));
        this.log("kputh1Txtype", r.substring(r.length() - 29, r.length() - 28));
        this.log("wkRptCode", r.substring(3, 9));
        // 025500     MOVE  KPUTH1-CODE           TO     WK-RPT017-CODE .
        String wkRptCode = r.substring(3, 9);
        String kputh1Txtype = r.substring(r.length() - 29, r.length() - 28);
        if ("C".equals(kputh1Txtype)
                || "M".equals(kputh1Txtype)
                || "A".equals(kputh1Txtype)
                || "R".equals(kputh1Txtype)
                || "E".equals(kputh1Txtype)
                || "V".equals(kputh1Txtype)
                || "B".equals(kputh1Txtype)
                || "I".equals(kputh1Txtype)
                || "W".equals(kputh1Txtype)
                || "H".equals(kputh1Txtype)
                || "G".equals(kputh1Txtype)
                || "F".equals(kputh1Txtype)
                || "P".equals(kputh1Txtype)
                || "J".equals(kputh1Txtype)
                || "D".equals(kputh1Txtype)
                || "S".equals(kputh1Txtype)
                || "1".equals(kputh1Txtype)) {
            wk_C_Cnt = wk_C_Cnt + 1;
            wk_C_Amt = wk_C_Amt.add(kputh1Amt);
        }

        // 032600
        // 032700     IF    ( KPUTH-TXTYPE = "K" OR = "L" OR = "N" OR = "O")
        // 032800       MOVE    KPUTH-SMSERNO   TO    WK-RPT-SMSERNO
        // 032900       ADD     1               TO    WK-K-CNT
        // 033000       ADD     KPUTH-AMT       TO    WK-K-AMT.
        if ("K".equals(kputh1Txtype)
                || "L".equals(kputh1Txtype)
                || "N".equals(kputh1Txtype)
                || "O".equals(kputh1Txtype)) {
            wkRptSmserno = kputh1SmserNo;
            wk_K_Cnt = wk_K_Cnt + 1;
            wk_K_Amt = wk_K_Amt.add(kputh1Amt);
        }

        // 033100
        // 033200     IF    ( KPUTH-TXTYPE = "T" OR = "Q" OR = "X" OR = "Z"
        // 033250                       OR = "2"                           )
        // 033300       ADD     1               TO    WK-T-CNT
        // 033400       ADD     KPUTH-AMT       TO    WK-T-AMT.
        if ("T".equals(kputh1Txtype)
                || "Q".equals(kputh1Txtype)
                || "X".equals(kputh1Txtype)
                || "Z".equals(kputh1Txtype)
                || "2".equals(kputh1Txtype)) {

            wk_T_Cnt = wk_T_Cnt + 1;
            wk_T_Amt = wk_T_Amt.add(kputh1Amt);
        }

        // 033500
        // 033600     IF    ( KPUTH-TXTYPE  = "U")
        // 033700       AND ( KPUTH-SMSERNO = "198" OR = "179")
        // 033800       AND ( KPUTH-AMT  < 101  )
        // 033900       ADD     1               TO    WK-UB05-CNT
        // 034000       COMPUTE WK-UB05-AMT= WK-UB05-AMT + KPUTH-AMT -  5.
        if ("U".equals(kputh1Txtype)
                && ("198".equals(kputh1SmserNo) || "179".equals(kputh1SmserNo))
                && kputh1Amt.intValue() < 101) {
            wk_UB05_Cnt = wk_UB05_Cnt + 1;
            wk_UB05_Amt = wk_UB05_Amt.add(kputh1Amt).subtract(new BigDecimal("5"));
        }
        // 034100
        // 034200     IF    ( KPUTH-TXTYPE  = "U")
        // 034300       AND ( KPUTH-SMSERNO = "198" OR = "179")
        // 034400       AND ( KPUTH-AMT  > 101    AND   KPUTH-AMT  < 1001  )
        // 034500       ADD     1               TO    WK-UB10-CNT
        // 034600       COMPUTE WK-UB10-AMT= WK-UB10-AMT + KPUTH-AMT - 10.
        if ("U".equals(kputh1Txtype)
                && ("198".equals(kputh1SmserNo) || "179".equals(kputh1SmserNo))
                && (kputh1Amt.intValue() > 101 && kputh1Amt.intValue() < 1001)) {
            wk_UB10_Cnt = wk_UB10_Cnt + 1;
            wk_UB10_Amt = wk_UB10_Amt.add(kputh1Amt).subtract(new BigDecimal("10"));
        }
        // 034700
        // 034800     IF    ( KPUTH-TXTYPE = "U")
        // 034900       AND ( KPUTH-SMSERNO = "198" OR = "179")
        // 035000       AND ( KPUTH-AMT  > 1000 )
        // 035100       ADD     1               TO    WK-UB15-CNT
        // 035200       COMPUTE WK-UB15-AMT= WK-UB15-AMT + KPUTH-AMT - 15.
        if ("U".equals(kputh1Txtype)
                && ("198".equals(kputh1SmserNo) || "179".equals(kputh1SmserNo))
                && kputh1Amt.intValue() > 1000) {
            wk_UB15_Cnt = wk_UB15_Cnt + 1;
            wk_UB15_Amt = wk_UB15_Amt.add(kputh1Amt).subtract(new BigDecimal("15"));
        }
        // 035300
        // 035400     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "501")
        // 035500       AND ( KPUTH-AMT  < 20001)
        // 035600       ADD     1               TO    WK-UA06-CNT
        // 035700       COMPUTE WK-UA06-AMT= WK-UA06-AMT + KPUTH-AMT -  6.
        if ("U".equals(kputh1Txtype)
                && "501".equals(kputh1SmserNo)
                && kputh1Amt.intValue() < 20001) {
            wk_UA06_Cnt = wk_UA06_Cnt + 1;
            wk_UA06_Amt = wk_UA06_Amt.add(kputh1Amt).subtract(new BigDecimal("6"));
        }
        // 035800
        // 035900     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "501")
        // 036000       AND ( KPUTH-AMT  > 20000)
        // 036100       ADD     1               TO    WK-UA15-CNT
        // 036200       COMPUTE WK-UA15-AMT= WK-UA15-AMT + KPUTH-AMT - 15.
        if ("U".equals(kputh1Txtype)
                && "501".equals(kputh1SmserNo)
                && kputh1Amt.intValue() > 20000) {
            wk_UA15_Cnt = wk_UA15_Cnt + 1;
            wk_UA15_Amt = wk_UA15_Amt.add(kputh1Amt).subtract(new BigDecimal("15"));
        }
        // 036300
        // 036400     IF    ( KPUTH-TXTYPE  = "U")
        // 036500       AND ( KPUTH-SMSERNO = "179")
        // 036600       AND ( KPUTH-AMT  < 101  )
        // 036700       ADD     1               TO    WK-UB05-CNT
        // 036800       ADD     KPUTH-AMT       TO    WK-UB05-AMT .
        if ("U".equals(kputh1Txtype) && "179".equals(kputh1SmserNo) && kputh1Amt.intValue() < 101) {
            wk_UB05_Cnt = wk_UB05_Cnt + 1;
            wk_UB05_Amt = wk_UB05_Amt.add(kputh1Amt);
        }
        // 036900
        // 037000     IF    ( KPUTH-TXTYPE  = "U")
        // 037100       AND ( KPUTH-SMSERNO = "179")
        // 037200       AND ( KPUTH-AMT  > 101    AND   KPUTH-AMT  < 1001  )
        // 037300       ADD     1               TO    WK-UB10-CNT
        // 037400       ADD     KPUTH-AMT       TO    WK-UB10-AMT .
        if ("U".equals(kputh1Txtype)
                && "179".equals(kputh1SmserNo)
                && (kputh1Amt.intValue() > 101 && kputh1Amt.intValue() < 1001)) {
            wk_UB10_Cnt = wk_UB10_Cnt + 1;
            wk_UB10_Amt = wk_UB10_Amt.add(kputh1Amt);
        }
        // 037500
        // 037600     IF    ( KPUTH-TXTYPE = "U")
        // 037700       AND ( KPUTH-SMSERNO = "179")
        // 037800       AND ( KPUTH-AMT  > 1000 )
        // 037900       ADD     1               TO    WK-UB15-CNT
        // 038000       ADD     KPUTH-AMT       TO    WK-UB15-AMT .
        if ("U".equals(kputh1Txtype)
                && "179".equals(kputh1SmserNo)
                && (kputh1Amt.intValue() > 101 && kputh1Amt.intValue() < 1001)) {
            wk_UB15_Cnt = wk_UB15_Cnt + 1;
            wk_UB15_Amt = wk_UB15_Amt.add(kputh1Amt);
        }
        // 038020
        // 038025     IF      KPUTH-TXTYPE = "3"
        // 038030       ADD     1               TO    WK-EFCS-CNT
        // 038035       ADD     KPUTH-AMT       TO    WK-EFCS-AMT .
        if ("3".equals(kputh1Txtype)) {
            wk_EFCS_Cnt = wk_EFCS_Cnt + 1;
            wk_EFCS_Amt = wk_EFCS_Amt.add(kputh1Amt);
        }
        // 038037
        // 038040     IF      KPUTH-TXTYPE = "4" OR "5" OR "6" OR "7" OR "8"
        // 038060       ADD     1               TO    WK-LINE-CNT
        // 038080       ADD     KPUTH-AMT       TO    WK-LINE-AMT .
        if ("4".equals(kputh1Txtype)
                || "5".equals(kputh1Txtype)
                || "6".equals(kputh1Txtype)
                || "7".equals(kputh1Txtype)
                || "8".equals(kputh1Txtype)) {
            wk_LINE_Cnt = wk_LINE_Cnt + 1;
            wk_LINE_Amt = wk_LINE_Amt.add(kputh1Amt);
        }
        // 038100
        // 038200 5000-DTLIN-EXIT.
    }

    private String format4 = "Z,ZZ9";
    private String format5 = "ZZ,ZZ9";
    private String format6 = "ZZZ,ZZ9";
    private String format7 = "Z,ZZZ,ZZ9";
    private String format8 = "ZZ,ZZZ,ZZ9";
    private String format9 = "ZZZ,ZZZ,ZZ9";
    private String format10 = "Z,ZZZ,ZZZ,ZZ9";
    private String format11 = "ZZ,ZZZ,ZZZ,ZZ9";

    private void _WK_DTL_LINE() {
        String tmpCnt = "";
        String tmpAmt = "";

        // 010100 01 WK-DTL-LINE.
        // 010200    02 FILLER                   PIC X(01) VALUE SPACES.
        // 010300    02 WK-RPT-CODE              PIC X(06).
        // 010400    02 FILLER                   PIC X(02) VALUE SPACES.
        // 010500    02 WK-RPT-C-CNT             PIC ZZZ,ZZ9.
        // 010600    02 FILLER                   PIC X(01) VALUE SPACES.
        // 010700    02 WK-RPT-C-AMT             PIC ZZ,ZZZ,ZZZ,ZZ9.
        // 010800    02 FILLER                   PIC X(02) VALUE SPACES.
        // 010900    02 WK-RPT-SMSERNO           PIC X(03) VALUE SPACES.
        // 011000    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011100    02 WK-RPT-K-CNT             PIC ZZZ,ZZ9.
        // 011200    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011300    02 WK-RPT-K-AMT             PIC Z,ZZZ,ZZZ,ZZ9.
        // 011400    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011500    02 WK-RPT-T-CNT             PIC ZZ,ZZ9.
        // 011600    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011700    02 WK-RPT-T-AMT             PIC ZZZ,ZZZ,ZZ9.
        // 011702    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011704    02 WK-RPT-EFCS-CNT          PIC Z,ZZ9.
        // 011706    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011708    02 WK-RPT-EFCS-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 011720    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011740    02 WK-RPT-LINE-CNT          PIC Z,ZZ9.
        // 011760    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011780    02 WK-RPT-LINE-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 011800    02 FILLER                   PIC X(01) VALUE SPACES.
        // 011900    02 WK-RPT-UA06-CNT          PIC Z,ZZ9.
        // 012000    02 FILLER                   PIC X(01) VALUE SPACES.
        // 012100    02 WK-RPT-UA06-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 012200    02 FILLER                   PIC X(01) VALUE SPACES.
        // 012300    02 WK-RPT-UA15-CNT          PIC Z,ZZ9.
        // 012400    02 FILLER                   PIC X(01) VALUE SPACES.
        // 012500    02 WK-RPT-UA15-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 012600    02 FILLER                   PIC X(01) VALUE SPACES.
        // 012700    02 WK-RPT-UB05-CNT          PIC Z,ZZ9.
        // 012800    02 FILLER                   PIC X(01) VALUE SPACES.
        // 012900    02 WK-RPT-UB05-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 013000    02 FILLER                   PIC X(01) VALUE SPACES.
        // 013100    02 WK-RPT-UB10-CNT          PIC Z,ZZ9.
        // 013200    02 FILLER                   PIC X(01) VALUE SPACES.
        // 013300    02 WK-RPT-UB10-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 013400    02 FILLER                   PIC X(01) VALUE SPACES.
        // 013500    02 WK-RPT-UB15-CNT          PIC Z,ZZ9.
        // 013600    02 FILLER                   PIC X(01) VALUE SPACES.
        // 013700    02 WK-RPT-UB15-AMT          PIC ZZZ,ZZZ,ZZ9.
        // 013800    02 FILLER                   PIC X(01) VALUE SPACES.
        // 013900    02 WK-RPT-TOTCNT            PIC Z,ZZZ,ZZ9.
        // 014000    02 FILLER                   PIC X(01) VALUE SPACES.
        // 014100    02 WK-RPT-TOTAMT            PIC ZZ,ZZZ,ZZZ,ZZ9.

        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(wkRptCode, 6));
        sb.append(formatUtil.padX("", 2));
        tmpCnt = reportUtil.customFormat(wk_C_Cnt + "", format6);
        this.log("wkRpt_C_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 7));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wk_C_Amt + "", format11);
        this.log("wkRpt_C_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 14));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkRptSmserno, 3));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_K_Cnt + "", format6);
        this.log("wkRpt_K_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 7));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_K_Amt + "", format10);
        this.log("wkRpt_K_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 13));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_T_Cnt + "", format5);
        this.log("wkRpt_T_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 6));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_T_Amt + "", format9);
        this.log("wkRpt_T_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_EFCS_Cnt + "", format4);
        this.log("wkRpt_EFCS_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_EFCS_Amt + "", format9);
        this.log("wkRpt_EFCS_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_LINE_Cnt + "", format4);
        this.log("wkRpt_LINE_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_LINE_Amt + "", format9);
        this.log("wkRpt_LINE_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_UA06_Cnt + "", format4);
        this.log("wkRpt_UA06_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_UA06_Amt + "", format9);
        this.log("wkRpt_UA06_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_UA15_Cnt + "", format4);
        this.log("wkRpt_UA15_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_UA15_Amt + "", format9);
        this.log("wkRpt_UA15_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_UB05_Cnt + "", format4);
        this.log("wkRpt_UB05_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_UB05_Amt + "", format9);
        this.log("wkRpt_UB05_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_UB10_Cnt + "", format4);
        this.log("wkRpt_UB10_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_UB10_Amt + "", format9);
        this.log("wkRpt_UB10_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRpt_UB15_Cnt + "", format4);
        this.log("wkRpt_UB15_Cnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 5));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRpt_UB15_Amt + "", format9);
        this.log("wkRpt_UB15_Amt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 11));
        sb.append(formatUtil.padX("", 1));

        tmpCnt = reportUtil.customFormat(wkRptTotCnt + "", format7);
        this.log("wkRptTotCnt", tmpCnt);
        sb.append(formatUtil.padX(tmpCnt, 9));
        sb.append(formatUtil.padX("", 1));
        tmpAmt = reportUtil.customFormat(wkRptTotAmt + "", format11);
        this.log("wkRptTotAmt", tmpAmt);
        sb.append(formatUtil.padX(tmpAmt, 14));

        fileContentsSTAT7.add(sb.toString());
    }

    private void writeFile() {

        textFileSTAT7.deleteFile(fileDir + fileNameSTAT7);

        try {
            textFileSTAT7.writeFileContent(fileDir + fileNameSTAT7, fileContentsSTAT7, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
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

    private void log(String col, String text) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), col + "=" + text);
    }
}
