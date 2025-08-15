/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV36F209;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileNoDataPUTFN;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.fileVo.FileSumPUTFN;
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
@Component("CONV36F209Lsnr")
@Scope("prototype")
public class CONV36F209Lsnr extends BatchListenerCase<CONV36F209> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FileSumPUTFN fileSumPUTFN;
    @Autowired private FileNoDataPUTFN fileNoDataPUTFN;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV36F209 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_02C436F209 = "02C436F209";
    private static final String CONVF_PATH_CL004 = "CL004";
    private static final String CONVF_PATH_005 = "005";
    private static final String CONVF_PATH_FCL00436F209 = "FCL00436F209";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String writeNoDataPath;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String fcsCtl;
    private String fcsTtype;
    private String fcsYYYYMMDD;
    private String fcsExstep;
    private String fcsBankno;
    private String fcsTeclno;
    private String fcsFtype;
    private String fcsCllbr;
    private String wkCllbr;
    private String fcsCltypen01;
    private String fcsCltypen02;
    private String fcsSerino;
    private String fcsTxtype;
    private String fcsFortype;
    private String fcsDatasource;
    private String fcsActno;
    private String fcsUserdata;
    private String fcsBarcode1;
    private String fcsBarcode2;
    private String fcsBarcode3;
    private String fcsSitdate;
    private int putfnSitdate;
    private int wkCnt = 0;
    private int fcsTotCnt = 0;
    private int putfnTotCnt = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal putfnTotAmt = ZERO;
    private BigDecimal fcsTotAmt = ZERO;
    private BigDecimal fcsAmt = ZERO;
    private List<String> fileFd36f209Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV36F209 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV36F209Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV36F209 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV36F209Lsnr run()");

        init(event);

        chechFdPutfnDataExist();

        checkPath();

        batchResponse();
    }

    private void init(CONV36F209 event) {
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
        String wkCdate = processDate.substring(1);

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
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_02C436F209;
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
                        + CONVF_PATH_02C436F209; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }

        writeNoDataPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CL004
                        + PATH_SEPARATOR
                        + CONVF_PATH_005
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_FCL00436F209;
    }

    private void chechFdPutfnDataExist() {
        // FD-PUTFN檔案存在，執行36F209-RTN,否則執行EMPTY-RTN
        if (textFile.exists(readFdPutfnPath)) {
            // 搬相關資料到FCS-REC(CTL=1)
            valuateCtl1();

            // 寫檔FD-FCS(CTL=1)
            writeCtl1();

            // 循序讀取FD-PUTFN
            readFdPutfnData();
            //            writeFile();
        } else {
            // 當日無交易亦須提供媒體（內含首筆即尾筆）並直接指定分行下載路徑
            // 搬相關資料到FCS-REC(CTL=1)
            valuateCtl1();

            // 寫檔FD-FCS(CTL=1)
            writeCtl1();

            // 搬相關資料到FCS-REC(CTL=3)
            valuateCtl3();

            // 寫檔FD-FCS
            writeCtl3();
            //            writeNodata();
        }
    }

    private void readFdPutfnData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readFdPutfnData run()");
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);

        if (Objects.isNull(readFdPutfnPath) || readFdPutfnPath.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putfnFileContent is null");
            return;
        }

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);
            text2VoFormatter.format(detail, fileSumPUTFN);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");

            // IF PUTFN-CTL = 11 OR = 21(明細),寫檔FD-FCS(CTL=1)
            // IF PUTFN-CTL = 12 OR = 22(彙總),寫檔FD-FCS(CTL=3)
            if (putfnCtl == 11 || putfnCtl == 21) {
                putfnRcptid = filePutfn.getRcptid();
                putfnCllbr = filePutfn.getCllbr();
                putfnUserdata = filePutfn.getUserdata();
                putfnSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getSitdate())
                                        ? filePutfn.getSitdate()
                                        : "0"));
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : "0"));

                wkCnt++;

                // 搬相關資料到FCS-REC(CTL=2)
                valuateCtl2();

                // 寫檔FD-FCS
                writeCtl2();
            } else if (putfnCtl == 12 || putfnCtl == 22) {
                putfnTotCnt =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTFN.getTotcnt())
                                        ? fileSumPUTFN.getTotcnt()
                                        : "0"));
                putfnTotAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(fileSumPUTFN.getTotamt())
                                        ? fileSumPUTFN.getTotamt()
                                        : "0"));

                // 搬相關資料到FCS-REC(CTL=3)
                valuateCtl3();

                // 寫檔FD-FCS
                writeCtl3();
            }
        }
    }

    private void valuateCtl1() {
        fcsCtl = "1";
        fcsTtype = "P";
        fcsYYYYMMDD = processDate;
        fcsExstep = "B";
        fcsBankno = "0040059";
        fcsTeclno = "9990250";
        fcsFtype = "FCS01";
        wkCnt = 0;
    }

    private void writeCtl1() {
        StringBuilder sbCtl1 = new StringBuilder();
        sbCtl1.append(formatUtil.pad9(fcsCtl, 1));
        sbCtl1.append(formatUtil.padX(fcsTtype, 1));
        sbCtl1.append(formatUtil.pad9(fcsYYYYMMDD, 8));
        sbCtl1.append(formatUtil.padX(fcsExstep, 1));
        sbCtl1.append(formatUtil.padX(fcsBankno, 7));
        sbCtl1.append(formatUtil.padX(fcsTeclno, 7));
        sbCtl1.append(formatUtil.padX(fcsFtype, 5));
        sbCtl1.append(formatUtil.padX("", 150));
        fileFd36f209Contents.add(formatUtil.padX(sbCtl1.toString(), 180));
    }

    private void valuateCtl2() {
        fcsCtl = "2";
        checkCllbr();
        fcsCllbr = wkCllbr;
        fcsCltypen01 = putfnUserdata.substring(6, 9);
        fcsCltypen02 = "";
        fcsSerino = parse.decimal2String(wkCnt, 8, 0);
        fcsAmt = putfnAmt;
        fcsSitdate = parse.decimal2String(putfnSitdate, 8, 0);
        fcsTxtype = "1";
        fcsFortype = "560";
        fcsDatasource = "1";
        fcsActno = "0000000000000000";
        fcsUserdata = putfnUserdata;
        // 014200       MOVE    PUTFN-USERDATA     TO      WK-USERDATE   .
        // WK-USERDATE.
        // 005400     03 WK-BARCODE1                 PIC X(09).
        // 005500     03 WK-BARCODE3                 PIC X(15).
        // 005600     03 FILLER                      PIC X(16).
        fcsBarcode1 = putfnUserdata.substring(0, 9);
        fcsBarcode3 = putfnUserdata.substring(9, 24);
        fcsBarcode2 = putfnRcptid.substring(0, 16);
    }

    private void writeCtl2() {
        StringBuilder sbCtl2 = new StringBuilder();
        sbCtl2.append(formatUtil.pad9(fcsCtl, 1));
        sbCtl2.append(formatUtil.padX(fcsCllbr, 7));
        sbCtl2.append(formatUtil.padX(fcsCltypen01, 3));
        sbCtl2.append(formatUtil.padX(fcsCltypen02, 3));
        sbCtl2.append(formatUtil.padX(cntFormat.format(fcsSerino), 8));
        sbCtl2.append(formatUtil.pad9(decimalFormat.format(fcsAmt), 11));
        sbCtl2.append(formatUtil.pad9(fcsSitdate, 8));
        sbCtl2.append(formatUtil.padX(fcsTxtype, 1));
        sbCtl2.append(formatUtil.padX(fcsFortype, 3));
        sbCtl2.append(formatUtil.padX(fcsDatasource, 1));
        sbCtl2.append(formatUtil.padX(fcsActno, 16));
        sbCtl2.append(formatUtil.padX(fcsUserdata, 20));
        sbCtl2.append(formatUtil.padX(fcsBarcode1, 9));
        sbCtl2.append(formatUtil.padX(fcsBarcode2, 16));
        sbCtl2.append(formatUtil.padX(fcsBarcode3, 15));
        sbCtl2.append(formatUtil.padX("", 45));
        sbCtl2.append(formatUtil.padX("", 14));
        fileFd36f209Contents.add(formatUtil.padX(sbCtl2.toString(), 180));
    }

    private void valuateCtl3() {
        fcsCtl = "3";
        fcsTotCnt = putfnTotCnt;
        fcsTotAmt = putfnTotAmt;
    }

    private void writeCtl3() {
        StringBuilder sbCtl3 = new StringBuilder();
        sbCtl3.append(formatUtil.pad9(fcsCtl, 1));
        sbCtl3.append(formatUtil.pad9(cntFormat.format(fcsTotCnt), 8));
        sbCtl3.append(formatUtil.pad9(decimalFormat.format(fcsTotAmt), 13));
        sbCtl3.append(formatUtil.padX("", 159));
        fileFd36f209Contents.add(formatUtil.padX(sbCtl3.toString(), 180));
    }

    private void checkCllbr() {
        String wkBkno = "004";
        String wkBrno = putfnCllbr;
        wkCllbr = wkBkno + wkBrno;
        String wkKv = "7317310";
        int wkIdx;
        int wkTot = 0;
        for (wkIdx = 0; wkIdx < 7; wkIdx++) {
            int wkTem =
                    parse.string2Integer(wkCllbr.substring(wkIdx, wkIdx + 1))
                            * parse.string2Integer(wkKv.substring(wkIdx, wkIdx + 1));
            wkTot += wkTem;
        }
        String wkRtolbr = formatUtil.padX(parse.decimal2String(wkTot, 3, 0), 3).substring(2, 3);
        wkCllbr = wkBkno + wkBrno + wkRtolbr;
    }

    private void writeFile() {
        try {
            textFile.deleteFile(readFdPutfnPath);
            textFile.writeFileContent(readFdPutfnPath, fileFd36f209Contents, CHARSET_UTF8);
            upload(readFdPutfnPath, "DATA", "PUTFN");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void writeNodata() {
        try {
            textFile.deleteFile(writeNoDataPath);
            textFile.writeFileContent(writeNoDataPath, fileFd36f209Contents, CHARSET_UTF8);
            upload(writeNoDataPath, "DATA", "");
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
                        CONVF_PATH_02C436F209, // 來源檔案名稱(20碼長)
                        CONVF_PATH_02C436F209, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV36F209", // 檔案設定代號 ex:CONVF001
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
