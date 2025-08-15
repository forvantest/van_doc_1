/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.RPTC015PMPZ;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("RPTC015PMPZLsnr")
@Scope("prototype")
public class RPTC015PMPZLsnr extends BatchListenerCase<RPTC015PMPZ> {
    @Autowired private FsapSync fsapSync;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private static final String FILE_CHARSET = "UTF-8"; // 產檔檔案編碼
    private static final String REPORT_CHARSET = "BIG-5"; // 產報表檔案編碼
    private static final String FOLD_INPUT_NAME = "CONN"; // 讀檔資料夾
    private static final String FILE_INPUT_NAME = "PMPZ"; // 讀檔檔名
    private static final String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";
    private StringBuilder sb = new StringBuilder();
    private List<String> fileAContents; // 報表內容A
    private List<String> fileBContents; // 報表內容B
    private String inputFilePath; // 讀檔路徑
    private String outputFilePathA; // 讀檔路徑A
    private String outputFilePathB; // 讀檔路徑B
    private RPTC015PMPZ event;

    private final DecimalFormat dFormatNum = new DecimalFormat("#,##0");
    private String wkTaskDate = "";
    private String wkIndate = "";
    private int wkPctl = 0;
    private int wkClndrKey = 0;
    private String pmpzCtl = "";
    private String pmpzDate = "";
    private BigDecimal pmpzAmt = BigDecimal.ZERO;
    private String pmpzCuno = "";
    private String pmpzTpdy = "";
    private String pmpzTrno = "";
    private String wkSitdate = "";
    private int wkChktrd = 0;
    private int wkSumnnday = 0;
    private int wkMvdp = 0;
    private int wkMvd = 0;
    private int wkMvdYYY = 0;
    private int wkMvdMM = 0;
    private int wkMvdDD = 0;
    private int clndrTmndy = 0;
    private int clndrNdycnt = 0;
    private int clndrNndcnt = 0;
    private int clndrNbsdy = 0;
    private int clndrNnbsdy = 0;
    private int clndrN3bsdy = 0;
    private int clndrTbsdy = 0;
    ;
    private int clndrTbsYY = 0;
    private int clndrTbsMM = 0;
    private int clndrTbsDD = 0;
    private int wkTrd = 0;
    private int wkTrdp = 0;
    private int wkTrdYYY = 0;
    private int wkTrdMM = 0;
    private int wkTrdDD = 0;
    private int rpt2PageP = 0;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private BigDecimal wkTempTxAmt = BigDecimal.ZERO;
    private int wkTotcnt = 0;
    private int wkTempcnt = 0;
    private int wkTradecnt = 0;
    private String wkTempTrno = "";
    private Boolean firstPage = true;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(RPTC015PMPZ event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(RPTC015PMPZ event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr run()");
        init(event);
        main();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr main");
        //// 若FD-PMPZ檔案存在，開啟檔案、執行0500-REPORT-RTN、關閉檔案

        // 030200   IF  ATTRIBUTE RESIDENT OF FD-PMPZ IS = VALUE(TRUE)
        if (textFile.exists(inputFilePath)) {
            // 030300       OPEN  INPUT     FD-PMPZ
            // 030400       OPEN  OUTPUT    REPORTFL
            // 030500       OPEN  OUTPUT    REPORTFL2
            // 030600       PERFORM 0500-REPORT-RTN THRU 0500-REPORT-EXIT
            reportRtn();
            // 030700       CLOSE FD-PMPZ
            // 030800       CLOSE REPORTFL  WITH SAVE
            // 030900       CLOSE REPORTFL2 WITH SAVE.

            try {
                textFile.writeFileContent(outputFilePathA, fileAContents, REPORT_CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            try {
                textFile.writeFileContent(outputFilePathB, fileBContents, REPORT_CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        // 031100 0000-END-RTN.
    }

    private void reportRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr reportRtn");
        // 031600 0500-REPORT-RTN.
        // 031700
        // 031800 0500-LOOP-S.
        //// 循序讀取FD-PMPZ，直到檔尾，跳到0500-LOOP-E

        // 031900      READ  FD-PMPZ  AT  END  GO TO  0500-LOOP-E.
        List<String> lines = textFile.readFileContent(inputFilePath, FILE_CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 004300     01 PMPZ-REC.
            // 004400     05 REC-PMPZ-CTL          PIC X(01)   . 0-1
            pmpzCtl = detail.substring(0, 1);
            // 004500     05 REC-PMPZ-SEND         PIC X(08)   . 1-9
            // 004600     05 REC-PMPZ-RECI         PIC X(08)   . 9-17
            // 004700     05 REC-PMPZ-TYPE         PIC X(03)   . 17-20
            // 004800     05 REC-PMPZ-DATE         PIC 9(07)   . 20-27
            pmpzDate = detail.substring(20, 27);
            // 004900     05 REC-PMPZ-ACNO         PIC X(14)   . 27-41
            // 005000     05 REC-PMPZ-AMT          PIC 9(12)V99. 41-56
            pmpzAmt = parse.string2BigDecimal(detail.substring(41, 56));
            // 005100     05 REC-PMPZ-CHKC         PIC X(01)   . 56-57
            // 005200     05 REC-PMPZ-KEEP         PIC X(02)   . 57-59
            // 005300     05 REC-PMPZ-PTNO         PIC X(05)   . 59-64
            // 005400     05 REC-PMPZ-STAT         PIC X(02)   . 64-66
            // 005500     05 REC-PMPZ-CUNO         PIC X(11)   . 66-77
            pmpzCuno = detail.substring(66, 77);
            // 005600     05 REC-PMPZ-KEEP2        PIC X(02)   . 77-79
            // 005700     05 REC-PMPZ-ARCO         PIC X(04)   . 79-83
            // 005800     05 REC-PMPZ-TPDY         PIC X(07)   . 83-90
            pmpzTpdy = detail.substring(83, 90);
            // 005900     05 REC-PMPZ-TPTY         PIC X(01)   . 90-91
            // 006000     05 FILLER                PIC X(20)   . 91-111
            // 006100     05 REC-PMPZ-TRNO         PIC X(10)   . 111-121
            pmpzTrno = detail.substring(111, 121);

            //// 若REC-PMPZ-CTL = "1"
            ////  A.執行7200-GETTRD-RTN，依指定日期(WK-SITDATE)，設定 傳檔日期WK-TRD & 解繳日期WK-MVD
            ////  B.執行1500-HEAD-S，寫REPORTFL2表頭

            // 032100      IF  REC-PMPZ-CTL = "1"
            if ("1".equals(pmpzCtl)) {
                // 032200          MOVE REC-PMPZ-DATE     TO  WK-SITDATE
                wkSitdate = pmpzDate;
                // 032300          PERFORM 7200-GETTRD-RTN  THRU 7200-GETTRD-EXIT
                gettrdRtn();
                // 032400          PERFORM 1500-HEAD-S THRU 1500-HEAD-E.
                headS(PAGE_SEPARATOR);
            }
            //// 若REC-PMPZ-CTL = "2"
            ////  執行1000-RPC015-B-RTN，寫REPORTFL2明細
            //
            // 032700      IF  REC-PMPZ-CTL = "2"
            if ("2".equals(pmpzCtl)) {
                // 032800          PERFORM 1000-RPC015-B-RTN  THRU 1000-RPCO15-B-EXIT.
                rpc015BRtn();
                // 032900
            }
            //// LOOP讀下一筆FD-PMPZ
            // 033000      GO TO 0500-LOOP-S.
            // 033100 0500-LOOP-E.
            if (cnt == lines.size()) {
                //// 若WK-PCTL <> 0
                ////  A.執行1600-TAIL-S，寫REPORTFL2表尾
                ////  B.執行3000-RPC015-A-RTN，寫REPORTFL代收台電公司費款日結單
                //// 若WK-PCTL = 0
                ////  A.執行7800-NODATA-RP-RTN，寫REPORTFL2(" 無交易 ")、REPORTFL(" 無交易 ")
                // 033200      IF  WK-PCTL NOT = 0
                if (wkPctl != 0) {
                    // 033300          PERFORM 1600-TAIL-S         THRU 1600-TAIL-E
                    tailS();
                    // 033400          PERFORM 3000-RPC015-A-RTN   THRU 3000-RPC015-A-EXIT
                    rpc015ARtn();
                    // 033500      ELSE
                } else {
                    // 033600          PERFORM 7800-NODATA-RP-RTN  THRU 7800-NODATA-RP-EXIT
                    noDataRpRtn();
                }
                // 033700      END-IF.
            }
        }
        // 033900 0500-REPORT-EXIT.
    }

    private void noDataRpRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr noDataRpRtn");
        // 057000 7800-NODATA-RP-RTN.

        //// A.執行7200-GETTRD-RTN，依指定日期(WK-SITDATE)，設定 傳檔日期WK-TRD & 解繳日期WK-MVD
        //// B.執行1900-NODATA-2-RTN，寫REPORTFL2(" 無交易 ")
        //// C.執行3900-NODATA-1-RTN，寫REPORTFL(" 無交易 ")

        // 057100      MOVE  WK-INDATE          TO WK-SITDATE.
        wkSitdate = wkIndate;
        // 057300      PERFORM 7200-GETTRD-RTN  THRU 7200-GETTRD-EXIT.
        gettrdRtn();
        // 057400      PERFORM 1900-NODATA-2-RTN  THRU 1900-NODATA-2-EXIT.
        nodata2Rtn();
        // 057500      PERFORM 3900-NODATA-1-RTN  THRU 3900-NODATA-1-EXIT.
        nodata1Rtn();

        // 057700 7800-NODATA-RP-EXIT.
    }

    private void nodata1Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr nodata1Rtn");
        // 049500 3900-NODATA-1-RTN.

        //// 寫REPORTFL代收台電公司費款日結單(" 無交易 ")

        // 049700* 搬繳匯日期
        // 049800      MOVE WK-MVD-YYY       TO RPT-YYY.
        int rptYYY = wkMvdYYY;
        // 049900      MOVE WK-MVD-MM        TO RPT-MM.
        int rptMM = wkMvdMM;
        // 050000      MOVE WK-MVD-DD        TO RPT-DD.
        int rptDD = wkMvdDD;
        // 050100* 搬傳送日期
        // 050200      MOVE WK-TRD-YYY       TO RPT-RDAY1-YYY.
        int rptRday1YYY = wkTrdYYY;
        // 050300      MOVE WK-TRD-MM        TO RPT-RDAY1-MM.
        int rptRday1MM = wkTrdMM;
        // 050400      MOVE WK-TRD-DD        TO RPT-RDAY1-DD.
        int rptRday1DD = wkTrdDD;
        // 050500* 搬交易日期
        // 050600      MOVE FD-CLNDR-TBSYY   TO RPT-4A-YYY.
        int rpt4AYYY = clndrTbsYY;
        // 050700      MOVE FD-CLNDR-TBSMM   TO RPT-4A-MM.
        int rpt4AMM = clndrTbsMM;
        // 050800      MOVE FD-CLNDR-TBSDD   TO RPT-4A-DD.
        int rpt4ADD = clndrTbsDD;
        // 050900      MOVE " 無　交　易 "   TO RPT-4A-TXT.
        String rpt4aTxt = " 無　交　易 ";
        // 051000
        // 051100      WRITE REPORTFL-REC   FROM RPT-1-REC AFTER PAGE.
        // 011000 01 RPT-1-REC.
        // 011100    03 FILLER            PIC X(20) VALUE SPACES.
        // 011200    03 RPT-HEAD1-TITLE   PIC X(40) VALUE
        // 011300        " 臺灣銀行　代收台電公司費款日結單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.padX(" 臺灣銀行　代收台電公司費款日結單 ", 40));
        fileAContents.add(sb.toString());

        // 051200      WRITE REPORTFL-REC   FROM RPT-2-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");
        // 011400 01 RPT-2-REC.
        // 011500    03 FILLER            PIC X(08) VALUE SPACES.
        // 011600    03 FILLER            PIC X(14) VALUE " 單位代號： ".
        // 011700    03 FILLER            PIC X(10) VALUE "PMPZ     ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 單位代號： ", 14));
        sb.append(formatUtil.padX("PMPZ     ", 10));
        fileAContents.add(sb.toString());

        // 051300      WRITE REPORTFL-REC   FROM RPT-3-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");
        // 011800 01 RPT-3-REC.
        // 011900    03 FILLER            PIC X(08) VALUE SPACES.
        // 012000    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 012100    03 RPT-YYY           PIC ZZ9.
        // 012200    03 FILLER            PIC X(01) VALUE "/".
        // 012300    03 RPT-MM            PIC 99.
        // 012400    03 FILLER            PIC X(01) VALUE "/".
        // 012500    03 RPT-DD            PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.padX("" + rptYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptDD, 2));
        fileAContents.add(sb.toString());

        // 051400      WRITE REPORTFL-REC   FROM RPT-4-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");
        // 012600 01 RPT-4-REC.
        // 012700    03 FILLER            PIC X(08) VALUE SPACES.
        // 012800    03 FILLER            PIC X(12) VALUE " 傳檔日期： ".
        // 012900    03 RPT-RDAY1-YYY     PIC ZZ9.
        // 013000    03 FILLER            PIC X(01) VALUE "/".
        // 013100    03 RPT-RDAY1-MM      PIC 99.
        // 013200    03 FILLER            PIC X(01) VALUE "/".
        // 013300    03 RPT-RDAY1-DD      PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.padX("" + rptRday1YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1DD, 2));
        fileAContents.add(sb.toString());
        // 051500      WRITE REPORTFL-REC   FROM RPT-4A-REC AFTER 2.
        sb = new StringBuilder();
        fileAContents.add("");
        fileAContents.add("");
        // 013500 01 RPT-4A-REC.
        // 013600    03 FILLER            PIC X(08) VALUE SPACES.
        // 013700    03 FILLER            PIC X(12) VALUE " 交易日期： ".
        // 013800    03 RPT-4A-YYY        PIC ZZ9.
        // 013900    03 FILLER            PIC X(01) VALUE "/".
        // 014000    03 RPT-4A-MM         PIC 99.
        // 014100    03 FILLER            PIC X(01) VALUE "/".
        // 014200    03 RPT-4A-DD         PIC 99.
        // 014300    03 FILLER            PIC X(04) VALUE SPACES.
        // 014400    03 RPT-4A-TXT        PIC X(12) VALUE SPACES.
        // 014500    03 FILLER            PIC X(03) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.padX("" + rpt4AYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt4AMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt4ADD, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt4aTxt, 12));
        sb.append(formatUtil.padX("", 3));
        fileAContents.add(sb.toString());

        // 051700 3900-NODATA-1-EXIT.
    }

    private void nodata2Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr nodata2Rtn");
        // 041100 1900-NODATA-2-RTN.
        // 041200

        //// 執行1500-HEAD-S，寫REPORTFL2表頭
        //// 寫REPORTFL2明細(" 無交易 ")
        //// 執行1600-TAIL-S，寫REPORTFL2表尾

        // 041300      MOVE  0                 TO RPT2-PAGE-P.
        rpt2PageP = 0;
        // 041400      PERFORM 1500-HEAD-S   THRU 1500-HEAD-E.
        headS(PAGE_SEPARATOR);
        // 041500      MOVE  SPACE             TO RPT2-DETAIL.
        // 041600      MOVE  FD-CLNDR-TBSDY    TO RPT2-SITDATE-P.
        int rpt2SitdateP = clndrTbsdy;
        // 041700      MOVE  " 無交易 "        TO RPT2-NO-P.
        String rpt2NoP = " 無交易 ";
        // 041800      WRITE REPORTFL2-REC   FROM RPT2-DETAIL.
        // 022500 01 RPT2-DETAIL.
        // 022600  03  FILLER                           PIC X(05) VALUE SPACES.
        // 022700  03  RPT2-SENDATE-P                   PIC 999/99/99.
        // 022800  03  FILLER                           PIC X(06) VALUE SPACES.
        // 022900  03  RPT2-NO-P                        PIC X(11).
        // 023000  03  FILLER                           PIC X(03) VALUE SPACES.
        // 023100  03  FILLER                           PIC X(08) VALUE SPACES.
        // 023200  03  RPT2-ELEDATE-P                   PIC 99/99/99.
        // 023300  03  FILLER                           PIC X(05) VALUE SPACES.
        // 023400  03  RPT2-AMT-P                       PIC $,$$$,$$$,$$9.
        // 023500  03  FILLER                           PIC X(06) VALUE SPACES.
        // 023600  03  RPT2-SITDATE-P                   PIC 999/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("", 9));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(rpt2NoP, 11));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(reportUtil.customFormat("" + rpt2SitdateP, "999/99/99"), 9));
        fileBContents.add(sb.toString());

        // 041900      MOVE  0                 TO WK-TRADECNT.
        wkTradecnt = 0;
        // 042000      MOVE  0                 TO WK-TOTCNT.
        wkTotcnt = 0;
        // 042100      MOVE  0                 TO WK-TOTAMT.
        wkTotamt = new BigDecimal(0);
        // 042200      PERFORM 1600-TAIL-S   THRU 1600-TAIL-E.
        tailS();
        // 042300      MOVE  0                 TO RPT2-PAGE-P.
        rpt2PageP = 0;

        // 042500 1900-NODATA-2-EXIT.
    }

    private void rpc015ARtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr rpc015ARtn");
        // 042800 3000-RPC015-A-RTN.
        // 042900
        //// 寫REPORTFL代收台電公司費款日結單(RPT-1-REC~RPT-8-REC)
        // 043000* 搬繳匯日期
        // 043100      MOVE WK-MVD-YYY       TO RPT-YYY.
        int rptYYY = wkMvdYYY;
        // 043200      MOVE WK-MVD-MM        TO RPT-MM.
        int rptMM = wkMvdMM;
        // 043300      MOVE WK-MVD-DD        TO RPT-DD.
        int rptDD = wkMvdDD;
        // 043400* 搬傳送日期
        // 043500      MOVE WK-TRD-YYY       TO RPT-RDAY1-YYY.
        int rptRday1YYY = wkTrdYYY;
        // 043600      MOVE WK-TRD-MM        TO RPT-RDAY1-MM.
        int rptRday1MM = wkTrdMM;
        // 043700      MOVE WK-TRD-DD        TO RPT-RDAY1-DD.
        int rptRday1DD = wkTrdDD;
        // 043800* 搬交易日期
        // 043900      MOVE FD-CLNDR-TBSYY   TO RPT-4A-YYY.
        int rpt4AYYY = clndrTbsYY;
        // 044000      MOVE FD-CLNDR-TBSMM   TO RPT-4A-MM.
        int rpt4AMM = clndrTbsMM;
        // 044100      MOVE FD-CLNDR-TBSDD   TO RPT-4A-DD.
        int rpt4ADD = clndrTbsDD;
        // 044200* 筆數靠左對齊
        // 044300      MOVE  WK-TEMPCNT  TO WK-CNT.
        // 044400      MOVE 0 TO COUNTER WK-A WK-B.
        // 044500      INSPECT WK-CNT TALLYING COUNTER FOR LEADING SPACE.
        // 044600      COMPUTE WK-A = COUNTER + 1.
        // 044700      COMPUTE WK-B = 7 - COUNTER.
        // 044800      MOVE WK-CNT(WK-A:WK-B) TO RPT-CNT.
        int rptCnt = wkTempcnt;
        // 044900* 交易筆數靠左對齊
        // 045000      MOVE  WK-TRADECNT TO WK-CNT.
        // 045100      MOVE 0 TO COUNTER WK-A WK-B.
        // 045200      INSPECT WK-CNT TALLYING COUNTER FOR LEADING SPACE.
        // 045300      COMPUTE WK-A = COUNTER + 1.
        // 045400      COMPUTE WK-B = 7 - COUNTER.
        // 045500      MOVE WK-CNT(WK-A:WK-B) TO RPT-CNTA.
        int rptCnta = wkTradecnt;
        // 045600* 總金額靠左對齊
        // 045700      MOVE  WK-TEMPTXAMT  TO WK-TXAMT.
        // 045800      MOVE 0 TO COUNTER WK-A WK-B.
        // 045900      INSPECT WK-TXAMT TALLYING COUNTER FOR LEADING SPACE.
        // 046000      COMPUTE WK-A = COUNTER + 1.
        // 046100      COMPUTE WK-B = 16 - COUNTER.
        // 046200      MOVE WK-TXAMT(WK-A:WK-B) TO RPT-TXAMT.
        BigDecimal rptTxamt = wkTempTxAmt;
        // 046300* 手續費靠左對齊
        // 046400      COMPUTE  WK-TEMPTXFEE =  WK-TRADECNT * 3 .
        BigDecimal wkTempTxFee = new BigDecimal(wkTradecnt).multiply(new BigDecimal(3));
        // 046500      MOVE     WK-TEMPTXFEE TO WK-TXFEE.
        // 046600      MOVE 0 TO COUNTER WK-A WK-B.
        // 046700      INSPECT WK-TXFEE TALLYING COUNTER FOR LEADING SPACE.
        // 046800      COMPUTE WK-A = COUNTER + 1.
        // 046900      COMPUTE WK-B = 13 - COUNTER.
        // 047000      MOVE WK-TXFEE(WK-A:WK-B) TO RPT-TXFEE.
        BigDecimal rptTxFee = wkTempTxFee;
        // 047100* 淨金額靠左對齊
        // 047200      COMPUTE  WK-TXAMT2 =  WK-TEMPTXAMT - WK-TEMPTXFEE.
        BigDecimal wkTxAmt2 = wkTempTxAmt.subtract(wkTempTxFee);
        // 047300      MOVE 0 TO COUNTER WK-A WK-B.
        // 047400      INSPECT WK-TXAMT2 TALLYING COUNTER FOR LEADING SPACE.
        // 047500      COMPUTE WK-A = COUNTER + 1.
        // 047600      COMPUTE WK-B = 16 - COUNTER.
        // 047700      MOVE WK-TXAMT2(WK-A:WK-B) TO RPT-TXAMT2.
        BigDecimal rptTxAmt2 = wkTxAmt2;

        // 047900      MOVE SPACES               TO  RPT-4A-TXT  .
        String rpt4aTxt = "";
        // 048100      WRITE REPORTFL-REC   FROM RPT-1-REC AFTER PAGE.
        // 011000 01 RPT-1-REC.
        // 011100    03 FILLER            PIC X(20) VALUE SPACES.
        // 011200    03 RPT-HEAD1-TITLE   PIC X(40) VALUE
        // 011300        " 臺灣銀行　代收台電公司費款日結單 ".
        sb = new StringBuilder();
        sb.append(firstPage ? " " : PAGE_SEPARATOR);
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.padX(" 臺灣銀行　代收台電公司費款日結單 ", 40));
        fileAContents.add(sb.toString());

        // 048200      WRITE REPORTFL-REC   FROM RPT-2-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");
        // 011400 01 RPT-2-REC.
        // 011500    03 FILLER            PIC X(08) VALUE SPACES.
        // 011600    03 FILLER            PIC X(14) VALUE " 單位代號： ".
        // 011700    03 FILLER            PIC X(10) VALUE "PMPZ     ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 單位代號： ", 14));
        sb.append(formatUtil.padX("PMPZ     ", 10));
        fileAContents.add(sb.toString());

        // 048300      WRITE REPORTFL-REC   FROM RPT-3-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 011800 01 RPT-3-REC.
        // 011900    03 FILLER            PIC X(08) VALUE SPACES.
        // 012000    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 012100    03 RPT-YYY           PIC ZZ9.
        // 012200    03 FILLER            PIC X(01) VALUE "/".
        // 012300    03 RPT-MM            PIC 99.
        // 012400    03 FILLER            PIC X(01) VALUE "/".
        // 012500    03 RPT-DD            PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.padX("" + rptYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptDD, 2));
        fileAContents.add(sb.toString());

        // 048400      WRITE REPORTFL-REC   FROM RPT-4-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 012600 01 RPT-4-REC.
        // 012700    03 FILLER            PIC X(08) VALUE SPACES.
        // 012800    03 FILLER            PIC X(12) VALUE " 傳檔日期： ".
        // 012900    03 RPT-RDAY1-YYY     PIC ZZ9.
        // 013000    03 FILLER            PIC X(01) VALUE "/".
        // 013100    03 RPT-RDAY1-MM      PIC 99.
        // 013200    03 FILLER            PIC X(01) VALUE "/".
        // 013300    03 RPT-RDAY1-DD      PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.padX("" + rptRday1YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1DD, 2));
        fileAContents.add(sb.toString());

        // 048500      WRITE REPORTFL-REC   FROM RPT-4A-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 013500 01 RPT-4A-REC.
        // 013600    03 FILLER            PIC X(08) VALUE SPACES.
        // 013700    03 FILLER            PIC X(12) VALUE " 交易日期： ".
        // 013800    03 RPT-4A-YYY        PIC ZZ9.
        // 013900    03 FILLER            PIC X(01) VALUE "/".
        // 014000    03 RPT-4A-MM         PIC 99.
        // 014100    03 FILLER            PIC X(01) VALUE "/".
        // 014200    03 RPT-4A-DD         PIC 99.
        // 014300    03 FILLER            PIC X(04) VALUE SPACES.
        // 014400    03 RPT-4A-TXT        PIC X(12) VALUE SPACES.
        // 014500    03 FILLER            PIC X(03) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.padX("" + rpt4AYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt4AMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt4ADD, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt4aTxt, 12));
        sb.append(formatUtil.padX("", 3));
        fileAContents.add(sb.toString());

        // 048600      WRITE REPORTFL-REC   FROM RPT-5-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 014700 01 RPT-5-REC.
        // 014800    03 FILLER            PIC X(08) VALUE SPACES.
        // 014900    03 FILLER            PIC X(14) VALUE " 總帳單筆數： ".
        // 015000    03 FILLER            PIC X(01) VALUE SPACES.
        // 015100    03 RPT-CNT           PIC X(07).
        // 015200    03 FILLER            PIC X(13) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總帳單筆數： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptCnt, 7));
        sb.append(formatUtil.padX("", 13));
        fileAContents.add(sb.toString());

        // 048700      WRITE REPORTFL-REC   FROM RPT-5A-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 015300 01 RPT-5A-REC.
        // 015400    03 FILLER            PIC X(08) VALUE SPACES.
        // 015500    03 FILLER            PIC X(14) VALUE " 總交易筆數： ".
        // 015600    03 FILLER            PIC X(01) VALUE SPACES.
        // 015700    03 RPT-CNTA          PIC X(07).
        // 015800    03 FILLER            PIC X(13) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總交易筆數： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptCnta, 7));
        sb.append(formatUtil.padX("", 13));
        fileAContents.add(sb.toString());

        // 048800      WRITE REPORTFL-REC   FROM RPT-6-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 015900 01 RPT-6-REC.
        // 016000    03 FILLER            PIC X(08) VALUE SPACES.
        // 016100    03 FILLER            PIC X(14) VALUE " 總　金　額： ".
        // 016200    03 FILLER            PIC X(01) VALUE SPACES.
        // 016300    03 RPT-TXAMT         PIC X(16).
        // 016400    03 FILLER            PIC X(01) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總　金　額： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxamt, 16));
        sb.append(formatUtil.padX("", 1));
        fileAContents.add(sb.toString());

        // 048900      WRITE REPORTFL-REC   FROM RPT-7-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 016500 01 RPT-7-REC.
        // 016600    03 FILLER            PIC X(08) VALUE SPACES.
        // 016700    03 FILLER            PIC X(14) VALUE " 手　續　費： ".
        // 016800    03 FILLER            PIC X(01) VALUE SPACES.
        // 016900    03 RPT-TXFEE         PIC X(13).
        // 017000    03 FILLER            PIC X(06) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 手　續　費： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxFee, 13));
        sb.append(formatUtil.padX("", 6));
        fileAContents.add(sb.toString());

        // 049000      WRITE REPORTFL-REC   FROM RPT-8-REC AFTER 2.
        fileAContents.add("");
        fileAContents.add("");

        // 017100 01 RPT-8-REC.
        // 017200    03 FILLER            PIC X(08) VALUE SPACES.
        // 017300    03 FILLER            PIC X(14) VALUE " 淨解繳金額： ".
        // 017400    03 FILLER            PIC X(01) VALUE SPACES.
        // 017500    03 RPT-TXAMT2        PIC X(16).
        // 017600    03 FILLER            PIC X(01) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 淨解繳金額： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxAmt2, 16));
        sb.append(formatUtil.padX("", 6));
        fileAContents.add(sb.toString());

        if (firstPage) {
            firstPage = false;
        }
        // 049200 3000-RPC015-A-EXIT.
    }

