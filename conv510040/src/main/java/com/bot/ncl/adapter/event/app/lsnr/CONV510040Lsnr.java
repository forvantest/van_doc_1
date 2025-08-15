/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV510040;
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
@Component("CONV510040Lsnr")
@Scope("prototype")
public class CONV510040Lsnr extends BatchListenerCase<CONV510040> {

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

    private CONV510040 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String CONVF_PATH_07X1510040 = "07X1510040";
    private static final String CONVF_PATH_017 = "CL-BH-017";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String readFdPutfPath;
    private String writeReportPath;
    private String processDate;
    private String tbsdy;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String putfNodata;
    private String putfFiller;
    private String filler;
    private String filler1;
    private String rc510040;
    private String send510040;
    private String bh510040;
    private String prcdate510040;
    private String wkCtl;
    private String actno510040;
    private String bill510040;
    private String ofisno510040;
    private String no510040;
    private String YYYMM510040;
    private String lmtdate510040;
    private String date510040;
    private String sitdate510040;
    private String rptBrno = "000";
    private String rptSubCnt;
    private String wkBank;
    private String rptTitle;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int wkTotCnt = 0;
    private int wkSubCnt = 0;
    private int wkCtlSwitchFlag = 0;
    private int totCnt510040 = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal putfTotAmt = ZERO;
    private BigDecimal putfTotCnt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal wkSubAmt = ZERO;
    private BigDecimal amt510040 = ZERO;
    private BigDecimal rptSubAmt = ZERO;
    private BigDecimal totAmt510040 = ZERO;
    private List<String> fileFd510040Contents = new ArrayList<>();
    private List<String> fileReportContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV510040 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV510040Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV510040 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV510040Lsnr run()");

        init(event);

