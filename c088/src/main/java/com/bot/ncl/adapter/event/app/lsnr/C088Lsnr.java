/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C088;
import com.bot.ncl.dto.entities.ClfeebyCodeBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svcimp.ClfeeServiceImpl;
import com.bot.ncl.jpa.svcimp.ClmrServiceImpl;
import com.bot.ncl.jpa.svcimp.CltmrServiceImpl;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
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
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C088Lsnr")
@Scope("prototype")
public class C088Lsnr extends BatchListenerCase<C088> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ClmrServiceImpl sClmrServiceImpl;
    @Autowired private CltmrServiceImpl sCltmrServiceImpl;
    @Autowired private ClfeeServiceImpl sClfeeServiceImpl;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_NAME_1 = "CL-BH-C088"; // 檔名1
    private static final String FILE_NAME_2 = "CL-BH-C015-3"; // 檔名2
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String FILE1_DIR = "CLMR";
    private String outputFilePath1; // 產檔路徑1
    private String outputFilePath2; // 產檔路徑2
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC088Contents; // 檔案內容
    private List<String> fileClmrContents; // 檔案內容
    private String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";

    private C088 event;
    private String wkTaskDate = "";
    private String wkRptPDate = "";
    private String wkRptYYY = "";
    private String wkRptMM = "";
    private String wkCode = "";
    private BigDecimal wkCfeeeb = BigDecimal.ZERO;
    private int wkCnt = 0;
    private int wkRecCnt = 0;
    private String wkFKPuth = "Y";
    private String wkStopKPuth = "N";
    private int wkRptCode = 0;
    private int wkRptPbrno = 0;
    private BigDecimal wkRptCfeeeb = BigDecimal.ZERO;
    private BigDecimal wkRptSumCFee = BigDecimal.ZERO;
    private String wkRptEbtype = "";
    private int wkRptCnt = 0;
    private int wkRptPage = 0;
    private String wkRptCname = "";
    private int kPuthPbrno = 0;
    private int kPuthCode = 0;
    private int clmrControl = 0;
    private int clmrPbrno = 0;
    private String clmrCode = "";
    private BigDecimal clmrCFeeeb = BigDecimal.ZERO;
    private String clmrCName = "";
    private String clmrEbType = "";
    private String recClmrControl = "";
    private String recClmrPbrno = "";
    private String recClmrCode = "";
    private String recClmrCname = "";
    private String recClmrCfeeeb = "";
    private String recClmrEbtype = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C088 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C088 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr run");
        init(event);

        // 024100* 產生手續費大於零的代收類別及營業單位
        // 024200     OPEN INQUIRY BOTSRDB.
        // 024300     OPEN OUTPUT  FD-CLMR.

        // 024400     PERFORM  0200-BCTL-RTN    THRU 0200-BCTL-EXIT.
        bctl();
        // 024500     PERFORM  0500-CLMR-RTN    THRU 0500-CLMR-EXIT.
        clmr();
        // 024600     CLOSE BOTSRDB.
        // 024700     CLOSE FD-CLMR  WITH  SAVE.

        try {
            textFile.writeFileContent(outputFilePath1, fileClmrContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 024900* 排序ＣＬＭＲ檔案
        // 025000     SORT SORTFL
        // 025100          ON ASCENDING KEY S-PBRNO S-CONTROL S-CODE
        // 025200          USING   FD-CLMR
        // 025300          GIVING  FD-CLMR.
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(7, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 6, SortBy.ASC));
        externalSortUtil.sortingFile(outputFilePath1, outputFilePath1, keyRanges, CHARSET);

        // 025500* 產生報表
        // 025600     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO WK-KPUTHDIR.

        //// FD-KPUTH檔案存在，執行1000-C088-RTN
        // 025700     IF  ATTRIBUTE  RESIDENT   OF FD-KPUTH IS =  VALUE(TRUE)
        if (textFile.exists(inputFilePath)) {
            // 025800       PERFORM  1000-C088-RTN  THRU 1000-C088-EXIT.
            c088();

            try {
                textFile.writeFileContent(outputFilePath2, fileC088Contents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        // 025900 0000-END-RTN.
    }

    private void init(C088 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr init ....");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        //// 搬本月最終營業日給WK-FNBSDY
        wkTaskDate = textMap.get("WK_TASK_DATE"); // TODO: 待確認BATCH參數名稱
        // 023700     MOVE   WK-TASK-DATE       TO       WK-RPT-PDATE.
        // 023800     MOVE   WK-TASK-DATE(1:3)  TO       WK-RPT-YYY.
        // 023900     MOVE   WK-TASK-DATE(4:2)  TO       WK-RPT-MM.
        wkRptPDate = wkTaskDate;
        wkRptYYY = wkTaskDate.substring(0, 3);
        wkRptMM = wkTaskDate.substring(3, 5);
        // 讀檔路徑     DATA/CL/BH/ANALY/KPUTH.
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        // 產檔路徑1    DATA/CL/BH/CLMR/C088.
        outputFilePath1 = fileDir + FILE_NAME_1;
        // 產檔路徑2    DATA/CL/BH/C015/3.
        outputFilePath2 = fileDir + FILE_NAME_2;
        // 刪除舊檔
        textFile.deleteFile(outputFilePath1);
        textFile.deleteFile(outputFilePath2);

        fileC088Contents = new ArrayList<>();
        fileClmrContents = new ArrayList<>();
    }

    private void bctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr bctl ....");
        // 026300 0200-BCTL-RTN.

        //// 將DB-BCTL-IDX指標移至開始
        // 026400      SET   DB-BCTL-IDX   TO BEGINNING.
        // 026500 0200-BCTL-LOOP.

        //// FIND DB-BCTL-IDX營業單位控制檔
        ////  若有誤，結束本段落
        ////  若正常，IF DB-BCTL-TAXNO NOT= SPACES AND DB-BCTL-BRNO < 500，執行0300-WBRNO-RTN

        // 026600      FIND  NEXT DB-BCTL-IDX
        // 026700            ON EXCEPTION
        // 026800            GO  TO   0200-BCTL-EXIT.
        // 026900      IF    DB-BCTL-TAXNO  NOT= SPACES  AND DB-BCTL-BRNO < 500
        for (int i = -1; i < 500; i++) {
            Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(i);
            if (bctl == null) {
                continue;
            }
            // 027000         PERFORM  0300-WBRNO-RTN  THRU  0300-WBRNO-EXIT.
            wbrno(bctl);
        }

        // 027100      GO TO 0200-BCTL-LOOP.
        // 027200 0200-BCTL-EXIT.

    }

    private void clmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr clmr ....");
        // 028400 0500-CLMR-RTN.

        //// 將DB-CLMR-IDX1指標移至開始
        // 028500     SET     DB-CLMR-IDX1        TO    BEGINNING.
        // 028600 0500-CLMR-LOOP.
        List<ClmrBus> lClmr = sClmrServiceImpl.findAll(0, Integer.MAX_VALUE);
        // FIND事業單位基本資料檔
        //  若有誤，結束本段落
        //  若正常，搬代收類別、全國性繳費稅交易手續費
        // IF WK-CODE(1:1) = "5" AND WK-CFEEEB > 0，執行0600-WCLMR-RTN
        // 028700     FIND  NEXT  DB-CLMR-IDX1
        // 028800           ON EXCEPTION
        // 028900           GO  TO   0500-CLMR-EXIT.
        if (lClmr == null) {
            return;
        }
        for (ClmrBus tClmr : lClmr) {
            // 029000     MOVE    DB-CLMR-CODE        TO    WK-CODE.
            // 029100     MOVE    DB-CLMR-CFEEEB      TO    WK-CFEEEB.
            wkCode = tClmr.getCode();
            wkCfeeeb = tClmr.getAmt();
            // 029200     IF    WK-CODE(1:1) =  "5"   AND WK-CFEEEB > 0
            if ("5".equals(wkCode.substring(0, 1))) {
                // 029300        PERFORM  0600-WCLMR-RTN  THRU  0600-WCLMR-EXIT.
                wclmr(tClmr);
            }
            // 029400     GO TO 0500-CLMR-LOOP.
        }
        // 029500 0500-CLMR-EXIT.
    }

    private void wbrno(Bctl bctl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr wbrno ....");
        // 027500 0300-WBRNO-RTN.

        //// 搬主辦分行資料到WK-CLMRDATA
        // 027600     MOVE    SPACES              TO    WK-CLMRDATA.
        // 027700     MOVE    "1"                 TO    CLMR-CONTROL.
        // 027800     MOVE    DB-BCTL-BRNO        TO    CLMR-PBRNO.
        clmrControl = 1;
        clmrPbrno = parse.string2Integer(bctl.getBrno());
        // 027900     WRITE   CLMR-REC            FROM  WK-CLMRDATA.
        // 003600   01  CLMR-REC.
        // 003700       05  REC-CLMR-CONTROL             PIC X(01).
        // 003800       05  REC-CLMR-CODE                PIC X(06).
        // 003900       05  REC-CLMR-PBRNO               PIC 9(03).
        // 004000       05  REC-CLMR-CNAME               PIC X(40).
        // 004100       05  REC-CLMR-CFEEEB              PIC 9(05).
        // 004200       05  REC-CLMR-EBTYPE              PIC X(10).
        // 004300       05  FILLER                       PIC X(35).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("" + clmrControl, 1));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.pad9("" + clmrPbrno, 3));
        sb.append(formatUtil.padX(" ", 40));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" ", 10));
        sb.append(formatUtil.padX(" ", 35));
        fileClmrContents.add(sb.toString());

        // 028100 0300-WBRNO-EXIT.
    }

    private void wclmr(ClmrBus tClmr) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr wclmr ....");

        // 029800 0600-WCLMR-RTN.

        //// 寫明細到WK-CLMRDATA
        // 029900     MOVE    SPACES              TO    WK-CLMRDATA.
        // 030000     MOVE    "2"                 TO    CLMR-CONTROL.
        // 030100     MOVE    DB-CLMR-CODE        TO    CLMR-CODE.
        // 030200     MOVE    DB-CLMR-PBRNO       TO    CLMR-PBRNO.
        // 030300     MOVE    DB-CLMR-CFEEEB      TO    CLMR-CFEEEB.
        // 030400     MOVE    DB-CLMR-CNAME       TO    CLMR-CNAME.
        // 030500     MOVE    DB-CLMR-EBTYPE      TO    CLMR-EBTYPE.
        List<ClfeebyCodeBus> lClfee = sClfeeServiceImpl.findbyCode(tClmr.getCode(), 0, 1);
        CltmrBus tCltmr = sCltmrServiceImpl.findById(tClmr.getCode());

        clmrControl = 2;
        clmrCode = tClmr.getCode();
        clmrPbrno = tClmr.getPbrno();
        clmrCFeeeb = lClfee.get(0).getCfeeeb();
        clmrCName = tClmr.getCname();
        clmrEbType = tCltmr.getEbtype();
        // 030600     WRITE   CLMR-REC            FROM  WK-CLMRDATA.
        // 003600   01  CLMR-REC.
        // 003700       05  REC-CLMR-CONTROL             PIC X(01).
        // 003800       05  REC-CLMR-CODE                PIC X(06).
        // 003900       05  REC-CLMR-PBRNO               PIC 9(03).
        // 004000       05  REC-CLMR-CNAME               PIC X(40).
        // 004100       05  REC-CLMR-CFEEEB              PIC 9(05).
        // 004200       05  REC-CLMR-EBTYPE              PIC X(10).
        // 004300       05  FILLER                       PIC X(35).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("" + clmrControl, 1));
        sb.append(formatUtil.padX(clmrCode, 6));
        sb.append(formatUtil.pad9("" + clmrPbrno, 3));
        sb.append(formatUtil.padX(clmrCName, 40));
        sb.append(formatUtil.padX("" + clmrCFeeeb, 5));
        sb.append(formatUtil.padX(clmrEbType, 10));
        sb.append(formatUtil.padX(" ", 35));
        fileClmrContents.add(sb.toString());
        // 030800 0600-WCLMR-EXIT.
    }

    private void c088() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr c088 ....");
        // 031100 1000-C088-RTN.

        // 開啟檔案
        // 031200     OPEN    OUTPUT    REPORTFL.
        // 031300     OPEN    INPUT     FD-KPUTH.
        // 031400     OPEN    INPUT     FD-CLMR.
        //// 清變數
        // 031500     MOVE    0         TO    WK-CNT      .
        // 031600     MOVE    0         TO    WK-REC-CNT  .
        wkCnt = 0;
        wkRecCnt = 0;
        // 031700 1000-C088-LOOP.

        //// 循序讀取FD-CLMR，直到檔尾，跳到1000-C088-ENDCLMR
        // 031800     READ     FD-CLMR   AT END   GO TO  1000-C088-ENDCLMR.
        if (!textFile.exists(outputFilePath1)) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "C088Lsnr {} noexists ....",
                    outputFilePath1);
        }

        List<String> lines = textFile.readFileContent(outputFilePath1, CHARSET);
        Boolean firstPage = true;
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 003600   01  CLMR-REC.
            // 003700       05  REC-CLMR-CONTROL             PIC X(01).
            // 003800       05  REC-CLMR-CODE                PIC X(06).
            // 003900       05  REC-CLMR-PBRNO               PIC 9(03).
            // 004000       05  REC-CLMR-CNAME               PIC X(40).
            // 004100       05  REC-CLMR-CFEEEB              PIC 9(05).
            // 004200       05  REC-CLMR-EBTYPE              PIC X(10).
            // 004300       05  FILLER                       PIC X(35).
            recClmrControl = detail.substring(0, 1);
            recClmrPbrno = detail.substring(7, 10);
            recClmrCode = detail.substring(1, 7).trim().isEmpty() ? "0" : detail.substring(1, 7);
            recClmrCname = detail.substring(10, 30);
            recClmrCfeeeb = detail.substring(30, 35);
            recClmrEbtype = detail.substring(35, 45);
            // IF WK-CNT > 0 AND REC-CLMR-CONTROL = "1",寫報表表尾
            if (wkCnt > 0 && recClmrControl.equals("1")) {
                // 032000         PERFORM  3000-WTAIL-RTN    THRU   3000-WTAIL-EXIT.
                wtail();
            }
            // IF REC-CLMR-CONTROL = "1",寫報表表頭
            // 032200     IF  REC-CLMR-CONTROL = "1"
            if ("1".equals(recClmrControl)) {
                // 032300         MOVE     0                 TO     WK-RPT-PAGE
                // 032400         PERFORM  2500-TITLE-RTN    THRU   2500-TITLE-EXIT.
                wkRptPage = 0;
                title(firstPage ? " " : PAGE_SEPARATOR);
                if (firstPage) {
                    firstPage = false;
                }
            }
            // IF  WK-FKPUTH  =  "Y",寫報表明細
            // 032600     IF  WK-FKPUTH  =  "Y"
            if ("Y".equals(wkFKPuth)) {
                // 032700         PERFORM  1500-RKPUTH-RTN    THRU   1500-RKPUTH-EXIT.
                rkputh();
                // 032800     GO TO 1000-C088-LOOP.
                if (cnt == lines.size()) {
                    // 033000 1000-C088-ENDCLMR.
                    // 寫報表表尾
                    // 033100     PERFORM  3000-WTAIL-RTN     THRU  3000-WTAIL-EXIT.
                    wtail();
                }
                continue;
            }
            if (cnt == lines.size()) {
                // 033000 1000-C088-ENDCLMR.
                // 寫報表表尾
                // 033100     PERFORM  3000-WTAIL-RTN     THRU  3000-WTAIL-EXIT.
                wtail();
            }
        }
        // 033200     CLOSE    REPORTFL  WITH SAVE.
        // 033300     CLOSE    FD-KPUTH.
        // 033400     CLOSE    FD-CLMR.
        // 033500
        // 033600 1000-C088-EXIT.
    }

    private void wtail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr wtail ....");
        // 040000 3000-WTAIL-RTN.

        //// 寫報表表尾、MEMO
        // 040100     IF    WK-CNT  =  1
        if (wkCnt == 1) {
            // 040200       MOVE    SPACES        TO    REPORT-LINE
            // 040300       WRITE   REPORT-LINE   FROM  WK-GATE-NODATA
            // 016200 01 WK-GATE-NODATA.
            // 016300    02 FILLER                   PIC X(05) VALUE SPACES.
            // 016400    02 FILLER                   PIC X(12) VALUE " 本月無資料 ".
            // 016500    02 FILLER                   PIC X(113) VALUE SPACES.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 5));
            sb.append(formatUtil.padX(" 本月無資料 ", 12));
            sb.append(formatUtil.padX(" ", 113));
            fileC088Contents.add(sb.toString());
            // 040400     END-IF.
        }
        // 040500     MOVE      SPACES        TO    REPORT-LINE    .
        // 040600     WRITE     REPORT-LINE   FROM  WK-GATE-LINE3  .
        // 014200 01 WK-GATE-LINE3.
        // 014300    02 FILLER                   PIC X(130) VALUE ALL " ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 130));
        fileC088Contents.add(sb.toString());

        // 040700     MOVE      SPACES        TO    REPORT-LINE    .
        // 040800     WRITE     REPORT-LINE   FROM  WK-GATE-LINESPA.
        // 015800 01 WK-GATE-LINESPA.
        // 015900    02 FILLER                   PIC X(05) VALUE SPACES.
        // 016000    02 FILLER                   PIC X(10) VALUE " 以下空白 ".
        // 016100    02 FILLER                   PIC X(115) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 以下空白 ", 10));
        sb.append(formatUtil.padX(" ", 115));
        fileC088Contents.add(sb.toString());

        // 040900     MOVE      SPACES        TO    REPORT-LINE    .
        // 041000     WRITE     REPORT-LINE   FROM  WK-GATE-LINEEND.
        // 013600 01 WK-GATE-LINEEND.
        // 013700    02 FILLER                   PIC X(63) VALUE ALL "-".
        // 013800    02 FILLER                   PIC X(03) VALUE "END".
        // 013900    02 FILLER                   PIC X(64) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 63));
        sb.append(formatUtil.padX(" END ", 5));
        sb.append(reportUtil.makeGate("-", 64));
        fileC088Contents.add(sb.toString());

        // 041100     MOVE      SPACES        TO    REPORT-LINE    .
        // 041200     WRITE     REPORT-LINE   FROM  WK-GATE-LINE3  .
        // 014200 01 WK-GATE-LINE3.
        // 014300    02 FILLER                   PIC X(130) VALUE ALL " ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 130));
        fileC088Contents.add(sb.toString());

        // 041300     MOVE      SPACES        TO    REPORT-LINE    .
        // 041400     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO0  .
        // 016700 01 WK-LINE-MEMO0.
        // 016800    02 FILLER                   PIC X(03) VALUE SPACES.
        // 016900    02 FILLER                   PIC X(08) VALUE " 備註： ".
        // 017000    02 FILLER                   PIC X(119) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 備註： ", 8));
        sb.append(formatUtil.padX(" ", 119));
        fileC088Contents.add(sb.toString());

        // 041500     MOVE      SPACES        TO    REPORT-LINE    .
        // 041600     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO1  .
        // 017100 01 WK-LINE-MEMO1.
        // 017200    02 FILLER                   PIC X(03) VALUE SPACES.
        // 017300    02 FILLER                   PIC X(54) VALUE
        // 017400    " 一、所收款項帳入手續費收入 (410305) －電子商務手續費 ".
        // 017500    02 FILLER                   PIC X(30) VALUE
        // 017600    "(0145) －電子化收款 (0007) 。 ".
        // 017700    02 FILLER                   PIC X(43) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 一、所收款項帳入手續費收入 (410305) －電子商務手續費 ", 54));
        sb.append(formatUtil.padX("(0145) －電子化收款 (0007) 。 ", 30));
        sb.append(formatUtil.padX(" ", 43));
        fileC088Contents.add(sb.toString());

        // 041700     MOVE      SPACES        TO    REPORT-LINE    .
        // 041800     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO2  .
        // 017800 01 WK-LINE-MEMO2.
        // 017900    02 FILLER                   PIC X(03) VALUE SPACES.
        // 018000    02 FILLER                   PIC X(22) VALUE
        // 018100    " 二、管道類型說明如下 ".
        // 018200    02 FILLER                   PIC X(105) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 二、管道類型說明如下 ", 22));
        sb.append(formatUtil.padX(" ", 105));
        fileC088Contents.add(sb.toString());

        // 041900     MOVE      SPACES        TO    REPORT-LINE    .
        // 042000     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO3  .
        // 018300 01 WK-LINE-MEMO3.
        // 018400    02 FILLER                   PIC X(07) VALUE SPACES.
        // 018500    02 FILLER                   PIC X(28) VALUE
        // 018600    " １：全國繳費網－晶片金融卡 ".
        // 018700    02 FILLER                   PIC X(95) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" １：全國繳費網－晶片金融卡 ", 28));
        sb.append(formatUtil.padX(" ", 95));
        fileC088Contents.add(sb.toString());

        // 042100     MOVE      SPACES        TO    REPORT-LINE    .
        // 042200     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO4  .
        // 018800 01 WK-LINE-MEMO4.
        // 018900    02 FILLER                   PIC X(07) VALUE SPACES.
        // 019000    02 FILLER                   PIC X(18) VALUE
        // 019100    " ２： ID + Account".
        // 019200    02 FILLER                   PIC X(105) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ２： ID + Account", 18));
        sb.append(formatUtil.padX(" ", 105));
        fileC088Contents.add(sb.toString());

        // 042300     MOVE      SPACES        TO    REPORT-LINE    .
        // 042400     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO5  .
        // 019300 01 WK-LINE-MEMO5.
        // 019400    02 FILLER                   PIC X(07) VALUE SPACES.
        // 019500    02 FILLER                   PIC X(34) VALUE
        // 019600    " ３：晶片金融卡網路收單或簡易代收 ".
        // 019700    02 FILLER                   PIC X(89) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ３：晶片金融卡網路收單或簡易代收 ", 34));
        sb.append(formatUtil.padX(" ", 89));
        fileC088Contents.add(sb.toString());

        // 042500     MOVE      SPACES        TO    REPORT-LINE    .
        // 042600     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO6  .
        // 019800 01 WK-LINE-MEMO6.
        // 019900    02 FILLER                   PIC X(07) VALUE SPACES.
        // 020000    02 FILLER                   PIC X(28) VALUE
        // 020100    " ４：台灣 Pay QRCode －帳單 ".
        // 020200    02 FILLER                   PIC X(95) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ４：台灣 Pay QRCode －帳單 ", 28));
        sb.append(formatUtil.padX(" ", 95));
        fileC088Contents.add(sb.toString());

        // 042700     MOVE      SPACES        TO    REPORT-LINE    .
        // 042800     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO7  .
        // 020300 01 WK-LINE-MEMO7.
        // 020400    02 FILLER                   PIC X(07) VALUE SPACES.
        // 020500    02 FILLER                   PIC X(14) VALUE
        // 020600    " ５：ＰＯＳ機 ".
        // 020700    02 FILLER                   PIC X(102) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ５：ＰＯＳ機 ", 14));
        sb.append(formatUtil.padX(" ", 102));
        fileC088Contents.add(sb.toString());

        // 042900     MOVE      SPACES        TO    REPORT-LINE    .
        // 043000     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO8  .
        // 020800 01 WK-LINE-MEMO8.
        // 020900    02 FILLER                   PIC X(07) VALUE SPACES.
        // 021000    02 FILLER                   PIC X(28) VALUE
        // 021100    " ６：台灣 Pay QRCode －臨櫃 ".
        // 021200    02 FILLER                   PIC X(95) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ６：台灣 Pay QRCode －臨櫃 ", 28));
        sb.append(formatUtil.padX(" ", 95));
        fileC088Contents.add(sb.toString());

        // 043100     MOVE      SPACES        TO    REPORT-LINE    .
        // 043200     WRITE     REPORT-LINE   FROM  WK-LINE-MEMO9  .
        // 021300 01 WK-LINE-MEMO9.
        // 021400    02 FILLER                   PIC X(07) VALUE SPACES.
        // 021500    02 FILLER                   PIC X(18) VALUE
        // 021600    " ７：事業單位網站 ".
        // 021700    02 FILLER                   PIC X(105) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ７：事業單位網站 ", 18));
        sb.append(formatUtil.padX(" ", 105));
        fileC088Contents.add(sb.toString());

        // 043300     MOVE      SPACES        TO    REPORT-LINE    .
        // 043400     WRITE     REPORT-LINE   FROM  WK-LINE-MEMOA  .
        // 021800 01 WK-LINE-MEMOA.
        // 021900    02 FILLER                   PIC X(07) VALUE SPACES.
        // 022000    02 FILLER                   PIC X(10) VALUE
        // 022100    " ８：其他 ".
        // 022200    02 FILLER                   PIC X(113) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.padX(" ８：其他 ", 10));
        sb.append(formatUtil.padX(" ", 113));
        fileC088Contents.add(sb.toString());

        // 043600 3000-WTAIL-EXIT.
    }

    private void title(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr title ....");
        // 037700 2500-TITLE-RTN.

        //// 頁數加一
        // 037800     ADD         1             TO   WK-RPT-PAGE.
        wkRptPage = wkRptPage + 1;

        //// 寫報表表頭
        // 038000     MOVE       REC-CLMR-PBRNO  TO     WK-RPT-PBRNO  .
        wkRptPbrno = parse.string2Integer(recClmrPbrno);
        // 038100     MOVE       SPACES          TO     REPORT-LINE   .
        // 038200     WRITE      REPORT-LINE     FROM   WK-TITLE-LINE1 AFTER PAGE.
        // 007900 01 WK-TITLE-LINE1.
        // 008000    02 FILLER                          PIC X(30) VALUE SPACE.
        // 008100    02 FILLER                          PIC X(40) VALUE
        // 008200       " 臺灣銀行全國性繳費稅業務（手續費內含） ".
        // 008300    02 WK-RPT-YYY                      PIC 9(03).
        // 008400    02 FILLER                          PIC X(04) VALUE " 年 ".
        // 008500    02 WK-RPT-MM                       PIC 9(02).
        // 008600    02 FILLER                          PIC X(20) VALUE
        // 008700       " 月應收手續費月報表 ".
        // 008800    02 FILLER                          PIC X(31) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(pageFg); // 預留換頁符號
        sb.append(formatUtil.padX(" ", 30));
        sb.append(formatUtil.padX(" 臺灣銀行全國性繳費稅業務（手續費內含） ", 40));
        sb.append(formatUtil.padX(wkRptYYY, 3));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(formatUtil.padX(wkRptMM, 3));
        sb.append(formatUtil.padX(" 月應收手續費月報表 ", 20));
        sb.append(formatUtil.padX(" ", 31));
        fileC088Contents.add(sb.toString());

        // 038300     MOVE       SPACES          TO     REPORT-LINE   .
        // 038400     WRITE      REPORT-LINE     FROM   WK-TITLE-LINE2.
        // 008900 01 WK-TITLE-LINE2.
        // 009000    02 FILLER                          PIC X(05) VALUE SPACE.
        // 009100    02 FILLER                          PIC X(12) VALUE
        // 009200       " 報表名稱： ".
        // 009300    02 FILLER                          PIC X(08) VALUE
        // 009400       " CL-C088".
        // 009500    02 FILLER                          PIC X(75) VALUE SPACE.
        // 009600    02 FILLER                          PIC X(12) VALUE
        // 009700       " 頁　　次： ".
        // 009800    02 WK-RPT-PAGE                     PIC 9(03).
        // 009900    02 FILLER                          PIC X(15) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX(" CL-C088", 8));
        sb.append(formatUtil.padX(" ", 75));
        sb.append(formatUtil.padX(" 頁　　次： ", 12));
        sb.append(formatUtil.pad9("" + wkRptPage, 3));
        sb.append(formatUtil.padX(" ", 15));
        fileC088Contents.add(sb.toString());

        // 038500     MOVE       SPACES          TO     REPORT-LINE   .
        // 038600     WRITE      REPORT-LINE     FROM   WK-TITLE-LINE3.
        // 010000 01 WK-TITLE-LINE3.
        // 010100    02 FILLER                          PIC X(05) VALUE SPACE.
        // 010200    02 FILLER                          PIC X(12) VALUE
        // 010300       " 主辦分行： ".
        // 010400    02 FILLER                          PIC X(01) VALUE SPACE.
        // 010500    02 WK-RPT-PBRNO                    PIC 9(03).
        // 010600    02 FILLER                          PIC X(79) VALUE SPACE.
        // 010700    02 FILLER                          PIC X(16) VALUE
        // 010800       " 用　　途：參考 ".
        // 010900    02 FILLER                          PIC X(14) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.pad9("" + wkRptPbrno, 3));
        sb.append(formatUtil.padX(" ", 79));
        sb.append(formatUtil.padX(" 用　　途：參考 ", 16));
        sb.append(formatUtil.padX(" ", 14));
        fileC088Contents.add(sb.toString());

        // 038700     MOVE       SPACES          TO     REPORT-LINE   .
        // 038800     WRITE      REPORT-LINE     FROM   WK-TITLE-LINE4.
        // 011000 01 WK-TITLE-LINE4.
        // 011100    02 FILLER                          PIC X(05) VALUE SPACE.
        // 011200    02 FILLER                          PIC X(12) VALUE
        // 011300       " 印表日期： ".
        // 011400    02 FILLER                          PIC X(01) VALUE SPACE.
        // 011500    02 WK-RPT-PDATE                    PIC Z99/99/99.
        // 011600    02 FILLER                          PIC X(73) VALUE SPACE.
        // 011700    02 FILLER                          PIC X(16) VALUE
        // 011800       " 保存年限：２年 ".
        // 011900    02 FILLER                          PIC X(14) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(reportUtil.customFormat(wkRptPDate, "Z999/99/99"));
        sb.append(formatUtil.padX(" ", 73));
        sb.append(formatUtil.padX(" 保存年限：２年 ", 16));
        sb.append(formatUtil.padX(" ", 14));
        fileC088Contents.add(sb.toString());

        // 038900     MOVE       SPACES          TO     REPORT-LINE   .
        // 039000     WRITE      REPORT-LINE     FROM   WK-GATE-LINE3 .
        // 014200 01 WK-GATE-LINE3.
        // 014300    02 FILLER                   PIC X(130) VALUE ALL " ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 130));
        fileC088Contents.add(sb.toString());

        // 039100     MOVE       SPACES          TO     REPORT-LINE   .
        // 039200     WRITE      REPORT-LINE     FROM   WK-TITLE-LINE5.
        // 012000 01 WK-TITLE-LINE5.
        // 012100    02 FILLER    PIC X(05) VALUE SPACES      .
        // 012200    02 FILLER    PIC X(10) VALUE " 代收類別 ".
        // 012300    02 FILLER    PIC X(02) VALUE SPACES.
        // 012400    02 FILLER    PIC X(10) VALUE " 單位名稱 ".
        // 012500    02 FILLER    PIC X(34) VALUE SPACE.
        // 012600    02 FILLER    PIC X(08) VALUE " 總筆數 ".
        // 012700    02 FILLER    PIC X(02) VALUE SPACE.
        // 012800    02 FILLER    PIC X(24) VALUE " 內含手續費率（元／筆） ".
        // 012900    02 FILLER    PIC X(10) VALUE " 應收金額 ".
        // 013000    02 FILLER    PIC X(02) VALUE SPACES.
        // 013100    02 FILLER    PIC X(10) VALUE " 管道類型 ".
        // 013200    02 FILLER    PIC X(13) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 單位名稱 ", 10));
        sb.append(formatUtil.padX(" ", 34));
        sb.append(formatUtil.padX(" 總筆數 ", 8));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 內含手續費率（元／筆） ", 24));
        sb.append(formatUtil.padX(" 應收金額 ", 10));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 管道類型 ", 10));
        sb.append(formatUtil.padX(" ", 13));
        fileC088Contents.add(sb.toString());

        // 039300     MOVE       SPACES          TO     REPORT-LINE   .
        // 039400     WRITE      REPORT-LINE     FROM   WK-GATE-LINE  .
        // 013400 01 WK-GATE-LINE.
        // 013500    02 FILLER                   PIC X(130) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 130));
        fileC088Contents.add(sb.toString());

        // 039600     MOVE       1               TO     WK-CNT.
        wkCnt = 1;
        // 039700 2500-TITLE-EXIT.
    }

    private void rkputh() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr rkputh ....");
        // 033900 1500-RKPUTH-RTN.
        // 034000 1500-RKPUTH-LOOP.
        // 034100     IF  WK-STOPKPUTH    = "N"
        if ("N".equals(wkStopKPuth)) {
            List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
            int cnt = 0;
            //// 循序讀取 FD-KPUTH，直到檔尾，跳到1500-RKPUTH-REND
            for (String detail : lines) {
                cnt++;
                //  05 KPUTH-ENTPNO	X(10)   0-10
                // 03 KPUTH-CODE	X(06)	代收類別 10-16
                // 03 KPUTH-RCPTID	X(16)	銷帳號碼 16-32
                // 03 KPUTH-DATE	9(07)	代收日 32-39
                // 03 KPUTH-TIME	9(06)	代收時間 39-45
                // 03 KPUTH-CLLBR	9(03)	代收行 45-48
                // 03 KPUTH-LMTDATE	9(06)	繳費期限 48-54
                // 03 KPUTH-AMT	9(10)	繳費金額 54-64
                // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
                // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
                // 03 KPUTH-TXTYPE	X(01)	帳務別 111-112
                // 03 KPUTH-SERINO	9(06)	交易明細流水序號 112-118
                // 03 KPUTH-PBRNO	9(03)	主辦分行 118-121
                // 03 KPUTH-UPLDATE	9(07)	  121-128
                // 03 KPUTH-FEETYPE	9(01)   128-129
                // 03 KPUTH-FEEO2L	9(05)V99
                kPuthPbrno = parse.string2Integer(detail.substring(118, 121));
                kPuthCode = parse.string2Integer(detail.substring(10, 16));
                // 034200         READ  FD-KPUTH  AT END      GO TO  1500-RKPUTH-REND.
                // 034400* 判斷分行別
                // 034500     IF  REC-CLMR-PBRNO  >   KPUTH-PBRNO
                if (parse.string2Integer(recClmrPbrno) > kPuthPbrno) {
                    // 034600         MOVE  "N"       TO  WK-STOPKPUTH
                    wkStopKPuth = "N";
                    // 034700         GO TO 1500-RKPUTH-LOOP.

                    // 037000 1500-RKPUTH-REND.
                    //// 檔尾IF REC-CLMR-CODE = KPUTH-CODE,寫明細
                    if (cnt == lines.size()) {
                        // 037100     IF  REC-CLMR-CODE  =   KPUTH-CODE
                        if (parse.string2Integer(recClmrCode) == kPuthCode) {
                            // 037200         PERFORM 4000-DTLLINE-RTN    THRU   4000-DTLLINE-EXIT.
                            dtlline();
                        }
                        // 037300     MOVE  "N"     TO   WK-FKPUTH.
                        wkFKPuth = "N";
                    }
                    continue;
                }
                // 034900     IF  REC-CLMR-PBRNO  <   KPUTH-PBRNO
                if (parse.string2Integer(recClmrPbrno) < kPuthPbrno) {
                    // 035000         MOVE  "Y"       TO  WK-STOPKPUTH
                    wkStopKPuth = "Y";
                    // 035100         GO TO 1500-RKPUTH-EXIT.
                    return;
                }
                // 035300* 判斷代收類別
                // 035400     IF  REC-CLMR-CODE   <   KPUTH-CODE
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "C088Lsnr recClmrCode= {} ,kPuthCode = {} ",
                        recClmrCode,
                        kPuthCode);
                if (parse.string2Integer(recClmrCode) < kPuthCode) {
                    // 035500         IF  WK-REC-CNT NOT = 0   AND  WK-STOPKPUTH = "N"
                    if (wkRecCnt != 0 && "N".equals(wkStopKPuth)) {
                        // 035600             PERFORM 4000-DTLLINE-RTN    THRU   4000-DTLLINE-EXIT
                        dtlline();
                        // 035700             MOVE  "Y"      TO  WK-STOPKPUTH
                        wkStopKPuth = "Y";
                        // 035800         END-IF
                    }
                    // 035900         GO TO 1500-RKPUTH-EXIT
                    return;
                } else {
                    // 036000     ELSE
                    // 036100         MOVE  "N"      TO  WK-STOPKPUTH.
                    wkStopKPuth = "N";
                }
                // 036300     IF  REC-CLMR-CODE  >   KPUTH-CODE
                if (parse.string2Integer(recClmrCode) > kPuthCode) {
                    // 036400         GO TO 1500-RKPUTH-LOOP.
                    continue;
                }
                // 036600     IF  REC-CLMR-CODE  =   KPUTH-CODE
                if (parse.string2Integer(recClmrCode) == kPuthCode) {
                    // 036700         ADD  1                  TO  WK-REC-CNT.
                    wkRecCnt = wkRecCnt + 1;
                }
                // 036900     GO  TO   1500-RKPUTH-LOOP.

                // 037000 1500-RKPUTH-REND.
                //// 檔尾IF REC-CLMR-CODE = KPUTH-CODE,寫明細
                if (cnt == lines.size()) {
                    // 037100     IF  REC-CLMR-CODE  =   KPUTH-CODE
                    if (parse.string2Integer(recClmrCode) == kPuthCode) {
                        // 037200         PERFORM 4000-DTLLINE-RTN    THRU   4000-DTLLINE-EXIT.
                        dtlline();
                    }
                    // 037300     MOVE  "N"     TO   WK-FKPUTH.
                    wkFKPuth = "N";
                }
            }
        }
        // 037400 1500-RKPUTH-EXIT.

    }

    private void dtlline() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C088Lsnr dtlline ....");
        // 043900 4000-DTLLINE-RTN.

        // 換頁
        // 044000     IF   WK-CNT > 46
        if (wkCnt > 46) {
            // 044100       PERFORM  2500-TITLE-RTN    THRU   2500-TITLE-EXIT.
            title(PAGE_SEPARATOR);
        }
        //// 寫報表明細
        // 044300     MOVE       REC-CLMR-CODE     TO   WK-RPT-CODE    .
        // 044400     MOVE       REC-CLMR-CNAME    TO   WK-RPT-CNAME   .
        // 044500     MOVE       WK-REC-CNT        TO   WK-RPT-CNT     .
        // 044600     MOVE       REC-CLMR-CFEEEB   TO   WK-RPT-CFEEEB  .
        // 044700     COMPUTE    WK-RPT-SUMCFEE    = WK-REC-CNT * REC-CLMR-CFEEEB.
        // 044800     MOVE       REC-CLMR-EBTYPE   TO   WK-RPT-EBTYPE  .
        wkRptCode = parse.string2Integer(recClmrCode);
        wkRptCname = recClmrCname;
        wkRptCnt = wkRecCnt;
        wkRptCfeeeb = parse.string2BigDecimal(recClmrCfeeeb);
        wkRptSumCFee = new BigDecimal(wkRecCnt).multiply(parse.string2BigDecimal(recClmrCfeeeb));
        wkRptEbtype = recClmrEbtype;
        // 044900     MOVE       SPACES            TO   REPORT-LINE    .
        // 045000     WRITE      REPORT-LINE       FROM WK-DTL-LINE    .
        // 014400 01 WK-DTL-LINE.
        // 014500    02 FILLER                   PIC X(07) VALUE SPACES.
        // 014600    02 WK-RPT-CODE              PIC X(06) VALUE SPACES.
        // 014700    02 FILLER                   PIC X(04) VALUE SPACES.
        // 014800    02 WK-RPT-CNAME             PIC X(40) VALUE SPACES.
        // 014900    02 FILLER                   PIC X(02) VALUE SPACES.
        // 015000    02 WK-RPT-CNT               PIC Z,ZZZ,ZZ9.
        // 015100    02 FILLER                   PIC X(08) VALUE SPACES.
        // 015200    02 WK-RPT-CFEEEB            PIC ZZ9.99.
        // 015300    02 FILLER                   PIC X(13) VALUE SPACES.
        // 015400    02 WK-RPT-SUMCFEE           PIC Z,ZZZ,ZZ9.
        // 015500    02 FILLER                   PIC X(05) VALUE SPACES.
        // 015600    02 WK-RPT-EBTYPE            PIC X(10) VALUE SPACES.
        // 015700    02 FILLER                   PIC X(11) VALUE SPACES.

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 7));
        sb.append(formatUtil.pad9("" + wkRptCode, 6));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(wkRptCname, 40));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(reportUtil.customFormat("" + wkRptCnt, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(reportUtil.customFormat("" + wkRptCfeeeb, "ZZ9.99"));
        sb.append(formatUtil.padX(" ", 13));
        sb.append(reportUtil.customFormat("" + wkRptSumCFee, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(wkRptEbtype, 10));
        sb.append(formatUtil.padX(" ", 11));
        fileC088Contents.add(sb.toString());

        // 045200     ADD        1                 TO   WK-CNT         .
        // 045300     MOVE       0                 TO   WK-REC-CNT.
        wkCnt = wkCnt + 1;
        wkRecCnt = 0;
        // 045400 4000-DTLLINE-EXIT.
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