    private void tailS() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr tailS");
        // 039300 1600-TAIL-S.
        //
        //// 寫REPORTFL2表尾(RPT2-TOT、RPT2-TOTA、RPT2-MEMO-REC)
        //
        // 039400      WRITE REPORTFL2-REC   FROM RPT2-DASH-REC.
        // 026500 01 RPT2-DASH-REC.
        // 026600    03 FILLER            PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 150));
        fileBContents.add(sb.toString());

        // 039500      MOVE  WK-TRADECNT       TO RPT2-TOTCNT.
        int rpt2Totcnt = wkTradecnt;
        // 039600      MOVE  WK-TOTAMT         TO RPT2-TOTAMT.
        BigDecimal rpt2Totamt = wkTotamt;
        // 039700      WRITE REPORTFL2-REC   FROM RPT2-TOT.
        // 024300 01   RPT2-TOT.
        // 024400  03  FILLER                           PIC X(05) VALUE SPACES.
        // 024500  03  FILLER                           PIC X(14) VALUE
        // 024600      " 總交易筆數： ".
        // 024700  03  RPT2-TOTCNT                      PIC ZZZZZZ9.
        // 024800  03  FILLER                           PIC X(17) VALUE SPACES.
        // 024900  03  FILLER                           PIC X(10) VALUE
        // 025000      " 總金額： ".
        // 025100  03  RPT2-TOTAMT                      PIC $$$,$$$,$$$,$$9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總交易筆數： ", 14));
        sb.append(String.format("%7s", rpt2Totcnt));
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.padX(" 總金額： ", 10));
        sb.append(formatUtil.padX("$" + dFormatNum.format(rpt2Totamt), 16));
        fileBContents.add(sb.toString());

        // 039800      MOVE  WK-TOTCNT         TO RPT2-TOTACNT.
        int rpt2Totacnt = wkTotcnt;
        // 039900      WRITE REPORTFL2-REC   FROM RPT2-TOTA.
        // 025200 01   RPT2-TOTA.
        // 025300  03  FILLER                           PIC X(05) VALUE SPACES.
        // 025400  03  FILLER                           PIC X(14) VALUE
        // 025500      " 總帳單筆數： ".
        // 025600  03  RPT2-TOTACNT                     PIC ZZZZZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總帳單筆數： ", 14));
        sb.append(String.format("%7s", rpt2Totacnt));
        fileBContents.add(sb.toString());

        // 040000      MOVE  " 註：交易日為非營業日時，以總金額乙欄表示 "
        // 040100                              TO WK-MEMO.
        // 040200      MOVE  WK-MEMO(1:41)     TO RPT2-MEMO-TXT.
        // 040300      MOVE  " 該當日全天應解繳之金額。 "
        // 040400                              TO WK-MEMO.
        // 040500      MOVE  WK-MEMO(2:25)     TO RPT2-MEMO-TXT(42:25).
        // 040600      WRITE REPORTFL2-REC   FROM RPT2-MEMO-REC.
        // 025900 01 RPT2-MEMO-REC.
        // 026000    03 FILLER            PIC X(05) VALUE SPACES.
        // 026100    03 RPT2-MEMO-TXT     PIC X(95) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 註：交易日為非營業日時，以總金額乙欄表示該當日全天應解繳之金額。 ", 95));
        fileBContents.add(sb.toString());

        // 040800 1600-TAIL-E.
    }

    private void rpc015BRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr rpc015BRtn");
        // 034200 1000-RPC015-B-RTN.
        // 034300

        //// REPORTFL2換頁控制
        // 034400      IF  WK-PCTL = 46
        if (wkPctl == 46) {
            // 034500          PERFORM 1500-HEAD-S THRU 1500-HEAD-E.
            headS(PAGE_SEPARATOR);
        }
        //// 寫REPORTFL2明細(RPT2-DETAIL)
        // 034700      MOVE WK-TRDP                 TO RPT2-SENDATE-P.
        String rpt2SendateP = "" + wkTrdp;
        // 034800      MOVE REC-PMPZ-CUNO           TO RPT2-NO-P.
        String rpt2NoP = pmpzCuno;
        // 034900      MOVE REC-PMPZ-TPDY           TO WK-ELEDATE.
        String wkEledate = pmpzTpdy;
        // 035000      MOVE WK-ELEDATE(3:6)         TO RPT2-ELEDATE-P.
        String rpt2EledateP = wkEledate.substring(1, 7);
        // 035100      MOVE REC-PMPZ-AMT            TO RPT2-AMT-P.
        BigDecimal rpt2AmtP = pmpzAmt;
        // 035200      MOVE REC-PMPZ-DATE           TO RPT2-SITDATE-P.
        String rpt2SitdateP = pmpzDate;

        // 035400      WRITE REPORTFL2-REC          FROM RPT2-DETAIL.
        // 022500 01 RPT2-DETAIL.
        // 022600  03  FILLER                           PIC X(05) VALUE SPACES.
        // 022700  03  RPT2-SENDATE-P                   PIC 999/99/99.
        // 022800  03  FILLER                           PIC X(06) VALUE SPACES.
        // 022900  03  RPT2-NO-P                        PIC X(11).
        // 023000  03  FILLER                           PIC X(03) VALUE SPACES.
        // 023100  03  FILLER                           PIC X(08) VALUE SPACES.
        // 023200  03  RPT2-ELEDATE-P                   PIC 99/99/99.
        // 023300  03  FILLER                           PIC X(05) VALUE SPACES.
        // 023400  03  RPT2-AMT-P                       PIC $,$$$,$$$,$$9.
        // 023500  03  FILLER                           PIC X(06) VALUE SPACES.
        // 023600  03  RPT2-SITDATE-P                   PIC 999/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2SendateP, "999/99/99"), 9));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(rpt2NoP, 11));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2EledateP, "99/99/99"), 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("$" + dFormatNum.format(rpt2AmtP), 14));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2SitdateP, "999/99/99"), 9));
        fileBContents.add(sb.toString());

        //// 累計筆數、金額
        // 035600      ADD  1                TO WK-PCTL.
        wkPctl = wkPctl + 1;
        // 035700      ADD  REC-PMPZ-AMT     TO WK-TOTAMT,WK-TEMPTXAMT.
        wkTotamt = wkTotamt.add(pmpzAmt);
        wkTempTxAmt = wkTempTxAmt.add(pmpzAmt);
        // 035800      ADD  1                TO WK-TOTCNT,WK-TEMPCNT.
        wkTotcnt = wkTotcnt + 1;
        wkTempcnt = wkTempcnt + 1;
        // 035900      IF   REC-PMPZ-TRNO       NOT =  WK-TEMP-TRNO
        if (!pmpzTrno.equals(wkTempTrno)) {
            // 036000           ADD  1              TO     WK-TRADECNT.
            wkTradecnt = wkTradecnt + 1;
        }
        // 036100      MOVE REC-PMPZ-TRNO    TO WK-TEMP-TRNO.
        wkTempTrno = pmpzTrno;

        // 036300 1000-RPCO15-B-EXIT.
    }

    private void headS(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr headS");
        // 036600 1500-HEAD-S.

        //// 寫REPORTFL2表頭(RPT2-TIT1~RPT2-TIT3、RPT2-TIT3A、RPT2-TIT4~RPT2-TIT6)

        // 036800* 搬繳匯日期
        // 036900      ADD   1                 TO RPT2-PAGE-P.
        rpt2PageP = rpt2PageP + 1;
        // 037000      MOVE  WK-MVD-YYY        TO RPT2-YYY.
        int rpt2YYY = wkMvdYYY;
        // 037100      MOVE  WK-MVD-MM         TO RPT2-MM.
        int rpt2MM = wkMvdMM;
        // 037200      MOVE  WK-MVD-DD         TO RPT2-DD.
        int rpt2DD = wkMvdDD;
        // 037300      WRITE REPORTFL2-REC   FROM RPT2-TIT1   AFTER PAGE.
        // 017800 01 RPT2-TIT1.
        // 017900  03  FILLER                           PIC X(33) VALUE SPACES.
        // 018000  03  FILLER                           PIC X(46) VALUE
        // 018100      "  臺灣銀行　代收台電電費資料明細單 ".
        // 018200  03  RPT2-TIT1-D                   PIC X(20) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(pageFg);
        sb.append(formatUtil.padX("", 33));
        sb.append(formatUtil.padX("  臺灣銀行　代收台電電費資料明細單 ", 46));
        sb.append(formatUtil.padX("", 20));
        fileBContents.add(sb.toString());

        // 037400      WRITE REPORTFL2-REC   FROM RPT2-SPACES-REC.
        // 026300 01 RPT2-SPACES-REC.
        // 026400    03 FILLER            PIC X(100) VALUE ALL SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 100));
        fileBContents.add(sb.toString());

        // 037500* 解繳、傳檔日
        // 037600      MOVE  WK-TRDP           TO RPT2-BDATE-P.
        int rpt2BdateP = wkTrdp;
        // 037700      MOVE  FD-CLNDR-TBSDY    TO RPT2-TRDATE-P.
        int rpt2TrdateP = clndrTbsdy;
        // 037900      WRITE REPORTFL2-REC   FROM RPT2-TIT2.
        // 018300 01 RPT2-TIT2.
        // 018400    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 018500    03 RPT2-YYY          PIC ZZ9.
        // 018600    03 FILLER            PIC X(01) VALUE "/".
        // 018700    03 RPT2-MM           PIC 99.
        // 018800    03 FILLER            PIC X(01) VALUE "/".
        // 018900    03 RPT2-DD           PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.pad9("" + rpt2YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt2MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rpt2DD, 2));
        fileBContents.add(sb.toString());

        // 038000      WRITE REPORTFL2-REC   FROM RPT2-TIT3.
        // 019000 01 RPT2-TIT3.
        // 019100  03  FILLER                           PIC X(12) VALUE
        // 019200      " 傳檔日期： ".
        // 019300  03  RPT2-BDATE-P                     PIC 9999999.
        // 019400  03  FILLER                           PIC X(58) VALUE SPACES.
        // 019500  03  FILLER                           PIC X(07) VALUE
        // 019600      " 頁數 :".
        // 019700  03  RPT2-PAGE-P                      PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.pad9("" + rpt2BdateP, 7));
        sb.append(formatUtil.padX("", 58));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("" + rpt2PageP, 4));
        fileBContents.add(sb.toString());

        // 038100      WRITE REPORTFL2-REC   FROM RPT2-TIT3A.
        // 019800 01 RPT2-TIT3A.
        // 019900  03 FILLER                            PIC X(12) VALUE
        // 020000     " 交易日期： ".
        // 020100  03 RPT2-TRDATE-P                      PIC 9999999.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.pad9("" + rpt2TrdateP, 7));
        fileBContents.add(sb.toString());

        // 038200      WRITE REPORTFL2-REC   FROM RPT2-TIT4.
        // 020200 01 RPT2-TIT4.
        // 020300  03  FILLER                           PIC X(30) VALUE
        // 020400      " 台電北市： (00)   區營業處 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 台電北市： (00)   區營業處 ", 30));
        fileBContents.add(sb.toString());

        // 038300      WRITE REPORTFL2-REC   FROM RPT2-TIT5.
        // 020500 01 RPT2-TIT5.
        // 020600  03  FILLER                           PIC X(28) VALUE
        // 020700      " 單位代號： PMPZ  （彙總） ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 單位代號： PMPZ  （彙總） ", 28));
        fileBContents.add(sb.toString());

        // 038400      WRITE REPORTFL2-REC   FROM RPT2-SPACES-REC.
        // 026300 01 RPT2-SPACES-REC.
        // 026400    03 FILLER            PIC X(100) VALUE ALL SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 100));
        fileBContents.add(sb.toString());

        // 038500      WRITE REPORTFL2-REC   FROM RPT2-TIT6.
        // 020800 01 RPT2-TIT6.
        // 020900  03  FILLER                           PIC X(04) VALUE SPACES.
        // 021000  03  FILLER                           PIC X(10) VALUE
        // 021100      " 傳檔日期 ".
        // 021200  03  FILLER                           PIC X(05) VALUE SPACES.
        // 021300  03  FILLER                           PIC X(06) VALUE
        // 021400      " 電號 ".
        // 021500  03  FILLER                           PIC X(15) VALUE SPACES.
        // 021600  03  FILLER                           PIC X(12) VALUE
        // 021700      " 台電收費日 ".
        // 021800  03  FILLER                           PIC X(05) VALUE SPACES.
        // 021900  03  FILLER                           PIC X(10) VALUE
        // 022000      " 繳費金額 ".
        // 022100  03  FILLER                           PIC X(05) VALUE SPACES.
        // 022200  03  FILLER                           PIC X(12) VALUE
        // 022300      " 代收電費日 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 傳檔日期 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 電號 ", 6));
        sb.append(formatUtil.padX("", 15));
        sb.append(formatUtil.padX(" 台電收費日 ", 12));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 繳費金額 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 代收電費日 ", 12));
        fileBContents.add(sb.toString());

        // 038600      WRITE REPORTFL2-REC   FROM RPT2-DASH-REC.
        // 026500 01 RPT2-DASH-REC.
        // 026600    03 FILLER            PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 150));
        fileBContents.add(sb.toString());

        // 038700
        // 038800      MOVE    1               TO WK-PCTL.
        wkPctl = 1;
        // 038900
        // 039000 1500-HEAD-E.
        // 039100      EXIT.
    }

    private void gettrdRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr gettrdRtn");
        // 053500 7200-GETTRD-RTN.
        //
        //// 執行7500-SETDAY-RTN，找到日曆資料=指定日期(WK-SITDATE)
        //
        // 053600      PERFORM 7500-SETDAY-RTN THRU 7500-SETDAY-EXIT.
        setdayRtn();
        //// 設定傳檔日期WK-TRD:指定日期的後一日曆日
        //// FD-CLNDR-TMNDY:本月月底日
        // 053700      MOVE WK-SITDATE       TO WK-CHKTRD.
        wkChktrd = parse.string2Integer(wkSitdate);
        // 053800      ADD  1                TO WK-CHKTRD.
        wkChktrd = wkChktrd + 1;
        // 053900      MOVE WK-CHKTRD        TO WK-TRD.
        wkTrd = wkChktrd;
        wkTrdYYY = wkTrd / 10000;
        wkTrdMM = wkTrd / 100 % 100;
        wkTrdDD = wkTrd % 100;
        // 054000      IF  WK-CHKTRD > FD-CLNDR-TMNDY
        if (wkChktrd > clndrTmndy) {
            // 054100          IF  WK-TRD-MM = 12
            if (wkTrdMM == 12) {
                // 054200              ADD   1       TO WK-TRD-YYY
                wkTrdYYY = wkTrdYYY + 1;
                // 054300              MOVE  1       TO WK-TRD-MM
                wkTrdMM = 1;
                // 054400          ELSE
            } else {
                // 054500              ADD   1       TO WK-TRD-MM
                wkTrdMM = 1;
                // 054600          END-IF
            }
            // 054700      MOVE  1               TO WK-TRD-DD
            wkTrdDD = 1;
            // 054800      END-IF.
        }
        // 054900      MOVE  WK-TRD          TO WK-TRDP,WK-CHKTRD.
        wkTrdp = wkTrd;
        wkChktrd = wkTrd;
        //// 執行7000-GETMVD-RTN，設定解繳日期WK-MVD
        // 055000      PERFORM 7000-GETMVD-RTN  THRU 7000-GETMVD-EXIT.
        getmvdRtn();
        // 055100 7200-GETTRD-EXIT.
    }

    private void getmvdRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr getmvdRtn");
        // 052000 7000-GETMVD-RTN.
        //// 設定解繳日期WK-MVD
        //// FD-CLNDR-NDYCNT:下營業日差
        //// FD-CLNDR-NNDCNT:下下營業日差
        //// FD-CLNDR-NBSDY:下營業日
        //// FD-CLNDR-NNBSDY:下下營業日
        //// FD-CLNDR-N3BSDY:下下下個營業日
        //// WK-MVDP、WK-MVD:解繳日期

        // 052100      MOVE FD-CLNDR-NDYCNT  TO WK-SUMNNDAY.
        wkSumnnday = clndrNdycnt;
        // 052200      ADD  FD-CLNDR-NNDCNT  TO WK-SUMNNDAY.
        wkSumnnday = wkSumnnday + clndrNndcnt;
        // 052300      IF  FD-CLNDR-NDYCNT  > 2
        if (clndrNdycnt > 2) {
            // 052400          MOVE  FD-CLNDR-NBSDY  TO WK-MVDP
            wkMvdp = clndrNbsdy;
            // 052500      ELSE
        } else {
            // 052600          IF  WK-SUMNNDAY  > 2
            if (wkSumnnday > 2) {
                // 052700              MOVE  FD-CLNDR-NNBSDY  TO WK-MVDP
                wkMvdp = clndrNnbsdy;
                // 052800          ELSE
            } else {
                // 052900              MOVE  FD-CLNDR-N3BSDY  TO WK-MVDP.
                wkMvdp = clndrN3bsdy;
            }
            // 053000      MOVE  WK-MVDP    TO WK-MVD.
            wkMvd = wkMvdp;
            wkMvdYYY = wkMvd / 10000;
            wkMvdMM = wkMvdYYY / 100 % 100;
            wkMvdDD = wkMvdYYY % 100;
        }
        // 053200 7000-GETMVD-EXIT.
    }

    private void setdayRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr setdayRtn");
        // 055400 7500-SETDAY-RTN.

        //// KEY值搬1
        // 055500      MOVE 1 TO WK-CLNDR-KEY.
        wkClndrKey = 1;
        // 055600 7500-LOOP-S.

        //// RANDOM READ FD-CLNDR，異常時
        ////  A.DISPLAY錯誤訊息
        ////  B.輸出TASKVALUE為-1
        ////  C.跳至 0000-END-RTN，關檔、結束程式

        // 055700      READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY"
        // 055800           ,WK-CLNDR-STUS
        // 055900           CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO  -1
        // 056000           GO TO  0000-END-RTN.

        //// 若不是指定日期，A.KEY值加1、B.往下一日找

        // 056100      IF FD-CLNDR-TBSDY NOT = WK-SITDATE
        // 056200         ADD 1 TO WK-CLNDR-KEY
        // 056300         GO TO 7500-LOOP-S.
        // 056400 7500-LOOP-E

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), wkSitdate, wkSitdate);
        txBizDates.get(0).isHliday();
        clndrTmndy = txBizDates.get(0).getTmndy();
        clndrNdycnt = txBizDates.get(0).getNdycnt();
        clndrNndcnt = txBizDates.get(0).getNndycnt();
        clndrNbsdy = txBizDates.get(0).getNbsdy();
        clndrNnbsdy = txBizDates.get(0).getNnbsdy();
        clndrN3bsdy = txBizDates.get(0).getN3bsdy();
        clndrTbsdy = txBizDates.get(0).getTbsdy();
        clndrTbsYY = clndrTbsdy / 10000;
        clndrTbsMM = clndrTbsdy / 100 % 100;
        clndrTbsDD = clndrTbsdy % 100;
        //// FD-CLNDR-NDYCNT:下營業日差
        //// FD-CLNDR-NNDCNT:下下營業日差
        //// FD-CLNDR-NBSDY:下營業日
        //// FD-CLNDR-NNBSDY:下下營業日
        //// FD-CLNDR-N3BSDY:下下下個營業日
        // 056600 7500-SETDAY-EXIT.
    }

    private void init(RPTC015PMPZ event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015PMPZLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        //// 接收參數:WK-TASK-DATE    9(07) 指定日期
        // 029200 PROCEDURE      DIVISION USING WK-TASK-DATE.
        wkTaskDate = getrocdate(parse.string2Integer(textMap.get("INDATE"))); // TODO: 待確認BATCH參數名稱
        //// 搬 接收參數-指定日期 給 變數
        // 029900* 指定來源檔
        // 030000   MOVE  WK-TASK-DATE     TO  WK-INDATE.
        wkIndate = wkTaskDate;
        // 讀檔路徑 "DATA/CL/BH/CONN/PMPZ"
        inputFilePath = fileDir + FOLD_INPUT_NAME + PATH_SEPARATOR + FILE_INPUT_NAME;
        fileAContents = new ArrayList<>();
        fileBContents = new ArrayList<>();
        // 產報表路徑A "BD/CL/BH/RPT/C015/A"
        outputFilePathA = fileDir + "CL-BH-RPT-C015-A";
        // 產報表路徑B "BD/CL/BH/RPT/C015/B"
        outputFilePathB = fileDir + "CL-BH-RPT-C015-B";
        textFile.deleteFile(outputFilePathA);
        textFile.deleteFile(outputFilePathB);
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
