/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONVCLCMP2;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.dto.entities.ClmrbyCodeBus;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
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
@Component("CONVCLCMP2Lsnr")
@Scope("prototype")
public class CONVCLCMP2Lsnr extends BatchListenerCase<CONVCLCMP2> {

    @Autowired private ClmrService clmrService;
    @Autowired private ClcmpService clcmpService;
    @Autowired private TextFileUtil textFile;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private Map<String, String> labelMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTFN"; // 讀檔檔名
    private static final String FILE_OUTPUT_NAME = "CONVF"; // 檔名
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> file04301Contents; //  檔案內容
    private String PAGE_SEPARATOR = "\u000C";
    private CONVCLCMP2 event;
    private String wkFileName = "";
    private String wkPutfile = "";
    private String wkRptname = "";
    private String processDate = "";
    private String wkYYMMDD = "";
    private String wkYYYMMDD = "";
    private String wkPdate = "";
    private String wkFdate = "";
    private String wkCode = "";

    private String wkDateRpt = "";
    private String wkTimeRpt = "";
    private String wkAmtRpt = "";
    private String wkBalanceRtp = "";
    private int wkNotfound;
    private int wkPbrno = 0;
    private int wkRptCnt;
    private int wkTotpage;
    private String wkSumCnt1 = "";
    private String wkSumAmt1 = "";
    private String wkSumCnt2 = "";
    private String wkSumAmt2 = "";
    private String wkSumBalance = "";
    private String fd118001Ctl = "";
    private String fd118001Rcptid = "";
    private String fd118001Sitdate = "";
    private String fd118001Time = "";
    private String fd118001Amt = "";
    private String fd118001Balance = "";
    private String fd118001RcodeFirst = "";
    private String fd118001Cnt1 = "";
    private String fd118001Amt1 = "";
    private String fd118001Cnt2 = "";
    private String fd118001Amt2 = "";
    private String fd118001Amt3 = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONVCLCMP2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONVCLCMP2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr run()");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        wkFileName = textMap.get("DCNAME"); // TODO: 待確認BATCH參數名稱
        // 作業日期(民國年yyyymmdd)
        processDate =
                getrocdate(parse.string2Integer(labelMap.get("PROCESS_DATE"))); // 待中菲APPLE提供正確名稱
        // 018500     MOVE    FD-BHDATE-TBSDY     TO     WK-YYMMDD WK-YYYMMDD.
        // 018600     MOVE    WK-YYMMDD           TO     WK-FDATE.
        // 018650     MOVE    WK-FILENAME(5:6)    TO     WK-CODE.
        wkYYMMDD = processDate;
        wkYYYMMDD = processDate;
        wkFdate = wkYYMMDD.substring(1, 7);
        wkCode = wkFileName.substring(4, 10);
        // 018700     PERFORM DB-FINDCLMR-RTN   THRU     DB-FINDCLMR-EXIT.
        dbFindClmrRtn();

        // 018740     IF WK-NOTFOUND = 1 GO TO  0000-END-RTN.
        if (wkNotfound == 1) {
            return;
        }
        //// 設定檔名變數值
        //// WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        //// WK-PUTDIR  <-"DATA/CL/BH/PUTFN/"+WK-FDATE+"/"+WK-PUTFILE+"."
        //// WK-RPTNAME <-"BD/CL/BH/043/01."
        // 018900        MOVE WK-FILENAME          TO     WK-PUTFILE.
        // 019000        MOVE "BD/CL/BH/043/01."   TO     WK-RPTNAME.
        wkPutfile = wkFileName;
        wkRptname = "CL-BH-043-01";
        //// 設定檔名
        // 019500     CHANGE  ATTRIBUTE FILENAME  OF FD-11800  TO WK-PUTDIR.
        // 019600     CHANGE  ATTRIBUTE TITLE     OF REPORTFL  TO WK-RPTNAME.
        // 003000 FD  FD-11800
        // 003100  COPY "SYM/CL/BH/FD/118001.".
        //        inputFilePath = "DATA/CL/BH/PUTFN/" + wkFdate + PATH_SEPARATOR + wkPutfile;
        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTFN"
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;
        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + wkRptname;

