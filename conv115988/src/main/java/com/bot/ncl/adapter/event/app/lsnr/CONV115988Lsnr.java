/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV115988;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV115988Lsnr")
@Scope("prototype")
public class CONV115988Lsnr extends BatchListenerCase<CONV115988> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private ReportUtil reportUtil;

    private CONV115988 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_17X4115988 = "17X4115988";
    private static final String CONVF_PATH_CL005 = "CL005";
    private static final String CONVF_PATH_003 = "003";
    private static final String CONVF_PATH_FCL005115988 = "FCL005115988";
    private static final String FILE_NAME_17X411598T = "17X411598T";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final String PATH_SEPARATOR = File.separator;
    private Map<String, String> textMap;
    private String checkFdPath;
    private String fileName;
    private String wflDate = "";
    private String wflPutFile = "";
    private String wflTeldate = "";
    private String wflBizId = "";
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String cldate;
    private String readFdPutfnPath;
    private String writeFd115988TotPath;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnTxtype;
    private int putfnDate;
    private int putfnTime;
    private int putfnSitdate;
    private int wkTotcnt = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal wkTotamt = ZERO;
    private List<String> fileFd115988Contents = new ArrayList<>();
    private List<String> fileFd115988TotContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV115988 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV115988 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr run()");

        init(event);

        checkFdPutfnExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV115988 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr init()");

        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        String wkTotdate = formatUtil.pad9(processDate, 8).substring(1, 8);
        cldate = wkFdate;

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
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_17X4115988;
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
                        + CONVF_PATH_17X4115988; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }

        //     COPY   DATA/GN/DWL/CL005/003/#DATE7/FCL005115988 AS
        //                  00068986
        //            DATA/CL/BH/PUTFN/#CLDATE/17X411598T;
        //                  00068987
        // 直接改存PUTFN/CLDATE/17X411598T
        writeFd115988TotPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + FILE_NAME_17X411598T;
    }

    private void checkFdPutfnExist() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr checkFdPutfnExist()");
        // 若FD-PUTFN檔案存在，執行115988-RTN，寫FD-115988、FD-115988-TOT(彙計總表之文字檔)
        // 若FD-PUTFN檔案不存在，執行115988-SUM-RTN，寫FD-115988-TOT(彙計總表之文字檔)
        if (textFile.exists(readFdPutfnPath)) {
            writeFd115988Rc1();
            readFdPutfnData();
            writeFd115988Rc3();
            writeFd115988Tot();
            //            writeFile();
            writeFileTot();
        } else {
            wkTotcnt = 0;
            wkTotamt = ZERO;
            writeFd115988Tot();
            writeFileTot();
        }
    }

    private void readFdPutfnData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr readFdPutfnData()");
        // 循序讀取FD-PUTFN
        // 搬PUTFN-REC...到 115988-REC
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");
            putfnRcptid = filePutfn.getRcptid();

            if (!putfnRcptid.startsWith("NO DATA", 1) && (putfnCtl == 11 || putfnCtl == 21)) {
                putfnDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getEntdy()) ? filePutfn.getEntdy() : "0");
                putfnTime =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getTime()) ? filePutfn.getTime() : "0");
                putfnCllbr = filePutfn.getCllbr();
                putfnSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getSitdate())
                                        ? filePutfn.getSitdate()
                                        : "0"));
                putfnTxtype = filePutfn.getTxtype();
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : "0"));

                writeFd115988Rc2();

                // 累計筆數、金額
                wkTotcnt++;
                wkTotamt = wkTotamt.add(putfnAmt);
            }
        }
    }

    private void writeFd115988Rc1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFd115988Rc1()");
        // 寫檔FD-115988(FIRST RECORD)
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.pad9("1", 1));
        sbRc1.append(formatUtil.padX("004", 8));
        sbRc1.append(formatUtil.padX("BLI", 8));
        sbRc1.append(formatUtil.pad9("718", 3));
        sbRc1.append(formatUtil.pad9(processDate, 7));
        sbRc1.append(formatUtil.pad9("2", 1));
        sbRc1.append(formatUtil.padX("", 91));
        sbRc1.append(formatUtil.pad9("1", 1));
        fileFd115988Contents.add(formatUtil.padX(sbRc1.toString(), 160));
    }

    private void writeFd115988Rc2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFd115988Rc2()");
        // 寫檔FD-115988(DETAIL)
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.pad9("2", 1));
        sbRc2.append(formatUtil.padX("004", 3));
        sbRc2.append(formatUtil.pad9(putfnCllbr, 5));
        sbRc2.append(formatUtil.padX("BLI", 8));
        sbRc2.append(formatUtil.pad9("718", 3));
        sbRc2.append(formatUtil.pad9(parse.decimal2String(putfnDate, 8, 0).substring(1, 8), 7));
        sbRc2.append(formatUtil.padX("003031040747  ", 14));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(putfnAmt), 14));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.pad9(parse.decimal2String(putfnSitdate, 8, 0).substring(1, 8), 7));
        sbRc2.append(formatUtil.padX("", 1));
        sbRc2.append(formatUtil.pad9("00", 2));
        sbRc2.append(formatUtil.padX(putfnRcptid, 16));
        sbRc2.append(formatUtil.padX("", 36));
        sbRc2.append(formatUtil.pad9(parse.decimal2String(putfnTime, 6, 0), 6));
        sbRc2.append(formatUtil.padX("", 29));
        sbRc2.append(formatUtil.padX(putfnTxtype, 1));
        sbRc2.append(formatUtil.padX("1", 1));
        fileFd115988Contents.add(formatUtil.padX(sbRc2.toString(), 160));
    }

    private void writeFd115988Rc3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFd115988Rc3()");
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.pad9("3", 1));
        sbRc3.append(formatUtil.padX("004", 8));
        sbRc3.append(formatUtil.padX("BLI", 8));
        sbRc3.append(formatUtil.pad9("718", 3));
        sbRc3.append(formatUtil.pad9(processDate, 7));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(wkTotamt), 14));
        sbRc3.append(formatUtil.pad9(cntFormat.format(wkTotcnt), 10));
        sbRc3.append(formatUtil.padX("", 68));
        sbRc3.append(formatUtil.padX("1", 1));
        fileFd115988Contents.add(formatUtil.padX(sbRc3.toString(), 160));
    }

    private void writeFd115988Tot() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFd115988Tot()");

        fileFd115988TotContents.add("");
        fileFd115988TotContents.add("");
        fileFd115988TotContents.add("");

        StringBuilder sbTot2 = new StringBuilder();
        sbTot2.append(formatUtil.padX("", 12));
        sbTot2.append(formatUtil.padX(" 製表日期 : ", 12));
        sbTot2.append(
                formatUtil.pad9(
                        processDate.substring(0, 3)
                                + "/"
                                + processDate.substring(3, 5)
                                + "/"
                                + processDate.substring(5, 7),
                        9));
        fileFd115988TotContents.add(formatUtil.padX(sbTot2.toString(), 100));

        fileFd115988TotContents.add("");

        StringBuilder sbTot3 = new StringBuilder();
        sbTot3.append(formatUtil.padX("", 12));
        sbTot3.append(formatUtil.padX(" 臺　　灣　　銀　　行 ", 22));
        fileFd115988TotContents.add(formatUtil.padX(sbTot3.toString(), 100));

        fileFd115988TotContents.add("");
        StringBuilder sbTot4 = new StringBuilder();
        sbTot4.append(formatUtil.padX("", 12));
        sbTot4.append(formatUtil.padX(" 勞工職業災害保險費 ( 特別加保 ) 彙計總表－非臨櫃繳款（Ｋ） ", 60));
        fileFd115988TotContents.add(formatUtil.padX(sbTot4.toString(), 100));

        fileFd115988TotContents.add("");
        StringBuilder sbTot5 = new StringBuilder();
        sbTot5.append(formatUtil.padX("", 12));
        sbTot5.append(formatUtil.padX(" 入　帳　日　期 : ", 18));
        sbTot5.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + "/"
                                + processDate.substring(3, 5)
                                + "/"
                                + processDate.substring(5, 7),
                        9));
        fileFd115988TotContents.add(formatUtil.padX(sbTot5.toString(), 100));

        fileFd115988TotContents.add("");
        StringBuilder sbTot6 = new StringBuilder();
        sbTot6.append(formatUtil.padX("", 12));
        sbTot6.append(formatUtil.padX(" 總　　筆　　數 : ", 18));
        sbTot6.append(reportUtil.customFormat("" + wkTotcnt, "Z,ZZZ,ZZ9"));
        sbTot6.append(formatUtil.padX(" 筆 ", 4));
        fileFd115988TotContents.add(formatUtil.padX(sbTot6.toString(), 100));

        fileFd115988TotContents.add("");
        StringBuilder sbTot7 = new StringBuilder();
        sbTot7.append(formatUtil.padX("", 12));
        sbTot7.append(formatUtil.padX(" 總　　金　　額 : ", 18));
        sbTot7.append(reportUtil.customFormat("" + wkTotamt, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sbTot7.append(formatUtil.padX(" 元 ", 4));
        fileFd115988TotContents.add(formatUtil.padX(sbTot7.toString(), 100));

        fileFd115988TotContents.add("");
        fileFd115988TotContents.add("");
        fileFd115988TotContents.add("");
        StringBuilder sbTot8 = new StringBuilder();
        sbTot8.append(formatUtil.padX("", 12));
        sbTot8.append(formatUtil.padX(" 主辦單位 :", 11));
        sbTot8.append(formatUtil.padX("", 15));
        sbTot8.append(formatUtil.padX(" 經辦 :", 7));
        fileFd115988TotContents.add(formatUtil.padX(sbTot8.toString(), 100));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFile()");
        try {
            textFile.deleteFile(readFdPutfnPath);
            textFile.writeFileContent(readFdPutfnPath, fileFd115988Contents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void writeFileTot() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV115988Lsnr writeFileTot()");
        try {
            textFile.deleteFile(writeFd115988TotPath);
            textFile.writeFileContent(writeFd115988TotPath, fileFd115988TotContents, CHARSET_BIG5);
            upload(writeFd115988TotPath, "DATA", "");
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
            forFsap(CONVF_PATH_17X4115988, CONVF_PATH_17X4115988, "NCL_CONV115988");
        }
        if (textFile.exists(writeFd115988TotPath)) {
            upload(writeFd115988TotPath, "", "");
            forFsap(CONVF_PATH_FCL005115988, FILE_NAME_17X411598T, "NCL_FTP_ONLY");
        }
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

    private void forFsap(String filename, String tarFilename, String bizId) {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        filename, // 來源檔案名稱(20碼長)
                        tarFilename, // 目的檔案名稱(20碼長)
                        "1", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        bizId, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
