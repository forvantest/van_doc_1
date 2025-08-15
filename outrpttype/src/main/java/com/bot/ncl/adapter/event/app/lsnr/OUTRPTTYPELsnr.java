/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTRPTTYPE;
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
@Component("OUTRPTTYPELsnr")
@Scope("prototype")
public class OUTRPTTYPELsnr extends BatchListenerCase<OUTRPTTYPE> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    private OUTRPTTYPE event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // File related
    private static final String CHARSET = "UTF-8";
    private static final String CL020_PATH = "CL022"; // 讀檔目錄
    private static final String _003_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME_KPUTH = "KPUTH."; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME_056 = "CL-BH-056"; // 產檔檔名
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private String PATH_SEPARATOR = File.separator;
    private StringBuilder sb = new StringBuilder();
    private final List<String> fileRPTTYPEContents = new ArrayList<>();

    private Map<String, String> textMap;
    // ----------- WK  int (9) -----------
    private int wkKdate;
    private int wkRptCount;
    private int wkReccnt;
    private int wkTotcnt;
    private int wkPrePbrno;
    private int wkKCnt;
    private int wkTCnt;
    private int wkUa06Cnt;
    private int wkUa15Cnt;
    private int wkUb05Cnt;
    private int wkUb10Cnt;
    private int wkUb15Cnt;
    private int wkEfcsCnt;
    private int wkLineCnt;
    private int wkRptKCnt;
    private int wkRptTCnt;
    private int wkRptEfcsCnt;
    private int wkRptLineCnt;
    private int wkRptUb05Cnt;
    private int wkRptUb10Cnt;
    private int wkRptUb15Cnt;
    private int wkRptUa06Cnt;
    private int wkRptUa15Cnt;
    private int wkRptTotcnt;
    private int wkRptPbrno;

    // ---------- WK  String (X) ----------
    private String wkTaskRdate;
    private String wkTaskDate;
    private String wkRptTdate;
    private String wkRptPdate;
    private String wkPreCode;
    private String wkRptCode;
    private String wkRptSmserno;

    // ---------- WK  BigDecimal ----------
    private BigDecimal wkTotamt;
    private BigDecimal wkKAmt;
    private BigDecimal wkTAmt;
    private BigDecimal wkUa06Amt;
    private BigDecimal wkUa15Amt;
    private BigDecimal wkUb05Amt;
    private BigDecimal wkUb10Amt;
    private BigDecimal wkEfceAmt;
    private BigDecimal wkUb15Amt;
    private BigDecimal wkLineAmt;
    private BigDecimal wkRptKAmt;
    private BigDecimal wkRptTAmt;
    private BigDecimal wkRptEfcsAmt;
    private BigDecimal wkRptLineAmt;
    private BigDecimal wkRptUb05Amt;
    private BigDecimal wkRptUb10Amt;
    private BigDecimal wkRptUb15Amt;
    private BigDecimal wkRptUa06Amt;
    private BigDecimal wkRptUa15Amt;
    private BigDecimal wkRptTotamt;
    private BigDecimal wkEfcsAmt;

    // ----------- KPUTH  int (9) -----------
    private int kputhPbrno;

    // ---------- KPUTH  String (X) ----------
    private String kputhCode;
    private String kputhTxtype;
    private String kputhSmserno;

    // ---------- KPUTH  BigDecimal ----------
    private BigDecimal kputhAmt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTRPTTYPE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTTYPELsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTRPTTYPE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTTYPELsnr run");
        init(event);
        // FD-KPUTH檔案存在，執行0000-MAIN-RTN
        // 017600     IF  ATTRIBUTE  RESIDENT   OF FD-KPUTH IS =  VALUE(TRUE)
        if (textFile.exists(inputFilePath)) {
            // 017700       PERFORM  0000-MAIN-RTN  THRU 0000-MAIN-EXIT.
            main();
        }
        // 顯示訊息、結束程式
        // 017900     DISPLAY "SYM/CL/BH/OUTING/RPTPOST COMPLETED (CL-C056)".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/OUTING/RPTPOST COMPLETED (CL-C056)");
    }

    private void init(OUTRPTTYPE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTTYPELsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = textMap.get("WK_TASK_DATE");
        wkTaskRdate = textMap.get("RGDAY");

        // 設定日期
        // 017200     MOVE   WK-TASK-RDATE      TO       WK-KDATE    .
        // 017300     MOVE   WK-TASK-DATE       TO       WK-RPT-TDATE,
        // 017400                                        WK-RPT-PDATE.
        wkKdate = parse.string2Integer(wkTaskRdate);
        wkRptTdate = wkTaskDate;
        wkRptPdate = wkTaskDate;

        // 設定檔名
        // 017500     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO WK-KPUTHDIR.
        inputFilePath =
                fileDir
                        + CL020_PATH
                        + PATH_SEPARATOR
                        + _003_PATH
                        + PATH_SEPARATOR
                        + wkKdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME_KPUTH;

        // 002100  FD  REPORTFL
        // 002200      VALUE  OF  TITLE  IS  "BD/CL/BH/056."
        // 002300                 USERBACKUPNAME IS TRUE
        outputFilePath = fileDir + FILE_OUTPUT_NAME_056;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTRPTTYPELsnr main");

        // 清變數
        // 018300     MOVE    0   TO                 WK-RPT-COUNT,WK-RECCNT   ,
        // 018400                       WK-TOTCNT   ,WK-TOTAMT   ,WK-PRE-PBRNO,
        // 018500                       WK-K-CNT    ,WK-T-CNT    ,WK-UA06-CNT ,
        // 018600                       WK-K-AMT    ,WK-T-AMT    ,WK-UA06-AMT ,
        // 018700                       WK-UA15-CNT ,WK-UB05-CNT ,WK-UB10-CNT ,
        // 018800                       WK-UA15-AMT ,WK-UB05-AMT ,WK-UB10-AMT ,
        // 018900                       WK-UB15-CNT ,WK-EFCS-CNT ,WK-EFCS-AMT ,
        // 019000                       WK-UB15-AMT ,WK-LINE-CNT ,WK-LINE-AMT .
        wkRptCount = 0;
        wkReccnt = 0;
        wkTotcnt = 0;
        wkTotamt = new BigDecimal(0);
        wkPrePbrno = 0;
        wkKCnt = 0;
        wkTCnt = 0;
        wkUa06Cnt = 0;
        wkKAmt = new BigDecimal(0);
        wkTAmt = new BigDecimal(0);
        wkUa06Amt = new BigDecimal(0);
        wkUa15Cnt = 0;
        wkUb05Cnt = 0;
        wkUb10Cnt = 0;
        wkUa15Amt = new BigDecimal(0);
        wkUb05Amt = new BigDecimal(0);
        wkUb10Amt = new BigDecimal(0);
        wkUb15Cnt = 0;
        wkEfcsCnt = 0;
        wkEfceAmt = new BigDecimal(0);
        wkUb15Amt = new BigDecimal(0);
        wkLineCnt = 0;
        wkLineAmt = new BigDecimal(0);

        // 019200     MOVE    SPACES              TO     WK-PRE-CODE  .
        wkPreCode = "";
        // 開啟檔案
        // 019300     OPEN    OUTPUT    REPORTFL.
        // 019400     OPEN    INPUT     FD-KPUTH.
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        // 019500 0000-MAIN-LOOP.
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
            kputhTxtype = detail.substring(111, 112);
            kputhSmserno = detail.substring(64, 67);
        }

        // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LFAST
        // 019600     READ    FD-KPUTH  AT END    GO TO  0000-MAIN-LFAST   .
        // 判斷換頁、判斷代收類別、累加金額筆數
        // 019800     PERFORM 3000-PAGESWH-RTN    THRU   3000-PAGESWH-EXIT .
        pageswh3000();
        // 019900     PERFORM 3100-CODESWH-RTN    THRU   3100-CODESWH-EXIT .
        codeswh3100();
        // 020000     PERFORM 5000-DTLIN-RTN      THRU   5000-DTLIN-EXIT   .
        dtlin5000();
        // 020100     GO TO   0000-MAIN-LOOP.

        if (cnt == lines.size()) {
            // 020200 0000-MAIN-LFAST.
            // 寫REPORTFL報表
            // 020300     PERFORM 4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT .
            subtailRtn4000();
            // 020400 0000-MAIN-EXIT.
            try {
                textFile.writeFileContent(outputFilePath, fileRPTTYPEContents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }

    // 4000-SUBTAIL-RTN
    private void subtailRtn4000() {
        // 寫報表
        // 026700     MOVE       WK-K-CNT        TO   WK-RPT-K-CNT .
        wkRptKCnt = wkKCnt;
        // 026800     ADD        WK-K-CNT        TO   WK-TOTCNT    .
        wkTotcnt += wkKCnt;
        // 026900     MOVE       WK-K-AMT        TO   WK-RPT-K-AMT .
        wkRptKAmt = wkKAmt;
        // 027000     ADD        WK-K-AMT        TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkKAmt);
        // 027100     MOVE       WK-T-CNT        TO   WK-RPT-T-CNT .
        wkRptTCnt = wkTCnt;
        // 027200     ADD        WK-T-CNT        TO   WK-TOTCNT    .
        wkTotcnt += wkTCnt;
        // 027300     MOVE       WK-T-AMT        TO   WK-RPT-T-AMT .
        wkRptTAmt = wkTAmt;
        // 027400     ADD        WK-T-AMT        TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkTAmt);
        // 027420     MOVE       WK-EFCS-CNT     TO   WK-RPT-EFCS-CNT.
        wkRptEfcsCnt = wkEfcsCnt;
        // 027440     ADD        WK-EFCS-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkEfcsCnt;
        // 027460     MOVE       WK-EFCS-AMT     TO   WK-RPT-EFCS-AMT.
        wkRptEfcsAmt = wkEfceAmt;
        // 027480     ADD        WK-EFCS-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkEfceAmt);
        // 027482     MOVE       WK-LINE-CNT     TO   WK-RPT-LINE-CNT.
        wkRptLineCnt = wkLineCnt;
        // 027484     ADD        WK-LINE-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkLineCnt;
        // 027486     MOVE       WK-LINE-AMT     TO   WK-RPT-LINE-AMT.
        wkRptLineAmt = wkLineAmt;
        // 027488     ADD        WK-LINE-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkLineAmt);
        // 027500     MOVE       WK-UB05-CNT     TO   WK-RPT-UB05-CNT.
        wkRptUb05Cnt = wkUb05Cnt;
        // 027600     ADD        WK-UB05-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb05Cnt;
        // 027700     MOVE       WK-UB05-AMT     TO   WK-RPT-UB05-AMT.
        wkRptUb05Amt = wkUb05Amt;
        // 027800     ADD        WK-UB05-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb05Amt);
        // 027900     MOVE       WK-UB10-CNT     TO   WK-RPT-UB10-CNT.
        wkRptUb10Cnt = wkUb10Cnt;
        // 028000     ADD        WK-UB10-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb10Cnt;
        // 028100     MOVE       WK-UB10-AMT     TO   WK-RPT-UB10-AMT.
        wkRptUb10Amt = wkUb10Amt;
        // 028200     ADD        WK-UB10-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb10Amt);
        // 028300     MOVE       WK-UB15-CNT     TO   WK-RPT-UB15-CNT.
        wkRptUb15Cnt = wkUb15Cnt;
        // 028400     ADD        WK-UB15-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb15Cnt;
        // 028500     MOVE       WK-UB15-AMT     TO   WK-RPT-UB15-AMT.
        wkRptUb15Amt = wkUb15Amt;
        // 028600     ADD        WK-UB15-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb15Amt);
        // 028700     MOVE       WK-UA06-CNT     TO   WK-RPT-UA06-CNT.
        wkRptUa06Cnt = wkUa06Cnt;
        // 028800     ADD        WK-UA06-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUa06Cnt;
        // 028900     MOVE       WK-UA06-AMT     TO   WK-RPT-UA06-AMT.
        wkRptUa06Amt = wkUa06Amt;
        // 029000     ADD        WK-UA06-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUa06Amt);
        // 029100     MOVE       WK-UA15-CNT     TO   WK-RPT-UA15-CNT.
        wkRptUa15Cnt = wkUa15Cnt;
        // 029200     ADD        WK-UA15-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUa15Cnt;
        // 029300     MOVE       WK-UA15-AMT     TO   WK-RPT-UA15-AMT.
        wkRptUa15Amt = wkUa15Amt;
        // 029400     ADD        WK-UA15-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUa15Amt);
        // 029600     MOVE       WK-TOTCNT       TO   WK-RPT-TOTCNT.
        wkRptTotcnt = wkTotcnt;
        // 029700     MOVE       WK-TOTAMT       TO   WK-RPT-TOTAMT.
        wkRptTotamt = wkTotamt;
        // 029900     MOVE       SPACES          TO   REPORT-LINE    .
        sb = new StringBuilder();
        // 030000     WRITE      REPORT-LINE     FROM WK-DTL-LINE    .
        // 010600 01 WK-DTL-LINE.
        // 010700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 010800    02 WK-RPT-CODE              PIC X(06).
        sb.append(formatUtil.padX(wkRptCode, 6));
        // 010900    02 FILLER                   PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 011000    02 WK-RPT-SMSERNO           PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX(wkRptSmserno, 3));
        // 011100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011200    02 WK-RPT-K-CNT             PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptKCnt, "Z,ZZ9"));
        // 011300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011400    02 WK-RPT-K-AMT             PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptKAmt, "ZZZ,ZZZ,ZZ9"));
        // 011500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011600    02 WK-RPT-T-CNT             PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTCnt, "ZZ,ZZ9"));
        // 011700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011800    02 WK-RPT-T-AMT             PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTAmt, "ZZZ,ZZZ,ZZ9"));
        // 011900    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011920    02 WK-RPT-EFCS-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptEfcsCnt, "Z,ZZ9"));
        // 011940    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011960    02 WK-RPT-EFCS-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptEfcsAmt, "ZZZ,ZZZ,ZZ9"));
        // 011980    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011982    02 WK-RPT-LINE-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptLineCnt, "Z,ZZ9"));
        // 011984    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011986    02 WK-RPT-LINE-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptLineAmt, "ZZZ,ZZZ,ZZ9"));
        // 011988    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012000    02 WK-RPT-UA06-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa06Cnt, "Z,ZZ9"));
        // 012100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012200    02 WK-RPT-UA06-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa06Amt, "ZZZ,ZZZ,ZZ9"));
        // 012300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012400    02 WK-RPT-UA15-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa15Cnt, "Z,ZZ9"));
        // 012500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012600    02 WK-RPT-UA15-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa15Amt, "ZZZ,ZZZ,ZZ9"));
        // 012700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012800    02 WK-RPT-UB05-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb05Cnt, "Z,ZZ9"));
        // 012900    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013000    02 WK-RPT-UB05-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb05Amt, "ZZZ,ZZZ,ZZ9"));
        // 013100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013200    02 WK-RPT-UB10-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb10Cnt, "Z,ZZ9"));
        // 013300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013400    02 WK-RPT-UB10-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb10Amt, "ZZZ,ZZZ,ZZ9"));
        // 013500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013600    02 WK-RPT-UB15-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb15Cnt, "Z,ZZ9"));
        // 013700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013800    02 WK-RPT-UB15-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb15Amt, "ZZZ,ZZZ,ZZ9"));
        // 013900    02 FILLER                   PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX("", 3));
        // 014000    02 WK-RPT-TOTCNT            PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTotcnt, "ZZZ,ZZ9"));
        // 014100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 014200    02 WK-RPT-TOTAMT            PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTotamt, "Z,ZZZ,ZZZ,ZZ9"));
        fileRPTTYPEContents.add(sb.toString());
        // 030100     MOVE       0  TO  WK-K-CNT    ,WK-T-CNT    ,WK-UA06-CNT ,
        // 030200                       WK-K-AMT    ,WK-T-AMT    ,WK-UA06-AMT ,
        // 030300                       WK-UA15-CNT ,WK-UB05-CNT ,WK-UB10-CNT ,
        // 030400                       WK-UA15-AMT ,WK-UB05-AMT ,WK-UB10-AMT ,
        // 030500                       WK-UB15-CNT ,WK-TOTCNT   ,WK-EFCS-CNT ,
        // 030600                       WK-UB15-AMT ,WK-TOTAMT   ,WK-EFCS-AMT ,
        // 030650                       WK-LINE-CNT ,WK-LINE-AMT .
        wkKCnt = 0;
        wkTCnt = 0;
        wkUa06Cnt = 0;
        wkKAmt = new BigDecimal(0);
        wkTAmt = new BigDecimal(0);
        wkUa06Amt = new BigDecimal(0);
        wkUa15Cnt = 0;
        wkUb05Cnt = 0;
        wkUb10Cnt = 0;
        wkUa15Amt = new BigDecimal(0);
        wkUb05Amt = new BigDecimal(0);
        wkUb10Amt = new BigDecimal(0);
        wkUb15Cnt = 0;
        wkTotcnt = 0;
        wkEfcsCnt = 0;
        wkUb15Amt = new BigDecimal(0);
        wkTotamt = new BigDecimal(0);
        wkEfcsAmt = new BigDecimal(0);
        wkLineCnt = 0;
        wkLineAmt = new BigDecimal(0);
        fileRPTTYPEContents.add(sb.toString());

        // 030700     MOVE       SPACES          TO  WK-RPT-SMSERNO  .
        wkRptSmserno = "";
    }

    // 3000-PAGESWH-RTN
    private void pageswh3000() {
        // 022800*  換主辦行或滿 50 行換頁
        // 023200     IF    (    WK-PRE-PBRNO        =      000      )
        if (wkPrePbrno == 0) {
            // 023300       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
            // 023400                                           WK-RPT-PBRNO
            wkPrePbrno = kputhPbrno;
            // 023500       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
            // 023600       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            titleRtn2000();
            // 023700       PERFORM  2100-TITLE-RTN      THRU   2100-TITLE-EXIT
            titleRtn2100();
        }

        // 024200     IF    (    KPUTH-PBRNO         NOT =  WK-PRE-PBRNO )
        if (wkPrePbrno != kputhPbrno) {
            // 024300       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail4000();
            // 024400       MOVE     KPUTH-CODE          TO     WK-PRE-CODE ,
            // 024500                                           WK-RPT-CODE
            wkPreCode = kputhCode;
            wkRptCode = kputhCode;
            // 024600       MOVE     KPUTH-PBRNO         TO     WK-PRE-PBRNO,
            // 024700                                           WK-RPT-PBRNO
            wkPrePbrno = kputhPbrno;
            wkRptPbrno = kputhPbrno;
            // 024800       MOVE     SPACES              TO     REPORT-LINE
            // 024900       WRITE    REPORT-LINE         AFTER  PAGE
            fileRPTTYPEContents.add("\u000c");
            // 025000       PERFORM  2000-TITLE-RTN      THRU   2000-TITLE-EXIT
            title2000();
            // 025100       PERFORM  2100-TITLE-RTN      THRU   2100-TITLE-EXIT
            title2100();
            // 025200       GO TO 3000-PAGESWH-EXIT.
        }
    }

    // 2000-TITLE-RTN
    private void title2000() {
        // 寫表頭(LINE123)
        // 020800     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 020900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(81) VALUE SPACE.
        sb.append(formatUtil.padX("", 81));
        // 004300    02 FILLER                          PIC X(30) VALUE
        // 004400       " 外部代收－代收類別管道彙記表 ".
        sb.append(formatUtil.padX(" 外部代收－代收類別管道彙記表 ", 30));
        // 004500    02 FILLER                          PIC X(75) VALUE SPACE.
        sb.append(formatUtil.padX("", 75));
        // 004600    02 FILLER                          PIC X(12) VALUE
        // 004700       "FORM : C056 ".
        sb.append(formatUtil.padX("FORM : C056 ", 12));
        fileRPTTYPEContents.add(sb.toString());
        // 021000     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 021100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004800 01 WK-TITLE-LINE2.
        // 004900    02 FILLER                          PIC X(181) VALUE SPACE.
        sb.append(formatUtil.padX("", 181));
        // 005000    02 FILLER                          PIC X(12) VALUE
        // 005100       " 印表日期 :".
        sb.append(formatUtil.padX(" 印表日期 :", 12));
        // 005200    02 WK-RPT-PDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        fileRPTTYPEContents.add(sb.toString());
        // 021200     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 021300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 005300 01 WK-TITLE-LINE3.
        // 005400    02 FILLER                          PIC X(10) VALUE
        // 005500       " 主辦行： ".
        sb.append(formatUtil.padX(" 主辦行： ", 10));
        // 005600    02 WK-RPT-PBRNO                    PIC 9(03).
        sb.append(formatUtil.pad9("" + wkRptPbrno, 3));
        // 005700    02 FILLER                          PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 005800    02 FILLER                          PIC X(12) VALUE
        // 005900       " 入帳日期： ".
        sb.append(formatUtil.padX(" 入帳日期： ", 12));
        // 006000    02 WK-RPT-TDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(wkRptTdate, "Z99/99/99"));
        // 006100    02 FILLER                          PIC X(65) VALUE SPACE.
        sb.append(formatUtil.padX("", 65));
        fileRPTTYPEContents.add(sb.toString());
    }

    // 2100-TITLE-RTN
    private void title2100() {
        // 寫表頭(LINE45)
        // 021800     MOVE       SPACES              TO     REPORT-LINE   .
        // 021900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        fileRPTTYPEContents.add("");
        // 022000     MOVE       SPACES              TO     REPORT-LINE   .
        // 022100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE5.
        fileRPTTYPEContents.add("");
        // 022200     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 022300     WRITE      REPORT-LINE         FROM   WK-GATE-LINE  .
        fileRPTTYPEContents.add(sb.toString());
    }

    // 2000-TITLE-RTN
    private void titleRtn2000() {
        // 寫表頭(LINE123)
        // 020800     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 020900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // 004100 01 WK-TITLE-LINE1.
        // 004200    02 FILLER                          PIC X(81) VALUE SPACE.
        sb.append(formatUtil.padX("", 81));
        // 004300    02 FILLER                          PIC X(30) VALUE
        // 004400       " 外部代收－代收類別管道彙記表 ".
        sb.append(formatUtil.padX(" 外部代收－代收類別管道彙記表 ", 30));
        // 004500    02 FILLER                          PIC X(75) VALUE SPACE.
        sb.append(formatUtil.padX("", 75));
        // 004600    02 FILLER                          PIC X(12) VALUE
        // 004700       "FORM : C056 ".
        sb.append(formatUtil.padX("FORM : C056 ", 12));
        fileRPTTYPEContents.add(sb.toString());
        // 021000     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 021100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        // 004800 01 WK-TITLE-LINE2.
        // 004900    02 FILLER                          PIC X(181) VALUE SPACE.
        sb.append(formatUtil.padX("", 181));
        // 005000    02 FILLER                          PIC X(12) VALUE
        // 005100       " 印表日期 :".
        sb.append(formatUtil.padX(" 印表日期 :", 12));
        // 005200    02 WK-RPT-PDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        fileRPTTYPEContents.add(sb.toString());
        // 021200     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 021300     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        // 005300 01 WK-TITLE-LINE3.
        // 005400    02 FILLER                          PIC X(10) VALUE
        // 005500       " 主辦行： ".
        sb.append(formatUtil.padX(" 主辦行： ", 10));
        // 005600    02 WK-RPT-PBRNO                    PIC 9(03).
        sb.append(formatUtil.pad9(wkRptPdate, 3));
        // 005700    02 FILLER                          PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 005800    02 FILLER                          PIC X(12) VALUE
        // 005900       " 入帳日期： ".
        sb.append(formatUtil.padX(" 入帳日期： ", 12));
        // 006000    02 WK-RPT-TDATE                    PIC Z99/99/99.
        sb.append(reportUtil.customFormat(wkRptTdate, "Z99/99/99"));
        // 006100    02 FILLER                          PIC X(65) VALUE SPACE.
        sb.append(formatUtil.padX("", 65));
        fileRPTTYPEContents.add(sb.toString());
    }

    private void titleRtn2100() {
        // 寫表頭(LINE45)
        // 021800     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 021900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE4.
        // 006500 01 WK-TITLE-LINE4.
        // 006600    02 FILLER    PIC X(10) VALUE " 代收類別 ".
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        // 006700    02 FILLER    PIC X(05) VALUE SPACES      .
        sb.append(formatUtil.padX("", 5));
        // 006800    02 FILLER    PIC X(06) VALUE " 超商 ".
        sb.append(formatUtil.padX(" 超商 ", 6));
        // 006900    02 FILLER    PIC X(15) VALUE SPACE.
        sb.append(formatUtil.padX("", 15));
        // 007000    02 FILLER    PIC X(08) VALUE " 信用卡 ".
        sb.append(formatUtil.padX(" 信用卡 ", 8));
        // 007100    02 FILLER    PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX("", 10));
        // 007140    02 FILLER    PIC X(10) VALUE " ＥＦＣＳ ".
        sb.append(formatUtil.padX(" ＥＦＣＳ ", 10));
        // 007160    02 FILLER    PIC X(07) VALUE SPACE.
        sb.append(formatUtil.padX("", 7));
        // 007170    02 FILLER    PIC X(10) VALUE "ThirdParty".
        sb.append(formatUtil.padX("ThirdParty", 10));
        // 007180    02 FILLER    PIC X(07) VALUE SPACE.
        sb.append(formatUtil.padX("", 7));
        // 007200    02 FILLER    PIC X(14) VALUE " 郵局一  6 元 ".
        sb.append(formatUtil.padX(" 郵局一  6 元 ", 14));
        // 007300    02 FILLER    PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 007400    02 FILLER    PIC X(14) VALUE " 郵局一 15 元 ".
        sb.append(formatUtil.padX(" 郵局一 15 元 ", 14));
        // 007500    02 FILLER    PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 007600    02 FILLER    PIC X(14) VALUE " 郵局二  5 元 ".
        sb.append(formatUtil.padX(" 郵局二  5 元 ", 14));
        // 007700    02 FILLER    PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 007800    02 FILLER    PIC X(14) VALUE " 郵局二 10 元 ".
        sb.append(formatUtil.padX(" 郵局二 10 元 ", 14));
        // 007900    02 FILLER    PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 008000    02 FILLER    PIC X(14) VALUE " 郵局二 15 元 ".
        sb.append(formatUtil.padX(" 郵局二 15 元 ", 14));
        fileRPTTYPEContents.add(sb.toString());
        // 022000     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 022100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE5.
        // 008100  01 WK-TITLE-LINE5.
        // 008200    02 FILLER    PIC X(08) VALUE SPACES        .
        sb.append(formatUtil.padX("", 8));
        // 008300    02 FILLER    PIC X(06) VALUE " 代號 "      .
        sb.append(formatUtil.padX(" 代號 ", 6));
        // 008400    02 FILLER    PIC X(16) VALUE " 筆數　　　金額 ".
        sb.append(formatUtil.padX(" 筆數　　　金額 ", 16));
        // 008500    02 FILLER    PIC X(03) VALUE SPACES        .
        sb.append(formatUtil.padX("", 3));
        // 008600    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 008620    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 008640    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 008660    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 008680    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 008700    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 008800    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 008900    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 009000    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 009100    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 009200    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 009300    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 009400    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 009500    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 009600    02 FILLER    PIC X(16) VALUE " 筆數      金額 ".
        sb.append(formatUtil.padX(" 筆數      金額 ", 16));
        // 009700    02 FILLER    PIC X(03) VALUE SPACES        .
        sb.append(formatUtil.padX("", 3));
        // 009800    02 FILLER    PIC X(12) VALUE " 總筆數 "    .
        sb.append(formatUtil.padX(" 總筆數 ", 12));
        // 009900    02 FILLER    PIC X(02) VALUE SPACES        .
        sb.append(formatUtil.padX("", 2));
        // 010000    02 FILLER    PIC X(12) VALUE " 總金額 "    .
        sb.append(formatUtil.padX(" 總金額 ", 12));
        fileRPTTYPEContents.add(sb.toString());
        // 022200     MOVE       SPACES              TO     REPORT-LINE   .
        sb = new StringBuilder();
        // 022300     WRITE      REPORT-LINE         FROM   WK-GATE-LINE  .
        // 010200 01 WK-GATE-LINE.
        // 010300    02 FILLER                   PIC X(203) VALUE ALL "-".
        sb.append(reportUtil.makeGate("-", 203));
        fileRPTTYPEContents.add(sb.toString());
    }

    // 3100-CODESWH-RTN
    private void codeswh3100() {
        // 判斷代收類別
        // 025800     IF    (    KPUTH-CODE          NOT =  WK-PRE-CODE  )
        // 025900       AND (    WK-PRE-CODE         NOT =  SPACES       )
        if (!wkPreCode.equals(kputhCode) && !wkPreCode.trim().isEmpty()) {
            // 026000       PERFORM  4000-SUBTAIL-RTN    THRU   4000-SUBTAIL-EXIT
            subtail4000();
            // 026100       MOVE     KPUTH-CODE          TO     WK-PRE-CODE
            wkPreCode = kputhCode;
            // 026200       GO TO 3100-CODESWH-EXIT.
        }
    }

    // 4000-SUBTAIL-RTN
    private void subtail4000() {
        // 寫報表
        // 026700     MOVE       WK-K-CNT        TO   WK-RPT-K-CNT .
        wkRptKCnt = wkKCnt;
        // 026800     ADD        WK-K-CNT        TO   WK-TOTCNT    .
        wkTotcnt += wkKCnt;
        // 026900     MOVE       WK-K-AMT        TO   WK-RPT-K-AMT .
        wkRptKAmt = wkKAmt;
        // 027000     ADD        WK-K-AMT        TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkKAmt);
        // 027100     MOVE       WK-T-CNT        TO   WK-RPT-T-CNT .
        wkRptTCnt = wkTCnt;
        // 027200     ADD        WK-T-CNT        TO   WK-TOTCNT    .
        wkTotcnt += wkTCnt;
        // 027300     MOVE       WK-T-AMT        TO   WK-RPT-T-AMT .
        wkRptTAmt = wkTAmt;
        // 027400     ADD        WK-T-AMT        TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkTAmt);
        // 027420     MOVE       WK-EFCS-CNT     TO   WK-RPT-EFCS-CNT.
        wkRptEfcsCnt = wkEfcsCnt;
        // 027440     ADD        WK-EFCS-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkEfcsCnt;
        // 027460     MOVE       WK-EFCS-AMT     TO   WK-RPT-EFCS-AMT.
        wkRptEfcsAmt = wkEfcsAmt;
        // 027480     ADD        WK-EFCS-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkEfcsAmt);
        // 027482     MOVE       WK-LINE-CNT     TO   WK-RPT-LINE-CNT.
        wkRptLineCnt = wkLineCnt;
        // 027484     ADD        WK-LINE-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkLineCnt;
        // 027486     MOVE       WK-LINE-AMT     TO   WK-RPT-LINE-AMT.
        wkRptLineAmt = wkLineAmt;
        // 027488     ADD        WK-LINE-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkLineAmt);
        // 027500     MOVE       WK-UB05-CNT     TO   WK-RPT-UB05-CNT.
        wkRptUb05Cnt = wkUb05Cnt;
        // 027600     ADD        WK-UB05-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb05Cnt;
        // 027700     MOVE       WK-UB05-AMT     TO   WK-RPT-UB05-AMT.
        wkRptUb05Amt = wkUb05Amt;
        // 027800     ADD        WK-UB05-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb05Amt);
        // 027900     MOVE       WK-UB10-CNT     TO   WK-RPT-UB10-CNT.
        wkRptUb10Cnt = wkUb10Cnt;
        // 028000     ADD        WK-UB10-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb10Cnt;
        // 028100     MOVE       WK-UB10-AMT     TO   WK-RPT-UB10-AMT.
        wkRptUb10Amt = wkUb10Amt;
        // 028200     ADD        WK-UB10-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb10Amt);
        // 028300     MOVE       WK-UB15-CNT     TO   WK-RPT-UB15-CNT.
        wkRptUb15Cnt = wkUb15Cnt;
        // 028400     ADD        WK-UB15-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUb15Cnt;
        // 028500     MOVE       WK-UB15-AMT     TO   WK-RPT-UB15-AMT.
        wkRptUb15Amt = wkUb15Amt;
        // 028600     ADD        WK-UB15-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUb15Amt);
        // 028700     MOVE       WK-UA06-CNT     TO   WK-RPT-UA06-CNT.
        wkRptUa06Cnt = wkUa06Cnt;
        // 028800     ADD        WK-UA06-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUa06Cnt;
        // 028900     MOVE       WK-UA06-AMT     TO   WK-RPT-UA06-AMT.
        wkRptUa06Amt = wkUa06Amt;
        // 029000     ADD        WK-UA06-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUa06Amt);
        // 029100     MOVE       WK-UA15-CNT     TO   WK-RPT-UA15-CNT.
        wkRptUa15Cnt = wkUa15Cnt;
        // 029200     ADD        WK-UA15-CNT     TO   WK-TOTCNT    .
        wkTotcnt += wkUa15Cnt;
        // 029300     MOVE       WK-UA15-AMT     TO   WK-RPT-UA15-AMT.
        wkRptUa15Amt = wkUa15Amt;
        // 029400     ADD        WK-UA15-AMT     TO   WK-TOTAMT    .
        wkTotamt = wkTotamt.add(wkUa15Amt);
        // 029600     MOVE       WK-TOTCNT       TO   WK-RPT-TOTCNT.
        wkRptTotcnt = wkTotcnt;
        // 029700     MOVE       WK-TOTAMT       TO   WK-RPT-TOTAMT.
        wkRptTotamt = wkTotamt;
        // 029900     MOVE       SPACES          TO   REPORT-LINE    .
        fileRPTTYPEContents.add("");
        // 030000     WRITE      REPORT-LINE     FROM WK-DTL-LINE    .
        // 010600 01 WK-DTL-LINE.
        sb = new StringBuilder();
        // 010700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 010800    02 WK-RPT-CODE              PIC X(06).
        sb.append(formatUtil.padX(wkRptCode, 6));
        // 010900    02 FILLER                   PIC X(02) VALUE SPACES.
        sb.append(formatUtil.padX("", 2));
        // 011000    02 WK-RPT-SMSERNO           PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX("", 3));
        // 011100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011200    02 WK-RPT-K-CNT             PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptKCnt, "Z,ZZ9"));
        // 011300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011400    02 WK-RPT-K-AMT             PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptKAmt, "ZZZ,ZZZ,ZZ9"));
        // 011500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011600    02 WK-RPT-T-CNT             PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTCnt, "ZZ,ZZ9"));
        // 011700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011800    02 WK-RPT-T-AMT             PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTAmt, "ZZZ,ZZZ,ZZ9"));
        // 011900    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011920    02 WK-RPT-EFCS-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptEfcsCnt, "Z,ZZ9"));
        // 011940    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011960    02 WK-RPT-EFCS-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptEfcsAmt, "ZZZ,ZZZ,ZZ9"));
        // 011980    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011982    02 WK-RPT-LINE-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptLineCnt, "Z,ZZ9"));
        // 011984    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 011986    02 WK-RPT-LINE-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptLineAmt, "ZZZ,ZZZ,ZZ9"));
        // 011988    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012000    02 WK-RPT-UA06-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa06Cnt, "Z,ZZ9"));
        // 012100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012200    02 WK-RPT-UA06-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa06Amt, "ZZZ,ZZZ,ZZ9"));
        // 012300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012400    02 WK-RPT-UA15-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa15Cnt, "Z,ZZ9"));
        // 012500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012600    02 WK-RPT-UA15-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUa15Amt, "ZZZ,ZZZ,ZZ9"));
        // 012700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 012800    02 WK-RPT-UB05-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb05Cnt, "Z,ZZ9"));
        // 012900    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013000    02 WK-RPT-UB05-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb05Amt, "ZZZ,ZZZ,ZZ9"));
        // 013100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013200    02 WK-RPT-UB10-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb10Cnt, "Z,ZZ9"));
        // 013300    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013400    02 WK-RPT-UB10-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb10Amt, "ZZZ,ZZZ,ZZ9"));
        // 013500    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013600    02 WK-RPT-UB15-CNT          PIC Z,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb15Cnt, "Z,ZZ9"));
        // 013700    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 013800    02 WK-RPT-UB15-AMT          PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptUb15Amt, "ZZZ,ZZZ,ZZ9"));
        // 013900    02 FILLER                   PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX("", 3));
        // 014000    02 WK-RPT-TOTCNT            PIC ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTotcnt, "ZZZ,ZZ9"));
        // 014100    02 FILLER                   PIC X(01) VALUE SPACES.
        sb.append(formatUtil.padX("", 1));
        // 014200    02 WK-RPT-TOTAMT            PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + wkRptTotamt, "Z,ZZZ,ZZZ,ZZ9"));
        fileRPTTYPEContents.add(sb.toString());

        // 030100     MOVE       0  TO  WK-K-CNT    ,WK-T-CNT    ,WK-UA06-CNT ,
        // 030200                       WK-K-AMT    ,WK-T-AMT    ,WK-UA06-AMT ,
        // 030300                       WK-UA15-CNT ,WK-UB05-CNT ,WK-UB10-CNT ,
        // 030400                       WK-UA15-AMT ,WK-UB05-AMT ,WK-UB10-AMT ,
        // 030500                       WK-UB15-CNT ,WK-TOTCNT   ,WK-EFCS-CNT ,
        // 030600                       WK-UB15-AMT ,WK-TOTAMT   ,WK-EFCS-AMT ,
        // 030650                       WK-LINE-CNT ,WK-LINE-AMT .
        wkKCnt = 0;
        wkTCnt = 0;
        wkUa06Cnt = 0;
        wkKAmt = new BigDecimal(0);
        wkTAmt = new BigDecimal(0);
        wkUa06Amt = new BigDecimal(0);
        wkUa15Cnt = 0;
        wkUb05Cnt = 0;
        wkUb10Cnt = 0;
        wkUa15Amt = new BigDecimal(0);
        wkUb05Amt = new BigDecimal(0);
        wkUb10Amt = new BigDecimal(0);
        wkUb15Cnt = 0;
        wkTotcnt = 0;
        wkEfcsCnt = 0;
        wkUb15Amt = new BigDecimal(0);
        wkTotamt = new BigDecimal(0);
        wkEfcsAmt = new BigDecimal(0);
        wkLineCnt = 0;
        wkLineAmt = new BigDecimal(0);

        // 030700     MOVE       SPACES          TO  WK-RPT-SMSERNO  .
        wkRptSmserno = "";
    }

    private void dtlin5000() {
        // 累加各帳務別、代收帳號筆數金額
        // 031200     MOVE   KPUTH-CODE        TO    WK-RPT-CODE.
        wkRptCode = kputhCode;

        // 031400     IF    ( KPUTH-TXTYPE ="K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 031500       MOVE    KPUTH-SMSERNO   TO    WK-RPT-SMSERNO
        // 031600       ADD     1               TO    WK-K-CNT
        // 031700       ADD     KPUTH-AMT       TO    WK-K-AMT.
        // 031900     IF    ( KPUTH-TXTYPE ="T" OR ="Q" OR ="Y" OR ="Z" OR ="2")
        // 032000       ADD     1               TO    WK-T-CNT
        // 032100       ADD     KPUTH-AMT       TO    WK-T-AMT.
        // 032300     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "198")
        // 032400       AND ( KPUTH-AMT  < 101  )
        // 032500       ADD     1               TO    WK-UB05-CNT
        // 032600       ADD     KPUTH-AMT       TO    WK-UB05-AMT.
        // 032800     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "198")
        // 032900       AND ( KPUTH-AMT  > 100    AND   KPUTH-AMT  < 1001    )
        // 033000       ADD     1               TO    WK-UB10-CNT
        // 033100       ADD     KPUTH-AMT       TO    WK-UB10-AMT.
        // 033300     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "198")
        // 033400       AND ( KPUTH-AMT  > 1000 )
        // 033500       ADD     1               TO    WK-UB15-CNT
        // 033600       ADD     KPUTH-AMT       TO    WK-UB15-AMT.
        // 033800     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "501")
        // 033900       AND ( KPUTH-AMT  < 20001)
        // 034000       ADD     1               TO    WK-UA06-CNT
        // 034100       ADD     KPUTH-AMT       TO    WK-UA06-AMT.
        // 034300     IF    ( KPUTH-TXTYPE = "U") AND ( KPUTH-SMSERNO = "501")
        // 034400       AND ( KPUTH-AMT  > 20000)
        // 034500       ADD     1               TO    WK-UA15-CNT
        // 034600       ADD     KPUTH-AMT       TO    WK-UA15-AMT.
        // 034620     IF      KPUTH-TXTYPE = "3"
        // 034630       ADD     1               TO    WK-EFCS-CNT
        // 034640       ADD     KPUTH-AMT       TO    WK-EFCS-AMT.
        // 034644     IF      KPUTH-TXTYPE = "4" OR "5" OR "6" OR "7" OR "8"
        // 034646       ADD     1               TO    WK-LINE-CNT
        // 034648       ADD     KPUTH-AMT       TO    WK-LINE-AMT.
        switch (kputhTxtype) {
            case "K":
            case "L":
            case "N":
            case "O":
            case "X":
                wkRptSmserno = kputhSmserno;
                wkKCnt++;
                wkKAmt = wkKAmt.add(kputhAmt);
                break;

            case "T":
            case "Q":
            case "Y":
            case "Z":
            case "2":
                wkTCnt++;
                wkTAmt = wkTAmt.add(kputhAmt);
                break;

            case "U":
                if ("198".equals(kputhSmserno)) {
                    if (kputhAmt.compareTo(new BigDecimal(101)) < 0) {
                        wkUb05Cnt++;
                        wkUb05Amt = wkUb05Amt.add(kputhAmt);
                    } else if (kputhAmt.compareTo(new BigDecimal(1001)) < 0) {
                        wkUb10Cnt++;
                        wkUb10Amt = wkUb10Amt.add(kputhAmt);
                    } else {
                        wkUb15Cnt++;
                        wkUb15Amt = wkUb15Amt.add(kputhAmt);
                    }
                } else if ("501".equals(kputhSmserno)) {
                    if (kputhAmt.compareTo(new BigDecimal(20001)) < 0) {
                        wkUa06Cnt++;
                        wkUa06Amt = wkUa06Amt.add(kputhAmt);
                    } else {
                        wkUa15Cnt++;
                        wkUa15Amt = wkUa15Amt.add(kputhAmt);
                    }
                }
                break;

            case "3":
                wkEfcsCnt++;
                wkEfcsAmt = wkEfcsAmt.add(kputhAmt);
                break;

            case "4":
            case "5":
            case "6":
            case "7":
            case "8":
                wkLineCnt++;
                wkLineAmt = wkLineAmt.add(kputhAmt);
                break;

            default:
                break;
        }
    }
}
