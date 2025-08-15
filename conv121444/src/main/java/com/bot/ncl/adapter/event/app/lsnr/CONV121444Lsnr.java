/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV121444;
import com.bot.ncl.util.FsapBatchUtil;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV121444Lsnr")
@Scope("prototype")
public class CONV121444Lsnr extends BatchListenerCase<CONV121444> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FileSumPUTFN fileSumPUTFN;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV121444 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_READFILE = "27Z4121444";
    private static final String CONVF_PATH_TCITY = "27Z412144A";
    private static final String CONVF_PATH_KCITY = "27Z412144E";
    private static final String CONVF_PATH_KCOUNTY = "27Z412144S";
    private static final String CONVF_PATH_NDISTRICT = "27Z412144H";
    private static final String CONVF_PATH_CDISTRICT = "27Z412144B";
    private static final String CONVF_PATH_SDISTRICT = "27Z412144D";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_CONV121444 = "NCL_CONV121444";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final Set<String> WK_N_DISTRICT_RCPTID =
            Set.of("012", "027", "022", "026", "068", "015", "018", "038", "039");
    private static final Set<String> WK_C_DISTRICT_RCPTID =
            Set.of("010", "029", "030", "032", "016", "031");
    private static final Set<String> WK_S_DISTRICT_RCPTID =
            Set.of("009", "014", "067", "028", "017", "023", "024");
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String writeTCityPath;
    private String writeKCityPath;
    private String writeKCountyPath;
    private String writeNDistrictPath;
    private String writeCDistrictPath;
    private String writeSDistrictPath;
    private String putfnCode;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String putfnTxtype;
    private int putfnDate;
    private int putfnTime;
    private int putfnLmtdate;
    private int putfnOldamt;
    private int putfnSitdate;
    private int putfnBdate;
    private int putfnEdate;

    // 彙總筆數、金額變數清0
    private int tCityTotcnt = 0;
    private int kCityTotcnt = 0;
    private int kCountyTotcnt = 0;
    private int nDistrictTotcnt = 0;
    private int cDistrictTotcnt = 0;
    private int sDistrictTotcnt = 0;
    private BigDecimal putfnAmt = ZERO;

    // 彙總筆數、金額變數清0
    private BigDecimal tCityTotamt = ZERO;
    private BigDecimal kCityTotamt = ZERO;
    private BigDecimal kCountyTotamt = ZERO;
    private BigDecimal nDistrictTotamt = ZERO;
    private BigDecimal cDistrictTotamt = ZERO;
    private BigDecimal sDistrictTotamt = ZERO;
    private List<String> fileTCityContents = new ArrayList<>();
    private List<String> fileKCityContents = new ArrayList<>();
    private List<String> fileKCountyContents = new ArrayList<>();
    private List<String> fileNDistrictContents = new ArrayList<>();
    private List<String> fileCDistrictContents = new ArrayList<>();
    private List<String> fileSDistrictContents = new ArrayList<>();
    private StringBuilder sbFdDtl = new StringBuilder();
    private StringBuilder sbFdSum = new StringBuilder();
    private StringBuilder sbNoDataDtl = new StringBuilder();
    private StringBuilder sbNoDataSum = new StringBuilder();

    @Override
    public void onApplicationEvent(CONV121444 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121444Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV121444 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121444Lsnr run()");

        init(event);

        checkDataExist();

        checkDataEmpty();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV121444 event) {
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        String tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);

        String wkFdate = processDate.substring(1);
        String wkCdate = processDate.substring(1);

        // 設定檔名變數值
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_READFILE;

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
                        + CONVF_PATH_READFILE; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }
        writeTCityPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_TCITY;
        writeKCityPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_KCITY;
        writeKCountyPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_KCOUNTY;
        writeNDistrictPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_NDISTRICT;
        writeCDistrictPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CDISTRICT;
        writeSDistrictPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_SDISTRICT;
    }

    private void checkDataExist() {
        // 若FD-PUTFN檔案存在，執行121444-RTN，寫FD-121444(明細資料 & 彙總資料)
        // 若FD-PUTFN檔案不存在，執行NODATA-RTN，寫FD-121444(NODATA)
        if (textFile.exists(readFdPutfnPath)) {
            readFdPutfnData();
        } else {
            writePutfnNoData();
        }
    }

    private void readFdPutfnData() {
        // 循序讀取FD-PUTFN
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);

        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);
            text2VoFormatter.format(detail, fileSumPUTFN);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");

            if (putfnCtl == 11) {
                putfnCode = filePutfn.getCode();
                putfnRcptid = filePutfn.getRcptid();
                putfnDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getEntdy()) ? filePutfn.getEntdy() : "0");
                putfnTime =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getTime()) ? filePutfn.getTime() : "0");
                putfnCllbr = filePutfn.getCllbr();
                putfnLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getLmtdate())
                                        ? filePutfn.getLmtdate()
                                        : "0"));
                putfnOldamt =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getOldamt())
                                        ? filePutfn.getOldamt()
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
                writeFd121444Dtl();
            } else {
                // PUTFN-CTL<>11 非明細資料
                //  執行PRT-TOT-RTN，寫檔FD-121444(彙總資料)
                //  LOOP讀下一筆FD-PUTFN
                putfnBdate =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTFN.getBdate())
                                        ? fileSumPUTFN.getBdate()
                                        : "0"));
                putfnEdate =
                        parse.string2Integer(
                                (parse.isNumeric(fileSumPUTFN.getEdate())
                                        ? fileSumPUTFN.getEdate()
                                        : "0"));
                writeFd121444Sum();
            }
        }
    }

    private void writeFd121444Dtl() {
        sbFdDtl.append(formatUtil.pad9("11", 2));
        sbFdDtl.append(formatUtil.padX(putfnCode, 6));
        sbFdDtl.append(formatUtil.padX(putfnRcptid, 26));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnDate, 8, 0), 8));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnTime, 6, 0), 6));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnCllbr, 3, 0), 3));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnLmtdate, 8, 0), 8));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnOldamt, 10, 0), 10));
        sbFdDtl.append(formatUtil.padX(putfnUserdata, 40));
        sbFdDtl.append(formatUtil.pad9(parse.decimal2String(putfnSitdate, 8, 0), 8));
        sbFdDtl.append(formatUtil.padX(putfnTxtype, 1));
        sbFdDtl.append(formatUtil.pad9(decimalFormat.format(putfnAmt), 13));
        sbFdDtl.append(formatUtil.padX(" ", 30));
        checkDistrictForDtl();
        sbFdDtl = new StringBuilder();
    }

    private void checkDistrictForDtl() {
        String wkRcptid = putfnRcptid.substring(4, 7);
        if (wkRcptid.equals("005")) {
            tCityTotcnt++;
            tCityTotamt = tCityTotamt.add(putfnAmt);
            fileTCityContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (wkRcptid.equals("011")) {
            kCityTotcnt++;
            kCityTotamt = kCityTotamt.add(putfnAmt);
            fileKCityContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (wkRcptid.equals("025")) {
            kCountyTotcnt++;
            kCountyTotamt = kCountyTotamt.add(putfnAmt);
            fileKCountyContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_N_DISTRICT_RCPTID.contains(wkRcptid)) {
            nDistrictTotcnt++;
            nDistrictTotamt = nDistrictTotamt.add(putfnAmt);
            fileNDistrictContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_C_DISTRICT_RCPTID.contains(wkRcptid)) {
            cDistrictTotcnt++;
            cDistrictTotamt = cDistrictTotamt.add(putfnAmt);
            fileCDistrictContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_S_DISTRICT_RCPTID.contains(wkRcptid)) {
            sDistrictTotcnt++;
            sDistrictTotamt = sDistrictTotamt.add(putfnAmt);
            fileSDistrictContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        }
    }

    private void writeFd121444Sum() {
        sbFdSum.append(formatUtil.pad9("12", 2));
        sbFdSum.append(formatUtil.padX("121444", 6));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(putfnBdate, 8, 0), 8));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(putfnEdate, 8, 0), 8));
        checkDistrictForSum();
        sbFdSum = new StringBuilder();
    }

    private void checkDistrictForSum() {
        String wkRcptid = putfnRcptid.substring(4, 7);
        if (wkRcptid.equals("005")) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(tCityTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(tCityTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileTCityContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (wkRcptid.equals("011")) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(kCityTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(kCityTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileKCityContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (wkRcptid.equals("025")) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(kCountyTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(kCountyTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileKCountyContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_N_DISTRICT_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(nDistrictTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(nDistrictTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileNDistrictContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_C_DISTRICT_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(cDistrictTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(cDistrictTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileCDistrictContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_S_DISTRICT_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(sDistrictTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(sDistrictTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileSDistrictContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        }
    }

    private void writePutfnNoData() {
        writeTCityNoData();
        writeKCityNoData();
        writeKCountyNoData();
        writeNDistrictNoData();
        writeCDistrictNoData();
        writeSDistrictNoData();
    }

    private void writeTCityNoData() {
        writeNoDataDtl();
        fileTCityContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileTCityContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeKCityNoData() {
        writeNoDataDtl();
        fileKCityContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileKCityContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeKCountyNoData() {
        writeNoDataDtl();
        fileKCountyContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileKCountyContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeNDistrictNoData() {
        writeNoDataDtl();
        fileNDistrictContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileNDistrictContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeCDistrictNoData() {
        writeNoDataDtl();
        fileCDistrictContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileCDistrictContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeSDistrictNoData() {
        writeNoDataDtl();
        fileSDistrictContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileSDistrictContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeNoDataDtl() {
        sbNoDataDtl.append(formatUtil.pad9("1", 2));
        sbNoDataDtl.append(formatUtil.padX("", 6));
        sbNoDataDtl.append(formatUtil.padX("", 26));
        sbNoDataDtl.append(formatUtil.pad9("0", 8));
        sbNoDataDtl.append(formatUtil.pad9("0", 6));
        sbNoDataDtl.append(formatUtil.pad9("0", 3));
        sbNoDataDtl.append(formatUtil.pad9("0", 8));
        sbNoDataDtl.append(formatUtil.pad9("0", 10));
        sbNoDataDtl.append(formatUtil.padX("", 40));
        sbNoDataDtl.append(formatUtil.pad9("0", 8));
        sbNoDataDtl.append(formatUtil.padX("", 1));
        sbNoDataDtl.append(formatUtil.pad9("0", 13));
        sbNoDataDtl.append(formatUtil.padX("", 30));
    }

    private void writeNoDataSum() {
        sbNoDataSum.append(formatUtil.pad9("2", 2));
        sbNoDataSum.append(formatUtil.padX("121444", 6));
        sbNoDataSum.append(formatUtil.pad9("0", 8));
        sbNoDataSum.append(formatUtil.pad9("0", 8));
        sbNoDataSum.append(formatUtil.pad9(cntFormat.format(0), 6));
        sbNoDataSum.append(formatUtil.pad9(decimalFormat.format(0), 14));
        sbNoDataSum.append(formatUtil.padX(" ", 117));
    }

    private void checkDataEmpty() {
        if (Objects.isNull(fileTCityContents) || fileTCityContents.isEmpty()) {
            writeTCityNoData();
        }
        if (Objects.isNull(fileKCityContents) || fileKCityContents.isEmpty()) {
            writeKCityNoData();
        }
        if (Objects.isNull(fileKCountyContents) || fileKCountyContents.isEmpty()) {
            writeKCountyNoData();
        }
        if (Objects.isNull(fileNDistrictContents) || fileNDistrictContents.isEmpty()) {
            writeNDistrictNoData();
        }
        if (Objects.isNull(fileCDistrictContents) || fileCDistrictContents.isEmpty()) {
            writeCDistrictNoData();
        }
        if (Objects.isNull(fileSDistrictContents) || fileSDistrictContents.isEmpty()) {
            writeSDistrictNoData();
        }
    }

    private void writeFile() {
        try {
            textFile.deleteFile(writeTCityPath);
            textFile.deleteFile(writeKCityPath);
            textFile.deleteFile(writeKCountyPath);
            textFile.deleteFile(writeNDistrictPath);
            textFile.deleteFile(writeCDistrictPath);
            textFile.deleteFile(writeSDistrictPath);
            textFile.writeFileContent(writeTCityPath, fileTCityContents, CHARSET_UTF8);
            textFile.writeFileContent(writeKCityPath, fileKCityContents, CHARSET_UTF8);
            textFile.writeFileContent(writeKCountyPath, fileKCountyContents, CHARSET_UTF8);
            textFile.writeFileContent(writeNDistrictPath, fileNDistrictContents, CHARSET_UTF8);
            textFile.writeFileContent(writeCDistrictPath, fileCDistrictContents, CHARSET_UTF8);
            textFile.writeFileContent(writeSDistrictPath, fileSDistrictContents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121444 upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + wkFsapYYYYMMDD + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(writeTCityPath)) {
            upload(writeTCityPath);
            forFsapTCity();
        }
        if (textFile.exists(writeKCityPath)) {
            upload(writeKCityPath);
            forFsapKCity();
        }
        if (textFile.exists(writeKCountyPath)) {
            upload(writeKCountyPath);
            forFsapKCounty();
        }
        if (textFile.exists(writeNDistrictPath)) {
            upload(writeNDistrictPath);
            forFsapNDistrict();
        }
        if (textFile.exists(writeCDistrictPath)) {
            upload(writeCDistrictPath);
            forFsapCDistrict();
        }
        if (textFile.exists(writeSDistrictPath)) {
            upload(writeSDistrictPath);
            forFsapSDistrict();
        }
    }

    private void forFsapTCity() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_TCITY, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapKCity() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_KCITY, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444 + "_E", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapKCounty() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_KCOUNTY, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444 + "_S", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapNDistrict() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_NDISTRICT, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444 + "_H", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapCDistrict() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_CDISTRICT, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444 + "_B", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapSDistrict() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_SDISTRICT, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121444 + "_D", // 檔案設定代號 ex:CONVF001
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
