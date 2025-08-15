/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONVCLCMP3;
import com.bot.ncl.dto.entities.ClcmpbyCodeBus;
import com.bot.ncl.dto.entities.CldtlbyCodeRcptidHcodeBus;
import com.bot.ncl.dto.entities.ClmrbyCodeBus;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
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
@Component("CONVCLCMP3Lsnr")
@Scope("prototype")
public class CONVCLCMP3Lsnr extends BatchListenerCase<CONVCLCMP3> {

    @Autowired private ClmrService clmrService;
    @Autowired private ClcmpService clcmpService;
    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET = "UTF-8"; // 檔案編碼 產檔用
    private static final String CHARSET2 = "Big5"; // 檔案編碼 產報表用
    private static final String FILE_INPUT_NAME = "PUTFN"; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME = "CONVF"; // 檔名
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> file04401Contents; //  檔案內容04401
    private List<String> fileC044Contents; //  檔案內容C044
    private String PAGE_SEPARATOR = "\u000C";
    private String processDate = "";
    private CONVCLCMP3 event;
    private String wkFileName = "";
    private String wkYYYYMMDD = "";
    private int wkYYYY = 0;
    private String wkMM = "";
    private String wkDD = "";
    private String wkYYYMMDD = "";
    private String wkPdate = "";
    private String wkPutfile = "";
    private String wkCode = "";
    private int wkNotfound = 0;
    private int wkPbrno = 0;
    private BigDecimal wkSumBalance = BigDecimal.ZERO;
    private int wkTotpage = 0;
    private int wkRptCnt = 0;
    private String wkCldtlCode = "";
    private String wkCldtlRcptid = "";
    private BigDecimal wkBalanceRtp = BigDecimal.ZERO;
    private String wkRcptidRpt = "";
    private String wkCnameRpt = "";
    private BigDecimal wkDtlBalance = BigDecimal.ZERO;
    private String wkDateRpt = "";
    private String wkC044FnMM = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONVCLCMP3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONVCLCMP3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr run()");
        this.event = event;
        // 接收參數:WK-FILENAME(FILENAME)
        // 009700 PROCEDURE   DIVISION  USING  WK-FILENAME.
        // 009701 GENERATED-SECTION SECTION.
        file04401Contents = new ArrayList<>();
        fileC044Contents = new ArrayList<>();

