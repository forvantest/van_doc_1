/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C077;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
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
@Component("C077Lsnr")
@Scope("prototype")
public class C077Lsnr extends BatchListenerCase<C077> {
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private CldtlService cldtlService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "big5";
    private static final String FILE_SORT_NAME = "SORTC077";
    private static final String FILE_REPORT_NAME = "CL-BH-C077";
    private static final String PAGE_SEPARATOR = "\u000C";

    private static final String CODE_121454 = "121454";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String sortPath;
    private String reportPath;
    private StringBuilder sb = new StringBuilder();
    private List<String> sortFileContents = new ArrayList<>(); //  檔案內容
    private List<String> reportContents = new ArrayList<>(); //  報表內容
    private Map<String, String> labelMap;

    private Boolean isNoData = true;
    private String processDate;
    private String wkDateR;
    private String wkYear;
    private String wkMonth;
    private String wkDd;
    private int wkSubcnt;
    private BigDecimal wkSubamt;
    private int wkTotcnt;
    private BigDecimal wkTotamt;
    private int wkPbrno;
    private String wkBrnoR;
    private int sdCllbr;
    private String sdCode;
    private int sdDate;
    private int sdSitdate;
    private BigDecimal sdAmt;
    private String sdRcptid;
    private int sdTime;
    private String sdUserdata;
    private String sdTxtype;
    private String sdPbrno;
    private int sdCnt;
    private int wkDate;
    private int wkCllbr;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C077 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C077 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr run()");
        init(event);

