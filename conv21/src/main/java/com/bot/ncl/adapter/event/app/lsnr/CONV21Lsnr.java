/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV21;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
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
@Component("CONV21Lsnr")
@Scope("prototype")
public class CONV21Lsnr extends BatchListenerCase<CONV21> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV21 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPUTF;
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String REPORT_NAME_1 = "CL-BH-014-1";
    private static final String REPORT_NAME_2 = "CL-BH-014-2";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String reportFilePath_1;
    private String reportFilePath_2;
    private StringBuilder sb = new StringBuilder();
    private String wkFileNameList = "";
    private List<String> fileContents = new ArrayList<>(); //  檔案內容
    private List<String> reportContents_1 = new ArrayList<>(); //  報表內容
    private List<String> reportContents_2 = new ArrayList<>(); //  報表內容

    private String wkPutdir;
    private String wkConvdir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private int processDateInt = 0;
    private int nbsdy;

    private String wkYYMMDD;
    private String wkFdate;
    private String wkCdate;
    private String wkDate;
    private String wkRptPdate = "";
    private int wkRptPage = 0;
    private String wkRptBhdate;
    private String wkPutfile;
    private String wkConvfile;

    private String wkHaverpt = "N";
    private String wkCompdate;
    private String wkRptDate;
    private String wkRptTotcnt;
    private String wkRptTotamt;
    private String wkRptBdate;
    private String wkRptEdate;
    private int wkRptCdcnt;
    private BigDecimal wkRptCdamt;
    private BigDecimal wkRptCdfee;
    private String wkRpt2Cdcnt;
    private String wkRpt2Cdamt;
    private int wkSubcnt;
    private BigDecimal wkSubamt;
    private BigDecimal wkRptFee;
    private String wkBdate;
    private String wkEdate;
    private int wkSeqno;
    private String wkRpt2Seqno;
    private String wkUserdata;
    private String wkNo;
    private String wkRcvdate;
    private String wkYYMM;
    private String wkRpt2No;
    private String wkRpt2Rcvdate;
    private String wkRpt2YYMM;
    private int wkPage;
    private int wkPctl;
    private int wkTotcnt = 0;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private String wkRpt2Amt;
    private String wkRunitDtl;
    private String wkFixnum;
    private String wkCllbr;
    private String _301017_Rc;
    private String _301017_Sunit;
    private String _301017_Kind;
    private String _301017_Trdate;
    private String _301017_Bcode;
    private String _301017_No;
    private String _301017_Rcvdate;
    private String _301017_YYMM;
    private String _301017_Trdate_3;
    private int _301017_Totcnt;
    private BigDecimal _301017_Totamt;
    private String _301017_Runit;
    private String _301017_Date;
    private int _301017_Actno;
    private String _301017_Amt;
    private String _301017_Stat;
    private String wkRpt2Pdate;
    private String wkRpt2Bdate;
    private String wkRpt2Edate;
    private String wkRpt2Page;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV21 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV21 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr run()");
        init(event);
        // 設定檔名變數值
        // 026300     MOVE        "27Z1301017"        TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = "27Z1301017";
        wkConvfile = "27Z1301017";
        // 設定檔名
        //  WK-PUTDIR="DATA/CL/BH/PUTF/"+WK-FILEDATE+"/27Z1301017."
        //  WK-CONVDIR="DATA/CL/BH/CONVF/"+WK-CONVFILE+"/27Z1301017."
        // 026400     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 026500     CHANGE  ATTRIBUTE FILENAME OF FD-301017 TO WK-CONVDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
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
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        wkConvdir = fileDir + wkCdate + PATH_SEPARATOR + wkConvfile;

        //// 若FD-PUTF檔案存在，執行301017-RTN
        // 026600     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 026700       PERFORM    301017-RTN  THRU  301017-EXIT.
            _301017();
        }

        checkPath();

        batchResponse();
    }

    private void init(CONV21 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY")); // 待中菲APPLE提供正確名稱
        //// 設定作業日
        // 025200     MOVE    FD-BHDATE-TBSDY         TO   WK-YYMMDD.
        // 025250* 因ＮＢＳＤＹ為壓縮格式，借用別的欄位將資料搬至報表
        // 025300     MOVE    FD-BHDATE-NBSDY         TO   WK-TOTCNT.
        // 025400     MOVE    WK-TOTCNT               TO   WK-RPT-BHDATE,
        // 025500                                          WK-RPT2-BHDATE.
        // 025600     MOVE    0                       TO   WK-TOTCNT.
        wkYYMMDD = formatUtil.pad9("" + processDate, 7).substring(1, 7);
        wkRptBhdate = formatUtil.pad9("" + nbsdy, 7).substring(1, 7);
        wkRptBdate = formatUtil.pad9("" + nbsdy, 7).substring(1, 7);
        wkRpt2Bdate = formatUtil.pad9("" + nbsdy, 7).substring(1, 7);

        //// 設定檔名日期變數值
        // 025700     MOVE    WK-YYMMDD               TO   WK-FDATE,WK-CDATE.
        wkFdate = wkYYMMDD;
        wkCdate = wkYYMMDD;
        //// PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        // 026100     MOVE    PARA-YYMMDD         TO     WK-DATE.
        wkDate = formatUtil.pad9(dateUtil.getNowStringRoc(), 7).substring(1, 7);

        //   挑PUTF-CTL=21,22，寫REPORTFL1(彙總單)("BD/CL/BH/014/1.")
        reportFilePath_1 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_1;
        //   挑PUTF-CTL=21,22，寫REPORTFL2(明細表)("BD/CL/BH/014/2.")
        reportFilePath_2 =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME_2;
    }

    private void _301017() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr _301017");
        // 027300 301017-RTN.
        // 027400***  REPORT-RTN
        // 027500 301017-RPT-RTN.
        //// 開啟檔案FD-PUTF

        // 027600     OPEN    INPUT   FD-PUTF.
        // 027700 301017-RPT-NEXT.

        //// 循序讀取FD-PUTF，直到檔尾，跳到301017-RPT-LAST
        // 027800     READ   FD-PUTF    AT  END  GO TO  301017-RPT-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            //// 若PUTF-CTL = 21(明細資料)，往下一步驟執行
            //// 若PUTF-CTL = 22(彙總資料)，執行301017-RPT-WTOT，寫表尾
            ////  A.搬相關資料到WK-RPT-DTL...
            ////  B.寫REPORTFL1報表明細
            ////  C.執行301017-RPT-WTOT，寫REPORTFL1表尾
            ////  D.清變數
            ////  E.LOOP讀下一筆FD-PUTF
            //// 其他，LOOP讀下一筆FD-PUTF
            // 027900     IF        PUTF-CTL          =       21
            if ("21".equals(filePutf.getCtl())) {
                // 028000       NEXT SENTENCE
            } else {
                // 028100     ELSE
                // 028200       IF      PUTF-CTL          =       22
                if ("22".equals(filePutf.getCtl())) {
                    // 028300         MOVE    WK-COMPDATE     TO      WK-RPT-DATE
                    // 028400         MOVE    WK-SUBCNT       TO      WK-RPT-CDCNT
                    // 028500         MOVE    WK-SUBAMT       TO      WK-RPT-CDAMT
                    // 028600         COMPUTE WK-RPT-CDFEE    =       WK-SUBCNT * 2.5
                    // 028700         MOVE    PUTF-TOTCNT     TO      WK-RPT-TOTCNT
                    // 028800         MOVE    PUTF-TOTAMT     TO      WK-RPT-TOTAMT
                    wkRptDate = wkCompdate;
                    wkRptCdcnt = wkSubcnt;
                    wkRptCdamt = wkSubamt;
                    wkRptCdfee = new BigDecimal(wkSubcnt).multiply(new BigDecimal("2.5"));
                    wkRptTotcnt = fileSumPUTF.getTotcnt();
                    wkRptTotamt = fileSumPUTF.getTotamt();

                    // 028900         MOVE    SPACE           TO      REPORT1-LINE
                    // 029000         WRITE   REPORT1-LINE   AFTER    1
                    reportContents_1.add("");
                    // 029100         WRITE   REPORT1-LINE    FROM    WK-RPT-DTL
                    reportContents_1.add(wk_Rpt_Dtl());

                    // 029200         PERFORM 301017-RPT-WTOT  THRU  301017-RPT-WTOT-E
                    _301017_Rpt_Wtot();
                    // 029300         MOVE  0                 TO      WK-SUBCNT,WK-SUBAMT
                    wkSubcnt = 0;
                    wkSubamt = BigDecimal.ZERO;
                    // 029400         GO TO 301017-RPT-NEXT
                    continue;
                } else {
                    // 029500       ELSE
                    // 029600         GO TO 301017-RPT-NEXT.
                    continue;
                }
            }
            //// 首筆
            ////  A.開啟REPORTFL1
            ////  B.WK-HAVERPT有無資料註記設為"Y"("N","Y")
            // 029700     IF        WK-HAVERPT        =       "N"
            if ("N".equals(wkHaverpt)) {
                // 029800       OPEN    OUTPUT    REPORTFL1
                // 029900       MOVE    "Y"       TO      WK-HAVERPT.
                wkHaverpt = "Y";
            }
            //// 若WK-SUBCNT = 0
            ////  A.執行301017-RPT-WTIT，寫REPORTFL1表頭
            ////  B.搬日期
            //
            // 030000     IF        WK-SUBCNT         =       0
            if (wkSubcnt == 0) {
                // 030100       PERFORM 301017-RPT-WTIT  THRU  301017-RPT-WTIT-E
                _301017_Rpt_Wtit();
                // 030200       MOVE    PUTF-DATE         TO      WK-COMPDATE.
                wkCompdate = filePutf.getEntdy();
            }
            //// 代收日期不同，寫REPORTFL1報表明細
            // 030300     IF        WK-COMPDATE   NOT  =      PUTF-DATE
            if (!filePutf.getEntdy().equals(wkCompdate)) {
                // 030400       MOVE    SPACES            TO      REPORT1-LINE
                // 030500       WRITE   REPORT1-LINE      AFTER   1
                reportContents_1.add("");
                // 030600       MOVE    WK-COMPDATE       TO      WK-RPT-DATE
                // 030700       MOVE    WK-SUBCNT         TO      WK-RPT-CDCNT
                // 030800       MOVE    WK-SUBAMT         TO      WK-RPT-CDAMT
                // 030900       COMPUTE WK-RPT-CDFEE      =       WK-SUBCNT * 2.5
                wkRptDate = wkCompdate;
                wkRptCdcnt = wkSubcnt;
                wkRptCdamt = wkSubamt;
                wkRptCdfee = new BigDecimal(wkSubcnt).multiply(new BigDecimal("2.5"));
                // 031000       WRITE   REPORT1-LINE      FROM    WK-RPT-DTL
                reportContents_1.add(wk_Rpt_Dtl());
                // 031100       MOVE    PUTF-DATE         TO      WK-COMPDATE
                // 031200       MOVE     0                TO      WK-SUBCNT,WK-SUBAMT.
                wkCompdate = filePutf.getEntdy();
                wkSubcnt = 0;
                wkSubamt = BigDecimal.ZERO;
            }
            //// 依代收日期，累加筆數、金額
            // 031300     COMPUTE   WK-SUBCNT         =       WK-SUBCNT + 1.
            // 031400     COMPUTE   WK-SUBAMT         =       WK-SUBAMT + PUTF-AMT.
            wkSubcnt = wkSubcnt + 1;
            wkSubamt = wkSubamt.add(new BigDecimal(filePutf.getAmt()));
            //// LOOP讀下一筆FD-PUTF
            //
            // 031500     GO  TO    301017-RPT-NEXT.
        }
        // 031600 301017-RPT-LAST.
        //// 關閉檔案
        // 031700     CLOSE     FD-PUTF    WITH  SAVE.
        //// WK-HAVERPT有無資料註記("N","Y")
        //// 若有資料，關閉REPORTFL1
        //// 若無資料，跳到301017-FILE-RTN
        // 031800     IF        WK-HAVERPT        =       "Y"
        if ("Y".equals(wkHaverpt)) {
            // 031900       MOVE    PUTF-BDATE  TO   WK-BDATE
            // 032000       MOVE    PUTF-EDATE  TO   WK-EDATE
            wkBdate = fileSumPUTF.getBdate();
            wkEdate = fileSumPUTF.getEdate();
            // 032100       CLOSE   REPORTFL1  WITH  SAVE
            try {
                textFile.writeFileContent(reportFilePath_1, reportContents_1, CHARSET_BIG5);
                upload(reportFilePath_1, "RPT", "");
                wkFileNameList += REPORT_NAME_1 + ",";
            } catch (LogicException e) {
                moveErrorResponse(e);
            }

            // 032400****
            // 032500 301017-RPT2-RTN.
            //// 開啟檔案
            // 032600     OPEN    OUTPUT  REPORTFL2.
            // 032700     OPEN    INPUT   FD-PUTF.

            //// 清變數
            // 032800     MOVE    1       TO      WK-SEQNO.
            // 032900     MOVE    0       TO      WK-PAGE,WK-PCTL.
            wkSeqno = 1;
            wkPage = 0;
            wkPctl = 0;
            // 033000 301017-RPT2-NEXT.
            //// 循序讀取FD-PUTF，直到檔尾，跳到301017-RPT2-LAST
            // 033100     READ   FD-PUTF    AT  END  GO TO  301017-RPT2-LAST.
            lines = textFile.readFileContent(wkPutdir, CHARSET);
            for (String detail : lines) {
                text2VoFormatter.format(detail, filePutf);
                text2VoFormatter.format(detail, fileSumPUTF);
                //// 若PUTF-CTL = 21(明細資料)，往下一步驟執行
                //// 若PUTF-CTL = 22(彙總資料)，執行301017-RPT2-WTOT，寫表尾
                ////  A.執行301017-RPT2-WTOT，寫REPORTFL2表尾
                ////  B.LOOP讀下一筆FD-PUTF
                //// 其他，LOOP讀下一筆FD-PUTF

                // 033200     IF        PUTF-CTL          =       21
                if ("21".equals(filePutf.getCtl())) {
                    // 033300       NEXT SENTENCE
                } else {
                    // 033400     ELSE
                    // 033500       IF      PUTF-CTL          =       22
                    if ("22".equals(filePutf.getCtl())) {
                        // 033600         PERFORM 301017-RPT2-WTOT THRU 301017-RPT2-WTOT-E
                        _301017_Rpt2_Wtot();
                        // 033700         GO TO 301017-RPT2-NEXT
                        continue;
                    } else {
                        // 033800       ELSE
                        // 033900         GO TO 301017-RPT2-NEXT.
                        continue;
                    }
                }
                //// 若WK-PCTL = 0，執行301017-RPT2-WTIT，寫REPORTFL2表頭

                // 034000     IF        WK-PCTL           =       0
                if (wkPctl == 0) {
                    // 034100       PERFORM 301017-RPT2-WTIT  THRU  301017-RPT2-WTIT-E.
                    _301017_Rpt2_Wtit();
                }
                //// 搬相關資料到WK-RPT2-DTL...，寫REPORTFL2報表明細
                // 034200     MOVE      WK-SEQNO          TO      WK-RPT2-SEQNO.
                // 034300     MOVE      PUTF-USERDATA     TO      WK-USERDATA.
                // 034400     MOVE      WK-NO             TO      WK-RPT2-NO.
                // 034500     MOVE      WK-RCVDATE        TO      WK-RPT2-RCVDATE.
                // 034600     MOVE      WK-YYMM           TO      WK-RPT2-YYMM.
                // 034700     MOVE      PUTF-AMT          TO      WK-RPT2-AMT.
                wkRpt2Seqno = "" + wkSeqno;
                wkUserdata = filePutf.getUserdata();
                // 022700 01  WK-USERDATA.
                // 022800  03 WK-FILLER                         PIC X(09).
                // 022900  03 WK-NO                             PIC X(08).
                // 023000* 03 WK-FIX                            PIC X(02).
                // 023100  03 WK-RCVDATE                        PIC X(08).
                // 023200  03 WK-YYMM                           PIC X(04).
                // 023300  03 WK-CHK                            PIC X(02).
                wkNo = wkUserdata.substring(9, 17);
                wkRcvdate = wkUserdata.substring(17, 25);
                wkYYMM = wkUserdata.substring(25, 29);
                wkRpt2No = wkNo;
                wkRpt2Rcvdate = wkRcvdate;
                wkRpt2YYMM = wkYYMM;
                wkRpt2Amt = filePutf.getAmt();

                // 034800     WRITE     REPORT2-LINE  FROM  WK-RPT2-DTL.
                reportContents_2.add(wk_Rpt2_Dtl());
                // 034900     ADD       1                 TO      WK-SEQNO,WK-PCTL.
                wkSeqno = wkSeqno + 1;
                wkPctl = wkPctl + 1;
                //// 換頁
                // 035000     IF        WK-PCTL           =       50
                if (wkPctl == 50) {
                    // 035100       MOVE    0                 TO      WK-PCTL.
                    wkPctl = 0;
                }
                //// LOOP讀下一筆FD-PUTF
                // 035200     GO TO     301017-RPT2-NEXT.
            }
            // 035300 301017-RPT2-LAST.
            //
            //// 關閉檔案
            //
            // 035400     CLOSE     FD-PUTF    WITH  SAVE.
            // 035500     CLOSE     REPORTFL2  WITH  SAVE.
            try {
                textFile.writeFileContent(reportFilePath_2, reportContents_2, CHARSET_BIG5);
                upload(reportFilePath_2, "RPT", "");
                wkFileNameList += reportFilePath_2 + ",";

            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            // 035600***  FILE-RTN
        } else {
            // 032200     ELSE
            // 032300       GO  TO  301017-FILE-RTN.
        }
        // 035700 301017-FILE-RTN.
        //// 開啟檔案
        // 035800     OPEN      OUTPUT    FD-301017.
        // 035900     OPEN      INPUT     FD-PUTF.

        //// 搬相關資料到301017-REC...(RC=1)
        // 036000     MOVE      0                 TO      WK-TOTCNT,WK-TOTAMT.
        // 036100     MOVE      SPACES            TO      301017-REC.
        // 036200     MOVE      "1"               TO      301017-RC.
        // 036300     MOVE      "303     "        TO      301017-SUNIT.
        // 036400     MOVE      "004     "        TO      301017-RUNIT.
        // 036500     MOVE      "000"             TO      301017-KIND.
        // 036600     MOVE      WK-YYMMDD         TO      301017-TRDATE.
        // 036700     MOVE      "2"               TO      301017-BCODE.
        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
        _301017_Rc = "1";
        _301017_Sunit = "303     ";
        _301017_Runit = "004     ";
        _301017_Kind = "000";
        _301017_Trdate = wkYYMMDD;
        _301017_Bcode = "2";
        //// 寫檔FD-301017(FIRST RECORD)
        // 036800     WRITE     301017-REC.
        fileContents.add(_301017_Rec("1"));
        // 036900***  DETAIL  RECORD  *****
        // 037000 301017-FILE-NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到301017-FILE-CLOSE
        // 037100     READ   FD-PUTF    AT  END  GO TO  301017-FILE-CLOSE.
        lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            //// 若PUTF-CTL <> 11(明細資料)，LOOP讀下一筆FD-PUTF
            // 037200     IF        PUTF-CTL       NOT  =   11
            if (!"11".equals(filePutf.getCtl())) {
                // 037300       GO TO   301017-FILE-NEXT.
                continue;
            }
            //// 搬相關資料到301017-REC...(RC=2)
            // 037400     MOVE      SPACES            TO      301017-REC.
            // 037500     MOVE      "2"               TO      301017-RC.
            // 037600     MOVE      "303     "        TO      301017-SUNIT.
            // 037650     MOVE      SPACES            TO      WK-RUNIT-DTL.
            // 037700     MOVE      "004"             TO      WK-FIXNUM.
            // 037720     MOVE      PUTF-CLLBR        TO      WK-CLLBR.
            // 037740     MOVE      WK-RUNIT-DTL      TO      301017-RUNIT.
            // 037800     MOVE      "000"             TO      301017-KIND.
            // 037900     MOVE      PUTF-SITDATE      TO      301017-DATE.
            // 038000     MOVE      0                 TO      301017-ACTNO.
            // 038100     MOVE      PUTF-AMT          TO      301017-AMT.
            // 038200     MOVE      "00"              TO      301017-STAT.
            // 038300     MOVE      PUTF-USERDATA     TO      WK-USERDATA.
            // 038400     MOVE      WK-NO             TO      301017-NO.
            // 038500     MOVE      WK-RCVDATE        TO      301017-RCVDATE.
            // 038600     MOVE      WK-YYMM           TO      301017-YYMM.
            _301017_Rc = "2";
            _301017_Sunit = "303     ";
            wkRunitDtl = "";
            wkFixnum = "004";
            wkCllbr = filePutf.getCllbr();
            wkRunitDtl = wkFixnum + wkCllbr + "  ";
            _301017_Runit = wkRunitDtl;
            _301017_Kind = "000";
            _301017_Date = filePutf.getSitdate();
            _301017_Actno = 0;
            _301017_Amt = filePutf.getAmt();
            _301017_Stat = "00";
            wkUserdata = filePutf.getUserdata();
            _301017_No = wkNo;
            _301017_Rcvdate = wkRcvdate;
            _301017_YYMM = wkYYMM;

            //// 寫檔FD-301007(DETAIL)
            // 038700     WRITE     301017-REC.
            fileContents.add(_301017_Rec("2"));
            //
            //// 累加金額,筆數
            //
            // 038800     ADD       1                 TO      WK-TOTCNT.
            // 038900     ADD       PUTF-AMT          TO      WK-TOTAMT.
            wkTotcnt = wkTotcnt + 1;
            wkTotamt = wkTotamt.add(new BigDecimal(filePutf.getAmt()));

            //// LOOP讀下一筆FD-PUTF
            // 039000     GO TO     301017-FILE-NEXT.
        }
        // 039100 301017-FILE-CLOSE.

        //// 搬相關資料到301007-REC...(RC=3)
        // 039200     MOVE      SPACES            TO      301017-REC.
        // 039300     MOVE      "3"               TO      301017-RC.
        // 039400     MOVE      "303     "        TO      301017-SUNIT.
        // 039500     MOVE      "004     "        TO      301017-RUNIT.
        // 039600     MOVE      "000"             TO      301017-KIND.
        // 039700     MOVE      WK-YYMMDD         TO      301017-TRDATE-3.
        // 039800     MOVE      WK-TOTCNT         TO      301017-TOTCNT.
        // 039900     MOVE      WK-TOTAMT         TO      301017-TOTAMT.
        _301017_Rc = "3";
        _301017_Sunit = "303     ";
        _301017_Runit = "004     ";
        _301017_Kind = "000";
        _301017_Trdate_3 = wkYYMMDD;
        _301017_Totcnt = wkTotcnt;
        _301017_Totamt = wkTotamt;

        //// 寫檔FD-301007(LAST RECORD)
        // 040000     WRITE     301017-REC.
        fileContents.add(_301017_Rec("3"));
        //
        //// 關閉檔案
        //
        // 040100     CLOSE  FD-PUTF    WITH  SAVE.
        // 040200     CHANGE ATTRIBUTE FILENAME OF FD-301017 TO WK-PUTDIR.
        // 040300     CLOSE  FD-301017  WITH  SAVE.
        // textFile.deleteFile(wkPutdir);
        // try {
        //    textFile.writeFileContent(wkPutdir, fileContents, CHARSET);
        // } catch (LogicException e) {
        //    moveErrorResponse(e);
        // }
        // 040400 301017-EXIT.
    }

    private String wk_Rpt_Tit5() {
        // 012400 01   WK-RPT-TIT5.
        // 012500  03  FILLER                           PIC X(14) VALUE SPACES.    90/12/22
        // 012600  03  FILLER                           PIC X(10) VALUE
        // 012700      " 代收日期 ".                                               90/12/22
        // 012800  03  FILLER                           PIC X(12) VALUE SPACES.    90/12/22
        // 012900  03  FILLER                           PIC X(12) VALUE            90/12/22
        // 013000      " 代收總筆數 ".                                             90/12/22
        // 013100  03  FILLER                           PIC X(10) VALUE SPACES.    90/12/22
        // 013200  03  FILLER                           PIC X(12) VALUE            90/12/22
        // 013300      " 代收總金額 ".                                             90/12/22
        // 013400  03  FILLER                           PIC X(10) VALUE SPACES.    90/12/22
        // 013500  03  FILLER                           PIC X(08) VALUE            90/12/22
        // 013600      " 手續費 ".                                                 90/12/22
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX(" 代收日期 ", 10));
        sb.append(formatUtil.padX("", 12));
        sb.append(formatUtil.padX(" 代收總筆數 ", 12));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 代收總金額 ", 12));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 手續費 ", 8));
        return sb.toString();
    }

    private String wk_Rpt_Tit4() {
        // 011500 01   WK-RPT-TIT4.
        // 011600  03  FILLER                           PIC X(12) VALUE            90/12/21
        // 011700      " 公司名稱： ".
        // 011800  03  WK-RPT-CNAME                     PIC X(20) VALUE
        // 011900      " 陽明山瓦斯公司 ".
        // 012000  03  FILLER                           PIC X(54) VALUE SPACES.
        // 012100  03  FILLER                           PIC X(07) VALUE
        // 012200      " 頁數 :".
        // 012300  03  WK-RPT-PAGE                      PIC 9(03).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 公司名稱： ", 12));
        sb.append(formatUtil.padX(" 陽明山瓦斯公司 ", 20));
        sb.append(formatUtil.padX("", 54));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("" + wkRptPage, 3));
        return sb.toString();
    }

    private String wk_Rpt_Tit3() {
        // 010700 01   WK-RPT-TIT3.
        // 010800  03  FILLER                           PIC X(12) VALUE
        // 010900      " 解繳日期 : ".
        // 011000  03  WK-RPT-BHDATE                    PIC 99/99/99.
        // 011100  03  FILLER                           PIC X(66) VALUE SPACES.
        // 011200  03  FILLER                           PIC X(12) VALUE
        // 011300      " 印表日期 : ".
        // 011400  03  WK-RPT-PDATE                     PIC 99/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 : ", 12));
        sb.append(reportUtil.customFormat(wkRptBdate, "99/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 印表日期 : ", 12));
        sb.append(reportUtil.customFormat(wkRptPdate, "99/99/99"));
        return sb.toString();
    }

    private String wk_Rpt_Tit2() {
        // 010000 01   WK-RPT-TIT2.
        // 010100  03  FILLER                           PIC X(01) VALUE SPACES.
        // 010200  03  FILLER                           PIC X(07) VALUE "BRNO : ".
        // 010300  03  WK-RPT-BRNO                      PIC X(03) VALUE "070".
        // 010400  03  FILLER                           PIC X(76) VALUE SPACES.
        // 010500  03  FILLER                           PIC X(13) VALUE
        // 010600                                       "FORM : C014/1".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.padX("070", 3));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX("FORM : C014/1", 13));
        return sb.toString();
    }

    private String wk_Rpt_Tit1() {
        // 009600 01   WK-RPT-TIT1.
        // 009700  03  FILLER                           PIC X(27) VALUE SPACES.
        // 009800  03  FILLER                           PIC X(48) VALUE
        // 009900      " 臺　灣　銀　行　代　收　瓦　斯　費　彙　總　單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 27));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　瓦　斯　費　彙　總　單 ", 48));
        return sb.toString();
    }

    private void _301017_Rpt_Wtit() {
        // 040700 301017-RPT-WTIT.
        // 040800    MOVE     WK-DATE                 TO      WK-RPT-PDATE.
        // 040900    MOVE     1                       TO      WK-RPT-PAGE.
        wkRptPdate = wkDate;
        wkRptPage = 1;
        // 041000    MOVE     SPACES                  TO      REPORT1-LINE.
        // 041100    WRITE    REPORT1-LINE         AFTER      PAGE.
        reportContents_1.add(PAGE_SEPARATOR);
        // 041200    MOVE     SPACES                  TO      REPORT1-LINE.
        // 041300    WRITE    REPORT1-LINE          FROM      WK-RPT-TIT1.
        reportContents_1.add(wk_Rpt_Tit1());
        // 041400    MOVE     SPACES                  TO      REPORT1-LINE.
        // 041500    WRITE    REPORT1-LINE          FROM      WK-RPT-TIT2.
        reportContents_1.add(wk_Rpt_Tit2());
        // 041600    MOVE     SPACES                  TO      REPORT1-LINE.
        // 041700    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");
        // 041800    WRITE    REPORT1-LINE          FROM      WK-RPT-TIT3.
        reportContents_1.add(wk_Rpt_Tit3());
        // 041900    MOVE     SPACES                  TO      REPORT1-LINE.
        // 042000    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");
        // 042100    MOVE     SPACES                  TO      REPORT1-LINE.
        // 042200    WRITE    REPORT1-LINE          FROM      WK-RPT-TIT4.
        reportContents_1.add(wk_Rpt_Tit4());
        // 042300    MOVE     SPACES                  TO      REPORT1-LINE.
        // 042400    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");
        // 042500    WRITE    REPORT1-LINE          FROM      WK-RPT-TIT5.
        reportContents_1.add(wk_Rpt_Tit5());
        // 042600    MOVE     SPACES                  TO      REPORT1-LINE.
        // 042700    WRITE    REPORT1-LINE          FROM      WK-RPT-SEP.
        reportContents_1.add(wk_Rpt_Sep());
        // 042800    MOVE     SPACES                  TO      REPORT1-LINE.
        // 042900    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");

        // 043000 301017-RPT-WTIT-E.
    }

    private void _301017_Rpt_Wtot() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr _301017_Rpt_Wtot");
        // 045700 301017-RPT-WTOT.
        //// 寫REPORTFL1報表表尾(WK-RPT-TOT)

        // 045800    MOVE     PUTF-BDATE              TO      WK-RPT-BDATE.
        // 045900    MOVE     PUTF-EDATE              TO      WK-RPT-EDATE.
        wkRptBdate = fileSumPUTF.getBdate();
        wkRptEdate = fileSumPUTF.getEdate();

        // 046000    MOVE     SPACES                  TO      REPORT1-LINE.
        // 046100    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");
        // 046200    WRITE    REPORT1-LINE          FROM      WK-RPT-SEP.
        reportContents_1.add(wk_Rpt_Sep());

        // 046300    MOVE     SPACES                  TO      REPORT1-LINE.
        // 046400    WRITE    REPORT1-LINE         AFTER      1.
        reportContents_1.add("");

        // 046500    COMPUTE  WK-RPT-FEE     =     PUTF-TOTCNT * 2.5 + 0.5
        wkRptFee =
                new BigDecimal(fileSumPUTF.getTotcnt())
                        .multiply(new BigDecimal("2.5"))
                        .add(new BigDecimal("0.5"));
        // 046600    WRITE    REPORT1-LINE          FROM      WK-RPT-TOT.
        reportContents_1.add(wk_Rpt_Tot());

        // 046700    MOVE     SPACES                  TO      REPORT1-LINE.
        // 046800    WRITE    REPORT1-LINE          AFTER     1.
        reportContents_1.add("");

        // 046900 301017-RPT-WTOT-E.
    }

    private void _301017_Rpt2_Wtot() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV21Lsnr _301017_Rpt2_Wtot");
        // 047200 301017-RPT2-WTOT.
        //// 寫REPORTFL2報表表尾(WK-RPT2-TOT)

        // 047300    MOVE     SPACES                  TO      REPORT2-LINE.
        // 047400    WRITE    REPORT2-LINE          FROM      WK-RPT-SEP.
        reportContents_2.add(wk_Rpt_Sep());
        // 047500    MOVE     SPACES                  TO      REPORT2-LINE.
        // 047600    WRITE    REPORT2-LINE         AFTER      1.
        reportContents_2.add("");

        // 047700    MOVE     PUTF-TOTCNT             TO      WK-RPT2-CDCNT.
        // 047800    MOVE     PUTF-TOTAMT             TO      WK-RPT2-CDAMT.
        wkRpt2Cdcnt = fileSumPUTF.getTotcnt();
        wkRpt2Cdamt = fileSumPUTF.getTotamt();

        // 047900    WRITE    REPORT2-LINE          FROM      WK-RPT2-TOT.
        reportContents_2.add(wk_Rpt2_Tot());

        // 048000 301017-RPT2-WTOT-E.
    }

    private void _301017_Rpt2_Wtit() {
        // 043300 301017-RPT2-WTIT.
        //// 寫REPORTFL2報表表頭(WK-RPT2-TIT1~WK-RPT2-TIT5)

        // 043400    MOVE     WK-DATE                 TO      WK-RPT2-PDATE.
        // 043500    MOVE     WK-BDATE                TO      WK-RPT2-BDATE.
        // 043600    MOVE     WK-EDATE                TO      WK-RPT2-EDATE.
        // 043700    ADD      1                       TO      WK-PAGE.
        // 043800    MOVE     WK-PAGE                 TO      WK-RPT2-PAGE.
        wkRpt2Pdate = wkDate;
        wkRpt2Bdate = wkBdate;
        wkRpt2Edate = wkEdate;
        wkPage = wkPage + 1;
        wkRpt2Page = "" + wkPage;

        // 043900    MOVE     SPACES                  TO      REPORT2-LINE.
        // 044000    WRITE    REPORT2-LINE         AFTER      PAGE.
        reportContents_2.add(PAGE_SEPARATOR);

        // 044100    MOVE     SPACES                  TO      REPORT2-LINE.
        // 044200    WRITE    REPORT2-LINE          FROM      WK-RPT2-TIT1.
        reportContents_2.add(wk_Rpt2_Tit1());

        // 044300    MOVE     SPACES                  TO      REPORT2-LINE.
        // 044400    WRITE    REPORT2-LINE          FROM      WK-RPT2-TIT2.
        reportContents_2.add(wk_Rpt2_Tit2());

        // 044500    MOVE     SPACES                  TO      REPORT2-LINE.
        // 044600    WRITE    REPORT2-LINE          FROM      WK-RPT2-TIT3.
        reportContents_2.add(wk_Rpt2_Tit3());

        // 044700    MOVE     SPACES                  TO      REPORT2-LINE.
        // 044800    WRITE    REPORT2-LINE          FROM      WK-RPT2-TIT4.
        reportContents_2.add(wk_Rpt2_Tit4());

        // 044900    MOVE     SPACES                  TO      REPORT2-LINE.
        // 045000    WRITE    REPORT2-LINE          FROM      WK-RPT2-TIT5.
        reportContents_2.add(wk_Rpt2_Tit5());

        // 045100    MOVE     SPACES                  TO      REPORT2-LINE.
        // 045200    WRITE    REPORT2-LINE          FROM      WK-RPT-SEP.
        reportContents_2.add(wk_Rpt_Sep());
        // 045300    MOVE     SPACES                  TO      REPORT2-LINE.

        // 045400 301017-RPT2-WTIT-E.
    }

    private String wk_Rpt2_Tit1() {
        // 016100 01   WK-RPT2-TIT1.
        // 016200  03  FILLER                           PIC X(27) VALUE SPACES.
        // 016300  03  FILLER                           PIC X(48) VALUE
        // 016400      " 臺　灣　銀　行　代　收　瓦　斯　費　明　細　表 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 27));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　瓦　斯　費　明　細　表 ", 48));
        return sb.toString();
    }

    private String wk_Rpt2_Tit2() {
        // 016500 01   WK-RPT2-TIT2.
        // 016600  03  FILLER                           PIC X(01) VALUE SPACES.
        // 016700  03  FILLER                           PIC X(07) VALUE "BRNO : ".
        // 016800  03  WK-RPT2-BRNO                     PIC X(03) VALUE "070".
        // 016900  03  FILLER                           PIC X(76) VALUE SPACES.
        // 017000  03  FILLER                           PIC X(14) VALUE
        // 017100                                       "FORM : C014/2".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.padX("070", 3));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX("FORM : C014/2", 14));
        return sb.toString();
    }

    private String wk_Rpt2_Tit3() {
        // 017200 01   WK-RPT2-TIT3.
        // 017300  03  FILLER                           PIC X(12) VALUE
        // 017400      " 解繳日期 : ".
        // 017500  03  WK-RPT2-BHDATE                   PIC 99/99/99.
        // 017600  03  FILLER                           PIC X(66) VALUE SPACES.
        // 017700  03  FILLER                           PIC X(12) VALUE
        // 017800      " 印表日期 : ".
        // 017900  03  WK-RPT2-PDATE                    PIC 99/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 : ", 12));
        sb.append(reportUtil.customFormat(wkRpt2Bdate, "99/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 印表日期 : ", 12));
        sb.append(reportUtil.customFormat(wkRpt2Pdate, "99/99/99"));
        return sb.toString();
    }

    private String wk_Rpt2_Tit4() {
        // 018000 01   WK-RPT2-TIT4.
        // 018100  03  FILLER                           PIC X(12) VALUE
        // 018200      " 代收期間 : ".
        // 018300  03  WK-RPT2-BDATE                    PIC 99/99/99.
        // 018400  03  FILLER                           PIC X(01) VALUE "-".
        // 018500  03  WK-RPT2-EDATE                    PIC 99/99/99.
        // 018600  03  FILLER                           PIC X(57) VALUE SPACES.
        // 018700  03  FILLER                           PIC X(07) VALUE
        // 018800      " 頁數 :".
        // 018900  03  WK-RPT2-PAGE                     PIC 9(03).

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收期間 : ", 12));
        sb.append(reportUtil.customFormat(wkRpt2Bdate, "99/99/99"));
        sb.append(formatUtil.padX("-", 1));
        sb.append(reportUtil.customFormat(wkRpt2Edate, "99/99/99"));
        sb.append(formatUtil.padX("", 57));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9(wkRpt2Page, 3));
        return sb.toString();
    }

    private String wk_Rpt2_Tit5() {
        // 019000 01   WK-RPT2-TIT5.
        // 019100  03  FILLER                           PIC X(01) VALUE SPACES.
        // 019200  03  FILLER                           PIC X(06) VALUE
        // 019300      " 序號 ".
        // 019400  03  FILLER                           PIC X(05) VALUE SPACES.
        // 019500  03  FILLER                           PIC X(10) VALUE
        // 019600      " 用戶號碼 ".
        // 019700  03  FILLER                           PIC X(02) VALUE SPACES.
        // 019800  03  FILLER                           PIC X(10) VALUE
        // 019900      " 應收日期 ".
        // 020000  03  FILLER                           PIC X(03) VALUE SPACES.
        // 020100  03  FILLER                           PIC X(10) VALUE
        // 020200      " 列帳年月 ".
        // 020300  03  FILLER                           PIC X(03) VALUE SPACES.
        // 020400  03  FILLER                           PIC X(10) VALUE
        // 020500      " 繳款金額 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 序號 ", 6));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 用戶號碼 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 應收日期 ", 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 列帳年月 ", 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 繳款金額 ", 10));
        return sb.toString();
    }

    private String wk_Rpt_Sep() {
        // 013700 01   WK-RPT-SEP.
        // 013800  03  FILLER                           PIC X(120) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        return sb.toString();
    }

    private String _301017_Rec(String fg) {
        // 004200 01         301017-REC.
        // 004300        03  301017-RC              PIC X(01).
        // 004400        03  301017-SUNIT           PIC X(08).
        // 004500        03  301017-RUNIT           PIC X(08).
        // 004600        03  301017-KIND            PIC X(03).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(_301017_Rc, 1));
        sb.append(formatUtil.padX(_301017_Sunit, 8));
        sb.append(formatUtil.padX(_301017_Runit, 8));
        sb.append(formatUtil.padX(_301017_Kind, 3));
        switch (fg) {
            case "1":
                // 004700        03  301017-1.
                // 004800         05 301017-TRDATE          PIC 9(06).
                // 004900         05 301017-BCODE           PIC X(01).
                // 005000         05 FILLER                 PIC X(93).
                sb.append(formatUtil.pad9(_301017_Trdate, 6));
                sb.append(formatUtil.padX(_301017_Bcode, 1));
                sb.append(formatUtil.padX("", 93));
                break;
            case "2":
                // 005100        03  301017-2  REDEFINES  301017-1.
                // 005200         05 301017-DATE            PIC 9(06).
                // 005300         05 301017-ACTNO           PIC 9(14).
                // 005400         05 301017-AMT             PIC 9(12)V99.
                // 005500         05 FILLER                 PIC X(08).
                // 005600         05 301017-STAT            PIC 9(02).
                // 005700         05 301017-NO              PIC X(08).
                // 005800         05 FILLER                 PIC X(07).
                // 006000         05 301017-RCVDATE         PIC X(08).
                // 006100         05 301017-YYMM            PIC 9(04).
                // 006200         05 FILLER                 PIC X(29).
                sb.append(formatUtil.pad9(_301017_Date, 6));
                sb.append(formatUtil.pad9("" + _301017_Actno, 14));
                sb.append(formatUtil.pad9(_301017_Amt, 12) + "00");
                sb.append(formatUtil.padX("", 8));
                sb.append(formatUtil.pad9(_301017_Stat, 2));
                sb.append(formatUtil.padX(_301017_No, 8));
                sb.append(formatUtil.padX("", 7));
                sb.append(formatUtil.padX(_301017_Rcvdate, 8));
                sb.append(formatUtil.pad9(_301017_YYMM, 4));
                sb.append(formatUtil.padX("", 29));
                break;
            case "3":
                // 006300        03  301017-3  REDEFINES  301017-1.
                // 006400         05 301017-TRDATE-3        PIC 9(06).
                // 006500         05 301017-TOTAMT          PIC 9(14)V99.
                // 006600         05 301017-TOTCNT          PIC 9(10).
                // 006700         05 FILLER                 PIC X(68).
                sb.append(formatUtil.pad9(_301017_Trdate_3, 6));
                sb.append(formatUtil.pad9("" + _301017_Totamt, 14) + "00");
                sb.append(formatUtil.pad9("" + _301017_Totcnt, 10));
                sb.append(formatUtil.padX("", 68));
                break;
        }
        return sb.toString();
    }

    private String wk_Rpt_Dtl() {
        // 013900 01   WK-RPT-DTL.
        // 014000  03  FILLER                           PIC X(17) VALUE SPACES.
        // 014100  03  WK-RPT-DATE                      PIC 9(06).
        // 014200  03  FILLER                           PIC X(14) VALUE SPACES.
        // 014300  03  WK-RPT-CDCNT                     PIC ZZ,ZZZ,ZZ9.
        // 014400  03  FILLER                           PIC X(09) VALUE SPACES.
        // 014500  03  WK-RPT-CDAMT                     PIC Z,ZZZ,ZZZ,ZZ9.
        // 014600  03  FILLER                           PIC X(04) VALUE SPACES.
        // 014700  03  WK-RPT-CDFEE                     PIC ZZZ,ZZZ,ZZ9.99.

        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.pad9(wkRptDate, 6));
        sb.append(formatUtil.padX("", 14));
        sb.append(reportUtil.customFormat("" + wkRptCdcnt, "ZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat("" + wkRptCdamt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + wkRptCdfee, "ZZZ,ZZZ,ZZ9.99"));
        return sb.toString();
    }

    private String wk_Rpt2_Dtl() {
        // 020600 01   WK-RPT2-DTL.
        // 020700  03  WK-RPT2-SEQNO                    PIC ZZZ,Z9.
        // 020800  03  FILLER                           PIC X(07) VALUE SPACES.
        // 020900  03  WK-RPT2-NO                       PIC X(08).
        // 021000  03  FILLER                           PIC X(04) VALUE SPACES.
        // 021100  03  WK-RPT2-RCVDATE                  PIC 9999/99/99.
        // 021200  03  FILLER                           PIC X(07) VALUE SPACES.
        // 021300  03  WK-RPT2-YYMM                     PIC X(04).
        // 021400  03  FILLER                           PIC X(02) VALUE SPACES.
        // 021500  03  WK-RPT2-AMT                      PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(reportUtil.customFormat(wkRpt2Seqno, "ZZZ,Z9"));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(wkRpt2No, 8));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat(wkRpt2Rcvdate, "9999/99/99"));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(wkRpt2YYMM, 4));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat(wkRpt2Amt, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String wk_Rpt_Tot() {
        // 014800 01   WK-RPT-TOT.
        // 014900  03  FILLER                           PIC X(14) VALUE
        // 015000      "   代收期間 : ".
        // 015100  03  WK-RPT-BDATE                     PIC 99/99/99.
        // 015200  03  FILLER                           PIC X(01) VALUE "-".
        // 015300  03  WK-RPT-EDATE                     PIC 99/99/99.
        // 015400  03  FILLER                           PIC X(09) VALUE SPACES.
        // 015500  03  WK-RPT-TOTCNT                    PIC ZZZ,ZZ9.
        // 015600  03  FILLER                           PIC X(11) VALUE SPACES.
        // 015700  03  WK-RPT-TOTAMT                    PIC ZZZ,ZZZ,ZZ9.
        // 015800  03  FILLER                           PIC X(11) VALUE SPACES.
        // 015900  03  WK-RPT-FEE                       PIC ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("   代收期間 : ", 14));
        sb.append(reportUtil.customFormat(wkRptBdate, "99/99/99"));
        sb.append(formatUtil.padX("-", 1));
        sb.append(reportUtil.customFormat(wkRptEdate, "99/99/99"));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat(wkRptTotcnt, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 11));
        sb.append(reportUtil.customFormat(wkRptTotamt, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 11));
        sb.append(reportUtil.customFormat("" + wkRptFee, "ZZZ,ZZ9"));
        return sb.toString();
    }

    private String wk_Rpt2_Tot() {
        // 021600 01   WK-RPT2-TOT.
        // 021700  03  FILLER                           PIC X(26) VALUE SPACES.
        // 021800  03  FILLER                           PIC X(18) VALUE
        // 021900      " 代收總筆數 : ".
        // 022000  03  WK-RPT2-CDCNT                    PIC ZZZ,ZZ9.
        // 022100  03  FILLER                           PIC X(01) VALUE SPACES.
        // 022200  03  FILLER                           PIC X(14) VALUE
        // 022300      " 代收總金額 : ".
        // 022400  03  WK-RPT2-CDAMT                    PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 26));
        sb.append(formatUtil.padX(" 代收總筆數 : ", 18));
        sb.append(reportUtil.customFormat(wkRpt2Cdcnt, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 代收總金額 : ", 14));
        sb.append(reportUtil.customFormat(wkRpt2Cdamt, "ZZZ,ZZZ,ZZ9"));
        return sb.toString();
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

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        "27Z1301017", // 來源檔案名稱(20碼長)
                        "27Z1301017", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV21", // 檔案設定代號 ex:CONVF001
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", wkFileNameList);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
