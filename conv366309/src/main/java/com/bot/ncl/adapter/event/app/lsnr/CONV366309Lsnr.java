/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV366309;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
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
@Component("CONV366309Lsnr")
@Scope("prototype")
public class CONV366309Lsnr extends BatchListenerCase<CONV366309> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV366309 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_07Z4366309 = "07Z4366309";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String putfnTxtype;
    private String bcode366309;
    private String sitdate366309;
    private String barcode1A2_366309;
    private String barcode1B2_366309;
    private String barcode2_366309;
    private String barcode3A2_366309;
    private String barcode3B2_366309;
    private String sunit366309;
    private String runit366309;
    private String type366309;
    private String edate366309;
    private String accno366309;
    private String stat366309;
    private String batno366309;
    private int putfnLmtdate;
    private int putfnSitdate;
    private int wkTotcnt = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal amt366309 = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private List<String> fileFd366309Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV366309 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366309Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV366309 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366309Lsnr run()");

        init(event);

        chechFdPutfnDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV366309 event) {
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
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_07Z4366309;

        textFile.deleteFile(readFdPutfnPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFN"
                        + File.separator
                        + CONVF_PATH_07Z4366309; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfnDataExist() {
        // FD-PUTFN檔案存在，執行366309-RTN
        if (textFile.exists(readFdPutfnPath)) {
            // 寫檔FD-366309(RC=1)
            valuateRc1();
            writeRc1();
            readFdPutfnData();
            writeRc3();
            //            writeFile();
        }
    }

    private void valuateRc1() {
        sunit366309 = "004     ";
        runit366309 = "BLI     ";
        type366309 = "D08";
        edate366309 = processDate;
        stat366309 = "2";
        batno366309 = "0";
    }

    private void writeRc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.pad9("1", 1));
        sbRc1.append(formatUtil.padX(sunit366309, 8));
        sbRc1.append(formatUtil.padX(runit366309, 8));
        sbRc1.append(formatUtil.padX(type366309, 3));
        sbRc1.append(formatUtil.pad9(edate366309, 7));
        sbRc1.append(formatUtil.pad9(stat366309, 1));
        sbRc1.append(formatUtil.padX("", 91));
        sbRc1.append(formatUtil.padX(batno366309, 1));
        fileFd366309Contents.add(formatUtil.padX(sbRc1.toString(), 120));
    }

    private void readFdPutfnData() {
        // 循序讀取FD-PUTFN
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");
            if (putfnCtl == 11) {
                putfnRcptid = filePutfn.getRcptid();
                putfnCllbr = filePutfn.getCllbr();
                putfnLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getLmtdate())
                                        ? filePutfn.getLmtdate()
                                        : "0"));
                putfnUserdata = filePutfn.getUserdata();
                putfnSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getSitdate())
                                        ? filePutfn.getSitdate()
                                        : "0"));
                putfnTxtype = filePutfn.getTxtype();
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : "0"));

                wkTotcnt++;
                wkTotAmt = wkTotAmt.add(putfnAmt);

                valuateRc2();
                writeRc2();
            }
        }
    }

    private void valuateRc2() {
        sunit366309 = "004";
        bcode366309 = putfnCllbr;
        accno366309 = "00003031123059";
        amt366309 = putfnAmt;
        stat366309 = "0";
        sitdate366309 = parse.decimal2String(putfnSitdate, 7, 0);
        valuateBarcode();
        barcode1B2_366309 = "630";
        barcode2_366309 = putfnRcptid;
        barcode3B2_366309 = parse.decimal2String(putfnAmt, 9, 0);
    }

    private void valuateBarcode() {
        if (putfnTxtype.equals("F")) {
            barcode1A2_366309 = putfnUserdata.substring(20, 26);
            barcode3A2_366309 = putfnUserdata.substring(26, 32);
        } else {
            barcode1A2_366309 = parse.decimal2String(putfnLmtdate, 6, 0);
            barcode3A2_366309 = putfnUserdata.substring(0, 6);
        }
    }

    private void writeRc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.pad9("2", 1));
        sbRc2.append(formatUtil.padX(sunit366309, 3));
        sbRc2.append(formatUtil.pad9(bcode366309, 5));
        sbRc2.append(formatUtil.padX(runit366309, 8));
        sbRc2.append(formatUtil.padX(type366309, 3));
        sbRc2.append(formatUtil.pad9(edate366309, 7));
        sbRc2.append(formatUtil.padX(accno366309, 14));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(amt366309), 15));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.padX(sitdate366309, 7));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.pad9(stat366309, 2));
        sbRc2.append(formatUtil.pad9(barcode1A2_366309, 6));
        sbRc2.append(formatUtil.padX(barcode1B2_366309, 3));
        sbRc2.append(formatUtil.padX(barcode2_366309, 20));
        sbRc2.append(formatUtil.padX(barcode3A2_366309, 6));
        sbRc2.append(formatUtil.pad9(barcode3B2_366309, 9));
        sbRc2.append(formatUtil.padX("", 9));
        sbRc2.append(formatUtil.padX(batno366309, 1));
        fileFd366309Contents.add(formatUtil.padX(sbRc2.toString(), 150));
    }

    private void writeRc3() {
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.pad9("3", 1));
        sbRc3.append(formatUtil.padX(sunit366309, 8));
        sbRc3.append(formatUtil.padX(runit366309, 8));
        sbRc3.append(formatUtil.padX(type366309, 3));
        sbRc3.append(formatUtil.pad9(edate366309, 7));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(wkTotAmt), 15));
        sbRc3.append(formatUtil.pad9(cntFormat.format(wkTotcnt), 10));
        sbRc3.append(formatUtil.padX("", 68));
        sbRc3.append(formatUtil.padX(batno366309, 1));
        fileFd366309Contents.add(formatUtil.padX(sbRc3.toString(), 150));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfnPath);
            textFile.writeFileContent(readFdPutfnPath, fileFd366309Contents, CHARSET_UTF8);
            upload(readFdPutfnPath, "DATA", "PUTFN");
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
        if (textFile.exists(readFdPutfnPath)) {
            upload(readFdPutfnPath, "", "");
            forFsap();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_07Z4366309, // 來源檔案名稱(20碼長)
                        CONVF_PATH_07Z4366309, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV366309", // 檔案設定代號 ex:CONVF001
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
