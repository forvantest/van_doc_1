/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.E002_RPT;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
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
@Component("E002_RPTLsnr")
@Scope("prototype")
public class E002_RPTLsnr extends BatchListenerCase<E002_RPT> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    private E002_RPT event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PERIOD = ".";
    private static final String COLON = ":";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_RPT = "RPT";
    private static final String STRING_E002 = "E002";
    private final List<String> fileReportflContents = new ArrayList<>();
    private StringBuilder sb = new StringBuilder();

    /* -- int (9) -- */
    private int wkDate;
    private int wkDateYY;
    private int wkDateMM;
    private int wkDateDD;
    private int wkNumYY;
    private int wkNumMM;
    private int wkNumDD;
    private int wkTime4;
    private int rptLen;
    private int rptDate;

    private String[] wkParamL;
    /* -- str (X)-- */
    private String wkTime;
    private String wkRptApseq;
    private String wkFilename;
    private String wkRptE002 = "";
    private String wkRptFilename;
    private String wkRptPbrno;
    private String wkPbrno;
    private String wkRgday7;
    private String wkRptRgday7;
    private String wkLen;
    private String wkParam1;
    private String wkParam2;
    private String wkRtcode;
    private String rptPbrno;
    private String rptTime;
    private String rptRgday7;
    private String rptFilename;
    private String rptParam1;
    private String rptParam2;
    private String rptEndText;
    private String rptEndRtcode;
    private String titleLabel;
    private String numDate;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(E002_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "E002_RPTLsnr onApplicationEvent()");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(E002_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "E002_RPTLsnr run()");

        if (!init(event)) {
            batchResponse();
            return;
        }
        // 執行1000-MAIN-RTN，寫REPORTFL報表表頭
        // 016000     PERFORM 1000-MAIN-RTN         THRU 1000-MAIN-EXIT.
        main();

        // 執行2000-RTCODE-RTN，寫REPORTFL報表明細
        // 016100     PERFORM 2000-RTCODE-RTN       THRU 2000-RTCODE-EXIT.
        rtcode();
        // 016200*
        // 016300 0000-END.
        // 016400     CLOSE REPORTFL.
        try {
            textFile.writeFileContent(wkRptE002, fileReportflContents, CHARSET_BIG5);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 016500     STOP RUN.
        batchResponse();
    }

    private Boolean init(E002_RPT event) {
        this.event = event;
        // 轉換成民國年到WK-DATE(YYYMMDD)
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        wkDate = parse.string2Integer(labelMap.get("BBSDY")); // TODO: 待確認 BATCH 參數名稱
        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        // 批次日期(民國年yyyymmdd)
        wkRgday7 = textMap.get("RGDAY"); // TODO: 待確認 WK-RGDAY7 參數名稱
        wkFilename = textMap.get("FILENAME");
        if (wkFilename.isEmpty()) {
            return false;
        }
        wkPbrno = paramMap.get("PBRNO"); // TODO: 待確認 WK-PBRNO 參數名稱
        wkLen = paramMap.get("LEN"); // TODO: 待確認 WK-LEN 參數名稱
        // BHNO1:= "0000000";
        // BHNO2:= "00";
        wkParam1 = "0000000"; // WFL固定放0000000
        wkParam2 = "00"; // WFL固定放00
        wkRtcode = paramMap.get("PARAM1"); // TODO: 借用PARAM1欄位

        // 014000 0000-START-RTN.
        // 014100*
        // 搬接收參數 給 WK-RPT-... (檔名變數值)& RPT-...
        // 014200     MOVE    WK-FILENAME(2:5)    TO WK-RPT-APSEQ.
        wkRptApseq = wkFilename.substring(1, 6);
        // 014300     MOVE    WK-FILENAME         TO WK-RPT-FILENAME.
        wkRptFilename = wkFilename;
        // 014400     MOVE    WK-PBRNO            TO WK-RPT-PBRNO  RPT-PBRNO.
        wkRptPbrno = wkPbrno;
        rptPbrno = wkPbrno;
        // 014500     MOVE    WK-RGDAY7           TO RPT-RGDAY7
        // 014600                                    WK-RPT-RGDAY7.
        rptRgday7 = wkRgday7;
        wkRptRgday7 = wkRgday7;
        // 014700     MOVE    WK-LEN              TO RPT-LEN.
        rptLen = parse.string2Integer(wkLen);
        // 014800     MOVE    WK-PARAM1           TO RPT-PARAM1.
        rptParam1 = wkParam1;
        // 014900     MOVE    WK-PARAM2           TO RPT-PARAM2.
        rptParam2 = wkParam2;
        // 015000     MOVE    WK-FILENAME         TO RPT-FILENAME.
        rptFilename = wkFilename;

        /*
           01 WK-RPT-E002.
              03 FILLER              PIC X(15)  VALUE "BD/CL/RPT/E002/".
              03 WK-RPT-APSEQ             PIC X(05).
              03 FILLER                   PIC X(01)  VALUE "/".
              03 WK-RPT-PBRNO             PIC X(03).
              03 FILLER                   PIC X(01)  VALUE "/".
              03 WK-RPT-RGDAY7            PIC X(07).
              03 FILLER                   PIC X(01)  VALUE "/".
              03 WK-RPT-FILENAME          PIC X(12).
              03 FILLER                   PIC X(01)  VALUE ".".
        */
        //     COPY  #RPT002  AS BD/CL/RPT/E002 FROM #BKPACK(PACK)                          00003500
        //                                      TO   #BKPACK(PACK);                         00003510
        // 直接改路徑
        wkRptE002 = fileDir + "RPT" + PATH_SEPARATOR + STRING_E002;
        //        wkRptE002 =
        //                fileDir
        //                        + "CL-BH-"
        //                        + STRING_RPT
        //                        + "-"
        //                        + STRING_E002
        //                        + "-"
        //                        + wkRptApseq
        //                        + "-"
        //                        + wkRptPbrno
        //                        + "-"
        //                        + wkRptRgday7
        //                        + "-"
        //                        + wkRptFilename;

        // 開啟檔案REPORTFL
        // 015100     OPEN    OUTPUT              REPORTFL.
        // 015200     MOVE    ALL ZEROS           TO WORK-AREA.
        // 015300*
        // 接收系統日期到NUM-DATE(MM/DD/YYYY)
        // 015400     ACCEPT  NUM-DATE            FROM   DATE.
        String numDateTemporary =
                formatUtil.pad9(this.event.getApiRequestCase().getLabel().getCaldy(), 8);
        String numYYYY = numDateTemporary.substring(0, 4);
        String numMM = numDateTemporary.substring(4, 6);
        String numDD = numDateTemporary.substring(6, 8);
        numDate = numMM + PATH_SEPARATOR + numDD + PATH_SEPARATOR + numYYYY;

        // 015500     MOVE    WK-NUM-DD           TO   WK-DATE-DD.
        wkDateDD = wkNumDD;
        // 015600     MOVE    WK-NUM-MM           TO   WK-DATE-MM.
        wkDateMM = wkNumMM;
        // 015700     COMPUTE WK-DATE-YY   =  WK-NUM-YYYY  -   1911.
        wkDateYY = wkNumYY - 1911;

        // 接收系統時間到WK-TIME(HHMMSSTT)
        // 015800     ACCEPT  WK-TIME   FROM     TIME.
        wkTime = this.event.getApiRequestCase().getLabel().getCaltm();

        // WK-TIME-4後續沒用到
        // 015900     COMPUTE WK-TIME-4    =  WK-TIME / 10000.
        wkTime4 = parse.string2Integer(wkTime) / 10000;
        return true;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "E002_RPTLsnr main()");
        // 依據WK-RPT-APSEQ(=接收參數WK-FILENAME(2:5))搬值給TITLE-LABEL(WK-TITLE-LINE1'S變數)
        // 016800     EVALUATE  WK-RPT-APSEQ
        // 016900        WHEN "CL008"
        // 017000          MOVE  " 票交所代收中華電信（ＦＣＳ）報表（傳檔狀態） "
        // 017100              TO TITLE-LABEL
        // 017200        WHEN "BS005"
        // 017300          MOVE  " 公庫部代收央行媒體票上傳作業報表（傳檔狀態） "
        // 017400              TO TITLE-LABEL
        // 017500        WHEN "CL106"
        // 017600          MOVE  " 敦化分行代理台塑集團上傳作業報表（傳檔狀態） "
        // 017700              TO TITLE-LABEL
        // 017800        WHEN "GN054"
        // 017900          MOVE  " 健保局利息所得就源扣繳收送作業　（傳檔狀態） "
        // 018000              TO TITLE-LABEL
        // 018100        WHEN "CF002"
        // 018200          MOVE  " 欠稅人資料查詢　（傳檔狀態） "
        // 018300              TO TITLE-LABEL
        // 018400        WHEN "GL001"
        // 018500          MOVE  " 黃金存摺盤後牌價上傳作業報表（傳檔狀態） "
        // 018600              TO TITLE-LABEL
        // 018700        WHEN "GL002"
        // 018800          MOVE  " 臺灣銀行黃金產品參數檔變更明細表（交易狀態） "
        // 018900              TO TITLE-LABEL
        // 019000        WHEN "CT002"
        // 019100          MOVE  " 優惠存款戶退休系統比對作業　　　（傳檔狀態） "
        // 019200              TO TITLE-LABEL
        // 019300        WHEN "CT003"
        // 019400          MOVE  " 優惠存款戶查驗系統比對作業　　　（傳檔狀態） "
        // 019500              TO TITLE-LABEL
        // 019600        WHEN "GL003"
        // 019700          MOVE  " 黃金系統實體黃金提領傳輸作業（傳檔狀態） "
        // 019800              TO TITLE-LABEL
        // 019900        WHEN "CK003"
        // 020000          MOVE  " 有價證券代號上傳明細表　（傳檔狀態） "
        // 020100              TO TITLE-LABEL
        // 020200        WHEN "CK004"
        // 020300          MOVE  " 國庫保管品有價證券提存明細　（傳檔狀態） "
        // 020400              TO TITLE-LABEL
        // 020500        WHEN "CK005"
        // 020600          MOVE  " 國庫保管品有價證券異動明細　（傳檔狀態） "
        // 020700              TO TITLE-LABEL
        // 020800        WHEN "RP003"
        // 020900          MOVE  " 兒少未來教育及發展帳戶每日作業（傳檔狀態） "
        // 021000              TO TITLE-LABEL
        // 021100        WHEN "RP004"
        // 021200          MOVE  " 兒少未來教育及發展帳戶新增分戶（傳檔狀態） "
        // 021300              TO TITLE-LABEL
        // 021400        WHEN "RP005"
        // 021500          MOVE  " 兒少未來教育及發展帳戶交易作業（傳檔狀態） "
        // 021600              TO TITLE-LABEL
        // 021700        WHEN "DP003"
        // 021800          MOVE " 存款餘額證明（歸戶）執行狀況 "
        // 021900              TO TITLE-LABEL
        // 021920        WHEN "DP004"
        // 021940          MOVE " 存款餘額證明（同統編指定帳號）執行狀況 "
        // 021960              TO TITLE-LABEL
        // 021970        WHEN "CL001"
        // 021980          MOVE  " 代收付主檔建檔交易作業（執行狀況） "
        // 021990              TO TITLE-LABEL
        // 021992        WHEN "BS001"
        // 021994          MOVE  " 臺銀證券媒體當日結果回傳（執行狀況） "
        // 021996              TO TITLE-LABEL
        // 022000        WHEN  OTHER
        // 022100             DISPLAY "*** CL-E002 WK-RPT-APSEQ=" WK-RPT-APSEQ
        // 022200     END-EVALUATE.
        switch (wkRptApseq) {
            case "CL008":
                titleLabel = "票交所代收中華電信（ＦＣＳ）報表（傳檔狀態）";
                break;
            case "BS005":
                titleLabel = "公庫部代收央行媒體票上傳作業報表（傳檔狀態）";
                break;
            case "CL106":
                titleLabel = "敦化分行代理台塑集團上傳作業報表（傳檔狀態）";
                break;
            case "GN054":
                titleLabel = "健保局利息所得就源扣繳收送作業　（傳檔狀態）";
                break;
            case "CF002":
                titleLabel = "欠稅人資料查詢　（傳檔狀態）";
                break;
            case "GL001":
                titleLabel = "黃金存摺盤後牌價上傳作業報表（傳檔狀態）";
                break;
            case "GL002":
                titleLabel = "臺灣銀行黃金產品參數檔變更明細表（交易狀態）";
                break;
            case "CT002":
                titleLabel = "優惠存款戶退休系統比對作業　　　（傳檔狀態）";
                break;
            case "CT003":
                titleLabel = "優惠存款戶查驗系統比對作業　　　（傳檔狀態）";
                break;
            case "GL003":
                titleLabel = "黃金系統實體黃金提領傳輸作業（傳檔狀態）";
                break;
            case "CK003":
                titleLabel = "有價證券代號上傳明細表　（傳檔狀態）";
                break;
            case "CK004":
                titleLabel = "國庫保管品有價證券提存明細　（傳檔狀態）";
                break;
            case "CK005":
                titleLabel = "國庫保管品有價證券異動明細　（傳檔狀態）";
                break;
            case "RP003":
                titleLabel = "兒少未來教育及發展帳戶每日作業（傳檔狀態）";
                break;
            case "RP004":
                titleLabel = "兒少未來教育及發展帳戶新增分戶（傳檔狀態）";
                break;
            case "RP005":
                titleLabel = "兒少未來教育及發展帳戶交易作業（傳檔狀態）";
                break;
            case "DP003":
                titleLabel = "存款餘額證明（歸戶）執行狀況";
                break;
            case "DP004":
                titleLabel = "存款餘額證明（同統編指定帳號）執行狀況";
                break;
            case "CL001":
                titleLabel = "代收付主檔建檔交易作業（執行狀況）";
                break;
            case "BS001":
                titleLabel = "臺銀證券媒體當日結果回傳（執行狀況）";
                break;
            default:
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "*** CL-E002 WK-RPT-APSEQ = {}",
                        wkRptApseq);
                break;
        }

        // 搬日期、時間給RPT-DATE(YYY/MM/DD)、RPT-TIME(HH:MM:SS)(WK-TITLE-LINE3'S變數)
        // 022300     MOVE       WK-DATE        TO  RPT-DATE.
        rptDate = wkDate;

        // 022400     MOVE       WK-TIME(1:2)   TO  RPT-TIME(1:2).
        // 022500     MOVE       ":"            TO  RPT-TIME(3:1).
        // 022600     MOVE       WK-TIME(3:2)   TO  RPT-TIME(4:2).
        // 022700     MOVE       ":"            TO  RPT-TIME(6:1).
        // 022800     MOVE       WK-TIME(5:2)   TO  RPT-TIME(7:2).
        String rptTimeHH = wkTime.substring(0, 2);
        String rptTimeMM = wkTime.substring(2, 4);
        String rptTimeSS = wkTime.substring(4, 6);
        rptTime = rptTimeHH + COLON + rptTimeMM + COLON + rptTimeSS;

        // 寫REPORTFL報表表頭(WK-TITLE-LINE1~WK-WK-TITLE-LINE5)
        // 022900     MOVE       SPACES            TO      REPORT-LINE.
        // 023000     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        // 007700 01 WK-TITLE-LINE1.
        sb = new StringBuilder();
        // 007800   02 FILLER                    PIC X(29) VALUE SPACE.
        sb.append(formatUtil.padX("", 29));
        // 007900   02 TITLE-LABEL               PIC X(46)
        // 008000       VALUE " 前置ＦＴＰ取檔作業報表（傳檔狀態） ".
        sb.append(formatUtil.padX(" 前置ＦＴＰ取檔作業報表（傳檔狀態） ", 46));
        // 008100   02 FILLER                    PIC X(45) VALUE SPACE.
        sb.append(formatUtil.padX("", 45));
        fileReportflContents.add(sb.toString());
        // 023100     MOVE       SPACES            TO      REPORT-LINE.
        // 023200     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        // 008200 01 WK-TITLE-LINE2.
        sb = new StringBuilder();
        // 008300   02 FILLER                  PIC X(12) VALUE " 報表種類： ".
        sb.append(formatUtil.padX(" 報表種類： ", 12));
        // 008400   02 RPT-PTYPE               PIC X(10) VALUE " 傳檔狀態 ".
        sb.append(formatUtil.padX(" 傳檔狀態 ", 10));
        // 008500   02 FILLER                  PIC X(58) VALUE SPACE.
        sb.append(formatUtil.padX("", 58));
        // 008600   02 FILLER                  PIC X(20)
        // 008700                              VALUE " 報表代號： CL-E002".
        sb.append(formatUtil.padX(" 報表代號： CL-E002", 20));
        // 008800   02 FILLER                  PIC X(20) VALUE SPACE.
        sb.append(formatUtil.padX("", 20));
        fileReportflContents.add(sb.toString());
        // 023300     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        // 008900 01 WK-TITLE-LINE3.
        sb = new StringBuilder();
        // 009000   02 FILLER                  PIC X(12) VALUE " 分行別　： ".
        sb.append(formatUtil.padX(" 分行別　： ", 12));
        // 009100   02 RPT-PBRNO               PIC X(03) VALUE SPACE.
        sb.append(formatUtil.padX(rptPbrno, 3));
        // 009200   02 FILLER                  PIC X(65) VALUE SPACE.
        sb.append(formatUtil.padX("", 65));
        // 009300   02 FILLER                  PIC X(14)
        // 009400                             VALUE " 印表日期： ".
        sb.append(formatUtil.padX(" 印表日期： ", 14));
        // 009500   02 RPT-DATE                PIC 999/99/99.
        sb.append(reportUtil.customFormat(String.valueOf(rptDate), "999/99/99"));
        // 009600   02 FILLER                  PIC X(04) VALUE SPACE.
        sb.append(formatUtil.padX("", 4));
        // 009700   02 RPT-TIME                PIC X(08).
        sb.append(formatUtil.padX(rptTime, 8));
        // 009800   02 FILLER                  PIC X(08) VALUE SPACE.
        sb.append(formatUtil.padX("", 8));
        fileReportflContents.add(sb.toString());
        // 023400     MOVE       SPACES            TO      REPORT-LINE.
        // 023500     WRITE      REPORT-LINE       AFTER   1.
        fileReportflContents.add("");
        // 023600     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        // 009900 01 WK-TITLE-LINE4.
        sb = new StringBuilder();
        // 010000   02 FILLER                  PIC X(12) VALUE " 輸入參數： ".
        sb.append(formatUtil.padX(" 輸入參數： ", 12));
        // 010100   02 RPT-RGDAY7              PIC X(07).
        sb.append(formatUtil.padX(rptRgday7, 7));
        // 010200   02 FILLER                  PIC X(08) VALUE " 檔名： ".
        sb.append(formatUtil.padX(" 檔名： ", 8));
        // 010300   02 RPT-FILENAME            PIC X(12).
        sb.append(formatUtil.padX(rptFilename, 12));
        // 010400   02 FILLER                  PIC X(08) VALUE " 長度： ".
        sb.append(formatUtil.padX(" 長度： ", 8));
        // 010500   02 RPT-LEN                 PIC ZZZ9.
        sb.append(reportUtil.customFormat(String.valueOf(rptLen), "ZZZ9"));
        // 010600   02 FILLER                  PIC X(10) VALUE " 參數一： ".
        sb.append(formatUtil.padX(" 參數一： ", 10));
        // 010700   02 RPT-PARAM1              PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX(rptParam1, 10));
        // 010800   02 FILLER                  PIC X(10) VALUE " 參數二： ".
        sb.append(formatUtil.padX(" 參數二： ", 10));
        // 010900   02 RPT-PARAM2              PIC X(10) VALUE SPACE.
        sb.append(formatUtil.padX(rptParam2, 10));
        // 011000   02 FILLER                  PIC X(30) VALUE SPACE.
        sb.append(formatUtil.padX("", 30));
        fileReportflContents.add(sb.toString());
        // 023700     MOVE       ALL "="           TO      REPORT-LINE.
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("=", 120));
        fileReportflContents.add(sb.toString());
        // 023800     WRITE      REPORT-LINE       AFTER   1.
        fileReportflContents.add("");
        // 023900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE5.
        // 011100 01 WK-TITLE-LINE5.
        sb = new StringBuilder();
        // 011200   02 FILLER         PIC X(20) VALUE " 訊息代號／訊息說明 ".
        sb.append(formatUtil.padX(" 訊息代號／訊息說明 ", 20));
        // 011300   02 FILLER         PIC X(100) VALUE SPACE.
        sb.append(formatUtil.padX("", 100));
        fileReportflContents.add(sb.toString());
        // 024000     MOVE       ALL "-"           TO      REPORT-LINE.
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        fileReportflContents.add(sb.toString());
        // 024100     WRITE      REPORT-LINE       AFTER   1.
        fileReportflContents.add("");
    }

    private void rtcode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "E002_RPTLsnr rtcode()");
        // 搬接收參數WK-RTCODE給RPT-END-RTCODE(WK-DTL-LINE1'S變數)
        // 024600     MOVE    WK-RTCODE  TO   RPT-END-RTCODE.
        rptEndRtcode = wkRtcode;

        // 依據接收參數WK-RTCODE搬值給RPT-END-TEXT(WK-DTL-LINE1'S變數)
        // 024700     EVALUATE  WK-RTCODE
        // 024800        WHEN "00"
        // 024900             MOVE   " 傳檔成功 " TO RPT-END-TEXT
        // 025000        WHEN "01"
        // 025100             MOVE   " 區別碼錯誤 " TO RPT-END-TEXT
        // 025200        WHEN "02"
        // 025300             MOVE   " 總合計與明細加總不合 " TO RPT-END-TEXT
        // 025400        WHEN "03"
        // 025500             MOVE   " 檔案不存在 " TO RPT-END-TEXT
        // 025600        WHEN "04"
        // 025700             MOVE   " 空檔 " TO RPT-END-TEXT
        // 025800        WHEN "05"
        // 025900             MOVE   " 輸入參數有誤 " TO RPT-END-TEXT
        // 026000        WHEN "06"
        // 026100             MOVE   " 資料內容有誤 " TO RPT-END-TEXT
        // 026200        WHEN "07"
        // 026300             MOVE   " 程式逾時有誤 " TO RPT-END-TEXT
        // 026400        WHEN "08"
        // 026500             MOVE   " 找不到資料 " TO RPT-END-TEXT
        // 026510        WHEN "09"
        // 026520             MOVE   " 併檔檔名不一致 " TO RPT-END-TEXT
        // 026530        WHEN "10"
        // 026540             MOVE   " 併檔軋帳記號不一致 " TO RPT-END-TEXT
        // 026550        WHEN "11"
        // 026560             MOVE   " 併檔挑檔週期不一致 " TO RPT-END-TEXT
        // 026570        WHEN "12"
        // 026580             MOVE   " 即時銷帳週期不一致 " TO RPT-END-TEXT
        // 026585        WHEN "13"
        // 026590             MOVE   " 檔案已存在無法建檔 " TO RPT-END-TEXT
        // 026592        WHEN "14"
        // 026594             MOVE   " 檔案無法傳至ＦＴＰ " TO RPT-END-TEXT
        // 026600        WHEN "99"
        // 026700             MOVE   " 其他原因失敗 " TO RPT-END-TEXT
        // 026800        WHEN  OTHER
        // 026900             DISPLAY "*** CL-E002 WK-RTCODE=" WK-RTCODE
        // 027000     END-EVALUATE.
        switch (wkRtcode) {
            case "00":
                rptEndText = "傳檔成功";
                break;
            case "01":
                rptEndText = "區別碼錯誤";
                break;
            case "02":
                rptEndText = "總合計與明細加總不合";
                break;
            case "03":
                rptEndText = "檔案不存在";
                break;
            case "04":
                rptEndText = "空檔";
                break;
            case "05":
                rptEndText = "輸入參數有誤";
                break;
            case "06":
                rptEndText = "資料內容有誤";
                break;
            case "07":
                rptEndText = "程式逾時有誤";
                break;
            case "08":
                rptEndText = "找不到資料";
                break;
            case "09":
                rptEndText = "併檔檔名不一致";
                break;
            case "10":
                rptEndText = "併檔軋帳記號不一致";
                break;
            case "11":
                rptEndText = "併檔挑檔週期不一致";
                break;
            case "12":
                rptEndText = "即時銷帳週期不一致";
                break;
            case "13":
                rptEndText = "檔案已存在無法建檔";
                break;
            case "14":
                rptEndText = "檔案無法傳至ＦＴＰ";
                break;
            case "99":
                rptEndText = "其他原因失敗";
                break;
            default:
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "*** CL-E002 WK-RTCODE = {}",
                        wkRtcode);
                break;
        }

        // 寫REPORTFL報表明細(WK-DTL-LINE1)
        // 027100     WRITE      REPORT-LINE   FROM  WK-DTL-LINE1.
        // 011400 01 WK-DTL-LINE1.
        sb = new StringBuilder();
        // 011500   02 RPT-END-RTCODE PIC X(02) VALUE SPACE.
        sb.append(formatUtil.padX(rptEndRtcode, 2));
        // 011600   02 FILLER         PIC X(02) VALUE "- ".
        sb.append(formatUtil.padX("- ", 2));
        // 011700   02 RPT-END-TEXT  PIC X(116) VALUE SPACE.
        sb.append(formatUtil.padX(rptEndText, 116));
        fileReportflContents.add(sb.toString());
        // 027200     MOVE       ALL "-"           TO      REPORT-LINE.
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        fileReportflContents.add(sb.toString());
        // 027300     WRITE      REPORT-LINE       AFTER   1.
        fileReportflContents.add("");
    }

    // Exception process
    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        if (!wkRptE002.isEmpty()) {
            responseTextMap.put("RPTNAME", wkRptE002);
        }
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
