/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV153894;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.files.TextFileUtil;
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
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV153894Lsnr")
@Scope("prototype")
public class CONV153894Lsnr extends BatchListenerCase<CONV153894> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV153894 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_07Z1153894 = "07Z1153894";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String code153894;
    private String rcptid153894;
    private String cllbr153894;
    private String userdata153894;
    private String txtype153894;
    private String readFdPutfPath;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int ctl153894;
    private int date153894;
    private int time153894;
    private int lmtdate153894;
    private int oldamt153894;
    private int sitdate153894;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt153894 = ZERO;
    private List<String> fileFd153894Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV153894 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV153894Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV153894 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV153894Lsnr run()");

        init(event);

        checkFdPutfDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV153894 event) {
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = processDate.substring(1);

        // 設定檔名變數值
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_07Z1153894;

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
                        + CONVF_PATH_07Z1153894; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void checkFdPutfDataExist() {
        // 若FD-PUTF檔案存在，執行153894-RTN
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
            //            writeFile();
        }
    }

    private void readFdPutfData() {
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);

        // 循序讀取FD-PUTF
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutf);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getCtl()) ? filePutf.getCtl() : "0");

            // IF PUTF-CTL NOT =  11(非明細)，LOOP讀下一筆FD-PUTF
            if (putfCtl == 11) {
                putfCode = filePutf.getCode();
                putfRcptid = filePutf.getRcptid();
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutf.getEntdy()) ? filePutf.getEntdy() : "0");
                putfTime =
                        parse.string2Integer(
                                parse.isNumeric(filePutf.getTime()) ? filePutf.getTime() : "0");
                putfCllbr = filePutf.getCllbr();
                putfLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutf.getLmtdate())
                                        ? filePutf.getLmtdate()
                                        : "0"));
                putfOldamt =
                        parse.string2Integer(
                                (parse.isNumeric(filePutf.getOldamt())
                                        ? filePutf.getOldamt()
                                        : "0"));
                putfUserdata = filePutf.getUserdata();
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutf.getSitdate())
                                        ? filePutf.getSitdate()
                                        : "0"));
                putfTxtype = filePutf.getTxtype();
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutf.getAmt()) ? filePutf.getAmt() : "0"));

                // 搬PUTF-REC給153894-REC
                valuateFd153894();

                // 寫檔FD-153894
                writeFd153894();
            }
        }
    }

    private void valuateFd153894() {
        ctl153894 = putfCtl;
        code153894 = putfCode;
        rcptid153894 = putfRcptid;
        date153894 = putfDate;
        time153894 = putfTime;
        cllbr153894 = putfCllbr;
        lmtdate153894 = putfLmtdate;
        oldamt153894 = putfOldamt;
        userdata153894 = putfUserdata;
        sitdate153894 = putfSitdate;
        txtype153894 = putfTxtype;
        amt153894 = putfAmt;
    }

    private void writeFd153894() {
        StringBuilder sbFd = new StringBuilder();
        sbFd.append(formatUtil.pad9(parse.decimal2String(ctl153894, 2, 0), 2));
        sbFd.append(formatUtil.padX(code153894, 6));
        sbFd.append(formatUtil.padX(rcptid153894, 16));
        sbFd.append(formatUtil.pad9(parse.decimal2String(date153894, 6, 0), 6));
        sbFd.append(formatUtil.pad9(parse.decimal2String(time153894, 6, 0), 6));
        sbFd.append(formatUtil.pad9(parse.decimal2String(cllbr153894, 3, 0), 3));
        sbFd.append(formatUtil.pad9(parse.decimal2String(lmtdate153894, 6, 0), 6));
        sbFd.append(formatUtil.pad9(parse.decimal2String(oldamt153894, 8, 0), 8));
        sbFd.append(formatUtil.padX(userdata153894, 40));
        sbFd.append(formatUtil.pad9(parse.decimal2String(sitdate153894, 6, 0), 6));
        sbFd.append(formatUtil.padX(txtype153894, 1));
        sbFd.append(formatUtil.pad9(decimalFormat.format(amt153894), 11));
        sbFd.append(formatUtil.padX("", 10));
        fileFd153894Contents.add(formatUtil.padX(sbFd.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd153894Contents, CHARSET_UTF8);
            upload(readFdPutfPath, "DATA", "PUTF");
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
                        CONVF_PATH_07Z1153894, // 來源檔案名稱(20碼長)
                        CONVF_PATH_07Z1153894, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV153894", // 檔案設定代號 ex:CONVF001
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
