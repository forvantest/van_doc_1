/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C077_RPT;
import com.bot.ncl.util.FsapBatchUtil;
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
@Component("C077_RPTLsnr")
@Scope("prototype")
public class C077_RPTLsnr extends BatchListenerCase<C077_RPT> {

    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FilePUTFN filePUTFN;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;

    private static final String CHARSET = "UTF-8";
    private static final String FILE_INPUT_NAME = "PUTFN";
    private static final String FILE_TMP_NAME = "TMPC077";
    private static final String FILE_NAME = "CL-BH-C077";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String inputFilePath;
    private String sortTmpFilePath;
    private String outputFilePath;
    private List<String> sortTmpfileC077Contents;
    private List<String> fileC077Contents;

    private String sdCllbr;
    private String sdAmt;
    private String sdRcptid;
    private String sdTime;
    private String sdUserdata;
    private String rptHYYY;
    private String rptHMM;
    private String rptHDD;
    private String rptCYYY;
    private String rptCMM;
    private String rptCDD;
    private int nbsdy;
    private int wkSeqno;
    private int page = 1;
    private int pageCount = 0;
    private int rptTotcnt = 0;
    private BigDecimal rptTotamt = new BigDecimal(0);

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C077_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C077_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr run()");
        init(event);
        if (textFile.exists(inputFilePath)) {
            sortFileContent();
            query();
            try {
                textFile.writeFileContent(outputFilePath, fileC077Contents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            textFile.deleteFile(sortTmpFilePath);
        }
        batchResponse(event);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C077_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr init ....");

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        String processDate = labelMap.get("PROCESS_DATE");
        int processDateInt = parse.string2Integer(processDate);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY")); // 待中菲APPLE提供正確名稱

        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + ANALY_FILE_PATH
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME;

        sortTmpFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + FILE_TMP_NAME;

        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDateInt + PATH_SEPARATOR + FILE_NAME;

        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC077Contents = new ArrayList<>();
        fileC077Contents = new ArrayList<>();
    }

    private void sortFileContent() throws IOException {
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePUTFN);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
            sortTmpfileC077Contents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC077Contents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        File sortTmpFile = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(109, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(42, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(48, 3, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges);
    }

    private void query() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr reportData ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        String sdDate = parse.decimal2String(nbsdy, 7, 0);
        rptHYYY = sdDate.substring(0, 3);
        rptHMM = sdDate.substring(3, 5);
        rptHDD = sdDate.substring(5, 7);
        reportHeader();
        for (String detail : lines) {
            if (pageCount > 60) {
                changePage();
            }
            String putfnCtl2 = detail.substring(1, 2);
            String putfnCllbr = detail.substring(48, 51);
            String putfnSitdate = detail.substring(109, 117);
            String putfnAmt = detail.substring(118, 130);
            String putfnRcptid = detail.substring(8, 34);
            String putfnTime = detail.substring(42, 48);
            String putfnUserdata = detail.substring(69, 109);
            if (putfnCtl2.equals("1")) {
                sdCllbr = putfnCllbr;
                rptCYYY = putfnSitdate.substring(0, 3);
                rptCMM = putfnSitdate.substring(3, 5);
                rptCDD = putfnSitdate.substring(5, 7);
                sdAmt = putfnAmt;
                sdRcptid = putfnRcptid;
                sdTime = putfnTime;
                sdUserdata = putfnUserdata;
                wkSeqno++;
                rptTotamt = rptTotamt.add(parse.string2BigDecimal(sdAmt));
                rptTotcnt++;
                pageCount++;
                reportDetail();
            }
        }
        if (rptTotcnt == 0) {
            reportNoData();
        }
        if (pageCount != 0 && pageCount < 60 && rptTotcnt != 0) {
            for (int i = pageCount; i < 60; i++) {
                reportBlank();
            }
        }
        reportFooter();
    }

    private void changePage() {
        page++;
        pageCount = 0;
        reportHeader();
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 58));
        stringBuilder1.append(formatUtil.padX(" 全民普發現金每日明細報表－系統日 ", 48));
        fileC077Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" 主辦行　： ", 12));
        stringBuilder2.append(formatUtil.padX("003", 3));
        stringBuilder2.append(formatUtil.padX(" ", 88));
        stringBuilder2.append(formatUtil.padX(" 報表名稱： ", 12));
        stringBuilder2.append(formatUtil.padX("C077    ", 12));
        fileC077Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" 印表日期： ", 12));
        stringBuilder3.append(formatUtil.padX(rptHYYY + "/" + rptHMM + "/" + rptHDD, 9));
        stringBuilder3.append(formatUtil.padX(" ", 82));
        stringBuilder3.append(formatUtil.padX(" 頁　　數： ", 12));
        stringBuilder3.append(formatUtil.padX("" + page, 4));
        fileC077Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilderBlank = new StringBuilder();
        stringBuilderBlank.append(formatUtil.padX(" ", 1));
        fileC077Contents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" 序號 ", 6));
        stringBuilder4.append(formatUtil.padX(" ", 3));
        stringBuilder4.append(formatUtil.padX(" 虛擬帳號 ", 16));
        stringBuilder4.append(formatUtil.padX(" ", 3));
        stringBuilder4.append(formatUtil.padX(" 交易日期 ", 10));
        stringBuilder4.append(formatUtil.padX(" 時間 ", 6));
        stringBuilder4.append(formatUtil.padX(" 代付行 ", 8));
        stringBuilder4.append(formatUtil.padX(" ", 2));
        stringBuilder4.append(formatUtil.padX(" 代付金額 ", 10));
        stringBuilder4.append(formatUtil.padX(" ", 2));
        stringBuilder4.append(formatUtil.padX(" 備註 ", 6));
        fileC077Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilderMakeGate = new StringBuilder();
        stringBuilderMakeGate.append(reportUtil.makeGate("=", 120));
        fileC077Contents.add(formatUtil.padX(stringBuilderMakeGate.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX("" + wkSeqno, 6));
        stringBuilder1.append(formatUtil.padX(" ", 2));
        stringBuilder1.append(formatUtil.padX(sdRcptid, 22));
        stringBuilder1.append(formatUtil.padX(rptCYYY + "/" + rptCMM + "/" + rptCDD, 9));
        stringBuilder1.append(formatUtil.padX(" ", 1));
        stringBuilder1.append(formatUtil.padX(sdTime, 6));
        stringBuilder1.append(formatUtil.padX(" ", 2));
        stringBuilder1.append(formatUtil.padX(sdCllbr, 3));
        stringBuilder1.append(formatUtil.padX(" ", 1));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(sdAmt), 13));
        stringBuilder1.append(formatUtil.padX(" ", 4));
        stringBuilder1.append(formatUtil.padX(sdUserdata, 40));
        fileC077Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 10));
        stringBuilder1.append(formatUtil.padX(" 總筆數 :", 20));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptTotcnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 18));
        stringBuilder1.append(formatUtil.padX(" 總金額 :", 20));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptTotamt), 17));
        fileC077Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilderBlank = new StringBuilder();
        stringBuilderBlank.append(formatUtil.padX(" ", 1));
        fileC077Contents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));
        fileC077Contents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 40));
        stringBuilder2.append(formatUtil.padX(" 經　辦： ", 10));
        stringBuilder2.append(formatUtil.padX(" ", 32));
        stringBuilder2.append(formatUtil.padX(" 主　管： ", 10));
        fileC077Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));
    }

    private void reportNoData() {
        StringBuilder stringBuilderBlank = new StringBuilder();
        stringBuilderBlank.append(formatUtil.padX(" ", 1));
        fileC077Contents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));

        StringBuilder stringBuilderNoData = new StringBuilder();
        stringBuilderNoData.append(formatUtil.padX(" ", 10));
        stringBuilderNoData.append(formatUtil.padX("NO DATA !!", 20));
        fileC077Contents.add(formatUtil.padX(stringBuilderNoData.toString(), 158));
    }

    private void reportBlank() {
        StringBuilder stringBuilderBlank = new StringBuilder();
        stringBuilderBlank.append(formatUtil.padX(" ", 1));
        fileC077Contents.add(formatUtil.padX(stringBuilderBlank.toString(), 158));
    }

    private void batchResponse(C077_RPT event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
