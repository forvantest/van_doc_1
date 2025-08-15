/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV12;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileNoDataPUTF;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@Component("CONV12Lsnr")
@Scope("prototype")
public class CONV12Lsnr extends BatchListenerCase<CONV12> {
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FileNoDataPUTF fileNoDataPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV12 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String CONVF_PATH_C032 = "CL-BH-032";
    private static final String wkPutfile = "27X1111323"; // 讀檔檔名 = wkConvfile
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final BigDecimal WKCFEE1 = BigDecimal.valueOf(6.75);
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String putfDataPath; // 讀檔路徑
    private String wkPutdirFdPutfPath; // 產檔路徑
    private String wkYYYMMDD;
    private String putfCode;
    private String putfRcptid;
    private String putfTxtype;
    private String rcptid111323;
    private String bank111323;
    private String prcdate111323;
    private String stdate111323;
    private String enddate111323;
    private String bhno111323;
    private String kind111323;
    private String cllbr111323;
    private String rptPDate;
    private String rptBrno;
    private int putfCtl = 0;
    private int putfDate;
    private int putfSitdate;
    private int rc111323;
    private int date111323;
    private int sitdate111323;
    private int page = 1;
    private int wkTotCnt = 0;
    private int wkSubCnt111323 = 0;
    private int wkSubCnt713040 = 0;
    private int wkSubCnt713925 = 0;
    private int wkSubCnt713926 = 0;
    private int wkSubCnt713927 = 0;
    private int wkSubCnt713928 = 0;
    private int wkSubCnt713929 = 0;
    private int totCnt111323 = 0;
    private int rptCnt111323 = 0;
    private int rptCnt713040 = 0;
    private int rptCnt713925 = 0;
    private int rptCnt713926 = 0;
    private int rptCnt713927 = 0;
    private int rptCnt713928 = 0;
    private int rptCnt713929 = 0;
    private int rptCnt = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt111323 = ZERO;
    private BigDecimal wkSubAmt111323 = ZERO;
    private BigDecimal wkSubAmt713040 = ZERO;
    private BigDecimal wkSubAmt713925 = ZERO;
    private BigDecimal wkSubAmt713926 = ZERO;
    private BigDecimal wkSubAmt713927 = ZERO;
    private BigDecimal wkSubAmt713928 = ZERO;
    private BigDecimal wkSubAmt713929 = ZERO;
    private BigDecimal totamt111323 = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal rptAmt111323 = ZERO;
    private BigDecimal rptFee111323 = ZERO;
    private BigDecimal rptAcAmt111323 = ZERO;
    private BigDecimal rptAmt713040 = ZERO;
    private BigDecimal rptFee713040 = ZERO;
    private BigDecimal rptAcAmt713040 = ZERO;
    private BigDecimal rptAmt713925 = ZERO;
    private BigDecimal rptFee713925 = ZERO;
    private BigDecimal rptAcAmt713925 = ZERO;
    private BigDecimal rptAmt713926 = ZERO;
    private BigDecimal rptFee713926 = ZERO;
    private BigDecimal rptAcAmt713926 = ZERO;
    private BigDecimal rptAmt713927 = ZERO;
    private BigDecimal rptFee713927 = ZERO;
    private BigDecimal rptAcAmt713927 = ZERO;
    private BigDecimal rptAmt713928 = ZERO;
    private BigDecimal rptFee713928 = ZERO;
    private BigDecimal rptAcAmt713928 = ZERO;
    private BigDecimal rptAmt713929 = ZERO;
    private BigDecimal rptFee713929 = ZERO;
    private BigDecimal rptAcAmt713929 = ZERO;
    private BigDecimal rptAmt = ZERO;
    private BigDecimal rptFee = ZERO;
    private BigDecimal rptAcAmt = ZERO;
    private List<String> fileRptContents = new ArrayList<>();
    private List<String> file111323Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV12 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV12Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV12 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV12Lsnr run()");

        init();

        checkPutfDataExist();

        writeFile();

        checkPath();

