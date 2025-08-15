/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV530004;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileNoDataPUTF;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
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
@Component("CONV530004Lsnr")
@Scope("prototype")
public class CONV530004Lsnr extends BatchListenerCase<CONV530004> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FileNoDataPUTF fileNoDataPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV530004 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String CONVF_PATH_17X1530004 = "17X1530004"; // 讀檔檔名 = wkConvfile
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String readFdPutfPath;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String rcptid530004;
    private String ctl530004;
    private String code530004;
    private String date530004;
    private String time530004;
    private String cllbr530004;
    private String lmtdate530004;
    private String oldamt530004;
    private String userdata530004;
    private String sitdate530004;
    private String txtype530004;
    private String bdate530004;
    private String edate530004;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int wkTotCnt = 0;
    private int totCnt530004 = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal amt530004 = ZERO;
    private BigDecimal totAmt530004 = ZERO;
    private List<String> fileFd530004Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV530004 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV530004Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV530004 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV530004Lsnr run()");

        init(event);

        checkFdPutfDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV530004 event) {
        // 讀批次日期檔
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init run()");
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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17X1530004;
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
                        + CONVF_PATH_17X1530004; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PUTF PATH = " + readFdPutfPath);
    }

    private void checkFdPutfDataExist() {
        // FD-PUTF檔案存在，執行530004-RTN
        if (textFile.exists(readFdPutfPath)) {
            readPutfData();
            valuateFd530004Sum();
            writeFd530004Sum();
            // writeFile();
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "該路徑無PUTF檔案");
        }
    }

    private void readPutfData() {
        // 循序讀取FD-PUTF
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

                // 搬PUTF-REC到530004-REC
                valuateFd530004Dtl();

                // 虛擬帳號，左靠右補空白
                modifyRcptid();

                // 寫檔FD-530004
                writeFd530004Dtl();

                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
            }
        }
    }

    private void valuateFd530004Dtl() {
        ctl530004 = parse.decimal2String(putfCtl, 2, 0);
        code530004 = putfCode;
        rcptid530004 = putfRcptid;
        date530004 = parse.decimal2String(putfDate, 6, 0);
        time530004 = parse.decimal2String(putfTime, 6, 0);
        cllbr530004 = putfCllbr;
        lmtdate530004 = parse.decimal2String(putfLmtdate, 6, 0);
        oldamt530004 = parse.decimal2String(putfOldamt, 8, 0);
        userdata530004 = putfUserdata;
        sitdate530004 = parse.decimal2String(putfSitdate, 6, 0);
        txtype530004 = putfTxtype;
        amt530004 = putfAmt;
    }

    private void writeFd530004Dtl() {
        StringBuilder sbDtl = new StringBuilder();
        sbDtl.append(formatUtil.pad9(ctl530004, 2));
        sbDtl.append(formatUtil.padX(code530004, 6));
        sbDtl.append(formatUtil.padX(rcptid530004, 16));
        sbDtl.append(formatUtil.pad9(date530004, 6));
        sbDtl.append(formatUtil.pad9(time530004, 6));
        sbDtl.append(formatUtil.pad9(cllbr530004, 3));
        sbDtl.append(formatUtil.pad9(lmtdate530004, 6));
        sbDtl.append(formatUtil.pad9(oldamt530004, 8));
        sbDtl.append(formatUtil.padX(userdata530004, 40));
        sbDtl.append(formatUtil.pad9(sitdate530004, 6));
        sbDtl.append(formatUtil.padX(txtype530004, 1));
        sbDtl.append(formatUtil.pad9(decimalFormat.format(amt530004), 11));
        sbDtl.append(formatUtil.padX("", 10));
        fileFd530004Contents.add(formatUtil.padX(sbDtl.toString(), 150));
    }

    private void modifyRcptid() {
        if (putfRcptid.startsWith("00")) {
            rcptid530004 = formatUtil.padX("  " + putfRcptid.substring(2, 16), 16);
        }
    }

    private void valuateFd530004Sum() {
        bdate530004 = processDate.substring(1);
        edate530004 = processDate.substring(1);
        totCnt530004 = wkTotCnt;
        totAmt530004 = wkTotAmt;
    }

    private void writeFd530004Sum() {
        StringBuilder sbSum = new StringBuilder();
        sbSum.append(formatUtil.pad9("12", 2));
        sbSum.append(formatUtil.padX(code530004, 6));
        sbSum.append(formatUtil.pad9(bdate530004, 6));
        sbSum.append(formatUtil.pad9(edate530004, 6));
        sbSum.append(formatUtil.pad9(cntFormat.format(totCnt530004), 6));
        sbSum.append(formatUtil.pad9(decimalFormat.format(totAmt530004), 14));
        sbSum.append(formatUtil.padX("", 80));
        fileFd530004Contents.add(formatUtil.padX(sbSum.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd530004Contents, CHARSET_UTF8);
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
                        CONVF_PATH_17X1530004, // 來源檔案名稱(20碼長)
                        CONVF_PATH_17X1530004, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV530004", // 檔案設定代號 ex:CONVF001
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
