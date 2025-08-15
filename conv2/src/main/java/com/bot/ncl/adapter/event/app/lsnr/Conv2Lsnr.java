/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Conv2;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.string.StringUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Conv2Lsnr")
@Scope("prototype")
public class Conv2Lsnr extends BatchListenerCase<Conv2> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private StringUtil strutil;

    private Conv2 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_27X1158686 = "27X1158686";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String REPORTFILENAME = "CL-BH-009-30003";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CODE_NAME_158686 = "158686";
    private static final String CODE_NAME_155916 = "155916";
    private static final String STRING_ZZZZ9 = "ZZ,ZZ9";
    private static final String STRING_ZZZZZZZZZ9 = "Z,ZZZ,ZZZ,ZZ9";
    private String readFdPutfPath;
    private String writeReportPath;
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String tbsdy;
    private String bd158686;
    private String ed158686;
    private String date158686;
    private String remark158686;
    private String cllbr158686;
    private String reportBhdate;
    private String reportPdate;
    private String reportSeq;
    private String reportCllbr;
    private String reportCllbrc;
    private String reportSitdate;
    private String reportBrseq;
    private String reportEnptpno;
    private String reportAmt;
    private String reportBd;
    private String reportEd;
    private String reportDate;
    private String reportRemark;
    private String reportBrcnt;
    private String reportBramt;
    private String putfCode;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String wkCllbr;
    private String wkBrnm;
    private int sitdate158686;
    private int entpno158686;
    private int page;
    private int putfDate;
    private int putfSitdate;
    private int wkBrcnt = 0;
    private int wkSeq = 0;
    private int pageCnt;
    private int totcnt158686 = 0;
    private int reportTotcnt = 0;
    private BigDecimal amt158686 = ZERO;
    private BigDecimal totamt158686 = ZERO;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal reportTotAmt = ZERO;
    private BigDecimal wkBramt = ZERO;
    private List<String> fileFd158686Contents = new ArrayList<>();
    private List<String> fileReportContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(Conv2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Conv2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr run()");
        init(event);
        // 若FIRST RECORD
        //  A.執行158686-WTIT，寫REPORTFL表頭
        checkFdPutfExist();

        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(Conv2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);

        checkProcessDate();

        // 設定檔名變數值
        // WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        // WK-PUTDIR  <-"DATA/CL/BH/PUTF/" +WK-FDATE+"/27X1158686."
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_27X1158686;
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
                        + CONVF_PATH_27X1158686; // 來源檔在FTP的位置
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
                        + REPORTFILENAME;

        wkCllbr = "001";
    }

    private void checkProcessDate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr checkProcessDate()");
        if (parse.string2Integer(processDate) < 970101) {
            int wkProcessDate = parse.string2Integer(processDate) + 1000000;
            processDate = parse.decimal2String(wkProcessDate, 8, 0);
        }
    }

    private void checkFdPutfExist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr checkFdPutfExist()");
        // FD-PUTF檔案存在，執行158686-RTN
        // 若不存在，執行158686NO-RTN
        if (textFile.exists(readFdPutfPath)) {
            writeFd158686Rc1();
            valuateReportHeader();
            writeReportHeader();
            readFdPutf();
        } else {
            // 158686NO-RTN
            writeFd158686Rc1();
            writeFd158686Rc3();
        }
    }

    private void readFdPutf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr readFdPutf()");
        // 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，WK-END檔案結束記號設為1，跳到158686-LAST
        // 挑 PUTF-CTL=11明細資料
        // PUTF-CTL=12，跳到158686-LAST
        // 開啟檔案
        List<String> lines = textFile.readFileContent(readFdPutfPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutf);

            int putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getCtl()) ? filePutf.getCtl() : "0");
            if (putfCtl == 11) {
                putfCode = filePutf.getCode();
                putfRcptid = filePutf.getRcptid();
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
                putfTxtype = filePutf.getTxtype();
                putfAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutf.getAmt()) ? filePutf.getAmt() : "0"));
                checkUserdata();
                valuateFd158686();
                writeFd158686Rc2();
                valuateReportContents();
                writeReportContents();
                changePage();
                totcnt158686++;
                totamt158686 = totamt158686.add(putfAmt);
            }
        }
        // 若LAST RECORD
        //  A.執行158686-WBRTOT，寫REPORTFL表尾－小計
        //  B.執行158686-WTTOT，寫REPORTFL表尾－總計
        //  C.關檔
        //  D.跳到158686-EXIT，結束本段落
        writeFd158686Rc3();
        valuateReportFooter();
        writeReportFooterTotal();
    }

    private void checkUserdata() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr checkUserdata()");
        // USERDATA-FILLER-RTN
        // WK-2BFLAG:USERDATA 欄位有中文觸發記號
        // 從第 20 碼逐一檢查 USERDATA 欄位每一碼內容，當發現有中文時就不保留直接轉成空白
        putfUserdata = cleanUserData(putfUserdata, 20);
        //        int end = putfUserdata.length();
        //        for (int i = 19; i < Math.min(40, end); i++) {
        //            int wkI = i + 1;
        //            if (!parse.isNumeric(strutil.substr(putfUserdata, i, wkI))) {
        //                putfUserdata =
        //                        strutil.substr(putfUserdata, 0, i)
        //                                + " "
        //                                + strutil.substr(putfUserdata, wkI, end);
        //            }
        //        }
    }

    private void valuateFd158686() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr valuateFd158686()");
        // 搬相關資料到158686-REC...
        // 搬PUTF-...到158686-REC...
        // 搬相關資料到158686-REC...
        cllbr158686 = putfCllbr;

        valuateSitdate();

        valuateDate();

        valuateByCode();

        valuateByTxtypeR();

        valuateTxtypeAEV();

        valuateByTxtypeI();

        amt158686 = putfAmt;
        date158686 = parse.decimal2String(putfDate, 7, 0);

        checkDate158686();

        valuateRemark158686();
    }

    private void valuateSitdate() {
        if (putfSitdate == 0) {
            sitdate158686 = putfDate;
        } else {
            sitdate158686 = putfSitdate;
        }
    }

    private void valuateDate() {
        if (putfDate < 970101) {
            date158686 = parse.decimal2String(putfDate + 1000000, 7, 0);
        } else {
            date158686 = parse.decimal2String(putfDate, 6, 0);
        }
    }

    private void valuateByCode() {
        Set<String> wkCode = Set.of(CODE_NAME_158686, CODE_NAME_155916);
        if (wkCode.contains(putfCode)) {
            entpno158686 = parse.string2Integer(strutil.substr(putfRcptid, 6, 14));
            bd158686 = strutil.substr(putfUserdata, 0, 5);
            ed158686 = strutil.substr(putfUserdata, 5, 10);
        } else {
            entpno158686 = parse.string2Integer(strutil.substr(putfRcptid, 0, 8));
            bd158686 = "0" + strutil.substr(bd158686, 1, 5);
            ed158686 = "0" + strutil.substr(ed158686, 1, 5);
        }
    }

    private void valuateByTxtypeR() {
        if ((putfTxtype.equals("R")
                        && parse.isNumeric(strutil.substr(putfUserdata, 19, 24))
                        && !putfCode.equals(CODE_NAME_158686))
                || (putfCode.equals(CODE_NAME_155916)
                        && parse.isNumeric(strutil.substr(putfUserdata, 19, 22)))) {
            valuateBdEmpty();
            valuateEdEmpty();
        } else if (putfTxtype.equals("R")) {
            bd158686 = strutil.substr(putfUserdata, 19, 24);
            ed158686 = strutil.substr(putfUserdata, 24, 29);
        }

        if (putfTxtype.equals("R")
                && parse.isNumeric(strutil.substr(putfUserdata, 19, 24))
                && !parse.isNumeric(strutil.substr(putfUserdata, 24, 29))) {
            valuateEdEmpty();
        }

        if (!putfTxtype.equals("R")
                && parse.isNumeric(strutil.substr(putfUserdata, 0, 5))
                && !parse.isNumeric(strutil.substr(putfUserdata, 5, 10))) {
            valuateEdEmpty();
        }
    }

    private void valuateTxtypeAEV() {
        Set<String> wkTxtype = Set.of("A", "E", "V");

        if (putfCode.equals(CODE_NAME_158686)
                && (wkTxtype.contains(putfTxtype) || !parse.isNumeric(bd158686))) {
            valuateBdEmpty();
            valuateEdEmpty();
        }

        if (putfCode.equals(CODE_NAME_155916)) {
            valuateBdEmpty();
            if (wkTxtype.contains(putfTxtype)
                    || !parse.isNumeric(strutil.substr(putfUserdata, 0, 3))) {
                valuateEdEmpty();
            }
        }
    }

    private void valuateByTxtypeI() {
        if (putfCode.equals(CODE_NAME_155916)
                && putfTxtype.equals("I")
                && !putfUserdata.startsWith("       ", 3)) {
            valuateBdEmpty();
            valuateEdEmpty();
        }
    }

    private void valuateBdEmpty() {
        bd158686 = "     ";
    }

    private void valuateEdEmpty() {
        ed158686 = "     ";
    }

    private void checkDate158686() {
        if (parse.string2Integer(date158686) < 970101) {
            int wkDate158686 = parse.string2Integer(date158686) + 1000000;
            date158686 = parse.decimal2String(wkDate158686, 7, 0);
        }
    }

    private void valuateRemark158686() {
        // 轉換繳款管道代號
        Set<String> wkPutfTxtypeCM = Set.of("C", "M");
        Set<String> wkPutfTxtypeWV = Set.of("W", "V");
        if (putfTxtype.equals("R") && putfCode.equals(CODE_NAME_158686)) {
            remark158686 = "A3";
        } else if (putfTxtype.equals("R") && putfCode.equals(CODE_NAME_155916)) {
            remark158686 = "W3";
        } else if (wkPutfTxtypeCM.contains(putfTxtype) && putfCode.equals(CODE_NAME_158686)) {
            remark158686 = "D1";
        } else if (wkPutfTxtypeCM.contains(putfTxtype) && putfCode.equals(CODE_NAME_155916)) {
            remark158686 = "W1";
        } else if (putfTxtype.equals("I") && putfCode.equals(CODE_NAME_158686)) {
            remark158686 = "A5";
        } else if (putfTxtype.equals("I") && putfCode.equals(CODE_NAME_155916)) {
            remark158686 = "W5";
        } else if (wkPutfTxtypeWV.contains(putfTxtype) && putfCode.equals(CODE_NAME_158686)) {
            remark158686 = "A6";
        } else if (wkPutfTxtypeWV.contains(putfTxtype) && putfCode.equals(CODE_NAME_155916)) {
            remark158686 = "W6";
        } else if (putfCode.equals(CODE_NAME_155916)) {
            remark158686 = "W4";
        } else {
            remark158686 = "A4";
        }
    }

    private void writeFd158686Rc1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeFd158686Rc1()");
        // 寫檔FD-158686(FIRST RECORD)
        StringBuilder stringBuilderRc1 = new StringBuilder();
        stringBuilderRc1.append(formatUtil.padX("1", 1));
        stringBuilderRc1.append(formatUtil.padX("004", 3));
        stringBuilderRc1.append(formatUtil.padX(processDate, 7));
        stringBuilderRc1.append(formatUtil.padX("", 69));
        fileFd158686Contents.add(formatUtil.padX(stringBuilderRc1.toString(), 80));
    }

    private void writeFd158686Rc2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeFd158686Rc2()");
        StringBuilder stringBuilderRc2 = new StringBuilder();
        stringBuilderRc2.append(formatUtil.padX("2", 1));
        stringBuilderRc2.append(formatUtil.padX("004", 3));
        stringBuilderRc2.append(formatUtil.padX(putfCllbr, 4));
        stringBuilderRc2.append(formatUtil.padX("" + sitdate158686, 7));
        stringBuilderRc2.append(formatUtil.padX("", 8));
        stringBuilderRc2.append(formatUtil.padX("" + entpno158686, 8));
        stringBuilderRc2.append(formatUtil.padX("", 1));
        stringBuilderRc2.append(formatUtil.padX("" + putfAmt, 12));
        stringBuilderRc2.append(formatUtil.padX(bd158686, 6));
        stringBuilderRc2.append(formatUtil.padX(ed158686, 6));
        stringBuilderRc2.append(formatUtil.padX(date158686, 7));
        stringBuilderRc2.append(formatUtil.padX("", 2));
        stringBuilderRc2.append(formatUtil.padX(putfRcptid, 14));
        stringBuilderRc2.append(formatUtil.padX("", 1));
        fileFd158686Contents.add(formatUtil.padX(stringBuilderRc2.toString(), 80));
    }

    private void writeFd158686Rc3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeFd158686Rc3()");
        // 寫檔FD-158686(LAST RECORD)
        StringBuilder stringBuilderRc3 = new StringBuilder();
        stringBuilderRc3.append(formatUtil.padX("3", 1));
        stringBuilderRc3.append(formatUtil.padX("004", 3));
        stringBuilderRc3.append(formatUtil.padX("" + totcnt158686, 6));
        stringBuilderRc3.append(formatUtil.padX("" + totamt158686, 12));
        stringBuilderRc3.append(formatUtil.padX("", 58));
        fileFd158686Contents.add(formatUtil.padX(stringBuilderRc3.toString(), 80));
    }

    private void valuateReportHeader() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr valuateReportHeader()");
        reportBhdate =
                strutil.substr(processDate, 0, 3)
                        + PATH_SEPARATOR
                        + strutil.substr(processDate, 3, 5)
                        + PATH_SEPARATOR
                        + strutil.substr(processDate, 5, 7);
        String wkReportPdate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7);
        reportPdate =
                strutil.substr(wkReportPdate, 0, 3)
                        + PATH_SEPARATOR
                        + strutil.substr(wkReportPdate, 3, 5)
                        + PATH_SEPARATOR
                        + strutil.substr(wkReportPdate, 5, 7);
        page = 1;
    }

    private void valuateReportContents() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr valuateReportContents()");
        // 代收行不同時
        //  (WK-CLLBR=001)第一筆明細資料，執行158686-FBCTL-RTN，取得分行名稱
        //  否則，A.執行158686-WBRTOT，寫REPORTFL表尾－小計、B.執行158686-FBCTL-RTN，取得分行名稱
        if (!wkCllbr.equals(cllbr158686)) {
            readBctl();
            wkBrcnt = 0;
            wkBramt = ZERO;
        }
        wkBrcnt++;
        wkSeq++;
        reportSeq = parse.decimal2String(wkSeq, 5, 0);
        reportCllbr = cllbr158686;
        reportCllbrc = wkBrnm;
        reportSitdate = parse.decimal2String(sitdate158686, 7, 0);
        reportBrseq = parse.decimal2String(wkBrcnt, 5, 0);
        reportEnptpno = parse.decimal2String(entpno158686, 8, 0);
        reportAmt = parse.decimal2String(amt158686, 10, 2);
        reportBd = bd158686;
        reportEd = ed158686;
        reportDate = date158686;
        reportRemark = remark158686;
        wkBramt = amt158686;
        wkCllbr = cllbr158686;
    }

    private void valuateReportFooter() {
        reportTotcnt = totcnt158686;
        reportTotAmt = totamt158686;
    }

    private void readBctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr readBctl()");
        Bctl bctl;
        if ("001".equals(wkCllbr)) {
            bctl =
                    this.event
                            .getAggregateBuffer()
                            .getMgGlobal()
                            .getBctl(parse.string2Integer(cllbr158686));
        } else {
            reportBrcnt = parse.decimal2String(wkBrcnt, 5, 0);
            reportBramt = parse.decimal2String(wkBramt, 10, 2);
            writeReportFooterCount();
            bctl =
                    this.event
                            .getAggregateBuffer()
                            .getMgGlobal()
                            .getBctl(parse.string2Integer(cllbr158686));
        }
        if (bctl == null) {
            wkBrnm = "";
        } else {
            wkBrnm = bctl.getChnam();
        }
    }

    private void writeReportHeader() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeReportHeader()");
        // 寫REPORTFL表頭 (158686-TIT1~158686-TIT4)
        StringBuilder stringBuilderHD1 = new StringBuilder();
        stringBuilderHD1.append(formatUtil.padX("", 26));
        stringBuilderHD1.append(formatUtil.padX(" 臺　灣　銀　行　代　收　舊　制　勞　工　退 ", 44));
        stringBuilderHD1.append(formatUtil.padX(" 休　基　金　清　單 ", 28));
        fileReportContents.add(formatUtil.padX(stringBuilderHD1.toString(), 150));

        StringBuilder stringBuilderHD2 = new StringBuilder();
        stringBuilderHD2.append(formatUtil.padX("BRNO : 236", 10));
        stringBuilderHD2.append(formatUtil.padX("", 93));
        stringBuilderHD2.append(formatUtil.padX("FORM : C009/15868", 17));
        fileReportContents.add(formatUtil.padX(stringBuilderHD2.toString(), 150));

        StringBuilder stringBuilderHD3 = new StringBuilder();
        stringBuilderHD3.append(formatUtil.padX("", 31));
        stringBuilderHD3.append(formatUtil.padX(" 入帳日期 : ", 12));
        stringBuilderHD3.append(formatUtil.padX(reportBhdate, 9));
        stringBuilderHD3.append(formatUtil.padX("", 14));
        stringBuilderHD3.append(formatUtil.padX(" 印表日期 : ", 12));
        stringBuilderHD3.append(formatUtil.padX(reportPdate, 9));
        stringBuilderHD3.append(formatUtil.padX("", 23));
        stringBuilderHD3.append(formatUtil.padX(" 頁數 :", 7));
        stringBuilderHD3.append(formatUtil.padX("" + page, 4));
        fileReportContents.add(formatUtil.padX(stringBuilderHD3.toString(), 150));

        StringBuilder stringBuilderHD4 = new StringBuilder();
        stringBuilderHD4.append(formatUtil.padX("", 2));
        stringBuilderHD4.append(formatUtil.padX(" 總行序號 ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 分行別   ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 15));
        stringBuilderHD4.append(formatUtil.padX(" 收款日期 ", 11));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 分行序號 ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 統一編號 ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 存入金額 ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 繳存月份 ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 5));
        stringBuilderHD4.append(formatUtil.padX(" 起息日   ", 10));
        stringBuilderHD4.append(formatUtil.padX("", 1));
        stringBuilderHD4.append(formatUtil.padX(" 備註 ", 6));
        fileReportContents.add(formatUtil.padX(stringBuilderHD4.toString(), 150));

        StringBuilder stringBuilderHD5 = new StringBuilder();
        stringBuilderHD5.append(reportUtil.makeGate("-", 134));
        fileReportContents.add(formatUtil.padX(stringBuilderHD5.toString(), 150));
    }

    private void writeReportContents() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeReportContents()");
        // 寫REPORTFL報表明細(158686-DTL)
        StringBuilder stringBuilderC1 = new StringBuilder();
        stringBuilderC1.append(formatUtil.padX("", 5));
        stringBuilderC1.append(reportUtil.customFormat(reportSeq, STRING_ZZZZ9));
        stringBuilderC1.append(formatUtil.padX("", 6));
        stringBuilderC1.append(formatUtil.padX(reportCllbr, 3));
        stringBuilderC1.append(formatUtil.padX(reportCllbrc, 20));
        stringBuilderC1.append(formatUtil.padX("", 3));
        stringBuilderC1.append(formatUtil.padX(reportSitdate, 9));
        stringBuilderC1.append(formatUtil.padX("", 9));
        stringBuilderC1.append(reportUtil.customFormat(reportBrseq, STRING_ZZZZ9));
        stringBuilderC1.append(formatUtil.padX("", 7));
        stringBuilderC1.append(formatUtil.padX(reportEnptpno, 8));
        stringBuilderC1.append(formatUtil.padX("", 2));
        stringBuilderC1.append(reportUtil.customFormat(reportAmt, STRING_ZZZZZZZZZ9));
        stringBuilderC1.append(formatUtil.padX("", 6));
        stringBuilderC1.append(formatUtil.padX(reportBd, 5));
        stringBuilderC1.append(formatUtil.padX("-", 1));
        stringBuilderC1.append(formatUtil.padX(reportEd, 5));
        stringBuilderC1.append(formatUtil.padX("", 5));
        stringBuilderC1.append(formatUtil.padX(reportDate, 9));
        stringBuilderC1.append(formatUtil.padX("", 2));
        stringBuilderC1.append(formatUtil.padX(reportRemark, 2));
        fileReportContents.add(formatUtil.padX(stringBuilderC1.toString(), 150));
        // WK-PCTL加1
        pageCnt++;
    }

    private void writeReportFooterCount() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeReportFooterCount()");
        // 寫REPORTFL表尾－小計(158686-BRTOT)
        StringBuilder stringBuilderFC1 = new StringBuilder();
        stringBuilderFC1.append(formatUtil.padX("", 53));
        stringBuilderFC1.append(formatUtil.padX(" 筆數 :", 8));
        stringBuilderFC1.append(reportUtil.customFormat(reportBrcnt, STRING_ZZZZ9));
        stringBuilderFC1.append(formatUtil.padX("", 6));
        stringBuilderFC1.append(reportUtil.customFormat(reportBramt, STRING_ZZZZZZZZZ9));
        fileReportContents.add(formatUtil.padX(stringBuilderFC1.toString(), 150));

        StringBuilder stringBuilderFC2 = new StringBuilder();
        stringBuilderFC2.append(reportUtil.makeGate("-", 134));
        fileReportContents.add(formatUtil.padX(stringBuilderFC2.toString(), 150));
        fileReportContents.add("");
        pageCnt = pageCnt + 3;
    }

    private void writeReportFooterTotal() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeReportFooterTotal()");
        // 寫REPORTFL表尾－總計(158686-TTOT)
        StringBuilder stringBuilderFT1 = new StringBuilder();
        stringBuilderFT1.append(formatUtil.padX("", 43));
        stringBuilderFT1.append(formatUtil.padX(" 總　計   ", 10));
        stringBuilderFT1.append(formatUtil.padX(" 筆數 :", 8));
        stringBuilderFT1.append(reportUtil.customFormat("" + reportTotcnt, STRING_ZZZZ9));
        stringBuilderFT1.append(formatUtil.padX("", 6));
        stringBuilderFT1.append(formatUtil.padX(" 代收金額 :", 11));
        stringBuilderFT1.append(reportUtil.customFormat("" + reportTotAmt, STRING_ZZZZZZZZZ9));
        fileReportContents.add(formatUtil.padX(stringBuilderFT1.toString(), 150));
    }

    private void changePage() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr changePage()");
        // WK-PCTL行數控制>=45時，A.執行158686-WTIT，寫REPORTFL表頭、B.WK-PCTL清0
        if (pageCnt > 45) {
            page++;
            pageCnt = 0;
            writeReportHeader();
        }
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv2Lsnr writeFile()");
        try {
            //            textFile.deleteFile(readFdPutfPath);
            textFile.deleteFile(writeReportPath);
            // CHARSET_UTF8);
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_UTF8);
            upload(writeReportPath, "RPT", "");
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

    public static String cleanUserData(String userdata, int st) {
        // st 起始位置 目前為20
        if (userdata == null || userdata.isEmpty()) {
            return "";
        }
        if (st == 0) {
            return userdata;
        }
        if (userdata.length() < st) {
            return userdata; // 若長度不足 20 碼，直接回傳原字串
        }

        // 保留前20碼
        StringBuilder newUserData = new StringBuilder(userdata.substring(0, st));

        // 遍歷第20碼後的字元
        for (int i = st; i < userdata.length(); i++) {
            char ch = userdata.charAt(i);
            if (isChinese(ch)) {
                newUserData.append(' '); // 若是中文則轉空白
            } else {
                newUserData.append(ch); // 其他字元保留
            }
        }

        return newUserData.toString();
    }

    // 判斷是否為中文字元
    private static boolean isChinese(char ch) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(ch);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
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
                        CONVF_PATH_27X1158686, // 來源檔案名稱(20碼長)
                        CONVF_PATH_27X1158686, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV2", // 檔案設定代號 ex:CONVF001
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
