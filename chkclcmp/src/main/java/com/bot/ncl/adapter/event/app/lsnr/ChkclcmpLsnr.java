/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Chkclcmp;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileStockCtl;
import com.bot.ncl.util.fileVo.FileStockDtl;
import com.bot.ncl.util.fileVo.FileStockTot;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.math.BigDecimal;
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
@Component("ChkclcmpLsnr")
@Scope("prototype")
public class ChkclcmpLsnr extends BatchListenerCase<Chkclcmp> {

    @Autowired private Parse parse;

    @Autowired private FormatUtil formatUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private FileStockCtl stkCtl;
    @Autowired private FileStockDtl stkDtl;
    @Autowired private FileStockTot stkTot;

    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Chkclcmp event;

    @Autowired private TextFileUtil textFileUtil;

    private static final String UTF_8 = "UTF-8";
    private static final String BIG5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;

    private String[] wkParamL;
    private String wkPutBrno;
    private String wkPutFdate;
    private String wkPutFile;

    private String wkPutDir;
    private String rptFilePath;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkCode;
    private String wkCodeRpt;

    private String wkErrCtl;

    private List<String> rptDataList;
    private int wkRtnCd;
    private int wkWhatItem;
    private List<String> wkTextList;

    private int aCnt;
    private BigDecimal aAmt;

    private int dCnt;
    private BigDecimal dAmt;

    @Override
    public void onApplicationEvent(Chkclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChkclcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Chkclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChkclcmpLsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }
        //// 主程式0000-MAIN-RTN
        // 010000     PERFORM 0000-MAIN-RTN       THRU     0000-MAIN-EXIT.
        mainRoutine();
        // 010200     DISPLAY "SYM/CL/BH/STOCK/CHKFLP  OK".
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/STOCK/CHKFLP  OK");
        //// 若無錯誤資料，寫一筆REPORTFL報表明細(WK-NOERR-LINE)
        // 010300     IF        WK-ERRCTL           =      "N"
        if ("N".equals(wkErrCtl)) {
            // 010400       MOVE    SPACES              TO     REPORT-LINE
            // 010500       WRITE   REPORT-LINE         FROM   WK-NOERR-LINE.
            // 006300 01 WK-NOERR-LINE.
            // 006400  03 FILLER                            PIC X(12) VALUE
            // 006500                                                 " 無錯誤資料 ".
            rptDataList.add(formatUtil.padX(" 無錯誤資料 ", 12));
        }
        //// 關檔，結束程式
        //
        // 010600     CLOSE     FD-UPLCLCMP         WITH   SAVE.
        // 010700     CLOSE     REPORTFL            WITH   SAVE.
        // 010800     STOP RUN.
        textFileUtil.writeFileContent(rptFilePath, rptDataList, BIG5);