        // 013030               INPUT  PROCEDURE SORT-IN
        sortIn();
        if (isNoData) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr isNoData ");

        } else {
            // 013000     SORT      SORTFL  ON  ASCENDING   KEY   SD-PBRNO,SD-DATE  ,
            // 013010                           SD-CLLBR,SD-RCPTID,SD-SITDATE, SD-TIME
            File tmpFile = new File(sortPath);
            List<KeyRange> keyRanges = new ArrayList<>();
            keyRanges.add(new KeyRange(9, 3, SortBy.ASC));
            keyRanges.add(new KeyRange(1, 8, SortBy.ASC));
            keyRanges.add(new KeyRange(12, 3, SortBy.ASC));
            keyRanges.add(new KeyRange(21, 26, SortBy.ASC));
            keyRanges.add(new KeyRange(47, 8, SortBy.ASC));
            keyRanges.add(new KeyRange(55, 6, SortBy.ASC));
            externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
            // 013040               OUTPUT PROCEDURE SORT-OUT.
            sortOut();
            // 013200 0000-END-RTN.                                                    86/11/04
            // 013400     CLOSE REPORTFL   WITH   SAVE.                                95/03/30
            // 013450     CLOSE BOTSRDB.
            // 013500     DISPLAY "SYM/CL/BH/C077 GENERATE BD/CL/BH/C077 OK".
            try {
                textFile.writeFileContent(reportPath, reportContents, CHARSET_BIG5);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }

        batchResponse(event);
    }

    private void init(C077 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr init");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        // 012961     MOVE  FD-BHDATE-TBSDY  TO   WK-TEMP-DATE.
        // 012971     MOVE  WK-TEMP-DATE     TO   WK-DATE-R.
        wkDateR = processDate;
        wkYear = wkDateR.substring(1, 3);
        wkMonth = wkDateR.substring(3, 5);
        wkDd = wkDateR.substring(5, 7);
        // 018400    MOVE              0     TO     WK-SUBCNT.                     95/03/30
        // 018500    MOVE              0     TO     WK-SUBAMT.                     95/03/30
        // 018600    MOVE              0     TO     WK-TOTCNT.                     95/03/30
        // 018700    MOVE              0     TO     WK-TOTAMT.                     95/03/30
        // 018800    MOVE              0     TO     WK-PBRNO.
        wkSubcnt = 0;
        wkSubamt = BigDecimal.ZERO;
        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
        wkPbrno = 0;

        sortPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_SORT_NAME;
        reportPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_REPORT_NAME;
    }

    private void sortIn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr sortIn");
        // 033800 SORT-IN     SECTION.
        // 033900 SORT-IN-RTN.
        // 034000     FIND NEXT  DB-CLDTL-IDX3 AT  DB-CLDTL-CODE ="121454"
        // 034200                          AND DB-CLDTL-DATE =     FD-BHDATE-TBSDY
        // 034400     ON EXCEPTION
        // 034500     GO TO     SORT-IN-EXIT.
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C077Lsnr CODE = {} ENTDY = {}",
                CODE_121454,
                processDate);

        List<CldtlbyCodeEntdyBus> lCldtl =
                cldtlService.findbyCodeEntdy(
                        CODE_121454, parse.string2Integer(processDate), 0, Integer.MAX_VALUE);
        if (Objects.isNull(lCldtl)) {
            isNoData = true;
            return;
        }
        isNoData = false;
        for (CldtlbyCodeEntdyBus cldtl : lCldtl) {
            // 034600     MOVE   DB-CLDTL-CLLBR    TO SD-CLLBR.
            // 034700     MOVE   DB-CLDTL-CODE     TO SD-CODE .
            // 034800     MOVE   DB-CLDTL-DATE     TO SD-DATE.
            // 034900     MOVE   DB-CLDTL-SITDATE  TO SD-SITDATE.
            // 035000     MOVE   DB-CLDTL-AMT      TO SD-AMT  .
            // 035100     MOVE   DB-CLDTL-RCPTID   TO SD-RCPTID.
            // 035200     MOVE   DB-CLDTL-TIME     TO SD-TIME.
            // 035300     MOVE   DB-CLDTL-USERDATA TO SD-USERDATA.
            // 035400     MOVE   DB-CLDTL-TXTYPE   TO SD-TXTYPE.
            // 035450* 依銷帳編號五到七碼決定主辦分行
            // 035500     MOVE   DB-CLDTL-RCPTID(5:3) TO SD-PBRNO.
            // 035600     MOVE   1                 TO SD-CNT.
            sdCllbr = cldtl.getCllbr();
            sdCode = cldtl.getCode();
            sdDate = cldtl.getEntdy();
            sdSitdate = cldtl.getSitdate();
            sdAmt = cldtl.getAmt();
            sdRcptid = cldtl.getRcptid();
            sdTime = cldtl.getTime();
            sdUserdata = cldtl.getUserdata();
            sdTxtype = cldtl.getTxtype();
            sdPbrno = cldtl.getRcptid().substring(4, 7);
            sdCnt = 1;
            // 035700     RELEASE SD-REC.
            sortFileContents.add(sdRec());
            // 035800     GO TO SORT-IN-RTN.
        }
        try {
            textFile.writeFileContent(sortPath, sortFileContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 035900 SORT-IN-EXIT.
    }

    private void sortOut() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr sortOut");
        // 036500 SORT-OUT  SECTION.
        // 036700 SORT-OUT-RTN.
        // 036900     RETURN    SORTFL  AT  END
        // 037000       GO  TO  SORT-OUT-LAST.
        // 037100
        List<String> lines = textFile.readFileContent(sortPath, CHARSET);
        for (String detail : lines) {
            // 037300* 首筆則將各欄位之值搬入
            sdDate = parse.string2Integer(detail.substring(0, 8));
            sdPbrno = detail.substring(8, 11);
            sdCllbr = parse.string2Integer(detail.substring(11, 14));
            sdCode = detail.substring(14, 20);
            sdRcptid = detail.substring(20, 46);
            sdSitdate = parse.string2Integer(detail.substring(46, 54));
            sdTime = parse.string2Integer(detail.substring(54, 60));
            sdAmt = parse.string2BigDecimal(detail.substring(60, 70));
            sdTxtype = detail.substring(70, 71);
            sdUserdata = detail.substring(71, 111);
            sdCnt = parse.string2Integer(detail.substring(111, 112));
            // 037400     IF WK-PBRNO                  =    0
            if (wkPbrno == 0) {
                // 037500       MOVE    SD-PBRNO             TO   WK-PBRNO,
                // 037600                                         WK-BRNO-R
                // 037700       MOVE    SD-DATE              TO   WK-DATE
                // 037850       MOVE    SD-CLLBR             TO   WK-CLLBR
                // 037900       ADD     SD-AMT               TO   WK-SUBAMT,
                // 038000                                         WK-TOTAMT
                // 038100       ADD     1                    TO   WK-SUBCNT,
                // 038200                                         WK-TOTCNT
                wkPbrno = parse.string2Integer(sdPbrno);
                wkBrnoR = sdPbrno;
                wkDate = sdDate;
                wkCllbr = sdCllbr;
                wkSubamt = wkSubamt.add(sdAmt);
                wkTotamt = wkTotamt.add(sdAmt);
                wkSubcnt = wkSubcnt + 1;
                wkTotcnt = wkTotcnt + 1;
                // 038300       PERFORM RPT077-WTIT-RTN    THRU RPT077-WTIT-EXIT
                rpt077_Wtit_Rtn();
                // 038400       GO  TO  SORT-OUT-RTN.
                continue;
            }
            // 038500* 相同代付主辦行相同代付行則續作加總
            // 038510     IF        SD-PBRNO           =    WK-PBRNO
            // 038520       AND     SD-CLLBR           =    WK-CLLBR
            if (parse.string2Integer(sdPbrno) == wkPbrno && sdCllbr == wkCllbr) {
                // 038530       PERFORM DTL-SUM-RTN        THRU DTL-SUM-EXIT
                dtl_Sum_Rtn();
                // 038540       GO   TO SORT-OUT-RTN.
                continue;
            }
            // 038550* 相同代付主辦行不同代付行則將資料寫入報表檔中
            // 038600     IF   SD-PBRNO                =    WK-PBRNO
            // 038700       AND SD-CLLBR           NOT =    WK-CLLBR
            if (parse.string2Integer(sdPbrno) == wkPbrno && sdCllbr != wkCllbr) {
                // 038800       PERFORM RPT-DTL-RTN        THRU RPT-DTL-EXIT
                rpt_Dtl_Rtn();
                // 038900       GO   TO SORT-OUT-RTN.
                continue;
            }
            // 039000* 不同代付主辦行則直接換頁
            // 039100     IF     SD-PBRNO              NOT =    WK-PBRNO
            if (parse.string2Integer(sdPbrno) != wkPbrno) {
                // 039150            MOVE SD-PBRNO         TO       WK-BRNO-R
                wkBrnoR = sdPbrno;
            }
            // 039200       PERFORM SWITCH-CODE-RTN    THRU SWITCH-CODE-EXIT.
            switch_Code_Rtn();
            // 039300       GO TO   SORT-OUT-RTN.
        }
        // 039400 SORT-OUT-LAST.
        // 039450* 最後還要印最後分行之總筆數金額
        // 039500       PERFORM LAST-RECODE-RTN      THRU LAST-RECODE-EXIT.
        last_Recode_Rtn();
        // 039600 SORT-OUT-EXIT.
        // 039700     EXIT.
    }

    private void rpt077_Wtit_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr rpt077_Wtit_Rtn");
        // 028600 RPT077-WTIT-RTN.                                                 95/03/30
        // 028700    MOVE     WK-YEAR                 TO      WK-YEAR-P.
        // 028800    MOVE     WK-MONTH                TO      WK-MONTH-P.
        // 028850    MOVE     WK-DD                   TO      WK-DD-P.
        // 028900    MOVE     PARA-YYY                TO      WK-YYY-R.            95/03/30
        // 029000    MOVE     PARA-MM                 TO      WK-MM-R.             95/03/30
        // 029100    MOVE     PARA-DD                 TO      WK-DD-R.             95/03/30
        // 029200    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 029300    WRITE    REPORT-LINE          AFTER      PAGE.                95/03/30
        reportContents.add(PAGE_SEPARATOR);
        // 029400    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 029500    WRITE    REPORT-LINE          AFTER      1.                   93/08/05
        // 029600    WRITE    REPORT-LINE           FROM      RPT077-TIT1.         95/03/30
        reportContents.add("");
        reportContents.add(rpt077_Tit1());
        // 029700    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 029800    WRITE    REPORT-LINE          AFTER      1.                   95/03/30
        // 029900    WRITE    REPORT-LINE           FROM      RPT077-TIT2.         95/03/30
        reportContents.add("");
        reportContents.add(rpt077_Tit2());
        // 030000    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 030100    WRITE    REPORT-LINE           FROM      RPT077-TIT3.         95/03/30
        reportContents.add(rpt077_Tit3());
        // 030200    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 030300    WRITE    REPORT-LINE          AFTER      1.                   95/03/30
        // 030400    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 030500    WRITE    REPORT-LINE           FROM      RPT077-TIT4.         95/03/30
        reportContents.add("");
        reportContents.add(rpt077_Tit4());
        // 030600    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 030700    WRITE    REPORT-LINE           FROM      RPT077-GATE-LINE.    95/03/30
        reportContents.add(rpt077_Gate_Line());

        // 030800 RPT077-WTIT-EXIT.                                                95/03/30
    }

    private void dtl_Sum_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr dtl_Sum_Rtn");
        // 019200 DTL-SUM-RTN.                                                     95/03/30
        // 019300    ADD               1     TO     WK-SUBCNT,                     95/03/30
        // 019400                                   WK-TOTCNT.                     95/03/30
        wkSubcnt = wkSubcnt + 1;
        wkTotcnt = wkTotcnt + 1;
        // 019500    ADD       SD-AMT        TO     WK-SUBAMT,
        // 019600                                   WK-TOTAMT.                     95/03/30
        wkSubamt = wkSubamt.add(sdAmt);
        wkTotamt = wkTotamt.add(sdAmt);
        // 019700 DTL-SUM-EXIT.                                                    95/03/30
    }

    private void rpt_Dtl_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr rpt_Dtl_Rtn");
        // 025200 RPT-DTL-RTN.                                                     95/03/30

        // 025300       MOVE    SPACES            TO      REPORT-LINE.             95/03/30
        // 025400       MOVE    WK-SUBCNT         TO      RPT077-CNT-R.            95/03/30
        // 025500       MOVE    WK-SUBAMT         TO      RPT077-AMT-R.            95/03/30
        // 025550       MOVE    WK-CLLBR          TO      RPT077-CLLBR-R.
        // 025600       WRITE   REPORT-LINE       FROM    RPT077-DTL.              95/03/30
        reportContents.add(rpt077_Dtl());
        // 025700       MOVE    0                 TO      WK-SUBCNT,WK-SUBAMT.     95/03/30
        // 025750       MOVE    SD-DATE           TO      WK-DATE.
        // 025820       MOVE    SD-CLLBR          TO      WK-CLLBR,
        // 025840                                         RPT077-CLLBR-R.
        // 025900       ADD     SD-AMT            TO      WK-SUBAMT,
        // 026000                                         WK-TOTAMT.               95/03/30
        // 026100       ADD     1                 TO      WK-SUBCNT,               95/03/30
        // 026200                                         WK-TOTCNT.               95/03/30
        wkSubcnt = 0;
        wkSubamt = BigDecimal.ZERO;
        wkDate = sdDate;
        wkCllbr = sdCllbr;
        wkSubamt = wkSubamt.add(sdAmt);
        wkTotamt = wkTotamt.add(sdAmt);
        wkSubcnt = wkSubcnt + 1;
        wkTotcnt = wkTotcnt + 1;

        // 026300 RPT-DTL-EXIT.                                                    95/03/30
    }

    private void switch_Code_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr rpt_Dtl_Rtn");
        // 026600 SWITCH-CODE-RTN.                                                 95/03/30
        // 027100       PERFORM LAST-RECODE-RTN   THRU    LAST-RECODE-EXIT.        95/03/30
        last_Recode_Rtn();
        // 027200       PERFORM INITIAL-RTN       THRU    INITIAL-EXIT.            95/03/30
        wkSubcnt = 0;
        wkSubamt = BigDecimal.ZERO;
        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
        wkPbrno = 0;
        // 027300       MOVE    SPACES            TO      REPORT-LINE.             95/03/30
        // 027350       WRITE   REPORT-LINE       AFTER   PAGE.                    95/03/30
        reportContents.add(PAGE_SEPARATOR);

        // 027400       MOVE    SD-PBRNO          TO      WK-PBRNO.
        // 027470       MOVE    SD-DATE           TO      WK-DATE.
        // 027520       MOVE    SD-CLLBR          TO      WK-CLLBR,
        // 027540                                         RPT077-CLLBR-R.
        // 027600       ADD     SD-AMT            TO      WK-SUBAMT,
        // 027700                                         WK-TOTAMT.               95/03/30
        // 027800       ADD     1                 TO      WK-SUBCNT,               95/03/30
        // 027900                                         WK-TOTCNT.               95/03/30
        wkPbrno = parse.string2Integer(sdPbrno);
        wkDate = sdDate;
        wkCllbr = sdCllbr;
        wkSubamt = wkSubamt.add(sdAmt);
        wkTotamt = wkTotamt.add(sdAmt);
        wkSubcnt = wkSubcnt + 1;
        wkTotcnt = wkTotcnt + 1;
        // 028100       PERFORM RPT077-WTIT-RTN   THRU    RPT077-WTIT-EXIT.        95/03/30
        rpt077_Wtit_Rtn();
        // 028300 SWITCH-CODE-EXIT.                                                95/03/30
        // 028400   EXIT.                                                          94/11/30
    }

    private void last_Recode_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr last_Recode_Rtn");
        // 032400 LAST-RECODE-RTN.                                                 95/03/30

        // 032420    MOVE    SPACES            TO      REPORT-LINE.                95/03/30
        // 032440    MOVE    WK-SUBCNT         TO      RPT077-CNT-R.               95/03/30
        // 032460    MOVE    WK-SUBAMT         TO      RPT077-AMT-R.               95/03/30
        // 032470    MOVE    WK-CLLBR          TO      RPT077-CLLBR-R.
        // 032480    WRITE   REPORT-LINE       FROM    RPT077-DTL.                 95/03/30
        reportContents.add(rpt077_Dtl());
        // 032600    MOVE    WK-TOTCNT         TO      RPT077-TOTCNT-R.            95/03/30
        // 032700    MOVE    WK-TOTAMT         TO      RPT077-TOTAMT-R.            95/03/30
        // 032800    MOVE    SPACE             TO      REPORT-LINE.                95/03/30
        // 032900    PERFORM RPT077-WTOT-RTN   THRU    RPT077-WTOT-EXIT.           95/03/30
        rpt077_Wtot_Rtn();
        // 033000 LAST-RECODE-EXIT.                                                95/03/30
        // 033100    EXIT.                                                         95/03/30
    }

    private void rpt077_Wtot_Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C077Lsnr rpt077_Wtot_Rtn");
        // 031100 RPT077-WTOT-RTN.                                                 95/03/30
        // 031200    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 031300    WRITE    REPORT-LINE          AFTER      1.                   95/03/30
        // 031400    WRITE    REPORT-LINE           FROM      RPT077-GATE-LINE.    95/03/30
        // 031700    WRITE    REPORT-LINE           FROM      RPT077-TOT.          95/03/30
        reportContents.add("");
        reportContents.add(rpt077_Gate_Line());
        reportContents.add(rpt077_Tot());
        // 031800    MOVE     SPACES                  TO      REPORT-LINE.         95/03/30
        // 031900    WRITE    REPORT-LINE          AFTER      1.                   95/03/30
        reportContents.add("");

        // 032100 RPT077-WTOT-EXIT.                                                95/03/30
        // 032200    EXIT.                                                         95/03/30
    }

    private String rpt077_Dtl() {
        // 009700 01   RPT077-DTL.                                                 95/03/30
        // 009800  03  FILLER                              PIC X(04) VALUE SPACES. 95/03/30
        // 009900  03  RPT077-CLLBR-R                      PIC X(03) VALUE SPACES.
        // 010000  03  FILLER                              PIC X(09) VALUE SPACES.
        // 010100  03  RPT077-AMT-R                        PIC ZZ,ZZZ,ZZZ,ZZ9.
        // 010200  03  FILLER                              PIC X(03) VALUE SPACES. 95/03/30
        // 010300  03  RPT077-CNT-R                        PIC ZZ,ZZZ,ZZ9.         95/03/30
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("" + wkCllbr, 3));
        sb.append(formatUtil.padX("", 9));
        sb.append(reportUtil.customFormat("" + wkSubamt, "ZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("" + wkSubcnt, "ZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String rpt077_Tit1() {
        // 05000 01   RPT077-TIT1.                                                95/03/30
        // 005100  03  FILLER                              PIC X(22) VALUE SPACES.
        // 005500  03  FILLER                              PIC X(36) VALUE
        // 005600      " 汽燃費退費憑單報表－依兌付分行小計 ".
        // 005700* 03  FILLER                              PIC X(06) VALUE SPACES.
        // 005800  03  FILLER                              PIC X(10) VALUE         95/03/30
        // 005900      " 印表日： ".                                               95/03/30
        // 006000  03  WK-YYY-R                            PIC 999.                95/03/30
        // 006100  03  FILLER                              PIC X(01) VALUE "/".    95/03/30
        // 006200  03  WK-MM-R                             PIC 99.                 95/03/30
        // 006300  03  FILLER                              PIC X(01) VALUE "/".    95/03/30
        // 006400  03  WK-DD-R                             PIC 99.                 95/03/30
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 22));
        sb.append(formatUtil.padX(" 汽燃費退費憑單報表－依兌付分行小計 ", 36));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 印表日： ", 10));
        sb.append(reportUtil.customFormat(dateUtil.getNowStringRoc(), "999/99/99"));
        return sb.toString();
    }

    private String rpt077_Tit2() {
        // 006500 01   RPT077-TIT2.                                                95/03/30
        // 006600  03  FILLER                              PIC X(02) VALUE SPACES. 95/03/30
        // 006700  03  FILLER                           PIC X(12) VALUE
        // 006800      " 代付日期： ".
        // 006820  03  WK-YEAR-P                        PIC 999.
        // 006840  03  FILLER                           PIC X(04) VALUE " 年 ".
        // 006860  03  WK-MONTH-P                       PIC 99.
        // 006880  03  FILLER                           PIC X(04) VALUE " 月 ".
        // 006885  03  WK-DD-P                          PIC 99.
        // 006890  03  FILLER                           PIC X(04) VALUE " 日 ".
        // 006900  03  FILLER                              PIC X(25) VALUE SPACES.
        // 007000  03  FILLER                              PIC X(10) VALUE         95/03/30
        // 007100      " 頁　次： ".                                               95/03/30
        // 007200  03  WK-PAGE-R                           PIC Z9    VALUE 1.      95/03/30
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 代付日期： ", 12));
        sb.append(formatUtil.pad9(wkYear, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.pad9(wkMonth, 2));
        sb.append(formatUtil.padX(" 月 ", 4));
        sb.append(formatUtil.pad9(wkDd, 2));
        sb.append(formatUtil.padX(" 日 ", 4));
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX(" 頁　次： ", 12));
        sb.append(reportUtil.customFormat("1", "Z9"));
        return sb.toString();
    }

    private String rpt077_Tit3() {
        // 007300 01   RPT077-TIT3.                                                95/03/30
        // 007400  03  FILLER                              PIC X(02) VALUE SPACES. 95/03/30
        // 007500  03  FILLER                              PIC X(14) VALUE
        // 007600      " 縣市主辦行： ".
        // 007700  03  WK-BRNO-R                           PIC X(03).
        // 007800  03  FILLER                              PIC X(39) VALUE SPACES.
        // 007900  03  FILLER                              PIC X(12) VALUE         95/03/30
        // 008000      " 報表名稱： ".                                             95/03/30
        // 008100  03  WK-FORM-R                           PIC X(04) VALUE "C077".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 縣市主辦行： ", 14));
        sb.append(formatUtil.padX(wkBrnoR, 3));
        sb.append(formatUtil.padX("", 39));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX("C077", 4));
        return sb.toString();
    }

    private String rpt077_Tit4() {
        // 008200 01   RPT077-TIT4.                                                95/03/30
        // 008300  03  FILLER                              PIC X(03) VALUE SPACES. 95/03/30
        // 008400  03  FILLER                              PIC X(10) VALUE         95/03/30
        // 008500      " 代付分行 ".
        // 008600  03  FILLER                              PIC X(10) VALUE SPACES.
        // 008700  03  FILLER                              PIC X(08) VALUE         95/03/30
        // 008800      " 總金額 ".                                                 95/03/30
        // 008900  03  FILLER                              PIC X(05) VALUE SPACES. 95/03/30
        // 009000  03  FILLER                              PIC X(08) VALUE         95/03/30
        // 009100      " 總件數 ".                                                 95/03/30
        // 009200  03  FILLER                              PIC X(14) VALUE SPACES. 95/03/30
        // 009300  03  FILLER                              PIC X(06) VALUE         95/03/30
        // 009400      " 備註 ".                                                   95/03/30
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 代付分行 ", 10));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 總金額 ", 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總件數 ", 8));
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX(" 備註 ", 6));
        return sb.toString();
    }

    private String rpt077_Gate_Line() {
        // 009500 01   RPT077-GATE-LINE.                                           95/03/30
        // 009600  03  FILLER                              PIC X(80) VALUE ALL "-".95/03/30
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        return sb.toString();
    }

    private String rpt077_Tot() {
        // 010400 01   RPT077-TOT.                                                 95/03/30
        // 010500  03  FILLER                              PIC X(05) VALUE SPACES. 95/03/30
        // 010600  03  FILLER                              PIC X(08) VALUE         95/03/30
        // 010700      " 總　計 ".                                                 95/03/30
        // 010800  03  RPT077-TOTAMT-R                  PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        // 010900  03  FILLER                           PIC X(03) VALUE SPACES.    95/03/30
        // 011000  03  RPT077-TOTCNT-R                  PIC ZZ,ZZZ,ZZ9.            95/03/30
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總　計 ", 8));
        sb.append(reportUtil.customFormat("" + wkTotamt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("" + wkTotcnt, "ZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String sdRec() {
        // 002410 01  SD-REC.
        // 002415     03 SD-DATE              PIC 9(08).
        // 002420     03 SD-PBRNO             PIC 9(03).
        // 002425     03 SD-CLLBR             PIC 9(03).
        // 002430     03 SD-CODE              PIC X(06).
        // 002435     03 SD-RCPTID            PIC X(26).
        // 002440     03 SD-SITDATE           PIC 9(08).
        // 002445     03 SD-TIME              PIC 9(06).
        // 002450     03 SD-AMT               PIC 9(10).
        // 002455     03 SD-TXTYPE            PIC X(01).
        // 002460     03 SD-USERDATA          PIC X(40).
        // 002465     03 SD-CNT               PIC 9(01).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9("" + sdDate, 8));
        sb.append(formatUtil.pad9(sdPbrno, 3));
        sb.append(formatUtil.pad9("" + sdCllbr, 3));
        sb.append(formatUtil.padX(sdCode, 6));
        sb.append(formatUtil.padX(sdRcptid, 26));
        sb.append(formatUtil.pad9("" + sdSitdate, 8));
        sb.append(formatUtil.pad9("" + sdTime, 6));
        sb.append(formatUtil.pad9("" + sdAmt, 10));
        sb.append(formatUtil.padX(sdTxtype, 1));
        sb.append(formatUtil.padX(sdUserdata, 40));
        sb.append(formatUtil.pad9("" + sdCnt, 1));
        return sb.toString();
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }

    private void batchResponse(C077 event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
