/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Conv361689;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Conv361689Lsnr")
@Scope("prototype")
public class Conv361689Lsnr extends BatchListenerCase<Conv361689> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private Conv361689 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_22C1361689 = "22C1361689";
    private static final String CONVF_PATH_037 = "CL-BH-037";
    private static final String CONVF_PATH_36168 = "36168";
    private static final String CONVF_PATH_003 = "003";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_004 = "004  ";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final BigDecimal WK_FEE_UNIT = BigDecimal.valueOf(0.5);
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String readFdPutfPath;
    private String writeReportPath;
    private String writeFd361689TotPath;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String wkFdate;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String wkReportPdate;
    private String reportDate;
    private String reportBdate;
    private String reportEdate;
    private String signdate361689;
    private String sno1_361689;
    private String sno2_361689;
    private String sno3_361689;
    private String stktxtype361689;
    private String sortPath;
    private String wkFlag;
    private String serino361689;
    private int wkCompdate;
    private int putfDate;
    private int putfLmtdate;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int reportCnt;
    private int wkSubcnt;
    private int lmtdate361689;
    private int sitdate361689;
    private int wkTotCnt = 0;
    private int reportTotcnt = 0;
    private int wkSerino = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal reportAmt = ZERO;
    private BigDecimal reportFee = ZERO;
    private BigDecimal reportAcamt = ZERO;
    private BigDecimal reportTotamt = ZERO;
    private BigDecimal reportTotfee = ZERO;
    private BigDecimal reportTotAcamt = ZERO;
    private BigDecimal wkSubamt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private List<String> fileFd361689Contents = new ArrayList<>();
    private List<String> fileFd361689TotContents = new ArrayList<>();
    private List<String> fileReportContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(Conv361689 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Conv361689 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr run()");

        init(event);

        checkSortPath();

        //        checkFdPutfExist();

        //        writeFile();
        writeRPTFile();
        checkPath();

        batchResponse();
    }

    private void checkSortPath() {
        sortPath =
                fileDir
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_22C1361689;
        if (textFile.exists(sortPath)) {
            sort361689();
        }
    }

    private void sort361689() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr sort361689()");
        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(25, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET_UTF8);
    }

    private void init(Conv361689 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr init()");
        wkFlag = "N";

        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 設定作業日、設定檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        wkFdate = processDate.substring(1);

        wkReportPdate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7);

        // 設定檔名變數,檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C1361689;
        textFile.deleteFile(readFdPutfPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C1361689; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }

        writeFd361689TotPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_36168
                        + PATH_SEPARATOR
                        + CONVF_PATH_003;
        writeReportPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_037;
    }

    private void checkFdPutfExist() {
        // FD-PUTF檔案存在，執行361689-RTN
        // 否則寫FD-361689-TOT
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
            checkLast();
            writeReportFooter();
            writeFd361689Tot();
        } else {
            valuateWhenFdPutfNoData();
            writeFd361689Tot();
            writeNoDataFile();
        }
    }

    private void readFdPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr readFdPutfData()");
        // 循序讀取FD-PUTF
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            int putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            if (putfCtl == 11) {
                putfCode = filePUTF.getCode();
                putfRcptid = filePUTF.getRcptid();
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
                putfCllbr = detail.substring(36, 39);
                putfLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(39, 45))
                                        ? detail.substring(39, 45)
                                        : "0"));
                putfUserdata = detail.substring(53, 93);
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(93, 99))
                                        ? detail.substring(93, 99)
                                        : "0"));
                putfTxtype = detail.substring(99, 100);
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(100, 110))
                                        ? detail.substring(100, 110)
                                        : "0"));

                // 搬PUTF...到361689-DTL..
                valuateFd361689Dtl();

                // 寫檔FD-361689-DTL
                writeFd361689Dtl();

                // 累加金額筆數
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
            }

            // IF PUTF-CTL = 22,寫彙總報表
            if (putfCtl == 22) {
                putfBdate =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTF.getBdate())
                                        ? fileSumPUTF.getBdate()
                                        : "0"));
                putfEdate =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTF.getEdate())
                                        ? fileSumPUTF.getEdate()
                                        : "0"));
                valuateReportContents();
            }
        }
    }

    private void valuateFd361689Dtl() {
        wkSerino++;
        serino361689 = parse.decimal2String(wkSerino, 5, 0);
        checkPutfCode();
        sno1_361689 = putfRcptid.substring(3, 4);
        sno2_361689 = putfRcptid.substring(2, 3);
        sno3_361689 = putfRcptid.substring(0, 2);
        wkTotAmt = putfAmt;
        sitdate361689 = putfSitdate;
        checkPutfTxtype();
    }

    private void checkPutfCode() {
        if (putfCode.equals("361689")) {
            signdate361689 = putfUserdata.substring(0, 4) + putfRcptid.substring(4, 6);
            lmtdate361689 = putfLmtdate;
        } else {
            signdate361689 = putfUserdata.substring(26, 30) + putfRcptid.substring(4, 6);
            lmtdate361689 = parse.string2Integer(putfUserdata.substring(20, 26));
        }
    }

    private void checkPutfTxtype() {
        Set<String> pt1 = Set.of("C", "M");

        if (pt1.contains(putfTxtype)) {
            stktxtype361689 = "Y";
        } else if (putfTxtype.equals("F") && putfUserdata.substring(38, 39).equals("M")) {
            stktxtype361689 = "M";
        } else if (putfTxtype.equals("F")) {
            stktxtype361689 = "B";
        } else {
            stktxtype361689 = "N";
        }
    }

    private void writeFd361689Dtl() {
        StringBuilder stringBuilderFdD = new StringBuilder();
        stringBuilderFdD.append(formatUtil.padX(STRING_004, 5));
        stringBuilderFdD.append(formatUtil.pad9(putfCllbr, 3));
        stringBuilderFdD.append(formatUtil.pad9(processDate, 7));
        stringBuilderFdD.append(formatUtil.padX("001", 3));
        stringBuilderFdD.append(formatUtil.pad9(serino361689, 5));
        stringBuilderFdD.append(formatUtil.padX("", 1));
        stringBuilderFdD.append(formatUtil.padX("", 2));
        stringBuilderFdD.append(formatUtil.padX("", 7));
        stringBuilderFdD.append(formatUtil.pad9(signdate361689, 6));
        stringBuilderFdD.append(formatUtil.pad9("" + lmtdate361689, 6));
        stringBuilderFdD.append(formatUtil.padX("168", 3));
        stringBuilderFdD.append(formatUtil.padX(sno1_361689, 1));
        stringBuilderFdD.append(formatUtil.padX(sno2_361689, 1));
        stringBuilderFdD.append(formatUtil.padX(sno3_361689, 1));
        stringBuilderFdD.append(formatUtil.pad9(decimalFormat.format(wkTotAmt), 9));
        stringBuilderFdD.append(formatUtil.pad9("" + sitdate361689, 6));
        stringBuilderFdD.append(formatUtil.padX(stktxtype361689, 1));
        stringBuilderFdD.append(formatUtil.padX(STRING_004, 5));
        fileFd361689Contents.add(formatUtil.padX(stringBuilderFdD.toString(), 100));
    }

    private void valuateWhenFdPutfNoData() {
        wkTotCnt = 0;
        wkTotAmt = ZERO;
    }

    private void writeFd361689Tot() {
        StringBuilder stringBuilderFdT = new StringBuilder();
        stringBuilderFdT.append(formatUtil.padX(STRING_004, 5));
        stringBuilderFdT.append(formatUtil.pad9(processDate, 7));
        stringBuilderFdT.append(formatUtil.pad9(cntFormat.format(wkTotCnt), 5));
        stringBuilderFdT.append(formatUtil.pad9(decimalFormat.format(wkTotAmt), 9));
        fileFd361689TotContents.add(formatUtil.padX(stringBuilderFdT.toString(), 50));
    }

    private void writeReportHeader() {
        StringBuilder sbH1 = new StringBuilder();
        sbH1.append(formatUtil.padX("", 20));
        sbH1.append(formatUtil.padX(" 臺　灣　銀　行　代　收　週　結　清　單 ", 40));
        fileReportContents.add(formatUtil.padX(sbH1.toString(), 120));

        StringBuilder sbH2 = new StringBuilder();
        sbH2.append(formatUtil.padX("", 3));
        sbH2.append(formatUtil.padX(" 主辦分行： ", 12));
        sbH2.append(formatUtil.padX("003", 3));
        sbH2.append(formatUtil.padX("", 46));
        sbH2.append(formatUtil.padX(" 報表名稱： ", 12));
        sbH2.append(formatUtil.padX("C037", 4));
        fileReportContents.add(formatUtil.padX(sbH2.toString(), 120));

        StringBuilder sbH3 = new StringBuilder();
        sbH3.append(formatUtil.padX("", 3));
        sbH3.append(formatUtil.padX(" 代收項目： ", 12));
        sbH3.append(formatUtil.padX("168", 3));
        sbH3.append(formatUtil.padX("", 20));
        sbH3.append(formatUtil.padX(" 週結日： ", 10));
        sbH3.append(formatUtil.pad9(wkReportPdate.substring(0, 3), 3));
        sbH3.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbH3.append(formatUtil.pad9(wkReportPdate.substring(3, 5), 2));
        sbH3.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbH3.append(formatUtil.pad9(wkReportPdate.substring(5, 7), 2));
        fileReportContents.add(formatUtil.padX(sbH3.toString(), 120));

        StringBuilder sbH4 = new StringBuilder();
        sbH4.append(formatUtil.padX("", 3));
        sbH4.append(formatUtil.padX(" 代收名稱：台北自來水處 ", 24));
        sbH4.append(formatUtil.padX("", 44));
        sbH4.append(formatUtil.padX(" 頁次： ", 8));
        sbH4.append(formatUtil.padX("1", 1));
        fileReportContents.add(formatUtil.padX(sbH4.toString(), 120));

        StringBuilder sbG = new StringBuilder();
        sbG.append(reportUtil.makeGate("-", 80));
        fileReportContents.add(formatUtil.padX(sbG.toString(), 120));

        StringBuilder sbH5 = new StringBuilder();
        sbH5.append(formatUtil.padX("", 3));
        sbH5.append(formatUtil.padX(" 代收日期 ", 10));
        sbH5.append(formatUtil.padX("", 6));
        sbH5.append(formatUtil.padX(" 總金額 ", 8));
        sbH5.append(formatUtil.padX("", 5));
        sbH5.append(formatUtil.padX(" 總筆數 ", 8));
        sbH5.append(formatUtil.padX("", 5));
        sbH5.append(formatUtil.padX(" 手續費 ", 8));
        sbH5.append(formatUtil.padX("", 6));
        sbH5.append(formatUtil.padX(" 入帳金額 ", 10));
        sbH5.append(formatUtil.padX("", 4));
        sbH5.append(formatUtil.padX(" 備註 ", 6));
        fileReportContents.add(formatUtil.padX(sbH5.toString(), 120));

        sbG = new StringBuilder();
        sbG.append(reportUtil.makeGate("-", 80));
        fileReportContents.add(formatUtil.padX(sbG.toString(), 120));

        fileReportContents.add("");
    }

    private void valuateReportContents() {
        wkCompdate = putfDate;
        reportCnt = wkSubcnt;
        reportAmt = wkSubamt;
        wkTotCnt = wkTotCnt + wkSubcnt;
        wkTotAmt = wkTotAmt.add(wkSubamt);
        reportTotcnt = wkTotCnt;
        reportTotamt = wkTotAmt;
        reportFee = WK_FEE_UNIT.multiply(BigDecimal.valueOf(wkSubcnt));
        reportAcamt = reportAmt.subtract(reportFee);
        reportTotfee =
                BigDecimal.valueOf(wkTotCnt).multiply(WK_FEE_UNIT).add(BigDecimal.valueOf(0.5));
        reportTotAcamt = wkTotAmt.subtract(reportTotfee);
        reportBdate = parse.decimal2String(putfBdate, 6, 0);
        reportEdate = parse.decimal2String(putfEdate, 6, 0);

        changeFlag();
        changeDate();
    }

    private void changeFlag() {
        if (wkFlag.equals("N")) {
            wkFlag = "Y";
            wkCompdate = putfDate;
            writeReportHeader();
        }
    }

    private void changeDate() {
        if (wkCompdate != putfDate) {
            reportDate = "1" + parse.decimal2String(wkCompdate, 6, 0);
            reportCnt = wkSubcnt;
            reportAmt = wkSubamt;
            wkTotCnt = wkTotCnt + wkSubcnt;
            wkTotAmt = wkTotAmt.add(wkSubamt);

            BigDecimal wkSubCntB = BigDecimal.valueOf(wkSubcnt);
            reportFee = wkSubCntB.multiply(WK_FEE_UNIT);
            reportAcamt = wkSubamt.subtract(wkSubCntB.multiply(WK_FEE_UNIT));

            writeReportContents();

            wkCompdate = putfDate;
            wkSubcnt = 1;
            wkSubamt = putfAmt;
        }
    }

    private void writeReportContents() {
        StringBuilder sbC = new StringBuilder();
        sbC.append(formatUtil.padX("", 4));
        sbC.append(formatUtil.pad9(reportDate.substring(0, 3), 3));
        sbC.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbC.append(formatUtil.pad9(reportDate.substring(3, 5), 2));
        sbC.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbC.append(formatUtil.pad9(reportDate.substring(5, 7), 2));
        sbC.append(formatUtil.padX("", 1));
        sbC.append(formatUtil.pad9(decimalFormat.format(reportAmt), 13));
        sbC.append(formatUtil.padX("", 3));
        sbC.append(formatUtil.pad9(cntFormat.format(reportCnt), 10));
        sbC.append(formatUtil.padX("", 1));
        sbC.append(formatUtil.pad9(decimalFormat.format(reportFee), 12));
        sbC.append(formatUtil.padX("", 1));
        sbC.append(formatUtil.pad9(decimalFormat.format(reportAcamt), 15));
        fileReportContents.add(formatUtil.padX(sbC.toString(), 120));
    }

    private void checkLast() {
        if (wkCompdate == putfDate) {
            writeReportContents();
        }
    }

    private void writeReportFooter() {
        fileReportContents.add("");

        StringBuilder sbG = new StringBuilder();
        sbG.append(reportUtil.makeGate("-", 80));
        fileReportContents.add(formatUtil.padX(sbG.toString(), 120));

        StringBuilder sbF1 = new StringBuilder();
        sbF1.append(formatUtil.padX("", 5));
        sbF1.append(formatUtil.padX(" 總　計 ", 8));
        sbF1.append(formatUtil.pad9(decimalFormat.format(reportTotamt), 13));
        sbF1.append(formatUtil.padX("", 3));
        sbF1.append(formatUtil.pad9(cntFormat.format(reportTotcnt), 10));
        sbF1.append(formatUtil.padX("", 1));
        sbF1.append(formatUtil.pad9(decimalFormat.format(reportTotfee), 12));
        sbF1.append(formatUtil.padX("", 3));
        sbF1.append(formatUtil.pad9(decimalFormat.format(reportTotAcamt), 13));
        fileReportContents.add(formatUtil.padX(sbF1.toString(), 120));

        fileReportContents.add("");

        StringBuilder sbF2 = new StringBuilder();
        sbF2.append(formatUtil.padX("", 3));
        sbF2.append(formatUtil.padX(" 代收期間： ", 12));
        sbF2.append(formatUtil.pad9(reportBdate.substring(0, 3), 3));
        sbF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbF2.append(formatUtil.pad9(reportBdate.substring(3, 5), 2));
        sbF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbF2.append(formatUtil.pad9(reportBdate.substring(5, 7), 2));
        sbF2.append(formatUtil.padX("-", 1));
        sbF2.append(formatUtil.pad9(reportEdate.substring(0, 3), 3));
        sbF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbF2.append(formatUtil.pad9(reportEdate.substring(3, 5), 2));
        sbF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        sbF2.append(formatUtil.pad9(reportEdate.substring(5, 7), 2));
        fileReportContents.add(formatUtil.padX(sbF2.toString(), 120));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr writeFile()");
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.deleteFile(writeFd361689TotPath);
            textFile.deleteFile(writeReportPath);
            textFile.writeFileContent(readFdPutfPath, fileFd361689Contents, CHARSET_UTF8);
            upload(readFdPutfPath, "DATA", "PUTF");
            textFile.writeFileContent(writeFd361689TotPath, fileFd361689TotContents, CHARSET_UTF8);
            upload(readFdPutfPath, "DATA", "PUTF");
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_Big5);
            upload(readFdPutfPath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void writeRPTFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr writeRPTFile()");
        try {
            textFile.deleteFile(writeReportPath);
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_Big5);
            upload(readFdPutfPath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void writeNoDataFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv361689Lsnr writeNoDataFile()");
        try {
            textFile.deleteFile(writeFd361689TotPath);
            textFile.writeFileContent(writeFd361689TotPath, fileFd361689TotContents, CHARSET_UTF8);
            upload(writeFd361689TotPath, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(readFdPutfPath)) {
            upload(readFdPutfPath, "", "");
            forFsap();
            forFsapTot();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C1361689, // 來源檔案名稱(20碼長)
                        "", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV361689", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapTot() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C1361689, // 來源檔案名稱(20碼長)
                        "", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV361689_TOT", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private File downloadFromSftp(String fileFtpPath, String tarDir) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath, tarDir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            return null;
        }
        return file;
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", CONVF_PATH_037);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
