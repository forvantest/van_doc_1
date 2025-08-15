/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTUPDBAF;
import com.bot.ncl.dto.entities.ClbafBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.entities.impl.ClbafId;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.exception.dbs.InsDupException;
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
@Component("OUTUPDBAFLsnr")
@Scope("prototype")
public class OUTUPDBAFLsnr extends BatchListenerCase<OUTUPDBAF> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private Parse parse;
    @Autowired private ClmrService clmrService;
    @Autowired private ClbafService clbafService;
    private OUTUPDBAF event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String CL022_FILE_PATH = "CL022"; // 目錄
    private static final String _003_FILE_PATH = "003"; // 目錄
    private static final String FILE_NAME_UPDBAF = "UPDBAF."; // 讀檔檔名
    private static final String FILE_NAME_054 = "CL-BH-054"; // 產檔檔名
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private String wkUpddir; // 讀檔路徑
    private String outputFilePath; // 讀檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileOUTPUTContents;
    private ClmrBus tClmr = new ClmrBus();
    private ClbafBus newClbaf = new ClbafBus();
    private ClbafBus tClbaf3 = new ClbafBus();
    private ClbafBus tClbaf999 = new ClbafBus();

    private Map<String, String> textMap;
    // ----tita----
    private int wkTaskRdate;
    private int wkTaskDate;
    // ----wk----
    private int wkUdate;
    private int wkRptDate;
    private int wkRptPdate;
    private int wkDate;
    private String wkRptPtimeHH;
    private String wkRptPtimeMM;
    private String wkRptPtimeSS;
    private BigDecimal wkDtlamt;
    private BigDecimal wkSubamt;
    private BigDecimal wkTotamt1;
    private BigDecimal wkTotfee;
    private BigDecimal wkTotamt2;
    private BigDecimal wkSubfee;
    private BigDecimal wkTotipal;
    private BigDecimal wkSubipal;
    private int wkSubcnt;
    private int wkTotcnt1;
    private int wkTotcnt2;
    private int wkLinecnt;
    private int wkPagecnt;
    private int wkForPbrno;
    private int wkRptPbrno;
    private String wkCode;
    private String wkRptCode;
    private int wkRptOupddate;
    private String wkTxtype;
    private int wkRtncd;
    private int wkPbrno;
    private BigDecimal wkRptDtlamt;
    private BigDecimal wkRptIpal;
    private BigDecimal wkRptFee;
    private int wkExistFlag;
    // ----UPDBAF----
    private int updbafPbrno;
    private BigDecimal updbafFee;
    private BigDecimal updbafIpal;
    private BigDecimal updbafAmt;
    private String updbafCode;
    private String updbafTxtype;
    private int updbafCnt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTUPDBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTUPDBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr run()");
        init(event);
        //// 若FD-UPDBAF檔案不存在，跳到0000-END-RTN 顯示訊息、結束程式
        // 014700     IF ATTRIBUTE RESIDENT OF FD-UPDBAF IS NOT = VALUE(TRUE)
        // 014800       GO TO 0000-END-RTN.

        if (textFile.exists(wkUpddir)) {
            //// 執行0000-MAIN-RTN，讀FD-UPDBAF、寫REPORTFL報表明細 & 若資料正確，更新收付累計檔
            // 015000     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
            main();
        }
        // 015100 0000-END-RTN.
        // 顯示訊息、結束程式
        // 015200     DISPLAY "SYM/CL/BH/OUTING/UPDBAF COMPLETED"  .
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/OUTING/UPDBAF COMPLETED");

        // 015300     STOP RUN.
    }

    private void init(OUTUPDBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr init....");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = parse.string2Integer(textMap.get("WK_TASK_DATE"));
        wkTaskRdate = parse.string2Integer(textMap.get("RGDAY"));
        //// 搬接收參數-預定入帳日 給WK-PUTDIR檔名變數值
        // 014100     MOVE   WK-TASK-RDATE        TO     WK-UDATE    .
        wkUdate = wkTaskRdate;

        dateUtil = new DateUtil();
        // 017300     MOVE    PARA-HHMMSS       TO     WK-KEEP-UTIME.
        //// 搬接收參數-實際入帳日 給 報表-入帳日期、印表日期 & WK-DATE
        //// 搬系統時間給報表-印表時間
        // 014200     MOVE   WK-TASK-DATE         TO     WK-RPT-DATE ,
        // 014300                                        WK-RPT-PDATE,
        // 014400                                        WK-DATE     .
        // 014420     MOVE   PARA-HH              TO     WK-RPT-PTIME-HH.
        // 014440     MOVE   PARA-MIN             TO     WK-RPT-PTIME-MM.
        // 014460     MOVE   PARA-SS              TO     WK-RPT-PTIME-SS.
        wkRptDate = wkTaskDate;
        wkRptPdate = wkTaskDate;
        wkDate = wkTaskDate;
        wkRptPtimeHH = dateUtil.getNowStringTime(false).substring(0, 2);
        wkRptPtimeMM = dateUtil.getNowStringTime(false).substring(2, 4);
        wkRptPtimeSS = dateUtil.getNowStringTime(false).substring(4, 6);

        //// 設定檔名
        ////  WK-UPDDIR="DATA/GN/DWL/CL022/003/"+WK-UDATE 9(07)+"/UPDBAF."
        // 014600     CHANGE ATTRIBUTE FILENAME OF FD-UPDBAF TO WK-UPDDIR.
        wkUpddir =
                fileDir
                        + CL022_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkUdate
                        + PATH_SEPARATOR
                        + FILE_NAME_UPDBAF;
        // 002300  FD  REPORTFL
        // 002400      VALUE  OF  TITLE  IS  "BD/CL/BH/054."
        outputFilePath = fileDir + FILE_NAME_054;

        fileOUTPUTContents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr main....");
        // 015500 0000-MAIN-RTN.
        //
        //// 開啟檔案
        // 015600     OPEN    INPUT              FD-UPDBAF.
        // 015700     OPEN    OUTPUT             REPORTFL .
        // 015800     OPEN    UPDATE             BOTSRDB  .
        //
        //// 清變數
        // 015900     MOVE    0                  TO    WK-DTLAMT,
        // 016000                                      WK-SUBAMT,
        // 016100                                      WK-TOTAMT1,WK-TOTFEE,
        // 016200                                      WK-TOTAMT2,WK-SUBFEE,
        // 016220                                      WK-TOTIPAL          ,
        // 016240                                      WK-SUBIPAL          ,
        // 016300                                      WK-SUBCNT,
        // 016400                                      WK-TOTCNT1,
        // 016500                                      WK-TOTCNT2,
        // 016600                                      WK-LINECNT  ,
        // 016700                                      WK-FOR-PBRNO.
        wkDtlamt = new BigDecimal(0);
        wkSubamt = new BigDecimal(0);
        wkTotamt1 = new BigDecimal(0);
        wkTotfee = new BigDecimal(0);
        wkTotamt2 = new BigDecimal(0);
        wkSubfee = new BigDecimal(0);
        wkTotipal = new BigDecimal(0);
        wkSubipal = new BigDecimal(0);
        wkSubcnt = 0;
        wkTotcnt1 = 0;
        wkTotcnt2 = 0;
        wkLinecnt = 0;
        wkForPbrno = 0;

        //// 執行RPT-TITLE-RTN，寫REPORTFL報表表頭
        // 016800     PERFORM    RPT-TITLE-RTN   THRU  RPT-TITLE-EXIT.
        rptTitle(PAGE_SEPARATOR);
        // 016900 0000-MAIN-LOOP.
        //
        //// 循序讀取FD-UPDBAF，直到檔尾，跳到0000-MAIN-CLOSE
        //
        // 017000     READ       FD-UPDBAF AT END GO TO 0000-MAIN-CLOSE.
        List<String> lines = textFile.readFileContent(wkUpddir, CHARSET_UTF8);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03 UPDBAF-CODE	X(06)	代收類別 0-6
            // 03 UPDBAF-PBRNO	9(03)	主辦行 6-9
            // 03 UPDBAF-FEE	9(06)	總手續費 9-15
            // 03 UPDBAF-CNT	9(05)	總筆數 15-20
            // 03 UPDBAF-UPDDATE	9(08)	 20-28
            // 03 UPDBAF-TXTYPE	X(01)	帳務別 28-29
            // 03 UPDBAF-AMT	9(13)	總金額 29-42
            // 03 UPDBAF-FEECOST	9(06)	 42-48
            // 03 UPDBAF-R1	REDEFINES UPDBAF-FEECOST
            //  05 UPDBAF-IPAL	9(06)	總內部損益 42-48
            // 03 FILLER	X(12)	 48-60
            updbafCode = detail.substring(0, 6);
            updbafTxtype = detail.substring(28, 29);
            updbafCnt = parse.string2Integer(detail.substring(15, 20));
            updbafAmt = parse.string2BigDecimal(detail.substring(29, 42));
            updbafIpal = parse.string2BigDecimal(detail.substring(42, 48));
            updbafFee = parse.string2BigDecimal(detail.substring(9, 15));
            updbafPbrno = parse.string2Integer(detail.substring(6, 9));
            //// WK-RTNCD預設為0 (0.資料正確、1.資料有誤)

            // 017200     MOVE       0               TO    WK-RTNCD     .
            wkRtncd = 0;
            //// 主辦行不同時
            ////  執行RPT-DTL2-RTN，寫REPORTFL報表小計

            // 017300     IF   (     WK-FOR-PBRNO    NOT = 0            )
            // 017400      AND (     WK-FOR-PBRNO    NOT = UPDBAF-PBRNO )
            if (wkForPbrno != 0 && wkForPbrno != updbafPbrno) {
                // 017500       PERFORM  RPT-DTL2-RTN    THRU  RPT-DTL2-EXIT.
                rptDtl2();
            }
            //// 搬UPDBAF-REC...給報表
            //
            // 017700     MOVE       UPDBAF-PBRNO    TO    WK-PBRNO     ,
            // 017800                                      WK-FOR-PBRNO ,
            // 017900                                      WK-RPT-PBRNO .
            // 018000     MOVE       UPDBAF-CODE     TO    WK-CODE      ,
            // 018100                                      WK-RPT-CODE  .
            // 018200     MOVE       WK-TASK-RDATE   TO    WK-RPT-OUPDDATE.
            // 018300     MOVE       UPDBAF-TXTYPE   TO    WK-TXTYPE    .
            // 018400     MOVE       UPDBAF-AMT      TO    WK-RPT-DTLAMT.
            // 018420     MOVE       UPDBAF-IPAL     TO    WK-RPT-IPAL   .
            // 018450     MOVE       UPDBAF-FEE      TO    WK-RPT-FEE   .
            wkPbrno = updbafPbrno;
            wkForPbrno = updbafPbrno;
            wkRptPbrno = updbafPbrno;
            wkCode = updbafCode;
            wkRptCode = updbafCode;
            wkRptOupddate = wkTaskRdate;
            wkTxtype = updbafTxtype;
            wkRptDtlamt = updbafAmt;
            wkRptIpal = updbafIpal;
            wkRptFee = updbafFee;

            //// 執行1000-CHK-RTN，檢核資料，若有誤，WK-RTNCD設為1並寫REPORTFL報表明細
            // 018500     PERFORM    1000-CHK-RTN    THRU  1000-CHK-EXIT.
            chk();

            //// 若WK-RTNCD=0資料正確，執行2000-UPD-RTN，寫REPORTFL報表明細 & 更新收付累計檔
            //// 若WK-RTNCD=1資料有誤，累計失敗筆數、金額
            // 018700     IF         WK-RTNCD        =     0
            if (wkRtncd == 0) {
                // 018800       PERFORM  2000-UPD-RTN    THRU  2000-UPD-EXIT
                upd();
                // 018900     ELSE
            } else {
                // 019000       ADD      1               TO    WK-TOTCNT2
                // 019100       ADD      UPDBAF-AMT      TO    WK-TOTAMT2.
                wkTotcnt2 = wkTotcnt2 + 1;
                wkTotamt2 = wkTotamt2.add(updbafAmt);
            }
            //// LOOP讀下一筆FD-UPDBAF
            // 019300     GO TO      0000-MAIN-LOOP.
            if (cnt == lines.size()) {
                // 019400 0000-MAIN-CLOSE.

                //// 執行RPT-TAIL-RTN，寫REPORTFL報表小計 & 報表表尾
                // 019500     PERFORM RPT-TAIL-RTN       THRU  RPT-TAIL-EXIT.
                rptTail();
                //// 關閉檔案
                // 019600     CLOSE   FD-UPDBAF   WITH   SAVE.
                // 019700     CLOSE   REPORTFL    WITH   SAVE.
                // 019800     CLOSE   BOTSRDB                .
                try {
                    textFile.writeFileContent(outputFilePath, fileOUTPUTContents, CHARSET_BIG5);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
            }
        }
        // 019900 0000-MAIN-EXIT.
    }

    private void rptTail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr rptTail....");
        // 034600 RPT-TAIL-RTN.

        //// 執行RPT-DTL2-RTN，寫REPORTFL報表小計
        //// 寫REPORTFL報表表尾(WK-TAIL-LINE1~WK-TAIL-LINE4)

        // 034700     PERFORM  RPT-DTL2-RTN       THRU   RPT-DTL2-EXIT    .
        rptDtl2();
        // 034750     MOVE     WK-TOTFEE          TO     WK-RPT-TOTFEE    .
        BigDecimal wkRptTotfee = wkTotfee;
        // 034770     MOVE     WK-TOTIPAL         TO     WK-RPT-TOTIPAL   .
        BigDecimal wkRptTotipal = wkTotipal;
        // 034800     MOVE     WK-TOTCNT1         TO     WK-RPT-TOTCNT1   .
        int wkRptTotcnt1 = wkTotcnt1;
        // 034900     MOVE     WK-TOTAMT1         TO     WK-RPT-TOTAMT1   .
        BigDecimal wkRptTotamt1 = wkTotamt1;
        // 035000     MOVE     SPACES             TO     REPORT-LINE      .
        // 035100     WRITE    REPORT-LINE        FROM   WK-TAIL-LINE1    .
        // 009400  01 WK-TAIL-LINE1.
        // 009500     02 FILLER            PIC X(16) VALUE "   成功總筆數 : ".
        // 009600     02 WK-RPT-TOTCNT1               PIC ZZZ,ZZ9            .
        // 009700     02 FILLER                       PIC X(02) VALUE SPACES .
        // 009800     02 FILLER            PIC X(14) VALUE " 成功總金額 : ".
        // 009900     02 WK-RPT-TOTAMT1               PIC Z,ZZZ,ZZZ,ZZZ,ZZ9  .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("   成功總筆數 : ", 16));
        sb.append(reportUtil.customFormat("" + wkRptTotcnt1, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 成功總金額 : ", 14));
        sb.append(reportUtil.customFormat("" + wkRptTotamt1, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        fileOUTPUTContents.add(sb.toString());

        // 035200     MOVE     WK-TOTCNT2         TO     WK-RPT-TOTCNT2   .
        int wkRptTotcnt2 = wkTotcnt2;
        // 035300     MOVE     WK-TOTAMT2         TO     WK-RPT-TOTAMT2   .
        BigDecimal wkRptTotamt2 = wkTotamt2;
        // 035400     MOVE     SPACES             TO     REPORT-LINE      .
        // 035500     WRITE    REPORT-LINE        FROM   WK-TAIL-LINE2    .
        // 010000  01 WK-TAIL-LINE2.
        // 010100     02 FILLER            PIC X(16) VALUE "   失敗總筆數 : ".
        // 010200     02 WK-RPT-TOTCNT2               PIC ZZZ,ZZ9            .
        // 010300     02 FILLER                       PIC X(02) VALUE SPACES .
        // 010400     02 FILLER            PIC X(14) VALUE " 失敗總金額 : ".
        // 010500     02 WK-RPT-TOTAMT2               PIC Z,ZZZ,ZZZ,ZZZ,ZZ9  .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("   失敗總筆數 : ", 16));
        sb.append(reportUtil.customFormat("" + wkRptTotcnt2, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 失敗總金額 : ", 14));
        sb.append(reportUtil.customFormat("" + wkRptTotamt2, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        fileOUTPUTContents.add(sb.toString());

        // 035600     MOVE     SPACES             TO     REPORT-LINE      .
        // 035700     WRITE    REPORT-LINE        FROM   WK-TAIL-LINE3    .
        // 010520  01 WK-TAIL-LINE3.
        // 010540     02 FILLER            PIC X(16) VALUE "   分潤總金額 : ".
        // 010560     02 WK-RPT-TOTFEE                PIC ZZZ,ZZ9            .
        // 010580     02 FILLER            PIC X(17) VALUE " 內部損益總金額 :".
        // 010590     02 WK-RPT-TOTIPAL               PIC Z,ZZZ,ZZ9          .
        sb = new StringBuilder();
        sb.append(formatUtil.padX("   分潤總金額 : ", 16));
        sb.append(reportUtil.customFormat("" + wkRptTotfee, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" 內部損益總金額 :", 17));
        sb.append(reportUtil.customFormat("" + wkRptTotipal, "Z,ZZZ,ZZ9"));
        fileOUTPUTContents.add(sb.toString());

        // 035720     MOVE     SPACES             TO     REPORT-LINE      .
        // 035740     WRITE    REPORT-LINE        FROM   WK-TAIL-LINE4    .
        // 010600  01 WK-TAIL-LINE4.
        // 010700     02 FILLER                       PIC X(23) VALUE SPACE.
        // 010800     02 FILLER                       PIC X(06) VALUE " 經辦 ".
        // 010900     02 FILLER                       PIC X(17) VALUE SPACE.
        // 011000     02 FILLER                       PIC X(06) VALUE " 覆核 ".
        // 011100     02 FILLER                       PIC X(13) VALUE SPACE.
        // 011200     02 FILLER                       PIC X(06) VALUE " 主管 ".
        // 011300     02 FILLER                       PIC X(20) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 23));
        sb.append(formatUtil.padX(" 經辦 ", 6));
        sb.append(formatUtil.padX("", 17));
        sb.append(formatUtil.padX(" 覆核 ", 6));
        sb.append(formatUtil.padX("", 13));
        sb.append(formatUtil.padX(" 主管 ", 6));
        sb.append(formatUtil.padX("", 20));
        fileOUTPUTContents.add(sb.toString());

        // 035800 RPT-TAIL-EXIT.
    }

    private void rptTitle(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr rptTitle....");
        // 032400 RPT-TITLE-RTN.

        //// 首筆或換頁
        ////  寫REPORTFL報表表頭(WK-TITLE-LINE1~WK-TITLE-LINE4)
        // 032500     IF    (  WK-LINECNT         >      55     )
        // 032600       OR  (  WK-LINECNT         =      0      )
        if (wkLinecnt > 55 || wkLinecnt == 0) {
            // 032700       ADD    1                  TO     WK-PAGECNT
            // 032800       MOVE   5                  TO     WK-LINECNT
            wkPagecnt = wkPagecnt + 1;
            wkLinecnt = 5;
            // 032900       MOVE   SPACES             TO     REPORT-LINE
            // 033000       WRITE  REPORT-LINE        AFTER  PAGE
            sb = new StringBuilder();
            sb.append(pageFg); // 預留換頁符號
            fileOUTPUTContents.add(sb.toString());

            // 033100       MOVE   SPACES             TO     REPORT-LINE
            // 033200       WRITE  REPORT-LINE        FROM   WK-TITLE-LINE1
            // 004300  01 WK-TITLE-LINE1.
            // 004400     02 FILLER                       PIC X(18) VALUE SPACE.
            // 004500     02 TITLE-LABEL                  PIC X(34)
            // 004600                         VALUE " 全行代理收款系統外部代入帳表 ".
            // 004700     02 FILLER                       PIC X(07) VALUE SPACES.
            // 004800     02 FILLER                       PIC X(11)
            // 004900                         VALUE "FORM : C054".
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 18));
            sb.append(formatUtil.padX(" 全行代理收款系統外部代入帳表 ", 34));
            sb.append(formatUtil.padX("", 7));
            sb.append(formatUtil.padX("FORM : C054", 11));
            fileOUTPUTContents.add(sb.toString());

            // 033300       MOVE   SPACES             TO     REPORT-LINE
            // 033400       WRITE  REPORT-LINE        AFTER  1
            fileOUTPUTContents.add("");

            // 033500       MOVE   SPACES             TO     REPORT-LINE
            // 033600       WRITE  REPORT-LINE        FROM   WK-TITLE-LINE2
            // 005000  01 WK-TITLE-LINE2.
            // 005100     02 FILLER                       PIC X(15)
            // 005200                              VALUE " 分行別  : 003 ".
            // 005300     02 FILLER                       PIC X(11)
            // 005400                              VALUE " 印表日期 :".
            // 005500     02 WK-RPT-PDATE                 PIC Z99/99/99.
            // 005520     02 FILLER                       PIC X(02) VALUE SPACES.
            // 005540     02 WK-RPT-PTIME-HH              PIC 9(02) VALUE 0     .
            // 005550     02 FILLER                       PIC X(01) VALUE ":"   .
            // 005560     02 WK-RPT-PTIME-MM              PIC 9(02) VALUE 0     .
            // 005570     02 FILLER                       PIC X(01) VALUE ":"   .
            // 005580     02 WK-RPT-PTIME-SS              PIC 9(02) VALUE 0     .
            // 005600     02 FILLER                       PIC X(14) VALUE SPACES.
            // 005700     02 FILLER                       PIC X(07) VALUE "PAGE : ".
            // 005800     02 WK-PAGECNT                   PIC 9(04) VALUE 0        .
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" 分行別  : 003 ", 15));
            sb.append(formatUtil.padX(" 印表日期 :", 11));
            sb.append(reportUtil.customFormat("" + wkRptPdate, "Z99/99/99"));
            sb.append(formatUtil.padX("", 2));
            sb.append(formatUtil.pad9(wkRptPtimeHH, 2));
            sb.append(formatUtil.padX(":", 1));
            sb.append(formatUtil.pad9(wkRptPtimeMM, 2));
            sb.append(formatUtil.padX(":", 1));
            sb.append(formatUtil.pad9(wkRptPtimeSS, 2));
            sb.append(formatUtil.padX("", 14));
            sb.append(formatUtil.padX("PAGE : ", 7));
            sb.append(formatUtil.pad9("" + wkPagecnt, 4));
            fileOUTPUTContents.add(sb.toString());

            // 033700       MOVE   SPACES             TO     REPORT-LINE
            // 033800       WRITE  REPORT-LINE        FROM   WK-TITLE-LINE3
            // 005900  01 WK-TITLE-LINE3.
            // 006000     02 FILLER                       PIC X(15) VALUE SPACES.
            // 006100     02 FILLER                       PIC X(11)
            // 006200                              VALUE " 入帳日期 :"   .
            // 006300     02 WK-RPT-DATE                  PIC Z99/99/99    .
            // 006400     02 FILLER                       PIC X(04) VALUE SPACES.
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 15));
            sb.append(formatUtil.padX(" 入帳日期 :", 11));
            sb.append(reportUtil.customFormat("" + wkRptDate, "Z99/99/99"));
            sb.append(formatUtil.padX("", 4));
            fileOUTPUTContents.add(sb.toString());

            // 033900       MOVE   SPACES             TO     REPORT-LINE
            // 034000       WRITE  REPORT-LINE        FROM   WK-TITLE-LINE4
            // 006500  01 WK-TITLE-LINE4.
            // 006600     02 FILLER                       PIC X(08) VALUE " 主辦行 ".
            // 006700     02 FILLER                       PIC X(10) VALUE " 代收類別 ".
            // 006900     02 FILLER                     PIC X(12) VALUE " 預定入帳日 ".
            // 007000     02 FILLER                       PIC X(06) VALUE SPACES      .
            // 007100     02 FILLER                       PIC X(06) VALUE " 金額 "   .
            // 007300     02 FILLER                       PIC X(10) VALUE " 內部損益 ".
            // 007450     02 FILLER                       PIC X(10) VALUE " 分潤金額 ".
            // 007490     02 FILLER                       PIC X(10) VALUE " 入帳結果 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" 主辦行 ", 8));
            sb.append(formatUtil.padX(" 代收類別 ", 10));
            sb.append(formatUtil.padX(" 預定入帳日 ", 12));
            sb.append(formatUtil.padX("", 6));
            sb.append(formatUtil.padX(" 金額 ", 6));
            sb.append(formatUtil.padX(" 內部損益 ", 10));
            sb.append(formatUtil.padX(" 分潤金額 ", 10));
            sb.append(formatUtil.padX(" 入帳結果 ", 10));
            fileOUTPUTContents.add(sb.toString());

            // 034100       MOVE   SPACES             TO     REPORT-LINE
            // 034200       WRITE  REPORT-LINE        FROM   WK-TITLE-GATELINE.
            // 007500  01 WK-TITLE-GATELINE.
            // 007600      02 FILLER                      PIC X(80) VALUE ALL "-".
            sb = new StringBuilder();
            sb.append(reportUtil.makeGate("-", 80));
            fileOUTPUTContents.add(sb.toString());
        }
        // 034300 RPT-TITLE-EXIT.
    }

    private void rptDtl2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr rptDtl2....");
        // 036100 RPT-DTL2-RTN.

        //// 寫REPORTFL報表小計(WK-DTL-LINE2)
        //// 小計變數清0

        // 036200     MOVE     SPACES             TO     REPORT-LINE      .
        // 036300     WRITE    REPORT-LINE        AFTER  1                .
        fileOUTPUTContents.add("");

        // 036400     MOVE     WK-SUBCNT          TO     WK-RPT-SUBCNT    .
        // 036500     MOVE     WK-SUBAMT          TO     WK-RPT-SUBAMT    .
        // 036520     MOVE     WK-SUBIPAL         TO     WK-RPT-SUBIPAL   .
        // 036550     MOVE     WK-SUBFEE          TO     WK-RPT-SUBFEE    .
        int wkRptSubcnt = wkSubcnt;
        BigDecimal wkRptSubamt = wkSubamt;
        BigDecimal wkRptSubipal = wkSubipal;
        BigDecimal wkRptSubfee = wkSubfee;

        // 036600     MOVE     SPACES             TO     REPORT-LINE      .
        // 036700     WRITE    REPORT-LINE        FROM   WK-DTL-LINE2     .
        // 008800  01 WK-DTL-LINE2.
        // 008900     02 FILLER            PIC X(17) VALUE " **  小計　筆數 :".
        // 009000     02 WK-RPT-SUBCNT                PIC ZZ9               .
        // 009100     02 FILLER                       PIC X(06) VALUE SPACES.
        // 009300     02 WK-RPT-SUBAMT                PIC ZZZ,ZZZ,ZZZ,ZZ9   .
        // 009330     02 WK-RPT-SUBIPAL               PIC Z,ZZZ,ZZ9         .
        // 009340     02 FILLER            PIC X(04) VALUE SPACES           .
        // 009360     02 WK-RPT-SUBFEE                PIC ZZ,ZZ9            .
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" **  小計　筆數 :", 17));
        sb.append(reportUtil.customFormat("" + wkRptSubcnt, "ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(reportUtil.customFormat("" + wkRptSubamt, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(reportUtil.customFormat("" + wkRptSubipal, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + wkRptSubfee, "ZZ,ZZ9"));
        fileOUTPUTContents.add(sb.toString());

        // 036800     MOVE     SPACES             TO     REPORT-LINE      .
        // 036900     WRITE    REPORT-LINE        FROM   WK-TITLE-GATELINE.
        // 007500  01 WK-TITLE-GATELINE.
        // 007600      02 FILLER                      PIC X(80) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        fileOUTPUTContents.add(sb.toString());

        // 037000     ADD      3                  TO     WK-LINECNT       .
        // 037050     MOVE     0                  TO     WK-SUBFEE        .
        // 037070     MOVE     0                  TO     WK-SUBIPAL       .
        // 037100     MOVE     0                  TO     WK-SUBCNT        .
        // 037200     MOVE     0                  TO     WK-SUBAMT        .
        wkLinecnt = wkLinecnt + 3;
        wkSubfee = new BigDecimal(0);
        wkSubipal = new BigDecimal(0);
        wkSubcnt = 0;
        wkSubamt = new BigDecimal(0);

        // 037300 RPT-DTL2-EXIT.
    }

    private void chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr chk....");
        // 020200 1000-CHK-RTN.

        //// 執行1100-CHK-RTN，依代收類別找事業單位基本資料檔；若有誤，WK-RTNCD設為1，寫REPORTFL報表明細
        //// 執行1200-CHK-RTN，空段落
        //// 執行1300-CHK-RTN，若UPDBAF-TXTYPE帳務別 <> "K"，WK-RTNCD設為1，寫REPORTFL報表明細
        // 020300     PERFORM 1100-CHK-RTN       THRU  1100-CHK-EXIT.
        chk1100();
        // 020400     PERFORM 1200-CHK-RTN       THRU  1200-CHK-EXIT.
        chk1200();
        // 020500     PERFORM 1300-CHK-RTN       THRU  1300-CHK-EXIT.
        chk1300();
        // 020600 1000-CHK-EXIT.
    }

    private void chk1100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr chk1100....");
        // 020900 1100-CHK-RTN.

        //// 將DB-CLMR-IDX1指標移至開始

        // 021000     SET    DB-CLMR-IDX1        TO    BEGINNING.
        //// 依代收類別FIND DB-CLMR-IDX1事業單位基本資料檔，若有誤，設定錯誤訊息，寫REPORTFL報表明細(WK-DTL-LINE1)
        // 021100     FIND   DB-CLMR-IDX1 AT DB-CLMR-CODE = WK-CODE
        tClmr = clmrService.findById(wkCode);
        // 021200       ON   EXCEPTION
        if (Objects.isNull(tClmr)) {
            // 021300         MOVE 1                 TO    WK-RTNCD
            // 021400         MOVE " 失敗 CODE"      TO    WK-RPT-RESULT
            wkRtncd = 1;
            String wkRptResult = " 失敗 CODE";
            // 021500         MOVE SPACES            TO    REPORT-LINE
            // 021600         WRITE  REPORT-LINE     FROM  WK-DTL-LINE1.
            // 007700  01 WK-DTL-LINE1.
            sb = new StringBuilder();
            // 007800     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 007900     02 WK-RPT-PBRNO                 PIC X(03)                  .
            // 008000     02 FILLER                       PIC X(05) VALUE SPACES     .
            // 008100     02 WK-RPT-CODE                  PIC X(06)                  .
            // 008200     02 FILLER                       PIC X(03) VALUE SPACES     .
            // 008300     02 WK-RPT-OUPDDATE              PIC Z99/99/99              .
            // 008400     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 008500     02 WK-RPT-DTLAMT                PIC ZZZ,ZZZ,ZZ9            .
            // 008600     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 008700     02 WK-RPT-IPAL                  PIC ZZZ,ZZ9                .
            // 008720     02 FILLER                       PIC X(04) VALUE SPACES     .
            // 008740     02 WK-RPT-FEE                   PIC ZZ,ZZ9                 .
            // 008760     02 FILLER                       PIC X(03) VALUE SPACES     .
            // 008780     02 WK-RPT-RESULT                PIC X(10)                  .
            sb.append(formatUtil.padX("", 2));
            sb.append(formatUtil.padX("" + wkRptPbrno, 3));
            sb.append(formatUtil.padX("", 5));
            sb.append(formatUtil.padX(wkRptCode, 6));
            sb.append(formatUtil.padX("", 3));
            sb.append(reportUtil.customFormat("" + wkRptOupddate, "Z99/99/99"));
            sb.append(formatUtil.padX("", 2));
            sb.append(reportUtil.customFormat("" + wkRptDtlamt, "ZZZ,ZZZ,ZZ9"));
            sb.append(formatUtil.padX("", 2));
            sb.append(reportUtil.customFormat("" + wkRptIpal, "ZZZ,ZZ9"));
            sb.append(formatUtil.padX("", 4));
            sb.append(reportUtil.customFormat("" + wkRptFee, "ZZ,ZZ9"));
            sb.append(formatUtil.padX("", 3));
            sb.append(formatUtil.padX(wkRptResult, 10));
            fileOUTPUTContents.add(sb.toString());
        }
        // 021700 1100-CHK-EXIT.
    }

    private void chk1200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr chk1200....");
        // 022000 1200-CHK-RTN.
        // 022100*    IF    UPDBAF-UPDDATE       NOT = WK-TASK-DATE
        // 022200*      MOVE  1                  TO    WK-RTNCD
        // 022300*      MOVE  " 入帳日期有誤 "   TO    WK-RPT-RESULT
        // 022400*      MOVE  SPACES             TO    REPORT-LINE
        // 022500*      ADD   1                  TO    WK-LINECNT
        // 022600*      WRITE REPORT-LINE        FROM  WK-DTL-LINE1
        // 022700*      PERFORM RPT-TITLE-RTN    THRU  RPT-TITLE-EXIT.
        // 022800 1200-CHK-EXIT.
    }

    private void chk1300() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr chk1300....");
        // 023100 1300-CHK-RTN.

        //// 若UPDBAF-TXTYPE帳務別 <> "K"，設定錯誤訊息，寫REPORTFL報表明細(WK-DTL-LINE1)
        // 023200     IF    UPDBAF-TXTYPE        NOT = "K"
        if ("K".equals(updbafTxtype)) {
            // 023300       MOVE  1                  TO    WK-RTNCD
            // 023400       MOVE  " 帳務別有誤 "     TO    WK-RPT-RESULT
            wkRtncd = 1;
            String wkRptResult = " 帳務別有誤 ";
            // 023500       MOVE  SPACES             TO    REPORT-LINE
            // 023600       ADD   1                  TO    WK-LINECNT
            wkLinecnt = wkLinecnt + 1;
            // 023700       WRITE REPORT-LINE        FROM  WK-DTL-LINE1
            // 007700  01 WK-DTL-LINE1.
            sb = new StringBuilder();
            // 007800     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 007900     02 WK-RPT-PBRNO                 PIC X(03)                  .
            // 008000     02 FILLER                       PIC X(05) VALUE SPACES     .
            // 008100     02 WK-RPT-CODE                  PIC X(06)                  .
            // 008200     02 FILLER                       PIC X(03) VALUE SPACES     .
            // 008300     02 WK-RPT-OUPDDATE              PIC Z99/99/99              .
            // 008400     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 008500     02 WK-RPT-DTLAMT                PIC ZZZ,ZZZ,ZZ9            .
            // 008600     02 FILLER                       PIC X(02) VALUE SPACES     .
            // 008700     02 WK-RPT-IPAL                  PIC ZZZ,ZZ9                .
            // 008720     02 FILLER                       PIC X(04) VALUE SPACES     .
            // 008740     02 WK-RPT-FEE                   PIC ZZ,ZZ9                 .
            // 008760     02 FILLER                       PIC X(03) VALUE SPACES     .
            // 008780     02 WK-RPT-RESULT                PIC X(10)                  .
            sb.append(formatUtil.padX("", 2));
            sb.append(formatUtil.padX("" + wkRptPbrno, 3));
            sb.append(formatUtil.padX("", 5));
            sb.append(formatUtil.padX(wkRptCode, 6));
            sb.append(formatUtil.padX("", 3));
            sb.append(reportUtil.customFormat("" + wkRptOupddate, "Z99/99/99"));
            sb.append(formatUtil.padX("", 2));
            sb.append(reportUtil.customFormat("" + wkRptDtlamt, "ZZZ,ZZZ,ZZ9"));
            sb.append(formatUtil.padX("", 2));
            sb.append(reportUtil.customFormat("" + wkRptIpal, "ZZZ,ZZ9"));
            sb.append(formatUtil.padX("", 4));
            sb.append(reportUtil.customFormat("" + wkRptFee, "ZZ,ZZ9"));
            sb.append(formatUtil.padX("", 3));
            sb.append(formatUtil.padX(wkRptResult, 10));
            fileOUTPUTContents.add(sb.toString());

            // 023800       PERFORM RPT-TITLE-RTN    THRU  RPT-TITLE-EXIT.
            rptTitle(PAGE_SEPARATOR);
        }
        // 023900 1300-CHK-EXIT.
    }

    private void chk1400() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr chk1400....");
        // 024200 1400-CHK-RTN.
        // 024300*  預設  0:  找不到
        // 024400*        1:  找到

        //// WK-EXIST-FLAG預設為0(找不到)

        // 024500     MOVE   0                   TO    WK-EXIST-FLAG     .
        wkExistFlag = 0;
        //// 將DB-CLBAF-IDX1指標移至開始
        //
        // 024600     SET    DB-CLBAF-IDX1       TO    BEGINNING         .
        // 024700
        //
        //// 依代收行、代收日、代收類別、交易類型FIND 收付累計檔，若有誤，結束本段落
        //
        // 024800     FIND   DB-CLBAF-IDX1 AT DB-CLBAF-CLLBR  = 003
        // 024900                         AND DB-CLBAF-DATE   = WK-DATE
        // 025000                         AND DB-CLBAF-CODE   = UPDBAF-CODE
        // 025100                         AND DB-CLBAF-TXTYPE = UPDBAF-TXTYPE
        tClbaf3 =
                clbafService.holdById(
                        new ClbafId(
                                3,
                                wkDate,
                                wkCode,
                                tClmr.getPbrno(),
                                tClmr.getCrdb(),
                                updbafTxtype,
                                0));
        // 025200       ON EXCEPTION GO TO 1400-CHK-EXIT.
        // 025300
        if (!Objects.isNull(tClbaf3)) {
            //// WK-EXIST-FLAG設為1(找到)
            //
            // 025400     MOVE  1                    TO    WK-EXIST-FLAG     .
            wkExistFlag = 1;
        }
        //
        // 025500 1400-CHK-EXIT.
    }

    private void cre() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr cre....");
        // 027900 2100-CRE-RTN.
        //// 新增收付累計檔

        // 028000     BEGIN-TRANSACTION NO-AUDIT RESTART-DST           .
        // 028100     CREATE DB-CLBAF-DDS                              .
        // 028200     MOVE   UPDBAF-CODE       TO    DB-CLBAF-CODE     .
        // 028300     MOVE   WK-DATE           TO    DB-CLBAF-DATE     .
        // 028400     MOVE   003               TO    DB-CLBAF-CLLBR    .
        // 028500     MOVE   UPDBAF-CNT        TO    DB-CLBAF-CNT      .
        // 028600     MOVE   UPDBAF-TXTYPE     TO    DB-CLBAF-TXTYPE   .
        // 028700     MOVE   UPDBAF-AMT        TO    DB-CLBAF-AMT      .
        // 028750     MOVE   UPDBAF-IPAL       TO    DB-CLBAF-CFEE2    .
        // 028770     COMPUTE WK-KFEE = UPDBAF-FEE + UPDBAF-IPAL       .
        // 028790     MOVE   WK-KFEE           TO    DB-CLBAF-KFEE     .
        // 028800     STORE  DB-CLBAF-DDS                              .
        insertClbaf(3);
        // 028900
        // 029000     CREATE DB-CLBAF-DDS                              .
        // 029100     MOVE   UPDBAF-CODE       TO    DB-CLBAF-CODE     .
        // 029200     MOVE   WK-DATE           TO    DB-CLBAF-DATE     .
        // 029300     MOVE   999               TO    DB-CLBAF-CLLBR    .
        // 029400     MOVE   UPDBAF-CNT        TO    DB-CLBAF-CNT      .
        // 029500     MOVE   UPDBAF-TXTYPE     TO    DB-CLBAF-TXTYPE   .
        // 029600     MOVE   UPDBAF-AMT        TO    DB-CLBAF-AMT      .
        // 029650     MOVE   UPDBAF-IPAL       TO    DB-CLBAF-CFEE2    .
        // 029670     COMPUTE WK-KFEE = UPDBAF-FEE + UPDBAF-IPAL       .
        // 029690     MOVE   WK-KFEE           TO    DB-CLBAF-KFEE     .
        // 029700     STORE  DB-CLBAF-DDS                              .
        // 029800     END-TRANSACTION NO-AUDIT RESTART-DST             .
        insertClbaf(999);
        // 029900 2100-CRE-EXIT.
    }

    private void insertClbaf(int cllbr) {
        newClbaf = new ClbafBus();
        // 028100     CREATE DB-CLBAF-DDS                              .
        // 028200     MOVE   UPDBAF-CODE       TO    DB-CLBAF-CODE     .
        // 028300     MOVE   WK-DATE           TO    DB-CLBAF-DATE     .
        // 028400     MOVE   003               TO    DB-CLBAF-CLLBR    .
        // 028500     MOVE   UPDBAF-CNT        TO    DB-CLBAF-CNT      .
        // 028600     MOVE   UPDBAF-TXTYPE     TO    DB-CLBAF-TXTYPE   .
        // 028700     MOVE   UPDBAF-AMT        TO    DB-CLBAF-AMT      .
        // 028750     MOVE   UPDBAF-IPAL       TO    DB-CLBAF-CFEE2    .
        // 028770     COMPUTE WK-KFEE = UPDBAF-FEE + UPDBAF-IPAL       .
        // 028790     MOVE   WK-KFEE           TO    DB-CLBAF-KFEE     .
        newClbaf.setCode(updbafCode);
        newClbaf.setEntdy(wkDate);
        newClbaf.setCllbr(cllbr);
        newClbaf.setCnt(updbafCnt);
        newClbaf.setTxtype(updbafTxtype);
        newClbaf.setAmt(updbafAmt);
        newClbaf.setCfee2(updbafIpal);
        BigDecimal wkKfee = updbafFee.add(updbafIpal);
        newClbaf.setKfee(wkKfee);
        newClbaf.setPbrno(tClmr.getPbrno());
        newClbaf.setCrdb(tClmr.getCrdb());
        newClbaf.setCurcd(0);
        // 028800     STORE  DB-CLBAF-DDS.
        try {
            clbafService.insert(newClbaf);
        } catch (InsDupException dup) {
            throw new LogicException("GE032", "寫入收付累計檔資料已存在,請進行刪檔並等待刪檔完成後才能繼續");
        } catch (Exception e) {
            throw new LogicException("GE032", "寫入收付累計檔時發生錯誤");
        }
    }

    private void upd() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr upd....");
        // 025800 2000-UPD-RTN.

        //// 寫REPORTFL報表明細(WK-DTL-LINE1)
        // 025900     ADD      1               TO    WK-LINECNT        .
        // 026000     MOVE     " 成功  "       TO    WK-RPT-RESULT     .
        wkLinecnt = wkLinecnt + 1;
        String wkRptResult = " 成功  ";
        // 026100     MOVE     SPACES          TO    REPORT-LINE       .
        // 026200     WRITE    REPORT-LINE     FROM  WK-DTL-LINE1      .
        // 007700  01 WK-DTL-LINE1.
        sb = new StringBuilder();
        // 007800     02 FILLER                       PIC X(02) VALUE SPACES     .
        // 007900     02 WK-RPT-PBRNO                 PIC X(03)                  .
        // 008000     02 FILLER                       PIC X(05) VALUE SPACES     .
        // 008100     02 WK-RPT-CODE                  PIC X(06)                  .
        // 008200     02 FILLER                       PIC X(03) VALUE SPACES     .
        // 008300     02 WK-RPT-OUPDDATE              PIC Z99/99/99              .
        // 008400     02 FILLER                       PIC X(02) VALUE SPACES     .
        // 008500     02 WK-RPT-DTLAMT                PIC ZZZ,ZZZ,ZZ9            .
        // 008600     02 FILLER                       PIC X(02) VALUE SPACES     .
        // 008700     02 WK-RPT-IPAL                  PIC ZZZ,ZZ9                .
        // 008720     02 FILLER                       PIC X(04) VALUE SPACES     .
        // 008740     02 WK-RPT-FEE                   PIC ZZ,ZZ9                 .
        // 008760     02 FILLER                       PIC X(03) VALUE SPACES     .
        // 008780     02 WK-RPT-RESULT                PIC X(10)                  .
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX("" + wkRptPbrno, 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(wkRptCode, 6));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("" + wkRptOupddate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptDtlamt, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + wkRptIpal, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat("" + wkRptFee, "ZZ,ZZ9"));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(wkRptResult, 10));
        fileOUTPUTContents.add(sb.toString());

        // 026300     PERFORM  RPT-TITLE-RTN   THRU  RPT-TITLE-EXIT    .
        rptTitle(PAGE_SEPARATOR);

        //// 累計筆數、金額
        // 026500     ADD      1               TO    WK-TOTCNT1        ,
        // 026600                                    WK-SUBCNT         .
        // 026700     ADD      UPDBAF-AMT      TO    WK-TOTAMT1        ,
        // 026800                                    WK-SUBAMT         .
        // 026820     ADD      UPDBAF-FEE      TO    WK-TOTFEE         ,
        // 026840                                    WK-SUBFEE         .
        // 026860     ADD      UPDBAF-IPAL     TO    WK-TOTIPAL        ,
        // 026880                                    WK-SUBIPAL        .
        wkTotcnt1 = wkTotcnt1 + 1;
        wkSubcnt = wkSubcnt + 1;
        wkTotamt1 = wkTotamt1.add(updbafAmt);
        wkSubamt = wkSubamt.add(updbafAmt);
        wkTotfee = wkTotfee.add(updbafFee);
        wkSubfee = wkSubfee.add(updbafFee);
        wkTotipal = wkTotipal.add(updbafIpal);
        wkSubipal = wkSubipal.add(updbafIpal);

        //// WK-EXIST-FLAG: 0.找不到、1.找到
        //// 執行1400-CHK-RTN，依代收行(003)、代收日、代收類別、交易類型("K") FIND 收付累計檔
        ////  若找不到，執行2100-CRE-RTN，新增收付累計檔；若找到，執行2200-ADD-RTN，修改收付累計檔
        ////    DB-CLBAF-CLLBR 代收行(003,999)
        ////    DB-CLBAF-CNT   該業務在當日之累計代收筆數(UPDBAF-CNT)
        ////    DB-CLBAF-AMT   該業務在當日之累計代收金額(UPDBAF-AMT)
        ////    DB-CLBAF-CFEE2 該業務在當日之累計代收手續費(UPDBAF-IPAL)
        ////    DB-CLBAF-KFEE  外部代收電子化手續費(UPDBAF-FEE + UPDBAF-IPAL)

        // 027000     PERFORM  1400-CHK-RTN    THRU  1400-CHK-EXIT     .
        chk1400();
        // 027100     IF       WK-EXIST-FLAG   =     0
        if (wkExistFlag == 0) {
            // 027200       PERFORM 2100-CRE-RTN   THRU  2100-CRE-EXIT     .
            cre();
        }
        // 027300     IF       WK-EXIST-FLAG   =     1
        if (wkExistFlag == 1) {
            // 027400       PERFORM 2200-ADD-RTN   THRU  2200-ADD-EXIT     .
            addRtn();
        }

        // 027600 2000-UPD-EXIT.
    }

    private void addRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTUPDBAFLsnr addRtn....");
        // 030200 2200-ADD-RTN.
        //// 更新收付累計檔

        // 030300     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
        //// 將DB-CLBAF-IDX1指標移至開始
        // 030400     SET    DB-CLBAF-IDX1     TO    BEGINNING         .
        // 030500     LOCK   DB-CLBAF-IDX1 AT DB-CLBAF-CLLBR  = 003
        // 030600                         AND DB-CLBAF-DATE   = WK-DATE
        // 030700                         AND DB-CLBAF-CODE   = UPDBAF-CODE
        // 030800                         AND DB-CLBAF-TXTYPE = UPDBAF-TXTYPE .
        int cnt = tClbaf3.getCnt();
        BigDecimal amt = tClbaf3.getAmt();
        BigDecimal cfee2 = tClbaf3.getCfee2();
        BigDecimal kfee = tClbaf3.getKfee();
        // 030900     ADD    UPDBAF-CNT        TO    DB-CLBAF-CNT      .
        tClbaf3.setCnt(cnt + updbafCnt);
        // 031000     ADD    UPDBAF-AMT        TO    DB-CLBAF-AMT      .
        tClbaf3.setAmt(amt.add(updbafAmt));
        // 031050     ADD    UPDBAF-IPAL       TO    DB-CLBAF-CFEE2    .
        tClbaf3.setCfee2(cfee2.add(updbafIpal));
        // 031070     COMPUTE WK-KFEE = UPDBAF-FEE + UPDBAF-IPAL       .
        BigDecimal wkKfee = updbafFee.add(updbafIpal);
        // 031090     ADD    WK-KFEE           TO    DB-CLBAF-KFEE     .
        tClbaf3.setKfee(kfee.add(wkKfee));
        // 031100     STORE  DB-CLBAF-DDS.
        try {
            clbafService.update(tClbaf3);
        } catch (Exception e) {
            throw new LogicException("", "更新收付累計檔");
        }

        //// 將DB-CLBAF-IDX1指標移至開始

        // 031200     SET    DB-CLBAF-IDX1     TO    BEGINNING         .
        // 031300     LOCK   DB-CLBAF-IDX1 AT DB-CLBAF-CLLBR  = 999
        // 031400                         AND DB-CLBAF-DATE   = WK-DATE
        // 031500                         AND DB-CLBAF-CODE   = UPDBAF-CODE
        // 031600                         AND DB-CLBAF-TXTYPE = UPDBAF-TXTYPE .
        tClbaf999 = new ClbafBus();
        tClbaf999 =
                clbafService.holdById(
                        new ClbafId(
                                999,
                                wkDate,
                                wkCode,
                                tClmr.getPbrno(),
                                tClmr.getCrdb(),
                                updbafTxtype,
                                0));
        cnt = tClbaf999.getCnt();
        amt = tClbaf999.getAmt();
        cfee2 = tClbaf999.getCfee2();
        kfee = tClbaf999.getKfee();
        // 031700     ADD    UPDBAF-CNT        TO    DB-CLBAF-CNT      .
        tClbaf999.setCnt(cnt + updbafCnt);
        // 031800     ADD    UPDBAF-AMT        TO    DB-CLBAF-AMT      .
        tClbaf999.setAmt(amt.add(updbafAmt));
        // 031850     ADD    UPDBAF-IPAL       TO    DB-CLBAF-CFEE2    .
        tClbaf999.setCfee2(cfee2.add(updbafIpal));
        // 031870     COMPUTE WK-KFEE = UPDBAF-FEE + UPDBAF-IPAL       .
        wkKfee = updbafFee.add(updbafIpal);
        // 031890     ADD    WK-KFEE           TO    DB-CLBAF-KFEE
        tClbaf999.setKfee(kfee.add(wkKfee));
        // 031900     STORE  DB-CLBAF-DDS.
        try {
            clbafService.update(tClbaf999);
        } catch (Exception e) {
            throw new LogicException("", "更新收付累計檔");
        }
        // 032000     END-TRANSACTION   NO-AUDIT RESTART-DST.
        // 032100 2200-ADD-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
