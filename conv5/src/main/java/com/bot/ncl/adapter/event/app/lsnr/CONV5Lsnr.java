/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV5;
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
@Component("CONV5Lsnr")
@Scope("prototype")
public class CONV5Lsnr extends BatchListenerCase<CONV5> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FilePUTF filePutf;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV5 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String CONVF_PATH_02C1330011 = "02C1330011"; // 讀檔檔名 = wkConvfile
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String readFdPutfPath;
    private String PATH_SEPARATOR = File.separator;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String ctl33xxxx;
    private String code33xxxx;
    private String rcptid33xxxx;
    private String date33xxxx;
    private String time33xxxx;
    private String cllbr33xxxx;
    private String lmtdate33xxxx;
    private String oldamt33xxxx;
    private String userdata33xxxx;
    private String sitdate33xxxx;
    private String txtype33xxxx;
    private String bdate33xxxx;
    private String edate33xxxx;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int wkTotCnt = 0;
    private int totCnt33xxxx = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal amt33xxxx = ZERO;
    private BigDecimal totAmt33xxxx = ZERO;
    private List<String> fileFd33xxxxContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV5 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV5Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV5 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV5Lsnr run()");

        init(event);

        chechFdPutfDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV5 event) {
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);

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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_02C1330011;
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
                        + CONVF_PATH_02C1330011; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfDataExist() {
        // FD-PUTF檔案存在，執行33XXXX-RTN
        if (textFile.exists(readFdPutfPath)) {
            readPutfData();
            valuateFd33xxxxSum();
            writeFd33xxxxSum();
            //            writeFile();
        }
    }

    private void readPutfData() {
        // 循序讀取FD-PUTF，直到檔尾，跳到33XXXX-CLOSE
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutf);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getCtl()) ? filePutf.getCtl() : "0");

            // IF PUTF-CTL NOT = 11,跳下一筆
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

                // 搬相關資料到33XXXX-REC
                valuateFd33xxxxDtl();

                // 寫檔FD-33XXXX
                writeFd33xxxxDtl();

                // 累加筆數金額
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
            }
        }
    }

    private void valuateFd33xxxxDtl() {
        ctl33xxxx = parse.decimal2String(putfCtl, 2, 0);
        code33xxxx = putfCode;
        rcptid33xxxx = putfRcptid;
        date33xxxx = parse.decimal2String(putfDate, 6, 0);
        time33xxxx = parse.decimal2String(putfTime, 6, 0);
        cllbr33xxxx = putfCllbr;
        lmtdate33xxxx = parse.decimal2String(putfLmtdate, 6, 0);
        oldamt33xxxx = parse.decimal2String(putfOldamt, 8, 0);
        userdata33xxxx = putfUserdata;
        sitdate33xxxx = parse.decimal2String(putfSitdate, 6, 0);
        txtype33xxxx = putfTxtype;
        amt33xxxx = putfAmt;
    }

    private void writeFd33xxxxDtl() {
        StringBuilder sbDtl = new StringBuilder();
        sbDtl.append(formatUtil.pad9(ctl33xxxx, 2));
        sbDtl.append(formatUtil.padX(code33xxxx, 6));
        sbDtl.append(formatUtil.padX(rcptid33xxxx, 16));
        sbDtl.append(formatUtil.pad9(date33xxxx, 6));
        sbDtl.append(formatUtil.pad9(time33xxxx, 6));
        sbDtl.append(formatUtil.pad9(cllbr33xxxx, 3));
        sbDtl.append(formatUtil.pad9(lmtdate33xxxx, 6));
        sbDtl.append(formatUtil.pad9(oldamt33xxxx, 8));
        sbDtl.append(formatUtil.padX(userdata33xxxx, 40));
        sbDtl.append(formatUtil.pad9(sitdate33xxxx, 6));
        sbDtl.append(formatUtil.padX(txtype33xxxx, 1));
        sbDtl.append(formatUtil.pad9(decimalFormat.format(amt33xxxx), 11));
        sbDtl.append(formatUtil.padX("", 10));
        fileFd33xxxxContents.add(formatUtil.padX(sbDtl.toString(), 150));
    }

    private void valuateFd33xxxxSum() {
        // 022440     MOVE      WK-YYMMDD         TO      33XXXX-BDATE,
        // 022460                                         33XXXX-EDATE.
        bdate33xxxx = processDate.substring(1);
        edate33xxxx = processDate.substring(1);
        totCnt33xxxx = wkTotCnt;
        totAmt33xxxx = wkTotAmt;
        ctl33xxxx = "12";
        code33xxxx = "330011";
    }

    private void writeFd33xxxxSum() {
        StringBuilder sbSum = new StringBuilder();
        sbSum.append(formatUtil.pad9(ctl33xxxx, 2));
        sbSum.append(formatUtil.padX(code33xxxx, 6));
        sbSum.append(formatUtil.pad9(bdate33xxxx, 6));
        sbSum.append(formatUtil.pad9(edate33xxxx, 6));
        sbSum.append(formatUtil.pad9(cntFormat.format(totCnt33xxxx), 6));
        sbSum.append(formatUtil.pad9(decimalFormat.format(totAmt33xxxx), 14));
        sbSum.append(formatUtil.padX("", 80));
        fileFd33xxxxContents.add(formatUtil.padX(sbSum.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd33xxxxContents, CHARSET_UTF8);
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
                        CONVF_PATH_02C1330011, // 來源檔案名稱(20碼長)
                        CONVF_PATH_02C1330011, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV5", // 檔案設定代號 ex:CONVF001
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
