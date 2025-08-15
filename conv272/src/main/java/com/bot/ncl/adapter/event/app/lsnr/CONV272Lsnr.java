/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV272;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.File111981;
import com.bot.ncl.util.fileVo.FileSum111981;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
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
@Component("CONV272Lsnr")
@Scope("prototype")
public class CONV272Lsnr extends BatchListenerCase<CONV272> {
    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private CONV272 event;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private File111981 file111981;
    @Autowired private FileSum111981 fileSum111981;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private Map<String, String> textMap;
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private StringBuilder sb = new StringBuilder();
    private String wkPutfile; // 讀檔檔名 = wkConvfile
    private String wkPutdir;
    private String wkRptDir;
    private String isNotConv;
    private List<String> reportFileContents = new ArrayList<>();
    private String processDate;
    private String tbsdy;
    private String wkFdate;
    private String wkYYMMDD;
    private String wkFilename;
    private String wkPbrno;
    private String wkRptname;
    private String wkPdate;
    private int processDateInt = 0;
    private int wkRptCnt;
    private int wkTotpage;
    private String _111981Ctl;
    private String wkRcptidRpt;
    private String wkCnameRpt;
    private String wkDateRpt;
    private String wkTimeRpt;
    private String wkAmtRpt;
    private String wkBalanceRpt;
    private String _111981RcodeFirst;
    private String wkActRpt;
    private String wkSumCnt1;
    private String wkSumAmt1;
    private String wkSumCnt2;
    private String wkSumAmt2;
    private String wkSumBalance;

    @Override
    public void onApplicationEvent(CONV272 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV272Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV272 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV272Lsnr run()");

        init(event);
        // 019700     IF  ATTRIBUTE  RESIDENT     OF FD-11198 IS = VALUE(TRUE)
        if (!"Y".equals(isNotConv)) { // 若CONV271未轉檔則不處理
            if (textFile.exists(wkPutdir)) {
                // 019800       PERFORM  0000-MAIN-RTN    THRU   0000-MAIN-EXIT.
                _0000_Main();
                writeFile();
            }
        }
        // 020000     DISPLAY "SYM/CL/BH/CONV27/2 GENERATE DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV27/2 GENERATE DATA/CL/BH/PUTF OK");
        // 020100     CLOSE   FD-BHDATE  WITH SAVE.

        batchResponse();
        // 020200     STOP RUN.
    }

    private void init(CONV272 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV272Lsnr init()");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate = labelMap.get("PROCESS_DATE"); // todo: 待確認BATCH參數名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);
        wkFilename = textMap.get("WK_FILENAME");
        isNotConv = textMap.get("ISNOTCONV");
        // 018500     MOVE    FD-BHDATE-TBSDY     TO     WK-YYMMDD.
        // 018600     MOVE    WK-YYMMDD           TO     WK-FDATE.
        wkYYMMDD = formatUtil.pad9(processDate, 8).substring(2);
        wkFdate = wkYYMMDD;

        //// 設定檔名變數值、分行別變數、REPORTFL
        //// WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        //// WK-PBRNO	<-WK-TITLE-LINE2'S變數
        //// WK-PUTDIR  <-"DATA/CL/BH/PUTF/"+WK-FDATE+"/"+WK-PUTFILE+"."
        //
        // 018700     IF      WK-FILENAME         =      "27X1111981"
        if ("27X1111981".equals(wkFilename)) {
            // 018800        MOVE "27X1111981"        TO     WK-PUTFILE
            // 018900        MOVE "085"               TO     WK-PBRNO
            // 019000        MOVE "BD/CL/BH/043/01."   TO     WK-RPTNAME
            wkPutfile = "27X1111981";
            wkPbrno = "085";
            wkRptname = "CL-BH-043-01";
            // 019100     ELSE IF WK-FILENAME         =      "27X1111801"
        } else if ("27X1111801".equals(wkFilename)) {
            // 019200        MOVE "27X1111801"        TO     WK-PUTFILE
            // 019300        MOVE "003"               TO     WK-PBRNO
            // 019400        MOVE "BD/CL/BH/043/02."   TO     WK-RPTNAME.
            wkPutfile = "27X1111801";
            wkPbrno = "003";
            wkRptname = "CL-BH-043-02";
        }
        wkRptDir = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + wkRptname;
        // WK-PUTDIR  <-"DATA/CL/BH/PUTF/"+WK-FDATE+"/"+WK-PUTFILE+"."
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + "PUTF"
                        + File.separator
                        + wkFdate;
        wkPutdir = putDir + File.separator + wkPutfile;
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
    }

