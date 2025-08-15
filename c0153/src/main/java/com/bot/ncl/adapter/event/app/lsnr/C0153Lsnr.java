/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C0153;
import com.bot.ncl.util.fileVo.FilePUTFN;
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
@Component("C0153Lsnr")
@Scope("prototype")
public class C0153Lsnr extends BatchListenerCase<C0153> {

    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FilePUTFN filePUTFN;
    @Autowired private ReportUtil reportUtil;

    private static final String CHARSET = "UTF-8";
    private static final String FILE_NAME = "CL-BH-C015-3";
    private static final String FILE_INPUT_NAME = "PUTFN";
    private static final String FILE_TMP_NAME = "TMPC0153";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY";
    private Map<String, String> textMap;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");

    private int tbsdy;
    private int nbsdy;
    private int tmndy;
    private int wkSitdate;
    private int putfnSitdate;
    private int wkBdate;
    private int wkEdate;
    private int wkTradedate;
    private int ndycnt;
    private int nndcnt;
    private int nnbsdy;
    private int n3bsdy;
    private int wkMVDP;
    private int wkChktrd;
    private int wkTrd;
    private int wkTrdMM;
    private int wkTrdDD;
    private int rptYYY;
    private int rptMM;
    private int rptTrdateYYY;
    private int rptTrdateMM;
    private int rptTrdateDD;
    private int rptTsdateYYY;
    private int rptTsdateMM;
    private int rptTsdateDD;
    private int mvDateYYY;
    private int mvDateMM;
    private int mvDateDD;
    private int page = 1;
    private int pageCnt = 0;
    private int cnt = 0;
    private int rptTrCnt = 0;
    private int totcnt = 0;
    private BigDecimal amt = new BigDecimal(0);
    private BigDecimal rptFeeAmt = new BigDecimal(0);
    private BigDecimal totAmt = new BigDecimal(0);
    private String inputFilePath;
    private String sortTmpFilePath;
    private String outputFilePath;
    private List<String> sortTmpfileC0153Contents;
    private List<String> fileC0153Contents;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C0153 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0153Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C0153 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0153Lsnr run()");
        init(event);
        sortFileContent();
        query();

