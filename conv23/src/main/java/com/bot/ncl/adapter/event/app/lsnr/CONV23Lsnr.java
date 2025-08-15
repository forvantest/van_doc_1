/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.*;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV23;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
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
@Component("CONV23Lsnr")
@Scope("prototype")
public class CONV23Lsnr extends BatchListenerCase<CONV23> {
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FilePUTF filePutf;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV23 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF"; // 讀檔目錄
    private static final String CONVF_PATH_C018 = "CL-BH-018";
    private static final String wkPutfile = "07X1510040"; // 讀檔檔名 = wkConvfile
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String wkPutdir;
    private String wkRptdir;
    private String rc510040;
    private String send510040;
    private String bh510040;
    private String prcdate510040;
    private String wkBank = "";
    private String putfRcptid;
    private String putfUserdata;
    private String wkUserdata;
    private String wkRcptid;
    private String wkBBank;
    private String wkBActno;
    private String opno510040;
    private String actno510040;
    private String bill510040;
    private String ofisno510040;
    private String no510040;
    private String YYMM510040;
    private String lmtdate510040;
    private String chkdg510040;
    private String chksno510040;
    private String kind510040;
    private String clbank510040;
    private String billbhno510040;
    private String reportBrno;
    private String titleLabel;
    private int putfDate;
    private int putfSitdate;
    private int wkCtlSwitchFlag;
    private int wkTotpage;
    private int lmtdate1_510040;
    private int YYMM1_510040;
    private int date1_510040;
    private int sitdate1_510040;
    private int sitdate510040;
    private int wkTotcnt;
    private int totcnt510040;
    private int reportTotcnt;
    private int wkSubcnt;
    private int reportSubcnt;
    private BigDecimal wkTotamt = ZERO;
    private BigDecimal wkSubamt = ZERO;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal amt510040 = ZERO;
    private BigDecimal reportSubamt = ZERO;
    private BigDecimal totamt510040 = ZERO;
    private BigDecimal reportTotamt = ZERO;
    private List<String> file510040Contents = new ArrayList<>();
    private List<String> file018Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV23 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV23 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr run()");
        init(event);
        checkPutfExist();
        checkPath();
        batchResponse();
    }

    private void init(CONV23 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr init()");
        // 設定作業日、檔名日期變數值
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = processDate.substring(1);

        // 設定檔名變數值、分行別變數、REPORTFL
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(wkPutdir);
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
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        wkRptdir =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_C018;

        titleLabel = " 中華電信全國性繳費彙總表　　　 ";
        wkTotpage = 1;
    }

    private void checkPutfExist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr checkPutfExist()");
        // 若FD-PUTF檔案存在，執行510040-RTN
        if (textFile.exists(wkPutdir)) {
            valuateFd510040Rc1();
            writeFd510040Rc1();
            writeReportHeader();
            readFdPutf();
            writeFile();
        }
    }

    private void valuateFd510040Rc1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuateFd510040Rc1()");
        // 搬相關資料到510040-REC...(RC=1)
        rc510040 = "1";
        send510040 = "0040000";
        bh510040 = "001";
        prcdate510040 = processDate;
        wkBank = "000";
        wkCtlSwitchFlag = 0;
    }

    private void writeFd510040Rc1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeFd510040Rc1()");
        // 寫檔FD-510040(FIRST RECORD)
        StringBuilder stringBuilderRc1 = new StringBuilder();
        stringBuilderRc1.append(formatUtil.padX(rc510040, 1));
        stringBuilderRc1.append(formatUtil.padX(send510040, 7));
        stringBuilderRc1.append(formatUtil.padX(bh510040, 3));
        stringBuilderRc1.append(formatUtil.padX(prcdate510040, 7));
        stringBuilderRc1.append(formatUtil.padX("", 92));
        file510040Contents.add(formatUtil.padX(stringBuilderRc1.toString(), 110));
    }

    private void writeReportHeader() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeReportHeader()");
        // 執行RPT-TITLE-RTN，寫REPORTFL報表表頭
        StringBuilder sbH1 = new StringBuilder();
        sbH1.append(formatUtil.padX("", 23));
        sbH1.append(formatUtil.padX(titleLabel, 32));
        sbH1.append(formatUtil.padX("", 21));
        sbH1.append(formatUtil.padX("FORM : C018 ", 12));
        file018Contents.add(formatUtil.padX(sbH1.toString(), 90));

        StringBuilder sbH2 = new StringBuilder();
        sbH2.append(formatUtil.padX(" 分行別： ", 10));
        sbH2.append(formatUtil.padX("054", 3));
        sbH2.append(formatUtil.padX("", 5));
        sbH2.append(formatUtil.padX("  印表日期： ", 13));
        sbH2.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + PATH_SEPARATOR
                                + processDate.substring(3, 5)
                                + PATH_SEPARATOR
                                + processDate.substring(5, 7),
                        9));
        sbH2.append(formatUtil.padX("", 38));
        sbH2.append(formatUtil.padX(" 總頁次 :", 9));
        sbH2.append(formatUtil.padX("" + wkTotpage, 4));
        sbH2.append(formatUtil.padX("", 15));
        file018Contents.add(formatUtil.padX(sbH2.toString(), 110));

        StringBuilder sbH3 = new StringBuilder();
        sbH3.append(formatUtil.padX("", 11));
        sbH3.append(formatUtil.padX(" 銀行別 ", 8));
        sbH3.append(formatUtil.padX("", 20));
        sbH3.append(formatUtil.padX(" 筆數 ", 6));
        sbH3.append(formatUtil.padX("", 20));
        sbH3.append(formatUtil.padX(" 金額 ", 6));
        sbH3.append(formatUtil.padX("", 30));
        file018Contents.add(formatUtil.padX(sbH3.toString(), 110));

        StringBuilder sbH4 = new StringBuilder();
        sbH4.append(formatUtil.padX("", 3));
        sbH4.append(reportUtil.makeGate("-", 97));
        file018Contents.add(formatUtil.padX(sbH4.toString(), 100));

        file018Contents.add(formatUtil.padX("", 100));
    }

    private void readFdPutf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr readFdPutf()");
        // 循序讀取FD-PUTF
        ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET_UTF8);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);

            String putfCtl = filePutf.getCtl();
            putfRcptid = filePutf.getRcptid();
            putfDate =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getEntdy()) ? filePutf.getEntdy() : "0");
            putfUserdata = filePutf.getUserdata();
            putfSitdate =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getSitdate()) ? filePutf.getSitdate() : "0");
            putfAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(filePutf.getAmt()) ? filePutf.getAmt() : "0");
            if ("11".equals(putfCtl)) {
                // 若PUTF-CTL = 11(銷帳明細資料)
                //  A.執行DATA-INPUT-RTN，搬PUTF-REC...到51004-REC...(RC=2)
                //  B.累加筆數金額
                //  C.執行FILE-DTL-RTN，A.搬相關資料到510040-REC...(RC=2)、B.寫檔FD-510040(DETAIL)
                //  D.執行RPT-DTL-RTN，BANK相同時，累計筆數、金額；BANK不同時，寫REPORTFL報表明細
                valuateFd510040Rc2();
                valuate510040Contents();
                valuateSum();
                writeFd510040Rc2();
                writeReportContents();
            } else if ("12".equals(putfCtl)) {
                // 若PUTF-CTL = 12(銷帳彙總資料)
                //  A.執行DATA-INPUT-RTN，搬PUTF-REC...到51004-REC...(RC=2)
                //  B.執行FILE-LAST-RTN，A.搬彙總資料到510040-REC...(RC=3)、B.寫檔FD-510040(LAST RECORD)
                //  C.執行RPT-LAST-RTN，A.寫REPORTFL報表明細、B.寫REPORTFL報表表尾
                valuateFd510040Rc2();
                valuateFd510040Rc3();
                writeFd510040Rc3();
                valuateReportLastContents();
                writeReportContents();
                writeReportFooter();
            } else if ("21".equals(putfCtl) && wkCtlSwitchFlag == 0) {
                // 若PUTF-CTL = 21(對帳明細資料) & WK-CTL-SWITCH-FLAG = 0
                //  A.執行RPT-CTL-SWITCH-RTN，跳頁、改TITLE-LABEL表頭、寫REPORTFL報表表頭
                //  B.WK-CTL-SWITCH-FLAG改表頭註記設為1(0.未改 1.已改)
                // 若PUTF-CTL = 21(對帳明細資料)
                //  A.執行DATA-INPUT-RTN，搬PUTF-REC...到51004-REC...(RC=2)
                //  B.累計筆數、金額
                //  C.執行RPT-DTL-RTN，BANK相同時，累計筆數、金額；BANK不同時，寫REPORTFL報表明細
                valuateHeaderChange();
                writeReportHeader();
                valuateFd510040Rc2();
                wkTotcnt++;
                wkTotamt = wkTotamt.add(putfAmt);
                valuateSum();
                writeReportContents();
            } else if ("22".equals(putfCtl)) {
                // 若PUTF-CTL = 22(對帳彙總資料)
                //  A.執行DATA-INPUT-RTN，搬PUTF-REC...到51004-REC...(RC=2)
                //  B.執行RPT-LAST-RTN，A.寫REPORTFL報表明細、B.寫REPORTFL報表表尾
                valuateFd510040Rc2();
                valuateReportLastContents();
                writeReportContents();
                writeReportFooter();
            }
        }
    }

    private void valuateFd510040Rc2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuateFd510040Rc2()");
        // 搬PUTF-REC...到51004-REC...(RC=2)
        // 搬相關資料到510040-REC...(RC=2)
        rc510040 = "2";
        wkUserdata = putfUserdata;
        wkRcptid = putfRcptid;
        wkBank = wkUserdata.substring(1, 4);
        wkBBank = wkBank;
        // wkBActno = wkActno
        wkBActno = wkUserdata.substring(6, 20);
    }

    private void valuate510040Contents() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuate510040Contents()");
        lmtdate1_510040 = 0;
        YYMM1_510040 = 1;
        date1_510040 = 1;
        sitdate1_510040 = 1;
        // no510040 = wkNo
        no510040 = wkRcptid.substring(0, 10);
        // YYMM510040 = wkYYMM
        YYMM510040 = wkRcptid.substring(10, 14);
        // bill510040 = wkBill
        bill510040 = wkRcptid.substring(14, 16);
        // lmtdate510040 = wkLmtdate
        lmtdate510040 = wkUserdata.substring(22, 28);
        // ofisno510040 = wkOfisno
        ofisno510040 = wkUserdata.substring(28, 32);
        opno510040 = "TD";
        // actno510040 = reportActno
        actno510040 = wkBBank + wkBActno;
        amt510040 = putfAmt;
        chkdg510040 = "  ";
        chksno510040 = "2";
        kind510040 = "I";
        clbank510040 = "0040000";
        billbhno510040 = "1";
        putfSitdateIsZero();
    }

    private void putfSitdateIsZero() {
        if (putfSitdate == 0) {
            sitdate510040 = putfDate;
        } else {
            sitdate510040 = putfSitdate;
        }
    }

    private void writeFd510040Rc2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeFd510040Rc2()");
        // 寫檔FD-510040(DETAIL)
        StringBuilder sbRc2 = new StringBuilder();
        sbRc2.append(formatUtil.padX(rc510040, 1));
        sbRc2.append(formatUtil.padX(actno510040, 17));
        sbRc2.append(formatUtil.padX(parse.decimal2String(bill510040, 2, 0), 2));
        sbRc2.append(formatUtil.padX(parse.decimal2String(ofisno510040, 4, 0), 4));
        sbRc2.append(formatUtil.padX("", 2));
        sbRc2.append(formatUtil.padX(parse.decimal2String(no510040, 10, 0), 10));
        sbRc2.append(formatUtil.padX(parse.decimal2String(YYMM1_510040, 1, 0), 1));
        sbRc2.append(formatUtil.padX(parse.decimal2String(YYMM510040, 4, 0), 4));
        sbRc2.append(formatUtil.padX(parse.decimal2String(lmtdate1_510040, 1, 0), 1));
        sbRc2.append(formatUtil.padX(parse.decimal2String(lmtdate510040, 6, 0), 6));
        sbRc2.append(formatUtil.padX(parse.decimal2String(amt510040, 9, 0), 9));
        sbRc2.append(formatUtil.padX(chkdg510040, 2));
        sbRc2.append(formatUtil.padX(parse.decimal2String(date1_510040, 1, 0), 1));
        sbRc2.append(formatUtil.padX(chksno510040, 4));
        sbRc2.append(formatUtil.padX(kind510040, 1));
        sbRc2.append(formatUtil.padX(clbank510040, 7));
        sbRc2.append(formatUtil.padX(billbhno510040, 1));
        sbRc2.append(formatUtil.padX(opno510040, 2));
        sbRc2.append(formatUtil.padX(parse.decimal2String(sitdate1_510040, 1, 0), 1));
        sbRc2.append(formatUtil.padX(parse.decimal2String(sitdate510040, 6, 0), 6));
        sbRc2.append(formatUtil.padX("", 22));
        file510040Contents.add(formatUtil.padX(sbRc2.toString(), 110));
    }

    private void valuateSum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuateSum()");
        // BANK相同時，累計筆數、金額
        // BANK不同時，寫REPORTFL報表明細(WK-DETAIL-LINE)
        if ("000".equals(reportBrno)) {
            reportBrno = wkBank;
            sumSub();
        } else {
            checkRptBrno();
        }
    }

    private void sumSub() {
        wkSubcnt++;
        wkSubamt = wkSubamt.add(putfAmt);
    }

    private void checkRptBrno() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr checkRptBrno()");
        if (wkBank.equals(reportBrno)) {
            sumSub();
        } else {
            reportSubcnt = wkSubcnt;
            reportSubamt = wkSubamt;
            wkSubcnt = 1;
            wkSubamt = putfAmt;
            reportBrno = wkBank;
        }
    }

    private void valuateFd510040Rc3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuateFd510040Rc3()");
        // 搬彙總資料到510040-REC...(RC=3)
        rc510040 = "3";
        totcnt510040 = wkTotcnt;
        totamt510040 = wkTotamt;
    }

    private void writeFd510040Rc3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeFd510040Rc3()");
        // 寫檔FD-510040(LAST RECORD)
        StringBuilder stringBuilderRc3 = new StringBuilder();
        stringBuilderRc3.append(formatUtil.padX(rc510040, 1));
        stringBuilderRc3.append(formatUtil.padX(parse.decimal2String(totcnt510040, 9, 0), 9));
        stringBuilderRc3.append(formatUtil.padX(parse.decimal2String(totamt510040, 13, 0), 13));
        stringBuilderRc3.append(formatUtil.padX("", 87));
        file510040Contents.add(formatUtil.padX(stringBuilderRc3.toString(), 110));
    }

    private void valuateReportLastContents() {
        reportSubcnt = wkSubcnt;
        reportSubamt = wkSubamt;
        reportTotcnt = wkTotcnt;
        reportTotamt = wkTotamt;
    }

    private void writeReportContents() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeReportContents()");
        // A.寫REPORTFL報表明細
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX("", 15));
        stringBuilder1.append(formatUtil.padX(reportBrno, 3));
        stringBuilder1.append(formatUtil.padX("", 18));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(reportSubcnt), 8));
        stringBuilder1.append(formatUtil.padX("", 15));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(reportSubamt), 11));
        stringBuilder1.append(formatUtil.padX("", 30));
        file018Contents.add(formatUtil.padX(stringBuilder1.toString(), 100));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX("", 3));
        stringBuilder2.append(reportUtil.makeGate("-", 117));
        file018Contents.add(formatUtil.padX(stringBuilder2.toString(), 120));
    }

    private void writeReportFooter() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeReportFooter()");
        // B.寫REPORTFL報表表尾
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(formatUtil.padX("", 13));
        stringBuilder.append(formatUtil.padX(" 總計 ", 6));
        stringBuilder.append(formatUtil.padX("", 17));
        stringBuilder.append(formatUtil.padX(cntFormat.format(reportTotcnt), 8));
        stringBuilder.append(formatUtil.padX("", 13));
        stringBuilder.append(formatUtil.padX(decimalFormat.format(reportTotamt), 13));
        stringBuilder.append(formatUtil.padX("", 30));
        file018Contents.add(formatUtil.padX(stringBuilder.toString(), 100));
    }

    private void valuateHeaderChange() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr valuateHeaderChange()");
        // 跳頁、改TITLE-LABEL表頭
        wkBank = "000";
        reportBrno = "000";
        reportSubcnt = wkSubcnt;
        reportSubamt = wkSubamt;
        wkTotcnt = 0;
        wkTotamt = ZERO;
        wkSubcnt = 0;
        wkSubamt = ZERO;
        titleLabel = " 中華電信全國性繳費彙總表月報　 ";
        wkCtlSwitchFlag = 1;
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV23Lsnr writeFile()");
        try {
            //            textFile.deleteFile(wkPutdir);
            textFile.deleteFile(wkRptdir);
            //            textFile.writeFileContent(wkPutdir, file510040Contents, CHARSET_UTF8);
            textFile.writeFileContent(wkRptdir, file018Contents, CHARSET_Big5);
            upload(wkRptdir, "RPT", "");
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
        if (textFile.exists(wkPutdir)) {
            upload(wkPutdir, "", "");
            forFsap();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        wkPutfile, // 來源檔案名稱(20碼長)
                        wkPutfile, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV23", // 檔案設定代號 ex:CONVF001
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", CONVF_PATH_C018);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
