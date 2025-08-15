/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV366ZA9;
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
@Component("CONV366ZA9Lsnr")
@Scope("prototype")
public class CONV366ZA9Lsnr extends BatchListenerCase<CONV366ZA9> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV366ZA9 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_22C4366ZA9 = "22C4366ZA9";
    private static final String CONVF_PATH_FCL005366ZA9 = "FCL005366ZA9";
    private static final String CONVF_PATH_CL005 = "CL005";
    private static final String CONVF_PATH_003 = "003";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String writeFd366za9TotPath;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String sunit366za9;
    private String runit366za9;
    private String type366za9;
    private String edate366za9;
    private String stat366za9;
    private String batno366za9;
    private String hbank366za9;
    private String bbank366za9;
    private String accno366za9;
    private String sitdate366za9;
    private String barcode1_366za9;
    private String barcode2_366za9;
    private String barcode3_366za9;
    private int putfnDate;
    private int putfnLmtdate;
    private int putfnSitdate;
    private int wkTotCnt = 0;
    private int okCnt366za9 = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal amt366za9 = ZERO;
    private BigDecimal okAmt366za9 = ZERO;
    private List<String> fileFd366za9Contents = new ArrayList<>();
    private List<String> fileFD366za9TotContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV366ZA9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366ZA9Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV366ZA9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366ZA9Lsnr run()");

        init(event);

        chechFdPutfnDataExist();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV366ZA9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366ZA9Lsnr init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、設定檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        String tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = processDate.substring(1);
        String wkCdate = processDate.substring(1);

        // 設定檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C4366ZA9;
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
                        + CONVF_PATH_22C4366ZA9; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }

        writeFd366za9TotPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CL005
                        + PATH_SEPARATOR
                        + CONVF_PATH_003
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_FCL005366ZA9;
    }

    private void chechFdPutfnDataExist() {
        // FD-PUTFN檔案存在，執行366ZA9-RTN
        if (textFile.exists(readFdPutfnPath)) {
            // 搬相關資料到366ZA9-REC (RC=1)
            valuateRc1();
            writeRc1();
            readFdPutfnData();

            // 搬相關資料到366ZA9-RE(RC=3)
            valuateRc3();

            // 寫檔FD-366ZA9
            writeRc3();
            writeFd366zaTot();
        }
    }

    private void valuateRc1() {
        sunit366za9 = "004";
        runit366za9 = "BLI";
        type366za9 = "108";
        edate366za9 = processDate;
        stat366za9 = "2";
        batno366za9 = "0";
    }

    private void writeRc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.pad9("1", 1));
        sbRc1.append(formatUtil.padX(sunit366za9, 8));
        sbRc1.append(formatUtil.padX(runit366za9, 8));
        sbRc1.append(formatUtil.pad9(type366za9, 3));
        sbRc1.append(formatUtil.pad9(edate366za9, 7));
        sbRc1.append(formatUtil.pad9(stat366za9, 1));
        sbRc1.append(formatUtil.padX("", 91));
        sbRc1.append(formatUtil.padX(batno366za9, 1));
        fileFd366za9Contents.add(formatUtil.padX(sbRc1.toString(), 120));
    }

    private void readFdPutfnData() {
        // 循序讀取FD-PUTFN，直到檔尾，跳到366ZA9-LAST
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");
            if (putfnCtl == 11 || putfnCtl == 21) {
                putfnRcptid = filePutfn.getRcptid();
                putfnDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getEntdy()) ? filePutfn.getEntdy() : "0");
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

                // 搬相關資料到366ZA9-RE(RC=2)
                valuateRc2();

                // 累加筆數金額
                wkTotAmt = wkTotAmt.add(putfnAmt);
                wkTotCnt++;

                // 寫檔FD-366ZA9
                writeRc2();
            }
        }
    }

    private void valuateRc2() {
        hbank366za9 = "004";
        bbank366za9 = putfnCllbr;
        edate366za9 = parse.decimal2String(putfnDate, 8, 0).substring(1);
        accno366za9 = "003031040747  ";
        amt366za9 = putfnAmt;
        sitdate366za9 = parse.decimal2String(putfnSitdate, 8, 0);
        stat366za9 = "00";
        barcode1_366za9 =
                parse.decimal2String(putfnLmtdate, 8, 0).substring(2, 8)
                        + putfnUserdata.substring(9, 12);
        barcode2_366za9 = putfnRcptid;
        barcode3_366za9 =
                putfnUserdata.substring(0, 6) + parse.decimal2String(putfnAmt, 12, 0).substring(3);
    }

    private void writeRc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.pad9("2", 1));
        sbRc2.append(formatUtil.padX(hbank366za9, 3));
        sbRc2.append(formatUtil.pad9(bbank366za9, 5));
        sbRc2.append(formatUtil.padX(runit366za9, 8));
        sbRc2.append(formatUtil.pad9(type366za9, 3));
        sbRc2.append(formatUtil.pad9(edate366za9, 7));
        sbRc2.append(formatUtil.padX(accno366za9, 14));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(amt366za9), 15));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.padX(sitdate366za9, 7));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.pad9(stat366za9, 2));
        sbRc2.append(formatUtil.padX(barcode1_366za9, 9));
        sbRc2.append(formatUtil.padX(barcode2_366za9, 20));
        sbRc2.append(formatUtil.padX(barcode3_366za9, 15));
        sbRc2.append(formatUtil.padX("", 9));
        sbRc2.append(formatUtil.padX(batno366za9, 1));
        fileFd366za9Contents.add(formatUtil.padX(sbRc2.toString(), 120));
    }

    private void valuateRc3() {
        okAmt366za9 = wkTotAmt;
        okCnt366za9 = wkTotCnt;
    }

    private void writeRc3() {
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.pad9("3", 1));
        sbRc3.append(formatUtil.padX(sunit366za9, 8));
        sbRc3.append(formatUtil.padX(runit366za9, 3));
        sbRc3.append(formatUtil.pad9(type366za9, 3));
        sbRc3.append(formatUtil.pad9(edate366za9, 7));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(okAmt366za9), 15));
        sbRc3.append(formatUtil.pad9(cntFormat.format(okCnt366za9), 10));
        sbRc3.append(formatUtil.padX("", 67));
        sbRc3.append(formatUtil.padX(batno366za9, 1));
        fileFd366za9Contents.add(formatUtil.padX(sbRc3.toString(), 120));
    }

    private void writeFd366zaTot() {
        StringBuilder sbFdTot1 = new StringBuilder();
        sbFdTot1.append(formatUtil.padX("", 3));
        sbFdTot1.append(formatUtil.padX(" 製表日期 : ", 12));
        sbFdTot1.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + PATH_SEPARATOR
                                + processDate.substring(3, 5)
                                + PATH_SEPARATOR
                                + processDate.substring(5, 7),
                        9));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot1.toString(), 50));

        StringBuilder sbFdTot2 = new StringBuilder();
        sbFdTot2.append(formatUtil.padX("", 12));
        sbFdTot2.append(formatUtil.padX(" 臺　　灣　　銀　　行 ", 22));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot2.toString(), 50));

        StringBuilder sbFdTot3 = new StringBuilder();
        sbFdTot3.append(formatUtil.padX("", 12));
        sbFdTot3.append(formatUtil.padX(" 勞　　保　　費　　彙　　計　　總　　表 ", 40));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot3.toString(), 100));

        StringBuilder sbFdTot4 = new StringBuilder();
        sbFdTot4.append(formatUtil.padX("", 4));
        sbFdTot4.append(formatUtil.padX(" 入　帳　日　期 : ", 18));
        sbFdTot4.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + PATH_SEPARATOR
                                + processDate.substring(3, 5)
                                + PATH_SEPARATOR
                                + processDate.substring(5, 7),
                        9));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot4.toString(), 50));

        StringBuilder sbFdTot5 = new StringBuilder();
        sbFdTot5.append(formatUtil.padX("", 7));
        sbFdTot5.append(formatUtil.padX(" 總　　筆　　數 : ", 18));
        sbFdTot5.append(formatUtil.padX(cntFormat.format(wkTotCnt), 9));
        sbFdTot5.append(formatUtil.padX(" 筆 ", 4));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot5.toString(), 50));

        StringBuilder sbFdTot6 = new StringBuilder();
        sbFdTot6.append(formatUtil.padX("", 7));
        sbFdTot6.append(formatUtil.padX(" 總　　金　　額 : ", 18));
        sbFdTot6.append(formatUtil.padX(decimalFormat.format(wkTotAmt), 15));
        sbFdTot6.append(formatUtil.padX(" 元 ", 4));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot6.toString(), 50));

        StringBuilder sbFdTot7 = new StringBuilder();
        sbFdTot7.append(formatUtil.padX("", 3));
        sbFdTot7.append(formatUtil.padX(" 主辦單位 :", 11));
        sbFdTot7.append(formatUtil.padX("", 15));
        sbFdTot7.append(formatUtil.padX(" 經辦 :", 7));
        fileFD366za9TotContents.add(formatUtil.padX(sbFdTot7.toString(), 50));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366ZA9Lsnr writeFile()");
        try {
            textFile.deleteFile(readFdPutfnPath);
            textFile.deleteFile(writeFd366za9TotPath);
            textFile.writeFileContent(readFdPutfnPath, fileFd366za9Contents, CHARSET_UTF8);
            textFile.writeFileContent(writeFd366za9TotPath, fileFD366za9TotContents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366ZA9 upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + wkFsapYYYYMMDD + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(readFdPutfnPath)) {
            upload(readFdPutfnPath);
            forFsap();
            forFsapTot();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C4366ZA9, // 來源檔案名稱(20碼長)
                        CONVF_PATH_22C4366ZA9, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV366ZA9", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }
    private void forFsapTot() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C4366ZA9, // 來源檔案名稱(20碼長)
                        CONVF_PATH_FCL005366ZA9, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV366ZA9_TOT", // 檔案設定代號 ex:CONVF001
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
