/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV115463;
import com.bot.ncl.util.FsapBatchUtil;
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
@Component("CONV115463Lsnr")
@Scope("prototype")
public class CONV115463Lsnr extends BatchListenerCase<CONV115463> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV115463 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_17X1115463 = "17X1115463";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String tbsdy;
    private String readFdPutfPath;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String code115463;
    private String rcptid115463;
    private String cllbr115463;
    private String userdata115463;
    private String txtype115463;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int ctl115463;
    private int date115463;
    private int time115463;
    private int lmtdate115463;
    private int oldamt115463;
    private int sitdate115463;
    private int wkTotcnt = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal wkTotamt = ZERO;
    private BigDecimal amt115463 = ZERO;
    private List<String> fileFd115463Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV115463 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115463Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV115463 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115463Lsnr run()");

        init(event);

        checkPutfDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV115463 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 開啟批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 讀作業日期檔
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        tbsdy = labelMap.get("PROCESS_DATE");
        // 設定FD-PUTF檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17X1115463;
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
                        + "PUTF"
                        + File.separator
                        + CONVF_PATH_17X1115463; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void checkPutfDataExist() {
        // FD-PUTF檔案存在，執行115463-RTN
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
            writeFd115463Sum();
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "該路徑無PUTF檔案");
        }
    }

    private void readFdPutfData() {
        // 循序讀取FD-PUTF
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readFdPutfData()");
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // PUTF-CTL=11 明細資料
            // 執行DATA-COUNT-RTN，累計筆數、金額
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
                countData();
            }
            if (putfCtl != 13) {
                valuateFd115463();
                writeFd115463Dtl();
            }
        }
    }

    private void countData() {
        wkTotcnt++;
        wkTotamt = wkTotamt.add(putfAmt);
    }

    private void valuateFd115463() {
        ctl115463 = putfCtl;
        code115463 = putfCode;
        rcptid115463 = putfRcptid;
        date115463 = putfDate;
        time115463 = putfTime;
        cllbr115463 = putfCllbr;
        lmtdate115463 = putfLmtdate;
        oldamt115463 = putfOldamt;
        userdata115463 = putfUserdata;
        sitdate115463 = putfSitdate;
        txtype115463 = putfTxtype;
        amt115463 = putfAmt;
    }

    private void writeFd115463Dtl() {
        StringBuilder stringBuilderD1 = new StringBuilder();
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(ctl115463, 2, 0), 2));
        stringBuilderD1.append(formatUtil.padX(code115463, 6));
        stringBuilderD1.append(formatUtil.padX(rcptid115463, 16));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(date115463, 6, 0), 6));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(time115463, 6, 0), 6));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(cllbr115463, 3, 0), 3));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(lmtdate115463, 6, 0), 6));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(oldamt115463, 8, 0), 8));
        stringBuilderD1.append(formatUtil.padX(userdata115463, 40));
        stringBuilderD1.append(formatUtil.pad9(parse.decimal2String(sitdate115463, 6, 0), 6));
        stringBuilderD1.append(formatUtil.padX(txtype115463, 1));
        stringBuilderD1.append(formatUtil.pad9(decimalFormat.format(amt115463), 10));
        stringBuilderD1.append(formatUtil.padX("", 10));
        fileFd115463Contents.add(formatUtil.padX(stringBuilderD1.toString(), 120));
    }

    private void writeFd115463Sum() {
        StringBuilder stringBuilderS1 = new StringBuilder();
        stringBuilderS1.append(formatUtil.pad9("13", 2));
        stringBuilderS1.append(formatUtil.pad9(cntFormat.format(wkTotcnt), 6));
        stringBuilderS1.append(formatUtil.pad9(decimalFormat.format(wkTotamt), 13));
        stringBuilderS1.append(formatUtil.padX("", 99));
        fileFd115463Contents.add(formatUtil.padX(stringBuilderS1.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd115463Contents, CHARSET_UTF8);
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
                        CONVF_PATH_17X1115463, // 來源檔案名稱(20碼長)
                        CONVF_PATH_17X1115463, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV115463", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
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

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