        try {
            textFile.writeFileContent(outputFilePath, fileC0153Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(C0153 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0153Lsnr init");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        String batchDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱

        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;

        sortTmpFilePath = fileDir + FILE_TMP_NAME;

        outputFilePath = fileDir + FILE_NAME;

        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileC0153Contents = new ArrayList<>();
        fileC0153Contents = new ArrayList<>();

        tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        nbsdy = event.getAggregateBuffer().getTxCom().getNbsdy();
        int lmndy = event.getAggregateBuffer().getTxCom().getLmndy();
        tmndy = event.getAggregateBuffer().getTxCom().getTmndy();
        ndycnt = event.getAggregateBuffer().getTxCom().getNdycnt();
        nndcnt = event.getAggregateBuffer().getTxCom().getNndycnt();
        nnbsdy = event.getAggregateBuffer().getTxCom().getNnbsdy();
        n3bsdy = event.getAggregateBuffer().getTxCom().getN3bsdy();

        wkSitdate = tbsdy;
        wkBdate = lmndy;
        wkEdate = tmndy;
        wkEdate--;
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0153Lsnr sortFileContent ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePUTFN);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
            sortTmpfileC0153Contents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileC0153Contents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
    }

    private void query() {
        int tbsdyYYY = parse.string2Integer(parse.decimal2String(tbsdy, 7, 0).substring(0, 3));
        int tbsdyMM = parse.string2Integer(parse.decimal2String(tbsdy, 7, 0).substring(3, 5));
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        rptYYY = tbsdyYYY;
        rptMM = tbsdyMM;
        reportHeader();

        if (Objects.isNull(lines) || lines.isEmpty()) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "putfnfileC0153Contents is null");
            reportNoData();
        } else {
            for (String detail : lines) {
                text2VoFormatter.format(detail, filePUTFN);
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePUTFN = {}", filePUTFN);
                String ctl = detail.substring(0, 2);
                amt = parse.string2BigDecimal(detail.substring(120, 132));
                putfnSitdate = parse.string2Integer(detail.substring(111, 119));
                if (ctl.equals("11") && putfnSitdate >= wkBdate && putfnSitdate <= wkEdate) {
                    if (putfnSitdate != wkTradedate) {
                        wkSitdate = wkTradedate;
                        wkChktrd = wkSitdate + 1;
                        wkTrd = wkChktrd;
                        String wkTrdString = parse.decimal2String(wkTrd, 8, 0);
                        int wkTrdYYY = parse.string2Integer(wkTrdString.substring(1, 4));
                        wkTrdMM = parse.string2Integer(wkTrdString.substring(4, 6));
                        wkTrdDD = parse.string2Integer(wkTrdString.substring(6, 8));
                        if (wkChktrd > tmndy) {
                            if (wkTrdMM == 12) {
                                wkTrdYYY++;
                                wkTrdMM = 1;
                            } else {
                                wkTrdMM++;
                            }
                            wkTrdDD = 1;
                        }
                        wkTrd =
                                parse.string2Integer(
                                        parse.decimal2String(wkTrdYYY, 3, 0)
                                                + parse.decimal2String(wkTrdMM, 2, 0)
                                                + parse.decimal2String(wkTrdDD, 2, 0));
                        wkChktrd = wkTrd;

                        int wkSumnnday = ndycnt + nndcnt;
                        if (ndycnt > 2) {
                            wkMVDP = nbsdy;
                        } else {
                            if (wkSumnnday > 2) {
                                wkMVDP = nnbsdy;
                            } else {
                                wkMVDP = n3bsdy;
                            }
                        }

                        int rptMvdate = wkMVDP;
                        String rptMvdateString = parse.decimal2String(rptMvdate, 8, 0);
                        mvDateYYY = parse.string2Integer(rptMvdateString.substring(1, 4));
                        mvDateMM = parse.string2Integer(rptMvdateString.substring(4, 6));
                        mvDateDD = parse.string2Integer(rptMvdateString.substring(6, 8));
                        int rptTsdate = wkChktrd;
                        String rptTsdateString = parse.decimal2String(rptTsdate, 8, 0);
                        rptTsdateYYY = parse.string2Integer(rptTsdateString.substring(1, 4));
                        rptTsdateMM = parse.string2Integer(rptTsdateString.substring(4, 6));
                        rptTsdateDD = parse.string2Integer(rptTsdateString.substring(6, 8));
                        int rptTrdate = wkTradedate;
                        String rptTrdateString = parse.decimal2String(rptTrdate, 8, 0);
                        rptTrdateYYY = parse.string2Integer(rptTrdateString.substring(1, 4));
                        rptTrdateMM = parse.string2Integer(rptTrdateString.substring(4, 6));
                        rptTrdateDD = parse.string2Integer(rptTrdateString.substring(6, 8));

                        wkTradedate = putfnSitdate;
                        cnt++;
                        rptTrCnt = cnt;
                        totcnt = totcnt + cnt;
                        pageCnt++;
                        rptFeeAmt = amt;
                        totAmt = totAmt.add(amt);
                        reportDetail();
                        cnt = 0;
                        rptTrCnt = 0;
                        amt = new BigDecimal(0);
                        rptFeeAmt = new BigDecimal(0);
                    } else {
                        cnt++;
                        amt = amt.add(amt);
                    }
                }
                if (pageCnt > 46) {
                    changePage();
                }
            }
            if (cnt != 0) {
                wkTradedate = putfnSitdate;
                cnt++;
                rptTrCnt = cnt;
                totcnt = totcnt + cnt;
                pageCnt++;
                rptFeeAmt = amt;
                totAmt = totAmt.add(amt);
                reportDetail();
                cnt = 0;
                rptTrCnt = 0;
                amt = new BigDecimal(0);
                rptFeeAmt = new BigDecimal(0);
            }
            reportfooter();
        }
    }

    private void changePage() {
        reportHeader();
        page++;
        pageCnt = 0;
    }

    private void reportNoData() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" 本　月　無　資　料 ", 25));
        fileC0153Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 28));
        stringBuilder1.append(formatUtil.padX(" 臺灣銀行　代收台電公司費款手續費月結表 ", 40));
        fileC0153Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" 代收月份： ", 12));
        stringBuilder2.append(formatUtil.padX("" + rptYYY, 3));
        stringBuilder2.append(formatUtil.padX(" 年 ", 4));
        stringBuilder2.append(formatUtil.padX("" + rptMM, 2));
        stringBuilder2.append(formatUtil.padX(" ", 39));
        stringBuilder2.append(formatUtil.padX(" 頁　　次： ", 12));
        stringBuilder2.append(formatUtil.padX("" + page, 3));
        fileC0153Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 8));
        stringBuilder3.append(formatUtil.padX(" 代收分行： ", 12));
        stringBuilder3.append(formatUtil.padX("034", 3));
        stringBuilder3.append(formatUtil.padX(" ", 49));
        stringBuilder3.append(formatUtil.padX(" 報表名稱： ", 12));
        stringBuilder3.append(formatUtil.padX("C015-3", 6));
        fileC0153Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 12));
        stringBuilder4.append(formatUtil.padX(" 交易日 ", 8));
        stringBuilder4.append(formatUtil.padX(" ", 7));
        stringBuilder4.append(formatUtil.padX(" 傳輸日 ", 8));
        stringBuilder4.append(formatUtil.padX(" ", 7));
        stringBuilder4.append(formatUtil.padX(" 解繳日 ", 8));
        stringBuilder4.append(formatUtil.padX(" ", 5));
        stringBuilder4.append(formatUtil.padX(" 交易筆數 ", 10));
        stringBuilder4.append(formatUtil.padX(" ", 7));
        stringBuilder4.append(formatUtil.padX(" 手續費 ", 8));
        fileC0153Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(reportUtil.makeGate("-", 90));
        fileC0153Contents.add(formatUtil.padX(stringBuilder5.toString(), 158));
    }

    private void reportDetail() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 11));
        stringBuilder1.append(
                formatUtil.padX(rptTrdateYYY + "/" + rptTrdateMM + "/" + rptTrdateDD, 9));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(
                formatUtil.padX(rptTsdateYYY + "/" + rptTsdateMM + "/" + rptTsdateDD, 9));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(mvDateYYY + "/" + mvDateMM + "/" + mvDateDD, 9));
        stringBuilder1.append(formatUtil.padX(" ", 8));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(rptTrCnt), 7));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(rptFeeAmt), 9));
        fileC0153Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportfooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(reportUtil.makeGate("-", 90));
        fileC0153Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 12));
        stringBuilder2.append(formatUtil.padX(" 總計： ", 8));
        stringBuilder2.append(formatUtil.padX(" ", 38));
        stringBuilder2.append(formatUtil.padX(cntFormat.format(totcnt), 7));
        stringBuilder2.append(formatUtil.padX(" ", 6));
        stringBuilder2.append(formatUtil.padX(decimalFormat.format(totAmt), 9));
        fileC0153Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 12));
        stringBuilder3.append(formatUtil.padX("註：本表手續費月結數係按台電指示依傳輸日彙記", 50));
        fileC0153Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));
    }
}