        batchResponse();
    }

    private void init() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init run()");

        // 讀批次日期檔
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、關閉批次日期檔、設定檔名日期變數值
        processDate = labelMap.get("PROCESS_DATE"); // todo: 待確認BATCH參數名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        wkYYYMMDD =
                processDate.substring(0, 3)
                        + PATH_SEPARATOR
                        + processDate.substring(3, 5)
                        + PATH_SEPARATOR
                        + processDate.substring(5, 7);

        String wkFdate = wkYYYMMDD;

        // 設定檔名變數,檔名
        putfDataPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;

        wkPutdirFdPutfPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_C032;
    }

    private void checkPutfDataExist() {
        // FD-PUTF檔案存在，執行111323-RTN,否則執行111323-EMPTY-RTN
        if (textFile.exists(putfDataPath)) {
            readPutfData();
            writeRpt();
        } else {
            writeNoData();
        }
    }

    private void readPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readPutfData run()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(wkPutdirFdPutfPath, CHARSET_UTF8);

        // 搬關資料到111323-REC(RC=1)
        valuate111323Rc1();

        // 寫檔FD-111323(RC=1)
        writeRc1();

        // 循序讀取FD-PUTF，直到檔尾
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            moveData();
        }
    }

    private void valuate111323Rc1() {
        rc111323 = 1;
        bank111323 = "004";
        int wkTempdate = parse.string2Integer(wkYYYMMDD) + 20110000;
        prcdate111323 = parse.decimal2String(wkTempdate, 8, 0);
        stdate111323 = parse.decimal2String(wkTempdate, 8, 0);
        enddate111323 = parse.decimal2String(wkTempdate, 8, 0);
        bhno111323 = "01";
    }

    private void writeRc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.padX(parse.decimal2String(rc111323, 1, 0), 1));
        sbRc1.append(formatUtil.padX(bank111323, 3));
        sbRc1.append(formatUtil.padX(prcdate111323, 8));
        sbRc1.append(formatUtil.padX(bhno111323, 2));
        sbRc1.append(formatUtil.padX(stdate111323, 8));
        sbRc1.append(formatUtil.padX(enddate111323, 8));
        sbRc1.append(formatUtil.padX("", 30));
        file111323Contents.add(formatUtil.padX(sbRc1.toString(), 60));
    }

    private void moveData() {
        // IF PUTF-CTL NOT = 11(非明細),跳到下一筆
        if (putfCtl == 11) {
            putfCode = filePUTF.getCode();
            putfRcptid = filePUTF.getRcptid();
            putfDate =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
            putfSitdate =
                    parse.string2Integer(
                            (parse.isNumeric(filePUTF.getSitdate()) ? filePUTF.getSitdate() : "0"));
            putfTxtype = filePUTF.getTxtype();
            putfAmt =
                    parse.string2BigDecimal(
                            (parse.isNumeric(filePUTF.getAmt()) ? filePUTF.getAmt() : "0"));

            // 搬關資料到111323-REC(RC=2)
            valuate111323Rc2();
            writeRc2();
            sumTot();
            switchPutfCode();
            valuate111323Rc3();
            writeRc3();
        }
    }

    private void valuate111323Rc2() {
        rc111323 = 2;
        // date111323 = wkTempdate
        date111323 = putfDate + 20110000;
        // sitdate111323 = wkTempdate
        sitdate111323 = putfSitdate + 20110000;
        kind111323 = putfTxtype;
        amt111323 = amt111323.add(putfAmt);
        rcptid111323 = putfRcptid;
        cllbr111323 = "004000 ";
    }

    private void writeRc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.padX(parse.decimal2String(rc111323, 1, 0), 1));
        sbRc2.append(formatUtil.padX(rcptid111323, 14));
        sbRc2.append(formatUtil.padX(parse.decimal2String(date111323, 8, 0), 8));
        sbRc2.append(formatUtil.padX(parse.decimal2String(sitdate111323, 8, 0), 8));
        sbRc2.append(formatUtil.padX(kind111323, 1));
        sbRc2.append(formatUtil.padX(parse.decimal2String(amt111323, 10, 0), 10));
        sbRc2.append(formatUtil.padX(parse.decimal2String(cllbr111323, 7, 0), 7));
        sbRc2.append(formatUtil.padX("", 11));
        file111323Contents.add(formatUtil.padX(sbRc2.toString(), 60));
    }

    private void sumTot() {
        wkTotCnt++;
        wkTotAmt = wkTotAmt.add(putfAmt);
    }

    private void switchPutfCode() {
        switch (putfCode) {
            case "111323" -> {
                wkSubCnt111323++;
                wkSubAmt111323 = wkSubAmt111323.add(putfAmt);
            }
            case "713040" -> {
                wkSubCnt713040++;
                wkSubAmt713040 = wkSubAmt713040.add(putfAmt);
            }
            case "713925" -> {
                wkSubCnt713925++;
                wkSubAmt713925 = wkSubAmt713925.add(putfAmt);
            }
            case "713926" -> {
                wkSubCnt713926++;
                wkSubAmt713926 = wkSubAmt713926.add(putfAmt);
            }
            case "713927" -> {
                wkSubCnt713927++;
                wkSubAmt713927 = wkSubAmt713927.add(putfAmt);
            }
            case "713928" -> {
                wkSubCnt713928++;
                wkSubAmt713928 = wkSubAmt713928.add(putfAmt);
            }
            case "713929" -> {
                wkSubCnt713929++;
                wkSubAmt713929 = wkSubAmt713929.add(putfAmt);
            }
            default -> {}
        }
    }

    private void valuate111323Rc3() {
        rc111323 = 3;
        totCnt111323 = wkTotCnt;
        totamt111323 = wkTotAmt;
    }

    private void writeRc3() {
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.padX(parse.decimal2String(rc111323, 1, 0), 1));
        sbRc3.append(formatUtil.padX(parse.decimal2String(totCnt111323, 8, 0), 8));
        sbRc3.append(formatUtil.padX(parse.decimal2String(totamt111323, 12, 0), 12));
        sbRc3.append(formatUtil.padX("", 39));
        file111323Contents.add(formatUtil.padX(sbRc3.toString(), 60));
    }

    private void writeNoData() {
        valuate111323Rc1();
        writeRc1();
        valuate111323Rc3();
        writeRc3();
    }

    private void writeRpt() {
        valuateRptHeader();
        writeRptHeader();
        valuateRptContents();
        writeRptContents();
        valuateRptFooter();
        writeRptFooter();
    }

    private void valuateRptHeader() {
        page = 1;
        rptPDate = wkYYYMMDD;
        rptBrno = "005";
    }

    private void writeRptHeader() {
        StringBuilder sbRptH1 = new StringBuilder();
        sbRptH1.append(formatUtil.padX("", 27));
        sbRptH1.append(formatUtil.padX(" 臺　灣　銀　行　代　收　商　港　服　務　費　彙　總　單 ", 56));
        fileRptContents.add(formatUtil.padX(sbRptH1.toString(), 90));

        StringBuilder sbRptH2 = new StringBuilder();
        sbRptH2.append(formatUtil.padX("", 1));
        sbRptH2.append(formatUtil.padX("BRNO : ", 7));
        sbRptH2.append(formatUtil.padX(rptBrno, 3));
        sbRptH2.append(formatUtil.padX("", 76));
        sbRptH2.append(formatUtil.padX("FORM : C032", 18));
        fileRptContents.add(formatUtil.padX(sbRptH2.toString(), 110));

        StringBuilder sbRptH3 = new StringBuilder();
        sbRptH3.append(formatUtil.padX(" 頁次 : ", 8));
        sbRptH3.append(formatUtil.padX("" + page, 4));
        sbRptH3.append(formatUtil.padX("", 74));
        sbRptH3.append(formatUtil.padX(" 印表日期 : ", 12));
        sbRptH3.append(formatUtil.padX(rptPDate, 10));
        fileRptContents.add(formatUtil.padX(sbRptH3.toString(), 110));

        StringBuilder sbRptH4 = new StringBuilder();
        sbRptH4.append(formatUtil.padX(" 代收類別 ", 10));
        sbRptH4.append(formatUtil.padX("", 4));
        sbRptH4.append(formatUtil.padX(" 代收日期 ", 10));
        sbRptH4.append(formatUtil.padX("", 12));
        sbRptH4.append(formatUtil.padX(" 代收總筆數 ", 12));
        sbRptH4.append(formatUtil.padX("", 10));
        sbRptH4.append(formatUtil.padX(" 代收總金額 ", 12));
        sbRptH4.append(formatUtil.padX("", 10));
        sbRptH4.append(formatUtil.padX(" 手續費 ", 8));
        sbRptH4.append(formatUtil.padX("", 12));
        sbRptH4.append(formatUtil.padX("  解繳金額 ", 11));
        fileRptContents.add(formatUtil.padX(sbRptH4.toString(), 120));

        StringBuilder sbRptH5 = new StringBuilder();
        sbRptH5.append(reportUtil.makeGate("-", 120));
        fileRptContents.add(formatUtil.padX(sbRptH5.toString(), 120));
    }

    private void valuateRptContents() {
        rptCnt111323 = wkSubCnt111323;
        rptAmt111323 = wkSubAmt111323;
        rptFee111323 = BigDecimal.valueOf(wkSubCnt111323).multiply(WKCFEE1);
        rptAcAmt111323 = wkSubAmt111323.subtract(rptFee111323);
        rptCnt713040 = wkSubCnt713040;
        rptAmt713040 = wkSubAmt713040;
        rptFee713040 = BigDecimal.valueOf(wkSubCnt713040).multiply(WKCFEE1);
        rptAcAmt713040 = wkSubAmt713040.subtract(rptFee713040);
        rptCnt713925 = wkSubCnt713925;
        rptAmt713925 = wkSubAmt713925;
        rptFee713925 = BigDecimal.valueOf(wkSubCnt713925).multiply(WKCFEE1);
        rptAcAmt713925 = wkSubAmt713925.subtract(rptFee713925);
        rptCnt713926 = wkSubCnt713926;
        rptAmt713926 = wkSubAmt713926;
        rptFee713926 = BigDecimal.valueOf(wkSubCnt713926).multiply(WKCFEE1);
        rptAcAmt713926 = wkSubAmt713926.subtract(rptFee713926);
        rptCnt713927 = wkSubCnt713927;
        rptAmt713927 = wkSubAmt713927;
        rptFee713927 = BigDecimal.valueOf(wkSubCnt713927).multiply(WKCFEE1);
        rptAcAmt713927 = wkSubAmt713927.subtract(rptFee713927);
        rptCnt713928 = wkSubCnt713928;
        rptAmt713928 = wkSubAmt713928;
        rptFee713928 = BigDecimal.valueOf(wkSubCnt713928).multiply(WKCFEE1);
        rptAcAmt713928 = wkSubAmt713928.subtract(rptFee713928);
        rptCnt713929 = wkSubCnt713929;
        rptAmt713929 = wkSubAmt713929;
        rptFee713929 = BigDecimal.valueOf(wkSubCnt713929).multiply(WKCFEE1);
        rptAcAmt713929 = wkSubAmt713929.subtract(rptFee713929);
    }

    private void writeRptContents() {
        StringBuilder sbRptC1 = new StringBuilder();
        sbRptC1.append(formatUtil.padX("", 2));
        sbRptC1.append(formatUtil.padX("111323", 6));
        sbRptC1.append(formatUtil.padX("", 8));
        sbRptC1.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC1.append(formatUtil.padX("", 10));
        sbRptC1.append(formatUtil.padX(cntFormat.format(rptCnt111323), 10));
        sbRptC1.append(formatUtil.padX("", 9));
        sbRptC1.append(formatUtil.padX(decimalFormat.format(rptAmt111323), 16));
        sbRptC1.append(formatUtil.padX("", 4));
        sbRptC1.append(formatUtil.padX(decimalFormat.format(rptFee111323), 14));
        sbRptC1.append(formatUtil.padX("", 7));
        sbRptC1.append(formatUtil.padX(decimalFormat.format(rptAcAmt111323), 16));
        fileRptContents.add(formatUtil.padX(sbRptC1.toString(), 120));

        StringBuilder sbRptC2 = new StringBuilder();
        sbRptC2.append(formatUtil.padX("", 2));
        sbRptC2.append(formatUtil.padX("713040", 6));
        sbRptC2.append(formatUtil.padX("", 8));
        sbRptC2.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC2.append(formatUtil.padX("", 10));
        sbRptC2.append(formatUtil.padX(cntFormat.format(rptCnt713040), 10));
        sbRptC2.append(formatUtil.padX("", 9));
        sbRptC2.append(formatUtil.padX(decimalFormat.format(rptAmt713040), 16));
        sbRptC2.append(formatUtil.padX("", 4));
        sbRptC2.append(formatUtil.padX(decimalFormat.format(rptFee713040), 14));
        sbRptC2.append(formatUtil.padX("", 7));
        sbRptC2.append(formatUtil.padX(decimalFormat.format(rptAcAmt713040), 16));
        fileRptContents.add(formatUtil.padX(sbRptC2.toString(), 120));

        StringBuilder sbRptC3 = new StringBuilder();
        sbRptC3.append(formatUtil.padX("", 2));
        sbRptC3.append(formatUtil.padX("713925", 6));
        sbRptC3.append(formatUtil.padX("", 8));
        sbRptC3.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC3.append(formatUtil.padX("", 10));
        sbRptC3.append(formatUtil.padX(cntFormat.format(rptCnt713925), 10));
        sbRptC3.append(formatUtil.padX("", 9));
        sbRptC3.append(formatUtil.padX(decimalFormat.format(rptAmt713925), 16));
        sbRptC3.append(formatUtil.padX("", 4));
        sbRptC3.append(formatUtil.padX(decimalFormat.format(rptFee713925), 14));
        sbRptC3.append(formatUtil.padX("", 7));
        sbRptC3.append(formatUtil.padX(decimalFormat.format(rptAcAmt713925), 16));
        fileRptContents.add(formatUtil.padX(sbRptC3.toString(), 120));

        StringBuilder sbRptC4 = new StringBuilder();
        sbRptC4.append(formatUtil.padX("", 2));
        sbRptC4.append(formatUtil.padX("713926", 6));
        sbRptC4.append(formatUtil.padX("", 8));
        sbRptC4.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC4.append(formatUtil.padX("", 10));
        sbRptC4.append(formatUtil.padX(cntFormat.format(rptCnt713926), 10));
        sbRptC4.append(formatUtil.padX("", 9));
        sbRptC4.append(formatUtil.padX(decimalFormat.format(rptAmt713926), 16));
        sbRptC4.append(formatUtil.padX("", 4));
        sbRptC4.append(formatUtil.padX(decimalFormat.format(rptFee713926), 14));
        sbRptC4.append(formatUtil.padX("", 7));
        sbRptC4.append(formatUtil.padX(decimalFormat.format(rptAcAmt713926), 16));
        fileRptContents.add(formatUtil.padX(sbRptC4.toString(), 120));

        StringBuilder sbRptC5 = new StringBuilder();
        sbRptC5.append(formatUtil.padX("", 2));
        sbRptC5.append(formatUtil.padX("713927", 6));
        sbRptC5.append(formatUtil.padX("", 8));
        sbRptC5.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC5.append(formatUtil.padX("", 10));
        sbRptC5.append(formatUtil.padX(cntFormat.format(rptCnt713927), 10));
        sbRptC5.append(formatUtil.padX("", 9));
        sbRptC5.append(formatUtil.padX(decimalFormat.format(rptAmt713927), 16));
        sbRptC5.append(formatUtil.padX("", 4));
        sbRptC5.append(formatUtil.padX(decimalFormat.format(rptFee713927), 14));
        sbRptC5.append(formatUtil.padX("", 7));
        sbRptC5.append(formatUtil.padX(decimalFormat.format(rptAcAmt713927), 16));
        fileRptContents.add(formatUtil.padX(sbRptC5.toString(), 120));

        StringBuilder sbRptC6 = new StringBuilder();
        sbRptC6.append(formatUtil.padX("", 2));
        sbRptC6.append(formatUtil.padX("713928", 6));
        sbRptC6.append(formatUtil.padX("", 8));
        sbRptC6.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC6.append(formatUtil.padX("", 10));
        sbRptC6.append(formatUtil.padX(cntFormat.format(rptCnt713928), 10));
        sbRptC6.append(formatUtil.padX("", 9));
        sbRptC6.append(formatUtil.padX(decimalFormat.format(rptAmt713928), 16));
        sbRptC6.append(formatUtil.padX("", 4));
        sbRptC6.append(formatUtil.padX(decimalFormat.format(rptFee713928), 14));
        sbRptC6.append(formatUtil.padX("", 7));
        sbRptC6.append(formatUtil.padX(decimalFormat.format(rptAcAmt713928), 16));
        fileRptContents.add(formatUtil.padX(sbRptC6.toString(), 120));

        StringBuilder sbRptC7 = new StringBuilder();
        sbRptC7.append(formatUtil.padX("", 2));
        sbRptC7.append(formatUtil.padX("713929", 6));
        sbRptC7.append(formatUtil.padX("", 8));
        sbRptC7.append(formatUtil.padX(wkYYYMMDD, 9));
        sbRptC7.append(formatUtil.padX("", 10));
        sbRptC7.append(formatUtil.padX(cntFormat.format(rptCnt713929), 10));
        sbRptC7.append(formatUtil.padX("", 9));
        sbRptC7.append(formatUtil.padX(decimalFormat.format(rptAmt713929), 16));
        sbRptC7.append(formatUtil.padX("", 4));
        sbRptC7.append(formatUtil.padX(decimalFormat.format(rptFee713929), 14));
        sbRptC7.append(formatUtil.padX("", 7));
        sbRptC7.append(formatUtil.padX(decimalFormat.format(rptAcAmt713929), 16));
        fileRptContents.add(formatUtil.padX(sbRptC7.toString(), 120));

        StringBuilder sbRptC8 = new StringBuilder();
        sbRptC8.append(reportUtil.makeGate("-", 120));
        fileRptContents.add(formatUtil.padX(sbRptC8.toString(), 120));
    }

    private void valuateRptFooter() {
        rptCnt = wkTotCnt;
        rptAmt = wkTotAmt;
        rptFee = BigDecimal.valueOf(wkTotCnt).multiply(WKCFEE1);
        rptAcAmt = rptAmt.subtract(rptFee);
    }

    private void writeRptFooter() {
        StringBuilder sbRptF1 = new StringBuilder();
        sbRptF1.append(formatUtil.padX("", 2));
        sbRptF1.append(formatUtil.padX(" 小計 ", 6));
        sbRptF1.append(formatUtil.padX("", 8));
        sbRptF1.append(formatUtil.padX(wkYYYMMDD, 7));
        sbRptF1.append(formatUtil.padX(cntFormat.format(rptCnt), 10));
        sbRptF1.append(formatUtil.padX("", 9));
        sbRptF1.append(formatUtil.padX(decimalFormat.format(rptAmt), 16));
        sbRptF1.append(formatUtil.padX("", 4));
        sbRptF1.append(formatUtil.padX(decimalFormat.format(rptFee), 14));
        sbRptF1.append(formatUtil.padX("", 7));
        sbRptF1.append(formatUtil.padX(decimalFormat.format(rptAcAmt), 16));
        fileRptContents.add(formatUtil.padX(sbRptF1.toString(), 100));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(wkPutdirFdPutfPath);
            //            textFile.deleteFile(putfDataPath);
            textFile.writeFileContent(wkPutdirFdPutfPath, fileRptContents, CHARSET_UTF8);
            //            textFile.writeFileContent(putfDataPath, file111323Contents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV12 upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + wkFsapYYYYMMDD + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(putfDataPath)) {
            upload(putfDataPath);
            forFsap();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        wkPutfile, // 來源檔案名稱(20碼長)
                        wkPutfile, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV12", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
