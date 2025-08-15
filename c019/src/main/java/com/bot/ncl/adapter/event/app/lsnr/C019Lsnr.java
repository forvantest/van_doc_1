/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C019;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
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
@Component("C019Lsnr")
@Scope("prototype")
public class C019Lsnr extends BatchListenerCase<C019> {

    @Autowired private ClmrService clmrService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Parse parse;
    @Autowired private FileKPUTH fileKPUTH;
    private C019 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String FILE_INPUT_NAME = "KPUTH";
    private static final String FILE_TMP_NAME = "TMPC019";
    private static final String FILE_NAME = "CL-BH-019";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY";
    private String inputFilePath;
    private String outputFilePath;
    private String sortTmpFilePath;
    private List<String> sortTmpfileC019Contents;
    private List<String> fileC019Contents;
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");

    private String wkPrecode;
    private String wkRptCode;
    private String wkRptDate;
    private String wkRptYYY;
    private String wkRptMM;
    private String wkRptCname;

    private int cnt = 0;
    private int rptCnt = 0;
    private int totCnt = 0;
    private BigDecimal amt = new BigDecimal(0);
    private BigDecimal rptAmt = new BigDecimal(0);
    private BigDecimal totAmt = new BigDecimal(0);

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C019Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C019Lsnr run()");

        init(event);

        sortFileContent();

        query();
        try {
            textFile.writeFileContent(outputFilePath, fileC019Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C019Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        String batchDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱

        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        outputFilePath = fileDir + FILE_NAME;
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC019Contents = new ArrayList<>();
        fileC019Contents = new ArrayList<>();
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "C019Lsnr batchDate = {}", batchDate);
        wkRptDate = batchDate;
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C019Lsnr sortFileContent ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, fileKPUTH);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileKPUTH = {}", fileKPUTH);
            sortTmpfileC019Contents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC019Contents, CHARSET);
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
            String kputhCode = detail.substring(20, 26);
            int kputhCodeInt = parse.string2Integer(kputhCode);
            if (!(kputhCodeInt < 500629 || kputhCodeInt > 500654)) {
                BigDecimal kputhAmt = parse.string2BigDecimal(detail.substring(64, 74));
                cnt++;
                amt = amt.add(kputhAmt);
                totCnt++;
                totAmt = totAmt.add(kputhAmt);
                if (totCnt == 0) {
                    String kputhEntdy = detail.substring(42, 49);
                    wkRptYYY = kputhEntdy.substring(0, 3);
                    wkRptMM = kputhEntdy.substring(3, 5);
                    reportHeader();
                    wkPrecode = kputhCode;
                }
                if (!kputhCode.equals(wkPrecode)) {
                    wkPrecode = kputhCode;
                    wkRptCode = wkPrecode;
                    rptCnt = cnt;
                    rptAmt = amt;
                    ClmrBus clmrBus = clmrService.findById(wkPrecode);
                    wkRptCname = clmrBus.getCname();
                    reportDetail();
                    cnt = 0;
                    amt = new BigDecimal(0);
                }
            }
        }
        if (totCnt == 0) {
            reportNoData();
        } else if (cnt != 0) {
            reportDetail();
        }
        reportFooter();
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 28));
        stringBuilder1.append(formatUtil.padX(" 公路總局各監理站 ", 18));
        stringBuilder1.append(formatUtil.padX(wkRptYYY, 3));
        stringBuilder1.append(formatUtil.padX(" 年 ", 4));
        stringBuilder1.append(formatUtil.padX(wkRptMM, 2));
        stringBuilder1.append(formatUtil.padX(" 月交易統計月報 ", 16));
        stringBuilder1.append(formatUtil.padX(" ", 29));
        fileC019Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 2));
        stringBuilder2.append(formatUtil.padX(" 使用單位： ", 12));
        stringBuilder2.append(formatUtil.padX(" 027 ", 6));
        stringBuilder2.append(formatUtil.padX(" ", 57));
        stringBuilder2.append(formatUtil.padX(" 報表名稱： ", 12));
        stringBuilder2.append(formatUtil.padX("C019", 4));
        stringBuilder2.append(formatUtil.padX(" ", 7));
        fileC019Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 2));
        stringBuilder3.append(formatUtil.padX(" 單位名稱：板橋分行   ", 22));
        stringBuilder3.append(formatUtil.padX(" ", 53));
        stringBuilder3.append(formatUtil.padX(" 印表日期： ", 12));
        stringBuilder3.append(formatUtil.padX(wkRptDate, 10));
        stringBuilder3.append(formatUtil.padX(" ", 2));
        fileC019Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 1));
        fileC019Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 4));
        stringBuilder5.append(formatUtil.padX(" 單位名稱 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 31));
        stringBuilder5.append(formatUtil.padX(" 代收類別 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 8));
        stringBuilder5.append(formatUtil.padX(" 筆數 ", 6));
        stringBuilder5.append(formatUtil.padX(" ", 13));
        stringBuilder5.append(formatUtil.padX(" 金額 ", 6));
        fileC019Contents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(reportUtil.makeGate("-", 100));
        fileC019Contents.add(formatUtil.padX(stringBuilder6.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 4));
        stringBuilder1.append(formatUtil.padX(wkRptCname, 40));
        stringBuilder1.append(formatUtil.padX(" ", 2));
        stringBuilder1.append(formatUtil.padX(wkRptCode, 6));
        stringBuilder1.append(formatUtil.padX(" ", 9));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptCnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptAmt), 14));
        stringBuilder1.append(formatUtil.padX(" ", 10));
        fileC019Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportNoData() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 17));
        stringBuilder1.append(formatUtil.padX("本　月　無　資　料", 20));
        fileC019Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(reportUtil.makeGate("=", 100));
        fileC019Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 47));
        stringBuilder2.append(formatUtil.padX(" 總計 ", 6));
        stringBuilder2.append(formatUtil.padX(" ", 9));
        stringBuilder2.append(formatUtil.padX(cntFormat.format(totCnt), 7));
        stringBuilder2.append(formatUtil.padX(" ", 8));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(totAmt), 14));
        fileC019Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));
    }
}
