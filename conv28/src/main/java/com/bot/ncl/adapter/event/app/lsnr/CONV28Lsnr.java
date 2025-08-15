/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV28;
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
@Component("CONV28Lsnr")
@Scope("prototype")
public class CONV28Lsnr extends BatchListenerCase<CONV28> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV28 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_02C1146002 = "02C1146002";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String nbsdy;
    private String putfRcptid;
    private String date146002;
    private String rcptid146002;
    private String sdate146002;
    private String readFdPutfPath;
    private int putfDate;
    private int putfSitdate;
    private int wkTotcnt = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt146002 = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private List<String> fileFd146002Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV28 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV28Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV28 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV28Lsnr run()");

        init(event);

        chechFdPutfDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV28 event) {
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、設定檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        nbsdy = parse.decimal2String(event.getAggregateBuffer().getTxCom().getNbsdy(), 8, 0);
        String wkFdate = processDate.substring(1);

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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_02C1146002;
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
                        + CONVF_PATH_02C1146002; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfDataExist() {
        // FD-PUTF檔案存在，執行146002-RTN
        if (textFile.exists(readFdPutfPath)) {
            // 搬相關資料到146002-REC
            // 寫檔FD-146002
            writeRc1();
            readFdPutfData();
        }
    }

    private void writeRc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.padX("1", 1));
        sbRc1.append(formatUtil.padX("401", 3));
        sbRc1.append(formatUtil.padX("004", 3));
        sbRc1.append(formatUtil.padX("IPREMIUM", 8));
        sbRc1.append(formatUtil.padX("00054004052428", 14));
        sbRc1.append(formatUtil.padX(processDate, 7));
        sbRc1.append(formatUtil.padX(processDate, 7));
        sbRc1.append(formatUtil.padX(nbsdy.substring(1, 8), 7));
        sbRc1.append(formatUtil.padX("", 40));
        fileFd146002Contents.add(formatUtil.padX(sbRc1.toString(), 90));
    }

    private void readFdPutfData() {
        // 循序讀取FD-PUTF
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);

            int putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // IF PUTF-CTL = 11(明細),搬PUTF...到146002...
            if (putfCtl == 11) {
                putfRcptid = filePUTF.getRcptid();
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePUTF.getSitdate())
                                        ? filePUTF.getSitdate()
                                        : "0"));
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePUTF.getAmt()) ? filePUTF.getAmt() : "0"));

                valuateRc2();
                writeRc2();

                wkTotcnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
            } else if (putfCtl == 12) {
                // IF PUTF-CTL = 12(彙總),(LAST REC)
                writeSum();
            }
        }
    }

    private void valuateRc2() {
        rcptid146002 = putfRcptid;
        amt146002 = putfAmt;
        sdate146002 = "0" + parse.decimal2String(putfSitdate, 6, 0);
        date146002 = "0" + parse.decimal2String(putfDate, 6, 0);
        checkDateYear();
        checkSitdateYear();
    }

    private void checkSitdateYear() {
        if (putfSitdate < 990101) {
            sdate146002 = "1" + parse.decimal2String(putfSitdate, 6, 0);
        }
    }

    private void checkDateYear() {
        if (putfDate < 990101) {
            date146002 = "1" + parse.decimal2String(putfDate, 6, 0);
        }
    }

    private void writeRc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.padX("2", 1));
        sbRc2.append(formatUtil.pad9("0000000", 7));
        sbRc2.append(formatUtil.padX(rcptid146002, 16));
        sbRc2.append(formatUtil.pad9(processDate.substring(0, 5), 5));
        sbRc2.append(formatUtil.padX("", 2));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(amt146002), 12));
        sbRc2.append(formatUtil.padX("0000000", 7));
        sbRc2.append(formatUtil.pad9(sdate146002, 7));
        sbRc2.append(formatUtil.pad9(date146002, 7));
        sbRc2.append(formatUtil.padX("004", 3));
        sbRc2.append(formatUtil.padX("", 4));
        sbRc2.append(formatUtil.padX("", 20));
        fileFd146002Contents.add(formatUtil.padX(sbRc2.toString(), 100));
    }

    private void writeSum() {
        StringBuilder sbSum = new StringBuilder();
        sbSum.append(formatUtil.padX("9", 1));
        sbSum.append(formatUtil.pad9(parse.decimal2String(wkTotcnt, 8, 0), 8));
        sbSum.append(formatUtil.pad9(decimalFormat.format(wkTotAmt), 16));
        sbSum.append(formatUtil.padX("00000000", 8));
        sbSum.append(formatUtil.padX("0000000000", 10));
        sbSum.append(formatUtil.padX("", 48));
        fileFd146002Contents.add(formatUtil.padX(sbSum.toString(), 100));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd146002Contents, CHARSET_UTF8);
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

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_02C1146002, // 來源檔案名稱(20碼長)
                        CONVF_PATH_02C1146002, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV28", // 檔案設定代號 ex:CONVF001
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
