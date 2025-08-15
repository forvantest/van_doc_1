/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV121454;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.fileVo.FileSumPUTFN;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
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
@Component("CONV121454Lsnr")
@Scope("prototype")
public class CONV121454Lsnr extends BatchListenerCase<CONV121454> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FileSumPUTFN fileSumPUTFN;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV121454 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_SPUTF = "SPUTF";
    private static final String CONVF_PATH_READFILE = "27X3121454";
    private static final String CONVF_PATH_TPC = "27X3121454_TPC";
    private static final String CONVF_PATH_SCH = "27X3121454_SCH";
    private static final String CONVF_PATH_TXG = "27X3121454_TXG";
    private static final String CONVF_PATH_CYI = "27X3121454_CYI";
    private static final String CONVF_PATH_KHC = "27X3121454_KHC";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_CONV121454 = "NCL_CONV121454";
    private static final String STRING_SCH = "_SCH";
    private static final String STRING_TXG = "_TXG";
    private static final String STRING_CYI = "_CYI";
    private static final String STRING_KHC = "_KHC";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final Set<String> WK_TPC_RCPTID =
            Set.of("005", "027", "042", "012", "022", "018");
    private static final Set<String> WK_SCH_RCPTID = Set.of("068", "015", "026", "029");
    private static final Set<String> WK_TXG_RCPTID = Set.of("010", "032", "016", "030");
    private static final Set<String> WK_CYI_RCPTID = Set.of("067", "014", "028", "009", "031");
    private static final Set<String> WK_KHC_RCPTID =
            Set.of("011", "017", "025", "023", "088", "024");
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String readFdPutfnPath;
    private String writeTPCPath;
    private String writeSCHPath;
    private String writeTXGPath;
    private String writeCYIPath;
    private String writeKHCPath;
    private String putfnCode;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String putfnTxtype;
    private String sortPath;
    private int putfnDate;
    private int putfnTime;
    private int putfnLmtdate;
    private int putfnOldamt;
    private int putfnSitdate;
    private int putfnBdate;
    private int putfnEdate;
    private int tpcTotcnt = 0;
    private int schTotcnt = 0;
    private int txgTotcnt = 0;
    private int cyiTotcnt = 0;
    private int khcTotcnt = 0;

    // 彙總筆數、金額變數清0
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal tpcTotamt = ZERO;
    private BigDecimal schTotamt = ZERO;
    private BigDecimal txgTotamt = ZERO;
    private BigDecimal cyiTotamt = ZERO;
    private BigDecimal khcTotamt = ZERO;
    private List<String> fileTPCContents = new ArrayList<>();
    private List<String> fileSCHContents = new ArrayList<>();
    private List<String> fileTXGContents = new ArrayList<>();
    private List<String> fileCYIContents = new ArrayList<>();
    private List<String> fileKHCContents = new ArrayList<>();
    private StringBuilder sbFdDtl = new StringBuilder();
    private StringBuilder sbFdSum = new StringBuilder();
    private StringBuilder sbNoDataDtl = new StringBuilder();
    private StringBuilder sbNoDataSum = new StringBuilder();

    @Override
    public void onApplicationEvent(CONV121454 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV121454 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454Lsnr run()");

        init(event);

        checkSortPath();

        checkDataExist();

        checkDataEmpty();

        //        writeFile();

        checkPath();

        batchResponse();
    }

    private void checkSortPath() {
        if (textFile.exists(sortPath)) {
            sort121454();
        }
    }

    private void init(CONV121454 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454Lsnr init()");
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

        writeTPCPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_TPC;
        writeSCHPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_SCH;
        writeTXGPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_TXG;
        writeCYIPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CYI;
        writeKHCPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_KHC;

        sortPath = fileDir + CONVF_PATH_SPUTF;
    }

    private void sort121454() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454Lsnr sort121454()");
        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(16, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(13, 3, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET_UTF8);
    }

    private void checkDataExist() {
        if (textFile.exists(readFdPutfnPath)) {
            readFdPutfnData();
        } else {
            writePutfnNoData();
        }
    }

    private void readFdPutfnData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454Lsnr readFdPutfnData()");
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
                writeFd121454Dtl();
            } else {
                // PUTFN-CTL<>11 非明細資料
                //  執行PRT-TOT-RTN，寫檔FD-121454(彙總資料)
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
                writeFd12144Sum();
            }
        }
    }

    private void writeFd121454Dtl() {
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
        sbFdDtl.append(formatUtil.padX("", 30));
        checkDistrictForDtl();
        sbFdDtl = new StringBuilder();
    }

    private void checkDistrictForDtl() {
        String wkRcptid = putfnRcptid.substring(4, 7);
        if (WK_TPC_RCPTID.contains(wkRcptid)) {
            tpcTotcnt++;
            tpcTotamt = tpcTotamt.add(putfnAmt);
            fileTPCContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_SCH_RCPTID.contains(wkRcptid)) {
            schTotcnt++;
            schTotamt = schTotamt.add(putfnAmt);
            fileSCHContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_TXG_RCPTID.contains(wkRcptid)) {
            txgTotcnt++;
            txgTotamt = txgTotamt.add(putfnAmt);
            fileTXGContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_CYI_RCPTID.contains(wkRcptid)) {
            cyiTotcnt++;
            cyiTotamt = cyiTotamt.add(putfnAmt);
            fileCYIContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        } else if (WK_KHC_RCPTID.contains(wkRcptid)) {
            khcTotcnt++;
            khcTotamt = khcTotamt.add(putfnAmt);
            fileKHCContents.add(formatUtil.padX(sbFdDtl.toString(), 200));
        }
    }

    private void writeFd12144Sum() {
        sbFdSum.append(formatUtil.pad9("12", 2));
        sbFdSum.append(formatUtil.padX("121454", 6));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(putfnBdate, 8, 0), 8));
        sbFdSum.append(formatUtil.pad9(parse.decimal2String(putfnEdate, 8, 0), 8));
        checkDistrictForSum();
        sbFdSum = new StringBuilder();
    }

    private void checkDistrictForSum() {
        String wkRcptid = putfnRcptid.substring(4, 7);
        if (WK_TPC_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(tpcTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(tpcTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileTPCContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_SCH_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(schTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(schTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileSCHContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_TXG_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(txgTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(txgTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileTXGContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_CYI_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(cyiTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(cyiTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileCYIContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        } else if (WK_KHC_RCPTID.contains(wkRcptid)) {
            sbFdSum.append(formatUtil.pad9(cntFormat.format(khcTotcnt), 6));
            sbFdSum.append(formatUtil.pad9(decimalFormat.format(khcTotamt), 14));
            sbFdSum.append(formatUtil.padX("", 117));
            fileKHCContents.add(formatUtil.padX(sbFdSum.toString(), 200));
        }
    }

    private void writePutfnNoData() {
        writeTPCNoData();
        writeSCHNoData();
        writeTXGNoData();
        writeCYINoData();
        writeKHCNoData();
    }

    private void writeTPCNoData() {
        writeNoDataDtl();
        fileTPCContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileTPCContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeSCHNoData() {
        writeNoDataDtl();
        fileSCHContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileSCHContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeTXGNoData() {
        writeNoDataDtl();
        fileTXGContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileTXGContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeCYINoData() {
        writeNoDataDtl();
        fileCYIContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileCYIContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
        sbNoDataSum = new StringBuilder();
    }

    private void writeKHCNoData() {
        writeNoDataDtl();
        fileKHCContents.add(formatUtil.padX(sbNoDataDtl.toString(), 200));
        sbNoDataDtl = new StringBuilder();

        writeNoDataSum();
        fileKHCContents.add(formatUtil.padX(sbNoDataSum.toString(), 200));
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
        sbNoDataDtl.append(formatUtil.pad9(decimalFormat.format(0), 13));
        sbNoDataDtl.append(formatUtil.padX("", 30));
    }

    private void writeNoDataSum() {
        sbNoDataSum.append(formatUtil.pad9("2", 2));
        sbNoDataSum.append(formatUtil.padX("", 6));
        sbNoDataSum.append(formatUtil.pad9("0", 8));
        sbNoDataSum.append(formatUtil.pad9("0", 8));
        sbNoDataSum.append(formatUtil.pad9(cntFormat.format(0), 6));
        sbNoDataSum.append(formatUtil.pad9(decimalFormat.format(0), 14));
        sbNoDataSum.append(formatUtil.padX("", 117));
    }

    private void checkDataEmpty() {
        if (Objects.isNull(fileTPCContents) || fileTPCContents.isEmpty()) {
            writeTPCNoData();
        }
        if (Objects.isNull(fileSCHContents) || fileSCHContents.isEmpty()) {
            writeSCHNoData();
        }
        if (Objects.isNull(fileTXGContents) || fileTXGContents.isEmpty()) {
            writeTXGNoData();
        }
        if (Objects.isNull(fileCYIContents) || fileCYIContents.isEmpty()) {
            writeCYINoData();
        }
        if (Objects.isNull(fileKHCContents) || fileKHCContents.isEmpty()) {
            writeKHCNoData();
        }
    }

    private void writeFile() {
        try {
            textFile.deleteFile(writeTPCPath);
            textFile.deleteFile(writeSCHPath);
            textFile.deleteFile(writeTXGPath);
            textFile.deleteFile(writeCYIPath);
            textFile.deleteFile(writeKHCPath);
            textFile.writeFileContent(writeTPCPath, fileTPCContents, CHARSET_UTF8);
            textFile.writeFileContent(writeSCHPath, fileSCHContents, CHARSET_UTF8);
            textFile.writeFileContent(writeTXGPath, fileTXGContents, CHARSET_UTF8);
            textFile.writeFileContent(writeCYIPath, fileCYIContents, CHARSET_UTF8);
            textFile.writeFileContent(writeKHCPath, fileKHCContents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV121454 upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + wkFsapYYYYMMDD + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(writeTPCPath)) {
            upload(writeTPCPath);
            forFsapTPC();
        }
        if (textFile.exists(writeSCHPath)) {
            upload(writeSCHPath);
            forFsapSCH();
        }
        if (textFile.exists(writeTXGPath)) {
            upload(writeTXGPath);
            forFsapTXG();
        }
        if (textFile.exists(writeCYIPath)) {
            upload(writeCYIPath);
            forFsapCYI();
        }
        if (textFile.exists(writeKHCPath)) {
            upload(writeKHCPath);
            forFsapKHC();
        }
    }

    private void forFsapTPC() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_TPC, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121454, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapSCH() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_SCH, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121454 + STRING_SCH, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapTXG() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_TXG, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121454 + STRING_TXG, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapCYI() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_CYI, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121454 + STRING_CYI, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapKHC() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_READFILE, // 來源檔案名稱(20碼長)
                        CONVF_PATH_KHC, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        STRING_CONV121454 + STRING_KHC, // 檔案設定代號 ex:CONVF001
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
