/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Conv7;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
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
@Component("Conv7Lsnr")
@Scope("prototype")
public class Conv7Lsnr extends BatchListenerCase<Conv7> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private ClmrService clmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private Conv7 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_029 = "CL-BH-029";
    private static final String CONVF_PATH_27Z1420209 = "27Z1420209";
    private static final String CONVF_PATH_PUTFCTL = "PUTFCTL";
    private static final String CONVF_PATH_PUTFCTL2 = "PUTFCTL2";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_GE999 = "GE999";
    private static final String STRING_CH_DOLLAR = " 元 ";
    private static final String STRING_ERROR_MSG = "查無事業單位基本資料檔";
    private static final String STRING_CODE = "420209";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final BigDecimal cfee15 = BigDecimal.valueOf(15);
    private static final BigDecimal cfee40 = BigDecimal.valueOf(40);
    private static final BigDecimal cfee60 = BigDecimal.valueOf(60);
    private static final BigDecimal cfee80 = BigDecimal.valueOf(80);
    private static final BigDecimal cfee100 = BigDecimal.valueOf(100);
    private static final BigDecimal cfee120 = BigDecimal.valueOf(120);
    private static final BigDecimal cfee150 = BigDecimal.valueOf(150);
    private static final BigDecimal cfee200 = BigDecimal.valueOf(200);
    private static final BigDecimal cfee300 = BigDecimal.valueOf(300);
    private ClmrBus clmrBus;
    private String readFdPutfPath;
    private String writeReportPath;
    private String writeFdPutfctlPath;
    private String writeFdPutfct2Path;
    private String processDate;
    private String tbsdy;
    private String wkReportPdate;
    private String putfCode;
    private String putfCllbr;
    private String putfUserdata;
    private String wkProdtype;
    private String kind2_420209;
    private String wkBankno;
    private String reportBdate;
    private String reportEdate;
    private String clmrActno;
    private String sunit420209;
    private String runit420209;
    private String kind420209;
    private String sdate420209;
    private String type420209;
    private String putfctlPuttype;
    private String putfctlPutname;
    private String putfctlGendt;
    private String putfctlTreat;
    private String putfctlPutaddr;
    private String putfctlPbrno;
    private String putfctl2Puttype;
    private String putfctl2Putname;
    private String putfctl2Code;
    private String putfctl2Bdate;
    private String putfctl2Edate;
    private String fixcode420209;
    private String serino420209;
    private String fix420209;
    private String txdate420209;
    private String bank420209;
    private String brno420209;
    private String trno420209;
    private String empno420209;
    private String acno420209;
    private String source420209;
    private String date420209;
    private String outbank420209;
    private String outacno420209;
    private int putfCtl;
    private int putfDate;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int reportSubcnt15;
    private int reportSubcnt40;
    private int reportSubcnt60;
    private int reportSubcnt80;
    private int reportSubcnt100;
    private int reportSubcnt120;
    private int reportSubcnt150;
    private int reportSubcnt200;
    private int reportSubcnt300;
    private int reportMonthSubcnt;
    private int wkTotCnt = 0;
    private int totCnt420209 = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal cfee2_420209 = ZERO;
    private BigDecimal reportSubamt15 = ZERO;
    private BigDecimal reportSubamt40 = ZERO;
    private BigDecimal reportSubamt60 = ZERO;
    private BigDecimal reportSubamt80 = ZERO;
    private BigDecimal reportSubamt100 = ZERO;
    private BigDecimal reportSubamt120 = ZERO;
    private BigDecimal reportSubamt150 = ZERO;
    private BigDecimal reportSubamt200 = ZERO;
    private BigDecimal reportSubamt300 = ZERO;
    private BigDecimal reportSubcfee15 = ZERO;
    private BigDecimal reportSubcfee40 = ZERO;
    private BigDecimal reportSubcfee60 = ZERO;
    private BigDecimal reportSubcfee80 = ZERO;
    private BigDecimal reportSubcfee100 = ZERO;
    private BigDecimal reportSubcfee120 = ZERO;
    private BigDecimal reportSubcfee150 = ZERO;
    private BigDecimal reportSubcfee200 = ZERO;
    private BigDecimal reportSubcfee300 = ZERO;
    private BigDecimal reportMonthSubamt = ZERO;
    private BigDecimal reportMonthSubcfee = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal wkTotcfee = ZERO;
    private BigDecimal totTxamt420209 = ZERO;
    private BigDecimal totCfee420209 = ZERO;
    private BigDecimal totAmt420209 = ZERO;
    private BigDecimal txamt420209 = ZERO;
    private BigDecimal amt420209 = ZERO;
    private List<String> fileFd420209Contents = new ArrayList<>();
    private List<String> fileReportContents = new ArrayList<>();
    private List<String> fileFdPutfctlContents = new ArrayList<>();
    private List<String> fileFdPutfctl2Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(Conv7 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv7Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Conv7 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv7Lsnr run()");
        init(event);
        writeFd420209Rc1();
        writeReportHeader();
        checkFdPutfExist();
        writeReportFooter();
        writeFile();

        batchResponse();
    }

    private void init(Conv7 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv7Lsnr init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 設定作業日、設定檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        String wkRocdate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7);
        wkReportPdate =
                wkRocdate.substring(0, 3)
                        + PATH_SEPARATOR
                        + wkRocdate.substring(3, 5)
                        + PATH_SEPARATOR
                        + wkRocdate.substring(5, 7);
        String wkFdate = processDate.substring(1);

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
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_27Z1420209;

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
                        + CONVF_PATH_27Z1420209; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfPath = getLocalPath(sourceFile);
        }

        writeReportPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_029;

        writeFdPutfctlPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFCTL;

        writeFdPutfct2Path =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFCTL2;
    }

    private void checkFdPutfExist() {
        if (textFile.exists(readFdPutfPath)) {
            readFdPutfData();
        } else {
            // 搬相關資料到420209-REC(RC=1)
            valuateFd420209Rc1();

            // 寫檔FD-420209(RC=1)
            writeFd420209Rc1();

            // 搬相關資料到420209-REC(RC=3)
            valuateFd420209Rc3();

            // 寫檔FD-420209
            writeFd420209Rc3();

            queryClmrBy420209();
            queryClmcBy420209();

            valuatePutfctl();
            writePutfctlFile();

            valuatePutfctl2();
            writePutfctl2File();
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PUTF路徑查無資料");
        }
    }

    private void valuateFd420209Rc1() {
        sunit420209 = "004";
        runit420209 = "03374805";
        kind420209 = "101";
        sdate420209 = parse.decimal2String(parse.string2Integer(processDate) + 20110000, 8, 0);
        type420209 = "2";
    }

    private void writeFd420209Rc1() {
        StringBuilder sbRc1 = new StringBuilder();
        sbRc1.append(formatUtil.padX("1", 1));
        sbRc1.append(formatUtil.padX(sunit420209, 8));
        sbRc1.append(formatUtil.padX(runit420209, 8));
        sbRc1.append(formatUtil.pad9(kind420209, 3));
        sbRc1.append(formatUtil.pad9(sdate420209, 8));
        sbRc1.append(formatUtil.padX(type420209, 1));
        sbRc1.append(formatUtil.padX("", 121));
        fileFd420209Contents.add(formatUtil.padX(sbRc1.toString(), 150));
    }

    private void readFdPutfData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv7Lsnr readFdPutf()");
        // 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，WK-END檔案結束記號設為1，跳到158686-LAST
        // 挑 PUTF-CTL=11明細資料
        // PUTF-CTL=12，跳到158686-LAST
        // 開啟檔案
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutf);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getCtl()) ? filePutf.getCtl() : "0");

            valuateWhenPutfctl1Equal2();

            if (putfCtl == 11) {
                putfCode = filePutf.getCode();
                putfDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutf.getEntdy()) ? filePutf.getEntdy() : "0");
                putfCllbr = filePutf.getCllbr();
                putfUserdata = filePutf.getUserdata();
                putfSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutf.getSitdate())
                                        ? filePutf.getSitdate()
                                        : "0"));
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutf.getAmt()) ? filePutf.getAmt() : "0"));
                valuateFd420209Rc2();
                writeFd420209Rc2();
                wkTotCnt++;
                wkTotAmt = wkTotAmt.add(putfAmt);
                wkTotcfee = wkTotcfee.add(cfee2_420209);
            } else if (putfCtl == 12) {
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
                valuateFd420209Rc3();
                writeFd420209Rc3();
            }
        }
    }

    private void valuateWhenPutfctl1Equal2() {
        if (putfCtl == 21) {
            valuateCfeeData();
            valuateSumCfee();
        } else if (putfCtl == 22) {
            valuateReportFile();
            writeReportContents();
        }
    }

    private void valuateCfeeData() {
        String wkUserdata = putfUserdata;
        wkProdtype = wkUserdata.substring(1, 3);
        wkBankno = wkUserdata.substring(2, 5);
        checkProdtype();
    }

    private void checkProdtype() {
        switch (wkProdtype) {
            case "23" -> valuateProdtypeIs23();
            case "32" -> valuateProdtypeIs32();
            case "25" -> valuateProdtypeIs25();
            case "34" -> valuateProdtypeIs34();
        }
    }

    private void valuateProdtypeIs23() {
        kind2_420209 = "2";
        Set<BigDecimal> setAmt1 =
                Set.of(BigDecimal.valueOf(688), BigDecimal.valueOf(567), BigDecimal.valueOf(777));
        Set<BigDecimal> setAmt2 =
                Set.of(BigDecimal.valueOf(1088), BigDecimal.valueOf(917), BigDecimal.valueOf(1127));
        Set<BigDecimal> setAmt3 = Set.of(BigDecimal.valueOf(1437), BigDecimal.valueOf(1027));
        Set<BigDecimal> setAmt4 =
                Set.of(
                        BigDecimal.valueOf(1688),
                        BigDecimal.valueOf(1727),
                        BigDecimal.valueOf(1517));
        Set<BigDecimal> setAmt5 = Set.of(BigDecimal.valueOf(2102), BigDecimal.valueOf(1692));
        Set<BigDecimal> setAmt6 =
                Set.of(BigDecimal.valueOf(2293), BigDecimal.valueOf(1883)); // 1437,1027重複先拔掉
        if (setAmt1.contains(putfAmt)) {
            cfee2_420209 = cfee40;
        } else if (setAmt2.contains(putfAmt)) {
            cfee2_420209 = cfee60;
        } else if (setAmt3.contains(putfAmt)) {
            cfee2_420209 = cfee80;
        } else if (setAmt4.contains(putfAmt)) {
            cfee2_420209 = cfee100;
        } else if (setAmt5.contains(putfAmt)) {
            cfee2_420209 = cfee120;
        } else if (setAmt6.contains(putfAmt)) {
            cfee2_420209 = cfee150;
        } else {
            cfee2_420209 = cfee40;
        }
    }

    private void valuateProdtypeIs32() {
        kind2_420209 = "1";
        cfee2_420209 = cfee15;
        if (wkBankno.equals("004")) {
            Set<BigDecimal> setAmt1 =
                    Set.of(
                            BigDecimal.valueOf(688),
                            BigDecimal.valueOf(567),
                            BigDecimal.valueOf(777));
            Set<BigDecimal> setAmt2 =
                    Set.of(
                            BigDecimal.valueOf(1088),
                            BigDecimal.valueOf(917),
                            BigDecimal.valueOf(1127),
                            BigDecimal.valueOf(1017),
                            BigDecimal.valueOf(1227));
            Set<BigDecimal> setAmt3 = Set.of(BigDecimal.valueOf(1437), BigDecimal.valueOf(1027));
            Set<BigDecimal> setAmt4 =
                    Set.of(
                            BigDecimal.valueOf(1688),
                            BigDecimal.valueOf(1727),
                            BigDecimal.valueOf(1517),
                            BigDecimal.valueOf(2227),
                            BigDecimal.valueOf(2017),
                            BigDecimal.valueOf(1749),
                            BigDecimal.valueOf(1959));
            Set<BigDecimal> setAmt5 = Set.of(BigDecimal.valueOf(2102), BigDecimal.valueOf(1692));
            Set<BigDecimal> setAmt6 =
                    Set.of(BigDecimal.valueOf(2293), BigDecimal.valueOf(1883)); // 1437,1027重複先拔掉
            if (setAmt1.contains(putfAmt)) {
                cfee2_420209 = cfee40;
            } else if (setAmt2.contains(putfAmt)) {
                cfee2_420209 = cfee60;
            } else if (setAmt3.contains(putfAmt)) {
                cfee2_420209 = cfee80;
            } else if (setAmt4.contains(putfAmt)) {
                cfee2_420209 = cfee100;
            } else if (setAmt5.contains(putfAmt)) {
                cfee2_420209 = cfee120;
            } else if (setAmt6.contains(putfAmt)) {
                cfee2_420209 = cfee150;
            } else {
                cfee2_420209 = cfee40;
            }
        }
    }

    private void valuateProdtypeIs25() {
        kind2_420209 = "2";
        Set<BigDecimal> setAmt1 =
                Set.of(
                        BigDecimal.valueOf(1800),
                        BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(1957),
                        BigDecimal.valueOf(1756),
                        BigDecimal.valueOf(1850),
                        BigDecimal.valueOf(1635),
                        BigDecimal.valueOf(1970),
                        BigDecimal.valueOf(1550),
                        BigDecimal.valueOf(1212),
                        BigDecimal.valueOf(1430));
        Set<BigDecimal> setAmt2 =
                Set.of(
                        BigDecimal.valueOf(2488),
                        BigDecimal.valueOf(2888),
                        BigDecimal.valueOf(2808),
                        BigDecimal.valueOf(2485),
                        BigDecimal.valueOf(2450),
                        BigDecimal.valueOf(3000),
                        BigDecimal.valueOf(3700),
                        BigDecimal.valueOf(2950),
                        BigDecimal.valueOf(3650));
        Set<BigDecimal> setAmt3 =
                Set.of(
                        BigDecimal.valueOf(3588),
                        BigDecimal.valueOf(3988),
                        BigDecimal.valueOf(3803),
                        BigDecimal.valueOf(3460),
                        BigDecimal.valueOf(3850),
                        BigDecimal.valueOf(3300),
                        BigDecimal.valueOf(4550),
                        BigDecimal.valueOf(3800),
                        BigDecimal.valueOf(4500));
        if (setAmt1.contains(putfAmt)) {
            cfee2_420209 = cfee100;
        } else if (setAmt2.contains(putfAmt)) {
            cfee2_420209 = cfee200;
        } else if (setAmt3.contains(putfAmt)) {
            cfee2_420209 = cfee300;
        } else {
            cfee2_420209 = cfee100;
        }
    }

    private void valuateProdtypeIs34() {
        kind2_420209 = "1";
        cfee2_420209 = cfee15;
        if (wkBankno.equals("004")) {
            Set<BigDecimal> setAmt1 =
                    Set.of(
                            BigDecimal.valueOf(1800),
                            BigDecimal.valueOf(2000),
                            BigDecimal.valueOf(1957),
                            BigDecimal.valueOf(1756),
                            BigDecimal.valueOf(1850),
                            BigDecimal.valueOf(1635),
                            BigDecimal.valueOf(1970),
                            BigDecimal.valueOf(1550),
                            BigDecimal.valueOf(1212),
                            BigDecimal.valueOf(1430));
            Set<BigDecimal> setAmt2 =
                    Set.of(
                            BigDecimal.valueOf(2488),
                            BigDecimal.valueOf(2888),
                            BigDecimal.valueOf(2808),
                            BigDecimal.valueOf(2485),
                            BigDecimal.valueOf(2450),
                            BigDecimal.valueOf(3000),
                            BigDecimal.valueOf(3700),
                            BigDecimal.valueOf(2950),
                            BigDecimal.valueOf(3650));
            Set<BigDecimal> setAmt3 =
                    Set.of(
                            BigDecimal.valueOf(3588),
                            BigDecimal.valueOf(3988),
                            BigDecimal.valueOf(3803),
                            BigDecimal.valueOf(3460),
                            BigDecimal.valueOf(3850),
                            BigDecimal.valueOf(3300),
                            BigDecimal.valueOf(4550),
                            BigDecimal.valueOf(3800),
                            BigDecimal.valueOf(4500));
            if (setAmt1.contains(putfAmt)) {
                cfee2_420209 = cfee100;
            } else if (setAmt2.contains(putfAmt)) {
                cfee2_420209 = cfee200;
            } else if (setAmt3.contains(putfAmt)) {
                cfee2_420209 = cfee300;
            } else {
                cfee2_420209 = cfee100;
            }
        }
    }

    private void valuateSumCfee() {
        if (cfee2_420209.compareTo(cfee15) == 0) {
            reportSubcnt15++;
            reportSubamt15 = reportSubamt15.add(putfAmt);
            reportSubcfee15 = reportSubcfee15.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee40) == 0) {
            reportSubcnt40++;
            reportSubamt40 = reportSubamt40.add(putfAmt);
            reportSubcfee40 = reportSubcfee40.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee60) == 0) {
            reportSubcnt60++;
            reportSubamt60 = reportSubamt60.add(putfAmt);
            reportSubcfee60 = reportSubcfee60.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee80) == 0) {
            reportSubcnt80++;
            reportSubamt80 = reportSubamt80.add(putfAmt);
            reportSubcfee80 = reportSubcfee80.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee100) == 0) {
            reportSubcnt100++;
            reportSubamt100 = reportSubamt100.add(putfAmt);
            reportSubcfee100 = reportSubcfee100.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee120) == 0) {
            reportSubcnt120++;
            reportSubamt120 = reportSubamt120.add(putfAmt);
            reportSubcfee120 = reportSubcfee120.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee150) == 0) {
            reportSubcnt150++;
            reportSubamt150 = reportSubamt150.add(putfAmt);
            reportSubcfee150 = reportSubcfee150.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee200) == 0) {
            reportSubcnt200++;
            reportSubamt200 = reportSubamt200.add(putfAmt);
            reportSubcfee200 = reportSubcfee200.add(cfee2_420209);
        } else if (cfee2_420209.compareTo(cfee300) == 0) {
            reportSubcnt300++;
            reportSubamt300 = reportSubamt300.add(putfAmt);
            reportSubcfee300 = reportSubcfee300.add(cfee2_420209);
        }
        reportMonthSubcnt++;
        reportMonthSubamt = reportMonthSubamt.add(putfAmt);
        reportMonthSubcfee = reportMonthSubcfee.add(cfee2_420209);
    }

    private void valuateReportFile() {
        String wkPutfBdate = parse.decimal2String(putfBdate, 7, 0);
        String wkPutfEdate = parse.decimal2String(putfEdate, 7, 0);
        reportBdate =
                wkPutfBdate.substring(0, 3)
                        + PATH_SEPARATOR
                        + wkPutfBdate.substring(3, 5)
                        + PATH_SEPARATOR
                        + wkPutfBdate.substring(5, 7);
        reportEdate =
                wkPutfEdate.substring(0, 3)
                        + PATH_SEPARATOR
                        + wkPutfEdate.substring(3, 5)
                        + PATH_SEPARATOR
                        + wkPutfEdate.substring(5, 7);
    }

    private void writeReportHeader() {
        StringBuilder sbH1 = new StringBuilder();
        sbH1.append(formatUtil.padX("", 28));
        sbH1.append(formatUtil.padX("   台　灣　銀　行　代　收　富　邦　產　險　保　險  費　手　續　費　　　總　單", 80));
        fileReportContents.add(formatUtil.padX(sbH1.toString(), 150));

        StringBuilder sbH2 = new StringBuilder();
        sbH2.append(formatUtil.padX("", 2));
        sbH2.append(formatUtil.padX(" 分行別： ", 10));
        sbH2.append(formatUtil.padX("003", 3));
        sbH2.append(formatUtil.padX("", 84));
        sbH2.append(formatUtil.padX("FORM : C029 ", 12));
        fileReportContents.add(formatUtil.padX(sbH2.toString(), 150));

        StringBuilder sbH3 = new StringBuilder();
        sbH3.append(formatUtil.padX(" 代收月份 ： ", 12));
        sbH3.append(
                formatUtil.padX(
                        processDate.substring(1, 3) + PATH_SEPARATOR + processDate.substring(3, 5),
                        5));
        sbH3.append(formatUtil.padX("", 81));
        sbH3.append(formatUtil.padX(" 印表日期 : ", 12));
        sbH3.append(formatUtil.padX(wkReportPdate, 9));
        fileReportContents.add(formatUtil.padX(sbH3.toString(), 150));

        StringBuilder sbH4 = new StringBuilder();
        sbH4.append(formatUtil.padX(" 代收期間 : ", 12));
        sbH4.append(formatUtil.padX(reportBdate, 9));
        sbH4.append(formatUtil.padX("-", 1));
        sbH4.append(formatUtil.padX(reportEdate, 9));
        sbH4.append(formatUtil.padX("", 69));
        sbH4.append(formatUtil.padX(" 頁數 :", 7));
        sbH4.append(formatUtil.pad9("1", 2)); // page
        fileReportContents.add(formatUtil.padX(sbH4.toString(), 150));

        StringBuilder sbH5 = new StringBuilder();
        sbH5.append(formatUtil.padX("", 6));
        sbH5.append(formatUtil.padX("  手　續　費　種　類  ", 22));
        sbH5.append(formatUtil.padX("", 6));
        sbH5.append(formatUtil.padX("  筆　數  ", 10));
        sbH5.append(formatUtil.padX("", 13));
        sbH5.append(formatUtil.padX("  代　收　金　額  ", 18));
        sbH5.append(formatUtil.padX("", 6));
        sbH5.append(formatUtil.padX("  手　續　費　金　額  ", 22));
        fileReportContents.add(formatUtil.padX(sbH5.toString(), 150));

        StringBuilder sbH6 = new StringBuilder();
        sbH6.append(formatUtil.padX("", 2));
        sbH6.append(reportUtil.makeGate("-", 148));
        fileReportContents.add(formatUtil.padX(sbH6.toString(), 150));
    }

    private void writeReportContents() {
        fileReportContents.add("");
        fileReportContents.add("");
        StringBuilder sbC1 = new StringBuilder();
        sbC1.append(formatUtil.padX("", 12));
        sbC1.append(formatUtil.padX("15 ", 2));
        sbC1.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC1.append(formatUtil.padX("", 16));
        sbC1.append(formatUtil.padX(cntFormat.format(reportSubcnt15), 7));
        sbC1.append(formatUtil.padX("", 18));
        sbC1.append(formatUtil.padX(decimalFormat.format(reportSubamt15), 13));
        sbC1.append(formatUtil.padX("", 18));
        sbC1.append(formatUtil.padX(decimalFormat.format(reportSubcfee15), 10));
        fileReportContents.add(formatUtil.padX(sbC1.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC2 = new StringBuilder();
        sbC2.append(formatUtil.padX("", 12));
        sbC2.append(formatUtil.padX("40 ", 2));
        sbC2.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC2.append(formatUtil.padX("", 16));
        sbC2.append(formatUtil.padX(cntFormat.format(reportSubcnt40), 7));
        sbC2.append(formatUtil.padX("", 18));
        sbC2.append(formatUtil.padX(decimalFormat.format(reportSubamt40), 13));
        sbC2.append(formatUtil.padX("", 18));
        sbC2.append(formatUtil.padX(decimalFormat.format(reportSubcfee40), 10));
        fileReportContents.add(formatUtil.padX(sbC2.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC3 = new StringBuilder();
        sbC3.append(formatUtil.padX("", 12));
        sbC3.append(formatUtil.padX("60 ", 2));
        sbC3.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC3.append(formatUtil.padX("", 16));
        sbC3.append(formatUtil.padX(cntFormat.format(reportSubcnt60), 7));
        sbC3.append(formatUtil.padX("", 18));
        sbC3.append(formatUtil.padX(decimalFormat.format(reportSubamt60), 13));
        sbC3.append(formatUtil.padX("", 18));
        sbC3.append(formatUtil.padX(decimalFormat.format(reportSubcfee60), 10));
        fileReportContents.add(formatUtil.padX(sbC3.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC4 = new StringBuilder();
        sbC4.append(formatUtil.padX("", 12));
        sbC4.append(formatUtil.padX("80 ", 2));
        sbC4.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC4.append(formatUtil.padX("", 16));
        sbC4.append(formatUtil.padX(cntFormat.format(reportSubcnt80), 7));
        sbC4.append(formatUtil.padX("", 18));
        sbC4.append(formatUtil.padX(decimalFormat.format(reportSubamt80), 13));
        sbC4.append(formatUtil.padX("", 18));
        sbC4.append(formatUtil.padX(decimalFormat.format(reportSubcfee80), 10));
        fileReportContents.add(formatUtil.padX(sbC4.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC5 = new StringBuilder();
        sbC5.append(formatUtil.padX("", 12));
        sbC5.append(formatUtil.padX("100", 2));
        sbC5.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC5.append(formatUtil.padX("", 16));
        sbC5.append(formatUtil.padX(cntFormat.format(reportSubcnt100), 7));
        sbC5.append(formatUtil.padX("", 18));
        sbC5.append(formatUtil.padX(decimalFormat.format(reportSubamt100), 13));
        sbC5.append(formatUtil.padX("", 18));
        sbC5.append(formatUtil.padX(decimalFormat.format(reportSubcfee100), 10));
        fileReportContents.add(formatUtil.padX(sbC5.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC6 = new StringBuilder();
        sbC6.append(formatUtil.padX("", 12));
        sbC6.append(formatUtil.padX("120", 2));
        sbC6.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC6.append(formatUtil.padX("", 16));
        sbC6.append(formatUtil.padX(cntFormat.format(reportSubcnt120), 7));
        sbC6.append(formatUtil.padX("", 18));
        sbC6.append(formatUtil.padX(decimalFormat.format(reportSubamt120), 13));
        sbC6.append(formatUtil.padX("", 18));
        sbC6.append(formatUtil.padX(decimalFormat.format(reportSubcfee120), 10));
        fileReportContents.add(formatUtil.padX(sbC6.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC7 = new StringBuilder();
        sbC7.append(formatUtil.padX("", 12));
        sbC7.append(formatUtil.padX("150", 2));
        sbC7.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC7.append(formatUtil.padX("", 16));
        sbC7.append(formatUtil.padX(cntFormat.format(reportSubcnt150), 7));
        sbC7.append(formatUtil.padX("", 18));
        sbC7.append(formatUtil.padX(decimalFormat.format(reportSubamt150), 13));
        sbC7.append(formatUtil.padX("", 18));
        sbC7.append(formatUtil.padX(decimalFormat.format(reportSubcfee150), 10));
        fileReportContents.add(formatUtil.padX(sbC7.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC8 = new StringBuilder();
        sbC8.append(formatUtil.padX("", 12));
        sbC8.append(formatUtil.padX("200", 2));
        sbC8.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC8.append(formatUtil.padX("", 16));
        sbC8.append(formatUtil.padX(cntFormat.format(reportSubcnt200), 7));
        sbC8.append(formatUtil.padX("", 18));
        sbC8.append(formatUtil.padX(decimalFormat.format(reportSubamt200), 13));
        sbC8.append(formatUtil.padX("", 18));
        sbC8.append(formatUtil.padX(decimalFormat.format(reportSubcfee200), 10));
        fileReportContents.add(formatUtil.padX(sbC8.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");

        StringBuilder sbC9 = new StringBuilder();
        sbC9.append(formatUtil.padX("", 12));
        sbC9.append(formatUtil.padX("300", 2));
        sbC9.append(formatUtil.padX(STRING_CH_DOLLAR, 4));
        sbC9.append(formatUtil.padX("", 16));
        sbC9.append(formatUtil.padX(cntFormat.format(reportSubcnt300), 7));
        sbC9.append(formatUtil.padX("", 18));
        sbC9.append(formatUtil.padX(decimalFormat.format(reportSubamt300), 13));
        sbC9.append(formatUtil.padX("", 18));
        sbC9.append(formatUtil.padX(decimalFormat.format(reportSubcfee300), 10));
        fileReportContents.add(formatUtil.padX(sbC9.toString(), 150));

        fileReportContents.add("");
        fileReportContents.add("");
    }

    private void writeReportFooter() {
        StringBuilder sbF1 = new StringBuilder();
        sbF1.append(formatUtil.padX("", 2));
        sbF1.append(reportUtil.makeGate("-", 148));
        fileReportContents.add(formatUtil.padX(sbF1.toString(), 150));

        StringBuilder sbF2 = new StringBuilder();
        sbF2.append(formatUtil.padX(cntFormat.format(reportMonthSubcnt), 7));
        sbF2.append(formatUtil.padX("", 2));
        sbF2.append(formatUtil.padX(" 本月總金額： ", 14));
        sbF2.append(formatUtil.padX(decimalFormat.format(reportMonthSubamt), 13));
        sbF2.append(formatUtil.padX("", 4));
        sbF2.append(formatUtil.padX(" 本月手續費： ", 14));
        sbF2.append(formatUtil.padX(decimalFormat.format(reportMonthSubcfee), 10));
        fileReportContents.add(formatUtil.padX(sbF2.toString(), 150));
    }

    private void valuateFd420209Rc2() {
        fixcode420209 = "00";
        serino420209 = putfUserdata;
        fix420209 = "00";
        txdate420209 = parse.decimal2String(putfSitdate + 20110000, 8, 0);
        bank420209 = "004";
        brno420209 = putfCllbr;
        trno420209 = "";
        empno420209 = "";
        queryClmrByCode();
        acno420209 = clmrActno;
        source420209 = "4";
        date420209 = parse.decimal2String(putfDate + 20110000, 8, 0);
        txamt420209 = putfAmt;
        amt420209 = putfAmt;
        outbank420209 = "";
        outacno420209 = "";
    }

    private void writeFd420209Rc2() {
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.pad9("2", 1));
        sbRc2.append(formatUtil.padX(fixcode420209, 2));
        sbRc2.append(formatUtil.padX(serino420209, 12));
        sbRc2.append(formatUtil.padX(fix420209, 2));
        sbRc2.append(formatUtil.pad9(txdate420209, 8));
        sbRc2.append(formatUtil.padX(bank420209, 3));
        sbRc2.append(formatUtil.padX(brno420209, 4));
        sbRc2.append(formatUtil.padX(trno420209, 2));
        sbRc2.append(formatUtil.padX(empno420209, 10));
        sbRc2.append(formatUtil.padX(acno420209, 14));
        sbRc2.append(formatUtil.padX(kind2_420209, 1));
        sbRc2.append(formatUtil.padX(source420209, 1));
        sbRc2.append(formatUtil.pad9(date420209, 8));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(txamt420209), 9));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(cfee2_420209), 9));
        sbRc2.append(formatUtil.pad9(decimalFormat.format(amt420209), 9));
        sbRc2.append(formatUtil.padX(outbank420209, 3));
        sbRc2.append(formatUtil.padX(outacno420209, 19));
        sbRc2.append(formatUtil.padX("", 33));
        fileFd420209Contents.add(formatUtil.padX(sbRc2.toString(), 150));
    }

    private void valuateFd420209Rc3() {
        totTxamt420209 = wkTotAmt;
        totCnt420209 = wkTotCnt;
        totCfee420209 = wkTotcfee;
        totAmt420209 = wkTotAmt;
    }

    private void writeFd420209Rc3() {
        StringBuilder sbRc3 = new StringBuilder();
        sbRc3.append(formatUtil.padX("3", 1));
        sbRc3.append(formatUtil.padX(sunit420209, 8));
        sbRc3.append(formatUtil.padX(runit420209, 8));
        sbRc3.append(formatUtil.pad9(kind420209, 3));
        sbRc3.append(formatUtil.pad9(sdate420209, 8));
        sbRc3.append(formatUtil.padX(type420209, 1));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(totTxamt420209), 14));
        sbRc3.append(formatUtil.pad9(cntFormat.format(totCnt420209), 10));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(totCfee420209), 14));
        sbRc3.append(formatUtil.pad9(decimalFormat.format(totAmt420209), 10));
        sbRc3.append(formatUtil.padX("", 73));
        fileFd420209Contents.add(formatUtil.padX(sbRc3.toString(), 150));
    }

    private void queryClmrByCode() {
        clmrBus = clmrService.findById(putfCode);
        validateClmrExist();
        clmrActno = parse.decimal2String(clmrBus.getActno(), 14, 0);
    }

    private void validateClmrExist() {
        if (Objects.isNull(clmrBus)) {
            throw new LogicException(STRING_GE999, STRING_ERROR_MSG);
        }
    }

    private void queryClmrBy420209() {
        clmrBus = clmrService.findById(STRING_CODE);
        if (!Objects.isNull(clmrBus)) {
            putfctlPbrno = parse.decimal2String(clmrBus.getPbrno(), 3, 0);
        } else {
            putfctlPbrno = "";
        }
    }

    private void queryClmcBy420209() {
        ClmcBus clmcBus = clmcService.findById(STRING_CODE);
        if (!Objects.isNull(clmcBus)) {
            putfctlPutaddr = clmcBus.getPutaddr();
            putfctl2Puttype = parse.decimal2String(clmcBus.getPuttype(), 2, 0);
            putfctl2Putname = clmcBus.getPutname();
        } else {
            putfctlPutaddr = "";
            putfctl2Puttype = "";
            putfctl2Putname = "";
        }
    }

    private void valuatePutfctl() {
        putfctlPuttype = "27";
        putfctlPutname = "Z1420209";
        putfctlGendt = processDate.substring(1);
        putfctlTreat = "0";
    }

    private void writePutfctlFile() {
        StringBuilder sbPF = new StringBuilder();
        sbPF.append(formatUtil.pad9(putfctlPuttype, 2));
        sbPF.append(formatUtil.padX(putfctlPutname, 8));
        sbPF.append(formatUtil.pad9(putfctlGendt, 6));
        sbPF.append(formatUtil.padX(putfctlPutaddr, 40));
        sbPF.append(formatUtil.pad9(putfctlTreat, 1));
        sbPF.append(formatUtil.pad9(putfctlPbrno, 3));
        fileFdPutfctlContents.add(formatUtil.padX(sbPF.toString(), 60));
    }

    private void valuatePutfctl2() {
        putfctl2Code = STRING_CODE;
        putfctl2Bdate = processDate.substring(1);
        putfctl2Edate = processDate.substring(1);
    }

    private void writePutfctl2File() {
        StringBuilder sbPF2 = new StringBuilder();
        sbPF2.append(formatUtil.pad9(putfctl2Puttype, 2));
        sbPF2.append(formatUtil.padX(putfctl2Putname, 8));
        sbPF2.append(formatUtil.padX(putfctl2Code, 6));
        sbPF2.append(formatUtil.pad9(putfctl2Bdate, 6));
        sbPF2.append(formatUtil.pad9(putfctl2Edate, 6));
        sbPF2.append(formatUtil.padX("", 12));
        fileFdPutfctl2Contents.add(formatUtil.padX(sbPF2.toString(), 40));
    }

    private void writeFile() {
        try {
            chooseFileByPutf();
            textFile.deleteFile(writeReportPath);
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_BIG5);
            upload(writeReportPath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void chooseFileByPutf() {
        if (textFile.exists(readFdPutfPath)) {
            textFile.deleteFile(readFdPutfPath);
            textFile.writeFileContent(readFdPutfPath, fileFd420209Contents, CHARSET_UTF8);

            upload(readFdPutfPath, "DATA", "PUTF");
        } else {
            textFile.deleteFile(writeFdPutfctlPath);
            textFile.deleteFile(writeFdPutfct2Path);
            textFile.writeFileContent(writeFdPutfctlPath, fileFdPutfctlContents, CHARSET_UTF8);
            upload(writeFdPutfctlPath, "DATA", "");
            textFile.writeFileContent(writeFdPutfct2Path, fileFdPutfctl2Contents, CHARSET_UTF8);
            upload(writeFdPutfct2Path, "DATA", "");
        }
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

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", CONVF_PATH_029);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
