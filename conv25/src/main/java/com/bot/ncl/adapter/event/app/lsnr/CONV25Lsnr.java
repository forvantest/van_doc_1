/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV25;
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
@Component("CONV25Lsnr")
@Scope("prototype")
public class CONV25Lsnr extends BatchListenerCase<CONV25> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV25 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_17Z1145694 = "17Z1145694";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfRcptid;
    private String putfCllbr;
    private String putfTxtype;
    private String send145694;
    private String acton145694;
    private String ntd145694;
    private String date145694;
    private String txtype145694;
    private String recno145694;
    private String dscp145694;
    private String cantype145694;
    private String pbflag145694;
    private String cllbr145694;
    private String ckno145694;
    private String pbamtMinus145694;
    private String rcptid145694;
    private String txtype2_145694;
    private String recno2_145694;
    private String readFdPutfPath;
    private int putfDate;
    private int wkTotcnt = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt145694 = ZERO;
    private BigDecimal pbamt145694 = ZERO;
    private List<String> fileFd145694Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV25 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV25Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV25 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV25Lsnr run()");

        init(event);

        chechFdPutfDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV25 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV25Lsnr init()");
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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17Z1145694;
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
                        + CONVF_PATH_17Z1145694; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfDataExist() {
        // FD-PUTF檔案存在，執行145694-RTN
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
        }
    }

    private void readFdPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV25Lsnr readFdPutfData()");
        // 循序讀取FD-PUTF
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);

            int putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "0");

            // IF PUTF-CTL = 11(明細),搬PUTF...145694...
            if (putfCtl == 11) {
                putfRcptid = detail.substring(8, 24);
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(detail.substring(24, 30))
                                        ? detail.substring(24, 30)
                                        : "0");
                putfCllbr = detail.substring(36, 39);
                putfTxtype = detail.substring(99, 100);
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(100, 110))
                                        ? detail.substring(100, 110)
                                        : "0"));
                wkTotcnt++;

                // 搬PUTF...145694...
                valuateFd145694();
                writeFd145694();
            }
        }
    }

    private void valuateFd145694() {
        send145694 = "004";
        acton145694 = "00053001136789";
        ntd145694 = "00";
        date145694 = parse.decimal2String(putfDate, 6, 0);
        valuateTxtype();
        amt145694 = putfAmt;
        pbamtMinus145694 = "+";
        recno145694 = parse.decimal2String(wkTotcnt, 5, 0);
        dscp145694 = "00";
        cantype145694 = "0";
        pbflag145694 = "0";
        cllbr145694 = putfCllbr;
        ckno145694 = "00000000";
        pbamt145694 = parse.string2BigDecimal("00000000000000");
        rcptid145694 = putfRcptid;
        recno2_145694 = parse.decimal2String(wkTotcnt, 5, 0);
    }

    private void valuateTxtype() {
        if (putfTxtype.equals("C")) {
            txtype145694 = "1";
            txtype2_145694 = "1";
        } else if (putfTxtype.equals("M")) {
            txtype145694 = "2";
            txtype2_145694 = "2";
        }
    }

    private void writeFd145694() {
        StringBuilder sbFd = new StringBuilder();
        sbFd.append(formatUtil.padX(send145694, 3));
        sbFd.append(formatUtil.padX(acton145694, 14));
        sbFd.append(formatUtil.padX(ntd145694, 2));
        sbFd.append(formatUtil.pad9(date145694, 6));
        sbFd.append(formatUtil.pad9(txtype145694, 1));
        sbFd.append(formatUtil.pad9(decimalFormat.format(amt145694), 12));
        sbFd.append(formatUtil.pad9(recno145694, 5));
        sbFd.append(formatUtil.padX(dscp145694, 2));
        sbFd.append(formatUtil.padX(cantype145694, 1));
        sbFd.append(formatUtil.padX(pbflag145694, 1));
        sbFd.append(formatUtil.pad9(cllbr145694, 4));
        sbFd.append(formatUtil.padX(ckno145694, 8));
        sbFd.append(formatUtil.pad9(decimalFormat.format(pbamt145694), 12));
        sbFd.append(formatUtil.padX(pbamtMinus145694, 1));
        sbFd.append(formatUtil.padX(rcptid145694, 20));
        sbFd.append(formatUtil.padX(txtype2_145694, 1));
        sbFd.append(formatUtil.padX("", 20));
        sbFd.append(formatUtil.pad9(recno2_145694, 5));
        fileFd145694Contents.add(formatUtil.padX(sbFd.toString(), 150));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV25Lsnr writeFile()");
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd145694Contents, CHARSET_UTF8);
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
                        CONVF_PATH_17Z1145694, // 來源檔案名稱(20碼長)
                        CONVF_PATH_17Z1145694, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV25", // 檔案設定代號 ex:CONVF001
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