        batchResponse();
    }

    private Boolean init(Chkclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        this.event = event;
        // 009200 0000-START-RTN.
        // 009300     MOVE          F-BRNO          TO     WK-PUT-BRNO.
        // 009400     MOVE          F-FDATE         TO     WK-PUT-FDATE.
        // 009500     MOVE          F-FILENAME      TO     WK-PUT-FILE.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }

        wkPutFdate = formatUtil.pad9(textMap.get("DATE"), 8).substring(1); // TODO: 待確認BATCH參數名稱
        wkPutBrno = paramMap.get("PBRNO"); // TODO: 待確認BATCH參數名稱
        wkPutFile = textMap.get("FILENAME"); // TODO: 待確認BATCH參數名稱
        if (wkPutFile.isEmpty()) {
            return false;
        }
        // 設定本營業日、檔名日期變數值
        String processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱

        String wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);
        // 009600     CHANGE ATTRIBUTE FILENAME  OF FD-UPLCLCMP TO WK-PUTDIR.

        // WK-PUTDIR ref:
        // 007300 01 WK-PUTDIR.
        // 007400    03 FILLER                      PIC X(18)
        // 007500                   VALUE "DATA/GN/UPL/CL002/".
        // 007600    03 WK-PUT-BRNO                 PIC X(03).
        // 007700    03 FILLER                      PIC X(01) VALUE "/".
        // 007800    03 WK-PUT-FDATE                PIC X(07).
        // 007900    03 FILLER                      PIC X(01) VALUE "/".
        // 008000    03 WK-PUT-FILE.
        // 008100       05 WK-PUT-FILE-TYPE         PIC X(01).
        // 008200       05 WK-PUT-FILE-CLNUM        PIC X(05).
        // 008300       05 WK-PUT-FILE-CODE         PIC X(06).
        // 008400    03 FILLER                      PIC X(01) VALUE ".".
        wkPutDir = fileDir + wkPutBrno + PATH_SEPARATOR + wkPutFdate + PATH_SEPARATOR + wkPutFile;
        rptFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-040";
        // 009700     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 009800     MOVE   WK-PUT-FILE-CODE       TO     WK-CODE,
        // 009900                                          WK-CODE-RPT.
        wkCode = wkPutFile.substring(6);
        wkCodeRpt = wkPutFile.substring(6);

        aCnt = 0;
        aAmt = BigDecimal.ZERO;
        dCnt = 0;
        dAmt = BigDecimal.ZERO;
        return true;
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        // 011000 0000-MAIN-RTN.
        // 011100     MOVE    "N"      TO         WK-ERRCTL.
        wkErrCtl = "N";
        // 011200     OPEN    INPUT    FD-UPLCLCMP.
        // 011300     OPEN    OUTPUT   REPORTFL.
        // BD/CL/BH/040.
        textFileUtil.deleteFile(rptFilePath);
        rptDataList = new ArrayList<>();
        // 011400     PERFORM RPT-TITLE-RTN    THRU  RPT-TITLE-EXIT.
        rptTitle();
        // 011500 0000-READ-NEXT.
        if (!textFileUtil.exists(wkPutDir)) {
            return;
        }
        List<String> dataList = textFileUtil.readFileContent(wkPutDir, UTF_8);
        for (String data : dataList) {
            // 011600     MOVE    0                  TO    WK-RTNCD.
            wkRtnCd = 0;
            // 011700     MOVE    1                  TO    WK-WHAT-ITEM.
            wkWhatItem = 1;
            // 011800     MOVE    SPACES             TO    WK-TEXT-ALL.
            wkTextList = new ArrayList<>();
            // 011900     READ    FD-UPLCLCMP  AT  END  GO TO  0000-MAIN-EXIT.
            String f1 = data.substring(0, 1);
            switch (f1) {
                case "1":
                    // 012000     IF      STK-F1            =      1
                    // 012100       PERFORM 1000-CHK-FORM-RTN  THRU  1000-CHK-FORM-EXIT
                    chkForm1000(data);
                    break;
                case "2":
                    // 012200     ELSE
                    // 012300     IF      STK-F2            =      2
                    // 012400       PERFORM 2000-CHK-FORM-RTN  THRU  2000-CHK-FORM-EXIT
                    chkForm2000(data);
                    break;
                case "3":
                    // 012500     ELSE
                    // 012600     IF      STK-F3            =      3
                    // 012700       PERFORM 3000-CHK-FORM-RTN  THRU  3000-CHK-FORM-EXIT
                    chkForm3000(data);
                    break;
                default:
                    // 012800     ELSE
                    // 012900       MOVE  "FORM"             TO    WK-TEXT(WK-WHAT-ITEM)
                    // 013000       MOVE  "Y"                TO    WK-ERRCTL
                    // 013100       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT.
                    wkTextList.add("FORM");
                    wkErrCtl = "Y";
                    writeErr(data);
                    break;
            }
            // 013200     GO TO 0000-READ-NEXT.
        }
        // 013300 0000-MAIN-EXIT.
        // 013400     EXIT.
    }

    private void rptTitle() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rptTitle()");
        // 022300 RPT-TITLE-RTN.
        // 022400     MOVE       SPACES            TO      REPORT-LINE.
        // 022500     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        rptDataList.add(formatUtil.padX(wkTitleLine1(), 150));
        // 022600     MOVE       SPACES            TO      REPORT-LINE.
        // 022700     WRITE      REPORT-LINE       AFTER   1.
        rptDataList.add(" ".repeat(150));
        // 022800     MOVE       F-BRNO            TO      WK-PBRNO.
        // 022900     MOVE       F-FDATE           TO      WK-PDATE.
        // 023000*    MOVE       1                 TO      WK-TOTPAGE.
        // 023100     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        rptDataList.add(formatUtil.padX(wkTitleLine2(), 150));
        // 023200     MOVE       SPACES            TO      REPORT-LINE.
        // 023300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        rptDataList.add(formatUtil.padX(wkTitleLine3(), 150));
        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023600     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        rptDataList.add(formatUtil.padX(wkTitleLine4(), 150));
        // 023700 RPT-TITLE-EXIT.
        // 023800     EXIT.
    }

    private String wkTitleLine1() {
        // 003800 01 WK-TITLE-LINE1.
        // 003900    02 FILLER                       PIC X(33) VALUE SPACE.
        // 004000    02 TITLE-LABEL                  PIC X(32)
        // 004100                    VALUE " 代收比對維護錯誤檢核表一 ".
        // 004200    02 FILLER                       PIC X(21) VALUE SPACE.
        // 004300    02 FILLER                       PIC X(12)
        // 004400                              VALUE "FORM : C040 ".               96/11/29
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(33));
        sb.append(formatUtil.padX(" 代收比對維護錯誤檢核表一 ", 32));
        sb.append(" ".repeat(21));
        sb.append(formatUtil.padX("FORM : C040 ", 12));
        return sb.toString();
    }

    private String wkTitleLine2() {
        // 004500 01 WK-TITLE-LINE2.
        // 004600    02 FILLER                       PIC X(10)
        // 004700                              VALUE " 分行別： ".
        // 004800    02 WK-PBRNO                     PIC 9(03).
        // 004900    02 FILLER                       PIC X(05) VALUE SPACE.
        // 005000    02 FILLER                       PIC X(13)
        // 005100                              VALUE "  印表日期： ".
        // 005200    02 WK-PDATE                     PIC 99/99/99.
        // 005300    02 FILLER                       PIC X(38) VALUE SPACE.
        StringBuilder sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 32));
        sb.append(formatUtil.padX(wkPutBrno, 3));
        sb.append(" ".repeat(5));
        sb.append(formatUtil.padX("  印表日期： ", 13));
        sb.append(reportUtil.customFormat(wkPutFdate, "99/99/99"));
        sb.append(" ".repeat(38));
        return sb.toString();
    }

    private String wkTitleLine3() {
        // 005700 01 WK-TITLE-LINE3.
        // 005800    02 FILLER                       PIC X(12)
        // 005900                              VALUE " 代收類別： ".
        // 006000    02 WK-CODE-RPT                  PIC X(06).
        StringBuilder sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收類別： ", 12));
        sb.append(formatUtil.padX(wkCodeRpt, 6));
        return sb.toString();
    }

    private String wkTitleLine4() {
        // 006100 01 WK-TITLE-LINE4.
        // 006200    02 FILLER                       PIC X(100) VALUE ALL "-".
        return "-".repeat(100);
    }

    private void chkForm1000(String data) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkForm1000()");
        text2VoFormatter.format(data, stkCtl);
        // 013600     IF      STK-CODE          NOT =  WK-CODE
        // 013700       MOVE  "CODE"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 013800       MOVE  1                  TO    WK-RTNCD.
        if (!stkCtl.getCode().equals(wkCode)) {
            wkTextList.add("CODE");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 013900     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 014000     IF      STK-KIND          NOT    NUMERIC
        // 014100       MOVE  "KIND"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 014200       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkCtl.getKind())) {
            wkTextList.add("KIND");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 014300     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 014400     IF      STK-BHNO          NOT    NUMERIC
        // 014500       MOVE  "BHNO"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 014600       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkCtl.getBhno())) {
            wkTextList.add("BHNO");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 014700     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 014800     IF      STK-LMTDATE       NOT    NUMERIC
        // 014900       MOVE  "LMTDATE"          TO    WK-TEXT(WK-WHAT-ITEM)
        // 015000       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkCtl.getLmtdate())) {
            wkTextList.add("LMTDATE");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 015100     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 015200     IF      STK-LMTFLAG       NOT =  0  AND  NOT = 1
        // 015300       MOVE  "LMTFLAG"          TO    WK-TEXT(WK-WHAT-ITEM)
        // 015400       MOVE  1                  TO    WK-RTNCD.
        if (!stkCtl.getLmtflag().equals("0") && !stkCtl.getLmtflag().equals("1")) {
            wkTextList.add("LMTFLAG");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 015500     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 015600     IF      STK-LMTTIME       NOT    NUMERIC
        // 015700       MOVE  "LMTTIME"          TO    WK-TEXT(WK-WHAT-ITEM)
        // 015800       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkCtl.getLmttime())) {
            wkTextList.add("LMTTIME");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 015900     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 016000     IF      STK-KEPDATE       NOT    NUMERIC
        // 016100       MOVE  "KEPDATE"          TO    WK-TEXT(WK-WHAT-ITEM)
        // 016200       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkCtl.getKepdate())) {
            wkTextList.add("KEPDATE");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 016300     IF      WK-RTNCD           =     1
        // 016400       MOVE  "Y"                TO    WK-ERRCTL
        // 016500       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkRtnCd == 1) {
            wkErrCtl = "Y";
            writeErr(data);
        }
    }

    private void chkForm2000(String data) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkForm2000()");
        text2VoFormatter.format(data, stkDtl);
        // 016900     IF      STK-UFLG          NOT =  0 AND  NOT = 9
        // 017000       MOVE  "UFLG"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 017100       MOVE  1                  TO    WK-RTNCD.
        if (!stkDtl.getUflg().equals("0") && !stkDtl.getUflg().equals("9")) {
            wkTextList.add("UFLG");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 017200     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 017300*CHECK RCPTID*
        // 017400     IF      STK-RCPTID         =     SPACES
        // 017500       MOVE  "RCPTID"           TO    WK-TEXT(WK-WHAT-ITEM)
        // 017600       MOVE  1                  TO    WK-RTNCD.
        if (stkDtl.getRcptid().trim().isEmpty()) {
            wkTextList.add("RCPTID");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 017700     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 017800     IF      STK-AMT           NOT    NUMERIC
        // 017900       MOVE  "AMOUNT"           TO    WK-TEXT(WK-WHAT-ITEM)
        // 018000       MOVE  1                  TO    WK-RTNCD.
        if (!parse.isNumeric(stkDtl.getAmt())) {
            wkTextList.add("AMOUNT");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 018100     IF      WK-RTNCD           =     1
        // 018200       MOVE  "Y"                TO    WK-ERRCTL
        // 018300       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkRtnCd == 1) {
            wkErrCtl = "Y";
            writeErr(data);
        }
        // 018400     IF      STK-UFLG           =     0
        // 018500       ADD   1                  TO    WK-ACNT
        // 018600       ADD   STK-AMT            TO    WK-AAMTTOT.
        if (stkDtl.getUflg().equals("0")) {
            aCnt++;
            aAmt = aAmt.add(parse.string2BigDecimal(stkDtl.getAmt()));
        }
        // 018700     IF      STK-UFLG           =     9
        // 018800       ADD   1                  TO    WK-DCNT
        // 018900       ADD   STK-AMT            TO    WK-DAMTTOT.
        if (stkDtl.getUflg().equals("9")) {
            dCnt++;
            dAmt = dAmt.add(parse.string2BigDecimal(stkDtl.getAmt()));
        }
    }

    private void chkForm3000(String data) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkForm3000()");
        text2VoFormatter.format(data, stkTot);
        // 019200 3000-CHK-FORM-RTN.
        // 019300     IF      STK-F3ACNT        NOT =  WK-ACNT
        // 019400       MOVE  "ACNT"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 019500       MOVE  1                  TO    WK-RTNCD.
        int stkACnt = parse.string2Integer(stkTot.getAcnt());
        if (stkACnt != aCnt) {
            wkTextList.add("ACNT");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 019600     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 019700     IF      STK-F3AAMT        NOT =  WK-AAMTTOT
        // 019800       MOVE  "AAMT"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 019900       MOVE  1                  TO    WK-RTNCD.
        BigDecimal stkAAmt = parse.string2BigDecimal(stkTot.getAamt());
        if (stkAAmt.compareTo(aAmt) != 0) {
            wkTextList.add("AAMT");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 020000     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 020100     IF      STK-F3DCNT        NOT =  WK-DCNT
        // 020200       MOVE  "DCNT"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 020300       MOVE  1                  TO    WK-RTNCD.
        int stkDCnt = parse.string2Integer(stkTot.getDcnt());
        if (stkDCnt != dCnt) {
            wkTextList.add("DCNT");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 020400     ADD     1                  TO    WK-WHAT-ITEM.
        wkWhatItem++;
        // 020500     IF      STK-F3DAMT        NOT =  WK-DAMTTOT
        // 020600       MOVE  "DAMT"             TO    WK-TEXT(WK-WHAT-ITEM)
        // 020700       MOVE  1                  TO    WK-RTNCD.
        BigDecimal stkDAmt = parse.string2BigDecimal(stkTot.getDamt());
        if (stkDAmt.compareTo(dAmt) != 0) {
            wkTextList.add("DAMT");
            wkRtnCd = 1;
        } else {
            wkTextList.add("");
        }
        // 020800     IF      WK-RTNCD           =     1
        // 020900       MOVE  "Y"                TO    WK-ERRCTL
        // 021000       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkRtnCd == 1) {
            wkErrCtl = "Y";
            writeErr(data);
        }
        // 021100 3000-CHK-FORM-EXIT.
        // 021200     EXIT.
    }

    private void writeErr(String data) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeErr()");
        // 021300 9000-WRITE-ERR-RTN.
        // 021400     MOVE    STK-REC           TO     WK-REC.
        // 021500     MOVE    WK-REC            TO     WK-REC-P.
        // 021600     MOVE    SPACES            TO     REPORT-LINE.
        // 021700     WRITE   REPORT-LINE       FROM   WK-TEXT-LINE.

        // WK-TEXT-LINE ref:
        // 006600 01 WK-TEXT-LINE.
        // 006700  03 FILLER                            PIC X(10) VALUE
        // 006800                                                   "ERR ITEM: ".
        // 006900  03 WK-TEXT-ALL.
        // 007000   05 WK-TEXT          OCCURS  08      PIC X(07).
        StringBuilder wkTextAll = new StringBuilder();
        wkTextAll.append("ERR ITEM: ");
        for (String wkText : wkTextList) {
            wkTextAll.append(formatUtil.padX(wkText, 7));
        }
        rptDataList.add(formatUtil.padX(wkTextAll.toString(), 150));

        // 021800     MOVE    SPACES            TO     REPORT-LINE.
        // 021900     WRITE   REPORT-LINE       FROM   WK-DETAIL-LINE.

        // WK-DETAIL-LINE ref:
        // 007100 01 WK-DETAIL-LINE.
        // 007200  03 WK-REC-P                          PIC X(60).
        rptDataList.add(formatUtil.padX(formatUtil.padX(data, 60), 150));

        // 022000 9000-WRITE-ERR-EXIT.
        // 022100     EXIT.
    }

    private Map<String, String> getG2006Param(String lParam) {
        String[] paramL;
        if (lParam.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lParam is null");
            return null;
        }
        paramL = lParam.split(";");
        if (paramL == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "paramL is null");
            return null;
        }
        // G2006:
        //  PBRNO(;),
        //  HCODE(;),
        //  LEN(;),
        //  PARAM1(;),
        //  PARAM2(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("PBRNO", paramL[0]); // 對應 PBRNO
        if (paramL.length > 1) map.put("HCODE", paramL[1]); // 對應 HCODE
        if (paramL.length > 2) map.put("LEN", paramL[2]); // 對應 LEN
        if (paramL.length > 3) map.put("PARAM1", paramL[3]); // 對應 PARAM1
        if (paramL.length > 4) map.put("PARAM2", paramL[4]); // 對應 PARAM2
        if (map.size() == 0) {
            return null;
        }

        for (String key : map.keySet()) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "map KEY = {} ,VALUE = {}",
                    key,
                    map.get(key));
        }
        return map;
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
