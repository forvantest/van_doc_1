/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV113522;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.*;
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
@Component("CONV113522Lsnr")
@Scope("prototype")
public class CONV113522Lsnr extends BatchListenerCase<CONV113522> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV113522 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_07Z1113522 = "07Z1113522";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String tbsdy;
    private String readFdPutfPath;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String code113522;
    private String rcptid113522;
    private String cllbr113522;
    private String userdata113522;
    private String txtype113522;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int ctl113522;
    private int date113522;
    private int time113522;
    private int lmtdate113522;
    private int oldamt113522;
    private int sitdate113522;
    private int bdate113522;
    private int edate113522;
    private int wkTotcnt;
    private int totcnt113522 = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt113522 = ZERO;
    private BigDecimal totamt113522 = ZERO;
    private BigDecimal wkTotamt;
    private List<String> fileFd113522Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV113522 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV113522 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr run()");

        init(event);

        checkPutfDataExist();

        // writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV113522 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);

        // 清變數
        wkTotcnt = 0;
        wkTotamt = ZERO;

        // 設定檔名變數值
        // WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        // WK-PUTDIR  <-"DATA/CL/BH/PUTF/" +WK-FDATE+"/07Z1113522."
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_07Z1113522;

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
                        + CONVF_PATH_07Z1113522; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void checkPutfDataExist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "checkPutfDataExist()");
        // FD-PUTF檔案存在，執行113522-RTN
        // 若不存在，執行113522-RTN
        if (textFile.exists(readFdPutfPath)) {
            readPutf();
            valuateFd113522Sum();
            writeFd113522Sum();
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "該路徑無PUTF檔案");
        }
    }

    private void readPutf() {
        // 循序讀取FD-PUTF
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readFilePutf()");
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);

        if (Objects.isNull(readFdPutfPath) || readFdPutfPath.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putfFileContent is null");
            return;
        }

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");
            if (putfCtl == 11) {
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
                valuateFd113522Dtl();
                writeFd113522Dtl();
                wkTotcnt++;
                wkTotamt = wkTotamt.add(putfAmt);
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
                valuateBdateEdate();
            }
        }
    }

    private void valuateFd113522Dtl() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr valuateFd113522Dtl()");
        ctl113522 = putfCtl;
        code113522 = "113522";
        rcptid113522 = putfRcptid;
        date113522 = putfDate;
        time113522 = putfTime;
        cllbr113522 = putfCllbr;
        lmtdate113522 = putfLmtdate;
        oldamt113522 = putfOldamt;
        userdata113522 = putfUserdata;
        sitdate113522 = putfSitdate;
        txtype113522 = putfTxtype;
        amt113522 = putfAmt;
    }

    private void valuateBdateEdate() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr valuateBdateEdate()");
        if (putfCtl != 11 && (putfBdate < bdate113522 || bdate113522 == 0)) {
            bdate113522 = putfBdate;
        } else if (putfEdate > edate113522) {
            edate113522 = putfEdate;
        }
    }

    private void valuateFd113522Sum() {
        totcnt113522 = wkTotcnt;
        totamt113522 = wkTotamt;
    }

    private void writeFd113522Dtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr writeFd113522Dtl()");
        StringBuilder sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(ctl113522, 2, 0), 2));
        sbFdDtl.append(formatUtil.padX(code113522, 6));
        sbFdDtl.append(formatUtil.padX(rcptid113522, 16));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(date113522, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(time113522, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(cllbr113522, 3, 0), 3));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(lmtdate113522, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(oldamt113522, 8, 0), 8));
        sbFdDtl.append(formatUtil.padX(userdata113522, 40));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(sitdate113522, 6, 0), 6));
        sbFdDtl.append(formatUtil.padX(txtype113522, 1));
        sbFdDtl.append(formatUtil.pad9(decimalFormat.format(amt113522), 10));
        sbFdDtl.append(formatUtil.padX(" ", 10));
        fileFd113522Contents.add(formatUtil.padX(sbFdDtl.toString(), 120));
    }

    private void writeFd113522Sum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV113522Lsnr writeFd113522Sum()");
        StringBuilder sbSum = new StringBuilder();
        sbSum.append(formatUtil.pad9(parse.decimal2String(ctl113522, 2, 0), 2));
        sbSum.append(formatUtil.padX(code113522, 6));
        sbSum.append(formatUtil.pad9(parse.decimal2String(bdate113522, 6, 0), 6));
        sbSum.append(formatUtil.pad9(parse.decimal2String(edate113522, 6, 0), 6));
        sbSum.append(formatUtil.pad9(cntFormat.format(totcnt113522), 6));
        sbSum.append(formatUtil.pad9(decimalFormat.format(totamt113522), 13));
        sbSum.append(formatUtil.padX(" ", 81));
        fileFd113522Contents.add(formatUtil.padX(sbSum.toString(), 120));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeFile()");
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd113522Contents, CHARSET_UTF8);
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
                        CONVF_PATH_07Z1113522, // 來源檔案名稱(20碼長)
                        CONVF_PATH_07Z1113522, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV113522", // 檔案設定代號 ex:CONVF001
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
