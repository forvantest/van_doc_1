/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV196929;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("CONV196929Lsnr")
@Scope("prototype")
public class CONV196929Lsnr extends BatchListenerCase<CONV196929> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    private CONV196929 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_07Z1196929 = "07Z1196929";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String putfFiller;
    private String filler;
    private String putfNodata;
    private String filler1;
    private String code196929;
    private String rcptid196929;
    private String cllbr196929;
    private String userdata196929;
    private String txtype196929;
    private String readFdPutfPath;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int ctl196929;
    private int date196929;
    private int time196929;
    private int lmtdate196929;
    private int oldamt196929;
    private int sitdate196929;
    private int bdate196929;
    private int edate196929;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal putfTotCnt = ZERO;
    private BigDecimal putfTotAmt = ZERO;
    private BigDecimal amt196929 = ZERO;
    private BigDecimal totCnt196929 = ZERO;
    private BigDecimal totAmt196929 = ZERO;
    private List<String> fileFd196929Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV196929 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV196929Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV196929 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV196929Lsnr run()");

        init(event);

        chechFdPutfDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV196929 event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_07Z1196929;
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
                        + CONVF_PATH_07Z1196929; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfDataExist() {
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
        }
    }

    private void readFdPutfData() {
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            if (detail.length() < 120) {
                detail = formatUtil.padX(detail, 120);
            }

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "0");
            if (putfCtl == 11) {
                putfCode = detail.substring(2, 8);
                putfRcptid = detail.substring(8, 24);
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(detail.substring(24, 30))
                                        ? detail.substring(24, 30)
                                        : "0");
                putfTime =
                        parse.string2Integer(
                                parse.isNumeric(detail.substring(30, 36))
                                        ? detail.substring(30, 36)
                                        : "0");
                putfCllbr = detail.substring(36, 39);
                putfLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(39, 45))
                                        ? detail.substring(39, 45)
                                        : "0"));
                putfOldamt =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(45, 53))
                                        ? detail.substring(45, 53)
                                        : "0"));
                putfUserdata = detail.substring(53, 93);
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(93, 99))
                                        ? detail.substring(93, 99)
                                        : "0"));
                putfTxtype = detail.substring(99, 100);
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(100, 110))
                                        ? detail.substring(100, 110)
                                        : "0"));
                putfFiller = detail.substring(110, 120);
                valuateFd196929Dtl();
                writeFd196929Dtl();
            } else if (putfCtl == 12) {
                putfBdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(0, 6))
                                        ? detail.substring(0, 6)
                                        : "0"));
                putfEdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(6, 12))
                                        ? detail.substring(6, 12)
                                        : "0"));
                putfTotCnt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(12, 18))
                                        ? detail.substring(12, 18)
                                        : "0"));
                putfTotAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(18, 31))
                                        ? detail.substring(18, 31)
                                        : "0"));
                filler = detail.substring(31, 112);
                changeUserdata();
                valuateFd196929Sum();
                writeFd196929Sum();
            } else {
                putfNodata = detail.substring(0, 16);
                filler1 = detail.substring(16, 112);
                changeUserdata();
            }
        }
    }

    private void valuateFd196929Dtl() {
        ctl196929 = putfCtl;
        code196929 = putfCode;
        rcptid196929 = putfRcptid;
        date196929 = putfDate;
        time196929 = putfTime;
        cllbr196929 = putfCllbr;
        lmtdate196929 = putfLmtdate;
        oldamt196929 = putfOldamt;
        userdata196929 = putfUserdata;
        sitdate196929 = putfSitdate;
        txtype196929 = putfTxtype;
        amt196929 = putfAmt;
    }

    private void writeFd196929Dtl() {
        StringBuilder stringBuilderFdDtl = new StringBuilder();
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(ctl196929, 2, 0), 2));
        stringBuilderFdDtl.append(formatUtil.padX(code196929, 6));
        stringBuilderFdDtl.append(formatUtil.padX(rcptid196929, 16));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(date196929, 6, 0), 6));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(time196929, 6, 0), 6));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(cllbr196929, 3, 0), 3));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(lmtdate196929, 6, 0), 6));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(oldamt196929, 8, 0), 8));
        stringBuilderFdDtl.append(formatUtil.padX(userdata196929, 40));
        stringBuilderFdDtl.append(formatUtil.pad9(parse.decimal2String(sitdate196929, 6, 0), 6));
        stringBuilderFdDtl.append(formatUtil.padX(txtype196929, 1));
        stringBuilderFdDtl.append(formatUtil.pad9(decimalFormat.format(amt196929), 11));
        stringBuilderFdDtl.append(formatUtil.padX("", 10));
        fileFd196929Contents.add(formatUtil.padX(stringBuilderFdDtl.toString(), 120));
    }

    private void valuateFd196929Sum() {
        bdate196929 = putfBdate;
        edate196929 = putfEdate;
        totCnt196929 = putfTotCnt;
        totAmt196929 = putfTotAmt;
    }

    private void changeUserdata() {
        if (putfCtl == 11) {
            userdata196929 = "";
        }
    }

    private void writeFd196929Sum() {
        StringBuilder stringBuilderFdSum = new StringBuilder();
        stringBuilderFdSum.append(formatUtil.pad9("12", 2));
        stringBuilderFdSum.append(formatUtil.padX(putfCode, 6));
        stringBuilderFdSum.append(formatUtil.pad9(parse.decimal2String(bdate196929, 6, 0), 6));
        stringBuilderFdSum.append(formatUtil.pad9(parse.decimal2String(edate196929, 6, 0), 6));
        stringBuilderFdSum.append(formatUtil.pad9(parse.decimal2String(totCnt196929, 6, 0), 6));
        stringBuilderFdSum.append(formatUtil.pad9(decimalFormat.format(totAmt196929), 14));
        stringBuilderFdSum.append(formatUtil.padX("", 80));
        fileFd196929Contents.add(formatUtil.padX(stringBuilderFdSum.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd196929Contents, CHARSET_UTF8);
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
                        CONVF_PATH_07Z1196929, // 來源檔案名稱(20碼長)
                        CONVF_PATH_07Z1196929, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV196929", // 檔案設定代號 ex:CONVF001
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
