/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C009m;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C009mLsnr")
@Scope("prototype")
public class C009mLsnr extends BatchListenerCase<C009m> {

    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FilePUTFN filePUTFN;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    private C009m event;

    private String wkWdate;
    private String rptBdate;
    private String rptMdate;
    private String rptEdate;
    private String mvdateYYY;
    private String mvdateMM;
    private String mvdateDD;
    private String trdateEYYY;
    private String trdateEMM;
    private String trdateEDD;
    private String trdateSYYY;
    private String trdateSMM;
    private String trdateSDD;
    private String rptYYY;
    private String rptMM;
    private int page = 1;
    private int pageCnt = 0;
    private int wkCkdDD;
    private int wkCkdMM;
    private int wkCkdYYY;
    private int tmndy;
    private int weekdy;
    private int tbsdy;
    private int lbsdy;
    private int nbsdy;
    private int wkBdate;
    private int wkEdate;
    private int wkCheckDate;
    private int lmnDD;
    private int lmnMM;
    private int wkMvdp;
    private int holidy;
    private boolean wkFlone = true;
    private boolean wkFpYYY = true;
    private BigDecimal rptAmt = new BigDecimal(0);
    private BigDecimal totAmt = new BigDecimal(0);
    private BigDecimal cnt = new BigDecimal(0);
    private BigDecimal rptCnt = new BigDecimal(0);
    private BigDecimal totCnt = new BigDecimal(0);
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String FILE_INPUT_NAME = "PUTFN";
    private static final String FILE_TMP_NAME = "TMPC009M";
    private static final String FILE_NAME = "CL-BH-009-30200-M";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY";
    private String inputFilePath;
    private String sortTmpFilePath;
    private String outputFilePath;
    private List<String> sortTmpfileC009mContents;
    private List<String> fileC009mContents;
    private String batchDate = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C009m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C009mLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C009m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C009mLsnr run()");
        init(event);
        sortFileContent();
        reportData();

        try {
            textFile.writeFileContent(outputFilePath, fileC009mContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C009m event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C009mLsnr init ....");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        batchDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱

        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;

        sortTmpFilePath = fileDir + FILE_TMP_NAME;

        outputFilePath = fileDir + FILE_NAME;

        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC009mContents = new ArrayList<>();
        fileC009mContents = new ArrayList<>();

        tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        tmndy = event.getAggregateBuffer().getTxCom().getTmndy();
        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        weekdy = event.getAggregateBuffer().getTxCom().getWeekly();
        nbsdy = event.getAggregateBuffer().getTxCom().getNbsdy();
        holidy = event.getAggregateBuffer().getTxCom().getHolidy();

        lmnDD =
                parse.string2Integer(
                        parse.decimal2String(event.getAggregateBuffer().getTxCom().getLmndy(), 7, 0)
                                .substring(5, 7));
        lmnMM =
                parse.string2Integer(
                        parse.decimal2String(event.getAggregateBuffer().getTxCom().getLmndy(), 7, 0)
                                .substring(3, 5));

        rptYYY = batchDate.substring(0, 3);
        rptMM = batchDate.substring(3, 5);
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
            sortTmpfileC009mContents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC009mContents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        File sortTmpFile = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(34, 8, SortBy.ASC));
        externalSortUtil.sortingFile(sortTmpFile, sortTmpFile, keyRanges);
    }

