/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C059;
import com.bot.ncl.util.fileVo.FileKPUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
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
@Component("C059Lsnr")
@Scope("prototype")
public class C059Lsnr extends BatchListenerCase<C059> {

    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Parse parse;
    @Autowired private FileKPUTH fileKPUTH;
    private C059 event;

    private String inputFilePath; // 讀檔路徑
    private String sortTmpFilePath; // SortTmp檔路徑
    private String outputFilePath; // 產檔路徑
    private List<String> sortTmpfileC059Contents; // Tmp檔案內容
    private List<String> fileC059Contents; // 檔案內容
    private String batchDate; // 批次日期(民國年yyyymmdd)
    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTFN"; // 讀檔檔名
    private static final String FILE_TMP_NAME = "TMPC009M"; // tmp檔名
    private static final String FILE_NAME = "C009M"; // 檔名1
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式

    private String wkRptPdate;
    private String wkRptPdateYYY;
    private String wkRptPdateMM;
    private String wkRptPdateDD;
    private String wkRptTdate;
    private String wkRptTdateYYY;
    private String wkRptTdateMM;
    private String wkTxtype;
    private String wkRptType;
    private String kputhTxtype;
    private String kputhCode;
    private String wkPreCode;
    private String rptCode;
    private String wkRptSmserno;
    private String kputhSmserno;
    private String wkCountFee;
    private int cnt = 0;
    private int rptCnt = 0;
    private int totCnt = 0;
    private int pageCnt = 0;
    private int page = 1;
    private BigDecimal kputhAmt = new BigDecimal(0);
    private BigDecimal wkOpfee = new BigDecimal(0);
    private BigDecimal wkRecOpfee = new BigDecimal(0);
    private BigDecimal wkRptOpfee = new BigDecimal(0);
    private BigDecimal wkTotOpfee = new BigDecimal(0);
    private BigDecimal wkRecUplamt = new BigDecimal(0);
    private BigDecimal wkRptUplamt = new BigDecimal(0);
    private BigDecimal wkTotUplamt = new BigDecimal(0);
    private BigDecimal wkCostfee = new BigDecimal(0);
    private BigDecimal wkRecFeecost = new BigDecimal(0);
    private BigDecimal wkRptFeecost = new BigDecimal(0);
    private BigDecimal wkTotFeecost = new BigDecimal(0);
    private BigDecimal wkLiqamt = new BigDecimal(0);
    private BigDecimal wkRecLiqamt = new BigDecimal(0);
    private BigDecimal wkRptLiqamt = new BigDecimal(0);
    private BigDecimal wkTotLiqamt = new BigDecimal(0);

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C059 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C059Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C059 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C059Lsnr run()");

        init(event);

        sortFileContent();

        query();

        try {
            textFile.writeFileContent(outputFilePath, fileC059Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C059 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C059Lsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        batchDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱

        // 讀檔路徑
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        // 暫存檔路徑
        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        // 產檔路徑
        outputFilePath = fileDir + FILE_NAME;
        // 刪除舊檔
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC059Contents = new ArrayList<>();
        fileC059Contents = new ArrayList<>();

        wkRptPdate = batchDate;
        wkRptPdateYYY = wkRptPdate.substring(0, 3);
        wkRptPdateMM = wkRptPdate.substring(3, 5);
        wkRptPdateDD = wkRptPdate.substring(5, 7);
        wkRptTdate = batchDate.substring(0, 5);
        wkRptTdateYYY = wkRptTdate.substring(0, 3);
        wkRptTdateMM = wkRptTdateMM.substring(3, 5);
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

    private void sortFileContent() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C059Lsnr sortFileContent ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, fileKPUTH);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileKPUTH = {}", fileKPUTH);
            sortTmpfileC059Contents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC059Contents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
    }

    private void query() {
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        if (Objects.isNull(lines) || lines.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "kputhFileContent is null");
            return;
        }

