/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C072m;
import com.bot.ncl.util.fileVo.FilePUTFN;
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
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.io.IOException;
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
@Component("C072mLsnr")
@Scope("prototype")
public class C072mLsnr extends BatchListenerCase<C072m> {

    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FilePUTFN filePUTFN;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;

    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String FILE_INPUT_NAME = "PUTFN";
    private static final String FILE_TMP_NAME = "TMPC072M";
    private static final String FILE_NAME = "CL-BH-C072-M";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY";
    private String inputFilePath;
    private String sortTmpFilePath;
    private String outputFilePath;
    private List<String> sortTmpfileC072mContents;
    private List<String> fileC072mContents;
    private String batchDate = "";
    private Map<String, String> textMap;
    private String wkCllbr;
    private String wkCatchDate;
    private String rptHYYY;
    private String rptHMM;
    private String rptCllbr;
    private String rptYYY;
    private String rptMM;
    private String rptDD;
    private BigDecimal wkReccnt = new BigDecimal(0);
    private BigDecimal wkPaycnt = new BigDecimal(0);
    private BigDecimal wkSubreccnt = new BigDecimal(0);
    private BigDecimal wkSubpaycnt = new BigDecimal(0);
    private BigDecimal rptSubreccnt = new BigDecimal(0);
    private BigDecimal rptSubpaycnt = new BigDecimal(0);
    private BigDecimal wkRecamt = new BigDecimal(0);
    private BigDecimal wkPayamt = new BigDecimal(0);
    private BigDecimal wkSubamt = new BigDecimal(0);
    private BigDecimal wkSubrecamt = new BigDecimal(0);
    private BigDecimal wkSubpayamt = new BigDecimal(0);
    private BigDecimal rptSubrecamt = new BigDecimal(0);
    private BigDecimal rptSubpayamt = new BigDecimal(0);
    private BigDecimal totAmt = new BigDecimal(0);

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C072m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C072mLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C072m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C072mLsnr run()");
        init(event);
        sortFileContent();
        query();
        try {
            textFile.writeFileContent(outputFilePath, fileC072mContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C072m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C072mLsnr init ....");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        batchDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱

        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;

        sortTmpFilePath = fileDir + FILE_TMP_NAME;

        outputFilePath = fileDir + FILE_NAME;

        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC072mContents = new ArrayList<>();
        fileC072mContents = new ArrayList<>();
        rptHYYY = batchDate.substring(0, 3);
        rptHMM = batchDate.substring(3, 5);
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

    private void sortFileContent() throws IOException {
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePUTFN);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
            sortTmpfileC072mContents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC072mContents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        File sortTmpFile = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(48, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(34, 8, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges);
    }

    private void query() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C072mLsnr reportData ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePUTFN);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
            String putfnCtl = detail.substring(0, 2);
            String putfnDate = detail.substring(34, 42);
            String putfnCllbr = detail.substring(48, 51);
            String putfnUserdata = detail.substring(69, 109);
            int putfnUserdataIntCheck = parse.string2Integer(putfnUserdata.substring(8, 9));
            String putfnDateCheck = putfnDate.substring(3, 7);
            String wkFdate = batchDate.substring(0, 4);
            if (!(!putfnCtl.equals("11") || !putfnDateCheck.equals(wkFdate))) {
                if (!wkCllbr.equals(putfnCllbr)) {
                    if (!wkCllbr.isBlank()) {
                        rptYYY = wkCatchDate.substring(1, 4);
                        rptMM = wkCatchDate.substring(4, 6);
                        rptDD = wkCatchDate.substring(6, 8);
                        wkRecamt = wkReccnt.multiply(new BigDecimal(3));
                        wkPayamt = wkPaycnt.multiply(new BigDecimal(4));
                        wkSubamt = wkRecamt.subtract(wkPayamt);
                        wkSubreccnt = wkSubreccnt.add(wkReccnt);
                        wkSubrecamt = wkSubrecamt.add(wkRecamt);
                        wkSubpaycnt = wkSubpaycnt.add(wkPaycnt);
                        wkSubpayamt = wkSubpayamt.add(wkPayamt);
                        reportDetail();
                        wkReccnt = new BigDecimal(0);
                        wkRecamt = new BigDecimal(0);
                        wkPaycnt = new BigDecimal(0);
                        wkPayamt = new BigDecimal(0);
                        wkSubamt = new BigDecimal(0);
                        rptSubreccnt = wkSubreccnt;
                        rptSubrecamt = wkSubrecamt;
                        rptSubpaycnt = wkSubpaycnt;
                        rptSubpayamt = wkSubpayamt;
                        totAmt = wkSubrecamt.subtract(wkSubpayamt);
                        reportFooter();
                    } else {
                        wkCllbr = putfnCllbr;
                        wkCatchDate = putfnDate;
                        rptCllbr = wkCllbr;
                        reportHeader();
                    }
                }
                if (!putfnDate.equals(wkCatchDate)) {
                    rptYYY = wkCatchDate.substring(1, 4);
                    rptMM = wkCatchDate.substring(4, 6);
                    rptDD = wkCatchDate.substring(6, 8);
                    wkRecamt = wkReccnt.multiply(new BigDecimal(3));
                    wkPayamt = wkPaycnt.multiply(new BigDecimal(4));
                    wkSubamt = wkRecamt.subtract(wkPayamt);
                    wkSubreccnt = wkSubreccnt.add(wkReccnt);
                    wkSubrecamt = wkSubrecamt.add(wkRecamt);
                    wkSubpaycnt = wkSubpaycnt.add(wkPaycnt);
                    wkSubpayamt = wkSubpayamt.add(wkPayamt);
                    reportDetail();
                    wkReccnt = new BigDecimal(0);
                    wkRecamt = new BigDecimal(0);
                    wkPaycnt = new BigDecimal(0);
                    wkPayamt = new BigDecimal(0);
                    wkSubamt = new BigDecimal(0);
                    wkCatchDate = putfnDate;
                }
                if (putfnUserdataIntCheck >= 0 && putfnUserdataIntCheck <= 9) {
                    wkReccnt = wkReccnt.add(new BigDecimal(1));
                } else {
                    wkPaycnt = wkPaycnt.add(new BigDecimal(1));
                }
            }
        }
        if (wkReccnt.compareTo(new BigDecimal(0)) != 0
                || wkPaycnt.compareTo(new BigDecimal(0)) != 0) {
            rptYYY = wkCatchDate.substring(1, 4);
            rptMM = wkCatchDate.substring(4, 6);
            rptDD = wkCatchDate.substring(6, 8);
            wkRecamt = wkReccnt.multiply(new BigDecimal(3));
            wkPayamt = wkPaycnt.multiply(new BigDecimal(4));
            wkSubamt = wkRecamt.subtract(wkPayamt);
            wkSubreccnt = wkSubreccnt.add(wkReccnt);
            wkSubrecamt = wkSubrecamt.add(wkRecamt);
            wkSubpaycnt = wkSubpaycnt.add(wkPaycnt);
            wkSubpayamt = wkSubpayamt.add(wkPayamt);
            reportDetail();
            wkReccnt = new BigDecimal(0);
            wkRecamt = new BigDecimal(0);
            wkPaycnt = new BigDecimal(0);
            wkPayamt = new BigDecimal(0);
            wkSubamt = new BigDecimal(0);
            rptSubreccnt = wkSubreccnt;
            rptSubrecamt = wkSubrecamt;
            rptSubpaycnt = wkSubpaycnt;
            rptSubpayamt = wkSubpayamt;
            totAmt = wkSubrecamt.subtract(wkSubpayamt);
            reportFooter();
        }
        if (wkSubreccnt.compareTo(new BigDecimal(0)) == 0
                && wkSubpaycnt.compareTo(new BigDecimal(0)) == 0) {
            rptCllbr = wkCllbr;
            reportHeader();
            reportNoData();
            rptSubreccnt = wkSubreccnt;
            rptSubrecamt = wkSubrecamt;
            rptSubpaycnt = wkSubpaycnt;
            rptSubpayamt = wkSubpayamt;
            totAmt = wkSubrecamt.subtract(wkSubpayamt);
            reportFooter();
        }
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 28));
        stringBuilder1.append(formatUtil.padX(" 臺灣銀行代收汽車燃料使用費手續費統計表 ", 40));
        fileC072mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilderBlank = new StringBuilder();
        stringBuilderBlank.append(formatUtil.padX(" ", 1));
        fileC072mContents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 8));
        stringBuilder2.append(formatUtil.padX(" 代收月份： ", 12));
        stringBuilder2.append(formatUtil.padX(rptHYYY, 3));
        stringBuilder2.append(formatUtil.padX(" 年 ", 4));
        stringBuilder2.append(formatUtil.padX(rptHMM, 2));
        stringBuilder2.append(formatUtil.padX(" 月 ", 4));
        stringBuilder2.append(formatUtil.padX(" ", 39));
        stringBuilder2.append(formatUtil.padX(" 報表代號： ", 12));
        stringBuilder2.append(formatUtil.padX("C072-M", 6));
        fileC072mContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 8));
        stringBuilder3.append(formatUtil.padX(" 分行代號： ", 12));
        stringBuilder3.append(formatUtil.padX(rptCllbr, 3));
        fileC072mContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 24));
        stringBuilder4.append(formatUtil.padX(" 汽燃費本費 ", 12));
        stringBuilder4.append(formatUtil.padX(" ", 15));
        stringBuilder4.append(formatUtil.padX(" 汽燃費違費 ", 12));
        fileC072mContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 8));
        stringBuilder5.append(formatUtil.padX(" 日期 ", 4));
        stringBuilder5.append(formatUtil.padX(" ", 6));
        stringBuilder5.append(formatUtil.padX(" 筆數 ", 4));
        stringBuilder5.append(formatUtil.padX(" ", 6));
        stringBuilder5.append(formatUtil.padX(" 應收金額 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX(" 筆數 ", 4));
        stringBuilder5.append(formatUtil.padX(" ", 6));
        stringBuilder5.append(formatUtil.padX(" 應付金額 ", 10));
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX(" 應收（應付）差額 ", 18));
        fileC072mContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilderMakeGate = new StringBuilder();
        stringBuilderMakeGate.append(reportUtil.makeGate("-", 85));
        fileC072mContents.add(formatUtil.padX(stringBuilderMakeGate.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(rptYYY, 3));
        stringBuilder1.append(formatUtil.padX(".", 1));
        stringBuilder1.append(formatUtil.padX(rptMM, 2));
        stringBuilder1.append(formatUtil.padX(".", 1));
        stringBuilder1.append(formatUtil.padX(rptDD, 2));
        stringBuilder1.append(formatUtil.padX(" ", 1));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(wkReccnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 7));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkRecamt), 9));
        stringBuilder1.append(formatUtil.padX(" ", 4));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(wkPaycnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 7));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkPayamt), 9));
        stringBuilder1.append(formatUtil.padX(" ", 11));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkSubamt), 10));
        fileC072mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilderMakeGate = new StringBuilder();
        stringBuilderMakeGate.append(reportUtil.makeGate("-", 85));
        fileC072mContents.add(formatUtil.padX(stringBuilderMakeGate.toString(), 158));

        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(" 合計： ", 8));
        stringBuilder1.append(formatUtil.padX(" ", 2));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptSubreccnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 7));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptSubrecamt), 9));
        stringBuilder1.append(formatUtil.padX(" ", 4));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptSubpaycnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 7));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptSubpayamt), 9));
        stringBuilder1.append(formatUtil.padX(" ", 11));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(totAmt), 10));
        fileC072mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 8));
        stringBuilder2.append(formatUtil.padX(" 註：", 8));
        fileC072mContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 8));
        stringBuilder3.append(formatUtil.padX("１、代收汽燃費本費每筆應收票交所手續費新台幣３元整。", 60));
        fileC072mContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 8));
        stringBuilder4.append(formatUtil.padX("２、代收汽燃費違費每筆應付票交所手續費新台幣４元整。", 60));
        fileC072mContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 8));
        stringBuilder5.append(formatUtil.padX("３、「應收（應付）差額」欄合計數與票交所提供之費用明  細表「金融業代收服務（ＦＣＳ）", 100));
        fileC072mContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(formatUtil.padX(" ", 12));
        stringBuilder6.append(
                formatUtil.padX("收入」金額核對相符（信義分行除外）後貸入手續費收  入科目帳（ 410305-0050-0015 手", 100));
        fileC072mContents.add(formatUtil.padX(stringBuilder6.toString(), 158));

        StringBuilder stringBuilder7 = new StringBuilder();
        stringBuilder7.append(formatUtil.padX(" ", 12));
        stringBuilder7.append(formatUtil.padX("續費收入－代理收付手續費－代收汽燃費及違費），如  為負數，應沖轉該收入科目，不得", 100));
        fileC072mContents.add(formatUtil.padX(stringBuilder7.toString(), 158));

        StringBuilder stringBuilder8 = new StringBuilder();
        stringBuilder8.append(formatUtil.padX(" ", 12));
        stringBuilder8.append(formatUtil.padX("併入手續費用科目出帳。", 50));
        fileC072mContents.add(formatUtil.padX(stringBuilder8.toString(), 158));
    }

    private void reportNoData() {
        StringBuilder stringBuilderNoData = new StringBuilder();
        stringBuilderNoData.append(formatUtil.padX(" ", 18));
        stringBuilderNoData.append(formatUtil.padX("　本　　月　　無　　資　　料　", 50));
        fileC072mContents.add(formatUtil.padX(stringBuilderNoData.toString(), 158));
    }
}