        //// FD-11800檔案存在，執行0000-MAIN-RTN 主程式
        // 019700     IF  ATTRIBUTE  RESIDENT     OF FD-11800 IS = VALUE(TRUE)
        if (textFile.exists(inputFilePath)) {
            // 019800       PERFORM  0000-MAIN-RTN    THRU   0000-MAIN-EXIT.
            mainRtn();
        }
        // 019900 0000-END-RTN.
        //// 顯示訊息、關檔、結束程式
        // 020000     DISPLAY "SYM/CL/BH/CONVCLCMP/2 GENERATE RPT C043 OK".
        // 020100     CLOSE   FD-BHDATE  WITH SAVE.
        // 020150     CLOSE   BOTSRDB.
        try {
            textFile.writeFileContent(outputFilePath, file04301Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        // 020200     STOP RUN.
    }

    private void dbFindClmrRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr dbFindClmrRtn ...");
        // 028200 DB-FINDCLMR-RTN.

        //// WK-NOTFOUND找不到註記預設為0(0.否 1.是)

        // 028300     MOVE 0                        TO   WK-NOTFOUND.
        wkNotfound = 0;

        //// 將DB-CLMR-IDX1指標移至開始
        // 028400     SET   DB-CLMR-IDX1 TO BEGINNING.
        //// 依代收類別 FIND CLMR，若有誤，WK-NOTFOUND設為1

        // 028500     FIND  DB-CLMR-IDX1 OF DB-CLMR-DDS
        // 028550     AT    DB-CLMR-CODE = WK-CODE
        List<ClmrbyCodeBus> lClmr = clmrService.findbyCode(wkCode, 0, 1);
        // 028600          ON EXCEPTION
        // 028800             MOVE 1                TO   WK-NOTFOUND
        // 028900             GO TO DB-FINDCLMR-EXIT.
        if (Objects.isNull(lClmr)) {
            wkNotfound = 1;
            return;
        }
        //// WK-PBRNO <- WK-TITLE-LINE2'S變數
        // 029000* 找主辦行
        // 029700     MOVE DB-CLMR-PBRNO       TO     WK-PBRNO.
        wkPbrno = lClmr.get(0).getPbrno();

        // 029750**   DISPLAY "WK-PBRNO" WK-PBRNO.
        // 029800 DB-FINDCLMR-EXIT.
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr mainRtn ...");
        //// WK-RPT-CNT行數控制變數清0
        //// WK-TOTPAGE總頁次預設為1 <-WK-TITLE-LINE2'S變數
        // 020700     MOVE       0                 TO   WK-RPT-CNT.
        // 020800     MOVE       1                 TO   WK-TOTPAGE.
        wkRptCnt = 0;
        wkTotpage = 1;
        //// 執行RPT-TITLE-RTN，寫REPORTFL表頭
        // 020900     PERFORM    RPT-TITLE-RTN     THRU RPT-TITLE-EXIT.
        rptTitleRtn(PAGE_SEPARATOR);
        // 021000 0000-MAIN-LOOP.
        //// 循序讀取"DATA/CL/BH/PUTFN/..."，直到檔尾，跳到0000-MAIN-LAST
        // 021100     READ  FD-11800 AT  END    GO TO   0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03 118001-DTL	GROUP		明細資料
            //  05 118001-CTL	X(02)	CTL	 0-2
            //  05 118001-CODE	X(06)	代收類別 2-8
            //  05 118001-RCODE	REDEFINES 118001-CODE
            //   07 118001-RCODE-FIRST	X(02) 2-4
            //   07 118001-RCODE-END	X(04) 4-8
            //  05 118001-RCPTID	X(16)	繳款編號 8-24
            //  05 FILLER	X(10) 24-34
            //  05 118001-DATE	9(08)	代收日 34-42
            //  05 118001-TIME	9(06)	代收時間 42-48
            //  05 118001-CLLBR	9(03) 48-51
            //  05 118001-LMTDATE	9(08) 51-59
            //  05 118001-BALANCE	9(10) 59-69
            //  05 118001-USERDATA	GROUP	備註資料
            //   07 118001-USERDATA-1	X(30)	繳款人名稱 69-99
            //   07 118001-USERDATA-2	9(10)	餘額 99-109
            //  05 118001-SITDATE	9(08)	原代收日 109-117
            //  05 118001-TXTYPE	X(01)	帳務別 117-118
            //  05 118001-AMT	9(12)	繳費金額 118-130
            //  05 118001-FILLER	X(30)
            // 03 118001-SUM 	REDEFINES 118001-DTL	彙總資料
            //  05 FILLER	X(02) 0-2
            //  05 FILLER	X(06) 2-8
            //  05 118001-SDATE	9(08)	挑檔起日 8-16
            //  05 118001-EDATE	9(08)	挑檔迄日 16-24
            //  05 118001-CNT1	9(06)	存入總筆數 24-30
            //  05 118001-AMT1	9(13)	存入總金額 30-43
            //  05 118001-CNT2	9(06)	提取總筆數 43-49
            //  05 118001-AMT2	9(13)	提取總金額 49-62
            //  05 118001-AMT3	9(13)	總餘額 62-75
            //  05 FILLER	X(85)
            fd118001Ctl = detail.substring(0, 2);
            fd118001Rcptid = detail.substring(8, 24);
            fd118001Sitdate = detail.substring(109, 117);
            fd118001Time = detail.substring(42, 48);
            fd118001Amt = detail.substring(118, 130);
            fd118001Balance = detail.substring(59, 69);
            fd118001RcodeFirst = detail.substring(2, 4);
            fd118001Cnt1 = detail.substring(24, 30);
            fd118001Amt1 = detail.substring(30, 43);
            fd118001Cnt2 = detail.substring(43, 49);
            fd118001Amt2 = detail.substring(49, 62);
            fd118001Amt3 = detail.substring(62, 75);
            //// WK-RPT-CNT行數控制加1
            // 021200     ADD   1                      TO   WK-RPT-CNT.
            wkRptCnt = wkRptCnt + 1;
            // 021300*  報表換頁控制
            // 021400     IF  WK-RPT-CNT               >    45
            if (wkRptCnt > 45) {
                // 021500         MOVE    SPACE            TO   REPORT-LINE
                // 021600         WRITE   REPORT-LINE      AFTER PAGE
                // 021700         PERFORM RPT-TITLE-RTN    THRU RPT-TITLE-EXIT
                rptTitleRtn(PAGE_SEPARATOR);
                // 021800         ADD     1                TO   WK-TOTPAGE
                // 021900         MOVE    0                TO   WK-RPT-CNT.
                wkTotpage = wkTotpage + 1;
                wkRptCnt = 0;
                // 022000*
            }
            //// 118001-CTL=11明細資料
            ////  A.執行RPT-DTL-RTN，寫REPORTFL報表明細
            ////  B.GO TO 0000-MAIN-LOOP，LOOP讀下一筆PUTFN

            // 022100     IF  118001-CTL               =    11
            if ("11".equals(fd118001Ctl)) {
                // 022200         PERFORM RPT-DTL-RTN      THRU RPT-DTL-EXIT
                rptDtlRtn();

                if (cnt == lines.size()) {
                    rptLastRtn();
                }
                // 022300         GO TO 0000-MAIN-LOOP.
                continue;
            }

            //// 118001-CTL=12彙總資料
            ////  A.搬118001-REC給WK-118001-SUM	<-WK-118001-...沒用，多餘???
            ////  B.GO TO 0000-MAIN-LOOP，LOOP讀下一筆PUTFN
            // 022400     IF  118001-CTL               =    12
            if ("12".equals(fd118001Ctl)) {
                // 022500         MOVE   118001-REC        TO   WK-118001-SUM
                // 022600         GO TO 0000-MAIN-LOOP.
                if (cnt == lines.size()) {
                    rptLastRtn();
                }
                continue;
            }
            // 022700 0000-MAIN-LAST.
            if (cnt == lines.size()) {
                //// 執行RPT-LAST-RTN，寫REPORTFL表尾
                //
                // 022800     PERFORM  RPT-LAST-RTN        THRU  RPT-LAST-EXIT.
                rptLastRtn();
            }
        }
        //// 關閉檔案FD-11800
        //
        // 022900     CLOSE    FD-11800.
        // 023000 0000-MAIN-EXIT.
    }

    private void rptLastRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr rptLastRtn ...");
        // 026500 RPT-LAST-RTN.
        //// 寫REPORTFL表尾 (WK-TOTAL-LINE1~WK-TOTAL-LINE3)

        // 026600     MOVE       SPACE             TO      REPORT-LINE.
        // 026700     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 013800 01 WK-TITLE-LINE4.
        // 013900    02 FILLER                PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 100));
        file04301Contents.add(sb.toString());

        // 026800     MOVE       118001-CNT1       TO      WK-SUM-CNT1.
        // 026900     MOVE       118001-AMT1       TO      WK-SUM-AMT1.
        wkSumCnt1 = fd118001Cnt1;
        wkSumAmt1 = fd118001Amt1;
        // 027000     MOVE       SPACE             TO      REPORT-LINE.
        // 027100     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE1.
        // 015000 01 WK-TOTAL-LINE1.
        // 015100    02 FILLER                PIC X(14) VALUE " 存入總筆數： ".
        // 015200    02 WK-SUM-CNT1           PIC ZZZ,ZZ9.
        // 015300    02 FILLER                PIC X(14) VALUE " 存入總金額： ".
        // 015400    02 WK-SUM-AMT1           PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 存入總筆數： ", 14));
        sb.append(reportUtil.customFormat(wkSumCnt1, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" 存入總金額： ", 14));
        sb.append(reportUtil.customFormat(wkSumAmt1, "Z,ZZZ,ZZZ,ZZ9"));
        file04301Contents.add(sb.toString());

        // 027200     MOVE       118001-CNT2       TO      WK-SUM-CNT2.
        // 027300     MOVE       118001-AMT2       TO      WK-SUM-AMT2.
        wkSumCnt2 = fd118001Cnt2;
        wkSumAmt2 = fd118001Amt2;
        // 027400     MOVE       SPACE             TO      REPORT-LINE.
        // 027500     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE2.
        // 015500 01 WK-TOTAL-LINE2.
        // 015600    02 FILLER                PIC X(14) VALUE " 提取總筆數： ".
        // 015700    02 WK-SUM-CNT2           PIC ZZZ,ZZ9.
        // 015800    02 FILLER                PIC X(14) VALUE " 提取總金額： ".
        // 015900    02 WK-SUM-AMT2           PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 提取總筆數： ", 14));
        sb.append(reportUtil.customFormat(wkSumCnt2, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" 提取總金額： ", 14));
        sb.append(reportUtil.customFormat(wkSumAmt2, "Z,ZZZ,ZZZ,ZZ9"));
        file04301Contents.add(sb.toString());

        // 027600     MOVE       118001-AMT3       TO      WK-SUM-BALANCE.
        wkSumBalance = fd118001Amt3;
        // 027700     MOVE       SPACE             TO      REPORT-LINE.
        // 027800     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE3.
        // 016000 01 WK-TOTAL-LINE3.
        // 016100    02 FILLER                PIC X(10) VALUE " 總餘額： ".
        // 016200    02 FILLER                PIC X(25) VALUE SPACE.
        // 016300    02 WK-SUM-BALANCE        PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總餘額： ", 10));
        sb.append(formatUtil.padX("", 25));
        sb.append(reportUtil.customFormat(wkSumBalance, "Z,ZZZ,ZZZ,ZZ9"));
        file04301Contents.add(sb.toString());

        // 027900 RPT-LAST-EXIT.
    }

    private void rptDtlRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr rptDtlRtn ...");
        // 024800 RPT-DTL-RTN.

        //// 寫REPORTFL報表明細 (WK-DETAIL-LINE)
        //// 搬118001-REC... 給 WK-DETAIL-LINE

        // 024900     MOVE       SPACE             TO      WK-DETAIL-LINE
        // 025000     MOVE       118001-RCPTID     TO      WK-RCPTID-RPT
        String wkRcptidRpt = fd118001Rcptid;

        //// 執行CLCMP-FIND-RTN，取得繳款人名稱
        // 025100     PERFORM    CLCMP-FIND-RTN    THRU    CLCMP-FIND-EXIT
        String wkCnameRpt = clcmpFindRtn();
        // 025200     MOVE       118001-SITDATE    TO      WK-DATE-RPT
        // 025300     MOVE       118001-TIME       TO      WK-TIME-RPT
        // 025400     MOVE       118001-AMT        TO      WK-AMT-RPT
        // 025500     MOVE       118001-BALANCE    TO      WK-BALANCE-RTP

        wkDateRpt = fd118001Sitdate;
        wkTimeRpt = fd118001Time;
        wkAmtRpt = fd118001Amt;
        wkBalanceRtp = fd118001Balance;
        //// 118001-RCODE-FIRST=118001-CODE(1:2)
        //
        // 025600     IF         118001-RCODE-FIRST =       "11"
        String wkActRpt = "";
        if ("11".equals(fd118001RcodeFirst)) {
            // 025700       MOVE     " 存入 "          TO      WK-ACT-RPT
            wkActRpt = " 存入 ";
        } else {
            // 025800     ELSE
            // 025900       MOVE     " 提取 "          TO      WK-ACT-RPT.
            wkActRpt = " 提取 ";
        }
        // 026000     MOVE       SPACES            TO      REPORT-LINE.
        // 026100     WRITE      REPORT-LINE       FROM    WK-DETAIL-LINE.
        // 014000 01 WK-DETAIL-LINE.
        // 014100    02 WK-RCPTID-RPT                PIC X(16).
        // 014200    02 WK-CNAME-RPT                 PIC X(30).
        // 014300    02 WK-DATE-RPT                  PIC Z99/99/99.
        // 014400    02 FILLER                       PIC X(01).
        // 014500    02 WK-TIME-RPT                  PIC 999999.
        // 014600    02 FILLER                       PIC X(02).
        // 014700    02 WK-ACT-RPT                   PIC X(06).
        // 014800    02 WK-AMT-RPT                   PIC Z,ZZZ,ZZZ,ZZ9.
        // 014900    02 WK-BALANCE-RTP               PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkRcptidRpt, 16));
        sb.append(formatUtil.padX(wkCnameRpt, 30));
        sb.append(reportUtil.customFormat(wkDateRpt, "Z99/99/99"));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat(wkTimeRpt, "999999"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkActRpt, 6));
        sb.append(reportUtil.customFormat(wkAmtRpt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(reportUtil.customFormat(wkBalanceRtp, "Z,ZZZ,ZZZ,ZZ9"));
        file04301Contents.add(sb.toString());

        // 026200 RPT-DTL-EXIT.
    }

    private String clcmpFindRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr clcmpFindRtn ...");
        String wkCnameRpt = "";
        // 030200 CLCMP-FIND-RTN.
        //// WK-CLCMP-RCPTID="1"+118001-RCPTID(2:15)
        // 030220     MOVE  118001-RCPTID     TO  WK-CLCMP-RCPTID .
        // 030240     MOVE  "1"               TO  WK-CLCMP-RCPTID-1.
        String wkClcmpRcptid = "1" + fd118001Rcptid.substring(1, 16);

        //// 將DB-CLCMP-IDX1指標移至開始
        // 030300     SET     DB-CLCMP-IDX1        TO   BEGINNING.
        //// KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        //// 依代收類別、銷帳號碼 FIND DB-CLCMP-IDX1收付比對檔，若有誤
        ////   若NOTFOUND，GO TO CLCMP-FIND-EXIT，結束本段落
        ////   若其他錯誤，沒HANDLE???
        //
        // 030400     FIND NEXT DB-CLCMP-IDX1 AT DB-CLCMP-CODE = WK-CODE
        // 030500                          AND DB-CLCMP-RCPTID = WK-CLCMP-RCPTID
        ClcmpBus tClcmp = clcmpService.findById(new ClcmpId(wkCode, wkClcmpRcptid));
        // 030600       ON EXCEPTION
        if (Objects.isNull(tClcmp)) {
            // 030700       IF DMSTATUS(NOTFOUND)
            // 031200         GO TO CLCMP-FIND-EXIT.
            return wkCnameRpt;
        }
        //// 繳款人名稱
        // 031300     MOVE  DB-CLCMP-PNAME         TO   WK-CNAME-RPT    .
        wkCnameRpt = tClcmp.getPname();
        // 031500 CLCMP-FIND-EXIT.
        return wkCnameRpt;
    }

    private void rptTitleRtn(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr rptTitleRtn ...");
        // 023300 RPT-TITLE-RTN.

        //// 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE4)
        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023500     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        // 010500 01 WK-TITLE-LINE1.
        // 010600    02 FILLER                       PIC X(29) VALUE SPACE.
        // 010700    02 TITLE-LABEL                  PIC X(42)
        // 010800              VALUE " 全行代理收付系統－虛擬分戶當日交易明細表 ".
        // 010900    02 FILLER                       PIC X(17) VALUE SPACE.
        // 011000    02 FILLER                       PIC X(19)
        // 011100                              VALUE "FORM : C043".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 29));
        sb.append(formatUtil.padX(" 全行代理收付系統－虛擬分戶當日交易明細表 ", 42));
        sb.append(formatUtil.padX(" ", 17));
        sb.append(formatUtil.padX("FORM : C043", 19));
        file04301Contents.add(sb.toString());

        // 023600     MOVE       SPACES            TO      REPORT-LINE.
        // 023700     WRITE      REPORT-LINE       AFTER   1.
        file04301Contents.add("");

        // 023800     MOVE       WK-YYYMMDD        TO      WK-PDATE.
        wkPdate = wkYYYMMDD;
        // 023900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        // 011200 01 WK-TITLE-LINE2.
        // 011300    02 FILLER                       PIC X(10)
        // 011400                              VALUE " 分行別： ".
        // 011500    02 WK-PBRNO                     PIC 9(03).
        // 011600    02 FILLER                       PIC X(05) VALUE SPACE.
        // 011700    02 FILLER                       PIC X(12)
        // 011800                              VALUE " 印表日期： ".
        // 011900    02 WK-PDATE                     PIC Z99/99/99.
        // 012000    02 FILLER                       PIC X(38) VALUE SPACE.
        // 012100    02 FILLER                       PIC X(10) VALUE " 總頁次： ".
        // 012200    02 WK-TOTPAGE                   PIC 9(04).
        // 012300    02 FILLER                       PIC X(15) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.pad9("" + wkPbrno, 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(reportUtil.customFormat(wkPdate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 38));
        sb.append(formatUtil.padX(" 總頁次： ", 10));
        sb.append(formatUtil.pad9("" + wkTotpage, 4));
        sb.append(formatUtil.padX("", 15));
        file04301Contents.add(sb.toString());

        // 024000     MOVE       SPACES            TO      REPORT-LINE.
        // 024100     WRITE      REPORT-LINE       AFTER   1.
        file04301Contents.add("");

        // 024200     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        // 012400 01 WK-TITLE-LINE3.
        // 012500    02 FILLER                PIC X(10) VALUE " 分戶帳號 ".
        // 012600    02 FILLER                PIC X(07) VALUE SPACE.
        // 012700    02 FILLER                PIC X(14) VALUE " 分戶帳號名稱 ".
        // 012800    02 FILLER                PIC X(16) VALUE SPACE.
        // 012900    02 FILLER                PIC X(06) VALUE " 日期 ".
        // 013000    02 FILLER                PIC X(03) VALUE SPACE.
        // 013100    02 FILLER                PIC X(06) VALUE " 時間 ".
        // 013200    02 FILLER                PIC X(02) VALUE SPACE.
        // 013300    02 FILLER                PIC X(06) VALUE " 動作 ".
        // 013400    02 FILLER                PIC X(07) VALUE SPACE.
        // 013500    02 FILLER                PIC X(06) VALUE " 金額 ".
        // 013600    02 FILLER                PIC X(07) VALUE SPACE.
        // 013700    02 FILLER                PIC X(06) VALUE " 餘額 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分戶帳號： ", 10));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 分戶帳號名稱： ", 10));
        sb.append(formatUtil.padX("", 16));
        sb.append(formatUtil.padX(" 日期 ", 6));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 時間 ", 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 動作 ", 6));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 金額 ", 6));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 餘額 ", 6));
        file04301Contents.add(sb.toString());

        // 024300     MOVE       SPACES            TO      REPORT-LINE.
        // 024400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 013800 01 WK-TITLE-LINE4.
        // 013900    02 FILLER                PIC X(100) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 100));
        file04301Contents.add(sb.toString());

        // 024500 RPT-TITLE-EXIT.
    }

    //        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVCLCMP2Lsnr mainRtn ...");
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