    private void reportData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C009mLsnr reportData ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePUTFN);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
            String putfnDate = detail.substring(36, 44);
            int putfnDateInt = parse.string2Integer(putfnDate);
            if (detail.substring(0, 2).equals("11")
                    && (!(parse.decimal2String(wkMvdp, 7, 0)
                            .substring(3, 5)
                            .equals(batchDate.substring(3, 5))))) {
                cnt = cnt.add(new BigDecimal(1));
                totCnt = totCnt.add(new BigDecimal(1));
                if (wkBdate == 0 && wkEdate == 0) {
                    getTrd();
                    getMvd();
                    wkWdate = parse.decimal2String(wkBdate, 7, 0);
                }
                if (putfnDateInt < wkBdate || putfnDateInt > wkEdate) {
                    getTrd();
                    getMvd();
                }
                if (pageCnt > 47) {
                    page++;
                    pageCnt = 0;
                    reportHeader();
                }
                if (wkFlone) {
                    wkFlone = false;
                    wkWdate = parse.decimal2String(wkBdate, 7, 0);
                    rptBdate = parse.decimal2String(wkBdate, 7, 0);
                    rptEdate = parse.decimal2String(wkEdate, 7, 0);
                    rptMdate = parse.decimal2String(wkMvdp, 7, 0);
                }
                if (!(wkWdate.equals(parse.decimal2String(wkBdate, 7, 0)))) {
                    pageCnt++;
                    wkWdate = parse.decimal2String(wkBdate, 7, 0);
                    String rptMvdate = rptMdate;
                    mvdateYYY = rptMvdate.substring(0, 3);
                    mvdateMM = rptMvdate.substring(3, 5);
                    mvdateDD = rptMvdate.substring(5, 7);
                    String rptTrdateE = rptEdate;
                    trdateEYYY = rptTrdateE.substring(0, 3);
                    trdateEMM = rptTrdateE.substring(3, 5);
                    trdateEDD = rptTrdateE.substring(5, 7);
                    String rptTrdateS = rptBdate;
                    trdateSYYY = rptTrdateS.substring(0, 3);
                    trdateSMM = rptTrdateS.substring(3, 5);
                    trdateSDD = rptTrdateS.substring(5, 7);
                    rptCnt = cnt;
                    totAmt = cnt.multiply(new BigDecimal(3));
                    rptAmt = totAmt;
                    if (wkFpYYY) {
                        if (rptBdate.substring(0, 3).equals(rptYYY)) {
                            wkFpYYY = false;
                        } else {
                            mvdateYYY = "   ";
                            trdateEYYY = "   ";
                            trdateSYYY = "   ";
                        }
                    }
                    reportDetail();
                    cnt = new BigDecimal(0);
                    rptCnt = new BigDecimal(0);
                    rptAmt = new BigDecimal(0);
                }
            }
        }
        if (totCnt.compareTo(BigDecimal.ZERO) == 0) {
            reportNoData();
        } else {
            reportFooter();
        }
    }

    private void getTrd() {
        wkBdate = tbsdy;
        int wkMinDD = weekdy;
        Set<String> checkFirst = Set.of("1", "2", "3");
        Set<String> checkSecond = Set.of("4", "5", "6", "7");
        String weekdyString = parse.decimal2String(weekdy, 1, 0);
        if (checkFirst.contains(weekdyString)) {
            wkMinDD--;
        }
        if (checkSecond.contains(weekdyString)) {
            wkMinDD = wkMinDD - 4;
        }
        wkBdate = wkBdate - wkMinDD;
        wkCheckDate = wkBdate;
        mckDate();
        wkBdate = wkCheckDate;
        if (checkFirst.contains(weekdyString)) {
            wkEdate = wkBdate + 2;
        }
        if (checkSecond.contains(weekdyString)) {
            wkEdate = wkBdate + 3;
        }
        wkCheckDate = wkEdate;
        ackDate();
        wkEdate = wkCheckDate;
        wkMvdp = wkCheckDate;
        if (holidy == 1) {
            wkBdate = nbsdy;
        }
        if (holidy == 1) {
            wkEdate = lbsdy;
        }
    }

    private void ackDate() {
        int tmnDD = parse.string2Integer(parse.decimal2String(tmndy, 7, 0).substring(5, 7));
        if (wkCkdDD > tmnDD) {
            wkCkdDD = parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(5, 7));
            wkCkdMM = parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(3, 5));
            wkCkdYYY =
                    parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(0, 3));
            wkCkdDD = wkCkdDD - tmnDD;
            wkCkdMM++;
        }
        if (wkCkdMM > 12) {
            wkCkdMM = wkCkdMM - 12;
            wkCkdYYY++;
        }
        wkCheckDate =
                parse.string2Integer(
                        parse.decimal2String(wkCkdYYY, 3, 0)
                                + parse.decimal2String(wkCkdMM, 2, 0)
                                + parse.decimal2String(wkCkdDD, 2, 0));
    }

    private void mckDate() {
        if (wkCkdDD > 80) {
            wkCkdDD = parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(5, 7));
            wkCkdMM = parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(3, 5));
            wkCkdYYY =
                    parse.string2Integer(parse.decimal2String(wkCheckDate, 7, 0).substring(0, 3));
            wkCkdDD = lmnDD - wkCkdDD;
        }
        if (wkCkdDD == 0) {
            wkCkdDD = lmnDD;
            wkCkdMM--;
        }
        if (wkCkdMM == 0) {
            wkCkdMM = lmnMM;
            wkCkdYYY--;
        }
        wkCheckDate =
                parse.string2Integer(
                        parse.decimal2String(wkCkdYYY, 3, 0)
                                + parse.decimal2String(wkCkdMM, 2, 0)
                                + parse.decimal2String(wkCkdDD, 2, 0));
    }

    private void getMvd() {
        wkMvdp = wkMvdp + 2;
        wkCheckDate = wkMvdp;
        ackDate();
        wkMvdp = wkCheckDate;
        if (holidy == 1) {
            wkMvdp = nbsdy;
        }
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 24));
        stringBuilder1.append(formatUtil.padX(" 臺灣銀行公館分行　代收台電公司電費手續費月結表 ", 48));
        fileC009mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 8));
        stringBuilder2.append(formatUtil.padX(" 代收年月： ", 12));
        stringBuilder2.append(formatUtil.padX(rptYYY, 4));
        stringBuilder2.append(formatUtil.padX(" 年 ", 4));
        stringBuilder2.append(formatUtil.padX(rptMM, 3));
        stringBuilder2.append(formatUtil.padX(" 月 ", 4));
        stringBuilder2.append(formatUtil.padX(" ", 39));
        stringBuilder2.append(formatUtil.padX(" 頁　　次： ", 12));
        stringBuilder2.append(formatUtil.padX("" + page, 3));
        fileC009mContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 72));
        stringBuilder3.append(formatUtil.padX(" 報表名稱： ", 12));
        stringBuilder3.append(formatUtil.padX("C009-30200-M", 12));
        fileC009mContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 72));
        stringBuilder4.append(formatUtil.padX(" 單　　位：新臺幣元 ", 20));
        fileC009mContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 72));
        fileC009mContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(formatUtil.padX(" ", 11));
        stringBuilder6.append(formatUtil.padX(" 交易起日 ", 10));
        stringBuilder6.append(formatUtil.padX(" ", 5));
        stringBuilder6.append(formatUtil.padX(" 交易迄日 ", 10));
        stringBuilder6.append(formatUtil.padX(" ", 7));
        stringBuilder6.append(formatUtil.padX(" 解繳日 ", 8));
        stringBuilder6.append(formatUtil.padX(" ", 5));
        stringBuilder6.append(formatUtil.padX(" 交易筆數 ", 10));
        stringBuilder6.append(formatUtil.padX(" ", 7));
        stringBuilder6.append(formatUtil.padX(" 手續費 ", 8));
        fileC009mContents.add(formatUtil.padX(stringBuilder6.toString(), 158));

        StringBuilder stringBuilder7 = new StringBuilder();
        stringBuilder7.append(reportUtil.makeGate("-", 85));
        fileC009mContents.add(formatUtil.padX(stringBuilder7.toString(), 158));

        StringBuilder stringBuilder8 = new StringBuilder();
        stringBuilder8.append(formatUtil.padX(" ", 158));
        fileC009mContents.add(formatUtil.padX(stringBuilder8.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 11));
        stringBuilder1.append(formatUtil.padX(trdateSYYY + "/" + trdateSMM + "/" + trdateSDD, 10));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(trdateEYYY + "/" + trdateEMM + "/" + trdateEDD, 10));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(mvdateYYY + "/" + mvdateMM + "/" + mvdateDD, 10));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptCnt), 8));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptAmt), 10));
        fileC009mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX("", 1));
        fileC009mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(reportUtil.makeGate("-", 85));
        fileC009mContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 12));
        stringBuilder3.append(formatUtil.padX(" 總計： ", 8));
        stringBuilder3.append(formatUtil.padX(" ", 38));
        stringBuilder3.append(formatUtil.padX(cntFormat.format(totCnt), 8));
        stringBuilder3.append(formatUtil.padX(" ", 6));
        stringBuilder3.append(formatUtil.padX(decimalFormat.format(totAmt), 10));
        fileC009mContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 12));
        stringBuilder4.append(formatUtil.padX(" ", 88));
        fileC009mContents.add(formatUtil.padX(stringBuilder4.toString(), 158));
    }

    private void reportNoData() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 12));
        stringBuilder1.append(formatUtil.padX(" 本　月　無　資　料 ", 22));
        fileC009mContents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }
}
