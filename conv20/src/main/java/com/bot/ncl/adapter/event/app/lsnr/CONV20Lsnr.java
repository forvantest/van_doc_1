/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV20;
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
@Component("CONV20Lsnr")
@Scope("prototype")
public class CONV20Lsnr extends BatchListenerCase<CONV20> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV20 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_17X1112014 = "17X1112014";
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
    private String code112014;
    private String rcptid112014;
    private String cllbr112014;
    private String userdata112014;
    private String txtype112014;
    private String readFdPutfPath;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int ctl112014;
    private int date112014;
    private int time112014;
    private int lmtdate112014;
    private int oldamt112014;
    private int sitdate112014;
    private int bdate112014;
    private int edate112014;
    private int wkTotCnt = 0;
    private int totCnt112014 = 0;
    private int putfBdate;
    private int putfEdate;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt112014 = ZERO;
    private BigDecimal totAmt112014 = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private List<String> fileFd112014Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV20 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV20Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV20 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV20Lsnr run()");

        init(event);

        chechFdPutfDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV20 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV20Lsnr init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = processDate.substring(1);

        // 設定檔名日期變數值
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17X1112014;
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
                        + CONVF_PATH_17X1112014; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfDataExist() {
        // 若FD-PUTF檔案存在，執行112014-RTN
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();

            // 搬相關資料到112014-REC...(LAST RECORD)
            valuateFd112014Sum();

            // 寫檔FD-112014(LAST RECORD)
            writeFd112014Sum();
        }
    }

    private void readFdPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV20Lsnr readFdPutfData()");
        // 循序讀取FD-PUTF
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL <> 11(明細)，跳到112014-NEXT，LOOP讀下一筆FD-PUTF
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

                // 搬PUTF-REC...到112014-REC...
                valuateFd112014Dtl();

                // 寫檔FD-112014(DETAIL)
                writeFd112014Dtl();

                // 累加筆數,金額
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
            } else {
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
            }
        }
    }

    private void valuateFd112014Dtl() {
        ctl112014 = putfCtl;
        code112014 = putfCode;
        rcptid112014 = putfRcptid;
        date112014 = putfDate;
        time112014 = putfTime;
        cllbr112014 = putfCllbr;
        lmtdate112014 = putfLmtdate;
        oldamt112014 = putfOldamt;
        userdata112014 = putfUserdata;
        sitdate112014 = putfSitdate;
        txtype112014 = putfTxtype;
        amt112014 = putfAmt;
    }

    private void writeFd112014Dtl() {
        StringBuilder sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(ctl112014, 2, 0), 2));
        sbFdDtl.append(formatUtil.padX(code112014, 6));
        sbFdDtl.append(formatUtil.padX(rcptid112014, 16));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(date112014, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(time112014, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(cllbr112014, 3, 0), 3));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(lmtdate112014, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(oldamt112014, 8, 0), 8));
        sbFdDtl.append(formatUtil.padX(userdata112014, 40));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(sitdate112014, 6, 0), 6));
        sbFdDtl.append(formatUtil.padX(txtype112014, 1));
        sbFdDtl.append(formatUtil.pad9(decimalFormat.format(amt112014), 11));
        sbFdDtl.append(formatUtil.padX("", 10));
        fileFd112014Contents.add(formatUtil.padX(sbFdDtl.toString(), 120));
    }

    private void valuateFd112014Sum() {
        ctl112014 = 12;
        code112014 = "112014";
        bdate112014 = putfBdate;
        edate112014 = putfEdate;
        totCnt112014 = wkTotCnt;
        totAmt112014 = wkTotAmt;
    }

    private void writeFd112014Sum() {
        StringBuilder sbFdSum = new StringBuilder();
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(ctl112014, 2, 0), 2));
        sbFdSum.append(formatUtil.padX(code112014, 6));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(bdate112014, 6, 0), 6));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(edate112014, 6, 0), 6));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(totCnt112014, 6, 0), 6));
        sbFdSum.append(formatUtil.pad9(decimalFormat.format(totAmt112014), 14));
        sbFdSum.append(formatUtil.pad9("", 80));
        fileFd112014Contents.add(formatUtil.padX(sbFdSum.toString(), 120));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV20Lsnr writeFile()");
        try {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd112014Contents, CHARSET_UTF8);
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
                        CONVF_PATH_17X1112014, // 來源檔案名稱(20碼長)
                        CONVF_PATH_17X1112014, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV20", // 檔案設定代號 ex:CONVF001
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
