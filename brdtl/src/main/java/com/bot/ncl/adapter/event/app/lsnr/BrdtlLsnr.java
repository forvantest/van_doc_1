/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Brdtl;
import com.bot.ncl.jpa.anq.entities.impl.BrdtlQry01;
import com.bot.ncl.jpa.anq.svc.BrdtlQry01Service;
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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("BrdtlLsnr")
@Scope("prototype")
public class BrdtlLsnr extends BatchListenerCase<Brdtl> {
    @Value("${localFile.ncl.batch.directory}")
    protected String fileDir;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private BrdtlQry01Service sBrdtlQry01Service;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ReportUtil reportUtil;

    private List<String> fileContents;
    //  "BD/CL/BH/002."
    private static final String FILE_NAME =
            "CL-BH-002"; // 2024-08-26 from Mail 曾喜炘: 檔名要改成 CL-BH-002
    private static final String CHARSET = "BIG5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;
    private String filePath;
    private Brdtl event;
    private String processDate;
    private final DecimalFormat dFormatNum = new DecimalFormat("#,##0");
    private final DecimalFormat dFormatNum00 = new DecimalFormat("#,##0.  ");
    // BB是空白、9是數字0(補0) 等於程式的0、Z是空白 等於程式的#
    private final DecimalFormat dFormatNumBB = new DecimalFormat("#,###.  ");
    private final DecimalFormat dFormatNum99 = new DecimalFormat("#,###.00");
    private List<BrdtlQry01> resultQry = new ArrayList<>();

    /** 分行別(代碼) */
    private int cllbr = 0;

    /** 印表日期(當天日期) */
    private String tbsdy;

    /** 頁數 */
    private int page = 0;

    /** 代收類別 */
    private String code = "";

    /** 代收日(DATE為資料庫保留字,改為ENTDY) */
    private int entry = 0;

    /** 代收時間 */
    private int time = 0;

    /** 繳費金額 */
    private BigDecimal amt = BigDecimal.ZERO;

    private BigDecimal amtSubTotal = BigDecimal.ZERO;
    private BigDecimal amtSub2Total = BigDecimal.ZERO;
    private BigDecimal amtTotal = BigDecimal.ZERO;

    /** 銷帳號碼 */
    private String rcpid = "";

    /** KIND */
    private int kind = 0;

    /** KIND */
    private String txtype = "";

    /** 櫃台機編號 */
    private int trmno = 0;

    /** 櫃員號碼 */
    private String tlrno = "";

    /** 事業單位帳號 */
    private String actno = "";

    /** 備註資料 */
    private String userdata = "";

    /** 收付行手續費 */
    private BigDecimal cfee2SubTotal = BigDecimal.ZERO;

    private BigDecimal cfee2Sub2Total = BigDecimal.ZERO;
    private BigDecimal cfee2Total = BigDecimal.ZERO;
    private int cntSubTotal = 0;
    private int cntSub2Total = 0;
    private int cntTotal = 0;

    /** 列數 */
    private int rowNum = 0;

