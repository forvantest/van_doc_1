/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV36C179;
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
@Component("CONV36C179Lsnr")
@Scope("prototype")
public class CONV36C179Lsnr extends BatchListenerCase<CONV36C179> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV36C179 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_17X436C179 = "17X436C179";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String putfnCode;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String barCode2_711;
    private String ud711;
    private String ctl711;
    private String supmktno711;
    private String ofsino711;
    private String tno711;
    private String ttype711;
    private String yyyymmdd711;
    private String bhno1_711;
    private String bhno2_711;
    private String filler711;
    private String clflag711;
    private String date711;
    private String sitdate711;
    private String lmtdate711;
    private String smcode711;
    private int putfnLmtdate;
    private int putfnSitdate;
    private int wkYYYYMMDD;
    private int wkSubCnt = 0;
    private int totCnt711 = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal wkSubAmt = ZERO;
    private BigDecimal amt711 = ZERO;
    private BigDecimal totAmt711 = ZERO;
    private List<String> fileFd36c179Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV36C179 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV36C179Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV36C179 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV36C179Lsnr run()");

        init(event);

        chechFdPutfnDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV36C179 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV36C179Lsnr init()");
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
        wkYYYYMMDD = parse.string2Integer(processDate) + 19110000;

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
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17X436C179;
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
                        + CONVF_PATH_PUTFN
                        + File.separator
                        + CONVF_PATH_17X436C179; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }
    }

    private void chechFdPutfnDataExist() {
        // FD-PUTFN檔案存在，執行36C179-RTN
        if (textFile.exists(readFdPutfnPath)) {
            // 搬相關檔案到711-REC(CTL=1)
            valuateCtl1();

            // 寫檔(CTL=1)
            writeCtl1();
            readFdPutfnData();

            // 搬相關檔案到711-REC(CTL=3)
            valuateCtl3();
            // 寫檔(CTL=3)
            writeCtl3();
            //            writeFile();
        }
    }

    private void valuateCtl1() {
        ctl711 = "1";
        supmktno711 = "C17";
        ofsino711 = "004";
        tno711 = "000";
        ttype711 = "2";
        yyyymmdd711 = parse.decimal2String(wkYYYYMMDD, 8, 0);
        wkSubCnt = 0;
        wkSubAmt = ZERO;
    }

    private void writeCtl1() {
        StringBuilder sbCtl1 = new StringBuilder();
        sbCtl1.append(formatUtil.pad9(ctl711, 1));
        sbCtl1.append(formatUtil.padX(supmktno711, 8));
        sbCtl1.append(formatUtil.padX(ofsino711, 8));
        sbCtl1.append(formatUtil.pad9(tno711, 3));
        sbCtl1.append(formatUtil.padX(ttype711, 1));
        sbCtl1.append(formatUtil.pad9(yyyymmdd711, 8));
        sbCtl1.append(formatUtil.pad9("0", 1));
        sbCtl1.append(formatUtil.pad9(decimalFormat.format("0"), 6));
        sbCtl1.append(formatUtil.pad9(decimalFormat.format("0"), 6));
        sbCtl1.append(formatUtil.pad9(decimalFormat.format("0"), 6));
        sbCtl1.append(formatUtil.padX("", 66));
        fileFd36c179Contents.add(formatUtil.padX(sbCtl1.toString(), 120));
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
            if (putfnCtl == 11 || putfnCtl == 21) {
                putfnCode = filePutfn.getCode();
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
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : "0"));

                wkSubCnt++;
                wkSubAmt = wkSubAmt.add(putfnAmt);

                // 般相關資料到711-REC(CTL=2)
                valuateCtl2();
                writeCtl2();
            }
        }
    }

    private void valuateCtl2() {
        ctl711 = "2";
        bhno1_711 = "004";
        bhno2_711 = putfnCllbr;
        filler711 = "0";
        clflag711 = "0";
        date711 = parse.decimal2String(wkYYYYMMDD, 8, 0);
        sitdate711 = parse.decimal2String(putfnSitdate + 19110000, 8, 0);
        lmtdate711 = parse.decimal2String(putfnLmtdate, 8, 0).substring(2, 8);
        smcode711 = "C17";
        amt711 = putfnAmt;
    }

    private void writeCtl2() {
        StringBuilder sbCtl2 = new StringBuilder();
        sbCtl2.append(formatUtil.pad9(ctl711, 1));
        sbCtl2.append(formatUtil.padX(supmktno711, 8));
        sbCtl2.append(formatUtil.padX(ofsino711, 8));
        sbCtl2.append(formatUtil.padX(bhno1_711, 3));
        sbCtl2.append(formatUtil.padX(bhno2_711, 3));
        sbCtl2.append(formatUtil.padX("", 2));
        sbCtl2.append(formatUtil.pad9(filler711, 17));
        sbCtl2.append(formatUtil.pad9(clflag711, 2));
        sbCtl2.append(formatUtil.pad9(parse.decimal2String(date711, 8, 0), 8));
        sbCtl2.append(formatUtil.pad9(parse.decimal2String(sitdate711, 8, 0), 8));
        sbCtl2.append(formatUtil.pad9(parse.decimal2String(lmtdate711, 6, 0), 6));
        sbCtl2.append(formatUtil.padX(smcode711, 3));
        valuateBarCodeUd();
        sbCtl2.append(formatUtil.pad9(barCode2_711, 16));
        sbCtl2.append(formatUtil.padX("", 4));
        // 因為使用的交易關係　銷帳編號所在的位置會不同
        if (putfnCode.equals("36C179")) {
            sbCtl2.append(formatUtil.padX(ud711, 6));
            sbCtl2.append(formatUtil.pad9(parse.decimal2String(amt711, 9, 0), 9));
        } else {
            sbCtl2.append(formatUtil.padX(ud711, 9));
            sbCtl2.append(formatUtil.pad9(parse.decimal2String(amt711, 6, 0), 6));
        }
        sbCtl2.append(formatUtil.padX("", 16));
        fileFd36c179Contents.add(formatUtil.padX(sbCtl2.toString(), 120));
    }

    private void valuateBarCodeUd() {
        if (putfnCode.equals("36C179")) {
            barCode2_711 = putfnRcptid;
            ud711 = putfnUserdata.substring(9, 15);
        } else {
            barCode2_711 = putfnUserdata.substring(0, 16);
            ud711 = "";
        }
    }

    private void valuateCtl3() {
        ctl711 = "3";
        totCnt711 = wkSubCnt;
        totAmt711 = wkSubAmt;
    }

    private void writeCtl3() {
        StringBuilder sbCtl3 = new StringBuilder();
        sbCtl3.append(formatUtil.pad9(ctl711, 1));
        sbCtl3.append(formatUtil.padX(supmktno711, 8));
        sbCtl3.append(formatUtil.padX(ofsino711, 8));
        sbCtl3.append(formatUtil.pad9("0", 3));
        sbCtl3.append(formatUtil.pad9(date711, 8));
        sbCtl3.append(formatUtil.pad9(decimalFormat.format(totCnt711), 15));
        sbCtl3.append(formatUtil.pad9(cntFormat.format(totAmt711), 10));
        sbCtl3.append(formatUtil.padX("", 66));
        fileFd36c179Contents.add(formatUtil.padX(sbCtl3.toString(), 120));
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfnPath);
            textFile.writeFileContent(readFdPutfnPath, fileFd36c179Contents, CHARSET_UTF8);
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
                        CONVF_PATH_17X436C179, // 來源檔案名稱(20碼長)
                        CONVF_PATH_17X436C179, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV36C179", // 檔案設定代號 ex:CONVF001
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
