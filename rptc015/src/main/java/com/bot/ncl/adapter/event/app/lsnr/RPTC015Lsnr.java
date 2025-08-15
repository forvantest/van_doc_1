/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.RPTC015;
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
@Component("RPTC015Lsnr")
@Scope("prototype")
public class RPTC015Lsnr extends BatchListenerCase<RPTC015> {
    @Autowired private FsapSync fsapSync;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FormatUtil formatUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private static final String FILE_CHARSET = "UTF-8"; // 產檔檔案編碼
    private static final String REPORT_CHARSET = "BIG-5"; // 產報表檔案編碼
    private static final String FOLD_INPUT_NAME = "CONN"; // 讀檔資料夾
    private static final String FILE_INPUT_NAME = "PMPZ"; // 讀檔檔名
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String PAGE_SEPARATOR = "\u000C";
    private StringBuilder sb = new StringBuilder();
    private List<String> file1Contents; // 報表內容1
    private List<String> file2Contents; // 報表內容2
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath1; // 讀檔路徑A
    private String outputFilePath2; // 讀檔路徑B
    private RPTC015 event;
    private String wkTaskDate = "";
    private String wkFdate = "";
    private String wkPutfile = "";
    private String wkCode = "";
    private int wkBdate = 0;
    private int wkSitdate = 0;
    private int wkRpdate = 0;
    private int wkEdate = 0;
    private int wkPctl = 0;
    private int putfnCtl = 0;
    private String putfnBdate = "";
    private String putfnRcptid = "";
    private String putfnUserdata = "";
    private BigDecimal putfnAmt = BigDecimal.ZERO;
    private int putfnSitdate = 0;
    private int wkTradedate = 0;
    private int putfnDate = 0;
    private int wkPutfndate = 0;
    private String putfnEdate = "";
    private int wkBf1530Cntto = 0;
    private int wkAf1530Cntto = 0;
    private int wkBf1530Cnttr = 0;
    private int wkAf1530Cnttr = 0;
    private BigDecimal wkBf1530Amt = BigDecimal.ZERO;
    private BigDecimal wkAf1530Amt = BigDecimal.ZERO;
    private BigDecimal wkBf1530Fee = BigDecimal.ZERO;
    private BigDecimal wkAf1530Fee = BigDecimal.ZERO;
    private BigDecimal wkBf1530Amt2 = BigDecimal.ZERO;
    private BigDecimal wkAf1530Amt2 = BigDecimal.ZERO;
    private int rpt2PageP = 0;
    private String wkCountflg = "";
    private int wkTotcnt = 0;
    private int wkTempCnt = 0;
    private int wkTradecnt = 0;
    private int wkChktrd = 0;
    private String wkTrd = "";
    private String wkTrdp = "";
    private int wkTrdYYY = 0;
    private int wkTrdMM = 0;
    private int wkTrdDD = 0;
    private int rpt2Totcnt = 0;
    private Boolean firstPage = true;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private BigDecimal rpt2Totamt = BigDecimal.ZERO;
    private BigDecimal wkTemptxamt = BigDecimal.ZERO;
    private int wkMvdp = 0;
    private int wkMvd = 0;
    private String wkMvdS = "";
    private String wkMvdYYY = "";
    private String wkMvdMM = "";
    private String wkMvdDD = "";
    private int wkSumnnday = 0;
    private int clndrNdycnt = 0;
    private Boolean clndrHolidy = false;
    private int clndrNndcnt = 0;
    private int clndrNbsdy = 0;
    private int clndrNnbsdy = 0;
    private int clndrN3bsdy = 0;
    private String clndrTbsdy = "";
    private int clndrTmndy = 0;
    private String clndrTbsYY = "";
    private String clndrTbsMM = "";
    private String clndrTbsDD = "";
    private final DecimalFormat dFormatNum = new DecimalFormat("#,##0");

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(RPTC015 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(RPTC015 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr run()");
        init(event);
        // 033200   IF  ATTRIBUTE RESIDENT OF FD-PUTFN IS = VALUE(TRUE)
        if (textFile.exists(inputFilePath)) {
            // 033300       OPEN  INPUT     FD-PUTFN
            // 033400       OPEN  OUTPUT    REPORTFL
            // 033500       OPEN  OUTPUT    REPORTFL2
            // 033600       PERFORM 0500-REPORT-RTN THRU 0500-REPORT-EXIT
            reportRtn();
            // 033700       CLOSE FD-PUTFN.
            // 033800       CLOSE REPORTFL  WITH SAVE
            // 033900       CLOSE REPORTFL2 WITH SAVE.

            try {
                textFile.writeFileContent(outputFilePath1, file1Contents, REPORT_CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            try {
                textFile.writeFileContent(outputFilePath2, file2Contents, REPORT_CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
    }

    private void reportRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr reportRtn");
        // 034600 0500-REPORT-RTN.
        // 034800 0500-LOOP-S.

        //// 循序讀取FD-PUTFN，直到檔尾，跳到0500-LOOP-E
        // 034900      READ  FD-PUTFN  AT  END  GO TO  0500-LOOP-E.
        List<String> lines = textFile.readFileContent(inputFilePath, FILE_CHARSET);
        //// 若PUTFN-CTL = 0首筆，搬資料起迄日給變數
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03  PUTFN-CTL	9(02) 0-2
            // 03  PUTFN-CTL-R	REDEFINES PUTFN-CTL
            //  05 PUTFN-CTL1	9(01)
            //  05 PUTFN-CTL2	9(01)
            putfnCtl = parse.string2Integer(detail.substring(0, 2));
            // 03  PUTFN-CODE	X(06)	代收類別 2-8
            // 03  PUTFN-DATA.	GROUP
            //  05 PUTFN-RCPTID	X(26)	銷帳號碼 8-34
            putfnRcptid = detail.substring(8, 34);
            //  05 PUTFN-DATE	9(08)	代收日 34-42
            if (parse.isNumeric(detail.substring(34, 42))) {
                putfnDate = parse.string2Integer(detail.substring(34, 42));
            }
            //  05 PUTFN-TIME	9(06)	代收時間 42-48
            //  05 PUTFN-CLLBR	9(03)	代收行 48-51
            //  05 PUTFN-LMTDATE	9(08)	繳費期限 51-59
            //  05 PUTFN-OLDAMT	9(10) 59-69
            //  05 PUTFN-USERDATA	X(40)	備註資料 69-109
            putfnUserdata = detail.substring(69, 109);
            //  05 PUTFN-SITDATE	9(08)	原代收日 109-117
            if (parse.isNumeric(detail.substring(109, 117))) {
                putfnSitdate = parse.string2Integer(detail.substring(109, 117));
            }
            //  05 PUTFN-TXTYPE	X(01)	帳務別 117-118
            //  05 PUTFN-AMT	9(12)	繳費金額 118-130
            putfnAmt = parse.string2BigDecimal(detail.substring(118, 130));
            //  05 PUTFN-FILLER	X(30) 130-160
            // 03  PUTFN-DATA-R
            //	REDEFINES PUTFN-DATA
            //  05 PUTFN-BDATE	9(08)	挑檔起日 8-16
            putfnBdate = detail.substring(8, 16);
            //  05 PUTFN-EDATE	9(08)	挑檔迄日 16-24
            putfnEdate = detail.substring(16, 24);
            //  05 PUTFN-TOTCNT	9(06) 24-30
            //  05 PUTFN-TOTAMT	9(13) 30-43
            //  05 FILLER	X(117) 43-160
            // 03  PUTFN-DATA-NODATA	REDEFINES PUTFN-DATA
            //  05 PUTFN-NODATA	X(26) 8-34
            //  05 PUTFN-FILLER1	X(126)	34-160

            // 035000      IF  PUTFN-CTL =  0
            if (putfnCtl == 0) {
                // 035100          MOVE PUTFN-BDATE        TO  WK-BDATE
                wkBdate = parse.string2Integer(putfnBdate);
                // 035200          MOVE PUTFN-EDATE        TO  WK-EDATE.
                wkEdate = parse.string2Integer(putfnEdate);
            }
            //// 若PUTFN-CTL = 11明細資料
            ////  執行1000-RPC015-2-RTN，寫REPORTFL2明細

            // 035300      IF  PUTFN-CTL = 11
            if (putfnCtl == 11) {
                // 035400          PERFORM 1000-RPC015-2-RTN  THRU 1000-RPCO15-2-EXIT.
                rpc0152Rtn();
            }
            //// LOOP讀下一筆FD-PUTFN

            // 035600      GO TO 0500-LOOP-S.
            // 035700 0500-LOOP-E.
            if (cnt == lines.size()) {
                //// 若WK-PCTL <> 0
                ////  A.執行1600-TAIL-S，寫REPORTFL2表尾
                ////  B.執行3000-RPC015-1-RTN，寫REPORTFL代收台電公司費款日結單
                ////  C.WK-PUTFNDATE=資料迄日之下一日曆日
                ////  D.執行7700-CHKNODATA-RTN，判斷NODATA日期，寫REPORTFL2(" 無交易 ")、REPORTFL(" 無交易 ")
                //// 若WK-PCTL = 0
                ////  A.執行7800-NODATA-RP-RTN，寫REPORTFL2(" 無交易 ")、REPORTFL(" 無交易 ")

                // 035800      IF  WK-PCTL NOT = 0
                if (wkPctl != 0) {
                    // 035900          PERFORM 1600-TAIL-S         THRU 1600-TAIL-E
                    tailS();
                    // 036000          PERFORM 3000-RPC015-1-RTN   THRU 3000-RPC015-1-EXIT
                    rpc0151Rtn();
                    // 036100          MOVE WK-EDATE               TO   WK-PUTFNDATE
                    wkPutfndate = wkEdate;
                    // 036200          ADD  1                      TO   WK-PUTFNDATE
                    wkPutfndate = wkPutfndate + 1;
                    // 036300          PERFORM 7700-CHKNODATA-RTN  THRU 7700-CHKNODATA-EXIT
                    chknodataRtn();
                    // 036400      ELSE
                } else {
                    // 036500          PERFORM 7800-NODATA-RP-RTN  THRU 7800-NODATA-RP-EXIT
                    nodataRpRtn();
                    // 036600      END-IF.
                }
            }
        }
        // 036800 0500-REPORT-EXIT.
    }

    private void rpc0151Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr rpc0151Rtn");
        // 051600 3000-RPC015-1-RTN.
        // 051700

        //// 寫REPORTFL代收台電公司費款日結單(RPT-1-REC~RPT-8-REC)
        // 051800* 搬繳匯日期
        // 051900      MOVE WK-MVD-YYY       TO RPT-YYY.
        String rptYYY = wkMvdYYY;
        // 052000      MOVE WK-MVD-MM        TO RPT-MM.
        String rptMM = wkMvdMM;
        // 052100      MOVE WK-MVD-DD        TO RPT-DD.
        String rptDD = wkMvdDD;
        // 052200* 搬傳送日期
        // 052300      MOVE WK-TRD-YYY       TO RPT-RDAY1-YYY.
        int rptRday1YYY = wkTrdYYY;
        // 052400      MOVE WK-TRD-MM        TO RPT-RDAY1-MM.
        int rptRday1MM = wkTrdMM;
        // 052500      MOVE WK-TRD-DD        TO RPT-RDAY1-DD.
        int rptRday1DD = wkTrdDD;
        // 052600* 搬交易日期
        // 052700      MOVE FD-CLNDR-TBSYY   TO RPT-4A-YYY.
        String rpt4AYYY = clndrTbsYY;
        // 052800      MOVE FD-CLNDR-TBSMM   TO RPT-4A-MM.
        String rpt4AMM = clndrTbsMM;
        // 052900      MOVE FD-CLNDR-TBSDD   TO RPT-4A-DD.
        String rpt4ADD = clndrTbsDD;
        // 053000* 筆數靠左對齊
        // 053100      MOVE  WK-TEMPCNT  TO WK-CNT.
        // 053200      MOVE 0 TO COUNTER WK-A WK-B.
        // 053300      INSPECT WK-CNT TALLYING COUNTER FOR LEADING SPACE.
        // 053400      COMPUTE WK-A = COUNTER + 1.
        // 053500      COMPUTE WK-B = 7 - COUNTER.
        // 053600      MOVE WK-CNT(WK-A:WK-B) TO RPT-CNT.
        int rptCnt = wkTempCnt;
        // 053700* 交易筆數靠左對齊
        // 053800      MOVE  WK-TRADECNT TO WK-CNT.
        // 053900      MOVE 0 TO COUNTER WK-A WK-B.
        // 054000      INSPECT WK-CNT TALLYING COUNTER FOR LEADING SPACE.
        // 054100      COMPUTE WK-A = COUNTER + 1.
        // 054200      COMPUTE WK-B = 7 - COUNTER.
        // 054300      MOVE WK-CNT(WK-A:WK-B) TO RPT-CNTA.
        int rptCntA = wkTradecnt;
        // 054400* 總金額靠左對齊
        // 054500      MOVE  WK-TEMPTXAMT  TO WK-TXAMT.
        // 054600      MOVE 0 TO COUNTER WK-A WK-B.
        // 054700      INSPECT WK-TXAMT TALLYING COUNTER FOR LEADING SPACE.
        // 054800      COMPUTE WK-A = COUNTER + 1.
        // 054900      COMPUTE WK-B = 16 - COUNTER.
        // 055000      MOVE WK-TXAMT(WK-A:WK-B) TO RPT-TXAMT.
        BigDecimal rptTxamt = wkTemptxamt;
        // 055100* 手續費靠左對齊
        // 055200      COMPUTE  WK-TEMPTXFEE =  WK-TRADECNT * 3 .
        // 055300      MOVE     WK-TEMPTXFEE TO WK-TXFEE.
        // 055400      MOVE 0 TO COUNTER WK-A WK-B.
        // 055500      INSPECT WK-TXFEE TALLYING COUNTER FOR LEADING SPACE.
        // 055600      COMPUTE WK-A = COUNTER + 1.
        // 055700      COMPUTE WK-B = 13 - COUNTER.
        // 055800      MOVE WK-TXFEE(WK-A:WK-B) TO RPT-TXFEE.
        int rptTxfee = wkTradecnt * 3;
        // 055900* 淨金額靠左對齊
        // 056000      COMPUTE  WK-TXAMT2 =  WK-TEMPTXAMT - WK-TEMPTXFEE.
        // 056100      MOVE 0 TO COUNTER WK-A WK-B.
        // 056200      INSPECT WK-TXAMT2 TALLYING COUNTER FOR LEADING SPACE.
        // 056300      COMPUTE WK-A = COUNTER + 1.
        // 056400      COMPUTE WK-B = 16 - COUNTER.
        // 056500      MOVE WK-TXAMT2(WK-A:WK-B) TO RPT-TXAMT2.
        BigDecimal rptTxamt2 = wkTemptxamt.subtract(new BigDecimal(wkTradecnt * 3));

        // 056700      MOVE SPACES               TO  RPT-4A-TXT  .
        String rpt4ATxt = "";
        // 056800      MOVE WK-BF1530-CNTTO      TO  RPT-5-BFTO  .
        int rpt5Bfto = wkBf1530Cntto;
        // 056900      MOVE WK-AF1530-CNTTO      TO  RPT-5-AFTO  .
        int rpt5Afto = wkAf1530Cntto;
        // 057000      MOVE WK-BF1530-CNTTR      TO  RPT-5A-BFTR .
        int rpt5ABftr = wkBf1530Cnttr;
        // 057100      MOVE WK-AF1530-CNTTR      TO  RPT-5A-AFRT .
        int rpt5AAfrt = wkAf1530Cnttr;
        // 057200      MOVE WK-BF1530-AMT        TO  RPT-6-BFAMT .
        BigDecimal rpt6Bfamt = wkBf1530Amt;
        // 057300      MOVE WK-AF1530-AMT        TO  RPT-6-AFAMT .
        BigDecimal rpt6Afamt = wkAf1530Amt;
        // 057400      MOVE WK-BF1530-FEE        TO  RPT-7-BFFEE .
        BigDecimal rpt7Bffee = wkBf1530Fee;
        // 057500      MOVE WK-AF1530-FEE        TO  RPT-7-AFFEE .
        BigDecimal rpt7Affee = wkAf1530Fee;
        // 057600      MOVE WK-BF1530-AMT2       TO  RPT-8-BFAMT2.
        BigDecimal rpt8Bfamt2 = wkBf1530Amt2;
        // 057700      MOVE WK-AF1530-AMT2       TO  RPT-8-AFAMT2.
        BigDecimal rpt8Afamt2 = wkAf1530Amt2;

        // 057900      WRITE REPORTFL-REC   FROM RPT-1-REC AFTER PAGE.
        // 008100 01 RPT-1-REC.
        // 008200    03 FILLER            PIC X(20) VALUE SPACES.
        // 008300    03 RPT-HEAD1-TITLE   PIC X(40) VALUE
        // 008400        " 臺灣銀行　代收台電公司費款日結單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.padX(" 臺灣銀行　代收台電公司費款日結單 ", 40));
        file1Contents.add(sb.toString());

        // 058000      WRITE REPORTFL-REC   FROM RPT-2-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 008500 01 RPT-2-REC.
        // 008600    03 FILLER            PIC X(08) VALUE SPACES.
        // 008700    03 FILLER            PIC X(14) VALUE " 單位代號： ".
        // 008800    03 FILLER            PIC X(10) VALUE "PMPZ     ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 單位代號： ", 14));
        sb.append(formatUtil.padX("PMPZ     ", 10));
        file1Contents.add(sb.toString());

        // 058100      WRITE REPORTFL-REC   FROM RPT-3-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 008900 01 RPT-3-REC.
        // 009000    03 FILLER            PIC X(08) VALUE SPACES.
        // 009100    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 009200    03 RPT-YYY           PIC ZZ9.
        // 009300    03 FILLER            PIC X(01) VALUE "/".
        // 009400    03 RPT-MM            PIC 99.
        // 009500    03 FILLER            PIC X(01) VALUE "/".
        // 009600    03 RPT-DD            PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.padX(rptYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptDD, 2));
        file1Contents.add(sb.toString());

        // 058200      WRITE REPORTFL-REC   FROM RPT-4-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 009700 01 RPT-4-REC.
        // 009800    03 FILLER            PIC X(08) VALUE SPACES.
        // 009900    03 FILLER            PIC X(12) VALUE " 傳檔日期： ".
        // 010000    03 RPT-RDAY1-YYY     PIC ZZ9.
        // 010100    03 FILLER            PIC X(01) VALUE "/".
        // 010200    03 RPT-RDAY1-MM      PIC 99.
        // 010300    03 FILLER            PIC X(01) VALUE "/".
        // 010400    03 RPT-RDAY1-DD      PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.padX("" + rptRday1YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9("" + rptRday1DD, 2));
        file1Contents.add(sb.toString());

        // 058300      WRITE REPORTFL-REC   FROM RPT-4A-REC AFTER 2.
        file1Contents.add("");
        // 010600 01 RPT-4A-REC.
        // 010700    03 FILLER            PIC X(08) VALUE SPACES.
        // 010800    03 FILLER            PIC X(12) VALUE " 交易日期： ".
        // 010900    03 RPT-4A-YYY        PIC ZZ9.
        // 011000    03 FILLER            PIC X(01) VALUE "/".
        // 011100    03 RPT-4A-MM         PIC 99.
        // 011200    03 FILLER            PIC X(01) VALUE "/".
        // 011300    03 RPT-4A-DD         PIC 99.
        // 011400    03 FILLER            PIC X(04) VALUE SPACES.
        // 011500    03 RPT-4A-TXT        PIC X(12) VALUE SPACES.
        // 011600    03 FILLER            PIC X(03) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.padX(rpt4AYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt4AMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt4ADD, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt4ATxt, 12));
        sb.append(formatUtil.padX("", 3));
        file1Contents.add(sb.toString());

        // 058400      WRITE REPORTFL-REC   FROM RPT-DASHA-REC.
        // 025900 01 RPT-DASHA-REC.
        // 026000    03 FILLER            PIC X(47)  VALUE SPACES.
        // 026100    03 FILLER            PIC X(04)  VALUE " 前 ".
        // 026200    03 FILLER            PIC X(03)  VALUE ALL "-".
        // 026300    03 FILLER            PIC X(05)  VALUE "15:30".
        // 026400    03 FILLER            PIC X(03)  VALUE ALL "-".
        // 026500    03 FILLER            PIC X(04)  VALUE " 後 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 47));
        sb.append(formatUtil.padX(" 前 ", 4));
        sb.append(formatUtil.padX("---", 3));
        sb.append(formatUtil.padX("15:30", 5));
        sb.append(formatUtil.padX("---", 3));
        sb.append(formatUtil.padX(" 後 ", 4));
        file1Contents.add(sb.toString());

        // 058500      WRITE REPORTFL-REC   FROM RPT-5-REC
        // 011800 01 RPT-5-REC.
        // 011900    03 FILLER            PIC X(08) VALUE SPACES.
        // 012000    03 FILLER            PIC X(14) VALUE " 總帳單筆數： ".
        // 012100    03 FILLER            PIC X(01) VALUE SPACES.
        // 012200    03 RPT-CNT           PIC X(07).
        // 012300    03 FILLER            PIC X(13) VALUE SPACES.
        // 012400    03 RPT-5-BFTO        PIC ZZZ,ZZ9.
        // 012500    03 FILLER            PIC X(04) VALUE SPACES.
        // 012600    03 FILLER            PIC X(04) VALUE SPACES.
        // 012700    03 RPT-5-AFTO        PIC ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總帳單筆數： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptCnt, 7));
        sb.append(formatUtil.padX("", 13));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt5Bfto), 7));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt5Afto), 7));
        file1Contents.add(sb.toString());

        // 058600      WRITE REPORTFL-REC   FROM RPT-5A-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 012800 01 RPT-5A-REC.
        // 012900    03 FILLER            PIC X(08) VALUE SPACES.
        // 013000    03 FILLER            PIC X(14) VALUE " 總交易筆數： ".
        // 013100    03 FILLER            PIC X(01) VALUE SPACES.
        // 013200    03 RPT-CNTA          PIC X(07).
        // 013300    03 FILLER            PIC X(13) VALUE SPACES.
        // 013400    03 RPT-5A-BFTR       PIC ZZZ,ZZ9.
        // 013500    03 FILLER            PIC X(04) VALUE SPACES.
        // 013600    03 FILLER            PIC X(04) VALUE SPACES.
        // 013700    03 RPT-5A-AFRT       PIC ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總交易筆數： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptCntA, 7));
        sb.append(formatUtil.padX("", 13));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt5ABftr), 7));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt5AAfrt), 7));
        file1Contents.add(sb.toString());

        // 058700      WRITE REPORTFL-REC   FROM RPT-6-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 013800 01 RPT-6-REC.
        // 013900    03 FILLER            PIC X(08) VALUE SPACES.
        // 014000    03 FILLER            PIC X(14) VALUE " 總　金　額： ".
        // 014100    03 FILLER            PIC X(01) VALUE SPACES.
        // 014200    03 RPT-TXAMT         PIC X(16).
        // 014300    03 FILLER            PIC X(01) VALUE SPACES.
        // 014400    03 RPT-6-BFAMT       PIC ZZ,ZZZ,ZZ9.
        // 014500    03 FILLER            PIC X(05) VALUE SPACES.
        // 014600    03 RPT-6-AFAMT       PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總　金　額： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxamt, 16));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt6Bfamt), 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt6Afamt), 10));
        file1Contents.add(sb.toString());

        // 058800      WRITE REPORTFL-REC   FROM RPT-7-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 014700 01 RPT-7-REC.
        // 014800    03 FILLER            PIC X(08) VALUE SPACES.
        // 014900    03 FILLER            PIC X(14) VALUE " 手　續　費： ".
        // 015000    03 FILLER            PIC X(01) VALUE SPACES.
        // 015100    03 RPT-TXFEE         PIC X(13).
        // 015200    03 FILLER            PIC X(06) VALUE SPACES.
        // 015300    03 RPT-7-BFFEE       PIC ZZZZ,ZZ9.
        // 015400    03 FILLER            PIC X(04) VALUE SPACES.
        // 015500    03 FILLER            PIC X(03) VALUE SPACES.
        // 015600    03 RPT-7-AFFEE       PIC ZZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 手　續　費： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxfee, 16));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt7Bffee), 9));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt7Affee), 9));
        file1Contents.add(sb.toString());

        // 058900      WRITE REPORTFL-REC   FROM RPT-8-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 015700 01 RPT-8-REC.
        // 015800    03 FILLER            PIC X(08) VALUE SPACES.
        // 015900    03 FILLER            PIC X(14) VALUE " 淨解繳金額： ".
        // 016000    03 FILLER            PIC X(01) VALUE SPACES.
        // 016100    03 RPT-TXAMT2        PIC X(16).
        // 016200    03 FILLER            PIC X(01) VALUE SPACES.
        // 016300    03 RPT-8-BFAMT2      PIC ZZ,ZZZ,ZZ9.
        // 016400    03 FILLER            PIC X(05) VALUE SPACES.
        // 016500    03 RPT-8-AFAMT2      PIC ZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 淨解繳金額： ", 14));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptTxamt2, 16));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt8Bfamt2), 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padLeft(dFormatNum.format(rpt8Afamt2), 10));
        file1Contents.add(sb.toString());

        // 059100 3000-RPC015-1-EXIT.
    }

    private void tailS() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr tailS");
        // 046900 1600-TAIL-S.

        //// 寫REPORTFL2表尾(RPT2-TOT、RPT2-TOTA、RPT2-MEMO-REC)

        // 047000      IF WK-PCTL > 1  AND FD-CLNDR-HOLIDY = 0
        if (wkPctl > 1 && !clndrHolidy) {
            String rpt2CountTxt = "";
            BigDecimal rpt2CountAmt = BigDecimal.ZERO;
            // 047100         IF WK-COUNTFLG  = "N"
            if ("N".equals(wkCountflg)) {
                // 047200            MOVE   " （後）小計 "   TO RPT2-COUNT-TXT
                rpt2CountTxt = " （後）小計 ";
                // 047300            MOVE   WK-AF1530-AMT    TO RPT2-COUNT-AMT
                rpt2CountAmt = wkAf1530Amt;
                // 047400         END-IF
            }
            // 047500         IF WK-COUNTFLG  = "Y"
            if ("Y".equals(wkCountflg)) {
                // 047600            MOVE   " （前）小計 "   TO RPT2-COUNT-TXT
                rpt2CountTxt = " （前）小計 ";
                // 047700            MOVE   WK-BF1530-AMT    TO RPT2-COUNT-AMT
                rpt2CountAmt = wkBf1530Amt;
                // 047800         END-IF
            }
            // 047900         WRITE  REPORTFL2-REC  FROM RPT-DASHB-REC
            // 026600 01 RPT-DASHB-REC.
            // 026700    03 FILLER            PIC X(42)  VALUE SPACES.
            // 026800    03 FILLER            PIC X(26)  VALUE ALL "-".
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 42));
            sb.append(reportUtil.makeGate("-", 26));
            file2Contents.add(sb.toString());

            // 048000         WRITE  REPORTFL2-REC  FROM RPT2-DETAIL-COUNT
            // 023000 01 RPT2-DETAIL-COUNT.
            // 023100  03  FILLER                           PIC X(41) VALUE SPACES.
            // 023200  03  RPT2-COUNT-TXT                   PIC X(12) VALUE SPACES.
            // 023300  03  RPT2-COUNT-AMT                   PIC $$$,$$$,$$$,$$9.
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 41));
            sb.append(formatUtil.padX(rpt2CountTxt, 12));
            sb.append(formatUtil.left("$" + dFormatNum.format(rpt2CountAmt), 16));
            file2Contents.add(sb.toString());

            // 048100      END-IF.
        }
        // 048200      WRITE REPORTFL2-REC   FROM RPT2-DASH-REC.
        // 025700 01 RPT2-DASH-REC.
        // 025800    03 FILLER            PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 100));
        file2Contents.add(sb.toString());

        // 048300      MOVE  WK-TRADECNT       TO RPT2-TOTCNT.
        rpt2Totcnt = wkTradecnt;

        // 048400      MOVE  WK-TOTAMT         TO RPT2-TOTAMT.
        rpt2Totamt = wkTotamt;
        // 048500      WRITE REPORTFL2-REC   FROM RPT2-TOT.
        // 023500 01   RPT2-TOT.
        // 023600  03  FILLER                           PIC X(05) VALUE SPACES.
        // 023700  03  FILLER                           PIC X(14) VALUE
        // 023800      " 總交易筆數： ".
        // 023900  03  RPT2-TOTCNT                      PIC ZZZZZZ9.
        // 024000  03  FILLER                           PIC X(17) VALUE SPACES.
        // 024100  03  FILLER                           PIC X(10) VALUE
        // 024200      " 總金額： ".
        // 024300  03  RPT2-TOTAMT                      PIC $$$,$$$,$$$,$$9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總交易筆數： ", 14));
        sb.append(formatUtil.left("" + rpt2Totcnt, 7));
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.padX(" 總金額： ", 10));
        sb.append(formatUtil.left("$" + dFormatNum.format(rpt2Totamt), 16));
        file2Contents.add(sb.toString());

        // 048600      MOVE  WK-TOTCNT         TO RPT2-TOTACNT.
        int rpt2Totacnt = wkTotcnt;
        // 048700      WRITE REPORTFL2-REC   FROM RPT2-TOTA.
        // 024400 01   RPT2-TOTA.
        // 024500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 024600  03  FILLER                           PIC X(14) VALUE
        // 024700      " 總帳單筆數： ".
        // 024800  03  RPT2-TOTACNT                     PIC ZZZZZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 總帳單筆數： ", 14));
        sb.append(formatUtil.left("" + rpt2Totacnt, 7));
        file2Contents.add(sb.toString());

        // 048800      MOVE  " 註：交易日為非營業日時，以總金額乙欄表示 "
        // 048900                              TO WK-MEMO.
        // 049000      MOVE  WK-MEMO(1:41)     TO RPT2-MEMO-TXT.
        // 049100      MOVE  " 該當日全天應解繳之金額。 "
        // 049200                              TO WK-MEMO.
        // 049300      MOVE  WK-MEMO(2:25)     TO RPT2-MEMO-TXT(42:25).
        String rpt2MemoTxt = " 註：交易日為非營業日時，以總金額乙欄表示  該當日全天應解繳之金額。 ";
        // 049400      WRITE REPORTFL2-REC   FROM RPT2-MEMO-REC.
        // 025100 01 RPT2-MEMO-REC.
        // 025200    03 FILLER            PIC X(05) VALUE SPACES.
        // 025300    03 RPT2-MEMO-TXT     PIC X(95) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(rpt2MemoTxt, 95));
        file2Contents.add(sb.toString());

        // 049600 1600-TAIL-E.
    }

    private void nodataRpRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr nodataRpRtn");
        // 068800 7800-NODATA-RP-RTN.
        // 068900      MOVE  WK-BDATE           TO WK-SITDATE.
        wkSitdate = wkBdate;
        // 069000 7800-LOOP-S.
        // 069100
        for (int i = 0; wkChktrd < wkEdate; i++) {
            //// A.執行7200-GETTRD-RTN，依指定日期(WK-SITDATE)，設定 傳檔日期WK-TRD & 解繳日期WK-MVD
            //// B.執行1900-NODATA-2-RTN，寫REPORTFL2(" 無交易 ")
            //// C.執行3900-NODATA-1-RTN，寫REPORTFL(" 無交易 ")
            //
            // 069200      PERFORM 7200-GETTRD-RTN  THRU 7200-GETTRD-EXIT.
            gettrdRtn();
            // 069300      PERFORM 1900-NODATA-2-RTN  THRU 1900-NODATA-2-EXIT.
            nodata2Rtn();
            // 069400      PERFORM 3900-NODATA-1-RTN  THRU 3900-NODATA-1-EXIT.
            nodata1Rtn();
            // 069500*WK-CHKTRD 已執行完才判斷，故不含等於情況
            // 069600      IF WK-CHKTRD <= WK-EDATE
            // 069700         MOVE WK-CHKTRD     TO WK-SITDATE
            wkSitdate = wkChktrd;
            // 069800         GO TO  7800-LOOP-S.
        }
        // 070000 7800-LOOP-E.
        // 070100 7800-NODATA-RP-EXIT.
    }

    private void nodata1Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr nodata1Rtn");
        // 059400 3900-NODATA-1-RTN.
        // 059500

        //// 寫REPORTFL代收台電公司費款日結單(" 無交易 ")

        // 059600* 搬繳匯日期
        // 059700*     MOVE WK-TASK-DATE     TO WK-RDAY1.
        String wkRday1 = wkTaskDate;
        // 059800      MOVE WK-MVD-YYY       TO RPT-YYY.
        String rptYYY = wkMvdYYY;
        // 059900      MOVE WK-MVD-MM        TO RPT-MM.
        String rptMM = wkMvdMM;
        // 060000      MOVE WK-MVD-DD        TO RPT-DD.
        String rptDD = wkMvdDD;
        // 060100* 搬傳送日期
        // 060200      MOVE WK-TRD-YYY       TO RPT-RDAY1-YYY.
        String rptRday1YYY = "" + wkTrdYYY;
        // 060300      MOVE WK-TRD-MM        TO RPT-RDAY1-MM.
        String rptRday1MM = "" + wkTrdMM;
        // 060400      MOVE WK-TRD-DD        TO RPT-RDAY1-DD.
        String rptRday1DD = "" + wkTrdDD;
        // 060500* 搬交易日期
        // 060600      MOVE FD-CLNDR-TBSYY   TO RPT-4A-YYY.
        String rpt4AYYY = clndrTbsYY;
        // 060700      MOVE FD-CLNDR-TBSMM   TO RPT-4A-MM.
        String rpt4AMM = clndrTbsMM;
        // 060800      MOVE FD-CLNDR-TBSDD   TO RPT-4A-DD.
        String rpt4ADD = clndrTbsDD;
        // 060900      MOVE " 無　交　易 "   TO RPT-4A-TXT.
        String rpt4ATxt = " 無　交　易 ";

        // 061100      WRITE REPORTFL-REC   FROM RPT-1-REC AFTER PAGE.
        // 008100 01 RPT-1-REC.
        // 008200    03 FILLER            PIC X(20) VALUE SPACES.
        // 008300    03 RPT-HEAD1-TITLE   PIC X(40) VALUE
        // 008400        " 臺灣銀行　代收台電公司費款日結單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.padX(" 臺灣銀行　代收台電公司費款日結單 ", 40));
        file1Contents.add(sb.toString());

        // 061200      WRITE REPORTFL-REC   FROM RPT-2-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 008500 01 RPT-2-REC.
        // 008600    03 FILLER            PIC X(08) VALUE SPACES.
        // 008700    03 FILLER            PIC X(14) VALUE " 單位代號： ".
        // 008800    03 FILLER            PIC X(10) VALUE "PMPZ     ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 單位代號： ", 14));
        sb.append(formatUtil.padX("PMPZ     ", 10));
        file1Contents.add(sb.toString());

        // 061300      WRITE REPORTFL-REC   FROM RPT-3-REC AFTER 2.
        file1Contents.add("");
        file1Contents.add("");
        // 008900 01 RPT-3-REC.
        // 009000    03 FILLER            PIC X(08) VALUE SPACES.
        // 009100    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 009200    03 RPT-YYY           PIC ZZ9.
        // 009300    03 FILLER            PIC X(01) VALUE "/".
        // 009400    03 RPT-MM            PIC 99.
        // 009500    03 FILLER            PIC X(01) VALUE "/".
        // 009600    03 RPT-DD            PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.padX(rptYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptDD, 2));
        file1Contents.add(sb.toString());

        // 061400      WRITE REPORTFL-REC   FROM RPT-4-REC AFTER 2.
        sb = new StringBuilder();
        file1Contents.add("");
        file1Contents.add("");
        // 009700 01 RPT-4-REC.
        // 009800    03 FILLER            PIC X(08) VALUE SPACES.
        // 009900    03 FILLER            PIC X(12) VALUE " 傳檔日期： ".
        // 010000    03 RPT-RDAY1-YYY     PIC ZZ9.
        // 010100    03 FILLER            PIC X(01) VALUE "/".
        // 010200    03 RPT-RDAY1-MM      PIC 99.
        // 010300    03 FILLER            PIC X(01) VALUE "/".
        // 010400    03 RPT-RDAY1-DD      PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.padX(rptRday1YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptRday1MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rptRday1DD, 2));
        file1Contents.add(sb.toString());

        // 061500      WRITE REPORTFL-REC   FROM RPT-4A-REC AFTER 2.
        sb = new StringBuilder();
        file1Contents.add("");
        file1Contents.add("");
        // 010600 01 RPT-4A-REC.
        // 010700    03 FILLER            PIC X(08) VALUE SPACES.
        // 010800    03 FILLER            PIC X(12) VALUE " 交易日期： ".
        // 010900    03 RPT-4A-YYY        PIC ZZ9.
        // 011000    03 FILLER            PIC X(01) VALUE "/".
        // 011100    03 RPT-4A-MM         PIC 99.
        // 011200    03 FILLER            PIC X(01) VALUE "/".
        // 011300    03 RPT-4A-DD         PIC 99.
        // 011400    03 FILLER            PIC X(04) VALUE SPACES.
        // 011500    03 RPT-4A-TXT        PIC X(12) VALUE SPACES.
        // 011600    03 FILLER            PIC X(03) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.padX(rpt4AYYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt4AMM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt4ADD, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt4ATxt, 12));
        sb.append(formatUtil.padX("", 3));
        file1Contents.add(sb.toString());

        // 061700 3900-NODATA-1-EXIT.
    }

    private void nodata2Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr nodata2Rtn");
        // 049900 1900-NODATA-2-RTN.
        // 050000

        //// 執行1500-HEAD-S，寫REPORTFL2表頭
        //// 寫REPORTFL2明細(" 無交易 ")
        //// 執行1600-TAIL-S，寫REPORTFL2表尾

        // 050100      MOVE  0                 TO RPT2-PAGE-P.
        rpt2PageP = 0;
        // 050200      PERFORM 1500-HEAD-S   THRU 1500-HEAD-E.
        headS(PAGE_SEPARATOR);
        // 050300      MOVE  SPACE             TO RPT2-DETAIL.
        // 050400      MOVE  FD-CLNDR-TBSDY    TO RPT2-SITDATE-P.
        String rpt2SitdateP = clndrTbsdy;
        // 050500      MOVE  " 無交易 "        TO RPT2-NO-P.
        String rpt2NoP = " 無交易 ";
        // 050600      WRITE REPORTFL2-REC   FROM RPT2-DETAIL.
        // 021400 01 RPT2-DETAIL.
        // 021500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 021600  03  RPT2-SENDATE-P                   PIC 999/99/99.
        // 021700  03  FILLER                           PIC X(04) VALUE SPACES.
        // 021800  03  RPT2-NO-P                        PIC X(11).
        // 021900  03  FILLER                           PIC X(01) VALUE SPACES.
        // 022000  03  FILLER                           PIC X(01) VALUE "(".
        // 022100  03  RPT2-NO-C                        PIC 9(02).
        // 022200  03  FILLER                           PIC X(01) VALUE ")".
        // 022300  03  FILLER                           PIC X(08) VALUE SPACES.
        // 022400  03  RPT2-ELEDATE-P                   PIC 99/99/99.
        // 022500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 022600  03  RPT2-AMT-P                       PIC $,$$$,$$$,$$9.
        // 022700  03  FILLER                           PIC X(06) VALUE SPACES.
        // 022800  03  RPT2-SITDATE-P                   PIC 999/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("", 9));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt2NoP, 11));
        sb.append(formatUtil.padX("(", 1));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(")", 1));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2SitdateP, "999/99/99"), 9));
        file2Contents.add(sb.toString());

        // 050700      MOVE  0                 TO WK-TRADECNT.
        wkTradecnt = 0;
        // 050800      MOVE  0                 TO WK-TOTCNT.
        wkTotcnt = 0;
        // 050900      MOVE  0                 TO WK-TOTAMT.
        wkTotamt = new BigDecimal(0);
        // 051000      PERFORM 1600-TAIL-S   THRU 1600-TAIL-E.
        tailS();
        // 051100      MOVE  0                 TO RPT2-PAGE-P.
        rpt2PageP = 0;
        // 051200
        // 051300 1900-NODATA-2-EXIT.
    }

    private void chknodataRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr chknodataRtn");
        // 066900 7700-CHKNODATA-RTN.
        // 067000      IF WK-RPDATE = 0
        if (wkRpdate == 0) {
            // 067100         MOVE WK-BDATE         TO WK-RPDATE,WK-SITDATE
            wkRpdate = wkBdate;
            wkSitdate = wkBdate;
            // 067200      ELSE
        } else {
            // 067300         MOVE WK-CHKTRD        TO WK-RPDATE,WK-SITDATE
            wkRpdate = wkChktrd;
            wkSitdate = wkChktrd;
            // 067400      END-IF.
        }
        // 067600 7700-LOOP-S.
        //
        //// 若
        ////  A.執行7200-GETTRD-RTN，依指定日期(WK-SITDATE)，設定 傳檔日期WK-TRD & 解繳日期WK-MVD
        ////  B.執行1900-NODATA-2-RTN，寫REPORTFL2(" 無交易 ")
        ////  C.執行3900-NODATA-1-RTN，寫REPORTFL(" 無交易 ")

        // 067700      IF WK-RPDATE <  WK-PUTFNDATE
        for (int i = 0; wkRpdate < wkPutfndate; i++) {
            // 067800         PERFORM 7200-GETTRD-RTN  THRU 7200-GETTRD-EXIT
            gettrdRtn();
            // 067900         PERFORM 1900-NODATA-2-RTN  THRU 1900-NODATA-2-EXIT
            nodata2Rtn();
            // 068000         PERFORM 3900-NODATA-1-RTN  THRU 3900-NODATA-1-EXIT
            nodata1Rtn();
            // 068100         MOVE WK-CHKTRD     TO WK-SITDATE,WK-RPDATE
            wkSitdate = wkChktrd;
            wkRpdate = wkChktrd;
            // 068200         GO TO 7700-LOOP-S.
            // 068300
        }
        // 068400 7700-LOOP-E.
        // 068500 7700-CHKNODATA-EXIT.
    }

    private void rpc0152Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr rpc0152Rtn");
        // 037100 1000-RPC015-2-RTN.

        //// 若 非首筆 且 PUTFN-SITDATE原代收日 不同時
        ////  A.執行1600-TAIL-S，寫REPORTFL2表尾
        ////  B.執行3000-RPC015-1-RTN，寫REPORTFL代收台電公司費款日結單
        ////  C.清變數值

        // 037300      IF  WK-PCTL NOT = 0 AND PUTFN-SITDATE NOT = WK-TRADEDATE
        if (wkPctl != 0 && putfnSitdate != wkTradedate) {
            // 037400          PERFORM 1600-TAIL-S THRU 1600-TAIL-E
            tailS();
            // 037500          PERFORM 3000-RPC015-1-RTN  THRU 3000-RPC015-1-EXIT
            rpc0151Rtn();
            // 037600          MOVE  0           TO WK-BF1530-CNTTO
            wkBf1530Cntto = 0;
            // 037700          MOVE  0           TO WK-AF1530-CNTTO
            wkAf1530Cntto = 0;
            // 037800          MOVE  0           TO WK-BF1530-CNTTR
            wkBf1530Cnttr = 0;
            // 037900          MOVE  0           TO WK-AF1530-CNTTR
            wkAf1530Cnttr = 0;
            // 038000          MOVE  0           TO WK-BF1530-AMT
            wkBf1530Amt = new BigDecimal(0);
            // 038100          MOVE  0           TO WK-AF1530-AMT
            wkAf1530Amt = new BigDecimal(0);
            // 038200          MOVE  0           TO WK-BF1530-FEE
            wkBf1530Fee = new BigDecimal(0);
            // 038300          MOVE  0           TO WK-AF1530-FEE
            wkAf1530Fee = new BigDecimal(0);
            // 038400          MOVE  0           TO WK-BF1530-AMT2
            wkBf1530Amt2 = new BigDecimal(0);
            // 038500          MOVE  0           TO WK-AF1530-AMT2
            wkAf1530Amt2 = new BigDecimal(0);
            // 038600          MOVE  0           TO RPT2-PAGE-P
            rpt2PageP = 0;
            // 038700          MOVE  "Y"         TO WK-COUNTFLG
            wkCountflg = "Y";
            // 038800          MOVE  0           TO WK-TOTCNT,WK-TEMPCNT,WK-TRADECNT
            wkTotcnt = 0;
            wkTempCnt = 0;
            wkTradecnt = 0;
            // 038900          MOVE  0           TO WK-TOTAMT,WK-TEMPTXAMT.
            wkTotamt = new BigDecimal(0);
            wkTemptxamt = new BigDecimal(0);
        }
        //// 若 PUTFN-SITDATE原代收日 不同時
        ////  A.WK-PUTFNDATE=PUTFN-SITDATE原代收日
        ////  B.執行7700-CHKNODATA-RTN，判斷NODATA日期，寫REPORTFL2(" 無交易 ")、REPORTFL(" 無交易 ")
        ////  C.執行7200-GETTRD-RTN，依指定日期(WK-SITDATE)，設定 傳檔日期WK-TRD & 解繳日期WK-MVD
        ////  D.執行1500-HEAD-S，寫REPORTFL2表頭

        // 039200      IF  PUTFN-SITDATE NOT = WK-TRADEDATE
        if (putfnSitdate != wkTradedate) {
            // 039300          MOVE PUTFN-SITDATE       TO WK-PUTFNDATE
            wkPutfndate = putfnSitdate;
            // 039400          PERFORM 7700-CHKNODATA-RTN  THRU 7700-CHKNODATA-EXIT
            chknodataRtn();
            // 039500          PERFORM 7200-GETTRD-RTN  THRU 7200-GETTRD-EXIT
            gettrdRtn();
            // 039600          PERFORM 1500-HEAD-S THRU 1500-HEAD-E.
            headS(PAGE_SEPARATOR);
        }
        //// REPORTFL2換頁控制

        // 039800      IF  WK-PCTL = 46
        if (wkPctl == 46) {
            // 039900          PERFORM 1500-HEAD-S THRU 1500-HEAD-E.
            headS(PAGE_SEPARATOR);
        }
        //// 搬相關資料給RPT2-DETAIL

        // 040100      MOVE WK-TRDP                 TO RPT2-SENDATE-P.
        String rpt2SendateP = wkTrdp;
        // 040200      MOVE PUTFN-RCPTID(1:11)      TO RPT2-NO-P.
        String rpt2NoP = putfnRcptid;
        // 040300      MOVE PUTFN-USERDATA(28:1)    TO RPT2-NO-C.
        int rpt2NoC = parse.string2Integer(putfnUserdata.substring(28, 29));
        // 040400      MOVE PUTFN-USERDATA(21:7)    TO WK-ELEDATE.
        String wkEledate = putfnUserdata.substring(21, 28);
        // 040500      MOVE WK-ELEDATE(3:6)         TO RPT2-ELEDATE-P.
        String rpt2EledateP = wkEledate;
        // 040600      MOVE PUTFN-AMT               TO RPT2-AMT-P.
        BigDecimal rpt2AmtP = putfnAmt;
        // 040700      MOVE PUTFN-SITDATE           TO RPT2-SITDATE-P.
        int rpt2SitdateP = putfnSitdate;

        // 040900      IF   PUTFN-DATE NOT =  PUTFN-SITDATE
        if (putfnDate != putfnSitdate) {
            // 041000           ADD  RPT2-NO-C             TO WK-AF1530-CNTTO
            wkAf1530Cntto = wkAf1530Cntto + rpt2NoC;
            // 041100           ADD  1                     TO WK-AF1530-CNTTR
            wkAf1530Cnttr = wkAf1530Cnttr + 1;
            // 041200           ADD  PUTFN-AMT             TO WK-AF1530-AMT
            wkAf1530Amt = wkAf1530Amt.add(putfnAmt);
            // 041300           ADD  3                     TO WK-AF1530-FEE
            wkAf1530Fee = wkAf1530Fee.add(new BigDecimal(3));
            // 041400           SUBTRACT WK-AF1530-FEE   FROM WK-AF1530-AMT
            // 041500                                  GIVING WK-AF1530-AMT2
            wkAf1530Amt2 = wkAf1530Amt.subtract(wkAf1530Fee);
            // 041600           IF WK-COUNTFLG = "Y" AND WK-BF1530-CNTTO > 0
            if ("Y".equals(wkCountflg) && wkBf1530Cntto > 0) {
                // 041700              MOVE   "N"              TO WK-COUNTFLG
                wkCountflg = "N";
                // 041800              MOVE   " （前）小計 "   TO RPT2-COUNT-TXT
                String rpt2CountTxt = " （前）小計 ";
                // 041900              MOVE   WK-BF1530-AMT    TO RPT2-COUNT-AMT
                BigDecimal rpt2CountAmt = wkBf1530Amt;
                // 042000              WRITE  REPORTFL2-REC  FROM RPT-DASHB-REC
                // 026600 01 RPT-DASHB-REC.
                // 026700    03 FILLER            PIC X(42)  VALUE SPACES.
                // 026800    03 FILLER            PIC X(26)  VALUE ALL "-".
                sb = new StringBuilder();
                sb.append(formatUtil.padX("", 42));
                sb.append(reportUtil.makeGate("-", 26));
                file2Contents.add(sb.toString());

                // 042100              WRITE  REPORTFL2-REC  FROM RPT2-DETAIL-COUNT
                // 023000 01 RPT2-DETAIL-COUNT.
                // 023100  03  FILLER                           PIC X(41) VALUE SPACES.
                // 023200  03  RPT2-COUNT-TXT                   PIC X(12) VALUE SPACES.
                // 023300  03  RPT2-COUNT-AMT                   PIC $$$,$$$,$$$,$$9.
                sb = new StringBuilder();
                sb.append(formatUtil.padX("", 41));
                sb.append(formatUtil.padX(rpt2CountTxt, 12));
                sb.append(formatUtil.left("$" + dFormatNum.format(rpt2CountAmt), 16));
                file2Contents.add(sb.toString());

                // 042200              ADD 2                   TO WK-PCTL
                wkPctl = wkPctl + 2;
                // 042300           END-IF
            }
            // 042400      ELSE
        } else {
            // 042500           ADD  RPT2-NO-C             TO WK-BF1530-CNTTO
            wkBf1530Cntto = wkBf1530Cntto + rpt2NoC;
            // 042600           ADD  1                     TO WK-BF1530-CNTTR
            wkBf1530Cnttr = wkBf1530Cnttr + 1;
            // 042700           ADD  PUTFN-AMT             TO WK-BF1530-AMT
            wkBf1530Amt = wkBf1530Amt.add(putfnAmt);
            // 042800           ADD  3                     TO WK-BF1530-FEE
            wkBf1530Fee = wkBf1530Fee.add(new BigDecimal(3));
            // 042900           SUBTRACT WK-BF1530-FEE   FROM WK-BF1530-AMT
            wkBf1530Amt2 = wkBf1530Amt.subtract(wkBf1530Fee);
            // 043000                                  GIVING WK-BF1530-AMT2.
        }
        //// 寫REPORTFL2明細(RPT2-DETAIL)

        // 043100      WRITE REPORTFL2-REC          FROM RPT2-DETAIL.
        // 021400 01 RPT2-DETAIL.
        // 021500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 021600  03  RPT2-SENDATE-P                   PIC 999/99/99.
        // 021700  03  FILLER                           PIC X(04) VALUE SPACES.
        // 021800  03  RPT2-NO-P                        PIC X(11).
        // 021900  03  FILLER                           PIC X(01) VALUE SPACES.
        // 022000  03  FILLER                           PIC X(01) VALUE "(".
        // 022100  03  RPT2-NO-C                        PIC 9(02).
        // 022200  03  FILLER                           PIC X(01) VALUE ")".
        // 022300  03  FILLER                           PIC X(08) VALUE SPACES.
        // 022400  03  RPT2-ELEDATE-P                   PIC 99/99/99.
        // 022500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 022600  03  RPT2-AMT-P                       PIC $,$$$,$$$,$$9.
        // 022700  03  FILLER                           PIC X(06) VALUE SPACES.
        // 022800  03  RPT2-SITDATE-P                   PIC 999/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2SendateP, "999/99/99"), 9));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(rpt2NoP, 11));
        sb.append(formatUtil.padX("(", 1));
        sb.append(formatUtil.pad9("" + rpt2NoC, 2));
        sb.append(formatUtil.padX(")", 1));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(reportUtil.customFormat(rpt2EledateP, "99/99/99"), 8));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.left("$" + dFormatNum.format(rpt2AmtP), 14));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(reportUtil.customFormat("" + rpt2SitdateP, "999/99/99"), 9));
        file2Contents.add(sb.toString());

        //// 累計筆數、金額

        // 043300      ADD  1                TO WK-PCTL.
        wkPctl = wkPctl + 1;
        // 043400      ADD  PUTFN-AMT        TO WK-TOTAMT,WK-TEMPTXAMT.
        wkTotamt = wkTotamt.add(putfnAmt);
        wkTemptxamt = wkTemptxamt.add(putfnAmt);
        // 043500      ADD  RPT2-NO-C        TO WK-TOTCNT,WK-TEMPCNT.
        wkTotcnt = wkTotcnt + rpt2NoC;
        wkTempCnt = wkTempCnt + rpt2NoC;
        // 043600      ADD  1                TO WK-TRADECNT.
        wkTradecnt = wkTradecnt + 1;

        //// 保留PUTFN-SITDATE原代收日到WK-TRADEDATE

        // 043700      MOVE PUTFN-SITDATE    TO WK-TRADEDATE.
        wkTradedate = putfnSitdate;

        // 043900 1000-RPCO15-2-EXIT.
    }

    private void setdayRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr gettrdRtn");
        // 065400 7500-SETDAY-RTN.

        // KEY值搬1

        // 065500      MOVE 1 TO WK-CLNDR-KEY.
        // 065600 7500-LOOP-S.
        //// RANDOM READ FD-CLNDR，異常時
        ////  A.DISPLAY錯誤訊息
        ////  B.輸出TASKVALUE為-1
        ////  C.跳至 0000-END-RTN，關檔、結束程式
        // 065700      READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY"
        // 065800           ,WK-CLNDR-STUS
        // 065900           CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO  -1
        // 066000           GO TO  0000-END-RTN.
        //// 若不是指定日期，A.KEY值加1、B.往下一日找
        // 066100      IF FD-CLNDR-TBSDY NOT = WK-SITDATE
        // 066200         ADD 1 TO WK-CLNDR-KEY
        // 066300         GO TO 7500-LOOP-S.
        // 066400 7500-LOOP-E.
        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), "" + wkSitdate, "" + wkSitdate);
        clndrHolidy = txBizDates.get(0).isHliday();
        //// FD-CLNDR-NDYCNT:下營業日差
        //// FD-CLNDR-NNDCNT:下下營業日差
        //// FD-CLNDR-NBSDY:下營業日
        //// FD-CLNDR-NNBSDY:下下營業日
        //// FD-CLNDR-N3BSDY:下下下個營業日
        //// WK-MVDP、WK-MVD:解繳日期
        clndrNdycnt = txBizDates.get(0).getNdycnt();
        clndrNndcnt = txBizDates.get(0).getNndycnt();
        clndrNbsdy = txBizDates.get(0).getNbsdy();
        clndrNnbsdy = txBizDates.get(0).getNnbsdy();
        clndrN3bsdy = txBizDates.get(0).getN3bsdy();
        clndrTbsdy = getrocdate(txBizDates.get(0).getTbsdy());
        clndrTbsYY = clndrTbsdy.substring(0, 3);
        clndrTbsMM = clndrTbsdy.substring(3, 5);
        clndrTbsDD = clndrTbsdy.substring(5, 7);
        clndrTmndy = txBizDates.get(0).getTmndy();

        // 066600 7500-SETDAY-EXIT.
    }

    private void gettrdRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr gettrdRtn");
        // 063500 7200-GETTRD-RTN.

        //// 執行7500-SETDAY-RTN，找到日曆資料=指定日期(WK-SITDATE)

        // 063600      PERFORM 7500-SETDAY-RTN THRU 7500-SETDAY-EXIT.
        setdayRtn();
        //// 設定傳檔日期WK-TRD:指定日期的後一日曆日
        //// FD-CLNDR-TMNDY:本月月底日

        // 063700      MOVE WK-SITDATE       TO WK-CHKTRD.
        wkChktrd = wkSitdate;
        // 063800      ADD  1                TO WK-CHKTRD.
        wkChktrd = wkChktrd + 1;
        // 063900      MOVE WK-CHKTRD        TO WK-TRD.
        wkTrd = getrocdate(wkChktrd);
        wkTrdYYY = parse.string2Integer(wkTrd.substring(0, 3));
        wkTrdMM = parse.string2Integer(wkTrd.substring(3, 5));
        wkTrdDD = parse.string2Integer(wkTrd.substring(5, 7));
        // 064000      IF  WK-CHKTRD > FD-CLNDR-TMNDY
        if (wkChktrd > clndrTmndy) {
            // 064100          IF  WK-TRD-MM = 12
            if (wkTrdMM == 12) {
                // 064200              ADD   1       TO WK-TRD-YYY
                wkTrdYYY = wkTrdYYY + 1;
                // 064300              MOVE  1       TO WK-TRD-MM
                wkTrdMM = 1;
                // 064400          ELSE
            } else {
                // 064500              ADD   1       TO WK-TRD-MM
                wkTrdMM = wkTrdMM + 1;
                // 064600          END-IF
            }
            // 064700      MOVE  1               TO WK-TRD-DD
            wkTrdDD = 1;
            wkTrd = "" + wkTrdYYY + wkTrdMM + wkTrdDD;
            // 064800      END-IF.
        }
        // 064900      MOVE  WK-TRD          TO WK-TRDP,WK-CHKTRD.
        wkTrdp = wkTrd;
        wkChktrd = parse.string2Integer(wkTrd);

        //// 執行7000-GETMVD-RTN，設定解繳日期WK-MVD
        // 065000      PERFORM 7000-GETMVD-RTN  THRU 7000-GETMVD-EXIT.
        getmvdRtn();
        // 065100 7200-GETTRD-EXIT.
    }

    private void getmvdRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr getmvdRtn");
        // 062000 7000-GETMVD-RTN.

        //// 設定解繳日期WK-MVD

        //// FD-CLNDR-NDYCNT:下營業日差
        //// FD-CLNDR-NNDCNT:下下營業日差
        //// FD-CLNDR-NBSDY:下營業日
        //// FD-CLNDR-NNBSDY:下下營業日
        //// FD-CLNDR-N3BSDY:下下下個營業日
        //// WK-MVDP、WK-MVD:解繳日期

        // 062100      MOVE FD-CLNDR-NDYCNT  TO WK-SUMNNDAY.
        wkSumnnday = clndrNdycnt;
        // 062200      ADD  FD-CLNDR-NNDCNT  TO WK-SUMNNDAY.
        wkSumnnday = wkSumnnday + clndrNndcnt;
        // 062300      IF  FD-CLNDR-NDYCNT  > 2
        if (clndrNdycnt > 2) {
            // 062400          MOVE  FD-CLNDR-NBSDY  TO WK-MVDP
            wkMvdp = clndrNbsdy;
            // 062500      ELSE
        } else {
            // 062600          IF  WK-SUMNNDAY  > 2
            if (wkSumnnday > 2) {
                // 062700              MOVE  FD-CLNDR-NNBSDY  TO WK-MVDP
                wkMvdp = clndrNnbsdy;
                // 062800          ELSE
            } else {
                // 062900              MOVE  FD-CLNDR-N3BSDY  TO WK-MVDP.
                wkMvdp = clndrN3bsdy;
            }
            // 063000      MOVE  WK-MVDP    TO WK-MVD.
            wkMvd = wkMvdp;
            wkMvdS = getrocdate(wkMvd);
            wkMvdYYY = wkMvdS.substring(0, 3);
            wkMvdMM = wkMvdS.substring(3, 5);
            wkMvdDD = wkMvdS.substring(5, 7);
        }
        // 063200 7000-GETMVD-EXIT.
    }

    private void headS(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr headS");
        // 044200 1500-HEAD-S.

        //// 寫REPORTFL2表頭(RPT2-TIT1~RPT2-TIT3、RPT2-TIT3A、RPT2-TIT4~RPT2-TIT6)

        // 044400* 搬繳匯日期
        // 044500      ADD   1                 TO RPT2-PAGE-P.
        rpt2PageP = rpt2PageP + 1;
        // 044600      MOVE  WK-MVD-YYY        TO RPT2-YYY.
        String rpt2YYY = wkMvdYYY;
        // 044700      MOVE  WK-MVD-MM         TO RPT2-MM.
        String rpt2MM = wkMvdMM;
        // 044800      MOVE  WK-MVD-DD         TO RPT2-DD.
        String rpt2DD = wkMvdDD;
        // 044900      WRITE REPORTFL2-REC   FROM RPT2-TIT1   AFTER PAGE.
        // 016700 01 RPT2-TIT1.
        // 016800  03  FILLER                           PIC X(33) VALUE SPACES.
        // 016900  03  FILLER                           PIC X(46) VALUE
        // 017000      "  臺灣銀行　代收台電電費資料明細單 ".
        // 017100  03  RPT2-TIT1-D                   PIC X(20) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(pageFg);
        sb.append(formatUtil.padX("", 33));
        sb.append(formatUtil.padX("  臺灣銀行　代收台電電費資料明細單 ", 46));
        file2Contents.add(sb.toString());

        // 045000      WRITE REPORTFL2-REC   FROM RPT2-SPACES-REC.
        // 025500 01 RPT2-SPACES-REC.
        // 025600    03 FILLER            PIC X(100) VALUE ALL SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 100));
        file2Contents.add(sb.toString());
        // 045100* 起迄日
        // 045200      MOVE  WK-TRDP           TO RPT2-BDATE-P.
        String rpt2BdateP = wkTrdp;
        // 045300      MOVE  FD-CLNDR-TBSDY    TO RPT2-TRDATE-P.
        String rpt2TrdateP = clndrTbsdy;
        // 045500      WRITE REPORTFL2-REC   FROM RPT2-TIT2.
        // 017200 01 RPT2-TIT2.
        // 017300    03 FILLER            PIC X(12) VALUE " 解繳日期： ".
        // 017400    03 RPT2-YYY          PIC ZZ9.
        // 017500    03 FILLER            PIC X(01) VALUE "/".
        // 017600    03 RPT2-MM           PIC 99.
        // 017700    03 FILLER            PIC X(01) VALUE "/".
        // 017800    03 RPT2-DD           PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期： ", 12));
        sb.append(formatUtil.pad9(rpt2YYY, 3));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt2MM, 2));
        sb.append(formatUtil.padX("/", 1));
        sb.append(formatUtil.pad9(rpt2DD, 2));
        file2Contents.add(sb.toString());

        // 045600      WRITE REPORTFL2-REC   FROM RPT2-TIT3.
        // 017900 01 RPT2-TIT3.
        // 018000  03  FILLER                           PIC X(12) VALUE
        // 018100      " 傳檔日期： ".
        // 018200  03  RPT2-BDATE-P                     PIC 9999999.
        // 018300  03  FILLER                           PIC X(58) VALUE SPACES.
        // 018400  03  FILLER                           PIC X(07) VALUE
        // 018500      " 頁數 :".
        // 018600  03  RPT2-PAGE-P                      PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 傳檔日期： ", 12));
        sb.append(formatUtil.pad9(rpt2BdateP, 7));
        sb.append(formatUtil.padX("", 58));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("" + rpt2PageP, 4));
        file2Contents.add(sb.toString());

        // 045700      WRITE REPORTFL2-REC   FROM RPT2-TIT3A.
        // 018700 01 RPT2-TIT3A.
        // 018800  03 FILLER                            PIC X(12) VALUE
        // 018900     " 交易日期： ".
        // 019000  03 RPT2-TRDATE-P                      PIC 9999999.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 交易日期： ", 12));
        sb.append(formatUtil.pad9(rpt2TrdateP, 7));
        file2Contents.add(sb.toString());

        // 045800      WRITE REPORTFL2-REC   FROM RPT2-TIT4.
        // 019100 01 RPT2-TIT4.
        // 019200  03  FILLER                           PIC X(30) VALUE
        // 019300      " 台電北市： (00)   區營業處 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 台電北市： (00)   區營業處 ", 30));
        file2Contents.add(sb.toString());

        // 045900      WRITE REPORTFL2-REC   FROM RPT2-TIT5.
        // 019400 01 RPT2-TIT5.
        // 019500  03  FILLER                           PIC X(28) VALUE
        // 019600      " 單位代號： PMPZ  （彙總） ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 單位代號： PMPZ  （彙總） ", 28));
        file2Contents.add(sb.toString());

        // 046000      WRITE REPORTFL2-REC   FROM RPT2-SPACES-REC.
        // 025500 01 RPT2-SPACES-REC.
        // 025600    03 FILLER            PIC X(100) VALUE ALL SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 100));
        file2Contents.add(sb.toString());

        // 046100      WRITE REPORTFL2-REC   FROM RPT2-TIT6.
        // 019700 01 RPT2-TIT6.
        // 019800  03  FILLER                           PIC X(04) VALUE SPACES.
        // 019900  03  FILLER                           PIC X(10) VALUE
        // 020000      " 傳檔日期 ".
        // 020100  03  FILLER                           PIC X(05) VALUE SPACES.
        // 020200  03  FILLER                           PIC X(18) VALUE
        // 020300      " 電號（帳單筆數） ".
        // 020400  03  FILLER                           PIC X(03) VALUE SPACES.
        // 020500  03  FILLER                           PIC X(12) VALUE
        // 020600      " 台電收費日 ".
        // 020700  03  FILLER                           PIC X(05) VALUE SPACES.
        // 020800  03  FILLER                           PIC X(10) VALUE
        // 020900      " 繳費金額 ".
        // 021000  03  FILLER                           PIC X(05) VALUE SPACES.
        // 021100  03  FILLER                           PIC X(12) VALUE
        // 021200      " 代收電費日 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 傳檔日期 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 電號（帳單筆數） ", 18));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 台電收費日 ", 12));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 繳費金額 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 代收電費日 ", 12));
        file2Contents.add(sb.toString());

        // 046200      WRITE REPORTFL2-REC   FROM RPT2-DASH-REC.
        // 025700 01 RPT2-DASH-REC.
        // 025800    03 FILLER            PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 100));
        file2Contents.add(sb.toString());

        // 046400      MOVE    1               TO WK-PCTL.
        wkPctl = 1;

        if (firstPage) {
            firstPage = false;
        }
        // 046600 1500-HEAD-E.
    }

    private void init(RPTC015 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPTC015Lsnr init");
        this.event = event;
        //// 接收參數:WK-TASK-DATE    9(07) 指定日期
        // 031900 PROCEDURE      DIVISION USING WK-TASK-DATE.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        String processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱

        //// 接收參數:WK-TASK-DATE    9(07) 指定日期
        // 029200 PROCEDURE      DIVISION USING WK-TASK-DATE.
        wkTaskDate =
                getrocdate(parse.string2Integer(textMap.get("WK_TASK_DATE"))); // TODO: 待確認BATCH參數名稱
        //// 設定檔名變數
        // 032700   MOVE  WK-TASK-DATE     TO  WK-FDATE.
        // 032800   MOVE  "17X0511023"     TO  WK-PUTFILE.
        wkFdate = wkTaskDate;
        wkPutfile = "17X0511023";
        //// 搬"511023"給代收類別變數，後續沒用，多餘
        // 032900   MOVE  "511023"         TO  WK-CODE.
        wkCode = "511023";
        //// 設定檔名
        ////  WK-PUTDIR="DATA/CL/BH/CONN/"+WK-FDATE+"/17X0511023."
        // 033000   CHANGE  ATTRIBUTE FILENAME OF FD-PUTFN TO WK-PUTDIR.

        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FOLD_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;
        // "BD/CL/BH/RPT/C015/1"
        outputFilePath1 =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CL-BH-RPT-C015-1";
        // "BD/CL/BH/RPT/C015/2"
        outputFilePath2 =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CL-BH-RPT-C015-2";
        textFile.deleteFile(outputFilePath1);
        textFile.deleteFile(outputFilePath2);
        file1Contents = new ArrayList<>();
        file2Contents = new ArrayList<>();
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