        startRtn();
    }

    private void startRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr startRtn ...");

        // 010000     CHANGE  ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 010100     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 010200             STOP RUN.
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkFileName = textMap.get("DCNAME"); // TODO: 待確認BATCH參數名稱
        // 作業日期(民國年yyyymmdd)
        processDate =
                getrocdate(parse.string2Integer(labelMap.get("PROCESS_DATE"))); // 待中菲APPLE提供正確名稱

        // 設定本營業日、檔名變數值、代收類別變數
        // WK-PUTFILE  PIC X(10) <--WK-RPTDIR'S變數
        // WK-RPTDIR  <-"BD/CL/BH/044/"+WK-PUTFILE+"."
        // 010300     MOVE    FD-BHDATE-TBSDY     TO     WK-YYYYMMDD WK-YYYMMDD.
        // 010400     MOVE "01"                   TO     WK-PUTFILE.
        // 010500     MOVE WK-FILENAME(5:6)       TO     WK-CODE.
        wkYYYYMMDD = processDate;
        wkYYYY = parse.string2Integer(wkYYYYMMDD.substring(0, 3)) + 191100;
        wkMM = wkYYYYMMDD.substring(3, 5);
        wkDD = wkYYYYMMDD.substring(5, 7);
        wkYYYMMDD = processDate;
        wkPutfile = "01";
        wkCode = wkFileName.substring(4, 10);

        // 執行DB-FINDCLMR-RTN，找主辦行
        // 010600     PERFORM DB-FINDCLMR-RTN   THRU     DB-FINDCLMR-EXIT.
        dbFindclmrRtn();
        // 010700* 找不到代收主檔就結束
        // 010800     IF WK-NOTFOUND = 1 GO TO  0000-END-RTN.
        if (wkNotfound == 1) {
            return;
        }

        // 設定檔名
        // 011400     CHANGE ATTRIBUTE TITLE  OF  REPORTFL TO WK-RPTDIR.
        outputFilePath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + wkYYYMMDD
                        + PATH_SEPARATOR
                        + "CL-BH-044"
                        + "-"
                        + wkPutfile;
        // 執行0000-MAIN-RTN 主程式

        // 011500     PERFORM  0000-MAIN-RTN      THRU   0000-MAIN-EXIT.
        mainRtn();

        // 011600 0000-END-RTN.

        // 顯示訊息、關檔、結束程式

        // 011700     DISPLAY "SYM/CL/BH/CONVCLCMP/3  GENERATE C044  OK".
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/CONVCLCMP/3  GENERATE C044  OK");
        // 011800     CLOSE   FD-BHDATE.
        // 011850     CLOSE      BOTSRDB.
        // 011900     STOP RUN.

    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr mainRtn ...");
        // 012100 0000-MAIN-RTN.

        // 開啟檔案
        // 012200     OPEN       OUTPUT            REPORTFL.
        // 012300

        // WK-SUM-BALANCE總餘額變數清0
        // WK-TOTPAGE總頁次預設為1 <-WK-TITLE-LINE2'S變數

        // 012400     MOVE       0                 TO   WK-SUM-BALANCE.
        // 012500     MOVE       1                 TO   WK-TOTPAGE.
        wkSumBalance = new BigDecimal(0);
        wkTotpage = 1;
        // 執行RPT-TITLE-RTN，寫REPORTFL表頭

        // 012600     PERFORM    RPT-TITLE-RTN     THRU RPT-TITLE-EXIT.
        rptTitleRtn(PAGE_SEPARATOR);
        // 將DB-CLCMP-IDX1指標移至開始

        // 012700     SET        DB-CLCMP-IDX1     TO   BEGINNING.
        // 012800 0000-MAIN-LOOP.

        // KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        // 依代收類別 FIND NEXT DB-CLCMP-IDX1收付比對檔，若有誤
        //  若NOTFOUND，GO TO 0000-MAIN-LAST
        //  其他，GO TO 0000-END-RTN，關檔、結束程式

        // 012900     FIND NEXT DB-CLCMP-IDX1 AT DB-CLCMP-CODE   = WK-CODE
        List<ClcmpbyCodeBus> lClcmp = clcmpService.findbyCode(wkCode, 0, Integer.MAX_VALUE);
        if (Objects.isNull(lClcmp)) {
            // 013000      ON EXCEPTION
            // 013100      IF DMSTATUS(NOTFOUND)
            // 013200         GO TO    0000-MAIN-LAST
            // 013300      ELSE
            // 013400         GO TO    0000-END-RTN.
            return;
        }
        // WK-RPT-CNT行數控制加1
        int cnt = 0;
        for (ClcmpbyCodeBus tClcmp : lClcmp) {
            cnt++;
            // 013500     ADD        1                 TO    WK-RPT-CNT.
            wkRptCnt = wkRptCnt + 1;

            // WK-RPT-CNT行數控制>45時
            //  A.換頁
            //  B.執行RPT-TITLE-RTN，寫REPORTFL表頭
            //  C.WK-TOTPAGE總頁次加1 <-WK-TITLE-LINE2'S變數
            //  D.WK-RPT-CNT行數控制清0

            // 013600* 報表換頁控制
            // 013700     IF  WK-RPT-CNT               >    45
            if (wkRptCnt > 45) {
                // 013800         MOVE    SPACE            TO   REPORT-LINE
                // 013900         WRITE   REPORT-LINE      AFTER PAGE
                // 014000         PERFORM RPT-TITLE-RTN    THRU RPT-TITLE-EXIT
                rptTitleRtn(PAGE_SEPARATOR);
                // 014100         ADD     1                TO   WK-TOTPAGE
                // 014200         MOVE    0                TO   WK-RPT-CNT.
                wkTotpage = wkTotpage + 1;
                wkRptCnt = 0;
                // 014300*
            }

            // 搬DB-CLCMP-... 給 WK-DETAIL-LINE
            // 014400     MOVE       DB-CLCMP-RCPTID   TO    WK-RCPTID-RPT.
            // 014500     MOVE       DB-CLCMP-PNAME    TO    WK-CNAME-RPT .
            // 014600     MOVE       DB-CLCMP-AMT      TO    WK-DTL-BALANCE.
            // 014700     MOVE       WK-YYYMMDD         TO    WK-DATE-RPT.
            wkRcptidRpt = tClcmp.getRcptid();
            wkCnameRpt = tClcmp.getPname();
            wkDtlBalance = tClcmp.getAmt();
            wkDateRpt = wkYYYMMDD;

            // 計算當日此虛擬帳號之餘額WK-DTL-BALANCE

            // 014900     PERFORM    FIND-CLDTL-RTN    THRU  FIND-CLDTL-EXIT.
            findCldtlRtn(tClcmp);
            // 015000     MOVE       WK-DTL-BALANCE    TO    WK-BALANCE-RTP.
            // 015100     ADD        WK-DTL-BALANCE    TO    WK-SUM-BALANCE.
            wkBalanceRtp = wkDtlBalance;
            wkSumBalance = wkSumBalance.add(wkDtlBalance);

            // 執行RPT-DTL-RTN，寫REPORTFL報表明細

            // 015200     PERFORM    RPT-DTL-RTN       THRU  RPT-DTL-EXIT.
            rptDtlRtn();
            // LOOP讀下一筆CLCMP，直到NOTFOUND

            // 015300     GO TO 0000-MAIN-LOOP.
            if (cnt == lClcmp.size()) {
                mainLast();
            }
        }
        // 015800 0000-MAIN-EXIT.
    }

    private void rptDtlRtn() {
        // 022700 RPT-DTL-RTN .

        // 寫REPORTFL報表明細 (WK-DETAIL-LINE)

        // 022800     WRITE   REPORT-LINE          FROM    WK-DETAIL-LINE.
        // 008600 01 WK-DETAIL-LINE.
        // 008700    02 WK-RCPTID-RPT         PIC X(16).
        // 008800    02 WK-CNAME-RPT          PIC X(30).
        // 008900    02 WK-DATE-RPT           PIC Z99/99/99.
        // 009000    02 FILLER                PIC X(06) VALUE SPACE.
        // 009100    02 WK-BALANCE-RTP        PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkRcptidRpt, 16));
        sb.append(formatUtil.padX(wkCnameRpt, 30));
        sb.append(reportUtil.customFormat(wkDateRpt, "Z99/99/99"));
        sb.append(formatUtil.padX("", 6));
        sb.append(reportUtil.customFormat("" + wkBalanceRtp, "Z,ZZZ,ZZZ,ZZ9"));
        file04401Contents.add(sb.toString());
        // 022900 RPT-DTL-EXIT.
    }

    private void mainLast() {
        // 015400 0000-MAIN-LAST.
        // 執行RPT-TAIL-RTN，寫REPORTFL表尾
        // 015500     PERFORM    RPT-TAIL-RTN      THRU  RPT-TAIL-EXIT.
        rptTailRtn();
        // 關閉檔案REPORTFL

        // 015600     CLOSE      REPORTFL          WITH  SAVE.
        try {
            textFile.writeFileContent(outputFilePath, file04401Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 若 WK-CODE = "111801"
        //  A.執行FD-C044-RTN，寫FD-C044
        //  B.關閉檔案FD-C044

        // 015620     IF WK-CODE = "111801"
        if ("111801".equals(wkCode)) {
            // 015650        PERFORM FD-C044-RTN       THRU  FD-C044-EXIT
            fdC044Rtn();
            // 015750        CLOSE   FD-C044           WITH  SAVE.
        }
    }

    private void rptTailRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr rptTailRtn ...");
        // 023200 RPT-TAIL-RTN.

        // 寫REPORTFL表尾 (WK-TOTAL-LINE)

        // 023300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 008400 01 WK-TITLE-LINE4.
        // 008500    02 FILLER                PIC X(080) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        file04401Contents.add(sb.toString());

        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023500     WRITE      REPORT-LINE       AFTER   1.
        file04401Contents.add("");

        // 023600     MOVE       WK-SUM-BALANCE    TO      WK-SUMBAL-RPT.
        BigDecimal wkSumBalRpt = wkSumBalance;
        // 023700     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE.
        // 009200 01 WK-TOTAL-LINE.
        // 009300    02 FILLER                PIC X(10) VALUE " 總餘額： ".
        // 009400    02 FILLER                PIC X(05) VALUE SPACE.
        // 009500    02 WK-SUMBAL-RPT         PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總餘額 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + wkSumBalRpt, "Z,ZZZ,ZZZ,ZZ9"));
        file04401Contents.add(sb.toString());

        // 023800 RPT-TAIL-EXIT.
    }

    private void fdC044Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr fdC044Rtn ...");
        // 025220* 配合金融機構存款及其他各種負債準備金調整及查核辦法，１０４年７
        // 025240* 月１日起提供電子票證儲值業務及電子支付業務之全行每日餘額
        // 025300 FD-C044-RTN.

        // 設定檔名月份變數值
        // WK-C044-FN-MM PIC X(02) <-WK-C044-FN'S變數
        // WK-C044-FN <-"DATA/CL/BH/C044/"+WK-C044-FN-MM+"."
        // 每個月，一個檔案

        // 025400     MOVE WK-MM TO WK-C044-FN-MM.
        wkC044FnMM = wkMM;

        // 設定檔名
        // 025500     CHANGE ATTRIBUTE FILENAME OF FD-C044 TO WK-C044-FN.
        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkYYYMMDD
                        + PATH_SEPARATOR
                        + "C044/"
                        + wkC044FnMM;
        // 若FD-C044檔案存在
        //  A.OPEN I-O
        //  B.讀檔，若讀不到，結束本段落
        // 若FD-C044檔案不存在
        //  A.OPEN OUTPUT
        //  B.搬WK-...給FD-C044-...

        // 025600     IF ATTRIBUTE RESIDENT OF FD-C044 = VALUE (TRUE)
        sb = new StringBuilder();
        int startRange = 14 * (parse.string2Integer(wkDD) - 1);
        int endRange = 14 * parse.string2Integer(wkDD);
        if (textFile.exists(inputFilePath)) {
            List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
            for (String detail : lines) {
                sb.append(detail);
            }
            // 025700        OPEN I-O FD-C044
            // 025800        READ FD-C044 AT END GO TO FD-C044-EXIT
            // 025900        END-READ
            textFile.deleteFile(inputFilePath);
        } else {
            // 026000     ELSE
            // 026100        OPEN OUTPUT FD-C044
            // 026200        MOVE WK-YYYY TO FD-C044-YYYY
            // 026300        MOVE WK-MM TO FD-C044-MM
            // 026400     END-IF.
            sb.append(wkYYYY + wkMM + formatUtil.pad9("", 434));
        }
        // 搬WK-SUM-BALANCE總餘額變數到對應日期欄位
        // 026500     MOVE WK-SUM-BALANCE TO FD-C044-TDBAL(WK-DD).

        sb.replace(6 + startRange, 6 + endRange, formatUtil.pad9("" + wkSumBalance, 14));
        fileC044Contents.add(sb.toString());
        // 寫檔FD-C044
        // 026600     WRITE FD-C044-REC.
        // 002825 01  FD-C044-REC.
        // 002830      03  FD-C044-YYYYMM             PIC 9(06).
        // 002835      03  FD-C044-YYYYMM-R REDEFINES FD-C044-YYYYMM.
        // 002840          05  FD-C044-YYYY           PIC 9(04).
        // 002845          05  FD-C044-MM             PIC 9(02).
        // 002850      03  FD-C044-BAL  OCCURS 31 TIMES.
        // 002855          05  FD-C044-TDBAL          PIC 9(14).

        // 修改讀檔資料 刪除後新增檔案
        try {
            textFile.writeFileContent(inputFilePath, fileC044Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 026700 FD-C044-EXIT.
    }

    private void findCldtlRtn(ClcmpbyCodeBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr findCldtlRtn ...");
        // 016100 FIND-CLDTL-RTN.

        // 參考SYM/CL/BH/CONV27/1，應先加當日提款，再減當日存入 ???

        // 016200* 找出大於今日之提取或存入之金額
        // 016300     PERFORM  FIND-DTL1-RTN       THRU  FIND-DTL1-EXIT.
        findDtl1Rtn(tClcmp);
        // 016400     PERFORM  FIND-DTL2-RTN       THRU  FIND-DTL2-EXIT.
        findDtl2Rtn(tClcmp);
        // 016500 FIND-CLDTL-EXIT.
    }

    private void findDtl1Rtn(ClcmpbyCodeBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr findDtl1Rtn ...");
        // 016800 FIND-DTL1-RTN.
        // 016900* 計算出大於本日之存入交易之金額
        // 017000     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 017100     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        wkCldtlCode = tClcmp.getCode();
        wkCldtlRcptid = tClcmp.getRcptid();
        // 將DB-CLDTL-IDX1指標移至開始

        // 017200     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 017300 FIND-DTL1-LOOP.

        // KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        // 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        //  A.若不存在，GO TO FIND-DTL1-EXIT，結束本段落
        //  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        // 017400     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 017500                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkCldtlRcptid, 0, 0, Integer.MAX_VALUE);
        // 017600       ON EXCEPTION
        if (Objects.isNull(lCldtl)) {
            // 017700       IF DMSTATUS(NOTFOUND)
            // 017800          GO TO FIND-DTL1-EXIT
            // 017900       ELSE
            // 018000          GO TO 0000-MAIN-RTN.
            return;
        }
        for (CldtlbyCodeRcptidHcodeBus tCldtl : lCldtl) {
            // 大於當日之交易，從餘額WK-DTL-BALANCE減掉
            // GO TO FIND-DTL1-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND

            // 018100     IF   DB-CLDTL-DATE         >    FD-BHDATE-TBSDY
            if (tCldtl.getEntdy() > parse.string2Integer(processDate)) {
                // 018200       SUBTRACT DB-CLDTL-AMT    FROM WK-DTL-BALANCE
                wkDtlBalance = wkDtlBalance.subtract(tCldtl.getAmt());
                // 018300       GO TO FIND-DTL1-LOOP
            }
            // 018400     ELSE
            // 018500       GO TO FIND-DTL1-LOOP.
        }
        // 018600 FIND-DTL1-EXIT.
    }

    private void findDtl2Rtn(ClcmpbyCodeBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr findDtl2Rtn ...");
        // 018900 FIND-DTL2-RTN.
        // 019000* 計算出大於本日之提取交易之金額

        // WK-CLDTL-CODE=DB-CLCMP-CODE(1:1)+"2"+DB-CLCMP-CODE(3:4)
        // WK-CLDTL-RCPTID="2"+DB-CLCMP-RCPTID(2:15)

        // 019100     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 019200     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        // 019300     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        // 019400     MOVE "2"                  TO   WK-CLDTL-RCPTID-1.
        wkCldtlCode = tClcmp.getCode().substring(0, 1) + "2" + tClcmp.getCode().substring(2, 6);
        wkCldtlRcptid = "2" + tClcmp.getRcptid().substring(1, 16);

        // 將DB-CLDTL-IDX1指標移至開始

        // 019500     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 019600 FIND-DTL2-LOOP.

        // KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        // 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        //  A.若不存在，GO TO FIND-DTL2-EXIT，結束本段落
        //  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???

        // 019700     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 019800                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkCldtlRcptid, 0, 0, Integer.MAX_VALUE);
        // 019900       ON EXCEPTION
        if (Objects.isNull(lCldtl)) {
            // 020000       IF DMSTATUS(NOTFOUND)
            // 020100          GO TO FIND-DTL2-EXIT
            // 020200       ELSE
            // 020300          GO TO 0000-MAIN-RTN.
            return;
        }
        for (CldtlbyCodeRcptidHcodeBus tCldtl : lCldtl) {
            // 大於當日之交易，累計餘額WK-DTL-BALANCE
            // GO TO FIND-DTL2-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND

            // 020400     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
            if (tCldtl.getEntdy() > parse.string2Integer(processDate)) {
                // 020500       ADD   DB-CLDTL-AMT      TO   WK-DTL-BALANCE
                wkDtlBalance = wkDtlBalance.add(tCldtl.getAmt());
                // 020600       GO TO FIND-DTL2-LOOP
            }
            // 020700     ELSE
            // 020800       GO TO FIND-DTL2-LOOP.
        }
        // 020900 FIND-DTL2-EXIT.
    }

    private void rptTitleRtn(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr dbFindclmrRtn ...");
        // 021200 RPT-TITLE-RTN.

        // 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE4)
        // 021300     MOVE       SPACES            TO      REPORT-LINE.
        // 021400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        // 005700 01 WK-TITLE-LINE1.
        // 005800    02 FILLER                       PIC X(20) VALUE SPACE.
        // 005900    02 TITLE-LABEL                  PIC X(38)
        // 006000               VALUE " 全行代理收付系統－虛擬分戶當日餘額表 ".
        // 006100    02 FILLER                       PIC X(10) VALUE SPACE.
        // 006200    02 FILLER                       PIC X(12)
        // 006300                              VALUE "FORM : C044".
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 全行代理收付系統－虛擬分戶當日餘額表 ", 38));
        sb.append(formatUtil.padX(" ", 10));
        sb.append(formatUtil.padX("FORM : C044", 12));
        file04401Contents.add(sb.toString());

        // 021500     MOVE       SPACES            TO      REPORT-LINE.
        // 021600     WRITE      REPORT-LINE       AFTER   1.
        file04401Contents.add("");

        // 021700     MOVE       WK-YYYMMDD        TO      WK-PDATE.
        wkPdate = wkYYYMMDD;
        // 021800     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        // 006400 01 WK-TITLE-LINE2.
        // 006500    02 FILLER                       PIC X(10)
        // 006600                              VALUE " 分行別： ".
        // 006700    02 WK-PBRNO                     PIC 9(03).
        // 006800    02 FILLER                       PIC X(04) VALUE SPACE.
        // 006900    02 FILLER                       PIC X(13)
        // 007000                              VALUE " 印表日期： ".
        // 007100    02 WK-PDATE                     PIC Z99/99/99.
        // 007200    02 FILLER                       PIC X(25) VALUE SPACE.
        // 007300    02 FILLER                       PIC X(10) VALUE " 總頁次： ".
        // 007400    02 WK-TOTPAGE                   PIC 9(06).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.pad9("" + wkPbrno, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.padX(" 印表日期： ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX(" 總頁次： ", 10));
        sb.append(formatUtil.pad9("" + wkTotpage, 6));
        file04401Contents.add(sb.toString());

        // 021900     MOVE       SPACES            TO      REPORT-LINE.
        // 022000     WRITE      REPORT-LINE       AFTER   1.
        file04401Contents.add("");

        // 022100     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        // 007500 01 WK-TITLE-LINE3.
        // 007600    02 FILLER                PIC X(02) VALUE SPACE.
        // 007700    02 FILLER                PIC X(10) VALUE " 分戶帳號 ".
        // 007800    02 FILLER                PIC X(07) VALUE SPACE.
        // 007900    02 FILLER                PIC X(14) VALUE " 分戶帳號名稱 ".
        // 008000    02 FILLER                PIC X(14) VALUE SPACE.
        // 008100    02 FILLER                PIC X(06) VALUE " 日期 ".
        // 008200    02 FILLER                PIC X(14) VALUE SPACE.
        // 008300    02 FILLER                PIC X(06) VALUE " 餘額 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 分戶帳號： ", 10));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 分戶帳號名稱： ", 14));
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX(" 日期 ", 6));
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX(" 餘額 ", 6));
        file04401Contents.add(sb.toString());

        // 022200     MOVE       SPACES            TO      REPORT-LINE.
        // 022300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 008400 01 WK-TITLE-LINE4.
        // 008500    02 FILLER                PIC X(080) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        file04401Contents.add(sb.toString());

        // 022400 RPT-TITLE-EXIT.
    }

    private void dbFindclmrRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP3Lsnr dbFindclmrRtn ...");
        // 024100 DB-FINDCLMR-RTN.

        // WK-NOTFOUND找不到註記預設為0(0.否 1.是)
        // 024200     MOVE 0                        TO   WK-NOTFOUND.
        wkNotfound = 0;
        // 將DB-CLMR-IDX1指標移至開始
        // 024300     SET   DB-CLMR-IDX1 TO BEGINNING.
        // 依代收類別 FIND CLMR，若有誤，WK-NOTFOUND設為1

        // 024400     FIND  DB-CLMR-IDX1 OF DB-CLMR-DDS
        // 024450     AT    DB-CLMR-CODE  = WK-CODE
        List<ClmrbyCodeBus> lClmr = clmrService.findbyCode(wkCode, 0, 1);
        // 024500          ON EXCEPTION
        if (Objects.isNull(lClmr)) {
            // 024600             MOVE 1                TO   WK-NOTFOUND
            wkNotfound = 1;
            // 024700             GO TO DB-FINDCLMR-EXIT.
            return;
        }
        // WK-PBRNO <- WK-TITLE-LINE2'S變數
        // 024800* 找主辦行
        // 024900     MOVE DB-CLMR-PBRNO       TO     WK-PBRNO.
        wkPbrno = lClmr.get(0).getPbrno();

        // 025000 DB-FINDCLMR-EXIT.
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
