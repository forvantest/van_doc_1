/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONVCLCMP1;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
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
@Component("CONVCLCMP1Lsnr")
@Scope("prototype")
public class CONVCLCMP1Lsnr extends BatchListenerCase<CONVCLCMP1> {

    @Autowired private ClcmpService clcmpService;
    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTFN"; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME = "CONVF"; // 檔名
    private static final String CONVF_DATA = "DATA";
    private String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC118001Contents; //  檔案內容
    private CONVCLCMP1 event;
    private String wkFileName = "";
    private String processDate = "";
    private String wkYYMMDD = "";
    private String wkFdate = "";
    private String wkCdate = "";
    private String wkPutfile = "";
    private String wkConvfile = "";
    private String wkCode = "";
    private String wkForwardRcptid = "";
    private String wkForwardRcptid1 = "";
    private String wkForwardRcptid2 = "";
    private BigDecimal wkForwardAmt = BigDecimal.ZERO;
    private BigDecimal wkBalance = BigDecimal.ZERO;
    private String wkCldtlCode = "";
    private String wkClDtlRcptid = "";
    private String wkCldtlCode2 = "";
    private BigDecimal wkNtbsdSumAmt1 = BigDecimal.ZERO;
    private BigDecimal wkNtbsdSumAmt2 = BigDecimal.ZERO;
    private BigDecimal wk118001Balance = BigDecimal.ZERO;

    private int wk118001Cnt1 = 0;
    private BigDecimal wk118001Amt1 = BigDecimal.ZERO;
    private int wk118001Cnt2 = 0;
    private BigDecimal wk118001Amt2 = BigDecimal.ZERO;
    private BigDecimal wk118001Amt3 = BigDecimal.ZERO;
    private int wkCtlFlag = 0;