    private int tmpNextCllbr = 0;
    private String tmpNextCode = "";
    int tmpNextKind = 0;
    // 測試用(計算行數)
    private int tmpCnt = 1;
    private StringBuilder sb = null;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Brdtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BrdtlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Brdtl event) {
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "BrdtlLsnr run()");

        this.event = event;
        //
        init(3);
        // 0000-MAIN-RTN
        // SORT SORTFL
        // ASCENDING KEY S-CLLBR S-KIND S-CODE S-RCPTID
        // INPUT  PROCEDURE CS-SORTIN
        this.queryCldtl();

        // OUTPUT PROCEDURE CS-SORTOUT
        this.sortOutData();

        // CLOSE REPORTFL  WITH SAVE
        // DISPLAY "SYM/CL/BH/BRDTL GENERATE REPORT 002 OK"
        // CLOSE BOTSRDB
        // CLOSE FD-BHDATE
        // STOP RUN
        this.writeFile();

        batchResponse();
    }

    /** 查詢資料 */
    private void queryCldtl() {

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1);

        tbsdy = labelMap.get("PROCESS_DATE");
        // 待中菲APPLE提供正確名稱
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), processDate);
        // IF DB-CLDTL-CODE NOT=WK-CODE1 OR DB-CLDTL-CLLBR NOT= WK-CLLBR
        // 代收行或代收類別不同時，找事業單位基本資料檔，判斷主辦或協辦業務
        resultQry = sBrdtlQry01Service.queryBrdtl(parse.string2Integer(processDate));

        fileContents = new ArrayList<>();
        fileContents.add("\u000c");
    }

    private void sortOutData() {
        int dataTotalSize = resultQry.size();

        //        resultQry.sort(
        //                (c1, c2) -> {
        //                    if (c1.getCllbr() - c2.getCllbr() != 0) {
        //
        //                        if (c1.getCllbr() == c1.getPbrno()) {
        //                            c1.setPbrno(0);
        //                        } else {
        //                            c1.setPbrno(1);
        //                        }
        //                        if (c2.getCllbr() == c2.getPbrno()) {
        //                            c2.setPbrno(0);
        //                        } else {
        //                            c2.setPbrno(1);
        //                        }
        //                        return c1.getCllbr() - c2.getCllbr();
        //                    } else if (c1.getPbrno() - c2.getPbrno() != 0) {
        //                        // Pbrno 暫時取代 KIND
        //                        return c1.getPbrno() - c2.getPbrno();
        //                    } else if (!c1.getCode().equals(c2.getCode())) {
        //                        return 1;
        //                    } else if (!c1.getRcptid().equals(c2.getRcptid())) {
        //                        return 1;
        //                    } else {
        //                        return 0;
        //                    }
        //                });

        // 輸出檔名
        //        String fileName = "BD/CL/BH/002";
        filePath = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;

        int dataCnt = 0;

        boolean isCodeTotal;
        boolean isKindTotal;
        boolean isCllbrTotal;

        for (BrdtlQry01 r : resultQry) {

            // 計算資料筆數
            dataCnt++;

            // 筆數
            //            cnt++;
            cntSubTotal++;
            cntSub2Total++;
            cntTotal++;

            // 計算行數
            rowNum++;

            isCodeTotal = false;
            isKindTotal = false;
            isCllbrTotal = false;

            // 如果比數大於52行，動作如下；
            // 1.換頁
            // 2.page參數歸零
            // 3.印表頭
            // 4.印換頁記號
            // 5.判斷是否有用到表尾 或是 如果在52行 遇到表尾 繼續印 然後才做1234項目

            // 如何知道下一筆需要小計：
            // 1.判斷下一筆的分行、代收項目、主辦協辦是否不同
            // 2.判斷是否為最後一筆
            // 3.如果第一項不同，判斷現在小計到代收項目跟主辦協辦 是否在48~52行?=>小計到換分行判斷到50~52行,如果是最後的最後

            code = r.getBrdtlQry01Id().getCode();
            cllbr = r.getCllbr();
            kind = r.getKind();

            // 第一筆
            if (dataCnt == 1) {

                page++;
                cllbr = r.getCllbr();
                printHeader();
            }

            // 是否為最後一筆
            if (dataTotalSize != dataCnt) {
                tmpNextCllbr = resultQry.get(dataCnt).getCllbr();
                tmpNextCode = resultQry.get(dataCnt).getBrdtlQry01Id().getCode();
                tmpNextKind = resultQry.get(dataCnt).getKind();
            }

            printDetail(r);

            if (tmpNextCllbr != cllbr) {
                isCllbrTotal = true;
            }
            if (tmpNextKind != kind) {
                isKindTotal = true;
            }
            if (!tmpNextCode.equals((code))) {
                isCodeTotal = true;
            }

            printCalTotal(isCllbrTotal, isKindTotal, isCodeTotal, false);

            isNextPage(rowNum, false);

            if (dataTotalSize == dataCnt) {

                printCalTotal(true, true, true, true);
            }
        }
    }

    /**
     * 列印出小計/總計
     *
     * @param isCllbrTotal 是否為分行結算
     * @param isKindTotal 是否為主辦協辦結算
     * @param isCodeTotal 是否為代辦項目結算
     * @param isLast 是否為最後一筆
     */
    private void printCalTotal(
            boolean isCllbrTotal, boolean isKindTotal, boolean isCodeTotal, boolean isLast) {

        if (isCodeTotal) {
            // 代收項目結算
            printCodeTotol();
            init(1);
        }

        if (isKindTotal) {
            // 主協辦結算
            printKindTotol();
            init(2);
        }

        // 分行結算 須強制換頁
        if (isCllbrTotal) {
            // (協辦結算時就是也表示要換頁)
            if (!isKindTotal) {
                printKindTotol();
            }

            printTotol();
            init(3);
            if (!isLast) {
                isNextPage(rowNum, true);
            }
        }
    }

    /**
     * 初始化
     *
     * @param totalType 結算代號 1 代收項目結算 2.主協辦結算 3.分行結算(總計)
     */
    private void init(int totalType) {

        if (totalType == 1) {
            cntSubTotal = 0;
            amtSubTotal = BigDecimal.ZERO;
            cfee2SubTotal = BigDecimal.ZERO;
        }
        if (totalType == 2) {
            cntSubTotal = 0;
            amtSubTotal = BigDecimal.ZERO;
            cfee2SubTotal = BigDecimal.ZERO;

            cntSub2Total = 0;
            amtSub2Total = BigDecimal.ZERO;
            cfee2Sub2Total = BigDecimal.ZERO;
        }

        if (totalType == 3) {
            cntSubTotal = 0;
            amtSubTotal = BigDecimal.ZERO;
            cfee2SubTotal = BigDecimal.ZERO;

            cntSub2Total = 0;
            amtSub2Total = BigDecimal.ZERO;
            cfee2Sub2Total = BigDecimal.ZERO;

            cntTotal = 0;
            amtTotal = BigDecimal.ZERO;
            cfee2Total = BigDecimal.ZERO;

            page = 0;
        }
    }

    /**
     * 判斷是否換頁
     *
     * @param rowNum 當前行數
     * @param isNextPage 是否強制換頁
     */
    private void isNextPage(int rowNum, boolean isNextPage) {

        // 如果行數大於52 需換頁，重新賦值分行別及列印表頭,頁數+1
        // 否則繼續列印明細
        if (rowNum > 52 || isNextPage) {
            fileContents.add("\u000c");
            tmpCnt = 1;
            this.rowNum = 0;
            cllbr = tmpNextCllbr;
            page++;
            printHeader();
        }
        // MOVE       0                   TO     WK-PCTL.
        // ADD        1                   TO     WK-PAGE.
        // MOVE       SPACES              TO     REPORT-LINE.
        // WRITE      REPORT-LINE         AFTER  PAGE.
        // MOVE       SPACES              TO     REPORT-LINE.
    }

    /** 列印表頭 */
    private void printHeader() {

        // 01 WK-TITLE-LINE1.
        //  02 FILLER                          PIC X(30) VALUE SPACE.
        //  02 FILLER                          PIC X(48) VALUE
        //     "ONLINE COLLECT OPERATION DETAIL REPORT (COLLECT)".
        //  02 FILLER                          PIC X(30) VALUE SPACE.
        //  02 FILLER                          PIC X(12) VALUE
        //     "FORM : C002 ".
        // 表頭名稱
        String titleName = "ONLINE COLLECT OPERATION DETAIL REPORT (COLLECT)";
        // FORM NAME
        String form = "FORM : C002";

        // WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        // MOVE       SPACES              TO     REPORT-LINE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 30));
        sb.append(formatUtil.padX(titleName, 48));
        sb.append(formatUtil.padX(" ", 30));
        sb.append(formatUtil.padX(form, 12));
        fileContents.add(sb.toString());
        //        fileContents.add(space9(tmpCnt++, 2, "L"));
        // WRITE      REPORT-LINE         AFTER  1 LINE.
        fileContents.add("");

        // 01 WK-TITLE-LINE2.
        //    02 FILLER                          PIC X(05) VALUE SPACE.
        //    02 FILLER                          PIC X(07) VALUE
        //       "BRNO : ".
        //    02 WK-CLLBR-1                      PIC 9(03).
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        //    02 FILLER                          PIC X(12) VALUE
        //       "DATA DATE : ".
        //    02 WK-DDATE                        PIC Z99/99/99.
        //    02 FILLER                          PIC X(68) VALUE SPACE.
        //    02 FILLER                          PIC X(07) VALUE
        //       "PAGE : ".
        //    02 WK-PAGE                         PIC 9(03).

        //     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  1 LINE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.pad9(cllbr + "", 3));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX("DATA DATE : ", 12));
        sb.append(reportUtil.customFormat(tbsdy.substring(1), "Z99/99/99"));
        sb.append(formatUtil.padX(" ", 68));
        sb.append(formatUtil.padX("PAGE : ", 7));
        sb.append(formatUtil.pad9(page + "", 3));
        fileContents.add(sb.toString());
        fileContents.add("");
        printDetailColumn();

        rowNum = rowNum + 5;
    }

    /** 列印明細欄位 */
    private void printDetailColumn() {

        // 01 WK-TITLE-LINE3.
        //    02 FILLER              PIC X(02) VALUE SPACE.
        //    02 FILLER              PIC X(07) VALUE " CODE  ".
        //    02 FILLER              PIC X(10) VALUE "   DATE   ".
        //    02 FILLER              PIC X(09) VALUE "  TIME   ".
        //    02 FILLER              PIC X(13) VALUE "       AMOUNT".
        //    02 FILLER              PIC X(17) VALUE "      RCPTID     ".
        //    02 FILLER              PIC X(10) VALUE SPACE.
        //    02 FILLER              PIC X(05) VALUE " KIND".
        //    02 FILLER              PIC X(06) VALUE " TRMNO".
        //    02 FILLER              PIC X(06) VALUE " TLRNO".
        //    02 FILLER              PIC X(12) VALUE "     ACTNO  ".
        //    02 FILLER              PIC X(15) VALUE "       USERDATA".

        // WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" CODE  ", 7));
        sb.append(formatUtil.padX("   DATE   ", 10));
        sb.append(formatUtil.padX("  TIME   ", 9));
        sb.append(formatUtil.padX("       AMOUNT", 13));
        sb.append(formatUtil.padX("      RCPTID     ", 17));
        sb.append(formatUtil.padX(" ", 10));
        sb.append(formatUtil.padX(" KIND", 5));
        sb.append(formatUtil.padX(" TRMNO", 6));
        sb.append(formatUtil.padX(" TLRNO", 6));
        sb.append(formatUtil.padX("     ACTNO  ", 12));
        sb.append(formatUtil.padX("       USERDATA", 15));
        fileContents.add(sb.toString());
    }

    /** 列印明細 */
    private void printDetail(BrdtlQry01 data) {

        // 01 WK-DETAIL-LINE.
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        //    02 WK-CODE                         PIC X(06).
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        //    02 WK-DATE                         PIC Z99/99/99.
        //    02 FILLER                          PIC X(01) VALUE SPACE.
        //    02 WK-TIME                         PIC 999999.
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        //    02 WK-AMT                          PIC Z,ZZZ,ZZZ,ZZ9.
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        //    02 WK-RCPTID                       PIC X(26).
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        //    02 WK-TXTYPE                       PIC X(01).
        //    02 FILLER                          PIC X(01) VALUE SPACE.
        //    02 WK-TRMNO                        PIC 9(07).
        //    02 FILLER                          PIC X(01) VALUE SPACE.
        //    02 WK-TLRNO                        PIC X(02).
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        //    02 WK-ACTNO                        PIC X(12).
        //    02 FILLER                          PIC X(03) VALUE SPACE.
        //    02 WK-USERDATA                     PIC X(40).

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 2));

        code = data.getBrdtlQry01Id().getCode();
        sb.append(formatUtil.padX(code, 6));

        sb.append(formatUtil.padX(" ", 2));

        entry = data.getBrdtlQry01Id().getEntdy();
        sb.append(formatUtil.padX(dateFormate(entry), 9));

        sb.append(formatUtil.padX(" ", 1));

        SimpleDateFormat formatTime = new SimpleDateFormat(" hhmmss");
        time = data.getTime();
        sb.append(formatUtil.pad9("" + time, 6));

        sb.append(formatUtil.padX(" ", 2));
        // 金額
        amt = data.getAmt();
        amtSubTotal = amtSubTotal.add(data.getAmt());
        amtSub2Total = amtSub2Total.add(data.getAmt());
        amtTotal = amtTotal.add(data.getAmt());
        sb.append(formatUtil.padLeft(dFormatNum.format(amt), 13));

        sb.append(formatUtil.padX(" ", 2));

        rcpid = data.getBrdtlQry01Id().getRcptid();
        sb.append(formatUtil.padX(rcpid, 26));

        sb.append(formatUtil.padX(" ", 2));

        txtype = data.getTxtype();
        sb.append(formatUtil.padX(txtype, 1));

        sb.append(formatUtil.padX(" ", 1));

        trmno = data.getBrdtlQry01Id().getTrmno();
        sb.append(formatUtil.pad9(trmno + "", 7));

        sb.append(formatUtil.padX(" ", 1));

        tlrno = data.getTlrno();
        sb.append(formatUtil.padX(tlrno, 2));

        sb.append(formatUtil.padX(" ", 4));

        actno = data.getActno();
        sb.append(formatUtil.padX(actno, 12));

        sb.append(formatUtil.padX(" ", 3));

        userdata = data.getUserdata();
        sb.append(formatUtil.padX(userdata, 40));

        cfee2SubTotal = cfee2SubTotal.add(data.getCfee2());

        cfee2Sub2Total = cfee2Sub2Total.add(data.getCfee2());

        cfee2Total = cfee2Total.add(data.getCfee2());

        fileContents.add(sb.toString());
    }

    /** 代收項目小計 */
    private void printCodeTotol() {
        //     ADD        3                   TO     WK-PCTL.
        //     IF         WK-PCTL             >      52
        //       MOVE     WK-CLLBR            TO     WK-CLLBR-1
        //       PERFORM  098-WTIT-RTN        THRU   098-WTIT-EXIT.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  1 LINE.
        //     WRITE      REPORT-LINE         FROM   WK-SUBTOT-LINE.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  1 LINE.

        // 01 WK-SUBTOT-LINE.
        //    02 FILLER                   PIC X(02) VALUE "**".
        //    02 FILLER                   PIC X(10) VALUE "SUB**COUNT".
        //    02 WK-SCNT-Z                PIC ZZZ,ZZ9.BB.
        //    02 FILLER                   PIC X(06) VALUE "AMOUNT".
        //    02 WK-SAMT-Z                PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.BB.
        //    02 FILLER                   PIC X(10) VALUE "   FEE    ".
        //    02 WK-SCFEE-Z               PIC ZZZ,ZZZ.99.

        //        fileContents.add(space9(tmpCnt++, 2, "L"));
        //        fileContents.add(space9(tmpCnt++, 2, "L"));
        //        fileContents.add(space9(tmpCnt++, 2, "L"));
        fileContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX("**", 2));
        sb.append(formatUtil.padX("SUB**COUNT", 10));
        sb.append(formatUtil.padLeft(dFormatNum00.format(cntSubTotal), 10));
        sb.append(formatUtil.padX("AMOUNT", 6));
        sb.append(formatUtil.padLeft(dFormatNum00.format(amtSubTotal), 20));
        sb.append(formatUtil.padX("   FEE    ", 10));
        sb.append(formatUtil.padLeft(dFormatNum99.format(cfee2SubTotal), 10));
        fileContents.add(sb.toString());
        fileContents.add("");
        rowNum = rowNum + 4;
    }

    /** 主辦協辦總計 */
    private void printKindTotol() {

        //     ADD        2                   TO     WK-PCTL.
        //     IF         WK-PCTL             >      52
        //       MOVE     WK-CLLBR            TO     WK-CLLBR-1
        //       PERFORM  098-WTIT-RTN        THRU   098-WTIT-EXIT.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         FROM   WK-SUBTOT1-LINE.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.

        // 01 WK-SUBTOT1-LINE.
        //    02 FILLER                   PIC X(02) VALUE "*S".
        //    02 FILLER                   PIC X(10) VALUE "UBTOTCOUNT".
        //    02 WK-SCNT1-Z               PIC ZZZ,ZZ9.BB.
        //    02 FILLER                   PIC X(06) VALUE "AMOUNT".
        //    02 WK-SAMT1-Z               PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.BB.
        //    02 FILLER                   PIC X(10) VALUE "   FEE    ".
        //    02 WK-SCFEE1-Z              PIC ZZZ,ZZZ.99.

        sb = new StringBuilder();
        sb.append(formatUtil.padX("*S", 2));
        sb.append(formatUtil.padX("UBTOTCOUNT", 10));
        // 筆數
        sb.append(formatUtil.padLeft(dFormatNum00.format(cntSub2Total), 10));

        // 金額
        sb.append(formatUtil.padX("AMOUNT", 6));
        sb.append(formatUtil.padLeft(dFormatNum00.format(amtSub2Total), 20));
        // 費用
        sb.append(formatUtil.padX("   FEE    ", 10));
        sb.append(formatUtil.padLeft(dFormatNum99.format(cfee2Sub2Total), 10));

        sb.append(" ");
        sb.append(formatUtil.padX("  ", 2));

        // 01 WK-GATE-LINE.
        //    02 FILLER                   PIC X(02) VALUE SPACES.
        //    02 FILLER                   PIC X(130) VALUE ALL "-".
        String dash = "";
        for (int i = 1; i <= 130; i++) {
            dash = dash + "-";
        }

        fileContents.add(sb.toString());

        sb = new StringBuilder();
        sb.append(formatUtil.padX(dash, 130));

        fileContents.add(sb.toString());

        rowNum = rowNum + 4;
    }

    /** 總計 */
    private void printTotol() {
        // 原參數
        // 01 WK-TOTAL-LINE.
        //    02 FILLER                   PIC X(03) VALUE "*TO".
        //    02 FILLER                   PIC X(09) VALUE "TAL*COUNT".
        //    02 WK-PCNT-Z                PIC ZZZ,ZZ9.BB.
        //    02 FILLER                   PIC X(06) VALUE "AMOUNT".
        //    02 WK-PAMT-Z                PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.BB.

        // 處理方式
        //     MOVE       0                   TO     WK-PAGE.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         FROM   WK-TOTAL-LINE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("*TO", 3));
        sb.append(formatUtil.padX("TAL*COUNT", 9));
        sb.append(formatUtil.padLeft(dFormatNum00.format(cntTotal), 10));
        sb.append(formatUtil.padX("AMOUNT", 6));
        sb.append(formatUtil.padLeft(dFormatNum00.format(amtTotal), 20));

        fileContents.add(sb.toString());

        rowNum = rowNum + 1;
    }

    private void writeFile() {
        try {
            // 先刪原本的檔案
            textFile.deleteFile(filePath);
            // 寫入內容到檔案
            textFile.writeFileContent(filePath, fileContents, CHARSET);
            upload(filePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    //           字串用：formatUtil.padX(sb.toString(), 180));
    //           數字用：formatUtil.pad9("" + puttype, 2));

    /** 日期 格式 ex: 1120101 => 112/01/01 ex: 20240101 => 2024/01/01 */
    private String dateFormate(int date) {
        String sdate;
        int yy = 0;
        int mm = 0;
        int dd = 0;
        if (date > 0) {
            yy = date / 10000;
            mm = date / 100 % 100;
            dd = date % 100;
        }
        // 判斷西元或民國年
        if (yy <= 1911) {
            sdate = String.format("%03d", yy);
        } else {
            sdate = String.format("%04d", yy);
        }
        // xxxx/
        sdate = sdate + "/" + String.format("%02d", mm) + "/" + String.format("%02d", dd);

        return sdate;
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
