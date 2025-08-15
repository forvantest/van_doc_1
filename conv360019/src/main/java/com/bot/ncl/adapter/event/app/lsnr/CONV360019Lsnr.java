/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV360019;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
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
@Component("CONV360019Lsnr")
@Scope("prototype")
public class CONV360019Lsnr extends BatchListenerCase<CONV360019> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapSync fsapSync;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV360019 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private StringBuilder sb = new StringBuilder();

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;

    private List<String> fileContents = new ArrayList<>(); //  檔案內容
    private List<String> reportContents_1 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_2 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_3 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_4 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_5 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_6 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_7 = new ArrayList<>(); //  報表內容
    private String reportFilePath_1;
    private String reportFilePath_2;
    private String reportFilePath_3;
    private String reportFilePath_4;
    private String reportFilePath_5;
    private String reportFilePath_6;
    private String reportFilePath_7;
    private static final String REPORT_NAME_1 = "CL-BH-026-1";
    private static final String REPORT_NAME_2 = "CL-BH-026-2";
    private static final String REPORT_NAME_3 = "CL-BH-026-3";
    private static final String REPORT_NAME_4 = "CL-BH-026-4";
    private static final String REPORT_NAME_5 = "CL-BH-026-5";
    private static final String REPORT_NAME_6 = "CL-BH-026-6";
    private static final String REPORT_NAME_7 = "CL-BH-026-7";
    private static final String[] C_UNIT = {"", "拾", "佰", "仟"};
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final Map<Integer, String> digitMap = new HashMap<>();

    static {
        digitMap.put(0, "零");
        digitMap.put(1, "壹");
        digitMap.put(2, "貳");
        digitMap.put(3, "參");
        digitMap.put(4, "肆");
        digitMap.put(5, "伍");
        digitMap.put(6, "陸");
        digitMap.put(7, "柒");
        digitMap.put(8, "捌");
        digitMap.put(9, "玖");
    }

    private String wkPutdir;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String processDate;
    private int fnbsdy;
    private String tbsdy;
    private int nbsdy;
    private int nnbsdy;
    private String wkYYMMDD;
    private String wkYYMM_1;
    private String wkYYMM;
    private String wkChkdg;
    private String wkFdate;
    private String wkCdate;
    private String wkDate;
    private int wkTotcnt;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private String wkPutfile;
    private String wkConvfile;
    private String wkTxtype;
    private String wkCllbr;
    private String wkClbank;
    private int wkBdate;
    private int wkEdate;
    private int wkPgcnt;
    private int wkIdx;
    // ------------------360019-----------------
    private String _360019_Rc;
    private String _360019_Cldate_R;
    private int _360019_Acdate_R;
    private String _360019_Pdate_R;
    private String _360019_Pdate_P;
    private String _360019_Totamt;
    private String _360019_Totcnt;
    private String _360019_YYMM = "";
    private String _360019_Lmtdate = "";
    private String _360019_Date = "";
    private String _360019_Sitdate = "";
    private String _360019_Cardno = "";
    private String _360019_Kind = "";
    private String _360019_Opno = "";
    private String _360019_Clbank;
    private String _360019_Billbhno;
    private String _360019_Telecomid;
    private String _360019_Bill;
    private String _360019_Ofisno;
    private String _360019_Chksno;
    private String _360019_Tel;
    private String _360019_Bdate_P;
    private String _360019_Edate_P;
    private String _360019_Totcnt_Month;
    private String _360019_Totamt_Month;
    private String _360019_YYMM_P;
    private String _360019_Brno_R;
    private String _360019_Form_R;
    private String _360019_Send;
    private String _360019_Bh;
    private String _360019_Prcdate;
    private String _360019_Brhfee_Month_A;
    private String _360019_Brhfee_Month_I;
    private String _360019_Brhfee_Month_D;
    private String _360019_Feeamt_Month;
    private String _360019_Chkdg;
    private BigDecimal _360019_Brhamt_Month_I;
    private BigDecimal _360019_Brhamt_Month_A;
    private BigDecimal _360019_Brhamt_Month_D;
    private BigDecimal _360019_Amt;
    private int _360019_Brhcnt_Month_I;
    private int _360019_Brhcnt_Month_A;
    private int _360019_Brhcnt_Month_D;
    private String _360019_Txtype_Month_I;
    private String _360019_Txtype_Month_A;
    private String _360019_Txtype_Month_D;
    private String _360019_Cname_Month_I;
    private String _360019_Cname_Month_A;
    private String _360019_Cname_Month_D;
    private String _360019_Cname_Ar;
    private String _360019_Txtype_Ar;
    private int _360019_Brhcnt_Ar;
    private BigDecimal _360019_Brhamt_Ar;
    private BigDecimal _360019_Acamt_Ar;
    private String _360019_Cname_Ir;
    private String _360019_Txtype_Ir;
    private int _360019_Brhcnt_Ir;
    private BigDecimal _360019_Brhamt_Ir;
    private BigDecimal _360019_Acamt_Ir;
    private int _360019_Totcnt_R;
    private BigDecimal _360019_Totamt_R;
    private BigDecimal _360019_Totacamt_R;
    private String _360019_Cname_Dr;
    private String _360019_Txtype_Dr;
    private int _360019_Brhcnt_Dr;
    private BigDecimal _360019_Brhamt_Dr;
    private BigDecimal _360019_Acamt_Dr;
    // -----------------------------------------
    private String _320019_Form_R;
    private String _320019_Cldate_R;
    private int _320019_Acdate_R;
    private String _320019_Pdate_R;
    private int _320019_Brhcnt_Ar;
    private BigDecimal _320019_Brhamt_Ar;
    private BigDecimal _320019_Acamt_Ar;
    private int _320019_Brhcnt_Ir;
    private BigDecimal _320019_Brhamt_Ir;
    private BigDecimal _320019_Acamt_Ir;
    private int _320019_Brhcnt_Dr;
    private BigDecimal _320019_Brhamt_Dr;
    private BigDecimal _320019_Acamt_Dr;
    private int _320019_Brhcnt_Jr;
    private BigDecimal _320019_Brhamt_Jr;
    private BigDecimal _320019_Acamt_Jr;
    private int _320019_Totcnt_R2;
    private BigDecimal _320019_Totamt_R2;
    private int _320019_Totcnt_R1;
    private BigDecimal _320019_Totamt_R1;
    private BigDecimal _320019_Totacamt_R1;
    private BigDecimal _320019_Totacamt_R2;
    private int _320019_Totcnt_R3;
    private BigDecimal _320019_Totamt_R3;
    private BigDecimal _320019_Totacamt_R3;
    // -----------------------------------------
    private BigDecimal wkTotamt_A;
    private int wkTotcnt_A;
    private BigDecimal wkTotamt_I;
    private int wkTotcnt_I;
    private BigDecimal wkTotamt_D;
    private int wkTotcnt_D;
    private BigDecimal wkTotamt_J;
    private int wkTotcnt_J;
    private BigDecimal wkBrhfee_A;
    private BigDecimal wkBrhfee_I;
    private BigDecimal wkBrhfee_D;
    private BigDecimal wkBrhfee_J;
    private BigDecimal wkBrhfee;
    private int wkMonthcnt_A = 0;
    private BigDecimal wkMonthamt_A = BigDecimal.ZERO;
    private int wkMonthcnt_I = 0;
    private BigDecimal wkMonthamt_I = BigDecimal.ZERO;
    private int wkMonthcnt_D = 0;
    private BigDecimal wkMonthamt_D = BigDecimal.ZERO;
    private int wkMonthcnt_J = 0;
    private BigDecimal wkMonthamt_J = BigDecimal.ZERO;
    private int wkMonthcnt = 0;
    private String wkBrno = "";
    private BigDecimal wkMonthamt = BigDecimal.ZERO;
    private BigDecimal wkTotfee = BigDecimal.ZERO;
    private String wk_Head1_Brno = "";
    private String wk_Head1_Form_R = "";
    private String wk_Line2_Totcnt;
    private BigDecimal wk_Line4_Totfee = BigDecimal.ZERO;
    private String wk_Line3_Chinfee = "";
    private String wk_Line6_Ofisnm;
    private String wk_Line9_Memo;
    private int wk_Line9_Totcnt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV360019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV360019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr run()");
        init(event);
        //// 清變數
        // 066900* 銷帳媒體須區分臨櫃與網際網路交易
        // 067000* 臨櫃交易  A
        // 066800     MOVE       0            TO     WK-TOTCNT,WK-TOTAMT.
        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
        //// 設定檔名變數,檔名
        // 067100     MOVE       "27X1360019"    TO     WK-PUTFILE.
        // 067200     MOVE       "27X1360019TA"  TO     WK-CONVFILE.
        wkPutfile = "27X1360019";
        wkConvfile = "27X1360019TA";
        // 067300     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF   TO WK-PUTDIR.
        // 067400     CHANGE  ATTRIBUTE FILENAME OF FD-360019 TO WK-CONVDIR.
        // WK-PUTDIR.  DATA/CL/BH/PUTF/XXXXXX/27X1360019
        // WK-CONVDIR. DATA/CL/BH/PUTF/XXXXXX/27X1360019TA
        String putfDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putfDir + PATH_SEPARATOR + wkPutfile;
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
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        // 067500     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF  IS = VALUE(TRUE)
        // 067600       PERFORM    360019-RTN   THRU  360019-EXIT.
        if (textFile.exists(wkPutdir)) {
            _360019();
        }
        // 067700* 網際網路交易  I
        //// 清變數
        // 067800     MOVE        0              TO     WK-TOTAMT ,WK-TOTCNT.
        wkTotamt = BigDecimal.ZERO;
        wkTotcnt = 0;
        //// 設定檔名變數,檔名
        // 067900     MOVE       "27X1360019"    TO     WK-PUTFILE.
        // 068000     MOVE       "27X1360019TI"  TO     WK-CONVFILE.
        wkPutfile = "27X1360019";
        wkConvfile = "27X1360019TI";
        // 068100     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF   TO WK-PUTDIR.
        // 068200     CHANGE  ATTRIBUTE FILENAME OF FD-360019 TO WK-CONVDIR.
        putfDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putfDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(wkPutdir);
        sourceFtpPath =
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
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }
        //// FD-PUTF檔案存在，執行360019-RTN
        // 068300     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF  IS = VALUE(TRUE)
        // 068400       PERFORM    360019-RTN   THRU  360019-EXIT.
        if (textFile.exists(wkPutdir)) {
            _360019();
        }

        // 068500* 產生營業部 C026-1 日報表 (360019)
        // 068600* 產生營業部 C026-2 月報表 (360019)
        // 068700* 產生營業部 C026-3 收據 (360019)
        // 068800     PERFORM   0000-INIT-RTN     THRU  0000-INIT-EXIT.
        // 068900     PERFORM   360019-RPT-RTN   THRU  360019-RPT-EXIT.
        _0000_Init();
        _360019_Rpt();

        // 069000* 產生信義分行 C026-4 日報表 (320019)
        // 069100* 產生信義分行 C026-6 月報表 (320019&520010)
        // 069200* 產生信義分行 C026-7 收據 (320019&520010)
        // 069300     PERFORM   0000-INIT-RTN     THRU  0000-INIT-EXIT.
        // 069400     PERFORM   320019-RPT-RTN   THRU  320019-RPT-EXIT.
        _0000_Init();
        _320019_Rpt();

        // 069500* 產生給中華電信 C026-5 報表 (360019&320019&520010)
        // 069600     PERFORM   0000-INIT-RTN     THRU  0000-INIT-EXIT.
        // 069700     PERFORM   360019-RPT5-RTN   THRU  360019-RPT5-EXIT.
        _0000_Init();
        _360019_Rpt5();

        checkPath();

        batchResponse();
    }

    private void init(CONV360019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr init ....");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 作業日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        nbsdy = parse.string2Integer(labelMap.get("NBSDY")); // 待中菲APPLE提供正確名稱
        nnbsdy = parse.string2Integer(labelMap.get("NNBSDY")); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), processDate, processDate);
        if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
            TxBizDate fdClndr = txBizDates.get(0);
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "fdClndr = [{}]", fdClndr.toString());
            fnbsdy = fdClndr.getFnbsdy();
        }
        wkYYMMDD = formatUtil.pad9(processDate, 7);
        wkYYMM_1 = wkYYMMDD.substring(0, 5);
        wkFdate = wkYYMMDD.substring(1, 7);
        wkCdate = wkYYMMDD.substring(1, 7);
        // PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        wkDate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7);
        //// 設定代收日期、解繳日期、印表日期
        //// FD-BHDATE-NNBSDY 下下營業日
        //// 360019-ACDATE-R :解繳日期(360019-TIT3'S變數)
        //// 320019-ACDATE-R :解繳日期(320019-TIT3'S變數)
        //// 360019-CLDATE-R :代收日期(360019-TIT4'S變數)
        //// 320019-CLDATE-R :代收日期(320019-TIT4'S變數)
        // 066200     MOVE       WK-YYMMDD        TO     360019-CLDATE-R.
        // 066300     MOVE    FD-BHDATE-NNBSDY    TO     360019-ACDATE-R.
        // 066400     MOVE       WK-DATE      TO   360019-PDATE-R,360019-PDATE-P.
        // 066500     MOVE       WK-YYMMDD        TO     320019-CLDATE-R.
        // 066600     MOVE    FD-BHDATE-NNBSDY    TO     320019-ACDATE-R.
        // 066700     MOVE       WK-DATE      TO   320019-PDATE-R.
        _360019_Cldate_R = wkYYMMDD;
        _360019_Acdate_R = nnbsdy;
        _360019_Pdate_R = wkDate;
        _360019_Pdate_P = wkDate;
        _320019_Cldate_R = wkYYMMDD;
        _320019_Acdate_R = nnbsdy;
        _320019_Pdate_R = wkDate;

        reportFilePath_1 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_1;
        reportFilePath_2 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_2;
        reportFilePath_3 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_3;
        reportFilePath_4 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_4;
        reportFilePath_5 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_5;
        reportFilePath_6 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_6;
        reportFilePath_7 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_7;
        textFile.deleteFile(reportFilePath_1);
        textFile.deleteFile(reportFilePath_2);
        textFile.deleteFile(reportFilePath_3);
        textFile.deleteFile(reportFilePath_4);
        textFile.deleteFile(reportFilePath_5);
        textFile.deleteFile(reportFilePath_6);
        textFile.deleteFile(reportFilePath_7);
    }

    private void _360019() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _360019 ....");
        // 071500 360019-RTN.
        // 071600***
        // 071700 360019-FILE.

        //// 開啟檔案
        // 071800     OPEN       OUTPUT   FD-360019.
        // 071900     OPEN       INPUT    FD-PUTF.

        //// 搬相關資料到360019-REC
        // 072000     MOVE       SPACES       TO      360019-REC.
        // 072100     MOVE       "1"          TO      360019-RC.
        // 072200     MOVE       "004"        TO      360019-SEND.
        // 072300     MOVE       "001"        TO      360019-BH.
        // 072400     MOVE       WK-YYMMDD    TO      360019-PRCDATE.
        _360019_Rc = "1";
        _360019_Send = "004";
        _360019_Bh = "001";
        _360019_Prcdate = wkYYMMDD;
        //// 寫檔FD-360019
        // 072500     WRITE      360019-REC.
        fileContents.add(_360019_Rec());

        // 072600 360019-FNEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到360019-FLAST
        // 072700     READ   FD-PUTF     AT  END  GO TO  360019-FLAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            // 072800     MOVE      PUTF-TXTYPE  TO      WK-TXTYPE.
            wkTxtype = filePutf.getTxtype();
            //// IF PUTF-CTL <> 11,跳下一筆
            // 072900     IF        PUTF-CTL        NOT  =      11
            if (!"11".equals(filePutf.getCtl())) {
                // 073000       GO TO   360019-FNEXT.
                continue;
            }
            // 073100* 臨櫃交易  A
            // 073200*TXTYPE NOT=D AND CLIBR NOT=902  成立才寫檔
            // 073300     IF WK-CONVFILE(12:1) = "A"
            if ("A".equals(wkConvfile.substring(11, 12))) {
                // 073400        IF PUTF-TXTYPE = "D" OR "J" OR PUTF-CLLBR = "902"
                if ("D".equals(filePutf.getTxtype())
                        || "J".equals(filePutf.getTxtype())
                        || "902".equals(filePutf.getCllbr())) {
                    // 073500           GO TO   360019-FNEXT.
                    continue;
                }
            }
            // 073600* 網際網路交易  I
            // 073700*TXTYPE =D OR CLIBR=902  成立才寫檔  ，ＱＲＣＯＤＥ，Ｊ也算網路
            // 073800     IF WK-CONVFILE(12:1) = "I"
            if ("I".equals(wkConvfile.substring(11, 12))) {
                // 073900        IF  (PUTF-TXTYPE NOT = "D" AND PUTF-CLLBR NOT = "902")
                // 073950        AND (PUTF-TXTYPE NOT = "J")
                if (!"D".equals(filePutf.getTxtype())
                        && !"902".equals(filePutf.getCllbr())
                        && !"J".equals(filePutf.getTxtype())) {
                    // 074000           GO TO   360019-FNEXT.
                    continue;
                }
            }
            //// 搬PUTF...到36009...(RC=2)
            // 074100     MOVE       SPACES          TO      360019-REC.
            // 074200     MOVE       "2"             TO      360019-RC.
            // 074300     MOVE       PUTF-USERDATA   TO      WK-USERDATA.
            // 016500 01  WK-USERDATA.
            // 016600   03 WK-YYMM               PIC X(04).
            // 016700   03 WK-CHKDG              PIC X(02).
            // 016800   03 FILLER                PIC X(34).
            // 074400     ADD        1               TO      WK-TOTCNT.
            // 074500     ADD        PUTF-AMT        TO      WK-TOTAMT.
            // 074600     MOVE       PUTF-RCPTID(1:2)  TO      360019-BILL  .
            // 074700     MOVE       PUTF-RCPTID(3:4)  TO      360019-OFISNO.
            // 074800     MOVE       PUTF-RCPTID(7:10) TO      360019-TEL.
            _360019_Rc = "2";
            String wkUserdata = filePutf.getUserdata();
            wkYYMM = wkUserdata.substring(0, 4);
            wkChkdg = wkUserdata.substring(4, 6);

            wkTotcnt = wkTotcnt + 1;
            wkTotamt =
                    wkTotamt.add(
                            parse.string2BigDecimal(
                                    filePutf.getAmt().trim().isEmpty()
                                            ? "0"
                                            : filePutf.getAmt().trim()));
            _360019_Bill = filePutf.getRcptid().substring(0, 2);
            _360019_Ofisno = filePutf.getRcptid().substring(2, 6);
            _360019_Tel = filePutf.getRcptid().substring(6, 16);

            // 074900* 列帳年月小於 50 年，需變成 150 年
            // 075000     IF         WK-YYMM < "51"
            if (parse.string2Integer(wkYYMM.trim().isEmpty() ? "0" : wkYYMM) < 51) {
                // 075100         MOVE   "1"             TO      360019-YYMM(1:1)
                _360019_YYMM = "1";
            } else {
                // 075200     ELSE
                // 075300         MOVE   "0"             TO      360019-YYMM(1:1).
                _360019_YYMM = "0";
            }
            // 075400     MOVE       WK-YYMM         TO      360019-YYMM(2:4).
            _360019_YYMM += formatUtil.pad9(wkYYMM, 4);
            // 075500     IF         PUTF-LMTDATE >990101
            if (parse.string2Integer(
                            filePutf.getLmtdate().trim().isEmpty() ? "0" : filePutf.getLmtdate())
                    > 990101) {
                // 075600         MOVE   "0"             TO      360019-LMTDATE(1:1)
                _360019_Lmtdate = "0";
            } else {
                // 075700     ELSE
                // 075800         MOVE   "1"             TO      360019-LMTDATE(1:1).
                _360019_Lmtdate = "1";
            }
            // 075900     MOVE       PUTF-LMTDATE    TO      360019-LMTDATE(2:6).
            // 076000     MOVE       PUTF-AMT        TO      360019-AMT.
            // 076100     MOVE       WK-CHKDG        TO      360019-CHKDG.
            _360019_Lmtdate += filePutf.getLmtdate();
            _360019_Amt =
                    parse.string2BigDecimal(
                            filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt());
            _360019_Chkdg = wkChkdg;

            // 076200     IF         PUTF-DATE    >990101
            if (parse.string2Integer(
                            filePutf.getEntdy().trim().isEmpty() ? "0" : filePutf.getEntdy())
                    > 990101) {
                // 076300         MOVE   "0"             TO      360019-DATE(1:1)
                _360019_Date += "0";
            } else {
                // 076400     ELSE
                // 076500         MOVE   "1"             TO      360019-DATE(1:1).
                _360019_Date += "1";
            }
            // 076600     MOVE       PUTF-DATE       TO      360019-DATE(2:6).
            // 076700     MOVE       "2"             TO      360019-CHKSNO.
            // 076800     MOVE       PUTF-CLLBR      TO      WK-CLLBR.
            // 076900     MOVE       WK-CLBANK       TO      360019-CLBANK.
            // 077000     MOVE       "1"             TO      360019-BILLBHNO.
            _360019_Date += filePutf.getEntdy();
            _360019_Chksno = "2";
            wkCllbr = filePutf.getCllbr();
            wkClbank = "004" + formatUtil.pad9(wkCllbr, 3) + " ";
            _360019_Clbank = wkClbank;
            _360019_Billbhno = "1";

            // 077100* 電信別取帳單第一段條碼末三碼：
            // 077200*001: 實體帳單 ( 紙本帳單 ) ， 080: 手機條碼 ( 電子帳單 )
            // 077300     MOVE       PUTF-USERDATA(10:3)
            // 077400                                TO      360019-TELECOMID.

            _360019_Telecomid = filePutf.getUserdata().substring(9, 12);
            // 077500* 網路繳費方式固定放  I ，操作員代號固定放  TD
            // 077600* 臨櫃繳費方式固定放  A ，操作員代號固定放  2 個空白
            // 077700     IF         WK-CLLBR        =       "902"
            if ("902".equals(wkCllbr)) {
                // 077800        MOVE    "I"             TO      360019-KIND
                // 077900        MOVE    "TD"            TO      360019-OPNO
                _360019_Kind = "I";
                _360019_Opno = "TD";

            } else {
                // 078000     ELSE
                // 078100        MOVE    "A"             TO      360019-KIND
                // 078200        MOVE    SPACES          TO      360019-OPNO
                _360019_Kind = "A";
                _360019_Opno = "";
                // 078300     END-IF.
            }
            // 078400* 智慧繳費繳費方式固定放  Y  ，操作員代號固定放  TA
            // 078500     IF         WK-TXTYPE       =       "D"
            if ("D".equals(wkTxtype)) {
                // 078600        MOVE    "Y"             TO      360019-KIND
                // 078700        MOVE    "TA"            TO      360019-OPNO.
                _360019_Kind = "Y";
                _360019_Opno = "TA";
            }
            // 078720     IF  PUTF-TXTYPE = "J"
            if ("J".equals(filePutf.getTxtype())) {
                // 078740         MOVE   "1"    TO  360019-YYMM(1:1) 360019-LMTDATE(1:1)
                // 078750         MOVE   PUTF-USERDATA(21:4) TO      360019-YYMM(2:4)
                // 078755         MOVE   PUTF-USERDATA(27:6) TO      360019-LMTDATE(2:6)
                // 078760         MOVE   PUTF-USERDATA(25:2) TO      360019-CHKDG
                // 078770         MOVE   PUTF-USERDATA(33:3) TO      360019-TELECOMID
                // 078780         MOVE   PUTF-USERDATA(5:16) TO      360019-CARDNO(1:16)
                // 078790         MOVE    "N"                TO      360019-KIND
                // 078795         MOVE    "TD"               TO      360019-OPNO.
                _360019_YYMM = "1";
                _360019_Lmtdate = "1";
                _360019_YYMM += filePutf.getUserdata().substring(20, 24);
                _360019_Lmtdate += filePutf.getUserdata().substring(26, 32);
                _360019_Chkdg = filePutf.getUserdata().substring(24, 26);
                _360019_Telecomid = filePutf.getUserdata().substring(32, 35);
                _360019_Cardno = filePutf.getUserdata().substring(4, 20);
                _360019_Kind = "N";
                _360019_Opno = "TD";
            }
            // 078800* 實際核帳日期，持票據繳款會用到
            // 078900     IF         PUTF-SITDATE    =       0
            if (parse.string2Integer(
                            filePutf.getSitdate().trim().isEmpty() ? "0" : filePutf.getSitdate())
                    == 0) {
                // 079000        MOVE    360019-DATE     TO      360019-SITDATE
                _360019_Sitdate = _360019_Date;
            } else {
                // 079100     ELSE
                // 079200        IF      PUTF-SITDATE >990101
                if (parse.string2Integer(
                                filePutf.getSitdate().trim().isEmpty()
                                        ? "0"
                                        : filePutf.getSitdate())
                        > 990101) {
                    // 079300          MOVE   "0"             TO             360019-SITDATE(1:1)
                    // 079400          MOVE   PUTF-SITDATE    TO             360019-SITDATE(2:6)
                    _360019_Sitdate = "0";
                    _360019_Sitdate += filePutf.getSitdate();
                } else {
                    // 079500        ELSE
                    // 079600          MOVE   "1"             TO             360019-SITDATE(1:1)
                    // 079700          MOVE   PUTF-SITDATE    TO             360019-SITDATE(2:6).
                    _360019_Sitdate = "1";
                    _360019_Sitdate += filePutf.getSitdate();
                }
            }
            // 079800     WRITE      360019-REC.
            fileContents.add(_360019_Rec());

            // 079900     GO TO      360019-FNEXT.
        }
        // 080000 360019-FLAST.
        /// 搬LAST-REC(RC=3)
        // 080100     MOVE     SPACES          TO      360019-REC.
        // 080200     MOVE     "3"             TO      360019-RC.
        // 080300     MOVE     WK-TOTAMT       TO      360019-TOTAMT.
        // 080400     MOVE     WK-TOTCNT       TO      360019-TOTCNT.
        _360019_Rc = "3";
        _360019_Totamt = "" + wkTotamt;
        _360019_Totcnt = "" + wkTotcnt;
        // 080500     WRITE    360019-REC.
        fileContents.add(_360019_Rec());

        // 080600     CLOSE    FD-PUTF    WITH   SAVE.
        // 080700     CLOSE    FD-360019  WITH   SAVE.
        //        try {
        //            textFile.writeFileContent(wkConvdir, fileContents, CHARSET);
        //        } catch (LogicException e) {
        //            moveErrorResponse(e);
        //        }
        // 080800 360019-EXIT.
    }

    private void _0000_Init() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _0000_Init ....");
        // 069900  0000-INIT-RTN.
        //// 清變數
        // 070000     MOVE        0            TO     WK-TOTAMT-A,WK-TOTCNT-A.
        // 070100     MOVE        0            TO     WK-TOTAMT-I,WK-TOTCNT-I.
        // 070200     MOVE        0            TO     WK-TOTAMT-D,WK-TOTCNT-D.
        // 070250     MOVE        0            TO     WK-TOTAMT-J,WK-TOTCNT-J.
        // 070300     MOVE        0            TO     WK-TOTAMT  ,WK-TOTCNT.
        // 070400     MOVE        0            TO     WK-BRHFEE-A.
        // 070500     MOVE        0            TO     WK-BRHFEE-I.
        // 070600     MOVE        0            TO     WK-BRHFEE-D.
        // 070650     MOVE        0            TO     WK-BRHFEE-J.
        // 070700     MOVE        0            TO     WK-BRHFEE.
        wkTotamt_A = BigDecimal.ZERO;
        wkTotcnt_A = 0;
        wkTotamt_I = BigDecimal.ZERO;
        wkTotcnt_I = 0;
        wkTotamt_D = BigDecimal.ZERO;
        wkTotcnt_D = 0;
        wkTotamt_J = BigDecimal.ZERO;
        wkTotcnt_J = 0;
        wkTotamt = BigDecimal.ZERO;
        wkTotcnt = 0;
        wkBrhfee_A = BigDecimal.ZERO;
        wkBrhfee_I = BigDecimal.ZERO;
        wkBrhfee_D = BigDecimal.ZERO;
        wkBrhfee_J = BigDecimal.ZERO;
        wkBrhfee = BigDecimal.ZERO;
        // 070800  0000-INIT-EXIT.
    }

    private void _360019_Rpt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _360019_Rpt ....");
        // 081100 360019-RPT-RTN.
        //// 開啟檔案
        // 081200      OPEN       INPUT    FD-PUTF.
        // 081300 360019-RPT-NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到360019-RPT-LAST
        // 081400     READ   FD-PUTF     AT  END  GO TO  360019-RPT-LAST.
        if (!textFile.exists(wkPutdir)) {
            return;
        }
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            // 081500* 對帳媒體檔的代收筆數金額統計
            //// IF PUTF-CTL = 21,產生每月月底手續費筆數金額檔案
            // 081600     IF        PUTF-CTL             =      21
            if (parse.string2Integer(filePutf.getCtl().trim().isEmpty() ? "0" : filePutf.getCtl())
                    == 21) {
                // 081700       PERFORM    0000-MONTH-RTN  THRU   0000-MONTH-EXIT
                // 081800       GO TO   360019-RPT-NEXT
                _0000_Month();
                continue;
                // 081900     END-IF.
            }
            // 082000*C026-1 報表只提供給營業部，代收類別：３６００１９
            // 082100     IF        PUTF-CODE          NOT =   "360019"
            if (!"360019".equals(filePutf.getCode())) {
                // 082200       GO TO   360019-RPT-NEXT
                continue;
                // 082300     END-IF.
            }
            // 082400     IF        PUTF-CTL           NOT =      11
            if (!"11".equals(filePutf.getCtl())) {
                // 082500       GO TO   360019-RPT-NEXT
                continue;
                // 082600     END-IF.
            }
            // 082700     IF        PUTF-CLLBR             =      902
            if ("902".equals(filePutf.getCllbr())) {
                // 082800       ADD        1               TO      WK-TOTCNT-I
                // 082900       ADD        PUTF-AMT        TO      WK-TOTAMT-I
                wkTotcnt_I = wkTotcnt_I + 1;
                wkTotamt_I =
                        wkTotamt_I.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
            } else {

                // 083000     ELSE
                // 083100       ADD        1               TO      WK-TOTCNT-A
                // 083200       ADD        PUTF-AMT        TO      WK-TOTAMT-A
                wkTotcnt_A = wkTotcnt_A + 1;
                wkTotamt_A =
                        wkTotamt_A.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 083300     END-IF.

            }
            // 083400     GO TO   360019-RPT-NEXT.
            continue;
        }
        // 083500 360019-RPT-LAST.
        // 083600**FNBSDY= 當月月底日就出 C026-2 及 C026-3 報表
        // 083700     IF       FD-BHDATE-TBSDY  =      FD-BHDATE-FNBSDY
        if (parse.string2Integer(tbsdy) == fnbsdy) {
            // 083800        MOVE    PUTF-BDATE           TO      WK-BDATE
            // 083900        MOVE    PUTF-EDATE           TO      WK-EDATE
            // 084000        PERFORM  010-REPORT-RTN  THRU 010-REPORT-EXIT
            wkBdate =
                    parse.string2Integer(
                            filePutf.getRcptid().substring(0, 6).trim().isEmpty()
                                    ? "0"
                                    : filePutf.getRcptid().substring(0, 6));
            wkEdate =
                    parse.string2Integer(
                            filePutf.getRcptid().substring(6, 12).trim().isEmpty()
                                    ? "0"
                                    : filePutf.getRcptid().substring(6, 12));
            _010_Report();
        } else {
            // 084100     ELSE
            // 084200        MOVE       0        TO     WK-MONTHCNT-A,WK-MONTHAMT-A
            // 084300        MOVE       0        TO     WK-MONTHCNT-I,WK-MONTHAMT-I
            // 084400        MOVE       0        TO     WK-MONTHCNT-D,WK-MONTHAMT-D
            // 084450        MOVE       0        TO     WK-MONTHCNT-J,WK-MONTHAMT-J
            // 084500        MOVE       0        TO     WK-MONTHCNT  ,WK-MONTHAMT
            wkMonthcnt_A = 0;
            wkMonthamt_A = BigDecimal.ZERO;
            wkMonthcnt_I = 0;
            wkMonthamt_I = BigDecimal.ZERO;
            wkMonthcnt_D = 0;
            wkMonthamt_D = BigDecimal.ZERO;
            wkMonthcnt_J = 0;
            wkMonthamt_J = BigDecimal.ZERO;
            wkMonthcnt = 0;
            wkMonthamt = BigDecimal.ZERO;
            // 084600     END-IF.
        }
        // 084700     PERFORM  000-REPORT-RTN    THRU  000-REPORT-EXIT.
        _000_Report();
        // 084800     CLOSE    FD-PUTF    WITH   SAVE.
        // 084900 360019-RPT-EXIT.
    }

    private void _360019_Rpt5() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _360019_Rpt5 ....");
        // 089000 360019-RPT5-RTN.
        //// 開啟檔案

        if (!textFile.exists(wkPutdir)) {
            return;
        }
        // 089100      OPEN       INPUT    FD-PUTF.
        // 089200 360019-RPT5-NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到320019-RPT-LAST
        // 089300     READ   FD-PUTF     AT  END  GO TO  360019-RPT5-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            // 089400*C026-5 報表提供給中華電信，代收類別： 360019&320019&520010
            // 089500* 增加小計 ( 臨櫃 + 智慧繳費 ) 及小計 ( 網際網路 ) 筆數金額
            // 089600     IF        PUTF-CTL           NOT   =      11
            if (!"11".equals(filePutf.getCtl())) {
                // 089700       GO TO   360019-RPT5-NEXT
                continue;
                // 089800     END-IF.
            }
            // 089900     IF        PUTF-TXTYPE            =      "D"
            if ("D".equals(filePutf.getTxtype())) {
                // 090000       ADD        1               TO      WK-TOTCNT-D
                // 090100       ADD        PUTF-AMT        TO      WK-TOTAMT-D
                wkTotcnt_D = wkTotcnt_D + 1;
                wkTotamt_D =
                        wkTotamt_D.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 090200     ELSE IF   PUTF-TXTYPE            =      "J"
            } else if ("J".equals(filePutf.getTxtype())) {
                // 090220       ADD        1               TO      WK-TOTCNT-J
                // 090240       ADD        PUTF-AMT        TO      WK-TOTAMT-J
                wkTotcnt_J = wkTotcnt_J + 1;
                wkTotamt_J =
                        wkTotamt_J.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
            } else {
                // 090300     ELSE IF      PUTF-CLLBR            =      902
                if ("902".equals(filePutf.getCllbr())) {
                    // 090400           ADD        1               TO      WK-TOTCNT-I
                    // 090500           ADD        PUTF-AMT        TO      WK-TOTAMT-I
                    wkTotcnt_I = wkTotcnt_I + 1;
                    wkTotamt_I =
                            wkTotamt_I.add(
                                    parse.string2BigDecimal(
                                            filePutf.getAmt().trim().isEmpty()
                                                    ? "0"
                                                    : filePutf.getAmt()));
                } else {
                    // 090600         ELSE
                    // 090700           ADD        1               TO      WK-TOTCNT-A
                    // 090800           ADD        PUTF-AMT        TO      WK-TOTAMT-A
                    wkTotcnt_A = wkTotcnt_A + 1;
                    wkTotamt_A =
                            wkTotamt_A.add(
                                    parse.string2BigDecimal(
                                            filePutf.getAmt().trim().isEmpty()
                                                    ? "0"
                                                    : filePutf.getAmt()));
                    // 090900         END-IF
                }
                // 091000     END-IF.
            }
            // 091100     GO TO   360019-RPT5-NEXT.
        }
        // 091200 360019-RPT5-LAST.
        // 091300     PERFORM  050-REPORT-RTN    THRU  050-REPORT-EXIT.
        _050_Report();
        // 091400     CLOSE    FD-PUTF    WITH   SAVE.
        // 091500 360019-RPT5-EXIT.
    }

    private void _320019_Rpt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _320019_Rpt ....");
        // 085200 320019-RPT-RTN.

        if (!textFile.exists(wkPutdir)) {
            return;
        }
        //// 開啟檔案
        // 085300      OPEN       INPUT    FD-PUTF.
        // 085400 320019-RPT-NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到320019-RPT-LAST
        // 085500     READ   FD-PUTF     AT  END  GO TO  320019-RPT-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            // 085600* 對帳媒體檔的代收筆數金額統計
            // 085700     IF        PUTF-CTL             =      21
            if ("21".equals(filePutf.getCtl())) {
                // 085800       PERFORM    0001-MONTH-RTN  THRU   0001-MONTH-EXIT
                _0001_Month();
                // 085900       GO TO   320019-RPT-NEXT
                continue;
                // 086000     END-IF.
            }
            // 086100*C026-4 報表只提供給信義分行，代收類別： 302219,520010
            // 086200     IF  PUTF-CODE NOT = "320019" AND PUTF-CODE NOT = "520010"
            if (!"320019".equals(filePutf.getCode()) && !"520010".equals(filePutf.getCode())) {
                // 086300       GO TO   320019-RPT-NEXT
                continue;
                // 086400     END-IF.
            }
            // 086500     IF        PUTF-CTL           NOT   =      11
            if (!"11".equals(filePutf.getCtl())) {
                // 086600       GO TO   320019-RPT-NEXT
                continue;
                // 086700     END-IF.
            }
            // 086800     IF        PUTF-TXTYPE            =      "D"
            if ("D".equals(filePutf.getTxtype())) {
                // 086900       ADD        1               TO      WK-TOTCNT-D
                // 087000       ADD        PUTF-AMT        TO      WK-TOTAMT-D
                wkTotcnt_D = wkTotcnt_D + 1;
                wkTotamt_D =
                        wkTotamt_D.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 087100     END-IF.
            }
            // 087120     IF        PUTF-TXTYPE            =      "J"
            if ("J".equals(filePutf.getTxtype())) {
                // 087140       ADD        1               TO      WK-TOTCNT-J
                // 087160       ADD        PUTF-AMT        TO      WK-TOTAMT-J
                wkTotcnt_J = wkTotcnt_J + 1;
                wkTotamt_J =
                        wkTotamt_J.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 087180     END-IF.
            }
            // 087200     GO TO   320019-RPT-NEXT.
        }
        // 087300 320019-RPT-LAST.
        // 087400**FNBSDY= 當月月底日就出 C026-6 及 C026-7 報表
        // 087500     IF       FD-BHDATE-TBSDY  =      FD-BHDATE-FNBSDY
        if (parse.string2Integer(tbsdy) == fnbsdy) {
            // 087600        MOVE    PUTF-BDATE           TO      WK-BDATE
            // 087700        MOVE    PUTF-EDATE           TO      WK-EDATE
            wkBdate =
                    parse.string2Integer(
                            filePutf.getRcptid().substring(0, 6).trim().isEmpty()
                                    ? "0"
                                    : filePutf.getRcptid().substring(0, 6));
            wkEdate =
                    parse.string2Integer(
                            filePutf.getRcptid().substring(6, 12).trim().isEmpty()
                                    ? "0"
                                    : filePutf.getRcptid().substring(6, 12));
            // 087800        PERFORM  020-REPORT-RTN  THRU 020-REPORT-EXIT
            _020_Report();
        } else {
            // 087900     ELSE
            // 088000        MOVE       0        TO     WK-MONTHCNT-A,WK-MONTHAMT-A
            // 088100        MOVE       0        TO     WK-MONTHCNT-I,WK-MONTHAMT-I
            // 088200        MOVE       0        TO     WK-MONTHCNT-D,WK-MONTHAMT-D
            // 088300        MOVE       0        TO     WK-MONTHCNT  ,WK-MONTHAMT
            // 088350        MOVE       0        TO     WK-MONTHCNT-J,WK-MONTHAMT-J
            wkMonthcnt_A = 0;
            wkMonthamt_A = BigDecimal.ZERO;
            wkMonthcnt_I = 0;
            wkMonthamt_I = BigDecimal.ZERO;
            wkMonthcnt_D = 0;
            wkMonthamt_D = BigDecimal.ZERO;
            wkMonthcnt = 0;
            wkMonthamt = BigDecimal.ZERO;
            wkMonthcnt_J = 0;
            wkMonthamt_J = BigDecimal.ZERO;
            // 088400     END-IF.
        }
        // 088500     PERFORM  040-REPORT-RTN    THRU  040-REPORT-EXIT.
        _040_Report();
        // 088600     CLOSE    FD-PUTF    WITH   SAVE.
        // 088700 320019-RPT-EXIT.
    }

    private void _0001_Month() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _0001_Month ....");
        // 134800 0001-MONTH-RTN.
        // 134900   IF        PUTF-CTL            =      21
        if ("21".equals(filePutf.getCtl())) {
            // 135000       ADD        1               TO      WK-MONTHCNT
            // 135100       ADD        PUTF-AMT        TO      WK-MONTHAMT
            wkMonthcnt = wkMonthcnt + 1;
            wkMonthamt =
                    wkMonthamt.add(
                            parse.string2BigDecimal(
                                    filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt()));
            // 135200       IF        PUTF-TXTYPE            =      "D"
            if ("D".equals(filePutf.getTxtype())) {
                // 135300            ADD        1               TO      WK-MONTHCNT-D
                // 135400            ADD        PUTF-AMT        TO      WK-MONTHAMT-D
                wkMonthcnt_D = wkMonthcnt_D + 1;
                wkMonthamt_D =
                        wkMonthamt_D.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 135500       END-IF
            }
            // 135520       IF        PUTF-TXTYPE            =      "J"
            if ("J".equals(filePutf.getTxtype())) {
                // 135540            ADD        1               TO      WK-MONTHCNT-J
                // 135560            ADD        PUTF-AMT        TO      WK-MONTHAMT-J
                wkMonthcnt_J = wkMonthcnt_J + 1;
                wkMonthamt_J =
                        wkMonthamt_J.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 135580       END-IF
            }
            // 135600   END-IF.
        }
        // 135700 0001-MONTH-EXIT.
    }

    private void _020_Report() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _020_Report ....");
        // 105500 020-REPORT-RTN.
        //// 寫REPORTFL6,7,報表

        // 105600    OPEN  OUTPUT   REPORTFL6.
        // 105700    OPEN  OUTPUT   REPORTFL7.
        // 105800    IF    WK-BDATE   <  990101
        if (wkBdate < 990101) {
            // 105900          ADD        1000000 TO   WK-BDATE.
            wkBdate = wkBdate + 1000000;
        }
        // 106000    IF    WK-EDATE   <  990101
        if (wkEdate < 990101) {
            // 106100          ADD        1000000 TO   WK-EDATE.
            wkEdate = wkEdate + 1000000;
        }

        // 106200    MOVE     WK-BDATE                TO       360019-BDATE-P.
        // 106300    MOVE     WK-EDATE                TO       360019-EDATE-P.
        // 106400    COMPUTE  360019-TOTCNT-MONTH = WK-MONTHCNT-D + WK-MONTHCNT-J.
        // 106500    COMPUTE  360019-TOTAMT-MONTH = WK-MONTHAMT-D + WK-MONTHAMT-J.
        _360019_Bdate_P = "" + wkBdate;
        _360019_Edate_P = "" + wkEdate;
        _360019_Totcnt_Month = "" + wkMonthcnt_D + wkMonthcnt_J;
        _360019_Totamt_Month = "" + wkMonthamt_D.add(wkMonthamt_J);

        // 106600     MOVE  FD-BHDATE-NBSYY   TO  WK-LINE8-YY.
        // 106700     MOVE  FD-BHDATE-NBSMM   TO  WK-LINE8-MM.
        // 106800     MOVE  FD-BHDATE-NBSDD   TO  WK-LINE8-DD.
        // 106900     MOVE  FD-BHDATE-TBSYY   TO  WK-LINE9-YY.
        // 107000     MOVE  FD-BHDATE-TBSMM   TO  WK-LINE9-MM.
        // 107100     MOVE  WK-CHIN-NUM-REC   TO  WK-CHIN-NUM-GROUP.
        // 107200     MOVE  WK-CHIN-UNIT-REC  TO  WK-CHIN-UNIT-GROUP.
        // 107300     MOVE  WK-TRAIL-REC      TO  WK-TRAIL-GROUP.
        // 107400     MOVE  WK-NT-REC         TO  WK-NT-GROUP.
        // 107600     MOVE  WK-PGCNT          TO  WK-HEAD1-PAGE.
        //        wkPgcnt;
        // 107700     MOVE  "054"             TO  WK-HEAD1-BRNO.
        // 107800     MOVE  "7"               TO  WK-HEAD1-FORM-R.
        wk_Head1_Brno = "054";
        wk_Head1_Form_R = "7";
        // 107900    MOVE     SPACES                  TO       REPORT6-LINE.
        // 108000    WRITE    REPORT6-LINE         AFTER       PAGE.
        reportContents_6.add(PAGE_SEPARATOR);
        // 108100    MOVE     SPACES                  TO       REPORT6-LINE.
        // 108200    WRITE    REPORT6-LINE          FROM       360019-TIT21.
        reportContents_6.add(_360019_Tit21());
        // 108300    MOVE     SPACES                  TO       REPORT6-LINE.
        // 108400    MOVE     "054"                   TO       360019-BRNO-R.
        // 108500    MOVE     "6"                     TO       360019-FORM-R.
        _360019_Brno_R = "054";
        _360019_Form_R = "6";
        // 108600    WRITE    REPORT6-LINE          FROM       360019-TIT2.
        reportContents_6.add(_360019_Tit2());

        // 108700    MOVE     SPACES                  TO       REPORT6-LINE.
        // 108800    MOVE     WK-YYMM-1               TO       360019-YYMM-P.
        _360019_YYMM_P = wkYYMM_1;
        // 108900    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 109000    WRITE    REPORT6-LINE          FROM       360019-TIT23.
        reportContents_6.add(_360019_Tit23());

        // 109100    MOVE     SPACES                  TO       REPORT6-LINE.
        // 109200    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 109300    MOVE     SPACES                  TO       REPORT6-LINE.
        // 109400    WRITE    REPORT6-LINE          FROM       360019-TIT24.
        reportContents_6.add(_360019_Tit24());
        // 109500    MOVE     SPACES                  TO       REPORT6-LINE.
        // 109600    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 109700    WRITE    REPORT6-LINE          FROM       360019-TIT25.
        reportContents_6.add(_360019_Tit25());
        // 109800    MOVE     SPACES                  TO       REPORT6-LINE.
        // 109900    WRITE    REPORT6-LINE          FROM       360019-SEP.
        reportContents_6.add(_360019_Sep());
        // 110000    MOVE     SPACES                  TO       REPORT6-LINE.
        // 110100    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 110200    MOVE     " 中華電信股份有限公司 "  TO   360019-CNAME-MONTH-D.
        _360019_Cname_Month_D = " 中華電信股份有限公司 ";
        // 110300    MOVE     " 中華電信股份有限公司北區分公司 "
        // 110400                                     TO   WK-LINE6-OFISNM.
        wk_Line6_Ofisnm = " 中華電信股份有限公司北區分公司 ";
        // 110500    MOVE     " 智慧繳費 "              TO   360019-TXTYPE-MONTH-D.
        _360019_Txtype_Month_D = " 智慧繳費 ";
        // 110600    MOVE     WK-MONTHCNT-D           TO     360019-BRHCNT-MONTH-D.
        _360019_Brhcnt_Month_D = wkMonthcnt_D;
        // 110700    MOVE     WK-MONTHAMT-D           TO     360019-BRHAMT-MONTH-D.
        _360019_Brhamt_Month_D = wkMonthamt_D;
        // 110800    COMPUTE  WK-BRHFEE-D    =     WK-MONTHCNT-D * 3 .
        // 111000    MOVE     WK-BRHFEE-D             TO     360019-BRHFEE-MONTH-D.
        wkBrhfee_D = parse.string2BigDecimal("" + wkMonthcnt_D * 3);
        _360019_Brhfee_Month_D = "" + wkMonthcnt_D * 3;
        // 111300    COMPUTE  WK-TOTFEE        =   WK-BRHFEE-D.
        wkTotfee = wkBrhfee_D;
        // 111500    MOVE     "054"                   TO     WK-BRNO.
        wkBrno = "054";
        // 111700    WRITE    REPORT6-LINE          FROM       360019-DTL-MONTH-D.
        reportContents_6.add(_360019_Dtl_Month_D());

        // 111800    MOVE     SPACES                  TO       REPORT6-LINE.
        // 111900    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 111910* 台灣 PAY
        // 111920    MOVE     " 台灣ＰＡＹ "          TO     360019-TXTYPE-MONTH-D.
        _360019_Txtype_Month_D = " 台灣ＰＡＹ ";
        // 111930    MOVE     WK-MONTHCNT-J           TO     360019-BRHCNT-MONTH-D.
        _360019_Brhcnt_Month_D = wkMonthcnt_J;
        // 111940    MOVE     WK-MONTHAMT-J           TO     360019-BRHAMT-MONTH-D.
        _360019_Brhamt_Month_D = wkMonthamt_J;
        // 111950    COMPUTE  WK-BRHFEE-J    =     WK-MONTHCNT-J * 3 .
        wkBrhfee_J = parse.string2BigDecimal("" + wkMonthcnt_J * 3);
        // 111955    COMPUTE 360019-FEEAMT-MONTH =  WK-BRHFEE-D + WK-BRHFEE-J.
        _360019_Feeamt_Month = "" + wkBrhfee_D.add(wkBrhfee_J);
        // 111960    MOVE     WK-BRHFEE-J             TO     360019-BRHFEE-MONTH-D.
        _360019_Brhfee_Month_D = "" + wkBrhfee_J;
        // 111970    WRITE    REPORT6-LINE          FROM       360019-DTL-MONTH-D.
        reportContents_6.add(_360019_Dtl_Month_D());

        // 111980    PERFORM 2000-CNV-CHIN-NUM-RTN THRU 2000-EXIT.
        _2000_Cnv_Chin_Num();

        // 112000    WRITE    REPORT6-LINE          FROM       360019-SEP.
        reportContents_6.add(_360019_Sep());

        // 112100    MOVE     SPACES                  TO       REPORT6-LINE.
        // 112200    WRITE    REPORT6-LINE         AFTER       1.
        reportContents_6.add("");
        // 112300    WRITE    REPORT6-LINE          FROM       360019-TOT-MONTH.
        reportContents_6.add(_360019_Tot_Month());

        // 112400    CLOSE    REPORTFL6  WITH SAVE.
        // 112500    CLOSE    REPORTFL7  WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath_6, reportContents_6, CHARSET_BIG5);
            upload(reportFilePath_6, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(reportFilePath_7, reportContents_7, CHARSET_BIG5);
            upload(reportFilePath_7, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 112600 020-REPORT-EXIT.
    }

    private void _040_Report() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _040_Report ....");
        // 112900 040-REPORT-RTN.
        //// 寫REPORTFL4報表

        // 113000    OPEN  OUTPUT   REPORTFL4.
        // 113100    MOVE     SPACES                  TO       REPORT4-LINE.
        // 113200    WRITE    REPORT4-LINE         AFTER       PAGE.
        reportContents_4.add(PAGE_SEPARATOR);

        // 113300    MOVE     SPACES                  TO       REPORT4-LINE.
        // 113400    WRITE    REPORT4-LINE          FROM       360019-TIT1.
        reportContents_4.add(_360019_Tit1());

        // 113500    MOVE     SPACES                  TO       REPORT4-LINE.
        // 113600    MOVE     "4"                     TO       360019-FORM-R.
        // 113700    MOVE     SPACES                  TO       REPORT4-LINE.
        // 113800    WRITE    REPORT4-LINE         AFTER       1.
        // 113900    WRITE    REPORT4-LINE          FROM       360019-TIT3.
        _360019_Form_R = "4";
        reportContents_4.add("");
        reportContents_4.add(_360019_Tit3());

        // 114000    MOVE     SPACES                  TO       REPORT4-LINE.
        // 114100    WRITE    REPORT4-LINE         AFTER       1.
        // 114200    MOVE     SPACES                  TO       REPORT4-LINE.
        // 114300    WRITE    REPORT4-LINE          FROM       360019-TIT4.
        reportContents_4.add("");
        reportContents_4.add(_360019_Tit4());

        // 114400    MOVE     SPACES                  TO       REPORT4-LINE.
        // 114500    WRITE    REPORT4-LINE         AFTER       1.
        // 114600    WRITE    REPORT4-LINE          FROM       360019-TIT5.
        reportContents_4.add("");
        reportContents_4.add(_360019_Tit5());

        // 114700    MOVE     SPACES                  TO       REPORT4-LINE.
        // 114800    WRITE    REPORT4-LINE          FROM       360019-SEP.
        reportContents_4.add(_360019_Sep());

        // 114900    MOVE     SPACES                  TO       REPORT4-LINE.
        // 115000* 智慧繳費
        // 115100    WRITE    REPORT4-LINE         AFTER       1.
        // 115200    MOVE     " 中華電信股份有限公司 "   TO    360019-CNAME-DR.
        // 115300    MOVE     " 智慧繳費 "               TO       360019-TXTYPE-DR.
        // 115400    MOVE     WK-TOTCNT-D             TO       360019-BRHCNT-DR.
        // 115500    MOVE     WK-TOTAMT-D             TO       360019-BRHAMT-DR.
        // 115600    COMPUTE  WK-BRHFEE-D     = WK-MONTHCNT-D * 3 .
        // 115700    COMPUTE  360019-ACAMT-DR = WK-TOTAMT-D - WK-BRHFEE-D.
        // 115800    WRITE    REPORT4-LINE          FROM       360019-DTL3.
        reportContents_4.add("");
        _360019_Cname_Dr = " 中華電信股份有限公司 ";
        _360019_Txtype_Dr = " 智慧繳費 ";
        _360019_Brhcnt_Dr = wkTotcnt_D;
        _360019_Brhamt_Dr = wkTotamt_D;
        wkBrhfee_D = parse.string2BigDecimal("" + wkMonthcnt_D * 3);
        _360019_Acamt_Dr = wkTotamt_D.subtract(wkBrhfee_D);
        reportContents_4.add(_360019_Dtl3());

        // 115900    MOVE     SPACES                  TO       REPORT4-LINE.
        // 116000    WRITE    REPORT4-LINE         AFTER       1.
        // 116100    WRITE    REPORT4-LINE          FROM       360019-SEP.
        reportContents_4.add("");
        reportContents_4.add(_360019_Sep());

        // 116200    MOVE     SPACES                  TO       REPORT4-LINE.
        // 116300* 總計
        // 116400    WRITE    REPORT4-LINE         AFTER       1.
        // 116500    COMPUTE  WK-TOTCNT       = WK-TOTCNT-D .
        // 116600    COMPUTE  WK-TOTAMT       = WK-TOTAMT-D .
        // 116700    MOVE     WK-TOTCNT               TO       360019-TOTCNT-R.
        // 116800    MOVE     WK-TOTAMT               TO       360019-TOTAMT-R.
        // 116900    COMPUTE 360019-TOTACAMT-R = ( WK-TOTAMT-D - WK-BRHFEE-D ) .
        // 117000    WRITE    REPORT4-LINE          FROM       360019-TOT.
        wkTotcnt = wkTotcnt_D;
        wkTotamt = wkTotamt_D;
        _360019_Totcnt_R = wkTotcnt;
        _360019_Totamt_R = wkTotamt;
        _360019_Totacamt_R = wkTotamt_D.subtract(wkBrhfee_D);
        reportContents_4.add("");
        reportContents_4.add(_360019_Tot());

        // 117100    CLOSE    REPORTFL4  WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath_4, reportContents_4, CHARSET_BIG5);
            upload(reportFilePath_4, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 117200 040-REPORT-EXIT.
    }

    private void _050_Report() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _050_Report ....");
        // 117500 050-REPORT-RTN.
        //// 寫REPORTFL5報表

        // 117600    OPEN  OUTPUT   REPORTFL5.
        // 117700    MOVE     SPACES                  TO       REPORT5-LINE.
        // 117800    WRITE    REPORT5-LINE         AFTER       PAGE.
        reportContents_5.add(PAGE_SEPARATOR);

        // 117900    MOVE     SPACES                  TO       REPORT5-LINE.
        // 118000    WRITE    REPORT5-LINE          FROM       320019-TIT1.
        reportContents_5.add(_320019_Tit1());

        // 118100    MOVE     SPACES                  TO       REPORT5-LINE.
        // 118200    MOVE     "1"                     TO       320019-FORM-R.
        // 118300    MOVE     SPACES                  TO       REPORT5-LINE.
        // 118400    WRITE    REPORT5-LINE         AFTER       1.
        // 118500    WRITE    REPORT5-LINE          FROM       320019-TIT3.
        _320019_Form_R = "1";
        reportContents_5.add("");
        reportContents_5.add(_320019_Tit3());

        // 118600    MOVE     SPACES                  TO       REPORT5-LINE.
        // 118700    WRITE    REPORT5-LINE         AFTER       1.
        // 118800    MOVE     SPACES                  TO       REPORT5-LINE.
        // 118900    WRITE    REPORT5-LINE          FROM       320019-TIT4.
        reportContents_5.add("");
        reportContents_5.add(_320019_Tit4());

        // 119000    MOVE     SPACES                  TO       REPORT5-LINE.
        // 119100    WRITE    REPORT5-LINE         AFTER       1.
        // 119200    WRITE    REPORT5-LINE          FROM       320019-TIT5.
        reportContents_5.add("");
        reportContents_5.add(_320019_Tit5());

        // 119300    MOVE     SPACES                  TO       REPORT5-LINE.
        // 119400    WRITE    REPORT5-LINE          FROM       360019-SEP.
        reportContents_5.add(_360019_Sep());

        // 119500    MOVE     SPACES                  TO       REPORT5-LINE.
        // 119600* 臨櫃
        // 119700    WRITE    REPORT5-LINE         AFTER       1.
        // 119800    MOVE     WK-TOTCNT-A             TO       320019-BRHCNT-AR.
        // 119900    MOVE     WK-TOTAMT-A             TO       320019-BRHAMT-AR.
        // 120000    COMPUTE  WK-BRHFEE-A     = WK-MONTHCNT-A * 3 .
        // 120100    COMPUTE  320019-ACAMT-AR = WK-TOTAMT-A - WK-BRHFEE-A.
        // 120200    WRITE    REPORT5-LINE          FROM       320019-DTL1.
        reportContents_5.add("");
        _320019_Brhcnt_Ar = wkTotcnt_A;
        _320019_Brhamt_Ar = wkTotamt_A;
        wkBrhfee_A = parse.string2BigDecimal("" + wkMonthcnt_A * 3);
        _320019_Acamt_Ar = wkTotamt_A.subtract(wkBrhfee_A);
        reportContents_5.add(_320019_Dtl1());

        // 120300    MOVE     SPACES                  TO       REPORT5-LINE.
        // 120400* 網際網路
        // 120500    WRITE    REPORT5-LINE         AFTER       1.
        // 120600    MOVE     WK-TOTCNT-I             TO       320019-BRHCNT-IR.
        // 120700    MOVE     WK-TOTAMT-I             TO       320019-BRHAMT-IR.
        // 120800    COMPUTE  WK-BRHFEE-I     = WK-MONTHCNT-I * 5 / 2.
        // 120900    COMPUTE  320019-ACAMT-IR = WK-TOTAMT-I - WK-BRHFEE-I.
        // 121000    WRITE    REPORT5-LINE          FROM       320019-DTL2.
        reportContents_5.add("");
        _320019_Brhcnt_Ir = wkTotcnt_I;
        _320019_Brhamt_Ir = wkTotamt_I;
        wkBrhfee_I = parse.string2BigDecimal("" + (wkMonthcnt_I * 5) / 2);
        _320019_Acamt_Ir = wkTotamt_I.subtract(wkBrhfee_I);
        reportContents_5.add(_320019_Dtl2());

        // 121100    MOVE     SPACES                  TO       REPORT5-LINE.
        // 121200* 智慧繳費
        // 121300    WRITE    REPORT5-LINE         AFTER       1.
        // 121400    MOVE     WK-TOTCNT-D             TO       320019-BRHCNT-DR.
        // 121500    MOVE     WK-TOTAMT-D             TO       320019-BRHAMT-DR.
        // 121600    COMPUTE  WK-BRHFEE-D     = WK-MONTHCNT-D * 3 .
        // 121700    COMPUTE  320019-ACAMT-DR = WK-TOTAMT-D - WK-BRHFEE-D.
        // 121800    WRITE    REPORT5-LINE          FROM       320019-DTL3.
        reportContents_5.add("");
        _320019_Brhcnt_Dr = wkTotcnt_D;
        _320019_Brhamt_Dr = wkTotamt_D;
        wkBrhfee_D = parse.string2BigDecimal("" + wkMonthcnt_D * 3);
        _320019_Acamt_Dr = wkTotamt_D.subtract(wkBrhfee_D);
        reportContents_5.add(_320019_Dtl3());

        // 121900    MOVE     SPACES                  TO       REPORT5-LINE.
        // 121910* 台灣ＰＡＹ
        // 121920    WRITE    REPORT5-LINE         AFTER       1.
        // 121930    MOVE     WK-TOTCNT-J             TO       320019-BRHCNT-JR.
        // 121940    MOVE     WK-TOTAMT-J             TO       320019-BRHAMT-JR.
        // 121950    COMPUTE  WK-BRHFEE-J     = WK-MONTHCNT-J * 3 .
        // 121960    COMPUTE  320019-ACAMT-JR = WK-TOTAMT-J - WK-BRHFEE-J.
        // 121970    WRITE    REPORT5-LINE          FROM       320019-DTL4.
        reportContents_5.add("");
        _320019_Brhcnt_Jr = wkTotcnt_J;
        _320019_Brhamt_Jr = wkTotamt_J;
        wkBrhfee_J = parse.string2BigDecimal("" + wkMonthcnt_J * 3);
        _320019_Acamt_Jr = wkTotamt_J.subtract(wkBrhfee_J);
        reportContents_5.add(_320019_Dtl4());

        // 121980    MOVE     SPACES                  TO       REPORT5-LINE.
        // 122000* 小計：臨櫃 ( 業務代碼 A)
        // 122100    WRITE    REPORT5-LINE         AFTER       1.
        // 122200    WRITE    REPORT5-LINE          FROM       360019-SEP.
        reportContents_5.add("");
        reportContents_5.add(_360019_Sep());

        // 122300    MOVE     SPACES                  TO       REPORT5-LINE.
        // 122400    WRITE    REPORT5-LINE         AFTER       1.
        // 122500    COMPUTE  WK-TOTCNT       = WK-TOTCNT-A.
        // 122600    COMPUTE  WK-TOTAMT       = WK-TOTAMT-A.
        // 122700    MOVE     WK-TOTCNT               TO       320019-TOTCNT-R1.
        // 122800    MOVE     WK-TOTAMT               TO       320019-TOTAMT-R1.
        // 122900    COMPUTE 320019-TOTACAMT-R1 = ( WK-TOTAMT-A - WK-BRHFEE-A ).
        // 123000    WRITE    REPORT5-LINE          FROM       320019-TOT1.
        reportContents_5.add("");
        wkTotcnt = wkTotcnt_A;
        wkTotamt = wkTotamt_A;
        _320019_Totcnt_R1 = wkTotcnt;
        _320019_Totamt_R1 = wkTotamt;
        _320019_Totacamt_R1 = wkTotamt_A.subtract(wkBrhfee_A);
        reportContents_5.add(_320019_Tot1());

        // 123100    MOVE     SPACES                  TO       REPORT5-LINE.
        // 123200    WRITE    REPORT5-LINE         AFTER       1.
        // 123300    WRITE    REPORT5-LINE          FROM       360019-SEP.
        reportContents_5.add("");
        reportContents_5.add(_360019_Sep());

        // 123400    MOVE     SPACES                  TO       REPORT5-LINE.
        // 123500* 小計：網際網路、智慧繳費、台灣ＰＡＹ
        // 123600    WRITE    REPORT5-LINE         AFTER       1.
        // 123700    COMPUTE  WK-TOTCNT  = WK-TOTCNT-I + WK-TOTCNT-D + WK-TOTCNT-J.
        // 123800    COMPUTE  WK-TOTAMT  = WK-TOTAMT-I + WK-TOTAMT-D + WK-TOTAMT-J.
        // 123900    MOVE     WK-TOTCNT               TO       320019-TOTCNT-R2.
        // 124000    MOVE     WK-TOTAMT               TO       320019-TOTAMT-R2.
        // 124100    COMPUTE 320019-TOTACAMT-R2 = ( WK-TOTAMT-I - WK-BRHFEE-I ) +
        // 124200    (WK-TOTAMT-J - WK-BRHFEE-J)+ ( WK-TOTAMT-D - WK-BRHFEE-D ).
        // 124300    WRITE    REPORT5-LINE          FROM       320019-TOT2.
        reportContents_5.add("");
        wkTotcnt = wkTotcnt_I + wkTotcnt_D + wkTotcnt_J;
        wkTotamt = wkTotamt_I.add(wkTotamt_D).add(wkTotamt_J);
        _320019_Totcnt_R2 = wkTotcnt;
        _320019_Totamt_R2 = wkTotamt;
        _320019_Totacamt_R2 =
                wkTotamt_I
                        .subtract(wkBrhfee_I)
                        .add(wkTotamt_J.subtract(wkBrhfee_J))
                        .add(wkTotamt_D.subtract(wkBrhfee_D));
        reportContents_5.add(_320019_Tot2());

        // 124400    MOVE     SPACES                  TO       REPORT5-LINE.
        // 124500* 總計
        // 124600    WRITE    REPORT5-LINE         AFTER       1.
        // 124700    WRITE    REPORT5-LINE          FROM       360019-SEP.
        reportContents_5.add("");
        reportContents_5.add(_360019_Sep());

        // 124800    MOVE     SPACES                  TO       REPORT5-LINE.
        // 124900    WRITE    REPORT5-LINE         AFTER       1.
        // 125000    COMPUTE  WK-TOTCNT       = WK-TOTCNT-A + WK-TOTCNT-I +
        // 125100                               WK-TOTCNT-D + WK-TOTCNT-J .
        // 125200    COMPUTE  WK-TOTAMT       = WK-TOTAMT-A + WK-TOTAMT-I +
        // 125300                               WK-TOTAMT-D + WK-TOTAMT-J .
        // 125400    MOVE     WK-TOTCNT               TO       320019-TOTCNT-R3.
        // 125500    MOVE     WK-TOTAMT               TO       320019-TOTAMT-R3.
        // 125600    COMPUTE 320019-TOTACAMT-R3 = ( WK-TOTAMT-A - WK-BRHFEE-A ) +
        // 125700                                 ( WK-TOTAMT-I - WK-BRHFEE-I ) +
        // 125800    ( WK-TOTAMT-D - WK-BRHFEE-D )+( WK-TOTAMT-J - WK-BRHFEE-J ).
        // 125900    WRITE    REPORT5-LINE          FROM       320019-TOT3.
        reportContents_5.add("");
        wkTotcnt = wkTotcnt_A + wkTotcnt_I + wkTotcnt_D + wkTotcnt_J;
        wkTotamt = wkTotamt_A.add(wkTotamt_I).add(wkTotamt_D).add(wkTotamt_J);
        _320019_Totcnt_R3 = wkTotcnt;
        _320019_Totamt_R3 = wkTotamt;
        _320019_Totacamt_R3 =
                wkTotamt_A
                        .subtract(wkBrhfee_A)
                        .add(wkTotamt_I.subtract(wkBrhfee_I))
                        .add(wkTotamt_D.subtract(wkBrhfee_D))
                        .add(wkTotamt_J.subtract(wkBrhfee_J));
        reportContents_5.add(_320019_Tot3());

        // 126000    CLOSE    REPORTFL5  WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath_5, reportContents_5, CHARSET_BIG5);
            upload(reportFilePath_5, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        // 126100 050-REPORT-EXIT.
    }

    private void _000_Report() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _000_Report ....");
        // 091800 000-REPORT-RTN.
        //// 寫REPORTFL1報表
        // 091900    OPEN  OUTPUT   REPORTFL1.
        // 092000    MOVE     SPACES                  TO       REPORT1-LINE.
        // 092100    WRITE    REPORT1-LINE         AFTER       PAGE.
        reportContents_1.add(PAGE_SEPARATOR);

        // 092200    MOVE     SPACES                  TO       REPORT1-LINE.
        // 092300    WRITE    REPORT1-LINE          FROM       360019-TIT1.
        reportContents_1.add(_360019_Tit1());

        // 092400    MOVE     SPACES                  TO       REPORT1-LINE.
        // 092500    MOVE     "1"                     TO       360019-FORM-R.
        // 092600    MOVE     SPACES                  TO       REPORT1-LINE.
        // 092700    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 092800    WRITE    REPORT1-LINE          FROM       360019-TIT3.
        reportContents_1.add(_360019_Tit3());

        // 092900    MOVE     SPACES                  TO       REPORT1-LINE.
        // 093000    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 093100    MOVE     SPACES                  TO       REPORT1-LINE.
        // 093200    WRITE    REPORT1-LINE          FROM       360019-TIT4.
        reportContents_1.add(_360019_Tit4());

        // 093300    MOVE     SPACES                  TO       REPORT1-LINE.
        // 093400    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 093500    WRITE    REPORT1-LINE          FROM       360019-TIT5.
        reportContents_1.add(_360019_Tit5());

        // 093600    MOVE     SPACES                  TO       REPORT1-LINE.
        // 093700    WRITE    REPORT1-LINE          FROM       360019-SEP.
        reportContents_1.add(_360019_Sep());

        // 093800    MOVE     SPACES                  TO       REPORT1-LINE.
        // 093900* 臨櫃
        // 094000    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 094100    MOVE     " 中華電信股份有限公司 "   TO    360019-CNAME-AR.
        // 094200    MOVE     " 臨櫃 "                TO       360019-TXTYPE-AR.
        // 094300    MOVE     WK-TOTCNT-A             TO       360019-BRHCNT-AR.
        // 094400    MOVE     WK-TOTAMT-A             TO       360019-BRHAMT-AR.
        // 094500    COMPUTE  WK-BRHFEE-A     = WK-MONTHCNT-A * 3 .
        // 094600    COMPUTE 360019-ACAMT-AR  = WK-TOTAMT-A - WK-BRHFEE-A.
        _360019_Cname_Ar = " 中華電信股份有限公司 ";
        _360019_Txtype_Ar = " 臨櫃 ";
        _360019_Brhcnt_Ar = wkTotcnt_A;
        _360019_Brhamt_Ar = wkTotamt_A;
        wkBrhfee_A = new BigDecimal(wkMonthcnt_A * 3);
        _360019_Acamt_Ar = wkTotamt_A.subtract(wkBrhfee_A);
        // 094700    WRITE    REPORT1-LINE          FROM       360019-DTL1.
        reportContents_1.add(_360019_Dtl1());

        // 094800    MOVE     SPACES                  TO       REPORT1-LINE.
        // 094900* 網際網路
        // 095000    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 095100    MOVE     " 中華電信股份有限公司 "   TO    360019-CNAME-IR.
        // 095200    MOVE     " 網際網路 "            TO       360019-TXTYPE-IR.
        // 095300    MOVE     WK-TOTCNT-I             TO       360019-BRHCNT-IR.
        // 095400    MOVE     WK-TOTAMT-I             TO       360019-BRHAMT-IR.
        // 095500    COMPUTE  WK-BRHFEE-I     = WK-MONTHCNT-I * 5 / 2.
        // 095600    COMPUTE 360019-ACAMT-IR  = WK-TOTAMT-I - WK-BRHFEE-I.
        _360019_Cname_Ir = " 中華電信股份有限公司 ";
        _360019_Txtype_Ir = " 網際網路 ";
        _360019_Brhcnt_Ir = wkTotcnt_I;
        _360019_Brhamt_Ir = wkTotamt_I;
        wkBrhfee_I = new BigDecimal((wkMonthcnt_I * 5) / 2);
        _360019_Acamt_Ir = wkTotamt_I.subtract(wkBrhfee_I);
        // 095700    WRITE    REPORT1-LINE          FROM       360019-DTL2.
        reportContents_1.add(_360019_Dtl2());

        // 095800    MOVE     SPACES                  TO       REPORT1-LINE.
        // 095900    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 096000    WRITE    REPORT1-LINE          FROM       360019-SEP.
        reportContents_1.add(_360019_Sep());

        // 096100    MOVE     SPACES                  TO       REPORT1-LINE.
        // 096200* 總計
        // 096300    WRITE    REPORT1-LINE         AFTER       1.
        reportContents_1.add("");
        // 096400    COMPUTE  WK-TOTCNT       = WK-TOTCNT-A + WK-TOTCNT-I.
        // 096500    COMPUTE  WK-TOTAMT       = WK-TOTAMT-A + WK-TOTAMT-I.
        // 096600    MOVE     WK-TOTCNT               TO       360019-TOTCNT-R.
        // 096700    MOVE     WK-TOTAMT               TO       360019-TOTAMT-R.
        // 096800    COMPUTE 360019-TOTACAMT-R = ( WK-TOTAMT-A - WK-BRHFEE-A ) +
        // 096900                                ( WK-TOTAMT-I - WK-BRHFEE-I ).
        wkTotcnt = wkTotcnt_A + wkTotcnt_I;
        wkTotamt = wkTotamt_A.add(wkTotamt_I);
        _360019_Totcnt_R = wkTotcnt;
        _360019_Totamt_R = wkTotamt;
        _360019_Totacamt_R = wkTotamt_A.subtract(wkBrhfee_A).add(wkTotamt_I.subtract(wkBrhfee_I));
        // 097000    WRITE    REPORT1-LINE          FROM       360019-TOT.
        reportContents_1.add(_360019_Tot());

        // 097100    CLOSE    REPORTFL1  WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath_1, reportContents_1, CHARSET_BIG5);
            upload(reportFilePath_1, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 097200 000-REPORT-EXIT.
    }

    private void _0000_Month() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _0000_Month ....");
        // 133000 0000-MONTH-RTN.
        // 133100   IF        PUTF-CTL            =      21
        if ("21".equals(filePutf.getCtl())) {
            // 133200       IF        PUTF-TXTYPE  NOT=   "D" AND "J"
            if (!"D".equals(filePutf.getTxtype()) && !"J".equals(filePutf.getTxtype())) {
                // 133300         IF      PUTF-CLLBR            =      902
                if ("902".equals(filePutf.getCllbr())) {
                    // 133400            ADD        1               TO      WK-MONTHCNT-I
                    // 133500            ADD        PUTF-AMT        TO      WK-MONTHAMT-I
                    wkMonthcnt_I = wkMonthcnt_I + 1;
                    wkMonthamt_I =
                            wkMonthamt_I.add(
                                    parse.string2BigDecimal(
                                            filePutf.getAmt().trim().isEmpty()
                                                    ? "0"
                                                    : filePutf.getAmt()));
                } else {
                    // 133600         ELSE
                    // 133700            ADD        1               TO      WK-MONTHCNT-A
                    // 133800            ADD        PUTF-AMT        TO      WK-MONTHAMT-A
                    wkMonthcnt_A = wkMonthcnt_A + 1;
                    wkMonthamt_A =
                            wkMonthamt_A.add(
                                    parse.string2BigDecimal(
                                            filePutf.getAmt().trim().isEmpty()
                                                    ? "0"
                                                    : filePutf.getAmt()));
                    // 133900         END-IF
                }
                // 134000         ADD        1               TO      WK-MONTHCNT
                // 134100         ADD        PUTF-AMT        TO      WK-MONTHAMT
                wkMonthcnt = wkMonthcnt + 1;
                wkMonthamt =
                        wkMonthamt.add(
                                parse.string2BigDecimal(
                                        filePutf.getAmt().trim().isEmpty()
                                                ? "0"
                                                : filePutf.getAmt()));
                // 134200       END-IF
            }
            // 134300   END-IF.
        }
        // 134400 0000-MONTH-EXIT.
    }

    private void _010_Report() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _010_Report ....");
        // 097500 010-REPORT-RTN.
        //// 寫REPORTFL2,3報表

        // 097600    OPEN  OUTPUT   REPORTFL2.
        // 097700    OPEN  OUTPUT   REPORTFL3.

        // 097800    IF    WK-BDATE   <  990101
        if (wkBdate < 990101) {
            // 097900          ADD        1000000 TO   WK-BDATE.
            wkBdate = wkBdate + 1000000;
        }
        // 098000    IF    WK-EDATE   <  990101
        if (wkEdate < 990101) {
            // 098100          ADD        1000000 TO   WK-EDATE.
            wkEdate = wkEdate + 1000000;
        }
        // 098200    MOVE     WK-BDATE                TO       360019-BDATE-P.
        _360019_Bdate_P = "" + wkBdate;
        // 098300    MOVE     WK-EDATE                TO       360019-EDATE-P.
        _360019_Edate_P = "" + wkEdate;
        // 098400    MOVE     WK-MONTHCNT             TO       360019-TOTCNT-MONTH.
        _360019_Totcnt_Month = "" + wkMonthcnt;
        // 098500    MOVE     WK-MONTHAMT             TO       360019-TOTAMT-MONTH.
        _360019_Totamt_Month = "" + wkMonthamt;

        // 098600     MOVE  FD-BHDATE-NBSYY   TO  WK-LINE8-YY.
        // 098700     MOVE  FD-BHDATE-NBSMM   TO  WK-LINE8-MM.
        // 098800     MOVE  FD-BHDATE-NBSDD   TO  WK-LINE8-DD.
        // 098900     MOVE  FD-BHDATE-TBSYY   TO  WK-LINE2-YY.
        // 099000     MOVE  FD-BHDATE-TBSMM   TO  WK-LINE2-MM.
        // 099100     MOVE  WK-CHIN-NUM-REC   TO  WK-CHIN-NUM-GROUP.
        // 099200     MOVE  WK-CHIN-UNIT-REC  TO  WK-CHIN-UNIT-GROUP.
        // 099300     MOVE  WK-TRAIL-REC      TO  WK-TRAIL-GROUP.
        // 099400     MOVE  WK-NT-REC         TO  WK-NT-GROUP.
        // 099500     MOVE   1         TO WK-PGCNT.
        // 099600     MOVE WK-PGCNT    TO WK-HEAD1-PAGE.
        wkPgcnt = 1;
        // 099700    MOVE     SPACES                  TO       REPORT2-LINE.
        // 099800    WRITE    REPORT2-LINE         AFTER       PAGE.
        reportContents_2.add(PAGE_SEPARATOR);

        // 099900    MOVE     SPACES                  TO       REPORT2-LINE.
        // 100000    WRITE    REPORT2-LINE          FROM       360019-TIT21.
        reportContents_2.add(_360019_Tit21());

        // 100100    MOVE     SPACES                  TO       REPORT2-LINE.
        // 100200    MOVE     "003"                   TO       360019-BRNO-R.
        // 100300    MOVE     "2"                     TO       360019-FORM-R.
        _360019_Brno_R = "003";
        _360019_Form_R = "2";
        // 100400    WRITE    REPORT2-LINE          FROM       360019-TIT2.
        reportContents_2.add(_360019_Tit2());

        // 100500    MOVE     SPACES                  TO       REPORT2-LINE.
        // 100600    MOVE     WK-YYMM-1               TO       360019-YYMM-P.
        _360019_YYMM_P = wkYYMM_1;
        // 100700    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");
        // 100800    WRITE    REPORT2-LINE          FROM       360019-TIT23.
        reportContents_2.add(_360019_Tit23());

        // 100900    MOVE     SPACES                  TO       REPORT2-LINE.
        // 101000    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");
        // 101100    MOVE     SPACES                  TO       REPORT2-LINE.
        // 101200    WRITE    REPORT2-LINE          FROM       360019-TIT24.
        reportContents_2.add(_360019_Tit24());

        // 101300    MOVE     SPACES                  TO       REPORT2-LINE.
        // 101400    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");
        // 101500    WRITE    REPORT2-LINE          FROM       360019-TIT25.
        reportContents_2.add(_360019_Tit25());

        // 101600    MOVE     SPACES                  TO       REPORT2-LINE.
        // 101700    WRITE    REPORT2-LINE          FROM       360019-SEP.
        reportContents_2.add(_360019_Sep());

        // 101800    MOVE     SPACES                  TO       REPORT2-LINE.
        // 101900    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");

        // 102000    MOVE     " 中華電信股份有限公司 "  TO   360019-CNAME-MONTH-A,
        // 102100                                          360019-CNAME-MONTH-I.
        _360019_Cname_Month_A = " 中華電信股份有限公司 ";
        _360019_Cname_Month_I = " 中華電信股份有限公司 ";
        // 102200    MOVE     " 中華電信股份有限公司 "  TO   WK-LINE6-OFISNM.
        wk_Line6_Ofisnm = " 中華電信股份有限公司 ";
        // 102300    MOVE     " 臨櫃 "                  TO   360019-TXTYPE-MONTH-A.
        _360019_Txtype_Month_A = " 臨櫃 ";
        // 102400    MOVE     " 網際網路 "              TO   360019-TXTYPE-MONTH-I.
        _360019_Txtype_Month_I = " 網際網路 ";
        // 102500    MOVE     WK-MONTHCNT-A           TO     360019-BRHCNT-MONTH-A.
        _360019_Brhcnt_Month_A = wkMonthcnt_A;
        // 102600    MOVE     WK-MONTHCNT-I           TO     360019-BRHCNT-MONTH-I.
        _360019_Brhcnt_Month_I = wkMonthcnt_I;
        // 102700    MOVE     WK-MONTHAMT-A           TO     360019-BRHAMT-MONTH-A.
        _360019_Brhamt_Month_A = wkMonthamt_A;
        // 102800    MOVE     WK-MONTHAMT-I           TO     360019-BRHAMT-MONTH-I.
        _360019_Brhamt_Month_I = wkMonthamt_I;
        // 102900    COMPUTE  WK-BRHFEE-A    =     WK-MONTHCNT-A * 3 .
        wkBrhfee_A = new BigDecimal(wkMonthcnt_A).multiply(new BigDecimal(3));
        // 103000    COMPUTE  WK-BRHFEE-I    =     WK-MONTHCNT-I * 5 / 2.
        wkBrhfee_I =
                new BigDecimal(wkMonthcnt_I).multiply(new BigDecimal(5)).divide(new BigDecimal(2));
        // 103100    COMPUTE  WK-BRHFEE      =     WK-BRHFEE-A + WK-BRHFEE-I.
        wkBrhfee = wkBrhfee_A.subtract(wkBrhfee_I);
        // 103200    MOVE     WK-BRHFEE-A             TO     360019-BRHFEE-MONTH-A.
        _360019_Brhfee_Month_A = "" + wkBrhfee_A;
        // 103300    MOVE     WK-BRHFEE-I             TO     360019-BRHFEE-MONTH-I.
        _360019_Brhfee_Month_I = "" + wkBrhfee_I;
        // 103400    MOVE     WK-BRHFEE               TO     360019-FEEAMT-MONTH.
        _360019_Feeamt_Month = "" + wkBrhfee;
        // 103500    MOVE     WK-MONTHCNT             TO     WK-LINE2-TOTCNT.
        wk_Line2_Totcnt = "" + wkMonthcnt;
        // 103600    COMPUTE  WK-TOTFEE        =   WK-BRHFEE-A + WK-BRHFEE-I.
        wkTotfee = wkBrhfee_A.add(wkBrhfee_I);
        // 103700    COMPUTE  WK-LINE4-TOTFEE  =   WK-BRHFEE-A + WK-BRHFEE-I.
        wk_Line4_Totfee = wkBrhfee_A.add(wkBrhfee_I);
        // 103800    MOVE     "003"                   TO     WK-BRNO.
        wkBrno = "003";
        // 103900    PERFORM 2000-CNV-CHIN-NUM-RTN THRU 2000-EXIT.
        _2000_Cnv_Chin_Num();
        // 104000    WRITE    REPORT2-LINE          FROM       360019-DTL-MONTH-A.
        reportContents_2.add(_360019_Dtl_Month_A());
        // 104100    MOVE     SPACES                  TO       REPORT2-LINE.
        // 104200    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");

        // 104300    WRITE    REPORT2-LINE          FROM       360019-DTL-MONTH-I.
        reportContents_2.add(_360019_Dtl_Month_I());

        // 104400    MOVE     SPACES                  TO       REPORT2-LINE.
        // 104500    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");

        // 104600    WRITE    REPORT2-LINE          FROM       360019-SEP.
        reportContents_2.add(_360019_Sep());

        // 104700    MOVE     SPACES                  TO       REPORT2-LINE.
        // 104800    WRITE    REPORT2-LINE         AFTER       1.
        reportContents_2.add("");

        // 104900    WRITE    REPORT2-LINE          FROM       360019-TOT-MONTH.
        reportContents_2.add(_360019_Dtl_Month());

        // 105000    CLOSE    REPORTFL2  WITH SAVE.
        // 105100    CLOSE    REPORTFL3  WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath_2, reportContents_2, CHARSET_BIG5);
            upload(reportFilePath_2, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(reportFilePath_3, reportContents_3, CHARSET_BIG5);
            upload(reportFilePath_3, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 105200 010-REPORT-EXIT.
    }

    private void _2000_Cnv_Chin_Num() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _2000_Cnv_Chin_Num ....");
        // 126400 2000-CNV-CHIN-NUM-RTN.

        //// 列印手續費收據
        // 126600    PERFORM NUMBER-TO-ALPHA  THRU  NUMBER-TO-ALPHA-EXIT.
        wk_Line3_Chinfee = _number_To_Alpha(wkTotfee);

        // 129900*C026-3 手續費收據
        // 130000    IF WK-BRNO = "003"
        if ("003".equals(wkBrno)) {
            // 130100       WRITE REPORT3-LINE  AFTER PAGE
            reportContents_3.add(PAGE_SEPARATOR);
            // 130200       WRITE REPORT3-LINE FROM WK-HEAD1-REC AFTER 1
            reportContents_3.add("");
            wk_Head1_Brno = "003";
            reportContents_3.add(wk_Head1_Rec());

            // 130300       WRITE REPORT3-LINE FROM WK-LINE1-REC AFTER 6
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line1_Rec());

            // 130400       WRITE REPORT3-LINE FROM WK-LINE2-REC AFTER 3
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line2_Rec());

            // 130500       WRITE REPORT3-LINE FROM WK-LINE3-REC AFTER 2
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line3_Rec());

            // 130600       WRITE REPORT3-LINE FROM WK-LINE4-REC AFTER 2
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line4_Rec());

            // 130700       WRITE REPORT3-LINE FROM WK-LINE5-REC AFTER 3
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line5_Rec());

            // 130800       WRITE REPORT3-LINE FROM WK-LINE6-REC AFTER 3
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line6_Rec());

            // 130900       WRITE REPORT3-LINE FROM WK-LINE7-REC AFTER 7
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line7_Rec());

            // 131000       WRITE REPORT3-LINE FROM WK-LINE8-REC AFTER 8
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add("");
            reportContents_3.add(wk_Line8_Rec());

            // 131100    END-IF.
        }
        // 131200*C026-7 手續費收據
        // 131250    MOVE    0     TO       WK-PGCNT.
        wkPgcnt = 0;
        // 131300    IF WK-BRNO = "054" AND WK-MONTHCNT-D > 0
        if ("054".equals(wkBrno) && wkMonthcnt_D > 0) {
            // 131400       ADD    1                  TO  WK-PGCNT
            // 131450       MOVE   WK-PGCNT           TO  WK-HEAD1-PAGE
            // 131500       MOVE  "( 智慧繳費 )  "    TO  WK-LINE9-MEMO
            // 131520       MOVE   WK-MONTHCNT-D      TO  WK-LINE9-TOTCNT
            // 131540       COMPUTE  WK-LINE4-TOTFEE  =   WK-BRHFEE-D
            wkPgcnt = wkPgcnt + 1;
            wk_Line9_Memo = "( 智慧繳費 )  ";
            wk_Line9_Totcnt = wkMonthcnt_D;
            wk_Line4_Totfee = wkBrhfee_D;

            // 131600       PERFORM 2000-RPT-7-RTN  THRU  2000-RPT-7-EXIT.
            _2000_Rpt_7();
        }
        // 131700    IF WK-BRNO = "054" AND WK-MONTHCNT-J > 0
        if ("054".equals(wkBrno) && wkMonthcnt_J > 0) {
            // 131800       ADD    1                  TO  WK-PGCNT
            // 131850       MOVE   WK-PGCNT           TO  WK-HEAD1-PAGE
            // 131900       MOVE  "( 台灣ＰＡＹ )"    TO  WK-LINE9-MEMO
            // 132000       MOVE   WK-MONTHCNT-J      TO  WK-LINE9-TOTCNT
            // 132100       COMPUTE  WK-LINE4-TOTFEE  =   WK-BRHFEE-J
            // 132200       COMPUTE  WK-TOTFEE        =   WK-BRHFEE-J
            wkPgcnt = wkPgcnt + 1;
            wk_Line9_Memo = "( 台灣ＰＡＹ )";
            wk_Line9_Totcnt = wkMonthcnt_J;
            wk_Line4_Totfee = wkBrhfee_J;
            wkTotfee = wkBrhfee_J;
            // 132250* 重新產生中文大寫數字
            // 132300       PERFORM NUMBER-TO-ALPHA THRU  NUMBER-TO-ALPHA-EXIT
            wk_Line3_Chinfee = _number_To_Alpha(wkTotfee);
            // 132400       PERFORM 2000-RPT-7-RTN  THRU  2000-RPT-7-EXIT.
            _2000_Rpt_7();
        }
        // 132500 2000-EXIT.
    }

    private void _2000_Rpt_7() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _2000_Rpt_7 ....");
        // 136000 2000-RPT-7-RTN.
        // 136100* 列印智慧繳費、台灣 PAY 收據
        // 136200     WRITE REPORT7-LINE  AFTER PAGE.
        // 136300     WRITE REPORT7-LINE FROM WK-HEAD1-REC AFTER 1.
        reportContents_7.add(PAGE_SEPARATOR);
        reportContents_7.add("");
        reportContents_7.add(wk_Head1_Rec());

        // 136400     WRITE REPORT7-LINE FROM WK-LINE1-REC AFTER 6.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line1_Rec());

        // 136500     WRITE REPORT7-LINE FROM WK-LINE9-REC AFTER 3.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line9_Rec());

        // 136600     WRITE REPORT7-LINE FROM WK-LINE3-REC AFTER 2.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line3_Rec());

        // 136700     WRITE REPORT7-LINE FROM WK-LINE4-REC AFTER 2.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line4_Rec());

        // 136800     WRITE REPORT7-LINE FROM WK-LINE5-REC AFTER 3.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line5_Rec());

        // 136900     WRITE REPORT7-LINE FROM WK-LINE6-REC AFTER 3.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line6_Rec());

        // 137000     WRITE REPORT7-LINE FROM WK-LINE10-REC AFTER 7.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line10_Rec());

        // 137100     WRITE REPORT7-LINE FROM WK-LINE8-REC AFTER 8.
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add("");
        reportContents_7.add(wk_Line8_Rec());
        // 137200     MOVE  SPACES       TO   REPORT7-LINE.

        // 137400 2000-RPT-7-EXIT.
    }

    private String _360019_Rec() {
        sb = new StringBuilder();
        switch (_360019_Rc) {
            case "1":
                sb.append(formatUtil.padX(_360019_Rc, 1));
                sb.append(formatUtil.padX(_360019_Send, 7));
                sb.append(formatUtil.padX(_360019_Bh, 3));
                sb.append(formatUtil.padX(_360019_Prcdate, 7));
                sb.append(formatUtil.padX("", 92));
                break;
            case "2":
                sb.append(formatUtil.padX(_360019_Rc, 1));
                sb.append(formatUtil.padX(_360019_Cardno, 17));
                sb.append(formatUtil.padX(_360019_Bill, 2));
                sb.append(formatUtil.padX(_360019_Ofisno, 4));
                sb.append(formatUtil.padX("", 2));
                sb.append(formatUtil.padX(_360019_Tel, 10));
                sb.append(formatUtil.padX(_360019_YYMM, 5));
                sb.append(formatUtil.padX(_360019_Lmtdate, 7));
                sb.append(formatUtil.pad9("" + _360019_Amt, 9));
                sb.append(formatUtil.padX(_360019_Chkdg, 2));
                sb.append(formatUtil.padX(_360019_Sitdate, 7));
                sb.append(formatUtil.padX(_360019_Chksno, 4));
                sb.append(formatUtil.padX(_360019_Kind, 1));
                sb.append(formatUtil.padX(_360019_Clbank, 7));
                sb.append(formatUtil.padX(_360019_Billbhno, 1));
                sb.append(formatUtil.padX(_360019_Opno, 2));
                sb.append(formatUtil.padX(_360019_Date, 7));
                sb.append(formatUtil.padX(_360019_Telecomid, 3));
                sb.append(formatUtil.padX("", 19));
                break;
            case "3":
                sb.append(formatUtil.padX(_360019_Rc, 1));
                sb.append(formatUtil.pad9(_360019_Totcnt, 9));
                sb.append(formatUtil.pad9(_360019_Totamt, 13));
                sb.append(formatUtil.padX("", 87));
                break;
        }
        return sb.toString();
    }

    private String _360019_Tit21() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 27));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　電　信　費　手　續　費　彙　總　單 ", 60));
        return sb.toString();
    }

    private String _360019_Tit1() {
        // 023200 01   360019-TIT1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 23));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　電　信　費　資　料　彙　總　單 ", 56));
        return sb.toString();
    }

    private String _360019_Tit2() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.padX(_360019_Brno_R, 3));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX("FORM : C026/", 12));
        sb.append(formatUtil.padX(_360019_Form_R, 1));
        return sb.toString();
    }

    private String _360019_Tit3() {
        // 024400 01   360019-TIT3.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 :", 12));
        sb.append(reportUtil.customFormat("" + _360019_Acdate_R, "999/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 印表日期 :", 12));
        sb.append(reportUtil.customFormat(_360019_Pdate_R, "999/99/99"));
        return sb.toString();
    }

    private String _360019_Tit4() {
        // 025200 01   360019-TIT4.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收日期 :", 12));
        sb.append(reportUtil.customFormat(_360019_Cldate_R, "999/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("1", 4));
        return sb.toString();
    }

    private String _360019_Tit5() {
        // 026000 01   360019-TIT5.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 機構名稱 ", 10));
        sb.append(formatUtil.padX("", 15));
        sb.append(formatUtil.padX(" 代收管道 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 代收電信費筆數 ", 16));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 代收電信費金額 ", 16));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解繳金額 ", 10));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 入帳帳號 ", 10));
        return sb.toString();
    }

    private String _360019_Dtl1() {
        // 027900 01   360019-DTL1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(_360019_Cname_Ar, 24));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(_360019_Txtype_Ar, 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Ar, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Ar, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _360019_Acamt_Ar, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX("", 26));
        return sb.toString();
    }

    private String _360019_Dtl2() {
        // 029200 01   360019-DTL2.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(_360019_Cname_Ir, 24));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(_360019_Txtype_Ir, 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Ir, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Ir, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _360019_Acamt_Ir, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX("", 26));
        return sb.toString();
    }

    private String _360019_Dtl3() {
        sb = new StringBuilder();
        // 030500 01   360019-DTL3.
        // 030600  03  FILLER                           PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 030700  03  360019-CNAME-DR                  PIC X(24).
        sb.append(formatUtil.padX(_360019_Cname_Dr, 24));
        // 030800  03  FILLER                           PIC X(05) VALUE SPACES.
        sb.append(formatUtil.padX("", 5));
        // 030900  03  360019-TXTYPE-DR                 PIC X(10).
        sb.append(formatUtil.padX(_360019_Txtype_Dr, 10));
        // 031000  03  FILLER                           PIC X(05) VALUE SPACES.
        sb.append(formatUtil.padX("", 5));
        // 031100  03  360019-BRHCNT-DR                 PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Dr, "ZZZ,ZZZ,ZZ9"));
        // 031200  03  FILLER                           PIC X(10) VALUE SPACES.
        // 031300  03  360019-BRHAMT-DR                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Dr, "ZZZ,ZZZ,ZZZ,ZZ9"));
        // 031400  03  FILLER                           PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 031500  03  360019-ACAMT-DR                  PIC -,---,---,--9.
        sb.append(reportUtil.customFormat("" + _360019_Acamt_Dr, "Z,ZZZ,ZZZ,ZZ9"));
        // 031600  03  FILLER                           PIC X(06) VALUE SPACES.
        sb.append(formatUtil.padX("", 6));
        // 031700  03  360019-REFACNO-DR                PIC X(26) VALUE SPACES.
        sb.append(formatUtil.padX("", 26));
        return sb.toString();
    }

    private String _360019_Tot() {
        // 031800 01   360019-TOT.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總計： ", 8));
        sb.append(formatUtil.padX(" 中華電信公司 ", 14));
        sb.append(formatUtil.padX("", 26));
        sb.append(reportUtil.customFormat("" + _360019_Totcnt_R, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _360019_Totamt_R, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + _360019_Totacamt_R, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String _360019_Tit23() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收月份 : ", 12));
        sb.append(reportUtil.customFormat(_360019_YYMM_P, "999/99"));
        sb.append(formatUtil.padX("", 69));
        sb.append(formatUtil.padX(" 印表日期 : ", 12));
        sb.append(reportUtil.customFormat(_360019_Pdate_P, "999/99/99"));
        return sb.toString();
    }

    private String _360019_Tit24() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收期間 : ", 12));
        sb.append(reportUtil.customFormat(_360019_Bdate_P, "999/99/99"));
        sb.append(formatUtil.padX("-", 1));
        sb.append(reportUtil.customFormat(_360019_Edate_P, "999/99/99"));
        sb.append(formatUtil.padX("", 58));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("1", 4));
        return sb.toString();
    }

    private String _360019_Tit25() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 機構名稱 ", 10));
        sb.append(formatUtil.padX("", 15));
        sb.append(formatUtil.padX(" 代收管道 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 本月代收電信費筆數 ", 20));
        sb.append(formatUtil.padX("", 16));
        sb.append(formatUtil.padX(" 本月代收電信費金額 ", 20));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 手續費金額 ", 12));
        return sb.toString();
    }

    private String _320019_Tit1() {
        // 033400 01   320019-TIT1.
        // 033500  03  FILLER                           PIC X(33) VALUE SPACES.
        // 033600  03  FILLER                           PIC X(56) VALUE
        // 033700      " 臺　灣　銀　行　代　收　電　信　費　資　料　彙　總　單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 33));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　電　信　費　資　料　彙　總　單 ", 56));
        return sb.toString();
    }

    private String _320019_Tot1() {
        // 042900 01   320019-TOT1.
        // 043000  03  FILLER                           PIC X(08) VALUE
        // 043100      " 小計： ".
        // 043200  03  FILLER                           PIC X(50) VALUE
        // 043300      " 臨櫃 ( 業務代碼 A)".
        // 043400  03  320019-TOTCNT-R1                 PIC ZZZ,ZZZ,ZZ9.
        // 043500  03  FILLER                           PIC X(10) VALUE SPACES.
        // 043600  03  320019-TOTAMT-R1                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 043700  03  FILLER                           PIC X(02) VALUE SPACES.
        // 043800  03  320019-TOTACAMT-R1               PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 043900  03  FILLER                           PIC X(06) VALUE SPACES.
        // 044000  03  FILLER                           PIC X(28) VALUE
        // 044100      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 小計： ", 8));
        sb.append(formatUtil.padX(" 臨櫃 ( 業務代碼 A)", 50));
        sb.append(reportUtil.customFormat("" + _320019_Totcnt_R1, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Totamt_R1, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Totacamt_R1, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));

        return sb.toString();
    }

    private String _320019_Tot2() {
        // 044200 01   320019-TOT2.
        // 044300  03  FILLER                           PIC X(08) VALUE
        // 044400      " 小計： ".
        // 044500  03  FILLER                           PIC X(50) VALUE
        // 044600      " 網際網路 (I)+ 智慧繳費 (Y)+ 台灣ＰＡＹ (N)".
        // 044700  03  320019-TOTCNT-R2                 PIC ZZZ,ZZZ,ZZ9.
        // 044800  03  FILLER                           PIC X(10) VALUE SPACES.
        // 044900  03  320019-TOTAMT-R2                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 045000  03  FILLER                           PIC X(02) VALUE SPACES.
        // 045100  03  320019-TOTACAMT-R2               PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 045200  03  FILLER                           PIC X(06) VALUE SPACES.
        // 045300  03  FILLER                           PIC X(28) VALUE
        // 045400      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 小計： ", 8));
        sb.append(formatUtil.padX(" 網際網路 (I)+ 智慧繳費 (Y)+ 台灣ＰＡＹ (N)", 50));
        sb.append(reportUtil.customFormat("" + _320019_Totcnt_R2, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Totamt_R2, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + _320019_Totacamt_R2, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String _320019_Tot3() {
        // 045500  01  320019-TOT3.
        // 045600  03  FILLER                           PIC X(08) VALUE
        // 045700      " 總計： ".
        // 045800  03  FILLER                           PIC X(14) VALUE
        // 045900      " 中華電信公司 ".
        // 046000  03  FILLER                           PIC X(36) VALUE SPACES.
        // 046100  03  320019-TOTCNT-R3                 PIC ZZZ,ZZZ,ZZ9.
        // 046200  03  FILLER                           PIC X(10) VALUE SPACES.
        // 046300  03  320019-TOTAMT-R3                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 046400  03  FILLER                           PIC X(02) VALUE SPACES.
        // 046500  03  320019-TOTACAMT-R3               PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 046600  03  FILLER                           PIC X(06) VALUE SPACES.
        // 046700  03  FILLER                           PIC X(28) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總計： ", 8));
        sb.append(formatUtil.padX(" 中華電信公司 ", 14));
        sb.append(formatUtil.padX("", 36));
        sb.append(reportUtil.customFormat("" + _320019_Totcnt_R3, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Totamt_R3, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + _320019_Totacamt_R3, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX("", 28));
        return sb.toString();
    }

    private String _320019_Tit3() {
        // 034600 01   320019-TIT3.
        // 034700  03  FILLER                           PIC X(12) VALUE
        // 034800      " 解繳日期 :".
        // 034900  03  320019-ACDATE-R                  PIC 999/99/99.
        // 035000  03  FILLER                           PIC X(76) VALUE SPACES.
        // 035100  03  FILLER                           PIC X(12) VALUE
        // 035200      " 印表日期 :".
        // 035300  03  320019-PDATE-R                   PIC 999/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 :", 12));
        sb.append(reportUtil.customFormat("" + _320019_Acdate_R, "999/99/99"));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX(" 印表日期 :", 12));
        sb.append(reportUtil.customFormat(_320019_Pdate_R, "999/99/99"));
        return sb.toString();
    }

    private String _320019_Tit4() {
        // 035400 01   320019-TIT4.
        // 035500  03  FILLER                           PIC X(12) VALUE
        // 035600      " 代收日期 :".
        // 035700  03  320019-CLDATE-R                  PIC 999/99/99.
        // 035800  03  FILLER                           PIC X(76) VALUE SPACES.
        // 035900  03  FILLER                           PIC X(07) VALUE
        // 036000      " 頁數 :".
        // 036100  03  320019-PAGE-R                    PIC 9(04) VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收日期 :", 12));
        sb.append(reportUtil.customFormat(_320019_Cldate_R, "999/99/99"));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("1", 4));
        return sb.toString();
    }

    private String _320019_Tit5() {
        // 036200 01   320019-TIT5.
        // 036300  03  FILLER                           PIC X(08) VALUE SPACES.
        // 036400  03  FILLER                           PIC X(10) VALUE
        // 036500      " 機構名稱 ".
        // 036600  03  FILLER                           PIC X(15) VALUE SPACES.
        // 036700  03  FILLER                           PIC X(10) VALUE
        // 036800      " 代收管道 ".
        // 036900  03  FILLER                           PIC X(11) VALUE SPACES.
        // 037000  03  FILLER                           PIC X(16) VALUE
        // 037100      " 代收電信費筆數 ".
        // 037200  03  FILLER                           PIC X(10) VALUE SPACES.
        // 037300  03  FILLER                           PIC X(16) VALUE
        // 037400      " 代收電信費金額 ".
        // 037500  03  FILLER                           PIC X(07) VALUE SPACES.
        // 037600  03  FILLER                           PIC X(10) VALUE
        // 037700      " 解繳金額 ".
        // 037800  03  FILLER                           PIC X(06) VALUE SPACES.
        // 037900  03  FILLER                           PIC X(10) VALUE
        // 038000      " 入帳帳號 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 機構名稱 ", 10));
        sb.append(formatUtil.padX("", 15));
        sb.append(formatUtil.padX(" 代收管道 ", 10));
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.padX(" 代收電信費筆數 ", 10));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 代收電信費金額 ", 16));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 解繳金額 ", 10));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 入帳帳號 ", 10));
        return sb.toString();
    }

    private String _320019_Dtl1() {
        // 038100 01   320019-DTL1.
        // 038200  03  FILLER                           PIC X(04) VALUE SPACES.
        // 038300  03  FILLER                           PIC X(22) VALUE
        // 038400      " 中華電信股份有限公司 ".
        // 038500  03  FILLER                           PIC X(02) VALUE SPACES.
        // 038600  03  FILLER                           PIC X(26) VALUE
        // 038700      " 臨櫃 ( 業務代碼 A)".
        // 038800  03  FILLER                           PIC X(04) VALUE SPACES.
        // 038900  03  320019-BRHCNT-AR                 PIC ZZZ,ZZZ,ZZ9.
        // 039000  03  FILLER                           PIC X(10) VALUE SPACES.
        // 039100  03  320019-BRHAMT-AR                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 039200  03  FILLER                           PIC X(04) VALUE SPACES.
        // 039300  03  320019-ACAMT-AR                  PIC Z,ZZZ,ZZZ,ZZ9.
        // 039400  03  FILLER                           PIC X(06) VALUE SPACES.
        // 039500  03  FILLER                           PIC X(28) VALUE
        // 039600      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 中華電信股份有限公司 ", 22));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 臨櫃 ( 業務代碼 A)", 26));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Brhcnt_Ar, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Brhamt_Ar, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Acamt_Ar, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String _320019_Dtl2() {
        // 039700 01   320019-DTL2.
        // 039800  03  FILLER                           PIC X(04) VALUE SPACES.
        // 039900  03  FILLER                           PIC X(22) VALUE
        // 040000      " 中華電信股份有限公司 ".
        // 040100  03  FILLER                           PIC X(02) VALUE SPACES.
        // 040200  03  FILLER                           PIC X(26) VALUE
        // 040300      " 網際網路 ( 業務代碼 I)".
        // 040400  03  FILLER                           PIC X(04) VALUE SPACES.
        // 040500  03  320019-BRHCNT-IR                 PIC ZZZ,ZZZ,ZZ9.
        // 040600  03  FILLER                           PIC X(10) VALUE SPACES.
        // 040700  03  320019-BRHAMT-IR                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 040800  03  FILLER                           PIC X(04) VALUE SPACES.
        // 040900  03  320019-ACAMT-IR                  PIC Z,ZZZ,ZZZ,ZZ9.
        // 041000  03  FILLER                           PIC X(06) VALUE SPACES.
        // 041100  03  FILLER                           PIC X(28) VALUE
        // 041200      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 中華電信股份有限公司 ", 22));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 網際網路 ( 業務代碼 I)", 26));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Brhcnt_Ir, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Brhamt_Ir, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Acamt_Ir, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String _320019_Dtl3() {
        // 041300 01   320019-DTL3.
        // 041400  03  FILLER                           PIC X(04) VALUE SPACES.
        // 041500  03  FILLER                           PIC X(22) VALUE
        // 041600      " 中華電信股份有限公司 ".
        // 041700  03  FILLER                           PIC X(02) VALUE SPACES.
        // 041800  03  FILLER                           PIC X(26) VALUE
        // 041900      " 智慧繳費 ( 業務代碼 Y)".
        // 042000  03  FILLER                           PIC X(04) VALUE SPACES.
        // 042100  03  320019-BRHCNT-DR                 PIC ZZZ,ZZZ,ZZ9.
        // 042200  03  FILLER                           PIC X(10) VALUE SPACES.
        // 042300  03  320019-BRHAMT-DR                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 042400  03  FILLER                           PIC X(04) VALUE SPACES.
        // 042500  03  320019-ACAMT-DR                  PIC -,---,---,--9.
        // 042600  03  FILLER                           PIC X(06) VALUE SPACES.
        // 042700  03  FILLER                           PIC X(28) VALUE
        // 042800      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 中華電信股份有限公司 ", 22));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 智慧繳費 ( 業務代碼 Y)", 26));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Brhcnt_Dr, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Brhamt_Dr, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Acamt_Dr, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String _320019_Dtl4() {
        // 042805 01   320019-DTL4.
        // 042810  03  FILLER                           PIC X(04) VALUE SPACES.
        // 042815  03  FILLER                           PIC X(22) VALUE
        // 042820      " 中華電信股份有限公司 ".
        // 042825  03  FILLER                           PIC X(02) VALUE SPACES.
        // 042830  03  FILLER                           PIC X(28) VALUE
        // 042835      " 台灣ＰＡＹ ( 業務代碼 N)".
        // 042840  03  FILLER                           PIC X(02) VALUE SPACES.
        // 042845  03  320019-BRHCNT-JR                 PIC ZZZ,ZZZ,ZZ9.
        // 042850  03  FILLER                           PIC X(10) VALUE SPACES.
        // 042855  03  320019-BRHAMT-JR                 PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 042860  03  FILLER                           PIC X(04) VALUE SPACES.
        // 042865  03  320019-ACAMT-JR                  PIC -,---,---,--9.
        // 042870  03  FILLER                           PIC X(06) VALUE SPACES.
        // 042875  03  FILLER                           PIC X(28) VALUE
        // 042880      " 台銀信義分行 10041100400003".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 中華電信股份有限公司 ", 22));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 台灣ＰＡＹ ( 業務代碼 N)", 28));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + _320019_Brhcnt_Jr, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(reportUtil.customFormat("" + _320019_Brhamt_Jr, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + _320019_Acamt_Jr, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 台銀信義分行 10041100400003", 28));
        return sb.toString();
    }

    private String wk_Head1_Rec() {
        StringBuilder head1 = new StringBuilder();
        head1.append(formatUtil.padX("BRNO :", 6));
        head1.append(formatUtil.padX(wk_Head1_Brno, 3));
        head1.append(formatUtil.padX("", 48));
        head1.append(formatUtil.padX("PAGE:", 5));
        head1.append(reportUtil.customFormat("" + wkPgcnt, "ZZ9"));
        head1.append(formatUtil.padX("", 4));
        head1.append(formatUtil.padX("FORM:", 5));
        head1.append(formatUtil.padX("C026/", 6));
        head1.append(formatUtil.padX("3", 1));
        return head1.toString();
    }

    private String wk_Line1_Rec() {
        StringBuilder line1 = new StringBuilder();
        line1.append(formatUtil.padX("@1A468C@", 3)); // todo:內碼
        line1.append(formatUtil.padX(" 茲收到 ", 8));
        line1.append(formatUtil.padX("@1A4689@", 3)); // todo:內碼
        return line1.toString();
    }

    private String wk_Line2_Rec() {
        StringBuilder line2 = new StringBuilder();
        line2.append(formatUtil.padX("@1A468C@", 3)); // todo:內碼
        line2.append(formatUtil.padX(" 貴機構補貼本行 ", 16));
        String tbsdyYY = formatUtil.pad9("" + tbsdy, 7).substring(0, 3);
        String tbsdyMM = formatUtil.pad9("" + tbsdy, 7).substring(3, 5);
        line2.append(reportUtil.customFormat(tbsdyYY, "ZZ9"));
        line2.append(formatUtil.padX(" 年 ", 4));
        line2.append(reportUtil.customFormat(tbsdyMM, "Z9"));
        line2.append(formatUtil.padX(" 月份代收電話費手續費共 ", 24));
        line2.append(reportUtil.customFormat(wk_Line2_Totcnt, "**,***,***"));
        line2.append(formatUtil.padX(" 筆 ", 4));
        line2.append(formatUtil.padX("@1A4689@", 3)); // todo:內碼
        return line2.toString();
    }

    private String wk_Line3_Rec() {
        StringBuilder line3 = new StringBuilder();
        line3.append(formatUtil.padX("@1A468C@", 3));
        line3.append(formatUtil.padX("", 78));
        line3.append(formatUtil.padX("@1A4689@", 3));
        return line3.toString();
    }

    private String wk_Line4_Rec() {
        StringBuilder line7 = new StringBuilder();
        line7.append(formatUtil.padX("@1A468C@", 3));
        line7.append(formatUtil.padX("", 5));
        line7.append(formatUtil.padX("NT$", 3));
        line7.append(formatUtil.padX("NT$", 3));
        line7.append(reportUtil.customFormat("" + wk_Line4_Totfee, "***,***,**9.99"));
        line7.append(formatUtil.padX("@1A4689@", 3));
        return line7.toString();
    }

    private String wk_Line5_Rec() {
        StringBuilder line5 = new StringBuilder();
        line5.append(formatUtil.padX("@1A468C@", 3));
        line5.append(formatUtil.padX(" 此致 ", 6));
        line5.append(formatUtil.padX("@1A4689@", 3));
        return line5.toString();
    }

    private String wk_Line6_Rec() {
        StringBuilder line6 = new StringBuilder();
        line6.append(formatUtil.padX("@1A468C@", 3));
        line6.append(formatUtil.padX(wk_Line6_Ofisnm, 36));
        line6.append(formatUtil.padX(" 台照 ", 6));
        line6.append(formatUtil.padX("@1A4689@", 3));
        return line6.toString();
    }

    private String wk_Line7_Rec() {
        StringBuilder line7 = new StringBuilder();
        line7.append(formatUtil.padX("@1A468C@", 3));
        line7.append(formatUtil.padX("", 35));
        line7.append(formatUtil.padX(" 台灣銀行營業部 ", 20));
        line7.append(formatUtil.padX(" 啟 ", 4));
        line7.append(formatUtil.padX("@1A4689@", 3));
        return line7.toString();
    }

    private String wk_Line8_Rec() {
        StringBuilder line8 = new StringBuilder();
        String nbsdyX = formatUtil.pad9("" + nbsdy, 7);
        String nbsdyYY = nbsdyX.substring(0, 3);
        String nbsdyMM = nbsdyX.substring(3, 5);
        String nbsdyDD = nbsdyX.substring(5, 7);
        line8.append(formatUtil.padX("@1A468C@", 3));
        line8.append(formatUtil.padX(" 中華民國 ", 10));
        line8.append(reportUtil.customFormat(nbsdyYY, "ZZ9"));
        line8.append(formatUtil.padX(" 年 ", 4));
        line8.append(reportUtil.customFormat(nbsdyMM, "Z9"));
        line8.append(formatUtil.padX(" 月 ", 4));
        line8.append(reportUtil.customFormat(nbsdyDD, "Z9"));
        line8.append(formatUtil.padX(" 日 ", 4));
        line8.append(formatUtil.padX("@1A4689@", 3));
        return line8.toString();
    }

    private String wk_Line9_Rec() {
        String wk_Line9_YY = formatUtil.pad9("" + tbsdy, 7).substring(0, 3);
        String wk_Line9_MM = formatUtil.pad9("" + tbsdy, 7).substring(3, 5);
        StringBuilder line9 = new StringBuilder();
        line9.append(formatUtil.padX("@1A468C@", 3)); // todo:內碼
        line9.append(formatUtil.padX(" 貴機構補貼本行 ", 16));
        line9.append(reportUtil.customFormat(wk_Line9_YY, "ZZ9"));
        line9.append(formatUtil.padX(" 年 ", 4));
        line9.append(reportUtil.customFormat(wk_Line9_MM, "Z9"));
        line9.append(formatUtil.padX(" 月份代收電話費手續費共 ", 24));
        line9.append(reportUtil.customFormat("" + wk_Line9_Totcnt, "**,***,***"));
        line9.append(formatUtil.padX(" 筆 ", 4));
        line9.append(formatUtil.padX("(", 1));
        line9.append(formatUtil.padX(wk_Line9_Memo, 14));
        line9.append(formatUtil.padX(")", 1));
        line9.append(formatUtil.padX("@1A4689@", 3)); // todo:內碼
        return line9.toString();
    }

    private String wk_Line10_Rec() {
        StringBuilder line10 = new StringBuilder();
        line10.append(formatUtil.padX("@1A468C@", 3));
        line10.append(formatUtil.padX("", 35));
        line10.append(formatUtil.padX(" 台灣銀行信義分行 ", 20));
        line10.append(formatUtil.padX(" 啟 ", 4));
        line10.append(formatUtil.padX("@1A4689@", 3));
        return line10.toString();
    }

    private String _360019_Dtl_Month_A() {
        // 050900 01   360019-DTL-MONTH-A.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(_360019_Cname_Month_A, 24));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(_360019_Txtype_Month_A, 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Month_A, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 21));
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Month_A, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat(_360019_Brhfee_Month_A, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String _360019_Dtl_Month_I() {
        // 052000 01   360019-DTL-MONTH-I.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(_360019_Cname_Month_I, 24));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(_360019_Txtype_Month_I, 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Month_I, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 21));
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Month_I, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat(_360019_Brhfee_Month_I, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String _360019_Dtl_Month_D() {
        // 053100 01   360019-DTL-MONTH-D.
        // 053200  03  FILLER                           PIC X(04) VALUE SPACES.
        // 053300  03  360019-CNAME-MONTH-D             PIC X(24).
        // 053400  03  FILLER                           PIC X(05) VALUE SPACES.
        // 053500  03  360019-TXTYPE-MONTH-D            PIC X(12).
        // 053600  03  FILLER                           PIC X(03) VALUE SPACES.
        // 053700  03  360019-BRHCNT-MONTH-D            PIC ZZZ,ZZZ,ZZ9.
        // 053800  03  FILLER                           PIC X(21) VALUE SPACES.
        // 053900  03  360019-BRHAMT-MONTH-D            PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 054000  03  FILLER                           PIC X(09) VALUE  SPACES.
        // 054100  03  360019-BRHFEE-MONTH-D            PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(_360019_Cname_Month_D, 24));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(_360019_Txtype_Month_D, 12));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("" + _360019_Brhcnt_Month_D, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 21));
        sb.append(reportUtil.customFormat("" + _360019_Brhamt_Month_D, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat(_360019_Brhfee_Month_D, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String _360019_Dtl_Month() {
        // 054200 01   360019-TOT-MONTH.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 本月代收電信費總筆數 : ", 24));
        sb.append(reportUtil.customFormat(_360019_Totcnt_Month, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 本月代收電信費總金額 : ", 24));
        sb.append(reportUtil.customFormat(_360019_Totamt_Month, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 本月應收手續費總金額 : ", 25));
        sb.append(reportUtil.customFormat(_360019_Feeamt_Month, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String _360019_Sep() {
        return reportUtil.makeGate("-", 150);
    }

    private String _360019_Tot_Month() {
        // 054200 01   360019-TOT-MONTH.
        // 054300  03  FILLER                           PIC X(02) VALUE SPACES.
        // 054400  03  FILLER                           PIC X(24) VALUE
        // 054500      " 本月代收電信費總筆數 : ".
        // 054600  03  360019-TOTCNT-MONTH              PIC ZZZ,ZZZ,ZZ9.
        // 054700  03  FILLER                           PIC X(01) VALUE SPACES.
        // 054800  03  FILLER                           PIC X(24) VALUE
        // 054900      " 本月代收電信費總金額 : ".
        // 055000  03  360019-TOTAMT-MONTH              PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 055100  03  FILLER                           PIC X(02) VALUE SPACES.
        // 055200  03  FILLER                           PIC X(25) VALUE
        // 055300      " 本月應收手續費總金額 : ".
        // 055400  03  360019-FEEAMT-MONTH              PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 本月代收電信費總筆數 : ", 24));
        sb.append(reportUtil.customFormat(_360019_Totcnt_Month, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 本月代收電信費總金額 : ", 24));
        sb.append(reportUtil.customFormat(_360019_Totamt_Month, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 本月應收手續費總金額 : ", 25));
        sb.append(reportUtil.customFormat(_360019_Feeamt_Month, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String _number_To_Alpha(BigDecimal number) {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "CONV360019Lsnr _number_To_Alpha ....");
        //        //137700 NUMBER-TO-ALPHA.
        if (number.compareTo(BigDecimal.ZERO) == 0) {
            return "零";
        }
        StringBuilder resultBuilder = new StringBuilder();
        int unitIndex = 0;
        while (number.compareTo(BigDecimal.ZERO) > 0) {
            int digit = parse.string2Integer("" + number.divide(new BigDecimal(10)));
            resultBuilder.insert(0, digitMap.get(digit) + C_UNIT[unitIndex]);
            number = number.divide(new BigDecimal(10));
            unitIndex++;
        }
        return resultBuilder.toString();
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
            forFsapTA();
            forFsapTI();
        }
    }

    private void forFsapTA() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        wkPutfile, // 來源檔案名稱(20碼長)
                        wkPutfile + "_TA", // 目的檔案名稱(20碼長)
                        "1", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV360019", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapTI() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        wkPutfile, // 來源檔案名稱(20碼長)
                        wkPutfile + "_TI", // 目的檔案名稱(20碼長)
                        "1", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV360019_TI", // 檔案設定代號 ex:CONVF001
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
        String fileNameX =
                REPORT_NAME_1
                        + ","
                        + REPORT_NAME_2
                        + ","
                        + REPORT_NAME_3
                        + ","
                        + REPORT_NAME_4
                        + ","
                        + REPORT_NAME_5
                        + ","
                        + REPORT_NAME_6
                        + ","
                        + REPORT_NAME_7;

        responseTextMap.put("RPTNAME", fileNameX);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