        for (String detail : lines) {
            text2VoFormatter.format(detail, fileKPUTH);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileKPuth = {}", fileKPUTH);
            kputhTxtype = detail.substring(111, 112);
            kputhCode = detail.substring(20, 26);
            kputhAmt = parse.string2BigDecimal(detail.substring(64, 74));
            kputhSmserno = detail.substring(74, 114).substring(0, 3);
            if (totCnt == 0) {
                wkTxtype = detail.substring(111, 112);
                compareTxtype();
                reportHeader();
            }
            if (!kputhTxtype.equals(wkTxtype)) {
                changePage();
            }
            if (pageCnt > 46) {
                changePage();
            }
            if (!kputhCode.equals(wkPreCode)) {
                rptCode = wkPreCode;
                wkRptLiqamt = wkRecLiqamt;
                wkRptUplamt = wkRecUplamt;
                wkRptFeecost = wkRecFeecost;
                wkRptOpfee = wkRecOpfee;
                rptCnt = cnt;
                wkTotLiqamt = wkTotLiqamt.add(wkRecLiqamt);
                wkTotUplamt = wkTotUplamt.add(wkRecUplamt);
                wkTotFeecost = wkTotFeecost.add(wkRecFeecost);
                wkTotOpfee = wkTotOpfee.add(wkRecOpfee);
                reportDetail();
                wkRptLiqamt = new BigDecimal(0);
                wkRptUplamt = new BigDecimal(0);
                wkRptFeecost = new BigDecimal(0);
                wkRptOpfee = new BigDecimal(0);
                rptCnt = 0;
                wkRecLiqamt = new BigDecimal(0);
                wkRecUplamt = new BigDecimal(0);
                wkRecFeecost = new BigDecimal(0);
                wkRecOpfee = new BigDecimal(0);
                cnt = 0;
                wkRptSmserno = kputhSmserno;
                wkPreCode = kputhCode;
            }
            if (wkTxtype.equals("3")) {
                wkRptSmserno = "";
                wkRecUplamt = wkRecUplamt.add(kputhAmt);
                wkCountFee = kputhSmserno;
                wkOpfee =
                        parse.string2BigDecimal(wkCountFee)
                                .multiply(BigDecimal.valueOf(0.32))
                                .setScale(2, RoundingMode.HALF_UP);
                wkRecOpfee = wkRecOpfee.add(wkOpfee);
                wkCostfee = parse.string2BigDecimal(wkCountFee).subtract(wkOpfee);
                wkRecFeecost = wkRecFeecost.add(wkCostfee);
                wkLiqamt = kputhAmt.subtract(wkCostfee);
                wkRecLiqamt = wkRecLiqamt.add(wkLiqamt);
                cnt++;
                totCnt++;
                pageCnt++;
                wkCountFee = "0";
                wkOpfee = new BigDecimal(0);
                wkCostfee = new BigDecimal(0);
                wkLiqamt = new BigDecimal(0);
            } else {
                if (!kputhSmserno.equals(wkRptSmserno)) {
                    rptCode = wkPreCode;
                    wkRptLiqamt = wkRecLiqamt;
                    wkRptUplamt = wkRecUplamt;
                    wkRptFeecost = wkRecFeecost;
                    wkRptOpfee = wkRecOpfee;
                    rptCnt = cnt;
                    wkTotLiqamt = wkTotLiqamt.add(wkRecLiqamt);
                    wkTotUplamt = wkTotUplamt.add(wkRecUplamt);
                    wkTotFeecost = wkTotFeecost.add(wkRecFeecost);
                    wkTotOpfee = wkTotOpfee.add(wkRecOpfee);
                    reportDetail();
                    wkRptLiqamt = new BigDecimal(0);
                    wkRptUplamt = new BigDecimal(0);
                    wkRptFeecost = new BigDecimal(0);
                    wkRptOpfee = new BigDecimal(0);
                    rptCnt = 0;
                    wkRecLiqamt = new BigDecimal(0);
                    wkRecUplamt = new BigDecimal(0);
                    wkRecFeecost = new BigDecimal(0);
                    wkRecOpfee = new BigDecimal(0);
                    cnt = 0;
                    wkRptSmserno = kputhSmserno;
                    wkRecUplamt = wkRecUplamt.add(kputhAmt);
                    cnt++;
                    totCnt++;
                    pageCnt++;
                    if (kputhSmserno.equals("6D1")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(6));
                    }
                    if (kputhSmserno.equals("63F") || kputhSmserno.equals("AG2")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(6));
                        wkRecOpfee = wkRecOpfee.add(new BigDecimal(2));
                    }
                    if (kputhSmserno.equals("AG3") || kputhSmserno.equals("AG5")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(6));
                        wkRecOpfee = wkRecOpfee.add(new BigDecimal(4));
                    }
                    if (kputhSmserno.equals("6IJ") || kputhSmserno.equals("C69")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(7));
                        wkRecOpfee = wkRecOpfee.add(new BigDecimal(3));
                    }
                    if (kputhSmserno.equals("6T4") || kputhSmserno.equals("63P")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(18));
                    }
                    if (kputhSmserno.equals("63G") || kputhSmserno.equals("63R")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(10));
                        wkRecOpfee = wkRecOpfee.add(new BigDecimal(2));
                    }
                    if (kputhSmserno.equals("63B")
                            || kputhSmserno.equals("63T")
                            || kputhSmserno.equals("MU0")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(15));
                    }
                    if (kputhSmserno.equals("MV0")) {
                        wkRecFeecost = wkRecFeecost.add(new BigDecimal(20));
                    }
                    wkRecLiqamt = wkRecFeecost.subtract(wkRecUplamt);
                }
            }
        }
        if (totCnt == 0) {
            reportNoData();
        } else if (cnt != 0) {
            wkPreCode = kputhCode;
            wkRptLiqamt = wkRecLiqamt;
            wkRptUplamt = wkRecUplamt;
            wkRptFeecost = wkRecFeecost;
            wkRptOpfee = wkRecOpfee;
            rptCnt = cnt;
            wkTotLiqamt = wkTotLiqamt.add(wkRecLiqamt);
            wkTotUplamt = wkTotUplamt.add(wkRecUplamt);
            wkTotFeecost = wkTotFeecost.add(wkRecFeecost);
            wkTotOpfee = wkTotOpfee.add(wkRecOpfee);
            reportDetail();
            wkRptLiqamt = new BigDecimal(0);
            wkRptUplamt = new BigDecimal(0);
            wkRptFeecost = new BigDecimal(0);
            wkRptOpfee = new BigDecimal(0);
            rptCnt = 0;
            wkRecLiqamt = new BigDecimal(0);
            wkRecUplamt = new BigDecimal(0);
            wkRecFeecost = new BigDecimal(0);
            wkRecOpfee = new BigDecimal(0);
            cnt = 0;
            wkRptSmserno = kputhSmserno;
        }
        reportFooter();
    }

    private void compareTxtype() {
        if (wkTxtype.equals("3")) {
            wkRptType = "ＥＦＣＳ";
        }
        if (wkTxtype.equals("K")) {
            wkRptType = "統一超商";
        }
        if (wkTxtype.equals("L")) {
            wkRptType = "萊爾富超";
        }
        if (wkTxtype.equals("N")) {
            wkRptType = "全家超商";
        }
        if (wkTxtype.equals("O")) {
            wkRptType = "ＯＫ超商";
        }
    }

    private void changePage() {
        if (totCnt == 0) {
            wkRptSmserno = kputhSmserno;
            wkPreCode = kputhCode;
        } else {
            page++;
            pageCnt = 0;
        }
        compareTxtype();
        reportHeader();
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 45));
        stringBuilder1.append(formatUtil.padX(" 外部代收－代收類別管道手續費用統計月報表 ", 42));
        stringBuilder1.append(formatUtil.padX(" ", 24));
        stringBuilder1.append(formatUtil.padX("PAGE ： ", 8));
        stringBuilder1.append(formatUtil.padX("" + page, 3));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        fileC059Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 2));
        stringBuilder2.append(formatUtil.padX(" 代收機構： ", 12));
        stringBuilder2.append(formatUtil.padX(wkRptType, 10));
        stringBuilder2.append(formatUtil.padX(" ", 87));
        stringBuilder2.append(formatUtil.padX("FROM ： ", 8));
        stringBuilder2.append(formatUtil.padX("C059", 4));
        stringBuilder2.append(formatUtil.padX(" ", 7));
        fileC059Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 2));
        stringBuilder3.append(formatUtil.padX(" 報表日期：  ", 13));
        stringBuilder3.append(formatUtil.padX(wkRptTdateYYY + "/" + wkRptTdateMM, 6));
        stringBuilder3.append(formatUtil.padX(" ", 86));
        stringBuilder3.append(formatUtil.padX(" 印表日期： ", 12));
        stringBuilder3.append(
                formatUtil.padX(wkRptPdateYYY + "/" + wkRptPdateMM + "/" + wkRptPdateDD, 9));
        stringBuilder3.append(formatUtil.padX(" ", 2));
        fileC059Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 1));
        fileC059Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX(" 代收類別 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 4));
        stringBuilder5.append(formatUtil.padX(" 手續費種類 ", 12));
        stringBuilder5.append(formatUtil.padX(" ", 6));
        stringBuilder5.append(formatUtil.padX(" 清算金額 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 6));
        stringBuilder5.append(formatUtil.padX(" 入帳金額 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX(" 手續費用 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX(" 分潤金額 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 7));
        stringBuilder5.append(formatUtil.padX(" 筆數 ", 6));
        stringBuilder5.append(formatUtil.padX(" ", 24));
        fileC059Contents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(reportUtil.makeGate("-", 130));
        fileC059Contents.add(formatUtil.padX(stringBuilder6.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 7));
        stringBuilder1.append(formatUtil.padX(rptCode, 6));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(wkRptSmserno, 3));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkRptLiqamt), 14));
        stringBuilder1.append(formatUtil.padX(" ", 2));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkRptUplamt), 14));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkRptFeecost), 9));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkRptOpfee), 9));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptCnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 25));
        fileC059Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(reportUtil.makeGate("=", 130));
        fileC059Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 7));
        stringBuilder2.append(formatUtil.padX(" 總計 ", 4));
        stringBuilder2.append(formatUtil.padX(" ", 19));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(wkTotLiqamt), 14));
        stringBuilder2.append(formatUtil.padX(" ", 2));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(wkTotUplamt), 14));
        stringBuilder2.append(formatUtil.padX(" ", 6));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(wkTotFeecost), 9));
        stringBuilder2.append(formatUtil.padX(" ", 6));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(wkTotOpfee), 9));
        stringBuilder2.append(formatUtil.padX(" ", 6));
        stringBuilder2.append(formatUtil.padX(cntFormat.format(totCnt), 7));
        fileC059Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));
    }

    private void reportNoData() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(" 本　月　無　資　料 ", 25));
        fileC059Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }
}