        checkFdPutfDataExist();
        batchResponse();
    }

    private void init(CONV510040 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init run()");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = processDate.substring(1);
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_07X1510040;
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
                        + CONVF_PATH_07X1510040; // 來源檔在FTP的位置
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
                        + CONVF_PATH_017;
    }

    private void checkFdPutfDataExist() {
        if (textFile.exists(readFdPutfPath)) {
            rptTitle = " 中華電信全國性繳費彙總表　　　 ";
            valuateRc1();
            writeRc1();
            writeReportHeader();
            readPutfData();
            writeFile();
        } else {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "該路徑無PUTF檔案");
        }
    }

    private void readPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readPutfData run()");
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");
            if (putfCtl == 11) {
                putfCode = filePUTF.getCode();
                putfRcptid = filePUTF.getRcptid();
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
                putfTime =
                        parse.string2Integer(
                                parse.isNumeric(filePUTF.getTime()) ? filePUTF.getTime() : "0");
                putfCllbr = filePUTF.getCllbr();
                putfLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePUTF.getLmtdate())
                                        ? filePUTF.getLmtdate()
                                        : "0"));
                putfOldamt =
                        parse.string2Integer(
                                (parse.isNumeric(filePUTF.getOldamt())
                                        ? filePUTF.getOldamt()
                                        : "0"));
                putfUserdata = filePUTF.getUserdata();
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePUTF.getSitdate())
                                        ? filePUTF.getSitdate()
                                        : "0"));
                putfTxtype = filePUTF.getTxtype();
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePUTF.getAmt()) ? filePUTF.getAmt() : "0"));
                putfFiller = filePUTF.getFiller();
                valuateRc2();
                writeRc2();
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
                valuateReportDtl();
                writeReportDtl();
            } else if (putfCtl == 12) {
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
                putfTotCnt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(fileSumPUTF.getTotcnt())
                                        ? fileSumPUTF.getTotcnt()
                                        : "0"));
                putfTotAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(fileSumPUTF.getTotamt())
                                        ? fileSumPUTF.getTotamt()
                                        : "0"));
                filler = fileSumPUTF.getFiller();
                valuateRc2();
                writeRc2();
                valuateRc3();
                writeRc3();
                writeReportFooter();
            } else if (putfCtl == 21) {
                checkFlag();
                valuateRc2();
                writeRc2();
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
                valuateReportDtl();
                writeReportDtl();
            } else if (putfCtl == 22) {
                valuateRc2();
                writeRc2();
                valuateRc3();
                writeRc3();
            } else {
                putfNodata = fileNoDataPUTF.getNodata();
                filler1 = fileNoDataPUTF.getFiller();
            }
        }
    }

    private void valuateRc1() {
        rc510040 = "1";
        send510040 = "0040000";
        bh510040 = "001";
        prcdate510040 = processDate.substring(1);
        wkBank = "000";
        wkCtl = "00";
    }

    private void writeRc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.padX(rc510040, 1));
        sbRc1.append(formatUtil.padX(send510040, 7));
        sbRc1.append(formatUtil.padX(bh510040, 3));
        sbRc1.append(formatUtil.padX(prcdate510040, 7));
        sbRc1.append(formatUtil.padX("", 92));
        fileFd510040Contents.add(formatUtil.padX(sbRc1.toString(), 120));
    }

    private void writeReportHeader() {
        StringBuilder sbRh1 = new StringBuilder();
        sbRh1.append(formatUtil.padX("", 23));
        sbRh1.append(formatUtil.padX(rptTitle, 32));
        sbRh1.append(formatUtil.padX("", 21));
        sbRh1.append(formatUtil.padX("FORM : C018 ", 12));
        fileReportContents.add(formatUtil.padX(sbRh1.toString(), 100));

        fileReportContents.add("");

        StringBuilder sbRh2 = new StringBuilder();
        sbRh2.append(formatUtil.padX(" 分行別： ", 10));
        sbRh2.append(formatUtil.pad9("054", 3));
        sbRh2.append(formatUtil.padX("", 5));
        sbRh2.append(formatUtil.padX("  印表日期： ", 13));
        sbRh2.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + PATH_SEPARATOR
                                + processDate.substring(3, 5)
                                + PATH_SEPARATOR
                                + processDate.substring(5, 7),
                        9));
        sbRh2.append(formatUtil.padX("", 38));
        sbRh2.append(formatUtil.padX(" 總頁次 :", 9));
        sbRh2.append(formatUtil.pad9("1", 4));
        sbRh2.append(formatUtil.padX("", 15));
        fileReportContents.add(formatUtil.padX(sbRh2.toString(), 120));

        fileReportContents.add("");

        StringBuilder sbRh3 = new StringBuilder();
        sbRh3.append(formatUtil.padX("", 11));
        sbRh3.append(formatUtil.padX(" 銀行別 ", 8));
        sbRh3.append(formatUtil.padX("", 20));
        sbRh3.append(formatUtil.padX(" 筆數 ", 6));
        sbRh3.append(formatUtil.padX("", 20));
        sbRh3.append(formatUtil.padX(" 金額 ", 6));
        sbRh3.append(formatUtil.padX("", 30));
        fileReportContents.add(formatUtil.padX(sbRh3.toString(), 120));

        StringBuilder sbRh4 = new StringBuilder();
        sbRh4.append(formatUtil.padX("", 3));
        sbRh4.append(reportUtil.makeGate("-", 97));
        fileReportContents.add(formatUtil.padX(sbRh4.toString(), 120));
    }

    private void valuateRc2() {
        rc510040 = "2";
        actno510040 = putfUserdata.substring(1, 4) + putfUserdata.substring(6, 20);
        bill510040 = putfRcptid.substring(14, 16);
        ofisno510040 = putfUserdata.substring(28, 32);
        no510040 = putfRcptid.substring(0, 10);
        lmtdate510040 = putfUserdata.substring(22, 28);
        amt510040 = putfAmt;
        date510040 = parse.decimal2String(putfDate, 6, 0);
        sitdate510040 = parse.decimal2String(putfSitdate, 6, 0);
        checkPutfSitdate();
        wkCtl = parse.decimal2String(putfCtl, 2, 0);
        YYYMM510040 = putfRcptid.substring(10, 14);
    }

    private void checkPutfSitdate() {
        if (putfSitdate == 0) {
            sitdate510040 = parse.decimal2String(putfDate, 6, 0);
        }
    }

    private void writeRc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.padX(rc510040, 1));
        sbRc2.append(formatUtil.padX(actno510040, 17));
        sbRc2.append(formatUtil.padX(bill510040, 2));
        sbRc2.append(formatUtil.padX(ofisno510040, 4));
        sbRc2.append(formatUtil.padX("", 2));
        sbRc2.append(formatUtil.padX(no510040, 10));
        sbRc2.append(formatUtil.pad9("1", 1));
        sbRc2.append(formatUtil.pad9(processDate.substring(0, 4), 4));
        sbRc2.append(formatUtil.pad9("0", 1));
        sbRc2.append(formatUtil.pad9(lmtdate510040, 6));
        sbRc2.append(formatUtil.pad9("" + amt510040, 10));
        sbRc2.append(formatUtil.padX("", 2));
        sbRc2.append(formatUtil.pad9("1", 1));
        sbRc2.append(formatUtil.pad9(date510040, 6));
        sbRc2.append(formatUtil.padX("2", 4));
        sbRc2.append(formatUtil.padX("I", 1));
        sbRc2.append(formatUtil.padX("0040000", 7));
        sbRc2.append(formatUtil.padX("1", 1));
        sbRc2.append(formatUtil.padX("TD", 2));
        sbRc2.append(formatUtil.pad9("1", 1));
        sbRc2.append(formatUtil.pad9(sitdate510040, 6));
        sbRc2.append(formatUtil.padX("", 22));
        fileFd510040Contents.add(formatUtil.padX(sbRc2.toString(), 120));

        fileReportContents.add("");
    }

    private void valuateReportDtl() {
        wkBank = putfUserdata.substring(1, 4);
        if ("000".equals(rptBrno)) {
            rptBrno = wkBank;
            wkSubCnt++;
            wkSubAmt = wkSubAmt.add(putfAmt);
        } else {
            if (wkBank.equals(rptBrno)) {
                wkSubCnt++;
                wkSubAmt = wkSubAmt.add(putfAmt);
            } else {
                rptSubCnt = parse.decimal2String(wkSubCnt, 8, 0);
                rptSubAmt = wkSubAmt;
                wkSubCnt = 1;
                wkSubAmt = putfAmt;
                rptBrno = wkBank;
            }
        }
    }

    private void writeReportDtl() {
        StringBuilder sbRd1 = new StringBuilder();
        sbRd1.append(formatUtil.padX("", 15));
        sbRd1.append(formatUtil.pad9(rptBrno, 3));
        sbRd1.append(formatUtil.padX("", 18));
        sbRd1.append(formatUtil.pad9(rptSubCnt, 8));
        sbRd1.append(formatUtil.padX("", 15));
        sbRd1.append(reportUtil.customFormat("" + rptSubAmt, "ZZZ,ZZZ,ZZ9"));
        sbRd1.append(formatUtil.padX("", 30));
        fileReportContents.add(formatUtil.padX(sbRd1.toString(), 120));
    }

    private void valuateRc3() {
        rc510040 = "3";
        totCnt510040 = wkTotCnt;
        totAmt510040 = wkTotAmt;
    }

    private void writeRc3() {
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.padX(rc510040, 1));
        sbRc3.append(formatUtil.pad9("" + totCnt510040, 9));
        sbRc3.append(formatUtil.pad9("" + totAmt510040, 13));
        sbRc3.append(formatUtil.padX("", 87));
        fileReportContents.add(formatUtil.padX(sbRc3.toString(), 120));
    }

    private void writeReportFooter() {
        StringBuilder sbRF1 = new StringBuilder();
        sbRF1.append(formatUtil.padX("", 3));
        sbRF1.append(reportUtil.makeGate("-", 117));
        fileReportContents.add(formatUtil.padX(sbRF1.toString(), 120));

        StringBuilder sbrf2 = new StringBuilder();
        sbrf2.append(formatUtil.padX("", 13));
        sbrf2.append(formatUtil.padX(" 總計 ", 6));
        sbrf2.append(formatUtil.padX("", 17));
        sbrf2.append(formatUtil.pad9("" + wkTotCnt, 8));
        sbrf2.append(formatUtil.padX("", 13));
        sbrf2.append(reportUtil.customFormat("" + wkTotAmt, "Z,ZZZ,ZZZ,ZZ9"));
        sbrf2.append(formatUtil.padX("", 30));
        fileReportContents.add(formatUtil.padX(sbrf2.toString(), 120));
    }

    private void checkFlag() {
        if (wkCtlSwitchFlag == 0) {
            wkCtlSwitchFlag = 1;
            wkBank = "000";
            rptBrno = "000";
            rptSubCnt = parse.decimal2String(wkSubCnt, 8, 0);
            rptSubAmt = wkSubAmt;
            wkTotCnt = 0;
            wkSubCnt = 0;
            wkTotAmt = ZERO;
            wkSubAmt = ZERO;
            rptTitle = " 中華電信全國性繳費彙總表月報　 ";
            writeReportHeader();
        }
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.deleteFile(writeReportPath);
            textFile.writeFileContent(readFdPutfPath, fileFd510040Contents, CHARSET_UTF8);
            upload(readFdPutfPath, "DATA", "PUTF");
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_Big5);
            upload(writeReportPath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
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
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
