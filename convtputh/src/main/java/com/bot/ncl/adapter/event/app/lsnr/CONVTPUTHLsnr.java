/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONVTPUTH;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTH;
import com.bot.ncl.util.files.TextFileUtil;
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
@Component("CONVTPUTHLsnr")
@Scope("prototype")
public class CONVTPUTHLsnr extends BatchListenerCase<CONVTPUTH> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTH filePUTH;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONVTPUTH event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_TTPUTH = "TTPUTH"; // 讀檔目錄
    private static final String CONVF_PATH_TPUTH = "TPUTH";
    private static final String CONVF_PATH_CRMHDR = "CRMHDR";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private String readFdPuthPath;
    private String writeFdTputhPath;
    private String writeFdCrmhdrPath;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String tbsdy8;
    private String puthPutName;
    private String puthCode;
    private String puthRcptid;
    private String puthUserData;
    private String puthTxType;
    private String puthPbrno;
    private String sysDate;
    private String sysTime;
    private int puthSerino;
    private int puthPutType;
    private int puthDate;
    private int puthTime;
    private int puthCllbr;
    private int puthLmtDate;
    private int puthSitDate;
    private int wkTotCnt = 0;
    private int wkDate = 0;
    private int wkLmtDate = 0;
    private int wkSitDate = 0;
    private BigDecimal puthAmt = ZERO;
    private List<String> fileFdTputhContents = new ArrayList<>();
    private List<String> fileFdCrmhdrContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONVTPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVTPUTHLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONVTPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVTPUTHLsnr run()");

        init(event);

        chechFdPuthDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONVTPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        tbsdy8 = parse.decimal2String(parse.string2Integer(processDate) + 19110000, 8, 0);

        sysDate = dateUtil.getNowStringRoc(); // 取系統日
        sysTime = dateUtil.getNowStringTime(false);
        String readFdDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        readFdPuthPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_TTPUTH;
        textFile.deleteFile(readFdPuthPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_TTPUTH; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPuthPath = getLocalPath(sourceFile);
        }

        writeFdTputhPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_TPUTH;

        writeFdCrmhdrPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CRMHDR;
    }

    private void chechFdPuthDataExist() {
        if (textFile.exists(readFdPuthPath)) {
            readputhData();
            writeFdCrmhdr();
            //            writeFile();
        } else {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "該路徑無PUTH檔案");
        }
    }

    private void readputhData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readputhData()");
        List<String> lines = textFile.readFileContent(readFdPuthPath, CHARSET_UTF8);

        if (Objects.isNull(readFdPuthPath) || readFdPuthPath.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "puthFileContent is null");
            return;
        }

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTH);

            puthPutType =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getPuttype()) ? filePUTH.getPuttype() : "0");
            puthPutName = filePUTH.getPutname();
            puthCode = filePUTH.getCode();
            puthRcptid = filePUTH.getRcptid();
            puthDate =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getEntdy()) ? filePUTH.getEntdy() : "0");
            puthTime =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getTime()) ? filePUTH.getTime() : "0");
            puthCllbr =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getCllbr()) ? filePUTH.getCllbr() : "0");
            puthLmtDate =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getLmtdate()) ? filePUTH.getLmtdate() : "0");
            puthAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(filePUTH.getAmt()) ? filePUTH.getAmt() : "0");
            puthUserData = filePUTH.getUserdata();
            puthSitDate =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getSitdate()) ? filePUTH.getSitdate() : "0");
            puthTxType = filePUTH.getTxtype();
            puthSerino =
                    parse.string2Integer(
                            parse.isNumeric(filePUTH.getSerino()) ? filePUTH.getSerino() : "0");
            puthPbrno = filePUTH.getPbrno();

            wkTotCnt++;

            modifyDate();

            modifyLmtDate();

            modifySitDate();

            writeFdTputh();
        }
    }

    private void modifyDate() {
        if (puthDate < 500101 && puthDate != 0) {
            wkDate = puthDate + 19110000 + 1000000;
        } else {
            wkDate = puthDate + 19110000;
        }
    }

    private void modifyLmtDate() {
        if (puthLmtDate < 500101) {
            wkLmtDate = puthLmtDate + 19110000 + 1000000;
        } else {
            wkLmtDate = puthLmtDate + 19110000;
        }
    }

    private void modifySitDate() {
        if (puthSitDate < 500101) {
            wkSitDate = puthSitDate + 19110000 + 1000000;
        } else {
            wkSitDate = puthSitDate + 19110000;
        }
    }

    private void writeFdTputh() {
        StringBuilder sbFdTputh = new StringBuilder();
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(puthPutType, 2, 0), 2));
        sbFdTputh.append(formatUtil.padX(puthPutName, 8));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.padX(puthCode, 6));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.padX(puthRcptid, 26));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(wkDate, 8, 0), 8));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(puthTime, 6, 0), 6));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(puthCllbr, 3, 0), 3));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(wkLmtDate, 8, 0), 8));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(decimalFormat.format(puthAmt), 13));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.padX("\"\"", 2));
        sbFdTputh.append(formatUtil.padX(puthUserData, 40));
        sbFdTputh.append(formatUtil.padX("\"\"", 2));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(wkSitDate, 8, 0), 8));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.padX(puthTxType, 1));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.pad9(parse.decimal2String(puthSerino, 6, 0), 6));
        sbFdTputh.append(formatUtil.padX(",", 1));
        sbFdTputh.append(formatUtil.padX(puthPbrno, 3));
        sbFdTputh.append(formatUtil.padX("", 43));
        fileFdTputhContents.add(formatUtil.padX(sbFdTputh.toString(), 200));
    }

    private void writeFdCrmhdr() {
        StringBuilder sbFdCrmhdr = new StringBuilder();
        sbFdCrmhdr.append(formatUtil.pad9(tbsdy8, 8));
        sbFdCrmhdr.append(formatUtil.pad9(tbsdy8, 8));
        sbFdCrmhdr.append(formatUtil.padX("DUOT0005.D", 10));
        sbFdCrmhdr.append(formatUtil.pad9(sysDate, 8));
        sbFdCrmhdr.append(formatUtil.pad9(sysTime, 6));
        sbFdCrmhdr.append(formatUtil.pad9(parse.decimal2String(wkTotCnt, 10, 0), 10));
        fileFdCrmhdrContents.add(formatUtil.padX(sbFdCrmhdr.toString(), 50));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeFile()");
        try {
            textFile.deleteFile(writeFdTputhPath);
            textFile.deleteFile(writeFdCrmhdrPath);
            textFile.writeFileContent(writeFdTputhPath, fileFdTputhContents, CHARSET_UTF8);
            upload(writeFdTputhPath, "DATA", "");
            textFile.writeFileContent(writeFdCrmhdrPath, fileFdCrmhdrContents, CHARSET_UTF8);
            upload(writeFdCrmhdrPath, "DATA", "");
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
        if (textFile.exists(readFdPuthPath)) {
            upload(readFdPuthPath, "", "");
            forFsapTPUTH();
            forFsapCRMHDR();
        }
    }

    private void forFsapTPUTH() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_TTPUTH, // 來源檔案名稱(20碼長)
                        CONVF_PATH_TPUTH, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONVTPUTH", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapCRMHDR() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_TTPUTH, // 來源檔案名稱(20碼長)
                        CONVF_PATH_CRMHDR, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONVTPUTH_CRMHDR", // 檔案設定代號 ex:CONVF001
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
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