    private void _0000_Main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV272Lsnr _0000_Main");
        // 020400 0000-MAIN-RTN.
        //// 開啟檔案
        // 020500     OPEN       INPUT             FD-11198.
        // 020600     OPEN       OUTPUT            REPORTFL.

        //// INITIAL 行數控制變數、頁數變數
        // 020700     MOVE       0                 TO   WK-RPT-CNT.
        // 020800     MOVE       1                 TO   WK-TOTPAGE.
        wkRptCnt = 0;
        wkTotpage = 1;
        //// 執行RPT-TITLE-RTN，寫REPORTFL表頭
        // 020900     PERFORM    RPT-TITLE-RTN     THRU RPT-TITLE-EXIT.
        rpt_Title();
        // 021000 0000-MAIN-LOOP.

        //// 循序讀取FD-11198，直到檔尾，跳到0000-MAIN-LAST
        // 021100     READ  FD-11198 AT  END    GO TO   0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境

            _111981Ctl = detail.substring(0, 2);
            //// 行數控制變數 加 1
            // 021200     ADD   1                      TO   WK-RPT-CNT.
            wkRptCnt++;
            //// WK-RPT-CNT行數控制變數>45時
            ////  A.跳頁
            ////  B.執行RPT-TITLE-RTN，寫REPORTFL表頭
            ////  C.頁數加1、搬0給行數控制變數
            // 021300*  報表換頁控制
            // 021400     IF  WK-RPT-CNT               >    45
            if (wkRptCnt > 45) {
                // 021500         MOVE    SPACE            TO   REPORT-LINE
                // 021600         WRITE   REPORT-LINE      AFTER PAGE
                reportFileContents.add("\u000C"); // 換頁符號
                // 021700         PERFORM RPT-TITLE-RTN    THRU RPT-TITLE-EXIT
                rpt_Title();
                // 021800         ADD     1                TO   WK-TOTPAGE
                // 021900         MOVE    0                TO   WK-RPT-CNT.
                wkTotpage++;
                wkRptCnt = 0;
                // 022000*
            }
            //// 若111981-CTL=11明細資料
            ////  A.執行RPT-DTL-RTN，寫REPORTFL明細 (WK-DETAIL-LINE)
            ////  B.LOOP讀下一筆FD-11198
            // 022100     IF  111981-CTL               =    11
            if ("11".equals(_111981Ctl)) {
                text2VoFormatter.format(detail, file111981);
                // 022200         PERFORM RPT-DTL-RTN      THRU RPT-DTL-EXIT
                rpt_Dtl();
                // 022300         GO TO 0000-MAIN-LOOP.
                continue;
            }
            //// 若111981-CTL=12彙總資料
            ////  A.搬111981-REC給WK-111981-SUM，後續沒用，多餘???
            ////  B.LOOP讀下一筆FD-11198
            // 022400     IF  111981-CTL               =    12
            if ("12".equals(_111981Ctl)) {
                text2VoFormatter.format(detail, fileSum111981);
                // 022500         MOVE   111981-REC        TO   WK-111981-SUM
                // 022600         GO TO 0000-MAIN-LOOP.
                continue;
            }
        }
        // 022700 0000-MAIN-LAST.
        //// 執行RPT-LAST-RTN，寫REPORTFL表尾
        // 022800     PERFORM  RPT-LAST-RTN        THRU  RPT-LAST-EXIT.
        rpt_last();
        //// 關閉檔案FD-11198
        //
        // 022900     CLOSE    FD-11198.
        // 023000 0000-MAIN-EXIT.
    }

    private void rpt_Dtl() {
        // 024800 RPT-DTL-RTN.
        //// 寫REPORTFL明細 (WK-DETAIL-LINE)
        // 024900     MOVE       SPACE             TO      WK-DETAIL-LINE
        // 025000     MOVE       111981-RCPTID     TO      WK-RCPTID-RPT
        // 025100     MOVE       111981-USERDATA-1 TO      WK-CNAME-RPT
        // 025200     MOVE       111981-SITDATE    TO      WK-DATE-RPT
        // 025300     MOVE       111981-TIME       TO      WK-TIME-RPT
        // 025400     MOVE       111981-AMT        TO      WK-AMT-RPT
        // 025500     MOVE       111981-USERDATA-2 TO      WK-BALANCE-RTP
        wkRcptidRpt = file111981.getRcptid();
        wkCnameRpt = file111981.getUserdata1();
        wkDateRpt = file111981.getSitdate();
        wkTimeRpt = file111981.getTime();
        wkAmtRpt = file111981.getAmt();
        wkBalanceRpt = file111981.getUserdata2();
        _111981RcodeFirst = file111981.getRcodefirst();
        // 025600     IF         111981-RCODE-FIRST =       "11"
        if ("11".equals(_111981RcodeFirst)) {
            // 025700       MOVE     " 存入 "          TO      WK-ACT-RPT
            wkActRpt = " 存入 ";
            // 025800     ELSE
        } else {
            // 025900       MOVE     " 提取 "          TO      WK-ACT-RPT.
            wkActRpt = " 提取 ";
        }
        // 026000     MOVE       SPACES            TO      REPORT-LINE.
        // 026100     WRITE      REPORT-LINE       FROM    WK-DETAIL-LINE.
        reportFileContents.add(wk_Detail_Line());
        // 026200 RPT-DTL-EXIT.
    }

    private void rpt_last() {
        // 026500 RPT-LAST-RTN.
        //// 寫REPORTFL表尾 (WK-TOTAL-LINE1~WK-TOTAL-LINE3)
        // 026600     MOVE       SPACE             TO      REPORT-LINE.
        // 026700     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        reportFileContents.add(wk_Title_Line4());
        // 026800     MOVE       111981-CNT1       TO      WK-SUM-CNT1.
        // 026900     MOVE       111981-AMT1       TO      WK-SUM-AMT1.
        wkSumCnt1 = fileSum111981.getCnt1();
        wkSumAmt1 = fileSum111981.getAmt1();
        // 027000     MOVE       SPACE             TO      REPORT-LINE.
        // 027100     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE1.
        reportFileContents.add(wk_Total_Line1());
        // 027200     MOVE       111981-CNT2       TO      WK-SUM-CNT2.
        // 027300     MOVE       111981-AMT2       TO      WK-SUM-AMT2.
        wkSumCnt2 = fileSum111981.getCnt2();
        wkSumAmt2 = fileSum111981.getAmt2();
        // 027400     MOVE       SPACE             TO      REPORT-LINE.
        // 027500     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE2.
        reportFileContents.add(wk_Total_Line2());
        // 027600     MOVE       111981-AMT3       TO      WK-SUM-BALANCE.
        wkSumBalance = fileSum111981.getAmt3();
        // 027700     MOVE       SPACE             TO      REPORT-LINE.
        // 027800     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE3.
        reportFileContents.add(wk_Total_Line3());
        // 027900 RPT-LAST-EXIT.
    }

    private void rpt_Title() {
        // 023300 RPT-TITLE-RTN.
        //// 寫REPORTFL表頭 (WK-TITLE-LINE1~WK-TITLE-LINE3)
        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023500     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        // 023600     MOVE       SPACES            TO      REPORT-LINE.
        // 023700     WRITE      REPORT-LINE       AFTER   1.
        // 023800     MOVE       WK-YYMMDD         TO      WK-PDATE.
        // 023900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        // 024000     MOVE       SPACES            TO      REPORT-LINE.
        // 024100     WRITE      REPORT-LINE       AFTER   1.
        // 024200     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        // 024300     MOVE       SPACES            TO      REPORT-LINE.
        // 024400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        reportFileContents.add(wk_Title_Line1());
        reportFileContents.add("");
        wkPdate = wkYYMMDD;
        reportFileContents.add(wk_Title_Line2());
        reportFileContents.add("");
        reportFileContents.add(wk_Title_Line3());
        reportFileContents.add(wk_Title_Line4());
        // 024500 RPT-TITLE-EXIT.
    }

    private String wk_Detail_Line() {
        // 014000 01 WK-DETAIL-LINE.
        // 014100    02 WK-RCPTID-RPT                PIC X(16).
        // 014200    02 WK-CNAME-RPT                 PIC X(30).
        // 014300    02 WK-DATE-RPT                  PIC 99/99/99.
        // 014400    02 FILLER                       PIC X(01).
        // 014500    02 WK-TIME-RPT                  PIC 999999.
        // 014600    02 FILLER                       PIC X(02).
        // 014700    02 WK-ACT-RPT                   PIC X(06).
        // 014800    02 WK-AMT-RPT                   PIC Z,ZZZ,ZZZ,ZZ9.
        // 014900    02 WK-BALANCE-RTP               PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkRcptidRpt, 16));
        sb.append(formatUtil.padX(wkCnameRpt, 30));
        sb.append(reportUtil.customFormat(wkDateRpt, "99/99/99"));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat(wkTimeRpt, "999999"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkActRpt, 6));
        sb.append(reportUtil.customFormat(wkAmtRpt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(reportUtil.customFormat(wkBalanceRpt, "Z,ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String wk_Title_Line1() {
        // 010500 01 WK-TITLE-LINE1.
        // 010600    02 FILLER                       PIC X(29) VALUE SPACE.
        // 010700    02 TITLE-LABEL                  PIC X(42)
        // 010800              VALUE " 全行代理收付系統－虛擬分戶當日交易明細表 ".
        // 010900    02 FILLER                       PIC X(17) VALUE SPACE.
        // 011000    02 FILLER                       PIC X(19)
        // 011100                              VALUE "FORM : C043".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 29));
        sb.append(formatUtil.padX(" 全行代理收付系統－虛擬分戶當日交易明細表 ", 42));
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.padX("FORM : C043", 19));
        return sb.toString();
    }

    private String wk_Title_Line2() {
        // 011200 01 WK-TITLE-LINE2.
        // 011300    02 FILLER                       PIC X(10)
        // 011400                              VALUE " 分行別： ".
        // 011500    02 WK-PBRNO                     PIC 9(03).
        // 011600    02 FILLER                       PIC X(05) VALUE SPACE.
        // 011700    02 FILLER                       PIC X(13)
        // 011800                              VALUE " 印表日期： ".
        // 011900    02 WK-PDATE                     PIC 99/99/99.
        // 012000    02 FILLER                       PIC X(38) VALUE SPACE.
        // 012100    02 FILLER                       PIC X(10) VALUE " 總頁次： ".
        // 012200    02 WK-TOTPAGE                   PIC 9(04).
        // 012300    02 FILLER                       PIC X(15) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.pad9(wkPbrno, 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 印表日期： ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "99/99/99"));
        sb.append(formatUtil.padX("", 38));
        sb.append(formatUtil.padX(" 總頁次： ", 10));
        sb.append(formatUtil.pad9("" + wkTotpage, 4));
        sb.append(formatUtil.padX("", 15));
        return sb.toString();
    }

    private String wk_Title_Line3() {
        // 012400 01 WK-TITLE-LINE3.
        // 012500    02 FILLER                PIC X(10) VALUE " 分戶帳號 ".
        // 012600    02 FILLER                PIC X(07) VALUE SPACE.
        // 012700    02 FILLER                PIC X(14) VALUE " 分戶帳號名稱 ".
        // 012800    02 FILLER                PIC X(15) VALUE SPACE.
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
        sb.append(formatUtil.padX(" 分戶帳號 ", 10));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 分戶帳號名稱 ", 14));
        sb.append(formatUtil.padX("", 15));
        sb.append(formatUtil.padX(" 日期 ", 6));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 時間 ", 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 動作 ", 6));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 金額 ", 6));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(" 餘額 ", 6));
        return sb.toString();
    }

    private String wk_Title_Line4() {
        // 013800 01 WK-TITLE-LINE4.
        // 013900    02 FILLER                PIC X(100) VALUE ALL "-".
        return reportUtil.makeGate("-", 100);
    }

    private String wk_Total_Line1() {
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
        return sb.toString();
    }

    private String wk_Total_Line2() {
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
        return sb.toString();
    }

    private String wk_Total_Line3() {
        // 016000 01 WK-TOTAL-LINE3.
        // 016100    02 FILLER                PIC X(10) VALUE " 總餘額： ".
        // 016200    02 FILLER                PIC X(25) VALUE SPACE.
        // 016300    02 WK-SUM-BALANCE        PIC Z,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 總餘額： ", 10));
        sb.append(formatUtil.padX("", 25));
        sb.append(reportUtil.customFormat(wkSumBalance, "Z,ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(wkRptDir, reportFileContents, CHARSET_BIG5);
            upload(wkRptDir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
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
        responseTextMap.put("ISNOTCONV", "");
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
