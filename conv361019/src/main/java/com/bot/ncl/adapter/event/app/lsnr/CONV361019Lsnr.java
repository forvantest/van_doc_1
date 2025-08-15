/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV361019;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.fileVo.FileSumPUTFN;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Component("CONV361019Lsnr")
@Scope("prototype")
public class CONV361019Lsnr extends BatchListenerCase<CONV361019> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateutil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FileSumPUTFN fileSumPUTFN;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV361019 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    /* -- Final Define -- */
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String SPACE = "";
    private static final String CL005_010 = "CL005/010/";
    private static final String CONVF = "CONVF/";
    private static final String PUTFN = "PUTFN/";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STR_0 = "0";
    private static final String STR_1 = "1";
    private static final String STR_2 = "2";
    private static final String STR_3 = "3";
    private static final String STR_32 = "32";
    private static final String STR_33 = "33";
    private static final String STR_35 = "35";
    private static final String STR_37 = "37";
    private static final String STR_39 = "39";
    private static final String STR_99999 = "99999";
    private static final String STR_004 = "004     ";
    private static final String STR_TWA = "TWA     ";
    private static final String STR_101 = "101";
    private static final String STR_A = "A";
    private static final String STR_C = "C";
    private static final String STR_I = "I";
    private static final String STR_M = "M";
    private static final String STR_22C4361019 = "22C4361019";
    private static final String STR_FCL005361019 = "FCL005361019";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private final List<String> file361019Contents = new ArrayList<>();
    private final List<String> file361019RTContents = new ArrayList<>();
    private final List<String> fileReportflContents = new ArrayList<>();
    private StringBuilder sb = new StringBuilder();
    DateDto dateDto = new DateDto();
    /* -- int (9) -- */
    private String processDate;
    private String tbsdy;
    private int processDateInt = 0;
    private int putfnCtl;
    private int putfnSitdate;
    private int putfnCllbr;
    private int putfnLmtdate;
    private int putfnTotcnt;
    private int totcnt361019;
    private String wkCdate = "";
    private String wkFdate = "";
    private int wkRtdate = 0;
    private int wkUnitcnt = 0;
    private int wkSubcnt = 0;
    private int wkRttotcnt = 0;
    private int wkTotcnt = 0;
    private int wkMonday = 0;
    private int wkClndrKey = 0;
    private int bdate361019R;
    private int edate361019R;
    private int pdate361019R1;
    private int pdate361019R2;
    private int sdate361019;
    private int cllbr361019;
    private int totcnt361019R;
    private int txtype361019;
    private String workarea361019;
    private int lmtdate361019;
    private String YYYMM361019;
    private int rt361019Totcnt;
    private int cnt361019R;
    private int cnt361019R2;
    private int pagecnt361019R;
    private int yyy361019R2;
    private int mm361019R2;
    private int getWeek;

    /* -- str (X) -- */
    private String wkYYYMMDD = "";
    private String wkPutfile = "";
    private String wkConvfile = "";
    private String wkRtfile = "";
    private String wkPutdir = "";
    private String wkConvdir = "";
    private String wkRtdir = "";
    private String wkReportdir = "";
    private String wkPreRcptid = "";
    private String putfnRcptid;
    private String putfnUserdata;
    private String putfnTxtype;
    private String area361019R;
    private String sunit361019;
    private String runit361019;
    private String type361019;
    private String char361019;
    private String waterno361019;
    private String stat361019;
    private String station361019R;
    private String rc361019;
    private String chk361019;
    private String rname361019;
    private String rt361019Rc;
    private String rt361019Pbrno;
    private String rt361019Batno;
    private String rt361019Seq;
    private String rt361019Date;
    private String rt361019Type;
    private String rtname361019R;
    private String rt361019Filler;
    private String rt361019Runit;
    private String rt361019Account;
    private String rec361019;
    private String rt361019Rec;
    private String rt361019Sname;
    private String rt361019Rname;
    private String sortPath;

    /* -- BigDecimal -- */
    private BigDecimal wkUnitamt = new BigDecimal(0);
    private BigDecimal wkSubamt = new BigDecimal(0);
    private BigDecimal wkTotamt = new BigDecimal(0);
    private BigDecimal wkRttotamt = new BigDecimal(0);
    private BigDecimal wkSubfee = new BigDecimal(0);
    private BigDecimal wkRtfee = new BigDecimal(0);
    private BigDecimal wkSubliq = new BigDecimal(0);
    private BigDecimal wkDiv = new BigDecimal(0);
    private BigDecimal putfnAmt;
    private BigDecimal putfnTotamt;
    private BigDecimal rtfee361019R;
    private BigDecimal totamt361019;
    private BigDecimal totamt361019R;
    private BigDecimal totfee361019R;
    private BigDecimal amt361019;
    private BigDecimal liq361019R;
    private BigDecimal totliq361019R;
    private BigDecimal rt361019Amt;
    private BigDecimal rt361019Totamt;
    private BigDecimal ctamt361019R2;
    private BigDecimal amt361019R;
    private BigDecimal fee361019R;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV361019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV361019Lsnr onApplicationEvent");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV361019 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV361019Lsnr run");

        this.event = event;

        // 開啟檔案
        // 024600     OPEN    INPUT       FD-BHDATE.
        // 024700     OPEN    INPUT       FD-CLNDR .
        // 024800     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.

        // 讀批次日期檔，若讀不到，顯示訊息，結束程式
        // 024900     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        // 025000          STOP RUN.
        // 作業日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);

        // 設定本營業日、設定檔名日期變數值
        // 025100     MOVE    FD-BHDATE-TBSDY         TO   WK-RTDATE,
        // 025200                                          WK-FDATE ,WK-CDATE  ,
        // 025300                                          WK-YYYMMDD          ,
        // 025400                                          361019-PDATE-R1     ,
        // 025500                                          361019-PDATE-R2     .
        wkRtdate = processDateInt;
        wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        wkCdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        wkYYYMMDD = formatUtil.pad9(String.valueOf(processDate), 8);
        pdate361019R1 = processDateInt;
        pdate361019R2 = processDateInt;

        sortPath = fileDir + PUTFN + wkCdate + STR_22C4361019;
        checkSortPath();

        // 設定檔名變數,檔名
        // 025700     MOVE        "22C4361019"        TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = STR_22C4361019;
        wkConvfile = STR_22C4361019;
        // 025800     MOVE        "FCL005361019"      TO   WK-RTFILE.
        wkRtfile = STR_FCL005361019;
        // 025900     CHANGE  ATTRIBUTE FILENAME OF FD-PUTFN      TO WK-PUTDIR .
        // 026000     CHANGE  ATTRIBUTE FILENAME OF FD-361019     TO WK-CONVDIR.
        // 026100     CHANGE  ATTRIBUTE FILENAME OF FD-361019-RT  TO WK-RTDIR  .
        /*
           010700 01 WK-PUTDIR.
           010800  03 FILLER                            PIC X(17)
           010900                         VALUE "DATA/CL/BH/PUTFN/".
           011000  03 WK-FDATE                          PIC 9(06).
           011100  03 FILLER                            PIC X(01) VALUE "/".
           011200  03 WK-PUTFILE                        PIC X(10).
           011300  03 FILLER                            PIC X(01) VALUE ".".
        */
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + PUTFN
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
                        + PUTFN
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        /*
           011400 01 WK-CONVDIR.
           011500  03 FILLER                            PIC X(17)
           011600                         VALUE "DATA/CL/BH/CONVF/".
           011700  03 WK-CDATE                          PIC 9(06).
           011800  03 FILLER                            PIC X(01) VALUE "/".
           011900  03 WK-CONVFILE                       PIC X(10).
           012000  03 FILLER                            PIC X(01) VALUE ".".
        */
        wkConvdir = fileDir + CONVF + wkCdate + PATH_SEPARATOR + wkConvfile;
        /*
           012200 01 WK-RTDIR.
           012300  03 FILLER                            PIC X(22)
           012400                         VALUE "DATA/GN/DWL/CL005/010/".
           012500  03 WK-RTDATE                         PIC 9(07).
           012600  03 FILLER                            PIC X(01) VALUE "/".
           012700  03 WK-RTFILE                         PIC X(12).
           012800  03 FILLER                            PIC X(01) VALUE ".".
        */
        wkRtdir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CL005_010
                        + wkRtdate
                        + PATH_SEPARATOR
                        + wkRtfile;

        wkReportdir =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-038";

        // FD-PUTFN檔案存在，執行361019-RTN
        // 026200     IF  ATTRIBUTE  RESIDENT    OF FD-PUTFN IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 026300         PERFORM   361019-RTN  THRU  361019-EXIT.
            rtn361019();
        }

        // 關閉檔案、顯示訊息、結束程式
        // 026600     CLOSE   FD-BHDATE  WITH SAVE.
        // 026700     CLOSE   FD-CLNDR   WITH SAVE.
        // 026800     DISPLAY "SYM/CL/BH/CONV361019 COMPLETE".
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/CONV361019 COMPLETE");

        batchResponse();
    }

    private void checkSortPath() {
        if (textFile.exists(sortPath)) {
            sort361019();
        }
    }

    private void sort361019() {
        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 2, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET_UTF8);
    }

    private void rtn361019() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rtn361019()");
        // 開啟檔案
        // 027200     OPEN     INPUT     FD-PUTFN .
        // 027300     OPEN    OUTPUT     FD-361019.
        // 027400     OPEN    OUTPUT     REPORTFL .

        // 清變數
        // 027500     MOVE    SPACES             TO     WK-PRE-RCPTID    .
        wkPreRcptid = SPACE;

        // 027600 361019-RNEXT.

        // 循序讀取FD-PUTFN，直到檔尾，跳到361019-CLOSE
        // 027700     READ   FD-PUTFN   AT  END  GO TO  361019-CLOSE.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET_UTF8);
        for (String detail : lines) {

            /*
            01 PUTFN-REC TOTAL 160 BYTES
              03  PUTFN-CTL	            9(02)  0-2
              03  PUTFN-CTL-R	REDEFINES PUTFN-CTL
                   05 PUTFN-CTL1	        9(01)  0-1
                   05 PUTFN-CTL2	        9(01)  1-2
              03  PUTFN-CODE	            X(06)  2-8
              03  PUTFN-DATA.	GROUP
                   05 PUTFN-RCPTID	    X(26)  8-34
                   05 PUTFN-DATE	        9(08)  34-42
                   05 PUTFN-TIME	        9(06)  42-48
                   05 PUTFN-CLLBR	        9(03)  48-51
                   05 PUTFN-LMTDATE	    9(08)  51-59
                   05 PUTFN-OLDAMT	    9(10)  59-69
                   05 PUTFN-USERDATA	    X(40)  69-109
                   05 PUTFN-SITDATE	    9(08)  109-117
                   05 PUTFN-TXTYPE	        X(01)  117-118
                   05 PUTFN-AMT	        9(12)  118-130
                   05 PUTFN-FILLER	    X(30)  130-160
               03  PUTFN-DATA-R       REDEFINES PUTFN-DATA
                   05 PUTFN-BDATE	        9(08)  8-16
                   05 PUTFN-EDATE	        9(08)  16-24
                   05 PUTFN-TOTCNT	        9(06)  24-30
                   05 PUTFN-TOTAMT	        9(13)  30-43
                   05 FILLER	X(117)             43-160
              03  PUTFN-DATA-NODATA	REDEFINES PUTFN-DATA
                   05 PUTFN-NODATA	        X(26)  8-34
                   05 PUTFN-FILLER1        X(126)  34-160
            */
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);

            putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : STR_0);

            putfnAmt =
                    parse.string2BigDecimal(
                            (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : STR_0));
            putfnSitdate =
                    parse.string2Integer(
                            (parse.isNumeric(filePutfn.getSitdate())
                                    ? filePutfn.getSitdate()
                                    : STR_0));
            putfnCllbr =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCllbr()) ? filePutfn.getCllbr() : STR_0);
            putfnRcptid = filePutfn.getRcptid();
            putfnUserdata = filePutfn.getUserdata();
            putfnTxtype = filePutfn.getTxtype();
            putfnLmtdate =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getLmtdate())
                                    ? filePutfn.getLmtdate()
                                    : STR_0);
            putfnTotcnt =
                    parse.string2Integer(
                            parse.isNumeric(fileSumPUTFN.getTotcnt())
                                    ? fileSumPUTFN.getTotcnt()
                                    : STR_0);
            putfnTotamt =
                    parse.string2BigDecimal(
                            parse.isNumeric(fileSumPUTFN.getTotamt())
                                    ? fileSumPUTFN.getTotamt()
                                    : STR_0);

            // IF PUTFN-CTL = 11,寫銷帳明細報表,媒體檔(FD-361019)
            // IF PUTFN-CTL = 12,寫銷帳匯總報表,媒體檔
            // IF PUTFN-CTL = 21,寫對帳明細報表,匯款媒體檔(FD-361019-RT)
            // IF PUTFN-CTL = 22,寫對帳會總報表,匯款媒體檔
            // 027800     IF     PUTFN-CTL           =      11
            // 027900       PERFORM 2200-SWHSTIN-RTN THRU   2200-SWHSTIN-EXIT
            // 028000       PERFORM 2100-SWHAREA-RTN THRU   2100-SWHAREA-EXIT
            // 028100       PERFORM 2000-DTL-RTN     THRU   2000-DTL-EXIT       .
            // 028300     IF     PUTFN-CTL           =      12
            // 028400       PERFORM 3000-SUM-RTN     THRU   3000-SUM-EXIT       .
            // 028600     IF     PUTFN-CTL           =      21
            // 028700       PERFORM 4200-SWHSTIN-RTN THRU   4200-SWHSTIN-EXIT
            // 028800       PERFORM 4100-SWHAREA-RTN THRU   4100-SWHAREA-EXIT
            // 028900       PERFORM 4000-DTL-RTN     THRU   4000-DTL-EXIT       .
            // 029100     IF     PUTFN-CTL           =      22
            // 029200       PERFORM 5000-SUM-RTN     THRU   5000-SUM-EXIT       .
            switch (putfnCtl) {
                case 11:
                    swhsitn2200();
                    swhsitn2100();
                    dtl2000();
                    break;

                case 12:
                    sum3000();
                    break;

                case 21:
                    swhsitn4200();
                    swharea4100();
                    dtl4000();
                    break;

                case 22:
                    sum5000();
                    break;

                default:
                    break;
            }
            // 029400     GO TO 361019-RNEXT .
        }
        // 029600 361019-CLOSE.
        // 關閉檔案
        // 029700     CLOSE FD-PUTFN           .
        // 029800     CHANGE ATTRIBUTE FILENAME OF FD-361019 TO WK-PUTDIR   .
        // 029900     CLOSE REPORTFL  WITH SAVE.
        // 030000     CLOSE FD-361019 WITH SAVE.
        textFile.deleteFile(wkPutdir);
        textFile.deleteFile(wkReportdir);
        try {
            textFile.writeFileContent(wkPutdir, file361019Contents, CHARSET_UTF8);
            upload(wkPutdir, "DATA", "PUTFN");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(wkReportdir, fileReportflContents, CHARSET_BIG5);
            upload(wkReportdir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    // duplicate functions 361019-GATELINE2
    private void gateLine2() {
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 90));
        fileReportflContents.add(sb.toString());
    }

    private void swhsitn2200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "swhsitn2200()");
        // 寫媒體檔,報表(表頭)
        // 043500     IF     WK-PRE-RCPTID        =       SPACES
        if (wkPreRcptid.trim().isEmpty()) {
            // 043600       PERFORM 1000-HEAD-RTN     THRU    1000-HEAD-EXIT
            head1000();
            // 043700       PERFORM 8000-TITLE-RTN    THRU    8000-TITLE-EXIT
            title8000();
            // 043800       MOVE PUTFN-RCPTID(1:1)    TO      361019-AREA-R
            area361019R = putfnRcptid.substring(0, 1);
            // 043900       PERFORM 8100-TITLE-RTN    THRU    8100-TITLE-EXIT
            title8100();
            // 044000       GO TO 2200-SWHSTIN-EXIT.
            return;
        }
        // 044100     IF     WK-PRE-RCPTID(1:2)   NOT =   PUTFN-RCPTID(1:2)
        if (!wkPreRcptid.substring(0, 2).equals(putfnRcptid.substring(0, 2))) {
            // 044200       PERFORM 2400-RPTDTL-RTN   THRU    2400-RPTDTL-EXIT
            rptdtl2400();
            // 044300       MOVE    0                 TO      WK-UNITCNT,
            // 044400                                         WK-UNITAMT.
            wkUnitcnt = 0;
            wkUnitamt = BigDecimal.valueOf(0);
        }
    }

    // duplicate moving functions .
    private void moving0() {
        // MOVE    0                   TO      WK-SUBCNT,WK-UNITCNT,
        //                                     WK-SUBAMT,WK-UNITAMT.
        wkSubcnt = 0;
        wkUnitcnt = 0;
        wkSubamt = BigDecimal.valueOf(0);
        wkUnitamt = BigDecimal.valueOf(0);
    }

    private void afterPage() {
        // WRITE   REPORT-LINE         AFTER   PAGE .
        sb = new StringBuilder();
        sb.append(PATH_SEPARATOR);
        fileReportflContents.add(sb.toString());
    }

    private void swhsitn2100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "swhsitn2100()");
        // 寫報表表尾,表頭
        // 037100     IF     WK-PRE-RCPTID          =       SPACES
        // 037200       GO TO 2100-SWHAREA-EXIT.
        if (wkPreRcptid.trim().isEmpty()) {
            return;
        }
        // 037300     IF     WK-PRE-RCPTID(1:1)     NOT =   PUTFN-RCPTID(1:1)
        if (!wkPreRcptid.substring(0, 1).equals(putfnRcptid.substring(0, 1))) {
            // 037400       MOVE    SPACES              TO      REPORT-LINE
            // 037500       WRITE   REPORT-LINE         FROM    361019-GATELINE2
            gateLine2();
            // 037600       PERFORM 2300-RPTSUM-RTN     THRU    2300-RPTSUM-EXIT
            rptsum2300();

            // 037700       MOVE    0                   TO      WK-SUBCNT,WK-UNITCNT,
            // 037800                                           WK-SUBAMT,WK-UNITAMT
            moving0();

            // 037900       MOVE    SPACES              TO      REPORT-LINE
            // 038000       WRITE    REPORT-LINE        AFTER   PAGE
            afterPage();
            // 038100       PERFORM 8000-TITLE-RTN      THRU    8000-TITLE-EXIT
            title8000();

            // 038200       MOVE    PUTFN-RCPTID(1:1)   TO      361019-AREA-R
            area361019R = putfnRcptid.substring(0, 1);

            // 038300       PERFORM 8100-TITLE-RTN      THRU    8100-TITLE-EXIT  .
            title8100();
        }
    }

    // duplicate functions .
    private void subroutine() {
        /*
           MOVE    "004     "        TO      361019-SUNIT .
           MOVE    "TWA     "        TO      361019-RUNIT .
           MOVE    "101"             TO      361019-TYPE  .
        */
        sunit361019 = STR_004;
        runit361019 = STR_TWA;
        type361019 = STR_101;
    }

    // duplicate WRITE   361019-REC .
    private void write361019RecR1() {
        sb = new StringBuilder();
        // 004500 01  361019-REC.
        // 004600     03  361019-RC                     PIC X(01).
        sb.append(formatUtil.padX(rc361019, 1));
        // 004700     03  361019-SUNIT                  PIC X(08).
        sb.append(formatUtil.padX(sunit361019, 8));
        // 004800     03  361019-RUNIT                  PIC X(08).
        sb.append(formatUtil.padX(runit361019, 8));
        // 004900     03  361019-TYPE                   PIC X(03).
        sb.append(formatUtil.padX(type361019, 3));
        // 005000     03  361019-SDATE                  PIC 9(07).
        sb.append(formatUtil.pad9(String.valueOf(sdate361019), 7));
        // 005100     03  361019-1.
        // 005200       05  361019-CHAR                 PIC X(01).
        // 005300       05  FILLER                      PIC X(92).
        sb.append(formatUtil.padX(char361019, 1));
        sb.append(formatUtil.padX(SPACE, 92));
        file361019Contents.add(sb.toString());
    }

    // duplicate WRITE   361019-REC .
    private void write361019RecR2() {
        sb = new StringBuilder();
        // 004500 01  361019-REC.
        // 004600     03  361019-RC                     PIC X(01).
        sb.append(formatUtil.padX(rc361019, 1));
        // 004700     03  361019-SUNIT                  PIC X(08).
        sb.append(formatUtil.padX(sunit361019, 8));
        // 004800     03  361019-RUNIT                  PIC X(08).
        sb.append(formatUtil.padX(runit361019, 8));
        // 004900     03  361019-TYPE                   PIC X(03).
        sb.append(formatUtil.padX(type361019, 3));
        // 005000     03  361019-SDATE                  PIC 9(07).
        sb.append(formatUtil.pad9(String.valueOf(sdate361019), 7));
        // 005400     03  361019-2     REDEFINES         361019-1.
        // 005500       05  361019-STAT                 PIC X(01).
        // 005600       05  361019-CLLBR                PIC 9(03).
        // 005700       05  FILLER                      PIC X(12).
        // 005800       05  361019-AMT                  PIC 9(12)V99.
        // 005900       05  361019-CHK                  PIC X(01).
        // 006000       05  FILLER                      PIC X(04).
        // 006100       05  361019-TXTYPE               PIC 9(02).
        // 006200       05  361019-WATERNO              PIC X(11).
        // 006300       05  361019-WORKAREA             PIC 9(04).
        // 006400       05  361019-LMTDATE              PIC 9(07).
        // 006500       05  361019-YYYMM                PIC 9(05).
        // 006600       05  FILLER                      PIC X(29).
        sb.append(formatUtil.padX(stat361019, 1));
        sb.append(formatUtil.pad9(String.valueOf(cllbr361019), 3));
        sb.append(formatUtil.padX(SPACE, 12));
        sb.append(formatUtil.pad9(String.valueOf(amt361019), 12) + "00");
        sb.append(formatUtil.padX(chk361019, 1));
        sb.append(formatUtil.padX(SPACE, 4));
        sb.append(formatUtil.pad9(String.valueOf(txtype361019), 2));
        sb.append(formatUtil.padX(waterno361019, 11));
        sb.append(formatUtil.pad9(workarea361019, 4));
        sb.append(formatUtil.pad9(String.valueOf(lmtdate361019), 7));
        sb.append(formatUtil.pad9(YYYMM361019, 5));
        sb.append(formatUtil.padX(SPACE, 29));
        file361019Contents.add(sb.toString());
    }

    // duplicate WRITE   361019-REC .
    private void write361019RecR3() {
        sb = new StringBuilder();
        // 004500 01  361019-REC.
        // 004600     03  361019-RC                     PIC X(01).
        sb.append(formatUtil.padX(rc361019, 1));
        // 004700     03  361019-SUNIT                  PIC X(08).
        sb.append(formatUtil.padX(sunit361019, 8));
        // 004800     03  361019-RUNIT                  PIC X(08).
        sb.append(formatUtil.padX(runit361019, 8));
        // 004900     03  361019-TYPE                   PIC X(03).
        sb.append(formatUtil.padX(type361019, 3));
        // 005000     03  361019-SDATE                  PIC 9(07).
        sb.append(formatUtil.pad9(String.valueOf(sdate361019), 7));
        // 006700     03  361019-3     REDEFINES         361019-1.
        // 006800       05  361019-TOTAMT               PIC 9(14)V99.
        // 006900       05  361019-TOTCNT               PIC 9(10).
        // 007000       05  FILLER                      PIC X(67).
        sb.append(formatUtil.pad9(String.valueOf(totamt361019), 16));
        sb.append(formatUtil.pad9(String.valueOf(totcnt361019), 10));
        sb.append(formatUtil.padX(SPACE, 67));
        file361019Contents.add(sb.toString());
    }

    private void _361019Tit0() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX(SPACE, 75));
        sb.append(formatUtil.padX("Bank Of Taiwan", 14));
        fileReportflContents.add(sb.toString());
    }

    private void _361019Tit3() {
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收項目： ", 20));
        sb.append(formatUtil.padX(SPACE, 15));
        sb.append(reportUtil.customFormat(String.valueOf(bdate361019R), "999/99/99"));
        sb.append(formatUtil.padX(" - ", 3));
        sb.append(reportUtil.customFormat(String.valueOf(edate361019R), "999/99/99"));
        sb.append(formatUtil.padX(SPACE, 30));
        fileReportflContents.add(sb.toString());
    }

    private void dtl2000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dtl2000()");
        // 搬PUTFN...到361019...(RC=2)明細
        // 034400     ADD     1         TO     WK-UNITCNT,WK-SUBCNT,WK-TOTCNT.
        wkUnitcnt++;
        wkSubcnt++;
        wkTotcnt++;
        // 034500     ADD     PUTFN-AMT TO     WK-UNITAMT,WK-SUBAMT,WK-TOTAMT.
        wkUnitamt = wkUnitamt.add(putfnAmt);
        wkSubamt = wkSubamt.add(putfnAmt);
        wkTotamt = wkTotamt.add(putfnAmt);
        // 034600     MOVE    SPACES              TO    361019-REC     .
        rec361019 = SPACE;
        // 034700     MOVE    "2"                 TO    361019-RC      .
        rc361019 = STR_2;
        // 034800     MOVE    "0"                 TO    361019-STAT    .
        stat361019 = STR_0;

        // 034900     MOVE    "004     "          TO    361019-SUNIT   .
        // 035000     MOVE    "TWA     "          TO    361019-RUNIT   .
        // 035100     MOVE    "101"               TO    361019-TYPE    .
        subroutine();

        // 035200     MOVE    PUTFN-SITDATE       TO    361019-SDATE   .
        sdate361019 = putfnSitdate;
        // 035300     MOVE    PUTFN-CLLBR         TO    361019-CLLBR   .
        cllbr361019 = putfnCllbr;
        // 035400     MOVE    PUTFN-AMT           TO    361019-AMT     .
        amt361019 = putfnAmt;
        // 035500     MOVE    PUTFN-RCPTID        TO    WK-PRE-RCPTID  .
        wkPreRcptid = putfnRcptid;
        // 035600     MOVE    PUTFN-USERDATA(6:1) TO    361019-CHK     .
        chk361019 = putfnUserdata.substring(5, 6);

        // 035700     IF PUTFN-TXTYPE="C" OR ="M" MOVE "32" TO 361019-TXTYPE.
        // 035720     IF PUTFN-TXTYPE="I" MOVE "35" TO 361019-TXTYPE
        // 035740        IF PUTFN-USERDATA(23:2) = "37" MOVE "37" TO 361019-TXTYPE
        // 035760        IF PUTFN-USERDATA(23:2) = "39" MOVE "39" TO 361019-TXTYPE.
        // 036100     IF PUTFN-TXTYPE="A"         MOVE "33" TO 361019-TXTYPE.
        // 036200     MOVE    PUTFN-RCPTID(1:11)  TO    361019-WATERNO .
        // 036300     MOVE    PUTFN-RCPTID(12:4)  TO    361019-WORKAREA.
        // 036400     MOVE    PUTFN-LMTDATE       TO    361019-LMTDATE .
        // 036500     MOVE    PUTFN-USERDATA(1:5) TO    361019-YYYMM   .
        if (STR_C.equals(putfnTxtype) || STR_M.equals(putfnTxtype)) {
            txtype361019 = parse.string2Integer(STR_32);
        }
        if (STR_I.equals(putfnTxtype)) {
            txtype361019 = parse.string2Integer(STR_35);
            if (STR_37.equals(putfnUserdata.substring(22, 24))) {
                txtype361019 = parse.string2Integer(STR_37);
            }
            if (STR_39.equals(putfnUserdata.substring(22, 24))) {
                txtype361019 = parse.string2Integer(STR_39);
            }
        }
        if (STR_A.equals(putfnTxtype)) {
            txtype361019 = parse.string2Integer(STR_33);
        }

        waterno361019 = putfnRcptid.substring(0, 11);
        workarea361019 = putfnRcptid.substring(11, 15);
        lmtdate361019 = putfnLmtdate;
        YYYMM361019 = putfnUserdata.substring(0, 5);

        // 036600     WRITE   361019-REC.
        write361019RecR2();
    }

    private void sum3000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sum3000()");
        // 046200 3000-SUM-RTN.
        // 搬相關資料到361019-REC(RC=3)
        // 046300     MOVE    SPACES              TO    361019-REC      .
        // 046400     MOVE    "3"                 TO    361019-RC       .
        rc361019 = STR_3;
        // 046500     MOVE    "004     "          TO    361019-SUNIT    .
        // 046600     MOVE    "TWA     "          TO    361019-RUNIT    .
        // 046700     MOVE    "101"               TO    361019-TYPE     .
        subroutine();
        // 046800     MOVE    WK-YYYMMDD          TO    361019-SDATE    .
        // 046900     MOVE    PUTFN-TOTCNT        TO    361019-TOTCNT   .
        // 047000     MOVE    PUTFN-TOTAMT        TO    361019-TOTAMT   .
        sdate361019 = parse.string2Integer(wkYYYMMDD);
        totcnt361019 = putfnTotcnt;
        totamt361019 = putfnTotamt;
        // 047100     WRITE   361019-REC.
        // 004500 01  361019-REC.
        // 004600     03  361019-RC                     PIC X(01).
        // 004700     03  361019-SUNIT                  PIC X(08).
        // 004800     03  361019-RUNIT                  PIC X(08).
        // 004900     03  361019-TYPE                   PIC X(03).
        // 005000     03  361019-SDATE                  PIC 9(07).
        // 006700     03  361019-3     REDEFINES         361019-1.
        // 006800       05  361019-TOTAMT               PIC 9(14)V99.
        // 006900       05  361019-TOTCNT               PIC 9(10).
        // 007000       05  FILLER                      PIC X(67).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(rc361019, 1));
        sb.append(formatUtil.padX(sunit361019, 8));
        sb.append(formatUtil.padX(runit361019, 8));
        sb.append(formatUtil.padX(type361019, 3));
        sb.append(formatUtil.pad9(String.valueOf(sdate361019), 7));
        sb.append(formatUtil.pad9(String.valueOf(totamt361019), 14) + "00");
        sb.append(formatUtil.pad9(String.valueOf(totcnt361019), 10));
        sb.append(formatUtil.padX(SPACE, 67));
        file361019Contents.add(sb.toString());
        // 047200* 報表
        // 047300     PERFORM 2400-RPTDTL-RTN     THRU    2400-RPTDTL-EXIT .
        rptdtl2400();
        // 047400     MOVE    SPACES              TO      REPORT-LINE      .
        // 047500     WRITE   REPORT-LINE         FROM    361019-GATELINE2 .
        gateLine2();
        // 047600     PERFORM 2300-RPTSUM-RTN     THRU    2300-RPTSUM-EXIT .
        rptsum2300();
        // 047700     MOVE    0                   TO      WK-SUBCNT,WK-UNITCNT,
        // 047800                                         WK-SUBAMT,WK-UNITAMT.
        moving0();
        // 047900     MOVE    SPACES              TO      WK-PRE-RCPTID    .
        wkPreRcptid = SPACE;
        // 048000 3000-SUM-EXIT.
    }

    private void sum5000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sum5000()");
        // 寫彙總報表,媒體檔
        // 053300* 報表
        // 053400     PERFORM 2400-RPTDTL-RTN     THRU    2400-RPTDTL-EXIT .
        rptdtl2400();
        // 053500     MOVE    SPACES              TO      REPORT-LINE      .
        // 053600     WRITE   REPORT-LINE         FROM    361019-GATELINE2 .
        gateLine2();
        // 053700     PERFORM 2300-RPTSUM-RTN     THRU    2300-RPTSUM-EXIT .
        rptsum2300();
        // 053800     PERFORM 7100-RTDTL-RTN      THRU    7100-RTDTL-EXIT  .
        rtdtl7100();
        // 053900     ADD     WK-SUBLIQ           TO      WK-RTTOTAMT      .
        wkRttotamt = wkRttotamt.add(wkSubliq);
        // 054000     PERFORM 7200-RTTAIL-RTN     THRU    7200-RTTAIL-EXIT .
        rttail7200();
        // 054100     MOVE    0                   TO      WK-SUBCNT,WK-UNITCNT,
        // 054200                                         WK-SUBAMT,WK-UNITAMT.
        moving0();

        // 054300     CLOSE   FD-361019-RT        WITH    SAVE.

        try {
            textFile.writeFileContent(wkRtdir, file361019RTContents, CHARSET_UTF8);
            upload(wkRtdir, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void swhsitn4200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "swhsitn4200()");
        // 寫媒體檔,報表(表頭)
        // 051500     IF     WK-PRE-RCPTID        =       SPACES
        if (SPACE.equals(wkPreRcptid)) {
            // 051600       MOVE    SPACES            TO      REPORT-LINE
            // 051700       WRITE   REPORT-LINE       AFTER   PAGE
            afterPage();
            // 051800       MOVE    PUTFN-RCPTID(1:1) TO      361019-AREA-R
            area361019R = putfnRcptid.substring(0, 1);
            // 開啟檔案
            // 051900       OPEN    OUTPUT            FD-361019-RT

            // 052000       PERFORM 7000-RTHEAD-RTN   THRU    7000-RTHEAD-EXIT
            rthead7000();
            // 052100       PERFORM 9000-TITLE-RTN    THRU    9000-TITLE-EXIT
            title9000();
            // 052200       PERFORM 8100-TITLE-RTN    THRU    8100-TITLE-EXIT
            title8100();

            // 052300       GO TO 4200-SWHSTIN-EXIT.
            return;
        }

        // 052400     IF     WK-PRE-RCPTID(1:2)   NOT =   PUTFN-RCPTID(1:2)
        if (!wkPreRcptid.substring(0, 2).equals(putfnRcptid.substring(0, 2))) {
            // 052500       PERFORM 2400-RPTDTL-RTN   THRU    2400-RPTDTL-EXIT
            rptdtl2400();
            // 052600       MOVE    0                 TO      WK-UNITCNT,
            // 052700                                         WK-UNITAMT.
            wkUnitcnt = 0;
            wkUnitamt = BigDecimal.valueOf(0);
        }
    }

    private void swharea4100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "swharea4100()");
        // 寫報表
        // 049200     IF        WK-PRE-RCPTID       =       SPACES
        if (SPACE.equals(wkPreRcptid)) {
            // 049300       ADD     1                   TO      WK-RTTOTCNT
            wkRttotcnt++;
            // 049400       GO TO 4100-SWHAREA-EXIT.
            return;
        }
        // 049600     IF     WK-PRE-RCPTID(1:1)     NOT =   PUTFN-RCPTID(1:1)
        if (!wkPreRcptid.substring(0, 1).equals(putfnRcptid.substring(0, 1))) {
            // 049700       MOVE    SPACES              TO      REPORT-LINE
            // 049800       WRITE   REPORT-LINE         FROM    361019-GATELINE2
            gateLine2();
        }

        // 寫表尾
        // 049900       PERFORM 2300-RPTSUM-RTN     THRU    2300-RPTSUM-EXIT
        rptsum2300();

        // 寫明細
        // 050000       PERFORM 7100-RTDTL-RTN      THRU    7100-RTDTL-EXIT
        rtdtl7100();
        // 050100       ADD     1                   TO      WK-RTTOTCNT
        wkRttotcnt++;
        // 050200       ADD     WK-SUBLIQ           TO      WK-RTTOTAMT
        wkRttotamt = wkRttotamt.add(wkSubliq);
        // 050300       MOVE    0                   TO      WK-SUBCNT,WK-UNITCNT,
        // 050400                                           WK-SUBAMT,WK-UNITAMT,
        // 050500                                           WK-RTFEE
        wkSubcnt = 0;
        wkUnitcnt = 0;
        wkSubamt = BigDecimal.valueOf(0);
        wkUnitamt = BigDecimal.valueOf(0);
        wkRtfee = BigDecimal.valueOf(0);

        // 050600       MOVE    SPACES              TO      REPORT-LINE
        // 050700       WRITE   REPORT-LINE         AFTER   PAGE
        afterPage();

        // 寫報表表頭
        // 050800       PERFORM 9000-TITLE-RTN      THRU    9000-TITLE-EXIT
        title9000();

        // 050900       MOVE    PUTFN-RCPTID(1:1)   TO      361019-AREA-R
        area361019R = putfnRcptid.substring(0, 1);

        // 051000       PERFORM 8100-TITLE-RTN      THRU    8100-TITLE-EXIT  .
        title8100();
    }

    private void dtl4000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dtl4000()");
        // 累加筆數金額
        // 048400     ADD     1         TO     WK-UNITCNT,WK-SUBCNT,WK-TOTCNT.
        wkUnitcnt++;
        wkSubcnt++;
        wkTotcnt++;

        // 048500     ADD     PUTFN-AMT TO     WK-UNITAMT,WK-SUBAMT,WK-TOTAMT.
        wkUnitamt = wkUnitamt.add(putfnAmt);
        wkSubamt = wkSubamt.add(putfnAmt);
        wkTotamt = wkTotamt.add(putfnAmt);

        // 048600     MOVE    PUTFN-RCPTID        TO    WK-PRE-RCPTID  .
        wkPreRcptid = putfnRcptid;
    }

    // WRITE 361019-RT-REC
    private void write361019RtRec() {
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(String.valueOf(rt361019Rc), 1));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Pbrno), 3));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Batno), 10));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Seq), 5));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Date), 6));
        sb.append(formatUtil.padX(SPACE, 23));
        sb.append(formatUtil.padX(rt361019Sname, 68));
        sb.append(formatUtil.padX(SPACE, 84));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Runit), 7));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Type), 2));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Account), 14));
        sb.append(formatUtil.padX(rt361019Rname, 68));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Amt), 12));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Filler), 2));
        sb.append(formatUtil.padX(SPACE, 52));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Totcnt), 4));
        sb.append(formatUtil.pad9(String.valueOf(rt361019Totamt), 14));
        sb.append(formatUtil.padX(SPACE, 157));
        file361019RTContents.add(sb.toString());
    }

    private void rtSum() {
        /*
           MOVE  010                   TO      361019-RT-PBRNO.
           MOVE  WK-YYYMMDD(2:6)       TO      361019-RT-BATNO(1:6).
           MOVE  "8001"                TO      361019-RT-BATNO(7:4).
        */
        rt361019Pbrno = "010";
        rt361019Batno = wkYYYMMDD.substring(1, 7) + "8001";
    }

    private void rthead7000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rthead7000()");
        // 搬資料到361019-RT-REC(RC=1)
        // 062600     MOVE  SPACES                TO      361019-RT-REC  .
        // 062700     MOVE  "1"                   TO      361019-RT-RC   .
        rt361019Rc = STR_1;
        rtSum();
        // 063100     MOVE  99999                 TO      361019-RT-SEQ  .
        rt361019Seq = STR_99999;
        // 063200     MOVE  WK-YYYMMDD(2:6)       TO      361019-RT-DATE .
        // 063300     MOVE  " 臺灣銀行臺中分行 "  TO      361019-RT-SNAME.
        rt361019Date = wkYYYMMDD.substring(1, 7);
        rt361019Sname = " 臺灣銀行臺中分行 ";
        // 063400     WRITE 361019-RT-REC.
        write361019RtRec();
    }

    private void title9000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "title9000()");
        // 寫週報表頭
        // 055000     PERFORM 6100-POINTTBSDY-RTN THRU    6100-POINTTBSDY-EXIT.
        pointtbsdy6100();
        // 055100* 找到本週星期一日期
        // 055200     PERFORM 6000-MONDAY-RTN     THRU    6000-MONDAY-EXIT.
        monday6000();
        // 055400     MOVE    SPACES              TO      REPORT-LINE.
        // 055500     WRITE   REPORT-LINE         FROM    361019-TIT0.
        _361019Tit0();

        // 055600     MOVE    SPACES              TO      REPORT-LINE.
        // 055700     WRITE   REPORT-LINE         FROM    361019-TIT1.
        // 014900 01   361019-TIT1.
        sb = new StringBuilder();
        // 015000  03  FILLER      PIC X(20) VALUE " 代收單位：臺灣銀行 ".
        sb.append(formatUtil.padX(" 代收單位：臺灣銀行 ", 20));
        // 015100  03  FILLER                           PIC X(12) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 12));
        // 015200  03  FILLER      PIC X(35) VALUE " 五　日（週）週　結　清　單 ".
        sb.append(formatUtil.padX(" 五　日（週）週　結　清　單 ", 35));
        // 015300  03  FILLER      PIC X(10) VALUE " 印表日： ".
        sb.append(formatUtil.padX(" 印表日： ", 10));
        // 015400  03  361019-PDATE-R1                  PIC 999/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(pdate361019R1), "999/99/99"));
        fileReportflContents.add(sb.toString());
        // 055800     MOVE    SPACES              TO      REPORT-LINE.
        // 055900     MOVE    WK-MONDAY           TO      361019-BDATE-R .
        bdate361019R = wkMonday;
        // 056000     MOVE    WK-YYYMMDD          TO      361019-EDATE-R .
        edate361019R = parse.string2Integer(wkYYYMMDD);
        // 056100     WRITE   REPORT-LINE         FROM    361019-TIT3.
        _361019Tit3();
    }

    private void head1000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "head1000()");
        // 搬相關檔案到361019-REC(RC=1)
        // 030600     MOVE    SPACES            TO      361019-REC   .
        rec361019 = SPACE;
        // 030700     MOVE    1                 TO      361019-RC    .
        rc361019 = STR_1;

        // 030800     MOVE    "004     "        TO      361019-SUNIT .
        // 030900     MOVE    "TWA     "        TO      361019-RUNIT .
        // 031000     MOVE    "101"             TO      361019-TYPE  .
        subroutine();

        // 031100     MOVE    WK-YYYMMDD        TO      361019-SDATE .
        sdate361019 = parse.string2Integer(wkYYYMMDD);
        // 031200     MOVE    "1"               TO      361019-CHAR  .
        char361019 = STR_1;
        // 031300     WRITE   361019-REC.
        write361019RecR1();
    }

    private void title8000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "title8000()");
        // 寫REPORTFL報表表頭(TIT0~3)
        // 031800     MOVE    SPACES              TO      REPORT-LINE.
        // 031900     WRITE   REPORT-LINE         FROM    361019-TIT0.
        _361019Tit0();
        // 032000     MOVE    SPACES              TO      REPORT-LINE.
        // 032100     WRITE   REPORT-LINE         FROM    361019-TIT2.
        // 015500 01   361019-TIT2.
        sb = new StringBuilder();
        // 015600  03  FILLER      PIC X(20) VALUE " 代收單位：臺灣銀行 ".
        sb.append(formatUtil.padX(" 代收單位：臺灣銀行 ", 20));
        // 015700  03  FILLER                           PIC X(17) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 17));
        // 015800  03  FILLER      PIC X(30) VALUE " 日　結　清　單 ".
        sb.append(formatUtil.padX(" 日　結　清　單 ", 30));
        // 015900  03  FILLER      PIC X(10) VALUE " 印表日： ".
        sb.append(formatUtil.padX(" 印表日： ", 10));
        // 016000  03  361019-PDATE-R2                  PIC 999/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(pdate361019R2), "999/99/99"));
        fileReportflContents.add(sb.toString());
        // 032200     MOVE    SPACES              TO      REPORT-LINE.
        // 032300     MOVE    WK-YYYMMDD          TO      361019-BDATE-R .
        bdate361019R = parse.string2Integer(wkYYYMMDD);
        // 032400     MOVE    WK-YYYMMDD          TO      361019-EDATE-R .
        edate361019R = parse.string2Integer(wkYYYMMDD);
        // 032500     WRITE   REPORT-LINE         FROM    361019-TIT3.
        _361019Tit3();
    }

    private void title8100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "title8100()");
        // 寫REPORTFL報表表頭(TIT4~6)
        // 033000     MOVE    SPACES              TO      REPORT-LINE.
        // 033100     WRITE   REPORT-LINE         FROM    361019-TIT4.
        // 016800 01   361019-TIT4.
        sb = new StringBuilder();
        // 016900  03  FILLER                           PIC X(26) VALUE
        // 017000      " 代收名稱：台灣自來水公司 ".
        sb.append(formatUtil.padX(" 代收名稱：台灣自來水公司 ", 26));
        // 017100  03  FILLER                           PIC X(51) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 51));
        fileReportflContents.add(sb.toString());

        // 033200     MOVE    SPACES              TO      REPORT-LINE.
        // 033300     WRITE   REPORT-LINE         FROM    361019-TIT5.
        // 017200 01   361019-TIT5.
        sb = new StringBuilder();
        // 017300  03  FILLER                       PIC X(12) VALUE " 區處別　： ".
        sb.append(formatUtil.padX(" 區處別　： ", 12));
        // 017400  03  361019-AREA-R                    PIC X(01).
        sb.append(formatUtil.padX(area361019R, 1));
        // 017500  03  FILLER                         PIC X(62) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 62));
        // 017600  03  FILLER                         PIC X(08) VALUE " 頁次： ".
        sb.append(formatUtil.padX(" 頁次： ", 8));
        // 017700  03  361019-PAGECNT-R                 PIC 9(04) VALUE 0001.
        sb.append(formatUtil.pad9("1", 4));
        fileReportflContents.add(sb.toString());

        // 033400     MOVE    SPACES              TO      REPORT-LINE.
        // 033500     WRITE   REPORT-LINE         FROM    361019-GATELINE1.
        // 017800 01   361019-GATELINE1.
        // 017900  03  FILLER                         PIC X(90) VALUE ALL "=".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("=", 90));
        fileReportflContents.add(sb.toString());

        // 033600     MOVE    SPACES              TO      REPORT-LINE.
        // 033700     WRITE   REPORT-LINE         FROM    361019-TIT6.
        // 018000 01   361019-TIT6.
        sb = new StringBuilder();
        // 018100  03  FILLER                         PIC X(08) VALUE " 站所別 ".
        sb.append(formatUtil.padX(" 站所別 ", 8));
        // 018200  03  FILLER                         PIC X(06) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 6));
        // 018300  03  FILLER                         PIC X(10) VALUE " 總金額 ".
        sb.append(formatUtil.padX(" 總金額 ", 10));
        // 018400  03  FILLER                         PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 018500  03  FILLER                         PIC X(10) VALUE " 總件數 ".
        sb.append(formatUtil.padX(" 總件數 ", 10));
        // 018600  03  FILLER                         PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 018700  03  FILLER                         PIC X(08) VALUE " 手續費 ".
        sb.append(formatUtil.padX(" 手續費 ", 8));
        // 018800  03  FILLER                         PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 7));
        // 018900  03  FILLER                         PIC X(10) VALUE " 入帳金額 ".
        sb.append(formatUtil.padX(" 入帳金額 ", 10));
        // 019000  03  FILLER                         PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 019100  03  FILLER                         PIC X(06) VALUE " 備註 ".
        sb.append(formatUtil.padX(" 備註 ", 6));
        // 019200  03  FILLER                         PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        fileReportflContents.add(sb.toString());

        // 033800     MOVE    SPACES              TO      REPORT-LINE.
        // 033900     WRITE   REPORT-LINE         FROM    361019-GATELINE2.
        gateLine2();
    }

    private void rptdtl2400() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rptdtl2400()");
        // 寫報表明細(手續費)
        // 044900     MOVE    WK-PRE-RCPTID(1:2)  TO      361019-STATION-R.
        station361019R = wkPreRcptid.substring(0, 2);
        // 045000     MOVE    WK-UNITCNT          TO      361019-CNT-R    .
        cnt361019R = wkUnitcnt;
        // 045100     MOVE    WK-UNITAMT          TO      361019-AMT-R    .
        amt361019R = wkUnitamt;
        // 045200     COMPUTE WK-SUBFEE = WK-UNITCNT * 2.5                .
        wkSubfee = BigDecimal.valueOf(wkUnitcnt).multiply(new BigDecimal("2.5"));
        // 045300     COMPUTE WK-SUBLIQ = WK-UNITAMT - WK-SUBFEE          .
        wkSubliq = wkUnitamt.subtract(wkSubfee);
        // 045400     MOVE    WK-SUBFEE           TO      361019-FEE-R    .
        fee361019R = wkSubfee;
        // 045500     MOVE    WK-SUBLIQ           TO      361019-LIQ-R    .
        liq361019R = wkSubliq;
        // 045600     MOVE    SPACES              TO      REPORT-LINE     .
        // 045700     WRITE   REPORT-LINE         FROM    361019-DTL      .
        // 019500 01   361019-DTL.
        sb = new StringBuilder();
        // 019600  03  FILLER                           PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 3));
        // 019700  03  361019-STATION-R                 PIC X(02)             .
        sb.append(formatUtil.padX(station361019R, 2));
        // 019800  03  FILLER                           PIC X(05) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 5));
        // 019900  03  361019-AMT-R                     PIC ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(amt361019R), "ZZZ,ZZZ,ZZ9"));
        // 020000  03  FILLER                           PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 7));
        // 020100  03  361019-CNT-R                     PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(cnt361019R), "ZZ,ZZ9"));
        // 020200  03  FILLER                           PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 7));
        // 020300  03  361019-FEE-R                     PIC ZZ,ZZ9.9.
        sb.append(reportUtil.customFormat(String.valueOf(fee361019R), "ZZ,ZZ9.9"));
        // 020400  03  FILLER                           PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 020500  03  361019-LIQ-R                     PIC ZZZ,ZZZ,ZZ9.9.
        sb.append(reportUtil.customFormat(String.valueOf(liq361019R), "ZZZ,ZZZ,ZZ9.9"));
        fileReportflContents.add(sb.toString());
    }

    private void rptsum2300() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rptsum2300()");
        // 寫報表表尾
        // 總筆數,金額
        // 038800     MOVE    0                   TO      WK-RTFEE         .
        wkRtfee = BigDecimal.valueOf(0);
        // 038900     MOVE    WK-SUBAMT           TO      361019-TOTAMT-R  .
        totamt361019R = wkSubamt;
        // 039000     MOVE    WK-SUBCNT           TO      361019-TOTCNT-R  .
        totcnt361019R = wkSubcnt;
        // 039100     COMPUTE WK-SUBFEE = WK-SUBCNT * 2.5                  .
        wkSubfee = BigDecimal.valueOf(wkSubcnt).multiply(new BigDecimal("2.5"));
        // 039200     COMPUTE WK-SUBLIQ = WK-SUBAMT - WK-SUBFEE + 0.5      .
        wkSubliq = wkSubamt.subtract(wkSubfee).add(new BigDecimal("0.5"));
        // 039300     MOVE    WK-SUBFEE           TO      361019-TOTFEE-R  .
        totfee361019R = wkSubfee;
        // 039400* 週報還要再呈現扣匯款手續費
        // 039500     IF      PUTFN-CTL(1:1)      =       "2"
        String str_putfnCtl = Integer.toString(putfnCtl);
        if (STR_2.equals(str_putfnCtl.substring(0, 1))) {
            // 039600       PERFORM 6200-RTFEE-RTN    THRU    6200-RTFEE-EXIT
            rtfee6200();
            // 039700       COMPUTE WK-SUBLIQ = WK-SUBLIQ - WK-RTFEE
            wkSubliq = wkSubliq.subtract(wkRtfee);
            // 039800       MOVE    WK-RTFEE          TO      361019-RTFEE-R
            rtfee361019R = wkRtfee;
            // 039900       MOVE    " 匯款手續費： "  TO      361019-RTNAME-R
            rtname361019R = " 匯款手續費： ";
        } else {
            // 040000     ELSE
            // 040100       MOVE    WK-RTFEE          TO      361019-RTFEE-R
            rtfee361019R = wkRtfee;
            // 040200       MOVE    SPACES            TO      361019-RTNAME-R.
            rtname361019R = SPACE;
        }
        // 040400     MOVE    WK-SUBLIQ           TO      361019-TOTLIQ-R.
        totliq361019R = wkSubliq;
        // 040500     MOVE    SPACES              TO      REPORT-LINE    .
        // 040600     WRITE   REPORT-LINE         FROM    361019-TOT     .
        // 020700 01   361019-TOT.
        sb = new StringBuilder();
        // 020800  03  FILLER                           PIC X(08) VALUE
        // 020900      " 總合計 ".
        sb.append(formatUtil.padX(" 總合計 ", 8));
        // 021000* 03  FILLER                           PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 021100  03  361019-TOTAMT-R                  PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(totamt361019R), "Z,ZZZ,ZZZ,ZZ9"));
        // 021200  03  FILLER                           PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 7));
        // 021300  03  361019-TOTCNT-R                  PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(totcnt361019R), "ZZ,ZZ9"));
        // 021400  03  FILLER                           PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 7));
        // 021500  03  361019-TOTFEE-R                  PIC ZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(totfee361019R), "ZZ,ZZ9"));
        // 021600  03  FILLER                           PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 4));
        // 021700  03  361019-TOTLIQ-R                  PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(totliq361019R), "Z,ZZZ,ZZZ,ZZ9"));
        // 021800  03  FILLER                           PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX(SPACE, 3));
        // 021900  03  361019-RTNAME-R                  PIC X(14)    .
        sb.append(formatUtil.padX(rtname361019R, 14));
        // 022000  03  361019-RTFEE-R                   PIC ZZZ.
        sb.append(reportUtil.customFormat(String.valueOf(rtfee361019R), "ZZZ"));
        fileReportflContents.add(sb.toString());

        // 040800     MOVE    WK-YYYMMDD(1:3)     TO      361019-YYY-R2  .
        yyy361019R2 = parse.string2Integer(wkYYYMMDD.substring(0, 3));
        // 040900     MOVE    WK-YYYMMDD(4:2)     TO      361019-MM-R2   .
        mm361019R2 = parse.string2Integer(wkYYYMMDD.substring(3, 5));
        // 041000     MOVE    WK-SUBCNT           TO      361019-CNT-R2  .
        cnt361019R2 = wkSubcnt;
        // 041100     MOVE    WK-SUBFEE           TO      361019-CTAMT-R2.
        ctamt361019R2 = wkSubfee;
        // 041200     PERFORM 9000-UNAME-RTN      THRU    9000-UNAME-EXIT.
        uname9000();
        // 041300* 週報還要再呈現手續費收據
        // 041400     IF      PUTFN-CTL(1:1)      =       "2"
        if (STR_2.equals(str_putfnCtl.substring(0, 1))) {
            // 041500       MOVE    SPACES            TO      REPORT-LINE
            // 041600       WRITE   REPORT-LINE       AFTER   18
            for (int i = 0; i <= 18; i++) {
                fileReportflContents.add("");
            }
            // 041700       MOVE    SPACES            TO      REPORT-LINE
            // 041800       WRITE   REPORT-LINE       FROM    361019-TIT10
            // 022100 01   361019-TIT10.
            sb = new StringBuilder();
            // 022200  03  FILLER   PIC X(08) VALUE " 茲收到 ".
            sb.append(formatUtil.padX(" 茲收到 ", 8));
            fileReportflContents.add(sb.toString());
            // 041900       MOVE    SPACES            TO      REPORT-LINE
            // 042000       WRITE   REPORT-LINE       AFTER   1
            sb = new StringBuilder();
            fileReportflContents.add(sb.toString());
            // 042100       MOVE    SPACES            TO      REPORT-LINE
            // 042200       WRITE   REPORT-LINE       FROM    361019-TIT11
            // 022300 01   361019-TIT11.
            sb = new StringBuilder();
            // 022400  03  FILLER   PIC X(14) VALUE " 貴處補貼本行 ".
            sb.append(formatUtil.padX(" 貴處補貼本行 ", 14));
            // 022500  03  361019-YYY-R2                    PIC 9(03).
            sb.append(formatUtil.pad9(String.valueOf(yyy361019R2), 3));
            // 022600  03  FILLER   PIC X(04) VALUE " 年 ".
            sb.append(formatUtil.padX(" 年 ", 4));
            // 022700  03  361019-MM-R2                     PIC 9(02).
            sb.append(formatUtil.pad9(String.valueOf(mm361019R2), 2));
            // 022800  03  FILLER   PIC X(22) VALUE " 月份代收水費手續費共 ".
            sb.append(formatUtil.padX(" 月份代收水費手續費共 ", 22));
            // 022900  03  361019-CNT-R2                    PIC Z,ZZ9.
            sb.append(reportUtil.customFormat(String.valueOf(cnt361019R2), "Z,ZZ9"));
            // 023000  03  FILLER   PIC X(04) VALUE " 筆 ".
            sb.append(formatUtil.padX(" 筆 ", 4));
            fileReportflContents.add(sb.toString());
            // 042300       MOVE    SPACES            TO      REPORT-LINE
            // 042400       WRITE   REPORT-LINE       FROM    361019-TIT12
            // 023100 01   361019-TIT12.
            sb = new StringBuilder();
            // 023200  03  FILLER   PIC X(14) VALUE " 合計新台幣 ".
            sb.append(formatUtil.padX(" 合計新台幣 ", 14));
            // 023300  03  361019-CTAMT-R2                  PIC ZZZ,ZZ9.
            sb.append(reportUtil.customFormat(String.valueOf(ctamt361019R2), "ZZZ,ZZ9"));
            // 023400  03  FILLER   PIC X(06) VALUE " 元整 "      .
            sb.append(formatUtil.padX(" 元整 ", 6));
            fileReportflContents.add(sb.toString());
            // 042500       MOVE    SPACES            TO      REPORT-LINE
            // 042600       WRITE   REPORT-LINE       FROM    361019-TIT13
            // 023500 01   361019-TIT13.
            sb = new StringBuilder();
            // 023600  03  FILLER   PIC X(06) VALUE " 此致 ".
            sb.append(formatUtil.padX(" 此致 ", 6));
            // 023700  03  361019-RNAME                     PIC X(40).
            sb.append(formatUtil.padX(rname361019, 40));
            // 023800  03  FILLER   PIC X(06) VALUE " 台照 ".
            sb.append(formatUtil.padX(" 台照 ", 6));
            fileReportflContents.add(sb.toString());
            // 042700       MOVE    SPACES            TO      REPORT-LINE
            // 042800       WRITE   REPORT-LINE       AFTER   1
            sb = new StringBuilder();
            fileReportflContents.add(sb.toString());
            // 042900       MOVE    SPACES            TO      REPORT-LINE
            // 043000       WRITE   REPORT-LINE       FROM    361019-TIT14.
            // 023900 01   361019-TIT14.
            sb = new StringBuilder();
            // 024000  03  FILLER   PIC X(36) VALUE SPACES.
            sb.append(formatUtil.padX(SPACE, 36));
            // 024100  03  FILLER   PIC X(22) VALUE " 臺灣銀行臺中分行　啟 ".
            sb.append(formatUtil.padX(" 臺灣銀行臺中分行　啟 ", 22));
            fileReportflContents.add(sb.toString());
        }
    }

    private void rtdtl7100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rtdtl7100()");
        // 搬資料到361019-RT-REC(RC=2)
        // 064000     MOVE  SPACES                TO      361019-RT-REC  .
        // 064100     MOVE  "2"                   TO      361019-RT-RC   .
        rt361019Rc = STR_2;
        rtSum();

        // 064500     MOVE  WK-RTTOTCNT           TO      361019-RT-SEQ  .
        rt361019Seq = String.valueOf(wkRttotcnt);
        // 064600     MOVE  WK-YYYMMDD            TO      361019-RT-DATE .
        rt361019Date = wkYYYMMDD;
        // 064700     MOVE  10                    TO      361019-RT-TYPE .
        rt361019Type = "10";
        // 064800     MOVE  WK-SUBLIQ             TO      361019-RT-AMT  .
        rt361019Amt = wkSubliq;
        // 064900     MOVE  00                    TO      361019-RT-FILLER    .
        rt361019Filler = "00";
        // 065000     PERFORM 9000-UNAME-RTN      THRU    9000-UNAME-EXIT.
        uname9000();
        // 065100     WRITE 361019-RT-REC.
        write361019RtRec();
    }

    private void rtfee6200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rtfee6200()");
        // 手續費
        // 056510     IF WK-SUBLIQ > 2000000
        BigDecimal t = new BigDecimal("2000000");
        if (wkSubliq.compareTo(t) > 0) {
            // 056520       COMPUTE WK-DIV   = (WK-SUBLIQ - 2000001) / 1000000
            BigDecimal _2000001_ = new BigDecimal("2000001");
            BigDecimal _1000000_ = new BigDecimal("1000000");
            wkDiv = wkSubliq.subtract(_2000001_).divide(_1000000_, RoundingMode.HALF_UP);
            // 056530       COMPUTE WK-RTFEE = 10 * ( WK-DIV + 1 ) + 30
            BigDecimal _10_ = new BigDecimal("10");
            BigDecimal _30_ = new BigDecimal("30");
            BigDecimal _1_ = new BigDecimal("1");
            BigDecimal newValue = wkDiv.add(_1_); // Calculate (WK_DIV + 1)
            BigDecimal oldValue = _10_.multiply(newValue); // Calculate 10 * (WK_DIV + 1)
            wkRtfee = oldValue.add(_30_); // Calculate 10 * (WK_DIV + 1) + 30
        } else {
            // 056540     ELSE
            // 056590       MOVE  30                  TO      WK-RTFEE.
            wkRtfee = BigDecimal.valueOf(30);
        }
    }

    private void rttail7200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rttail7200()");
        // 搬資料到361019-RT-REC(RC=3)
        // 073500     MOVE  SPACES                TO      361019-RT-REC  .
        // 073600     MOVE  "3"                   TO      361019-RT-RC   .
        rt361019Rc = STR_3;
        rtSum();

        // 074000     MOVE  99999                 TO      361019-RT-SEQ  .
        rt361019Seq = STR_99999;
        // 074100     MOVE  WK-YYYMMDD            TO      361019-RT-DATE .
        rt361019Date = wkYYYMMDD;
        // 074200     MOVE  WK-RTTOTCNT           TO      361019-RT-TOTCNT.
        rt361019Totcnt = wkRttotcnt;
        // 074300     MOVE  WK-RTTOTAMT           TO      361019-RT-TOTAMT.
        rt361019Totamt = wkRttotamt;
        // 074400     WRITE 361019-RT-REC.
        write361019RtRec();
    }

    private void pointtbsdy6100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pointtbsdy6100()");
        // 找到下營業日的日曆資料
        // 059300     MOVE    1                   TO     WK-CLNDR-KEY.
        wkClndrKey = 1;
        // 059400 6100-POINTTBSDY-LOOP.
        // 059500     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 059600     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        dateDto.init();
        dateDto.setDateS(processDate); /* 算起始日期 */
        dateutil.getCalenderDay(dateDto); /* 計算用,若無計算 dateDto會是null */
        getWeek = dateDto.getDayOfWeek(); /* 抓今天星期幾 */
        // 059700          GO TO 0000-END-RTN.
        // 059800     IF      FD-CLNDR-TBSDY      NOT =  FD-BHDATE-TBSDY
        // 059900      ADD    1                   TO     WK-CLNDR-KEY
        // 060000      GO TO  6100-POINTTBSDY-LOOP.

    }

    private void monday6000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "monday6000()");
        // 找本週星期一
        // 060600     IF  FD-CLNDR-WEEKDY         =      1
        if (getWeek == 1) {
            // 060700         MOVE  FD-CLNDR-TBSDY    TO     WK-MONDAY
            wkMonday = dateDto.getDateS2Integer(false);
            // 060800         GO TO 6000-MONDAY-EXIT.
            return;
        }

        // 061000 6000-MONDAY-LOOP.
        // 061100     SUBTRACT 1                  FROM   WK-CLNDR-KEY.
        // 061200     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 061300     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        // 061400             GO TO 0000-END-RTN.
        int diffDay = getWeek - 1; /* 取得星期與日期相差的個數 */
        dateDto.init();
        dateDto.setDateS(processDate);
        dateDto.setDays(diffDay * -1); /* 乘以-1,為了算相差幾天(負的) */
        dateutil.getCalenderDay(dateDto);
        String resultDate = dateDto.getDateS2String(false);

        // 061600     IF  FD-CLNDR-WEEKDY         =      1
        // 061700         MOVE  FD-CLNDR-TBSDY    TO     WK-MONDAY
        wkMonday = dateDto.getDateS2Integer(false);
        // 061800         GO TO 6000-MONDAY-EXIT.
        // 062000     GO TO 6000-MONDAY-LOOP.
    }

    private void uname9000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "uname9000()");
        // 設定變數
        // 065600
        // 065700     IF    WK-PRE-RCPTID(1:1)    =       "1"
        // 065800        MOVE  0060121            TO      361019-RT-RUNIT
        // 065900        MOVE  00120705015151     TO      361019-RT-ACCOUNT
        // 066000        MOVE  " 台灣自來水股份有限公司第一區管理處 "
        // 066100                                 TO      361019-RT-RNAME,
        // 066200                                         361019-RNAME   .
        // 066300     IF    WK-PRE-RCPTID(1:1)    =       "2"
        // 066400        MOVE  0060165            TO      361019-RT-RUNIT
        // 066500        MOVE  00160705015152     TO      361019-RT-ACCOUNT
        // 066600        MOVE  " 台灣自來水股份有限公司第二區管理處 "
        // 066700                                 TO      361019-RT-RNAME,
        // 066800                                         361019-RNAME   .
        // 066900     IF    WK-PRE-RCPTID(1:1)    =       "3"
        // 067000        MOVE  0060176            TO      361019-RT-RUNIT
        // 067100        MOVE  00170717015153     TO      361019-RT-ACCOUNT
        // 067200        MOVE  " 台灣自來水股份有限公司第三區管理處 "
        // 067300                                 TO      361019-RT-RNAME,
        // 067400                                         361019-RNAME   .
        // 067500     IF    WK-PRE-RCPTID(1:1)    =       "4"
        // 067600        MOVE  0060224            TO      361019-RT-RUNIT
        // 067700        MOVE  00220705915156     TO      361019-RT-ACCOUNT
        // 067800        MOVE  " 台灣自來水股份有限公司第四區管理處 "
        // 067900                                 TO      361019-RT-RNAME,
        // 068000                                         361019-RNAME   .
        // 068100     IF    WK-PRE-RCPTID(1:1)    =       "5"
        // 068200        MOVE  0060280            TO      361019-RT-RUNIT
        // 068300        MOVE  00280705715150     TO      361019-RT-ACCOUNT
        // 068400        MOVE  " 台灣自來水股份有限公司第五區管理處 "
        // 068500                                 TO      361019-RT-RNAME,
        // 068600                                         361019-RNAME   .
        // 068700     IF    WK-PRE-RCPTID(1:1)    =       "6"
        // 068800        MOVE  0060305            TO      361019-RT-RUNIT
        // 068900        MOVE  00300705015159     TO      361019-RT-ACCOUNT
        // 069000        MOVE  " 台灣自來水股份有限公司第六區管理處 "
        // 069100                                 TO      361019-RT-RNAME,
        // 069200                                         361019-RNAME   .
        // 069300     IF    WK-PRE-RCPTID(1:1)    =       "7"
        // 069400        MOVE  0060512            TO      361019-RT-RUNIT
        // 069500        MOVE  00510705015153     TO      361019-RT-ACCOUNT
        // 069600        MOVE  " 台灣自來水股份有限公司第七區管理處 "
        // 069700                                 TO      361019-RT-RNAME,
        // 069800                                         361019-RNAME   .
        // 069900     IF    WK-PRE-RCPTID(1:1)    =       "8"
        // 070000        MOVE  0060132            TO      361019-RT-RUNIT
        // 070100        MOVE  00130705915157     TO      361019-RT-ACCOUNT
        // 070200        MOVE  " 台灣自來水股份有限公司第八區管理處 "
        // 070300                                 TO      361019-RT-RNAME,
        // 070400                                         361019-RNAME   .
        // 070500     IF    WK-PRE-RCPTID(1:1)    =       "9"
        // 070600        MOVE  0060383            TO      361019-RT-RUNIT
        // 070700        MOVE  00380705015152     TO      361019-RT-ACCOUNT
        // 070800        MOVE  " 台灣自來水股份有限公司第九區管理處 "
        // 070900                                 TO      361019-RT-RNAME,
        // 071000                                         361019-RNAME   .
        // 071100     IF    WK-PRE-RCPTID(1:1)    =       "A"
        // 071200        MOVE  0060394            TO      361019-RT-RUNIT
        // 071300        MOVE  00390705115152     TO      361019-RT-ACCOUNT
        // 071400        MOVE  " 台灣自來水股份有限公司第十區管理處 "
        // 071500                                 TO      361019-RT-RNAME,
        // 071600                                         361019-RNAME   .
        // 071700     IF    WK-PRE-RCPTID(1:1)    =       "B"
        // 071800        MOVE  0060235            TO      361019-RT-RUNIT
        // 071900        MOVE  00230705915151     TO      361019-RT-ACCOUNT
        // 072000        MOVE  " 台灣自來水股份有限公司第十一區管理處 "
        // 072100                                 TO      361019-RT-RNAME,
        // 072200                                         361019-RNAME   .
        // 072300     IF    WK-PRE-RCPTID(1:1)    =       "C"
        // 072400        MOVE  0060497            TO      361019-RT-RUNIT
        // 072500        MOVE  00490705015152     TO      361019-RT-ACCOUNT
        // 072600        MOVE  " 台灣自來水股份有限公司第十二區管理處 "
        // 072700                                 TO      361019-RT-RNAME,
        // 072800                                         361019-RNAME   .
        // 072900    IF    WK-PRE-RCPTID(1:1)    =       "D"
        // 072910       MOVE  0060361            TO      361019-RT-RUNIT
        // 072920       MOVE  00360705115156     TO      361019-RT-ACCOUNT
        // 072930       MOVE  " 台灣自來水股份有限公司屏東區管理處 "
        // 072940                                TO      361019-RT-RNAME,
        // 072950                                        361019-RNAME   .
        switch (wkPreRcptid.substring(0, 1)) {
            case "1":
                rt361019Runit = "0060121";
                rt361019Account = "00120705015151";
                rname361019 = "台灣自來水股份有限公司第一區管理處";
                break;
            case "2":
                rt361019Runit = "0060165";
                rt361019Account = "00160705015152";
                rname361019 = "台灣自來水股份有限公司第二區管理處";
                break;
            case "3":
                rt361019Runit = "0060176";
                rt361019Account = "00170717015153";
                rname361019 = "台灣自來水股份有限公司第三區管理處";
                break;
            case "4":
                rt361019Runit = "0060224";
                rt361019Account = "00220705915156";
                rname361019 = "台灣自來水股份有限公司第四區管理處";
                break;
            case "5":
                rt361019Runit = "0060280";
                rt361019Account = "00280705715150";
                rname361019 = "台灣自來水股份有限公司第五區管理處";
                break;
            case "6":
                rt361019Runit = "0060305";
                rt361019Account = "00300705015159";
                rname361019 = "台灣自來水股份有限公司第六區管理處";
                break;
            case "7":
                rt361019Runit = "0060512";
                rt361019Account = "00510705015153";
                rname361019 = "台灣自來水股份有限公司第七區管理處";
                break;
            case "8":
                rt361019Runit = "0060132";
                rt361019Account = "00130705915157";
                rname361019 = "台灣自來水股份有限公司第八區管理處";
                break;
            case "9":
                rt361019Runit = "0060383";
                rt361019Account = "00380705015152";
                rname361019 = "台灣自來水股份有限公司第九區管理處";
                break;
            case "A":
                rt361019Runit = "0060394";
                rt361019Account = "00390705115152";
                rname361019 = "台灣自來水股份有限公司第十區管理處";
                break;
            case "B":
                rt361019Runit = "0060235";
                rt361019Account = "00230705915151";
                rname361019 = "台灣自來水股份有限公司第十一區管理處";
                break;
            case "C":
                rt361019Runit = "0060497";
                rt361019Account = "00490705015152";
                rname361019 = "台灣自來水股份有限公司第十二區管理處";
                break;
            case "D":
                rt361019Runit = "0060361";
                rt361019Account = "00360705115156";
                rname361019 = "台灣自來水股份有限公司屏東區管理處";
                break;
            default:
                break;
        }
    }

    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", "CL-BH-038");

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
