/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.C087;
import com.bot.ncl.dto.entities.ClfeebyCodeBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svcimp.ClfeeServiceImpl;
import com.bot.ncl.jpa.svcimp.ClmrServiceImpl;
import com.bot.ncl.jpa.svcimp.CltmrServiceImpl;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C087Lsnr")
@Scope("prototype")
public class C087Lsnr extends BatchListenerCase<C087> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ClmrServiceImpl sClmrServiceImpl;
    @Autowired private CltmrServiceImpl sCltmrServiceImpl;
    @Autowired private ClfeeServiceImpl sClfeeServiceImpl;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;

    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_NAME_1 = "CL-BH-087"; // 檔名1
    private static final String FILE_NAME_2 = "4"; // 檔名2
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String tmpDir = "Dir";
    private String outputFilePath1; // 產檔路徑1
    private String outputFilePath2; // 產檔路徑2
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC087Contents; // 檔案內容
    private List<String> fileC0874Contents; // 檔案內容
    private String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";

    private String wkFnbsDy = "";
    private String wkFnbsDyYY = "";
    private String wkFnbsDyMM = "";
    private String wkFnbsDyDD = "";
    private String wkCode = "";
    private String wkRpt185Code = "";
    private String wkRptCode = "";
    private String wkPbrno = "";
    private String wkIPbrno = "";
    private String wk185Brno = "";

    private int wkSkip = 0;
    private int wkTotCnt = 0;
    private int wkRptCnt = 0;
    private String wkRptCName = "";
    private BigDecimal wkCfee1 = BigDecimal.ZERO;
    private BigDecimal wkRptCfee1 = BigDecimal.ZERO;

    private BigDecimal wkCfee4 = BigDecimal.ZERO;
    private BigDecimal wkAmt = BigDecimal.ZERO;
    private BigDecimal wkRptAmt = BigDecimal.ZERO;
    private BigDecimal wk185Amt = BigDecimal.ZERO;

    private BigDecimal wkRptCfee4 = BigDecimal.ZERO;
    private Long wkRptActno = 123456789l;
    private String wkPutname = "";
    private String wkRptNote = "";
    private int wkRptPage = 0;
    private String wkRptYY = "";
    private String wkRptMM = "";
    private int wk185Pageno = 0;
    private String wkRptYY185 = "";
    private String wkRptMM185 = "";
    private String wkRptPdate = "";
    private int wkPrtPage = 0;
    private String wkRptBrno = "";
    private int wk185Lineno = 0;
    private String kputhCode = "";
    private String kputhPbrno = "";
    private C087 event;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C087 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C087 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr run()");
        init(event);
        //// 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 014500     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 014600             STOP RUN.

        main();

        try {
            textFile.writeFileContent(outputFilePath1, fileC087Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        try {
            textFile.writeFileContent(outputFilePath2, fileC0874Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void init(C087 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr init ....");
        this.event = event;
        //// 搬本月最終營業日給WK-FNBSDY
        wkFnbsDy = getrocdate(this.event.getAggregateBuffer().getTxCom().getFnbsdy());

        wkFnbsDyYY = wkFnbsDy.substring(0, 3);
        wkFnbsDyMM = wkFnbsDy.substring(3, 5);
        wkFnbsDyDD = wkFnbsDy.substring(5, 7);
        // 讀檔路徑
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        // 產檔路徑1
        outputFilePath1 = fileDir + FILE_NAME_1;
        // 產檔路徑2
        outputFilePath2 = fileDir + FILE_NAME_1 + "-" + FILE_NAME_2;
        // 刪除舊檔
        textFile.deleteFile(outputFilePath1);
        textFile.deleteFile(outputFilePath2);
        fileC087Contents = new ArrayList<>();
        fileC0874Contents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr main ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        // 03 KPUTH-PUTFILE	GROUP
        //  05 KPUTH-PUTTYPE	9(02)	媒體種類 0-2
        //  05 KPUTH-PUTNAME	X(08)	媒體檔名 2-10
        // 03 KPUTH-PUTFILE-R1 	REDEFINES KPUTH-PUTFILE
        //  05 KPUTH-ENTPNO	X(10)
        // 03 KPUTH-CODE	X(06)	代收類別 10-16
        // 03 KPUTH-RCPTID	X(16)	銷帳號碼 16-32
        // 03 KPUTH-DATE	9(07)	代收日 32-39
        // 03 KPUTH-TIME	9(06)	代收時間 39-45
        // 03 KPUTH-CLLBR	9(03)	代收行 45-48
        // 03 KPUTH-LMTDATE	9(06)	繳費期限 48-54
        // 03 KPUTH-AMT	9(10)	繳費金額 54-64
        // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
        // 03 KPUTH-USERDATE-R1 	REDEFINES KPUTH-USERDATA
        //  05 KPUTH-SMSERNO	X(03)
        //  05 KPUTH-RETAILNO	X(08)
        //  05 KPUTH-BARCODE3	X(15)
        //  05 KPUTH-FILLER	X(14)
        // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
        // 03 KPUTH-TXTYPE	X(01)	帳務別 111-112
        // 03 KPUTH-SERINO	9(06)	交易明細流水序號 112-118
        // 03 KPUTH-PBRNO	9(03)	主辦分行 118-121
        // 03 KPUTH-UPLDATE	9(07) 121-128
        // 03 KPUTH-FEETYPE	9(01) 128-129
        // 03 KPUTH-FEEO2L	9(05)V99 129-134
        // 03 FILLER	X(02)
        // 03 FILLER	X(02)
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            kputhCode = detail.substring(10, 16);
            kputhPbrno = detail.substring(118, 121);
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "C087Lsnr kputhCode = {} ,kputhPbrno = {} ",
                    kputhCode,
                    kputhPbrno);

            // 首筆
            //  A.搬KPUTH-CODE、KPUTH-PBRNO給控制變數WK-... & 報表
            //  B.執行1000-CLMR-RTN，A.找CLMR、B.設定WK-SKIP、C.累計筆數、搬DB-CLMR-...給報表 & WK-...
            //  C.執行RPT-HEAD-RTN，寫REPORTFL表頭
            //  D.執行RPT-HEAD185-RTN，寫REPORTFLA表頭
            //  E.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-KPUTH
            // 015300* 一代收類別之首筆將各欄位之值搬入，並寫入報表表頭
            // 015400     IF        WK-CODE            =    SPACE
            // 015500     AND       WK-PBRNO           =    SPACE
            if (wkCode.trim().isEmpty() && wkPbrno.trim().isEmpty()) {
                // 015600       MOVE    KPUTH-CODE         TO   WK-CODE,
                // 015700                                       WK-RPT-CODE
                // 015800       MOVE    KPUTH-PBRNO        TO   WK-PBRNO,
                // 015900                                       WK-RPT-BRNO
                wkCode = kputhCode;
                wkRptCode = kputhCode;
                wkPbrno = kputhPbrno;
                wkRptBrno = kputhPbrno;

                // 016000       PERFORM 1000-CLMR-RTN      THRU 1000-CLMR-EXIT
                clmr();

                // 016100       PERFORM RPT-HEAD-RTN       THRU RPT-HEAD-EXIT
                rptHead(PAGE_SEPARATOR);

                // 016150       PERFORM RPT-HEAD185-RTN    THRU RPT-HEAD185-EXIT
                rptHead185(PAGE_SEPARATOR);

                // 016200       GO  TO  0000-MAIN-LOOP
                continue;
            }
            // 若 代收類別相同 時
            //  A.若WK-SKIP=0 時，累計筆數
            //  B.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-KPUTH
            //
            // 016400* 相同代收類別則續作加總
            // 016500     IF        KPUTH-CODE         =    WK-CODE
            if (kputhCode.equals(wkCode)) {
                // 016600       IF      WK-SKIP            =    0
                if (wkSkip == 0) {
                    // 016700         ADD   1                  TO   WK-TOTCNT
                    wkTotCnt = wkTotCnt + 1;
                    // 016800       END-IF
                }
                if (cnt == lines.size()) {
                    mainLast();
                }
                // 016900       GO   TO 0000-MAIN-LOOP
                continue;
                // 017000     END-IF.
            }
            // 若 主辦行相同 且 代收類別不同 時
            //  A.若WK-SKIP=0 時，執行RPT-DTL-RTN，寫REPORTFL、REPORTFLA明細
            //  B.執行SWITCH-CODE-RTN，CODE不同之初始設定
            //  C.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-KPUTH
            //
            // 017100* 不同代收類別則將前一代收類別資料寫入報表檔中
            // 017200     IF        KPUTH-CODE     NOT =    WK-CODE
            // 017300     AND       KPUTH-PBRNO        =    WK-PBRNO
            if (!kputhCode.equals(wkCode) && kputhPbrno.equals(wkPbrno)) {
                // 017400       IF      WK-SKIP            =    0
                if (wkSkip == 0) {
                    // 017500         PERFORM RPT-DTL-RTN      THRU RPT-DTL-EXIT
                    rptDtl();
                    // 017600       END-IF
                }
                // 017700       PERFORM SWITCH-CODE-RTN    THRU SWITCH-CODE-EXIT
                switchCode();

                if (cnt == lines.size()) {
                    mainLast();
                }
                // 017800       GO   TO 0000-MAIN-LOOP
                continue;
                // 017900     END-IF.
            }
            // 若 主辦行不同時
            //  A.執行SWITCH-BRNO-RTN，PBRNO不同之初始設定
            //  B.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-KPUTH
            // 018000* 不同主辦行則直接換頁
            // 018100     IF        KPUTH-PBRNO    NOT =    WK-PBRNO
            if (!kputhPbrno.equals(wkPbrno)) {
                // 018200       PERFORM SWITCH-BRNO-RTN    THRU SWITCH-BRNO-EXIT
                switchBrno();
                // 018300       GO TO   0000-MAIN-LOOP
                if (cnt == lines.size()) {
                    mainLast();
                }
                continue;
                // 018400     END-IF.
            }

            if (cnt == lines.size()) {
                mainLast();
            }
        }
    }

    private void clmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr clmr ....");
        wkSkip = 0;
        // 依 代收類別 找事業單位基本資料檔，若有誤
        //  若找無資料，A.WK-SKIP設為1、B.結束本段落
        //  其他資料庫異常；1. 搬值給WC-ERRMSG-... 2.執行DB99-DMERROR-RTN；PUTERRMSG & CALL
        // SYSTEM DMTERMINATE，異常結束程式
        // 019600    FIND  DB-CLMR-IDX1  AT  DB-CLMR-CODE   = WK-CODE
        // 019700      ON  EXCEPTION
        // 019800      IF  DMSTATUS(NOTFOUND)
        ClmrBus tClmr = sClmrServiceImpl.findById(wkCode);
        if (tClmr == null) {
            // 019900          MOVE  1    TO   WK-SKIP
            // 020000          GO TO 1000-CLMR-EXIT
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr tClmr = NULL ....");

            wkSkip = 1;
            return;
        }
        CltmrBus tCltmr = sCltmrServiceImpl.findById(tClmr.getCode());
        if (tCltmr == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr tCltmr = NULL ....");

            wkSkip = 1;
            return;
        }
        // 020100      ELSE
        // 020200          MOVE  SPACES          TO   WC-ERRMSG-REC
        // 020300          MOVE  2               TO   WC-LOCK-FLAG
        // 020400          MOVE  2               TO   WC-ERRMSG-TXRSUT
        // 020500          MOVE  "FDCLMR"        TO   WC-ERRMSG-SUB
        // 020600          MOVE  "FDCLMR"        TO   WC-ERRMSG-MAIN
        // 020700          PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT
        // 020800      END-IF.

        // 若 事業單位每筆給付費用 = 0 且 事業單位給付主辦行每月費用 = 0
        //  A.WK-SKIP設為1(不列印)
        //  B.結束本段落
        List<ClfeebyCodeBus> lClfee =
                sClfeeServiceImpl.findbyCode(tClmr.getCode(), 0, Integer.MAX_VALUE);
        if (lClfee == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr lClfee = NULL ....");

            wkSkip = 1;
            return;
        }
        ClfeebyCodeBus tClfee = lClfee.get(0);
        // 021000    IF   DB-CLMR-CFEE1 = 0     AND   DB-CLMR-CFEE4 = 0
        if (tClfee.getCfee1().compareTo(BigDecimal.ZERO) == 0
                && tClfee.getCfee4().compareTo(BigDecimal.ZERO) == 0) {
            // 021100         MOVE  1                 TO     WK-SKIP
            wkSkip = 1;
            // 021200         GO TO 1000-CLMR-EXIT
            return;
        }
        // 累計筆數
        // 021500    ADD        1                 TO      WK-TOTCNT.
        wkTotCnt = wkTotCnt + 1;

        // 搬DB-CLMR-...給報表 & WK-...
        // 021600    MOVE       DB-CLMR-CNAME     TO      WK-RPT-CNAME.
        // 021700    MOVE       DB-CLMR-CFEE1     TO      WK-CFEE1,
        // 021800                                         WK-RPT-CFEE1.
        // 021900    MOVE       DB-CLMR-CFEE4     TO      WK-CFEE4,
        // 022000                                         WK-RPT-CFEE4.
        // 022100    MOVE       DB-CLMR-ACTNO     TO      WK-RPT-ACTNO.
        wkRptCName = tClmr.getCname();
        wkCfee1 = tClfee.getCfee1();
        wkRptCfee1 = tClfee.getCfee1();
        wkCfee4 = tClfee.getCfee4();
        wkRptCfee4 = tClfee.getCfee4();
        wkRptActno = tClmr.getActno();
        wkPutname = tCltmr.getPutname();
        wkRptNote = getRptNote(wkPutname);

        // 023100 1000-CLMR-EXIT.
    }

    private void rptHead(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr rptHead ....");
        // 023400 RPT-HEAD-RTN.

        //// 搬 日期、頁次(1) 到 報表
        // 023500    MOVE     WK-FNBSDY-YEAR          TO      WK-RPT-YEAR  .
        // 023600    MOVE     WK-FNBSDY-MONTH         TO      WK-RPT-MONTH .
        // 023700    MOVE     WK-FNBSDY               TO      WK-RPT-PDATE.
        // 023720    MOVE     1                       TO      WK-RPT-PAGE.
        wkRptYY = wkFnbsDyYY;
        wkRptMM = wkFnbsDyMM;
        wkRptPdate = wkFnbsDy;
        wkRptPage = 1;
        //// 寫REPORTFL表頭(RPT087-TIT1~RPT087-TIT5)
        //
        // 023800    MOVE     SPACES                  TO      REPORT-LINE.
        // 023900    WRITE    REPORT-LINE          AFTER      PAGE.
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        fileC087Contents.add(sb.toString());

        // 024000    MOVE     SPACES                  TO      REPORT-LINE.
        // 024100    WRITE    REPORT-LINE          AFTER      1.
        fileC087Contents.add("");

        // 024200    WRITE    REPORT-LINE           FROM      RPT087-TIT1.
        // 005300 01   RPT087-TIT1.
        // 005400  03  FILLER                             PIC X(44) VALUE SPACES.
        // 005500  03  FILLER                             PIC X(24) VALUE
        // 005600      " 台灣銀行電子化代收業務 ".
        // 005700  03  WK-RPT-YEAR                        PIC Z99.
        // 005800  03  FILLER                             PIC X(04) VALUE " 年 ".
        // 005900  03  WK-RPT-MONTH                       PIC 99.
        // 006000  03  FILLER                             PIC X(38) VALUE
        // 006100      " 月系統使用及清算服務費應收費用月報表 ".
        // 006200  03  FILLER                             PIC X(35) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 44));
        sb.append(formatUtil.padX(" 台灣銀行電子化代收業務： ", 24));
        sb.append(formatUtil.padX(wkRptYY, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.padX(wkRptMM, 2));
        sb.append(formatUtil.padX(" 月系統使用及清算服務費應收費用月報表 ", 38));
        sb.append(formatUtil.padX(" ", 35));
        fileC087Contents.add(sb.toString());

        // 024300    MOVE     SPACES                  TO      REPORT-LINE.
        // 024400    WRITE    REPORT-LINE          AFTER      1.
        fileC087Contents.add("");

        // 024500    WRITE    REPORT-LINE           FROM      RPT087-TIT2.
        // 006300 01   RPT087-TIT2.
        // 006400  03  FILLER                             PIC X(05) VALUE SPACES.
        // 006500  03  FILLER                             PIC X(25) VALUE
        // 006600      " 報表名稱： CL-C087".
        // 006700  03  FILLER                             PIC X(80) VALUE SPACES.
        // 006800  03  FILLER                             PIC X(13) VALUE
        // 006900      " 頁　　次： ".
        // 006950  03  WK-RPT-PAGE                        PIC ZZ9.
        // 007000  03  FILLER                             PIC X(26) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 報表名稱： CL-C087", 25));
        sb.append(formatUtil.padX(" ", 80));
        sb.append(formatUtil.padX(" 頁　　次： ", 13));
        sb.append(formatUtil.padX("" + 1, 3));
        sb.append(formatUtil.padX(" ", 26));
        fileC087Contents.add(sb.toString());

        // 024600    MOVE     SPACES                  TO      REPORT-LINE.
        // 024700    WRITE    REPORT-LINE           FROM      RPT087-TIT3.
        // 007100 01   RPT087-TIT3.
        // 007200  03  FILLER                             PIC X(05) VALUE SPACES.
        // 007300  03  FILLER                             PIC X(12) VALUE
        // 007400      " 主辦分行： ".
        // 007500  03  WK-RPT-BRNO                        PIC X(03) VALUE SPACES.
        // 007600  03  FILLER                             PIC X(90) VALUE SPACES.
        // 007700  03  FILLER                             PIC X(16) VALUE
        // 007800      " 用　　途：參考 ".
        // 007900  03  FILLER                             PIC X(28) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        sb.append(formatUtil.padX(wkRptBrno, 3));
        sb.append(formatUtil.padX(" ", 90));
        sb.append(formatUtil.padX(" 用　　途：參考 ", 16));
        sb.append(formatUtil.padX(" ", 28));
        fileC087Contents.add(sb.toString());

        // 024800    MOVE     SPACES                  TO      REPORT-LINE.
        // 024900    WRITE    REPORT-LINE           FROM      RPT087-TIT4.
        // 008000 01   RPT087-TIT4.
        // 008100  03  FILLER                             PIC X(05) VALUE SPACES.
        // 008200  03  FILLER                             PIC X(12) VALUE
        // 008300      " 印表日期： ".
        // 008400  03  WK-RPT-PDATE                       PIC Z99/99/99.
        // 008500  03  FILLER                             PIC X(84) VALUE SPACES.
        // 008600  03  FILLER                             PIC X(16) VALUE
        // 008700      " 保存年限：２年 ".
        // 008800  03  FILLER                             PIC X(24) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        sb.append(formatUtil.padX(" ", 84));
        sb.append(formatUtil.padX(" 保存年限：２年 ", 16));
        sb.append(formatUtil.padX(" ", 24));
        fileC087Contents.add(sb.toString());

        // 025000    MOVE     SPACES                  TO      REPORT-LINE.
        // 025100    WRITE    REPORT-LINE          AFTER      1.
        fileC087Contents.add("");

        // 025200    MOVE     SPACES                  TO      REPORT-LINE.
        // 025300    WRITE    REPORT-LINE           FROM      RPT087-TIT5.
        // 008900 01   RPT087-TIT5.
        // 009000  03  FILLER                             PIC X(10) VALUE
        // 009100      " 代收類別 ".
        // 009200  03  FILLER                             PIC X(03) VALUE SPACES.
        // 009300  03  FILLER                             PIC X(10) VALUE
        // 009400      " 客戶名稱 ".
        // 009500  03  FILLER                             PIC X(33) VALUE SPACES.
        // 009600  03  FILLER                             PIC X(08) VALUE
        // 009700      " 總筆數 ".
        // 009800  03  FILLER                             PIC X(05) VALUE SPACES.
        // 009900  03  FILLER                             PIC X(24) VALUE
        // 010000      " 事業單位每筆／每月給付 ".
        // 010100  03  FILLER                             PIC X(05) VALUE SPACES.
        // 010200  03  FILLER                             PIC X(10) VALUE
        // 010300      " 應收金額 ".
        // 010400  03  FILLER                             PIC X(11) VALUE SPACES.
        // 010500  03  FILLER                             PIC X(14) VALUE
        // 010600      " 授權扣款帳號 ".
        // 010700  03  FILLER                             PIC X(03) VALUE SPACES.
        // 010800  03  FILLER                             PIC X(06) VALUE
        // 010900      " 備註 ".
        // 011000  03  FILLER                             PIC X(08) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 客戶名稱 ", 10));
        sb.append(formatUtil.padX(" ", 33));
        sb.append(formatUtil.padX(" 總筆數 ", 8));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 事業單位每筆／每月給付 ", 24));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 應收金額 ", 10));
        sb.append(formatUtil.padX(" ", 11));
        sb.append(formatUtil.padX(" 授權扣款帳號 ", 14));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 備註 ", 6));
        sb.append(formatUtil.padX(" ", 8));
        fileC087Contents.add(sb.toString());

        // 025400    MOVE     SPACES                  TO      REPORT-LINE.
        // 025500    WRITE    REPORT-LINE           FROM      RPT087-GATE-LINE.
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 150));
        fileC087Contents.add(sb.toString());

        // 025600 RPT-HEAD-EXIT.
    }

    private void rptHead185(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr head185 ....");
        // 025805 RPT-HEAD185-RTN.

        //// WK-185PAGENO:頁數控制變數
        // 025807    ADD      1                       TO      WK-185PAGENO.
        wk185Pageno = wk185Pageno + 1;

        //// 搬 日期、頁數、主辦分行"185" 到 報表
        // 025810    MOVE     WK-FNBSDY-YEAR          TO      WK-RPT-YEAR185.
        // 025815    MOVE     WK-FNBSDY-MONTH         TO      WK-RPT-MONTH185.
        // 025820    MOVE     WK-FNBSDY               TO      WK-RPT-PDATE.
        // 025825    MOVE     WK-185PAGENO            TO      WK-RPT-PAGE.
        // 025827    MOVE     "185"                   TO      WK-RPT-BRNO.
        wkRptYY185 = wkFnbsDyYY;
        wkRptMM185 = wkFnbsDyMM;
        wkRptPdate = wkFnbsDy;
        wkPrtPage = wk185Pageno;
        wkRptBrno = "185";

        //// 寫REPORTFLA表頭(RPT087-185TIT1、RPT087-TIT2~RPT087-TIT5)
        // 025831    MOVE     SPACES                  TO      REPORT-LINEA.
        // 025832    WRITE    REPORT-LINEA         AFTER      PAGE.
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 2= {}",
                sb.toString());

        // 025835    WRITE    REPORT-LINEA          FROM      REPORT-LINEA.
        sb = new StringBuilder();
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 3= {}",
                sb.toString());

        // 025836    WRITE    REPORT-LINEA          FROM      RPT087-185TIT1.
        // 013310 01   RPT087-185TIT1.
        // 013315  03  FILLER                             PIC X(44) VALUE SPACES.
        // 013320  03  FILLER                             PIC X(24) VALUE
        // 013325      " 台灣銀行電子化代收業務 ".
        // 013330  03  WK-RPT-YEAR185                     PIC Z99.
        // 013335  03  FILLER                             PIC X(04) VALUE " 年 ".
        // 013340  03  WK-RPT-MONTH185                    PIC 99.
        // 013345  03  FILLER                             PIC X(46) VALUE
        // 013350      " 月系統使用及清算服務費應收費用月報表（全行） ".
        // 013355  03  FILLER                             PIC X(27) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 44));
        sb.append(formatUtil.padX(" 台灣銀行電子化代收業務： ", 24));
        sb.append(formatUtil.padX(wkRptYY185, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.padX(wkRptMM185, 2));
        sb.append(formatUtil.padX(" 月系統使用及清算服務費應收費用月報表（全行） ", 46));
        sb.append(formatUtil.padX(" ", 27));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 4= {}",
                sb.toString());

        // 025837    MOVE     SPACES                  TO      REPORT-LINEA.
        // 025838    WRITE    REPORT-LINEA          FROM      REPORT-LINEA.
        sb = new StringBuilder();
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 5= {}",
                sb.toString());

        // 025839    MOVE     RPT087-TIT2             TO      RPT087-185LINE.
        //// 報表名稱： CL-C087 改為 CL-C087-4
        // 025840    MOVE     "-4"                    TO      RPT087-185LINE(25:2).
        // 025841    WRITE    REPORT-LINEA          FROM      RPT087-185LINE.
        // 006300 01   RPT087-TIT2.
        // 006400  03  FILLER                             PIC X(05) VALUE SPACES.
        // 006500  03  FILLER                             PIC X(25) VALUE
        // 006600      " 報表名稱： CL-C087".
        // 006700  03  FILLER                             PIC X(80) VALUE SPACES.
        // 006800  03  FILLER                             PIC X(13) VALUE
        // 006900      " 頁　　次： ".
        // 006950  03  WK-RPT-PAGE                        PIC ZZ9.
        // 007000  03  FILLER                             PIC X(26) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 報表名稱： CL-C087-4", 27));
        sb.append(formatUtil.padX(" ", 78));
        sb.append(formatUtil.padX(" 頁　　次： ", 13));
        sb.append(formatUtil.padX("" + wkPrtPage, 3));
        sb.append(formatUtil.padX(" ", 26));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 6= {}",
                sb.toString());

        // 025842    WRITE    REPORT-LINEA          FROM      RPT087-TIT3.
        // 007100 01   RPT087-TIT3.
        // 007200  03  FILLER                             PIC X(05) VALUE SPACES.
        // 007300  03  FILLER                             PIC X(12) VALUE
        // 007400      " 主辦分行： ".
        // 007500  03  WK-RPT-BRNO                        PIC X(03) VALUE SPACES.
        // 007600  03  FILLER                             PIC X(90) VALUE SPACES.
        // 007700  03  FILLER                             PIC X(16) VALUE
        // 007800      " 用　　途：參考 ".
        // 007900  03  FILLER                             PIC X(28) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        sb.append(formatUtil.padX(wkRptBrno, 3));
        sb.append(formatUtil.padX(" ", 90));
        sb.append(formatUtil.padX(" 用　　途：參考 ", 16));
        sb.append(formatUtil.padX(" ", 28));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 7= {}",
                sb.toString());

        // 025845    WRITE    REPORT-LINEA          FROM      RPT087-TIT4.
        // 008000 01   RPT087-TIT4.
        // 008100  03  FILLER                             PIC X(05) VALUE SPACES.
        // 008200  03  FILLER                             PIC X(12) VALUE
        // 008300      " 印表日期： ".
        // 008400  03  WK-RPT-PDATE                       PIC Z99/99/99.
        // 008500  03  FILLER                             PIC X(84) VALUE SPACES.
        // 008600  03  FILLER                             PIC X(16) VALUE
        // 008700      " 保存年限：２年 ".
        // 008800  03  FILLER                             PIC X(24) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(reportUtil.customFormat(wkRptPdate, "Z99/99/99"));
        sb.append(formatUtil.padX(" ", 84));
        sb.append(formatUtil.padX(" 保存年限：２年 ", 16));
        sb.append(formatUtil.padX(" ", 24));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 8= {}",
                sb.toString());

        // 025846    MOVE     SPACES                  TO      REPORT-LINEA.
        // 025847    WRITE    REPORT-LINEA          FROM      REPORT-LINEA.
        sb = new StringBuilder();
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 9= {}",
                sb.toString());

        // 025850    WRITE    REPORT-LINEA          FROM      RPT087-TIT5.
        // 008900 01   RPT087-TIT5.
        // 009000  03  FILLER                             PIC X(10) VALUE
        // 009100      " 代收類別 ".
        // 009200  03  FILLER                             PIC X(03) VALUE SPACES.
        // 009300  03  FILLER                             PIC X(10) VALUE
        // 009400      " 客戶名稱 ".
        // 009500  03  FILLER                             PIC X(33) VALUE SPACES.
        // 009600  03  FILLER                             PIC X(08) VALUE
        // 009700      " 總筆數 ".
        // 009800  03  FILLER                             PIC X(05) VALUE SPACES.
        // 009900  03  FILLER                             PIC X(24) VALUE
        // 010000      " 事業單位每筆／每月給付 ".
        // 010100  03  FILLER                             PIC X(05) VALUE SPACES.
        // 010200  03  FILLER                             PIC X(10) VALUE
        // 010300      " 應收金額 ".
        // 010400  03  FILLER                             PIC X(11) VALUE SPACES.
        // 010500  03  FILLER                             PIC X(14) VALUE
        // 010600      " 授權扣款帳號 ".
        // 010700  03  FILLER                             PIC X(03) VALUE SPACES.
        // 010800  03  FILLER                             PIC X(06) VALUE
        // 010900      " 備註 ".
        // 011000  03  FILLER                             PIC X(08) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 客戶名稱 ", 10));
        sb.append(formatUtil.padX(" ", 33));
        sb.append(formatUtil.padX(" 總筆數 ", 8));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 事業單位每筆／每月給付 ", 24));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 應收金額： ", 10));
        sb.append(formatUtil.padX(" ", 11));
        sb.append(formatUtil.padX(" 授權扣款帳號 ", 14));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 備註 ", 6));
        sb.append(formatUtil.padX(" ", 8));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 10= {}",
                sb.toString());

        // 025852    WRITE    REPORT-LINEA          FROM      RPT087-GATE-LINE.
        // 011100 01   RPT087-GATE-LINE.
        // 011200  03  FILLER                             PIC X(150) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 150));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 11= {}",
                sb.toString());

        //// WK-185LINENO:行數控制變數
        // 025853    MOVE     8                       TO      WK-185LINENO.
        wk185Lineno = 8;

        // 025860 RPT-HEAD185-EXIT.
    }

    private void rptDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr rptDtl ....");
        // 025900 RPT-DTL-RTN.

        //// 寫REPORTFL明細(RPT087-DTL)
        // 026000       MOVE    SPACES            TO      REPORT-LINE.
        // 026100       MOVE    WK-TOTCNT         TO      WK-RPT-CNT.
        wkRptCnt = wkTotCnt;
        // 026200       IF      WK-CFEE1 NOT= 0  AND  WK-CFEE4 NOT= 0
        if (wkCfee1.compareTo(BigDecimal.ZERO) != 0 && wkCfee4.compareTo(BigDecimal.ZERO) != 0) {
            // 026300            MOVE      0                 TO      WK-RPT-AMT
            // 026400            MOVE      " 給付資料有誤 "  TO      WK-RPT-NOTE
            wkRptAmt = BigDecimal.ZERO;
            wkRptNote = " 給付資料有誤 ";
        } else {
            // 026600            IF WK-CFEE4    = 0
            if (wkCfee4.compareTo(BigDecimal.ZERO) == 0) {
                // 026700               COMPUTE   WK-AMT =  WK-CFEE1 * WK-TOTCNT
                wkAmt = wkCfee1.multiply(new BigDecimal(wkTotCnt));
            } else {
                // 026900               COMPUTE   WK-AMT =  WK-CFEE4{
                wkAmt = wkCfee4;
                // 027000            END-IF
            }
            // 027100            MOVE      WK-AMT            TO      WK-RPT-AMT
            wkRptAmt = wkAmt;
            // 027200       END-IF.
        }
        // 027300       WRITE   REPORT-LINE       FROM    RPT087-DTL.
        // 011300 01   RPT087-DTL.
        // 011400  03  WK-RPT-CODE                        PIC X(06) VALUE SPACES.
        // 011500  03  FILLER                             PIC X(07) VALUE SPACES.
        // 011600  03  WK-RPT-CNAME                       PIC X(40) VALUE SPACES.
        // 011700  03  FILLER                             PIC X(03) VALUE SPACES.
        // 011800  03  WK-RPT-CNT                         PIC Z,ZZZ,ZZ9.
        // 011900  03  FILLER                             PIC X(04) VALUE SPACES.
        // 012000  03  WK-RPT-CFEE1                       PIC Z,ZZ9.99.
        // 012100  03  FILLER                             PIC X(01) VALUE "/".
        // 012200  03  WK-RPT-CFEE4                       PIC Z,ZZZ,ZZZ,ZZ9.99.
        // 012300  03  FILLER                             PIC X(04) VALUE SPACES.
        // 012400  03  WK-RPT-AMT                         PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
        // 012500  03  FILLER                             PIC X(03) VALUE SPACES.
        // 012600  03  WK-RPT-ACTNO                       PIC 9(12).
        // 012700  03  FILLER                             PIC X(05) VALUE SPACES.
        // 012800  03  WK-RPT-NOTE                        PIC X(14) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkRptCode, 6));
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(wkRptCName, 40));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(reportUtil.customFormat("" + wkRptCnt, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(reportUtil.customFormat("" + wkRptCfee1, "Z,ZZ9.99"));
        sb.append(formatUtil.padX("/", 1));
        sb.append(reportUtil.customFormat("" + wkRptCfee4, "Z,ZZZ,ZZZ,ZZ9.99"));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(reportUtil.customFormat("" + wkRptAmt, "ZZZ,ZZZ,ZZZ,ZZ9.99"));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.pad9("" + wkRptActno, 12));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(wkRptNote, 14));
        fileC087Contents.add(sb.toString());
        // 012900 01   RPT087-DTL-END.

        // 若主辦分行不同 且主辦分行控制變數 <> 空白 時(非首筆)
        //  寫REPORTFLA小計
        // 027301       IF      WK-PBRNO          NOT =   WK-185BRNO AND
        // 027302               WK-185BRNO        NOT =   SPACES
        if (!wkPbrno.equals(wk185Brno) && !wk185Brno.isEmpty()) {
            // 027303           MOVE  SPACES          TO      RPT087-185DTL
            // 027304           MOVE  " 小計 "        TO      RPT087-185DTL(1:6)
            // 027305           MOVE  WK-185AMT       TO      WK-RPT-185AMT
            // 027306           WRITE REPORT-LINEA    FROM    RPT087-185DTL
            // 013360 01   RPT087-185DTL.
            // 013361  03  WK-RPT-185PBRNO                    PIC 9(03).
            // 013362  03  WK-RPT-185X                        PIC X(01) VALUE "-".
            // 013363  03  WK-RPT-185CODE                     PIC X(06) VALUE SPACES.
            // 013364  03  FILLER                             PIC X(03) VALUE SPACES.
            // 013365  03  WK-RPT-185CNAME                    PIC X(40) VALUE SPACES.
            // 013366  03  FILLER                             PIC X(03) VALUE SPACES.
            // 013367  03  WK-RPT-185CNT                      PIC Z,ZZZ,ZZ9.
            // 013368  03  FILLER                             PIC X(04) VALUE SPACES.
            // 013369  03  WK-RPT-185CFEE1                    PIC Z,ZZ9.99.
            // 013370  03  FILLER                             PIC X(01) VALUE "/".
            // 013371  03  WK-RPT-185CFEE4                    PIC Z,ZZZ,ZZZ,ZZ9.99.
            // 013372  03  FILLER                             PIC X(04) VALUE SPACES.
            // 013373  03  WK-RPT-185AMT                      PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
            // 013374  03  FILLER                             PIC X(03) VALUE SPACES.
            // 013375  03  WK-RPT-185ACTNO                    PIC 9(12).
            // 013377  03  FILLER                             PIC X(05) VALUE SPACES.
            // 013379  03  WK-RPT-185NOTE                     PIC X(14) VALUE SPACES.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" 小計 ", 6));
            sb.append(formatUtil.padX(" ", 4));
            sb.append(formatUtil.padX(" ", 3));
            sb.append(formatUtil.padX(" ", 40));
            sb.append(formatUtil.padX(" ", 3));
            sb.append(formatUtil.padX(" ", 9));
            sb.append(formatUtil.padX(" ", 4));
            sb.append(formatUtil.padX(" ", 8));
            sb.append(formatUtil.padX(" ", 1));
            sb.append(formatUtil.padX(" ", 16));
            sb.append(formatUtil.padX(" ", 4));
            sb.append(reportUtil.customFormat("" + wk185Amt, "ZZZ,ZZZ,ZZZ,ZZ9.99"));
            sb.append(formatUtil.padX(" ", 3));
            sb.append(formatUtil.padX(" ", 12));
            sb.append(formatUtil.padX(" ", 5));
            sb.append(formatUtil.padX(" ", 14));
            fileC0874Contents.add(sb.toString());
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "C087Lsnr fileC0874Contents 12= {}",
                    sb.toString());

            // 027307           ADD   1               TO      WK-185LINENO
            wk185Lineno = wk185Lineno + 1;

            //// 若行數>55，執行RPT-HEAD185-RTN，寫REPORTFLA表頭
            //
            // 027308           IF  WK-185LINENO > "55"
            if (wk185Lineno > 55) {
                // 027309               PERFORM RPT-HEAD185-RTN    THRU RPT-HEAD185-EXIT
                rptHead185(PAGE_SEPARATOR);
                // 027310           END-IF
            }

            // 027311           MOVE  SPACES          TO      REPORT-LINEA
            // 027312           WRITE REPORT-LINEA    AFTER   1
            sb = new StringBuilder();
            fileC0874Contents.add(sb.toString());
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "C087Lsnr fileC0874Contents 13= {}",
                    sb.toString());

            // 027313           ADD   1               TO      WK-185LINENO
            wk185Lineno = wk185Lineno + 1;

            //// 若行數>55，執行RPT-HEAD185-RTN，寫REPORTFLA表頭
            // 027314           IF  WK-185LINENO > "55"
            if (wk185Lineno > 55) {
                // 027315               PERFORM RPT-HEAD185-RTN    THRU RPT-HEAD185-EXIT
                rptHead185(PAGE_SEPARATOR);
                // 027317           END-IF
            }
            // 027318           MOVE  0               TO      WK-185AMT
            wk185Amt = BigDecimal.ZERO;
            // 027319       END-IF.
        }

        //// 寫REPORTFLA明細(RPT087-185DTL)
        // 027320       MOVE    SPACES            TO      RPT087-185DTL.
        // 027325       MOVE    RPT087-DTL        TO      RPT087-185DTL.
        // 027350       MOVE    WK-PBRNO          TO      WK-RPT-185PBRNO,
        // 027352                                         WK-185BRNO.
        // 027355       MOVE    "-"               TO      WK-RPT-185X.
        // 027360       MOVE    WK-CODE           TO      WK-RPT-185CODE.
        wk185Brno = wkPbrno;
        wkRpt185Code = wkCode;
        // 027362       WRITE   REPORT-LINEA      FROM    RPT087-185DTL.
        // 013360 01   RPT087-185DTL.
        // 013361  03  WK-RPT-185PBRNO                    PIC 9(03).
        // 013362  03  WK-RPT-185X                        PIC X(01) VALUE "-".
        // 013363  03  WK-RPT-185CODE                     PIC X(06) VALUE SPACES.
        // 013364  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013365  03  WK-RPT-185CNAME                    PIC X(40) VALUE SPACES.
        // 013366  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013367  03  WK-RPT-185CNT                      PIC Z,ZZZ,ZZ9.
        // 013368  03  FILLER                             PIC X(04) VALUE SPACES.
        // 013369  03  WK-RPT-185CFEE1                    PIC Z,ZZ9.99.
        // 013370  03  FILLER                             PIC X(01) VALUE "/".
        // 013371  03  WK-RPT-185CFEE4                    PIC Z,ZZZ,ZZZ,ZZ9.99.
        // 013372  03  FILLER                             PIC X(04) VALUE SPACES.
        // 013373  03  WK-RPT-185AMT                      PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
        // 013374  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013375  03  WK-RPT-185ACTNO                    PIC 9(12).
        // 013377  03  FILLER                             PIC X(05) VALUE SPACES.
        // 013379  03  WK-RPT-185NOTE                     PIC X(14) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(wkPbrno, 3));
        sb.append(formatUtil.padX("-", 1));
        sb.append(formatUtil.padX(wkRpt185Code, 6));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(wkRptCName, 40));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(reportUtil.customFormat("" + wkTotCnt, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(reportUtil.customFormat("" + wkRptCfee1, "Z,ZZ9.99"));
        sb.append(formatUtil.padX("/", 1));
        sb.append(reportUtil.customFormat("" + wkRptCfee4, "Z,ZZZ,ZZZ,ZZ9.99"));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(reportUtil.customFormat("" + wkRptAmt, "ZZZ,ZZZ,ZZZ,ZZ9.99"));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.pad9("" + wkRptActno, 12));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(wkRptNote, 14));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 14 = {}",
                sb.toString());

        // 027363       ADD     WK-AMT            TO      WK-185AMT.
        // 027364       ADD     1                 TO      WK-185LINENO.
        wk185Amt = wk185Amt.add(wkAmt);
        wk185Lineno = wk185Lineno + 1;

        //// 若行數>55，執行RPT-HEAD185-RTN，寫REPORTFLA表頭
        //
        // 027365       IF  WK-185LINENO > "55"
        if (wk185Lineno > 55) {
            // 027366           PERFORM RPT-HEAD185-RTN    THRU RPT-HEAD185-EXIT
            rptHead185(PAGE_SEPARATOR);
            // 027367       END-IF.
        }
        // 027400 RPT-DTL-EXIT.
    }

    private void switchCode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr switchCode ....");
        // 027700 SWITCH-CODE-RTN.

        //// 清變數
        // 027800    MOVE     0                 TO       WK-TOTCNT,
        // 027900                                        WK-CFEE1,
        // 028000                                        WK-CFEE4,
        // 028100                                        WK-AMT,
        // 028200                                        WK-SKIP.
        wkTotCnt = 0;
        wkCfee1 = BigDecimal.ZERO;
        wkCfee4 = BigDecimal.ZERO;
        wkAmt = BigDecimal.ZERO;
        wkSkip = 0;

        //// 搬KPUTH-CODE給控制變數 & 報表
        // 028300    MOVE     KPUTH-CODE        TO       WK-CODE,
        // 028400                                        WK-RPT-CODE.
        wkCode = kputhCode;
        wkRptCode = kputhCode;

        //// 執行1000-CLMR-RTN，A.找CLMR、B.設定WK-SKIP、C.累計筆數、搬DB-CLMR-...給報表 & WK-...
        // 028500    PERFORM  1000-CLMR-RTN     THRU     1000-CLMR-EXIT.
        clmr();

        // 028600 SWITCH-CODE-EXIT.
    }

    private void switchBrno() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr switchBrno ....");
        // 028900 SWITCH-BRNO-RTN.

        //// 執行RPT-END-RTN，寫REPORTFL、REPORTFLA明細、REPORTFL表尾
        // 029000    PERFORM   RPT-END-RTN        THRU   RPT-END-EXIT.
        rptEnd();
        // 清變數
        // 029100    MOVE      0                  TO     WK-TOTCNT, WK-CFEE1,
        // 029200                                        WK-CFEE4, WK-AMT,
        // 029300                                        WK-SKIP.
        wkTotCnt = 0;
        wkCfee1 = BigDecimal.ZERO;
        wkCfee4 = BigDecimal.ZERO;
        wkAmt = BigDecimal.ZERO;
        wkSkip = 0;

        // 搬WK-PBRNO給WK-IPBRNO，for FIND NEXT BCTL
        // 029320    MOVE      WK-PBRNO           TO     WK-IPBRNO.
        wkIPbrno = wkPbrno;
        // 執行NODATA-BRNO-RTN，雖沒資料亦須寫REPORTFL表頭、表尾
        // 029340    PERFORM   NODATA-BRNO-RTN    THRU   NODATA-BRNO-EXIT.
        noDataBrno();

        //// 搬KPUTH-CODE、KPUTH-PBRNO給控制變數 & 報表
        // 029400    MOVE      KPUTH-CODE         TO     WK-CODE,
        // 029500                                        WK-RPT-CODE.
        // 029600    MOVE      KPUTH-PBRNO        TO     WK-PBRNO,
        // 029700                                        WK-RPT-BRNO.
        // 029750    MOVE      0                  TO     WK-SKIP.
        wkCode = kputhCode;
        wkRptCode = kputhCode;
        wkPbrno = kputhPbrno;
        wkRptBrno = kputhPbrno;
        wkSkip = 0;

        //// 執行1000-CLMR-RTN，A.找CLMR、B.設定WK-SKIP、C.累計筆數、搬DB-CLMR-...給報表 & WK-...
        // 029800    PERFORM   1000-CLMR-RTN      THRU   1000-CLMR-EXIT.
        clmr();

        //// 執行RPT-HEAD-RTN，寫REPORTFL表頭
        // 030100    PERFORM   RPT-HEAD-RTN       THRU   RPT-HEAD-EXIT.
        rptHead(PAGE_SEPARATOR);

        // 030200 SWITCH-BRNO-EXIT.
        // 030300    EXIT.
    }

    private void rptEnd() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr rptEnd ....");
        // 030500 RPT-END-RTN.

        // 若WK-SKIP=0，執行RPT-DTL-RTN，寫REPORTFL、REPORTFLA明細
        // 030600    IF        WK-SKIP           =      0
        if (wkSkip == 0) {
            // 030700      PERFORM RPT-DTL-RTN       THRU   RPT-DTL-EXIT
            rptDtl();
            // 030800    END-IF.
        }
        //
        //// 寫REPORTFL表尾
        //
        // 030900    MOVE      SPACES            TO     REPORT-LINE.
        // 030950    MOVE      " 以下空白 "      TO     REPORT-LINE(1:10).
        // 031000    WRITE     REPORT-LINE       AFTER  1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 以下空白 ", 10));
        fileC087Contents.add(sb.toString());

        // 031100    WRITE     REPORT-LINE       FROM   RPT087-DTL-END.
        // 012900 01   RPT087-DTL-END.
        // 013000  03  FILLER                             PIC X(72) VALUE ALL "-".
        // 013100  03  FILLER                             PIC X(05) VALUE " END ".
        // 013200  03  FILLER                             PIC X(73) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(
                formatUtil.padX(
                        reportUtil.makeGate("-", 72) + " END " + reportUtil.makeGate("-", 73),
                        150));
        fileC087Contents.add(sb.toString());

        // 031120    MOVE      SPACES            TO     REPORT-LINE.
        // 031140    WRITE     REPORT-LINE       AFTER  1.
        fileC087Contents.add("");

        // 031160    WRITE     REPORT-LINE       FROM   RPT087-END.
        // 013210 01   RPT087-END.
        // 013215  03  FILLER                             PIC X(05) VALUE SPACES.
        // 013220  03  FILLER                             PIC X(10) VALUE
        // 013225      " 經辦： ".
        // 013230  03  FILLER                             PIC X(30) VALUE SPACES.
        // 013240  03  FILLER                             PIC X(12) VALUE
        // 013245      " 營業主管： ".
        // 013250  03  FILLER                             PIC X(30) VALUE SPACES.
        // 013260  03  FILLER                             PIC X(12) VALUE
        // 013265      " 電金組長： ".
        // 013270  03  FILLER                             PIC X(51) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 經辦： ", 10));
        sb.append(formatUtil.padX(" ", 30));
        sb.append(formatUtil.padX(" 營業主管： ", 12));
        sb.append(formatUtil.padX(" ", 30));
        sb.append(formatUtil.padX(" 電金組長： ", 12));
        sb.append(formatUtil.padX(" ", 51));
        fileC087Contents.add(sb.toString());

        // 031200 RPT-END-EXIT.
    }

    private void noDataBrno() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr noDataBrno ....");

        // 031700 NODATA-BRNO-RTN.

        // FIND NEXT DB-BCTL-ACCESS AT DB-BCTL-BRNO > WK-IPBRNO，若有誤
        //  若找無資料，結束本段落

        // 031900    FIND  NEXT  DB-BCTL-ACCESS   OF   DB-BCTL-DDS
        // 032000    AT          DB-BCTL-BRNO     >    WK-IPBRNO
        // 032100          ON EXCEPTION
        Boolean isNotFound = true;
        for (int i = -1; i < 999; i++) {
            Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(i);
            if (bctl == null) {
                continue;
            }
            if (parse.isNumeric(bctl.getBrno())
                    && parse.string2Integer(bctl.getBrno()) > parse.string2Integer(wkIPbrno)) {
                isNotFound = false;
            }
            // 032200          IF DMSTATUS(NOTFOUND)
            // 032500             GO         TO   NODATA-BRNO-EXIT.
            if (isNotFound) {
                continue;
            }

            // 若DB-BCTL-BRNO > 800，結束本段落
            // 032700    IF  DB-BCTL-BRNO > 800
            // 032800        GO TO NODATA-BRNO-EXIT.
            if (parse.isNumeric(bctl.getBrno()) && parse.string2Integer(bctl.getBrno()) > 800) {
                return;
            }

            // 若DB-BCTL-BRNO < KPUTH-PBRNO，繼續下一步驟
            // 若DB-BCTL-BRNO >= KPUTH-PBRNO，結束本段落
            // 033200    IF  DB-BCTL-BRNO < KPUTH-PBRNO
            // 033300        CONTINUE
            // 033400    ELSE
            // 033500        GO TO NODATA-BRNO-EXIT.
            // 033600
            if (parse.isNumeric(bctl.getBrno())
                    && parse.string2Integer(bctl.getBrno()) < parse.string2Integer(kputhPbrno)) {

            } else {
                return;
            }

            // 若符合以下條件，不需處理，GO TO NODATA-BRNO-RTN，LOOP讀下一筆BCTL
            // 033700* 營業單位的才要列出來
            // 033800    IF  DB-BCTL-BRNO = 100 OR 102 OR 149 OR 152 OR 158 OR 166
            // 033900             OR 167 OR 168 OR 169 OR 177 OR 192 OR 196 OR 197
            // 034000             OR 199 OR 209 OR 212 OR 213 OR 214 OR 217 OR 219
            // 034100             OR 231 OR 234 OR 237 OR 250 OR 251 OR 254
            // 034200        GO TO NODATA-BRNO-RTN.
            List<Integer> lInt = new ArrayList<>();
            lInt.add(100);
            lInt.add(102);
            lInt.add(149);
            lInt.add(152);
            lInt.add(158);
            lInt.add(166);
            lInt.add(167);
            lInt.add(168);
            lInt.add(169);
            lInt.add(177);
            lInt.add(192);
            lInt.add(196);
            lInt.add(197);
            lInt.add(199);
            lInt.add(209);
            lInt.add(212);
            lInt.add(213);
            lInt.add(214);
            lInt.add(217);
            lInt.add(219);
            lInt.add(231);
            lInt.add(234);
            lInt.add(237);
            lInt.add(250);
            lInt.add(251);
            lInt.add(254);
            Boolean isBusiness = true;
            for (int pbrno : lInt) {
                if (parse.isNumeric(bctl.getBrno())
                        && parse.string2Integer(bctl.getBrno()) == pbrno) {
                    isBusiness = false;
                    break;
                }
            }
            if (!isBusiness) {
                continue;
            }

            //// 若符合以下條件
            ////  A.搬值、WK-SKIP設為1
            ////  B.執行RPT-HEAD-RTN，寫REPORTFL表頭
            ////  C.執行RPT-END-RTN，寫REPORTFL表尾
            // 034400    IF  DB-BCTL-BRLVL=6 OR DB-BCTL-BRNO=003 OR 005 OR 007 OR 236
            if (bctl.getBrlvl() == 6
                    || (parse.isNumeric(bctl.getBrno())
                            && (parse.string2Integer(bctl.getBrno()) == 3
                                    || parse.string2Integer(bctl.getBrno()) == 5
                                    || parse.string2Integer(bctl.getBrno()) == 7
                                    || parse.string2Integer(bctl.getBrno()) == 236))) {
                // 034500        MOVE      SPACES             TO     WK-CODE,
                // 034600                                            WK-RPT-CODE
                // 034700        MOVE      DB-BCTL-BRNO       TO     WK-PBRNO,
                // 034800                                            WK-RPT-BRNO
                // 034850        MOVE      1                  TO     WK-SKIP
                wkCode = "";
                wkRptCode = "";
                wkPbrno = bctl.getBrno();
                wkRptBrno = bctl.getBrno();
                wkSkip = 1;
                // 034900        PERFORM   RPT-HEAD-RTN       THRU   RPT-HEAD-EXIT
                rptHead(PAGE_SEPARATOR);
                // 035000        PERFORM   RPT-END-RTN        THRU   RPT-END-EXIT.
                rptEnd();
            }
            //// LOOP讀下一筆BCTL
            // 035200     GO TO NODATA-BRNO-RTN.
        }
        // 035500  NODATA-BRNO-EXIT.
    }

    private void mainLast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C087Lsnr mainLast ....");
        // 018500 0000-MAIN-LAST.
        // 018600     PERFORM   RPT-END-RTN        THRU RPT-END-EXIT.
        rptEnd();
        // 018620     MOVE  SPACES          TO      RPT087-185DTL.
        // 018640     MOVE  "小計 "        TO      RPT087-185DTL(1:6).
        // 018660     MOVE  WK-185AMT       TO      WK-RPT-185AMT.
        // 018680     WRITE REPORT-LINEA    FROM    RPT087-185DTL.
        // 013360 01   RPT087-185DTL.
        // 013361  03  WK-RPT-185PBRNO                    PIC 9(03).
        // 013362  03  WK-RPT-185X                        PIC X(01) VALUE "-".
        // 013363  03  WK-RPT-185CODE                     PIC X(06) VALUE SPACES.
        // 013364  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013365  03  WK-RPT-185CNAME                    PIC X(40) VALUE SPACES.
        // 013366  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013367  03  WK-RPT-185CNT                      PIC Z,ZZZ,ZZ9.
        // 013368  03  FILLER                             PIC X(04) VALUE SPACES.
        // 013369  03  WK-RPT-185CFEE1                    PIC Z,ZZ9.99.
        // 013370  03  FILLER                             PIC X(01) VALUE "/".
        // 013371  03  WK-RPT-185CFEE4                    PIC Z,ZZZ,ZZZ,ZZ9.99.
        // 013372  03  FILLER                             PIC X(04) VALUE SPACES.
        // 013373  03  WK-RPT-185AMT                      PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
        // 013374  03  FILLER                             PIC X(03) VALUE SPACES.
        // 013375  03  WK-RPT-185ACTNO                    PIC 9(12).
        // 013377  03  FILLER                             PIC X(05) VALUE SPACES.
        // 013379  03  WK-RPT-185NOTE                     PIC X(14) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 小計 ", 6));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" ", 40));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" ", 9));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" ", 16));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(reportUtil.customFormat("" + wk185Amt, "ZZZ,ZZZ,ZZZ,ZZ9.99"));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" ", 12));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" ", 14));
        fileC0874Contents.add(sb.toString());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C087Lsnr fileC0874Contents 1= {}",
                sb.toString());
    }

    private String getRptNote(String putname) {
        String note = " ";
        // 022300    IF       DB-CLMR-PUTNAME  = "X0199556"
        if ("X0199556".equals(putname)) {
            // 022400       MOVE    " 帳單代收 "      TO      WK-RPT-NOTE
            note = " 帳單代收 ";
        } else if ("X0111332".equals(putname)) {
            // 022500    ELSE IF  DB-CLMR-PUTNAME  = "X0111332"
            // 022600       MOVE    " 學雜費 "        TO      WK-RPT-NOTE
            note = " 學雜費 ";
        } else {
            // 022700    ELSE
            // 022800       MOVE      SPACES          TO      WK-RPT-NOTE
            note = " ";
            // 022900    END-IF.
        }
        return note;
    }

    private String getrocdate(int dateI) {
        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