    private String wk118001Ctl = "";
    private String wk118001SumCtl = "";
    private String wk118001Sdate = "";
    private String wk118001Edate = "";
    private String wk118001Code = "";
    private String wk118001Code1 = "";
    private String wk118001Code2 = "";
    private String wk118001Code3 = "";
    private String wk118001Rcptid = "";
    private String wk118001Rcptid1 = "";
    private String wk118001Rcptid2 = "";
    private String wk118001Date = "";
    private String wk118001Time = "";
    private String wk118001Cllbr = "";
    private String wk118001Userdata = "";
    private String wk118001Userdata1 = "";
    private String wk118001Userdata2 = "";
    private String wk118001Sitdate = "";
    private String wk118001Txtype = "";
    private String wk118001Amt = "";
    private String wkClcmpCode = "";
    private String wkClcmpCode2 = "";
    private String wkClcmpRcptid = "";
    private String wkClcmpRcptid1 = "";
    private String putfnCtl = "";
    private String putfnBdate = "";
    private String putfnEdate = "";
    private String putfnCode = "";
    private String putfnRcptid = "";
    private String putfnDate = "";
    private String putfnTime = "";
    private String putfnCllbr = "";
    private String putfnUserdata = "";
    private String putfnSitdate = "";
    private String putfnTxtype = "";
    private String putfnAmt = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONVCLCMP1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONVCLCMP1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr run()");
        init(event);
        if (textFile.exists(inputFilePath)) {
            main();
        }
    }

    private void init(CONVCLCMP1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr init ... ");
        this.event = event;

        // 設定本營業日、檔名日期、檔名變數值、代收類別變數
        // WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        // WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        // WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        // WK-CONVFILE PIC X(10) <--WK-CONVDIR'S變數
        // WK-PUTDIR  <-"DATA/CL/BH/PUTFN/"+WK-FDATE+"/"+WK-PUTFILE+"."
        // WK-CONVDIR <-"DATA/CL/BH/CONVF/"+WK-CDATE+"/"+WK-CONVFILE+"."
        // 011900     MOVE    FD-BHDATE-TBSDY     TO     WK-YYMMDD.
        // 012000     MOVE    WK-YYMMDD           TO     WK-FDATE,WK-CDATE.
        // 012100     MOVE    WK-FILENAME         TO     WK-PUTFILE,WK-CONVFILE.
        // 012500     MOVE WK-FILENAME(5:6)       TO     WK-CODE.
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        // 作業日期(民國年yyyymmdd)
        processDate =
                getrocdate(parse.string2Integer(labelMap.get("PROCESS_DATE"))); // 待中菲APPLE提供正確名稱
        wkFileName = textMap.get("DCNAME"); // TODO: 待確認BATCH參數名稱

        wkYYMMDD = processDate;
        wkFdate = wkYYMMDD.substring(1, 7);
        wkCdate = wkYYMMDD.substring(1, 7);
        wkPutfile = wkFileName;
        wkConvfile = wkFileName;
        wkCode = wkFileName.substring(4, 10);
        //// 設定檔名
        // 012600     CHANGE  ATTRIBUTE FILENAME  OF FD-PUTFN   TO WK-PUTDIR.
        // 012700     CHANGE  ATTRIBUTE FILENAME  OF FD-118001 TO WK-CONVDIR.
        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;

        outputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + FILE_OUTPUT_NAME
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + wkConvfile;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr main ... ");
        // 013500 0000-MAIN-RTN.
        //// 開啟檔案

        // 013600     OPEN       INPUT             FD-PUTFN.
        // 013700     OPEN       OUTPUT            FD-118001.
        // 013800     OPEN       INQUIRY           BOTSRDB.
        // 013900     MOVE       0            TO   WK-CTL-FLAG.
        wkCtlFlag = 0;

        // INITIAL
        // 014050     PERFORM    INITIAL-RTN       THRU INITIAL-EXIT.
        initial();

        // 014100 0000-MAIN-LOOP.
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到0000-MAIN-LAST
        // 014200     READ  FD-PUTFN  AT  END    GO TO  0000-MAIN-LAST.
        // 014300
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03  PUTFN-CTL	9(02)	0-2
            // 03  PUTFN-CTL-R	REDEFINES PUTFN-CTL
            //  05 PUTFN-CTL1	9(01)	0-1
            //  05 PUTFN-CTL2	9(01)	1-2
            // 03  PUTFN-CODE	X(06)	代收類別 2-8
            // 03  PUTFN-DATA.	GROUP
            //  05 PUTFN-RCPTID	X(26)	銷帳號碼 8-34
            //  05 PUTFN-DATE	9(08)	代收日 34-42
            //  05 PUTFN-TIME	9(06)	代收時間 42-48
            //  05 PUTFN-CLLBR	9(03)	代收行 48-51
            //  05 PUTFN-LMTDATE	9(08)	繳費期限 51-59
            //  05 PUTFN-OLDAMT	9(10)	 59-69
            //  05 PUTFN-USERDATA	X(40)	備註資料 69-109
            //  05 PUTFN-SITDATE	9(08)	原代收日 109-117
            //  05 PUTFN-TXTYPE	X(01)	帳務別 117-118
            //  05 PUTFN-AMT	9(12)	繳費金額 118-130
            //  05 PUTFN-FILLER	X(30)	 130-160
            // 03  PUTFN-DATA-R
            //	REDEFINES PUTFN-DATA
            //  05 PUTFN-BDATE	9(08)	挑檔起日 8-16
            //  05 PUTFN-EDATE	9(08)	挑檔迄日 16-24
            //  05 PUTFN-TOTCNT	9(06)	彙總筆數 24-30
            //  05 PUTFN-TOTAMT	9(13)	彙總金額 30-43
            //  05 FILLER	X(117)	     43-160
            // 03  PUTFN-DATA-NODATA	REDEFINES PUTFN-DATA
            //  05 PUTFN-NODATA	X(26)	8-34
            //  05 PUTFN-FILLER1	X(126)	34-160
            putfnCtl = detail.substring(0, 2);
            putfnBdate = detail.substring(8, 16);
            putfnEdate = detail.substring(16, 24);
            putfnCode = detail.substring(2, 8);
            putfnRcptid = detail.substring(8, 34);
            putfnDate = detail.substring(34, 42);
            putfnTime = detail.substring(42, 48);
            putfnCllbr = detail.substring(48, 51);
            putfnUserdata = detail.substring(69, 109);
            putfnSitdate = detail.substring(109, 117);
            putfnTxtype = detail.substring(117, 118);
            putfnAmt = detail.substring(118, 130);
            //// PUTFN-CTL=11,21 明細資料
            ////  A.WK-CTL-FLAG設為0
            ////  B.執行MOVE-REC-RTN，搬PUTFN-...到WK-118001-REC...
            ////  C.執行FIND-CLCMP-RTN，計算當日此虛擬帳號之餘額
            ////  D.執行SWH-RCPTID-RTN，計算餘額
            ////  E.執行USERDATA-IN-RTN，寫檔FD-118001(DTL)
            ////  F.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-PUTFN
            // 014400     IF  PUTFN-CTL                 =    11 OR 21
            if ("11".equals(putfnCtl) || "12".equals(putfnCtl)) {
                // 014450         MOVE    0                TO   WK-CTL-FLAG
                wkCtlFlag = 0;
                // 014500         PERFORM MOVE-REC-RTN     THRU MOVE-REC-EXIT
                moveRec();
                // 014700         PERFORM FIND-CLCMP-RTN   THRU FIND-CLCMP-EXIT
                findClcmp();
                // 014800         PERFORM SWH-RCPTID-RTN   THRU SWH-RCPTID-EXIT
                swhRcptid();
                // 014900         PERFORM USERDATA-IN-RTN  THRU USERDATA-IN-EXIT
                userdataIn();
                // 015000         GO TO 0000-MAIN-LOOP.
            }

            //// PUTFN-CTL=12,22 & WK-CTL-FLAG NOT = 0，跳到0000-MAIN-LOOP，LOOP讀下一筆FD-PUTFN

            // 015020     IF  ( PUTFN-CTL = 12 OR 22 ) AND ( WK-CTL-FLAG NOT = 0 )
            if (("12".equals(putfnCtl) || "22".equals(putfnCtl)) && (wkCtlFlag != 0)) {
                // 015060         GO TO 0000-MAIN-LOOP.
                continue;
            }
            //// PUTFN-CTL=12,22 彙總資料
            ////  A.WK-CTL-FLAG設為1
            ////  B.執行CLDTL-SUM-RTN，依代收類別找出大於當日之所有提取或所有存入之金額
            ////  C.執行CLCMP-SUM-RTN，依代收類別FIND NEXT DB-CLCMP-IDX1，累計總餘額
            ////  D.執行FILE-SUM-RTN，寫檔FD-118001(SUM)
            ////  E.執行INITIAL-RTN，INITIAL
            ////  F.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-PUTFN

            // 015100     IF  PUTFN-CTL                 =    12 OR 22
            if ("12".equals(putfnCtl) || "22".equals(putfnCtl)) {
                // 015140         MOVE    1                TO   WK-CTL-FLAG
                // 015150         MOVE    PUTFN-CTL         TO   WK-118001-SUM-CTL
                // 015200         MOVE    PUTFN-BDATE       TO   WK-118001-SDATE
                // 015300         MOVE    PUTFN-EDATE       TO   WK-118001-EDATE
                wkCtlFlag = 1;
                wk118001SumCtl = putfnCtl;
                wk118001Sdate = putfnBdate;
                wk118001Edate = putfnEdate;
                // 015320         PERFORM CLDTL-SUM-RTN    THRU CLDTL-SUM-EXIT
                cldtlSum();
                // 015340         PERFORM CLCMP-SUM-RTN    THRU CLCMP-SUM-EXIT
                clcmpSum();
                // 015360         PERFORM FILE-SUM-RTN     THRU FILE-SUM-EXIT
                fileSum();
                // 015370         PERFORM INITIAL-RTN      THRU INITIAL-EXIT
                initial();
                // 015400         GO TO 0000-MAIN-LOOP.
                continue;
            }
            if (cnt == lines.size()) {
                // 015500 0000-MAIN-LAST.
                // 關檔
                // 015800     CLOSE    FD-PUTFN             WITH  SAVE.
                // 015900     CHANGE   ATTRIBUTE  FILENAME OF FD-118001 TO WK-PUTDIR.
                // 016000     CLOSE    FD-118001           WITH  SAVE.
                // 016100     CLOSE    BOTSRDB.
                try {
                    textFile.writeFileContent(outputFilePath, fileC118001Contents, CHARSET);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
            }
        }
        // 016200 0000-MAIN-EXIT.
    }

    private void initial() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr initial ... ");
        // 034900 INITIAL-RTN.
        // 035000     MOVE       SPACE          TO   WK-FORWARD-RCPTID,
        // 035050                                    WK-118001-REC    ,
        // 035070                                    WK-118001-SUM    .
        // 035100     MOVE       0              TO   WK-RPT-CNT       ,
        // 035200                                    WK-BALANCE       ,
        // 035210                                    WK-118001-CNT1   ,
        // 035220                                    WK-118001-AMT1   ,
        // 035230                                    WK-118001-CNT2   ,
        // 035240                                    WK-118001-AMT2   ,
        // 035250                                    WK-118001-AMT3   .
        wkForwardRcptid = "";
        wkForwardRcptid1 = "";
        wkForwardRcptid2 = "";
        wkBalance = BigDecimal.ZERO;
        wk118001Cnt1 = 0;
        wk118001Amt1 = BigDecimal.ZERO;
        wk118001Cnt2 = 0;
        wk118001Amt2 = BigDecimal.ZERO;
        wk118001Amt3 = BigDecimal.ZERO;

        // 035260 INITIAL-EXIT.
    }

    private void moveRec() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr moveRec ... ");
        // 036170 MOVE-REC-RTN.

        //// 搬PUTFN-...到WK-118001-REC...
        // 036270     MOVE  PUTFN-CTL            TO    WK-118001-CTL     .
        // 036370     MOVE  PUTFN-CODE           TO    WK-118001-CODE    .
        // 036470     MOVE  PUTFN-RCPTID         TO    WK-118001-RCPTID  .
        // 036570     MOVE  PUTFN-DATE           TO    WK-118001-DATE    .
        // 036670     MOVE  PUTFN-TIME           TO    WK-118001-TIME    .
        // 036720     MOVE  PUTFN-CLLBR          TO    WK-118001-CLLBR   .
        // 036770     MOVE  PUTFN-USERDATA       TO    WK-118001-USERDATA.
        // 036870     MOVE  PUTFN-SITDATE        TO    WK-118001-SITDATE .
        // 036970     MOVE  PUTFN-TXTYPE         TO    WK-118001-TXTYPE  .
        // 037070     MOVE  PUTFN-AMT            TO    WK-118001-AMT     .
        wk118001Ctl = putfnCtl;
        wk118001Code = putfnCode;
        wk118001Code1 = wk118001Code.substring(0, 1);
        wk118001Code2 = wk118001Code.substring(1, 2);
        wk118001Code3 = wk118001Code.substring(2, 6);
        wk118001Rcptid = putfnRcptid;
        wk118001Rcptid1 = wk118001Rcptid.substring(0, 1);
        wk118001Rcptid2 = wk118001Rcptid.substring(1, 16);
        wk118001Date = putfnDate;
        wk118001Time = putfnTime;
        wk118001Cllbr = putfnCllbr;
        wk118001Userdata = putfnUserdata;
        wk118001Userdata1 = wk118001Userdata.substring(0, 30);
        wk118001Userdata2 = putfnUserdata.substring(30, 40);
        wk118001Sitdate = putfnSitdate;
        wk118001Txtype = putfnTxtype;
        wk118001Amt = putfnAmt;

        // 037170 MOVE-REC-EXIT.

    }

    private void findClcmp() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr moveRec ... ");
        // 016500 FIND-CLCMP-RTN.

        //// WK-FORWARD-...上一筆的資料
        //// 首筆或虛擬帳號不同時，往下一步驟執行
        //// 虛擬帳號相同時，結束本段落
        // 016510     IF   (WK-FORWARD-RCPTID      =    SPACES               )
        // 016520       OR (WK-118001-RCPTID-2 NOT =    WK-FORWARD-RCPTID-2  )
        if (wkForwardRcptid.isEmpty() || !wk118001Rcptid2.equals(wkForwardRcptid2)) {
            // 016530       NEXT  SENTENCE
        } else {
            // 016540     ELSE
            // 016550       GO TO  FIND-CLCMP-EXIT.
            return;
        }

        //// 若PUTF-RCPTID(1:1)="1"，搬PUTFN-... 給WK-CLCMP-...
        //// 其他
        ////   A.WK-CLCMP-CODE=PUTFN-CODE(1:1)+"1"+PUTFN-CODE(3:4)
        ////   B.WK-CLCMP-RCPTID="1"+PUTFN-RCPTID(2:15)

        // 016600     IF     WK-118001-RCPTID-1    =    "1"
        if ("1".equals(wk118001Rcptid1)) {
            // 016700        MOVE WK-118001-CODE       TO   WK-CLCMP-CODE
            // 016800        MOVE WK-118001-RCPTID     TO   WK-CLCMP-RCPTID
            wkClcmpCode = wk118001Code;
            wkClcmpCode2 = wkClcmpCode.substring(1, 2);
            wkClcmpRcptid = wk118001Rcptid;
            wkClcmpRcptid1 = wkClcmpRcptid.substring(0, 1);
        } else {
            // 016900     ELSE
            // 017000        MOVE WK-118001-CODE       TO   WK-CLCMP-CODE
            // 017100        MOVE WK-118001-RCPTID     TO   WK-CLCMP-RCPTID
            // 017200        MOVE "1"                  TO   WK-CLCMP-CODE-2
            // 017300        MOVE "1"                  TO   WK-CLCMP-RCPTID-1.
            wkClcmpCode = wk118001Code.substring(0, 1) + "1" + wk118001Code.substring(2, 6);
            wkClcmpRcptid = "1" + wk118001Rcptid.substring(1, 16);
            wkClcmpCode2 = "1";
            wkClcmpRcptid1 = "1";
        }
        // 017400*
        //// 將DB-CLCMP-IDX1指標移至開始
        // 017500     SET     DB-CLCMP-IDX1        TO   BEGINNING.

        //// KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        //// 依代收類別、銷帳號碼 FIND DB-CLCMP-IDX1收付比對檔，若有誤，GO TO 0000-END-RTN，關檔、結束程式
        //
        // 017600     FIND DB-CLCMP-IDX1 AT DB-CLCMP-CODE   = WK-CLCMP-CODE
        // 017700                       AND DB-CLCMP-RCPTID = WK-CLCMP-RCPTID
        // 017800       ON EXCEPTION
        // 017900       IF DMSTATUS(NOTFOUND)
        // 018000          GO TO 0000-END-RTN
        // 018100       ELSE
        // 018200          GO TO 0000-END-RTN.
        ClcmpBus tClcmp = clcmpService.findById(new ClcmpId(wkClcmpCode, wkClcmpRcptid));
        if (Objects.isNull(tClcmp)) {
            return; // TODO:結束程式
        }

        //// 計算當日此虛擬帳號之餘額
        // 018220     MOVE    DB-CLCMP-AMT         TO   WK-BALANCE.
        wkBalance = tClcmp.getAmt();

        // 018240     PERFORM FIND-CLDTL-RTN       THRU FIND-CLDTL-EXIT.
        findCldtl(tClcmp);
        // 018260     MOVE    WK-BALANCE           TO   WK-118001-BALANCE.
        wk118001Balance = wkBalance;

        // 018300 FIND-CLCMP-EXIT.
    }

    private void findCldtl(ClcmpBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr findCldtl ... ");
        // 025000 FIND-CLDTL-RTN.
        // 025100* 當日有交易之虛擬分戶中找出大於當日之提取或存入之金額
        // 025150* 必須先加當日提款，再減當日存入，比照更正規則
        // 025200     PERFORM  FIND-DTL2-RTN       THRU  FIND-DTL2-EXIT.
        findDtl2(tClcmp);
        // 025300     PERFORM  FIND-DTL1-RTN       THRU  FIND-DTL1-EXIT.
        findDtl1(tClcmp);
        // 025400 FIND-CLDTL-EXIT.
    }

    private void findDtl1(ClcmpBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr findDtl1 ... ");
        // 025700 FIND-DTL1-RTN.
        // 025800*  計算出大於本日之存入交易之金額
        // 025900     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 026000     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        wkCldtlCode = tClcmp.getCode();
        wkCldtlCode2 = wkCldtlCode.substring(1, 2);
        wkClDtlRcptid = tClcmp.getRcptid();
        //// 將DB-CLDTL-IDX1指標移至開始

        // 026100     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 026200 FIND-DTL1-LOOP.

        //// KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        //// 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO FIND-DTL1-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        // 026300     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 026400                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkClDtlRcptid, 0, 0, Integer.MAX_VALUE);
        // 026500       ON EXCEPTION
        // 026600       IF DMSTATUS(NOTFOUND)
        // 026700          GO TO FIND-DTL1-EXIT
        // 026800       ELSE
        // 026900          GO TO 0000-MAIN-RTN. //TODO:如何判斷NOTFOUND
        if (Objects.isNull(lCldtl)) {
            return;
        }
        for (CldtlbyCodeRcptidHcodeBus tClDtl : lCldtl) {
            //// 大於當日之交易，從累計金額 WK-BALANCE減掉
            //// GO TO FIND-DTL1-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND
            // 027000     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
            if (tClDtl.getEntdy() > parse.string2Integer(processDate)) {
                // 027100       SUBTRACT DB-CLDTL-AMT   FROM WK-BALANCE
                wkBalance = wkBalance.subtract(tClDtl.getAmt());
                // 027200       GO TO FIND-DTL1-LOOP
                continue;
            } else {
                // 027300     ELSE
                // 027400       GO TO FIND-DTL1-LOOP.
                continue;
            }
        }

        // 027500 FIND-DTL1-EXIT.
    }

    private void findDtl2(ClcmpBus tClcmp) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr findDtl2 ... ");
        // 027800 FIND-DTL2-RTN.
        // 027900*  計算出大於本日之提取交易之金額
        //// WK-CLDTL-CODE=DB-CLCMP-CODE(1:1)+"2"+DB-CLCMP-CODE(3:4)
        //// WK-CLDTL-RCPTID="2"+DB-CLCMP-RCPTID(2:15)
        // 028000     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 028100     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        // 028200     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        // 028300     MOVE "2"                  TO   WK-CLDTL-RCPTID-1.
        wkCldtlCode = tClcmp.getCode().substring(0, 1) + "2" + tClcmp.getCode().substring(2, 6);
        wkCldtlCode2 = "2";
        wkClDtlRcptid = "2" + tClcmp.getRcptid().substring(1, 16);

        //// 將DB-CLDTL-IDX1指標移至開始
        // 028400     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 028500 FIND-DTL2-LOOP.
        //// KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        //// 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO FIND-DTL2-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        // 028600     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 028700                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        // 028800       ON EXCEPTION
        // 028900       IF DMSTATUS(NOTFOUND)
        // 029000          GO TO FIND-DTL2-EXIT  TODO:如何判斷NOTFOUND
        // 029100       ELSE
        // 029200          GO TO 0000-MAIN-RTN.
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkClDtlRcptid, 0, 0, Integer.MAX_VALUE);
        if (Objects.isNull(lCldtl)) {
            return;
        }
        for (CldtlbyCodeRcptidHcodeBus tClDtl : lCldtl) {
            //// 大於當日之交易，累計金額 WK-BALANCE
            //// GO TO FIND-DTL2-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND
            // 029300     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
            if (tClDtl.getEntdy() > parse.string2Integer(processDate)) {
                // 029400       ADD   DB-CLDTL-AMT      TO   WK-BALANCE
                wkBalance = wkBalance.add(tClDtl.getAmt());
                // 029500       GO TO FIND-DTL2-LOOP
                continue;
            } else {
                // 029600     ELSE
                // 029700       GO TO FIND-DTL2-LOOP.
                continue;
            }
        }
        // 029800 FIND-DTL2-EXIT.
    }

    private void swhRcptid() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr swhRcptid ... ");
        // 018600 SWH-RCPTID-RTN.

        //// WK-FORWARD-...上一筆的資料
        //// 首筆時
        ////  A.保留資料至WK-FORWARD-RCPTID,AMT
        ////  B.執行PANME-RTN，準備繳款人名稱
        ////  C.搬WK-BALANCE至WK-118001-BALANCE
        ////  D.結束本段落
        if (wkForwardRcptid.isEmpty()) {
            // 018700     IF WK-FORWARD-RCPTID         =     SPACE
            // 018800        MOVE    WK-118001-RCPTID  TO    WK-FORWARD-RCPTID
            // 018900        MOVE    WK-118001-AMT     TO    WK-FORWARD-AMT
            // 019000        PERFORM PANME-RTN         THRU  PNAME-EXIT
            // 019100        MOVE    WK-BALANCE        TO    WK-118001-BALANCE
            // 019300        GO TO   SWH-RCPTID-EXIT.
            wkForwardRcptid = wk118001Rcptid;
            wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
            wkForwardRcptid2 = wkForwardRcptid.substring(1, 16);
            wkForwardAmt = parse.string2BigDecimal(wk118001Amt);
            panme();
            wk118001Balance = wkBalance;
            return;
        }
        //// 虛擬帳號不同時
        ////  A.保留資料至WK-FORWARD-RCPTID,AMT
        ////  B.執行PANME-RTN，準備繳款人名稱
        ////  C.搬WK-BALANCE至WK-118001-BALANCE
        ////  D.結束本段落

        // 019500     IF   WK-FORWARD-RCPTID-2     NOT = WK-118001-RCPTID-2
        if (!wkForwardRcptid2.equals(wk118001Rcptid2)) {
            // 019600        MOVE    WK-118001-RCPTID  TO    WK-FORWARD-RCPTID
            // 019700        MOVE    WK-118001-AMT     TO    WK-FORWARD-AMT
            // 019800        PERFORM PANME-RTN         THRU  PNAME-EXIT
            // 019900        MOVE    WK-BALANCE        TO    WK-118001-BALANCE
            // 020100        GO TO   SWH-RCPTID-EXIT
            wkForwardRcptid = wk118001Rcptid;
            wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
            wkForwardRcptid2 = wkForwardRcptid.substring(1, 16);
            wkForwardAmt = parse.string2BigDecimal(wk118001Amt);
            panme();
            wk118001Balance = wkBalance;
            return;
        } else {
            //// 虛擬帳號相同時
            // 020200     ELSE

            ////  WK-FORWARD-RCPTID-1="1"
            ////   A.從累計金額 WK-BALANCE減掉WK-FORWARD-AMT
            ////   B.保留資料至WK-FORWARD-AMT
            ////   C.執行PANME-RTN，準備繳款人名稱
            ////   D.搬WK-BALANCE至WK-118001-BALANCE
            ////   E.保留資料至WK-FORWARD-RCPTID
            //
            // 020300          IF WK-FORWARD-RCPTID-1  =     "1"
            if ("1".equals(wkForwardRcptid1)) {
                // 020400            SUBTRACT WK-FORWARD-AMT FROM WK-BALANCE
                // 020500            MOVE WK-118001-AMT    TO    WK-FORWARD-AMT
                // 020600            PERFORM PANME-RTN     THRU  PNAME-EXIT
                // 020700            MOVE WK-BALANCE       TO    WK-118001-BALANCE
                // 020800            MOVE WK-118001-RCPTID TO    WK-FORWARD-RCPTID
                wkForwardAmt = parse.string2BigDecimal(wk118001Amt);
                panme();
                wk118001Balance = wkBalance;
                wkForwardRcptid = wk118001Rcptid;
                wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
                wkForwardRcptid2 = wkForwardRcptid.substring(1, 16);
            } else {
                // 020900          ELSE
                //
                ////  WK-FORWARD-RCPTID-1="2"
                ////   A.累加WK-FORWARD-AMT到WK-BALANCE
                ////   B.保留資料至WK-FORWARD-AMT
                ////   C.執行PANME-RTN，準備繳款人名稱
                ////   D.搬WK-BALANCE至WK-118001-BALANCE
                ////   E.保留資料至WK-FORWARD-RCPTID
                //
                // 021000            ADD  WK-FORWARD-AMT   TO    WK-BALANCE
                // 021100            MOVE WK-118001-AMT    TO    WK-FORWARD-AMT
                // 021200            PERFORM PANME-RTN     THRU  PNAME-EXIT
                // 021300            MOVE WK-BALANCE       TO    WK-118001-BALANCE
                // 021400            MOVE WK-118001-RCPTID TO    WK-FORWARD-RCPTID.
                wkBalance = wkBalance.add(wkForwardAmt);
                wkForwardAmt = parse.string2BigDecimal(wk118001Amt);
                panme();
                wk118001Balance = wkBalance;
                wkForwardRcptid = wk118001Rcptid;
                wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
                wkForwardRcptid2 = wkForwardRcptid.substring(1, 16);
            }
        }
        // 021600 SWH-RCPTID-EXIT.
    }

    private void panme() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr panme ... ");
        // COBOL註記掉了

        // 035470 PANME-RTN.
        // 繳款人名稱
        // 035670*    MOVE  DB-CLCMP-PNAME      TO    WK-118001-USERDATA-1 .
        // 035870 PNAME-EXIT.
    }

    private void userdataIn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr userdataIn ... ");
        // 021900 USERDATA-IN-RTN.

        //// PUTFN-CODE(2:1)="1"，累計存入筆數、金額
        //// PUTFN-CODE(2:1)="2"，累計提取筆數、金額

        // 022000     IF      WK-118001-CODE-2     =    "1"
        if ("1".equals(wk118001Code2)) {
            // 022100        ADD  1                    TO   WK-118001-CNT1
            // 022200        ADD  WK-118001-AMT        TO   WK-118001-AMT1
            wk118001Cnt1 = wk118001Cnt1 + 1;
            wk118001Amt1 = wk118001Amt1.add(parse.string2BigDecimal(wk118001Amt));
        } else {
            // 022300     ELSE
            // 022400        ADD  1                    TO   WK-118001-CNT2
            // 022500        ADD  WK-118001-AMT        TO   WK-118001-AMT2.
            wk118001Cnt2 = wk118001Cnt2 + 1;
            wk118001Amt2 = wk118001Amt2.add(parse.string2BigDecimal(wk118001Amt));
            // 022600*
        }
        //// 寫檔FD-118001(DTL)
        // 022700     WRITE   118001-REC           FROM WK-118001-REC.
        // 004500  01 WK-118001-REC.
        // 004600    03 WK-118001-CTL                   PIC 9(02).
        // 004700    03 WK-118001-CODE.
        // 004720      05 WK-118001-CODE-1              PIC X(01).
        // 004740      05 WK-118001-CODE-2              PIC X(01).
        // 004760      05 WK-118001-CODE-3              PIC X(04).
        // 004800    03 WK-118001-RCPTID.
        // 004900      05 WK-118001-RCPTID-1            PIC X(01).
        // 005000      05 WK-118001-RCPTID-2            PIC X(15).
        // 005050    03 FILLER                          PIC X(10).
        // 005100    03 WK-118001-DATE                  PIC 9(08).
        // 005200    03 WK-118001-TIME                  PIC 9(06).
        // 005250    03 WK-118001-CLLBR                 PIC 9(03).
        // 005300    03 FILLER                          PIC X(08).
        // 005350    03 WK-118001-BALANCE               PIC 9(10).
        // 005400    03 WK-118001-USERDATA.
        // 005500      05 WK-118001-USERDATA-1          PIC X(30).
        // 005600      05 WK-118001-USERDATA-2          PIC X(10).
        // 005700    03 WK-118001-SITDATE               PIC 9(08).
        // 005750    03 WK-118001-TXTYPE                PIC X(01).
        // 005800    03 WK-118001-AMT                   PIC 9(12).
        // 005900    03 FILLER                          PIC X(30).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(wk118001Ctl, 2));
        sb.append(formatUtil.padX(wk118001Code1, 1));
        sb.append(formatUtil.padX(wk118001Code2, 1));
        sb.append(formatUtil.padX(wk118001Code3, 4));
        sb.append(formatUtil.padX(wk118001Rcptid1, 1));
        sb.append(formatUtil.padX(wk118001Rcptid2, 15));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.pad9(wk118001Date, 8));
        sb.append(formatUtil.pad9(wk118001Time, 6));
        sb.append(formatUtil.pad9(wk118001Cllbr, 3));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.pad9("" + wk118001Balance, 10));
        sb.append(formatUtil.padX(wk118001Userdata1, 30));
        sb.append(formatUtil.padX(wk118001Userdata2, 10));
        sb.append(formatUtil.pad9(wk118001Sitdate, 8));
        sb.append(formatUtil.padX(wk118001Txtype, 1));
        sb.append(formatUtil.pad9(wk118001Amt, 12));
        sb.append(formatUtil.padX("", 30));
        fileC118001Contents.add(sb.toString());

        // 022800 USERDATA-IN-EXIT.
    }

    private void cldtlSum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr cldtlSum ... ");
        // 030100 CLDTL-SUM-RTN.
        // 030200* 找出大於當日之所有提取或所有存入之金額
        // 030300     PERFORM  SUM-DTL1-RTN        THRU  SUM-DTL1-EXIT.
        sumDtl1();
        // 030400     PERFORM  SUM-DTL2-RTN        THRU  SUM-DTL2-EXIT.
        sumDtl2();
        // 030500 CLDTL-SUM-EXIT.

    }

    private void clcmpSum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr clcmpSum ... ");
        // 023100 CLCMP-SUM-RTN.
        //
        //// 將DB-CLCMP-IDX1指標移至開始
        //
        // 023200     SET     DB-CLCMP-IDX1        TO   BEGINNING.
        // 023300 CLCMP-SUM-LOOP.
        //
        //// KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        //// 依代收類別 FIND NEXT DB-CLCMP-IDX1收付比對檔，若有誤
        ////  若NOTFOUND，
        ////   A.扣除大於當日之資料
        ////   B.GO TO CLCMP-SUM-EXIT，結束本段落
        ////  其他，GO TO 0000-MAIN-EXIT，關檔、結束程式
        //
        // 023400     FIND NEXT DB-CLCMP-IDX1 AT DB-CLCMP-CODE = WK-CODE

        List<ClcmpbyCodeBus> lClcmp = clcmpService.findbyCode(wkClcmpCode, 0, Integer.MAX_VALUE);
        // 023500       ON EXCEPTION
        // 023600       IF DMSTATUS(NOTFOUND)
        // 023620         SUBTRACT WK-NTBSD-SUM-AMT1 FROM WK-118001-AMT3
        // 023640         ADD      WK-NTBSD-SUM-AMT2 TO   WK-118001-AMT3
        // 023700         GO TO CLCMP-SUM-EXIT
        // 023800       ELSE
        // 023900         GO TO 0000-MAIN-EXIT. //TODO:找下一筆資料
        if (Objects.isNull(lClcmp)) {
            wk118001Amt3 = wk118001Amt3.subtract(wkNtbsdSumAmt1);
            wk118001Amt3 = wk118001Amt3.add(wkNtbsdSumAmt2);
            return;
        }
        for (ClcmpbyCodeBus tClcmp : lClcmp) {
            //// 依代收類別累計總餘額
            //
            // 024000     ADD   DB-CLCMP-AMT           TO   WK-118001-AMT3.
            wk118001Amt3 = wk118001Amt3.add(tClcmp.getAmt());
            //// LOOP讀下一筆CLCMP，直到NOTFOUND
            //
            // 024100     GO TO CLCMP-SUM-LOOP.
        }
        // 024200 CLCMP-SUM-EXIT.
    }

    private void fileSum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr fileSum ... ");
        // 024500 FILE-SUM-RTN.

        //// 寫檔FD-118001(SUM)

        // 024600     WRITE   118001-REC           FROM WK-118001-SUM.
        // 005950  01  WK-118001-SUM.
        // 006000      03 WK-118001-SUM-CTL             PIC 9(02).
        // 006200      03 FILLER                        PIC X(06).
        // 006300      03 WK-118001-SDATE               PIC 9(08).
        // 006400      03 WK-118001-EDATE               PIC 9(08).
        // 006500      03 WK-118001-CNT1                PIC 9(06).
        // 006600      03 WK-118001-AMT1                PIC 9(13).
        // 006700      03 WK-118001-CNT2                PIC 9(06).
        // 006800      03 WK-118001-AMT2                PIC 9(13).
        // 006900      03 WK-118001-AMT3                PIC 9(13).
        // 007000      03 FILLER                        PIC X(85).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(wk118001SumCtl, 2));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.pad9(wk118001Sdate, 8));
        sb.append(formatUtil.pad9(wk118001Edate, 8));
        sb.append(formatUtil.pad9("" + wk118001Cnt1, 6));
        sb.append(formatUtil.pad9("" + wk118001Amt1, 13));
        sb.append(formatUtil.pad9("" + wk118001Cnt2, 6));
        sb.append(formatUtil.pad9("" + wk118001Amt2, 13));
        sb.append(formatUtil.pad9("" + wk118001Amt3, 13));
        sb.append(formatUtil.padX("", 85));
        fileC118001Contents.add(sb.toString());

        // 024700 FILE-SUM-EXIT.
    }

    private void sumDtl1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr sumDtl1 ... ");
        // 030800 SUM-DTL1-RTN.
        // 030900* 找出大於當日之所有存入之金額
        // 031000     MOVE WK-118001-CODE       TO   WK-CLDTL-CODE.
        // 031100     MOVE "1"                  TO   WK-CLDTL-CODE-2.
        wkCldtlCode = wk118001Code.substring(0, 1) + "1" + wk118001Code.substring(2, 6);
        wkCldtlCode2 = "1";

        // 031150*    DISPLAY "WK-CLDTL-CODE-1111= "WK-CLDTL-CODE.
        //
        //// 將DB-CLDTL-IDX3指標移至開始
        //
        // 031200     SET  DB-CLDTL-IDX3        TO   BEGINNING.
        // 031300 SUM-DTL1-LOOP.
        //
        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 代收類別&代收日>本營業日 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO SUM-DTL1-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        //
        // 031400     FIND NEXT DB-CLDTL-IDX3 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 031500                            AND DB-CLDTL-DATE   > FD-BHDATE-TBSDY
        List<CldtlByCodeEntdyRangeBus> lCldtl =
                cldtlService.findByCodeEntdyRange(
                        wkCldtlCode,
                        parse.string2Integer(processDate),
                        99991231,
                        0,
                        0,
                        Integer.MAX_VALUE);
        // 031600       ON EXCEPTION
        // 031700       IF DMSTATUS(NOTFOUND)
        // 031800          GO TO SUM-DTL1-EXIT
        // 031900       ELSE
        // 032000          GO TO 0000-MAIN-RTN.
        // 032100
        if (Objects.isNull(lCldtl)) {
            return;
        }
        for (CldtlByCodeEntdyRangeBus tClDtl : lCldtl) {
            //// 大於本日之交易，累計存入之金額 WK-NTBSD-SUM-AMT1

            // 032200     ADD  DB-CLDTL-AMT         TO   WK-NTBSD-SUM-AMT1.
            wkNtbsdSumAmt1 = wkNtbsdSumAmt1.add(tClDtl.getAmt());

            //// LOOP讀下一筆CLDTL，直到NOTFOUND
            // 032500     GO TO SUM-DTL1-LOOP.
        }
        // 032600 SUM-DTL1-EXIT.
    }

    private void sumDtl2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP1Lsnr sumDtl2 ... ");
        // 032900 SUM-DTL2-RTN.
        // 033000* 找出大於當日之所有提取之金額
        // 033100     MOVE WK-118001-CODE       TO   WK-CLDTL-CODE.
        // 033150     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        wkCldtlCode = wk118001Code.substring(0, 1) + "2" + wk118001Code.substring(2, 6);
        wkCldtlCode2 = "2";
        // 033170**   DISPLAY "WK-CLDTL-CODE-2222= "WK-CLDTL-CODE.
        //
        //// 將DB-CLDTL-IDX3指標移至開始
        //
        // 033200     SET  DB-CLDTL-IDX3        TO   BEGINNING.
        // 033300 SUM-DTL2-LOOP.
        //
        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 代收類別&代收日>本營業日 FIND NEXT 收付明細檔 ，若有誤
        ////  A.若不存在，GO TO SUM-DTL2-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        //
        // 033400     FIND NEXT DB-CLDTL-IDX3 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 033500                            AND DB-CLDTL-DATE   > FD-BHDATE-TBSDY
        List<CldtlByCodeEntdyRangeBus> lCldtl =
                cldtlService.findByCodeEntdyRange(
                        wkCldtlCode,
                        parse.string2Integer(processDate),
                        99991231,
                        0,
                        0,
                        Integer.MAX_VALUE);
        // 033600       ON EXCEPTION
        // 033700       IF DMSTATUS(NOTFOUND)
        // 033800          GO TO SUM-DTL2-EXIT
        // 033900       ELSE
        // 034000          GO TO 0000-MAIN-RTN. //TODO:找下一筆
        // 034100
        if (Objects.isNull(lCldtl)) {
            return;
        }
        for (CldtlByCodeEntdyRangeBus tClDtl : lCldtl) {
            //// 大於本日之交易，累計提取之金額 WK-NTBSD-SUM-AMT2

            // 034200     ADD  DB-CLDTL-AMT         TO   WK-NTBSD-SUM-AMT2
            wkNtbsdSumAmt2 = wkNtbsdSumAmt2.add(tClDtl.getAmt());
            //// LOOP讀下一筆CLDTL，直到NOTFOUND
            // 034500     GO TO SUM-DTL2-LOOP.
        }
        // 034600 SUM-DTL2-EXIT.
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
