/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV273;
import com.bot.ncl.dto.entities.CldmrbyCodeBus;
import com.bot.ncl.dto.entities.CldtlbyCodeRcptidHcodeBus;
import com.bot.ncl.jpa.svc.CldmrService;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("CONV273Lsnr")
@Scope("prototype")
public class CONV273Lsnr extends BatchListenerCase<CONV273> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private CldmrService cldmrService;
    @Autowired private CldtlService cldtlService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV273 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    // Final Define
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_DOT = ".";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private final String STRING_27X1111981 = "27X1111981";
    private final String STRING_27X1111801 = "27X1111801";
    private final String STRING_01 = "01";
    private final String STRING_085 = "085";
    private final String STRING_111981 = "111981";
    private final String STRING_02 = "02";
    private final String STRING_003 = "003";
    private final String STRING_111801 = "111801";
    private final String STRING_044 = "CL-BH-044";
    private final String STRING_C044 = "C044";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private final List<String> fileCONV273Contents = new ArrayList<>();
    private final List<String> fileC044Contents = new ArrayList<>();
    private String wkRptdir;
    private String wkC044Dir;
    private StringBuilder sb = new StringBuilder();

    // Define int (9)
    private int processDate;
    private String tbsdy;
    private int wkPbrno;
    private int wkYYYMMDD;
    private int wkTotpage;
    private int wkYYYY;
    private int wkPdate;
    private int wkDateRpt;
    private int wkRptCnt;
    private int wkMm;
    private int wkDd;
    private int dbCldtlDate;
    private int fdC044Mm;
    private int fdC044YYYY;

    // Define str (X)
    private String wkYYYYMMDD;
    private String wkFilename;
    private String wkCode = "";
    private String wkPutfile;
    private String wkC044FnMm;
    private String wkRcptid;
    private String wkCnameRpt;
    private String wkCldtlRcptid;
    private String dbClcmpRcptid;
    private String wkCldtlCode;
    private String dbClcmpCode;

    // Define BigDecimal
    private BigDecimal wkSumBalance = new BigDecimal(0);
    private BigDecimal wkDtlBalance = new BigDecimal(0);
    private BigDecimal wkBalanceRpt;
    private BigDecimal wkSumbalRpt;
    private BigDecimal dbCldtlAmt;
    private BigDecimal fdC044Tdbal;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV273 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV273 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr run()");
        // 開啟批次日期檔
        // 009900     OPEN    INPUT     FD-BHDATE.
        this.event = event;
        // DISPLAY訊息，包含在系統訊息中
        // 010000     CHANGE  ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.

        // 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 010100     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        // 作業日期(民國年yyyymmdd)
        processDate = parse.string2Integer(labelMap.get("PROCESS_DATE"));
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFilename = textMap.get("WK_FILENAME"); // TODO: 待確認BATCH參數名稱
        // 010200             STOP RUN.

        // 設定本營業日、檔名變數值、代收類別變數
        // WK-PUTFILE  PIC X(10) <--WK-RPTDIR'S變數
        // WK-PBRNO <- WK-TITLE-LINE2'S變數
        // WK-RPTDIR  <-"BD/CL/BH/044/"+WK-PUTFILE+"."

        // 010300     MOVE    FD-BHDATE-TBSDY     TO     WK-YYYMMDD WK-YYYYMMDD.
        wkYYYMMDD = processDate;
        wkYYYYMMDD = formatUtil.pad9("" + processDate, 8);
        wkYYYY = parse.string2Integer(wkYYYYMMDD.substring(0, 4));
        wkMm = parse.string2Integer(wkYYYYMMDD.substring(4, 6));
        wkDd = parse.string2Integer(wkYYYYMMDD.substring(6, 8));
        // 010400*
        // 010500     IF      WK-FILENAME         =      "27X1111981"
        if (STRING_27X1111981.equals(wkFilename)) {
            // 010600        MOVE "01"                TO     WK-PUTFILE
            wkPutfile = STRING_01;
            // 010700        MOVE "085"               TO     WK-PBRNO
            wkPbrno = parse.string2Integer(STRING_085);
            // 010800        MOVE "111981"            TO     WK-CODE
            wkCode = STRING_111981;
            // 010900     ELSE IF WK-FILENAME         =      "27X1111801"
        } else if (STRING_27X1111801.equals(wkFilename)) {
            // 011000        MOVE "02"                TO     WK-PUTFILE
            wkPutfile = STRING_02;
            // 011100        MOVE "003"               TO     WK-PBRNO
            wkPbrno = parse.string2Integer(STRING_003);
            // 011200        MOVE "111801"            TO     WK-CODE.
            wkCode = STRING_111801;
        }

        // 設定檔名
        // 011400     CHANGE ATTRIBUTE TITLE  OF  REPORTFL TO WK-RPTDIR.
        // WK-RPTDIR  <-"BD/CL/BH/044/"+WK-PUTFILE+"."
        wkRptdir =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + STRING_044
                        + "-"
                        + wkPutfile;

        // 執行0000-MAIN-RTN 主程式

        // 011500     PERFORM  0000-MAIN-RTN      THRU   0000-MAIN-EXIT.
        main();
        // 011600 0000-END-RTN.

        // 顯示訊息、關閉批次日期檔、結束程式
        // 011700     DISPLAY "SYM/CL/BH/CONV27/3 GENERATE BD/CL/BH/044  OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV27/3 GENERATE BD/CL/BH/044  OK");
        // 011800     CLOSE   FD-BHDATE.

        batchResponse();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr main");
        // 開啟檔案
        // 012200     OPEN       OUTPUT            REPORTFL.
        // 012300     OPEN       INQUIRY           BOTSRDB.

        // WK-SUM-BALANCE總餘額變數清0
        // WK-TOTPAGE總頁次預設為1 <-WK-TITLE-LINE2'S變數

        // 012400     MOVE       0                 TO   WK-SUM-BALANCE.
        wkSumBalance = new BigDecimal(0);
        // 012500     MOVE       1                 TO   WK-TOTPAGE.
        wkTotpage = 1;

        // 執行RPT-TITLE-RTN，寫REPORTFL表頭
        // 012600     PERFORM    RPT-TITLE-RTN     THRU RPT-TITLE-EXIT.
        rptTitle(PAGE_SEPARATOR);

        // 將DB-CLCMP-IDX1指標移至開始
        // 012700     SET        DB-CLCMP-IDX1     TO   BEGINNING.
        // 012800 0000-MAIN-LOOP.

        // KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        // 依代收類別 FIND NEXT DB-CLCMP-IDX1收付比對檔，若有誤
        //  若NOTFOUND，GO TO 0000-MAIN-LAST
        //  其他，GO TO 0000-END-RTN，關檔、結束程式
        // 012900     FIND NEXT DB-CLCMP-IDX1 AT DB-CLCMP-CODE   = WK-CODE

        List<CldmrbyCodeBus> lCldmr = cldmrService.findbyCode(wkCode, 0, Integer.MAX_VALUE);
        // 013000      ON EXCEPTION
        // 013100      IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldmr)) {
            // 013200         GO TO    0000-MAIN-LAST
            // 013300      ELSE
            // 013400         GO TO    0000-END-RTN.
        } else {
            for (CldmrbyCodeBus tCldmr : lCldmr) {
                // WK-RPT-CNT行數控制加1
                // 013500     ADD        1                 TO    WK-RPT-CNT.
                wkRptCnt++;
                dbClcmpCode = tCldmr.getCode();
                dbClcmpRcptid = tCldmr.getRcptid();
                // WK-RPT-CNT行數控制>45時
                //  A.換頁
                //  B.執行RPT-TITLE-RTN，寫REPORTFL表頭
                //  C.WK-TOTPAGE總頁次加1 <-WK-TITLE-LINE2'S變數
                //  D.WK-RPT-CNT行數控制清0

                // 013600*   報表換頁控制
                // 013700     IF  WK-RPT-CNT               >    45
                if (wkRptCnt > 45) {
                    // 013800         MOVE    SPACE            TO   REPORT-LINE
                    // 013900         WRITE   REPORT-LINE      AFTER PAGE
                    // 014000         PERFORM RPT-TITLE-RTN    THRU RPT-TITLE-EXIT
                    rptTitle(PAGE_SEPARATOR);
                    // 014100         ADD     1                TO   WK-TOTPAGE
                    wkTotpage++;
                    // 014200         MOVE    0                TO   WK-RPT-CNT.
                    wkRptCnt = 0;
                }

                // 搬DB-CLCMP-... 給 WK-DETAIL-LINE
                // 014400     MOVE       DB-CLCMP-RCPTID   TO    WK-RCPTID-RPT.
                wkRcptid = tCldmr.getRcptid();
                // 014500     MOVE       DB-CLCMP-PNAME    TO    WK-CNAME-RPT .
                wkCnameRpt = tCldmr.getPname();
                // 014600     MOVE       DB-CLCMP-AMT      TO    WK-DTL-BALANCE.
                wkDtlBalance = tCldmr.getBal();
                // 014700     MOVE       WK-YYYMMDD        TO    WK-DATE-RPT.
                wkDateRpt = wkYYYMMDD;

                // 計算當日此虛擬帳號之餘額WK-DTL-BALANCE
                // 014900     PERFORM    FIND-CLDTL-RTN    THRU  FIND-CLDTL-EXIT.
                findCldtl();
                // 015000     MOVE       WK-DTL-BALANCE    TO    WK-BALANCE-RTP.
                wkBalanceRpt = wkDtlBalance;
                // 015100     ADD        WK-DTL-BALANCE    TO    WK-SUM-BALANCE.
                wkSumBalance = wkSumBalance.add(wkDtlBalance);

                // 執行RPT-DTL-RTN，寫REPORTFL報表明細
                // 015200     PERFORM    RPT-DTL-RTN       THRU  RPT-DTL-EXIT.
                rptDtl();

                // LOOP讀下一筆CLCMP，直到NOTFOUND
                // 015300     GO TO 0000-MAIN-LOOP.
            }
        }

        // 015400 0000-MAIN-LAST.
        // 執行RPT-TAIL-RTN，寫REPORTFL表尾
        // 015500     PERFORM    RPT-TAIL-RTN      THRU  RPT-TAIL-EXIT.
        rptTail();
        // 關閉檔案REPORTFL
        // 015600     CLOSE      REPORTFL          WITH  SAVE.
        try {
            textFile.writeFileContent(wkRptdir, fileCONV273Contents, CHARSET_BIG5);
            upload(wkRptdir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 若 WK-CODE = "111801"
        //  A.執行FD-C044-RTN，寫FD-C044
        //  B.關閉檔案FD-C044

        // 015620     IF WK-CODE = "111801"
        if (STRING_111801.equals(wkCode)) {
            // 015640        PERFORM FD-C044-RTN       THRU  FD-C044-EXIT
            fdC044();
            // 015660        CLOSE   FD-C044           WITH  SAVE.
            try {
                textFile.writeFileContent(wkC044Dir, fileC044Contents, CHARSET);
                upload(wkC044Dir, "DATA", "C044");
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        // 關閉檔案BOTSRDB
        // 015700     CLOSE      BOTSRDB.

    }

    private void rptTail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr rptTail");
        // 寫REPORTFL表尾 (WK-TOTAL-LINE)
        // 023300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 008400 01 WK-TITLE-LINE4.
        // 008500    02 FILLER                PIC X(080) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        fileCONV273Contents.add(sb.toString());
        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023500     WRITE      REPORT-LINE       AFTER   1.
        fileCONV273Contents.add("");
        // 023600     MOVE       WK-SUM-BALANCE    TO      WK-SUMBAL-RPT.
        wkSumbalRpt = wkSumBalance;
        // 023700     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE.
        // 009200 01 WK-TOTAL-LINE.
        sb = new StringBuilder();
        // 009300    02 FILLER                PIC X(10) VALUE " 總餘額： ".
        sb.append(formatUtil.padX(" 總餘額： ", 10));
        // 009400    02 FILLER                PIC X(05) VALUE SPACE.
        sb.append(formatUtil.padX("", 5));
        // 009500    02 WK-SUMBAL-RPT         PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(wkSumbalRpt), "Z,ZZZ,ZZZ,ZZ9"));
        fileCONV273Contents.add(sb.toString());
    }

    private void rptDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr rptDtl");
        // 寫REPORTFL報表明細 (WK-DETAIL-LINE)
        // 022800     WRITE   REPORT-LINE          FROM    WK-DETAIL-LINE.
        // 008600 01 WK-DETAIL-LINE.
        // 008700    02 WK-RCPTID-RPT         PIC X(16).
        sb = new StringBuilder();
        // 008800    02 WK-CNAME-RPT          PIC X(30).
        sb.append(formatUtil.padX(wkCnameRpt, 30));
        // 008900    02 WK-DATE-RPT           PIC 999/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(wkDateRpt), "999/99/99"));
        // 009000    02 FILLER                PIC X(06) VALUE SPACE.
        sb.append(formatUtil.padX("", 6));
        // 009100    02 WK-BALANCE-RTP        PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(wkBalanceRpt), "Z,ZZZ,ZZZ,ZZ9"));
        fileCONV273Contents.add(sb.toString());
    }

    private void findCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr findCldtl");
        // 參考SYM/CL/BH/CONV27/1，應先加當日提款，再減當日存入 ???
        // 016200* 找出大於今日之提取或存入之金額
        // 016300     PERFORM  FIND-DTL1-RTN       THRU  FIND-DTL1-EXIT.
        findDtl1();
        // 016400     PERFORM  FIND-DTL2-RTN       THRU  FIND-DTL2-EXIT.
        findDtl2();
    }

    private void findDtl1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr findDtl1");
        // 016900* 計算出大於本日之存入交易之金額
        // 017000     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        wkCldtlCode = dbClcmpCode;
        // 017100     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        wkCldtlRcptid = dbClcmpRcptid;

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
        // 017700       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldtl)) {
            // 017800          GO TO FIND-DTL1-EXIT
            // 017900       ELSE
            // 018000          GO TO 0000-MAIN-RTN.
            return;
        }

        // 大於當日之交易，從餘額WK-DTL-BALANCE減掉
        // GO TO FIND-DTL1-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND
        for (CldtlbyCodeRcptidHcodeBus tCldtl : lCldtl) {
            dbCldtlAmt = tCldtl.getAmt();
            // 018100     IF   DB-CLDTL-DATE         >    FD-BHDATE-TBSDY
            if (dbCldtlDate > processDate) {
                // 018200       SUBTRACT DB-CLDTL-AMT    FROM WK-DTL-BALANCE
                wkDtlBalance = wkDtlBalance.subtract(dbCldtlAmt);
                // 018300       GO TO FIND-DTL1-LOOP
                // 018400     ELSE
                // 018500       GO TO FIND-DTL1-LOOP.
                return;
            }
        }
    }

    private void findDtl2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr findDtl2");
        // 019000* 計算出大於本日之提取交易之金額
        // WK-CLDTL-CODE=DB-CLCMP-CODE(1:1)+"2"+DB-CLCMP-CODE(3:4)
        // WK-CLDTL-RCPTID="2"+DB-CLCMP-RCPTID(2:15)
        // 019100     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 019200     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        // 019300     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        // 019400     MOVE "2"                  TO   WK-CLDTL-RCPTID-1.
        wkCldtlCode = dbClcmpCode;
        wkCldtlRcptid = dbClcmpRcptid;
        String wkCldtlCode1 = wkCldtlCode.substring(0, 1);
        String wkCldtlCode3 = wkCldtlCode.substring(2, 6);
        wkCldtlCode = wkCldtlCode1 + STRING_02 + wkCldtlCode3;
        String wkCldtlRcptid2 = wkCldtlRcptid.substring(1, wkCldtlRcptid.length());
        wkCldtlRcptid = STRING_02 + wkCldtlRcptid2;

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
        // 020000       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldtl)) {
            // 020100          GO TO FIND-DTL2-EXIT
            // 020200       ELSE
            // 020300          GO TO 0000-MAIN-RTN.
            return;
        }

        // 大於當日之交易，累計餘額WK-DTL-BALANCE
        // GO TO FIND-DTL2-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND

        // 020400     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
        if (dbCldtlDate > processDate) {
            // 020500       ADD   DB-CLDTL-AMT      TO   WK-DTL-BALANCE
            wkDtlBalance = wkDtlBalance.add(dbCldtlAmt);
            // 020600       GO TO FIND-DTL2-LOOP
            // 020700     ELSE
            // 020800       GO TO FIND-DTL2-LOOP.
        }
    }

    private void fdC044() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr fdC044");
        // 設定檔名月份變數值
        // WK-C044-FN-MM PIC X(02) <-WK-C044-FN'S變數
        // WK-C044-FN <-"DATA/CL/BH/C044/"+WK-C044-FN-MM+"."

        // 每個月，一個檔案
        int startRange = wkDd == 1 ? 6 : 6 + (14 * (wkDd - 1));
        int endRange = 6 + (14 * wkDd);
        // 024400     MOVE WK-MM TO WK-C044-FN-MM.
        wkC044FnMm = String.valueOf(wkMm);

        // 設定檔名
        // 024500     CHANGE ATTRIBUTE FILENAME OF FD-C044 TO WK-C044-FN.
        String c044Dir =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + STRING_C044;
        wkC044Dir = c044Dir + PATH_SEPARATOR + wkC044FnMm;
        textFile.deleteFile(wkC044Dir);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + STRING_C044
                        + File.separator
                        + wkC044FnMm; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, c044Dir);
        if (sourceFile != null) {
            wkC044Dir = getLocalPath(sourceFile);
        }
        // 若FD-C044檔案存在
        //  A.OPEN I-O
        //  B.讀檔，若讀不到，結束本段落
        // 若FD-C044檔案不存在
        //  A.OPEN OUTPUT
        //  B.搬WK-...給FD-C044-...
        sb = new StringBuilder();
        // 024600     IF ATTRIBUTE RESIDENT OF FD-C044 = VALUE (TRUE)
        if (textFile.exists(wkC044Dir)) {
            List<String> l = textFile.readFileContent(wkC044Dir, CHARSET);
            for (String t : l) {
                sb.append(t);
            }
            textFile.deleteFile(wkC044Dir);
            // 024700        OPEN I-O FD-C044
            // 024800        READ FD-C044 AT END GO TO FD-C044-EXIT
            // 024900        END-READ
        } else {
            // 025000     ELSE
            // 025100        OPEN OUTPUT FD-C044
            // 025200        MOVE WK-YYYY TO FD-C044-YYYY
            fdC044YYYY = wkYYYY;
            // 025300        MOVE WK-MM TO FD-C044-MM
            fdC044Mm = wkMm;

            sb.append(formatUtil.pad9("" + wkYYYY, 4));
            sb.append(formatUtil.pad9("" + wkMm, 2));
            sb.append(formatUtil.pad9("", 434));
            // 025400     END-IF.
        }

        // 搬WK-SUM-BALANCE總餘額變數到對應日期欄位
        // 025500     MOVE WK-SUM-BALANCE TO FD-C044-TDBAL(WK-DD).
        fdC044Tdbal = wkSumBalance;
        sb.replace(startRange, endRange, formatUtil.pad9("" + fdC044Tdbal, 14));
        // 寫檔FD-C044
        // 025600     WRITE FD-C044-REC.
        // 002825  01  FD-C044-REC.
        // 002830       03  FD-C044-YYYYMM             PIC 9(06).
        // 002835       03  FD-C044-YYYYMM-R REDEFINES FD-C044-YYYYMM.
        // 002840           05  FD-C044-YYYY           PIC 9(04).
        // 002845           05  FD-C044-MM             PIC 9(02).
        // 002850       03  FD-C044-BAL  OCCURS 31 TIMES.
        // 002855           05  FD-C044-TDBAL          PIC 9(14).
        fileC044Contents.add(sb.toString());
    }

    private void rptTitle(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV273Lsnr rptTitle");
        // 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE4)
        // 021300     MOVE       SPACES            TO      REPORT-LINE.
        // 021400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        // 005700 01 WK-TITLE-LINE1.
        sb = new StringBuilder();
        sb.append(pageFg);
        // 005800    02 FILLER                       PIC X(20) VALUE SPACE.
        sb.append(formatUtil.padX("", 20));
        // 005900    02 TITLE-LABEL                  PIC X(38)
        // 006000               VALUE " 全行代理收付系統－虛擬分戶當日餘額表 ".
        sb.append(formatUtil.padX(" 全行代理收付系統－虛擬分戶當日餘額表 ", 38));
        // 006100    02 FILLER                       PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX("", 10));
        // 006200    02 FILLER                       PIC X(12)
        // 006300                              VALUE "FORM : C044".
        sb.append(formatUtil.padX("FORM : C044", 12));
        fileCONV273Contents.add(sb.toString());
        // 021500     MOVE       SPACES            TO      REPORT-LINE.
        // 021600     WRITE      REPORT-LINE       AFTER   1.
        fileCONV273Contents.add("");
        // 021700     MOVE       WK-YYYMMDD        TO      WK-PDATE.
        wkPdate = wkYYYMMDD;
        // 021800     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        // 006400 01 WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 006500    02 FILLER                       PIC X(10)
        // 006600                              VALUE " 分行別： ".
        sb.append(formatUtil.padX(" 分行別： ", 10));
        // 006700    02 WK-PBRNO                     PIC 9(03).
        sb.append(formatUtil.pad9(String.valueOf(wkPbrno), 10));
        // 006800    02 FILLER                       PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 006900    02 FILLER                       PIC X(13)
        // 007000                              VALUE " 印表日期： ".
        sb.append(formatUtil.padX(" 印表日期： ", 13));
        // 007100    02 WK-PDATE                     PIC 999/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(wkPdate), "999/99/99"));
        // 007200    02 FILLER                       PIC X(25) VALUE SPACE.
        sb.append(formatUtil.padX("", 25));
        // 007300    02 FILLER                       PIC X(10) VALUE " 總頁次： ".
        sb.append(formatUtil.padX(" 總頁次： ", 10));
        // 007400    02 WK-TOTPAGE                   PIC 9(06).
        sb.append(formatUtil.pad9("" + wkTotpage, 6));
        // 021900     MOVE       SPACES            TO      REPORT-LINE.
        fileCONV273Contents.add(sb.toString());
        // 022000     WRITE      REPORT-LINE       AFTER   1.
        fileCONV273Contents.add("");
        // 022100     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        // 007500 01 WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 007600    02 FILLER                PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX("", 2));
        // 007700    02 FILLER                PIC X(10) VALUE " 分戶帳號 ".
        sb.append(formatUtil.padX(" 分戶帳號 ", 10));
        // 007800    02 FILLER                PIC X(07) VALUE SPACE.
        sb.append(formatUtil.padX("", 7));
        // 007900    02 FILLER                PIC X(14) VALUE " 分戶帳號名稱 ".
        sb.append(formatUtil.padX(" 分戶帳號名稱 ", 14));
        // 008000    02 FILLER                PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 008100    02 FILLER                PIC X(06) VALUE " 日期 ".
        sb.append(formatUtil.padX(" 日期 ", 6));
        // 008200    02 FILLER                PIC X(14) VALUE SPACE.
        sb.append(formatUtil.padX("", 14));
        // 008300    02 FILLER                PIC X(06) VALUE " 餘額 ".
        sb.append(formatUtil.padX(" 餘額 ", 6));
        fileCONV273Contents.add(sb.toString());
        // 022200     MOVE       SPACES            TO      REPORT-LINE.
        // 022300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 008400 01 WK-TITLE-LINE4.
        // 008500    02 FILLER                PIC X(080) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        fileCONV273Contents.add(sb.toString());
    }

    // Exception process
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

    private void batchResponse() {

        Map<String, String> responseTextMap = new HashMap<>(textMap);
        if ("27X1111981".equals(wkFilename)) {
            responseTextMap.put("WK_FILENAME", "27X1111801");
        } else if ("27X1111801".equals(wkFilename)) {
            responseTextMap.put("WK_FILENAME", "");
        }
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
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
}
