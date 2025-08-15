/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Conv19;
import com.bot.ncl.util.FsapBatchUtil;
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
@Component("Conv19Lsnr")
@Scope("prototype")
public class Conv19Lsnr extends BatchListenerCase<Conv19> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private Conv19 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF";
    // 設定檔名變數值
    private static final String CONVF_PATH_22C03668Q9 = "22C03668Q9";
    private static final String CONVF_PATH_011 = "CL-BH-011";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final String PATH_SEPARATOR = File.separator;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String readFdPutfPath;
    private String writeReportPath;
    private String wkReportPdate;
    private String reportDate;
    private String reportBdate;
    private String reportEdate;
    // WK-FLAG非首筆註記設為"N" ("N":首筆 "Y":非首筆)
    private String wkFlag = "N";
    private int wkCompdate;
    private int putfDate;
    private int putfBdate;
    private int putfEdate;
    private int wkSubcnt = 0;
    private int reportCnt;
    private int putfTotcnt;
    private int reportTotcnt;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal putfTotAmt = ZERO;
    private BigDecimal reportAmt = ZERO;
    private BigDecimal reportFee = ZERO;
    private BigDecimal wkSubamt = ZERO;
    private BigDecimal reportAcamt = ZERO;
    private BigDecimal reportTotamt = ZERO;
    private BigDecimal reportTotfee = ZERO;
    private BigDecimal reportTotAcamt = ZERO;
    private List<String> fileReportContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(Conv19 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Conv19 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr run()");

        init(event);

        checkFdPutfExist();

        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(Conv19 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr init()");
        // 開啟批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 設定工作日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);

        wkReportPdate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7);

        // 設定檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C03668Q9;
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
                        + CONVF_PATH_22C03668Q9; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }

        writeReportPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_011;
    }

    private void checkFdPutfExist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr checkFdPutfExist()");
        // 若FD-PUTF檔案存在，執行301007-RTN
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
            textFile.deleteFile(readFdPutfPath);
        }
    }

    private void readFdPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr readFdPutfData()");
        // 循序讀取FD-PUTF，直到檔尾，跳到301007-FILE
        // 若PUTF-CTL = 21 (對帳明細)往下一步驟執行
        // 若PUTF-CTL = 22 (對帳彙總)
        //  A.搬相關資料到301007-DTL...
        //  B.寫報表明細
        //  C.執行301007-WTOT，寫表尾
        //  D.LOOP讀下一筆FD-PUTF
        // 其他，LOOP讀下一筆FD-PUTF
        // 開啟檔案
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);

            int putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            if (putfCtl == 21) {
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePUTF.getAmt()) ? filePUTF.getAmt() : "0"));
                changeFlag();
                changeDate();
            } else if (putfCtl == 22) {
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
                putfTotcnt =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTF.getTotcnt())
                                        ? fileSumPUTF.getTotcnt()
                                        : "0"));
                putfTotAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(fileSumPUTF.getTotamt())
                                        ? fileSumPUTF.getTotamt()
                                        : "0"));
                valuateReportContents();
                writeReportContents();
                valuateReportFooter();
                writeReportFooter();
            }
        }
    }

    private void writeReportHeader() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr writeReportHeader()");
        int page = 1;
        // 寫REPORTFL報表表頭(301007-TIT1~301007-TIT4)
        StringBuilder stringBuilderH1 = new StringBuilder();
        stringBuilderH1.append(formatUtil.padX("", 2));
        stringBuilderH1.append(formatUtil.padX(" 代收單位：台灣銀行 ", 20));
        stringBuilderH1.append(formatUtil.padX("", 11));
        stringBuilderH1.append(formatUtil.padX(" 週　結　清　單 ", 16));
        stringBuilderH1.append(formatUtil.padX("", 10));
        stringBuilderH1.append(formatUtil.padX(" 印表日： ", 10));
        stringBuilderH1.append(formatUtil.pad9(wkReportPdate.substring(0, 3), 3));
        stringBuilderH1.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderH1.append(formatUtil.pad9(wkReportPdate.substring(3, 5), 2));
        stringBuilderH1.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderH1.append(formatUtil.pad9(wkReportPdate.substring(5, 7), 2));
        fileReportContents.add(formatUtil.padX(stringBuilderH1.toString(), 120));

        StringBuilder stringBuilderH2 = new StringBuilder();
        stringBuilderH2.append(formatUtil.padX("", 2));
        stringBuilderH2.append(formatUtil.padX(" 代收名稱：金門縣自來水廠 ", 26));
        stringBuilderH2.append(formatUtil.padX("", 31));
        stringBuilderH2.append(formatUtil.padX(" 頁　次： ", 10));
        stringBuilderH2.append(formatUtil.pad9("" + page, 2));
        fileReportContents.add(formatUtil.padX(stringBuilderH2.toString(), 120));

        StringBuilder stringBuilderH3 = new StringBuilder();
        stringBuilderH3.append(formatUtil.padX("", 2));
        stringBuilderH3.append(formatUtil.padX(" 主辦分行： ", 12));
        stringBuilderH3.append(formatUtil.padX("038", 3));
        stringBuilderH3.append(formatUtil.padX("", 42));
        stringBuilderH3.append(formatUtil.padX(" 報表名稱： ", 12));
        stringBuilderH3.append(formatUtil.padX("C011", 4));
        fileReportContents.add(formatUtil.padX(stringBuilderH3.toString(), 120));

        StringBuilder stringBuilderH4 = new StringBuilder();
        stringBuilderH4.append(formatUtil.padX("", 3));
        stringBuilderH4.append(formatUtil.padX(" 代收日期 ", 10));
        stringBuilderH4.append(formatUtil.padX("", 6));
        stringBuilderH4.append(formatUtil.padX(" 總金額 ", 8));
        stringBuilderH4.append(formatUtil.padX("", 5));
        stringBuilderH4.append(formatUtil.padX(" 總件數 ", 8));
        stringBuilderH4.append(formatUtil.padX("", 5));
        stringBuilderH4.append(formatUtil.padX(" 手續費 ", 8));
        stringBuilderH4.append(formatUtil.padX("", 6));
        stringBuilderH4.append(formatUtil.padX(" 入帳金額 ", 10));
        stringBuilderH4.append(formatUtil.padX("", 4));
        stringBuilderH4.append(formatUtil.padX(" 備註 ", 6));
        fileReportContents.add(formatUtil.padX(stringBuilderH4.toString(), 120));

        StringBuilder stringBuilderH5 = new StringBuilder();
        stringBuilderH5.append(reportUtil.makeGate("-", 80));
        fileReportContents.add(formatUtil.padX(stringBuilderH5.toString(), 120));
    }

    private void valuateReportContents() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Conv19Lsnr valuateReportContents()");
        // 依代收日期，累加筆數、金額
        reportDate = "1" + parse.decimal2String(wkCompdate, 6, 0);
        wkSubcnt++;
        wkSubamt = wkSubamt.add(putfAmt);
        reportCnt = wkSubcnt;
        reportAmt = wkSubamt;
        reportFee = BigDecimal.valueOf(2.5).multiply(BigDecimal.valueOf(wkSubcnt));
        reportAcamt = reportAmt.subtract(reportFee);
    }

    private void changeFlag() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr changeFlag()");
        // 首筆
        //  A.搬日期
        //  B.執行301007-WTIT，寫表頭
        //  C.WK-FLAG非首筆註記設為"Y" ("N":首筆 "Y":非首筆)
        if (!wkFlag.equals("Y")) {
            wkFlag = "Y";
            writeReportHeader();
        }
    }

    private void changeDate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr changeDate()");
        // 代收日期不同，寫報表明細
        if (wkFlag.equals("Y") && wkCompdate != putfDate) {
            valuateReportContents();
            writeReportContents();
            wkCompdate = putfDate;
            wkSubcnt = 1;
            wkSubamt = putfAmt;
        }
    }

    private void writeReportContents() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr writeReportContents()");
        StringBuilder stringBuilderC1 = new StringBuilder();
        stringBuilderC1.append(formatUtil.padX("", 3));
        stringBuilderC1.append(formatUtil.padX(reportDate.substring(0, 3), 3));
        stringBuilderC1.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderC1.append(formatUtil.padX(reportDate.substring(3, 5), 2));
        stringBuilderC1.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderC1.append(formatUtil.padX(reportDate.substring(5, 7), 2));
        stringBuilderC1.append(formatUtil.padX("", 3));
        stringBuilderC1.append(formatUtil.padX(decimalFormat.format(reportAmt), 11));
        stringBuilderC1.append(formatUtil.padX("", 3));
        stringBuilderC1.append(formatUtil.padX(cntFormat.format(reportCnt), 10));
        stringBuilderC1.append(formatUtil.padX("", 4));
        stringBuilderC1.append(formatUtil.padX(decimalFormat.format(reportFee), 9));
        stringBuilderC1.append(formatUtil.padX("", 3));
        stringBuilderC1.append(formatUtil.padX(decimalFormat.format(reportAcamt), 13));
        fileReportContents.add(formatUtil.padX(stringBuilderC1.toString(), 120));
    }

    private void valuateReportFooter() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr valuateReportFooter()");
        reportTotamt = putfTotAmt;
        reportTotcnt = putfTotcnt;
        reportTotfee =
                BigDecimal.valueOf(putfTotcnt)
                        .multiply(BigDecimal.valueOf(2.5))
                        .add(BigDecimal.valueOf(0.5));
        reportTotAcamt = reportTotamt.subtract(reportTotfee);
        reportBdate = parse.decimal2String(putfBdate, 7, 0);
        reportEdate = parse.decimal2String(putfEdate, 7, 0);
    }

    private void writeReportFooter() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr writeReportFooter()");
        // 寫REPORTFL報表表尾(301007-TOT、301007-TIT5)
        StringBuilder stringBuilderF1 = new StringBuilder();
        stringBuilderF1.append(formatUtil.padX("", 5));
        stringBuilderF1.append(formatUtil.padX(" 總　計 ", 8));
        stringBuilderF1.append(formatUtil.padX(decimalFormat.format(reportTotamt), 13));
        stringBuilderF1.append(formatUtil.padX("", 3));
        stringBuilderF1.append(formatUtil.padX(cntFormat.format(reportTotcnt), 10));
        stringBuilderF1.append(formatUtil.padX("", 3));
        stringBuilderF1.append(formatUtil.padX(decimalFormat.format(reportTotfee), 10));
        stringBuilderF1.append(formatUtil.padX("", 5));
        stringBuilderF1.append(formatUtil.padX(decimalFormat.format(reportTotAcamt), 11));
        fileReportContents.add(formatUtil.padX(stringBuilderF1.toString(), 120));

        StringBuilder stringBuilderF2 = new StringBuilder();
        stringBuilderF2.append(formatUtil.padX("", 2));
        stringBuilderF2.append(formatUtil.padX(" 代收期間： ", 12));
        stringBuilderF2.append(formatUtil.padX(reportBdate.substring(0, 3), 3));
        stringBuilderF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderF2.append(formatUtil.padX(reportBdate.substring(3, 5), 2));
        stringBuilderF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderF2.append(formatUtil.padX(reportBdate.substring(5, 7), 2));
        stringBuilderF2.append(formatUtil.padX("-", 1));
        stringBuilderF2.append(formatUtil.padX(reportEdate.substring(0, 3), 3));
        stringBuilderF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderF2.append(formatUtil.padX(reportEdate.substring(3, 5), 2));
        stringBuilderF2.append(formatUtil.padX(PATH_SEPARATOR, 1));
        stringBuilderF2.append(formatUtil.padX(reportEdate.substring(5, 7), 2));
        fileReportContents.add(formatUtil.padX(stringBuilderF2.toString(), 120));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv19Lsnr writeFile()");
        try {
            textFile.deleteFile(writeReportPath);
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_Big5);
            upload(writeReportPath, "RPT", "");
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
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C03668Q9, // 來源檔案名稱(20碼長)
                        CONVF_PATH_22C03668Q9, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV19", // 檔案設定代號 ex:CONVF001
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
        responseTextMap.put("RPTNAME", CONVF_PATH_011);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
