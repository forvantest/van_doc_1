/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.BAF3;
import com.bot.ncl.dto.entities.ClbafbyEntdyBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
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
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class BAF3Lsnr extends BatchListenerCase<BAF3> {

    @Autowired private ClbafService clbafService;
    @Autowired private ClmrService clmrService;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private BAF3 event;
    private static final String FILE_BINB35_NAME = "BINB35";
    private static final String FILE_020_NAME = "CL-BH-020";
    private static final String FILE_SORTFL_NAME = "BAF3SORTFL";
    private static final String FILE_DIR = "DLY/";

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir2;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET2 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String fileBINB35Path;
    private String file020Path;
    private String fileSortflPath;
    private List<String> fileBINB35Contents; // 檔案內容
    private List<String> file020Contents; // 報表內容
    private List<String> fileSortContents; // 暫存排序內容
    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private String wkYYY;
    private String wkMM;
    private String wkDD;
    private int cllbr; // 代收行
    private int pbrno; // 主辦行
    private Bctl bctl;
    private HashMap<Integer, BigDecimal> cllbrDbAmt = new HashMap<Integer, BigDecimal>();
    private HashMap<Integer, Integer> cllbrDbCnt = new HashMap<Integer, Integer>();
    private HashMap<Integer, BigDecimal> cllbrCrAmt = new HashMap<Integer, BigDecimal>();
    private HashMap<Integer, Integer> cllbrCrCnt = new HashMap<Integer, Integer>();
    private List<Integer> lCllbr = new ArrayList<Integer>();
    private DecimalFormat decimalFormat = new DecimalFormat("0000000000000.00");
    private DecimalFormat decimal020AmtFormat = new DecimalFormat("#########.00");
    private StringBuilder sb = new StringBuilder();
    // 空白24,20,4
    private String SPACE24 = "                        ";
    private String SPACE20 = "                    ";
    private String SPACE4 = "    ";
    // 頁尾總計
    private int lastDbCnt = 0;
    private BigDecimal lastDbAmt = BigDecimal.ZERO;
    private int lastCrCnt = 0;
    private BigDecimal lastCrAmt = BigDecimal.ZERO;
    // 換頁明細筆數 37
    private int NOWPAGECNT = 37;
    // 頁首行數
    private int HEADERROW = 8;
    // 頁首年月行數
    private int HEADERDATEROW = 4;
    private String PAGE_SEPARATOR = "\u000C";
    private static final String PATH_SEPARATOR = File.separator;
    private int wkCllbr;
    private BigDecimal wkAmt1 = BigDecimal.ZERO;
    private BigDecimal wkAmt2 = BigDecimal.ZERO;
    private BigDecimal wkAmt1t = BigDecimal.ZERO;
    private BigDecimal wkAmt2t = BigDecimal.ZERO;
    private int wkPctl;
    private int wkCnt1;
    private int wkCnt2;
    private int wkCnt1t;
    private int wkCnt2t;

    private int wk003Has = 0;
    private BigDecimal wk003Dbamt = BigDecimal.ZERO;

    private int wk003Dbcnt = 0;

    private BigDecimal wk003Cramt = BigDecimal.ZERO;

    private int wk003Crcnt = 0;
    private String wkCode = "";
    private String wkCode1 = "";
    private BigDecimal sAmt1 = BigDecimal.ZERO;
    private BigDecimal sAmt2 = BigDecimal.ZERO;
    private int sCnt1 = 0;
    private int sCnt2 = 0;
    private int sCllbr = 0;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(BAF3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(BAF3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr run()");
        init(event);
        // 011100     PERFORM 0000-MAIN-RTN   THRU    0000-MAIN-EXIT.
        mainRtn();

        try {
            textFile.writeFileContent(file020Path, file020Contents, CHARSET2);
            upload(file020Path, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(fileBINB35Path, fileBINB35Contents, CHARSET);
            upload(fileBINB35Path, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        batchResponse();
    }

    private void init(BAF3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr init");
        this.event = event;
        // 抓批次營業日
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkYYY = processDate.substring(0, 3);
        wkMM = processDate.substring(3, 5);
        wkDD = processDate.substring(5, 7);
        fileBINB35Path =
                fileDir2
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_DIR
                        + FILE_BINB35_NAME;
        file020Path =
                fileDir2
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_020_NAME;
        fileSortflPath =
                fileDir2
                        + "DATA"
                        + File.separator
                        + processDate
                        + File.separator
                        + FILE_SORTFL_NAME;
        fileBINB35Contents = new ArrayList<>();
        file020Contents = new ArrayList<>();
        fileSortContents = new ArrayList<>();
        textFile.deleteFile(fileBINB35Path);
        textFile.deleteFile(fileSortflPath);
        textFile.deleteFile(file020Path);
        //// 清變數值
        // 010900     MOVE    0     TO  WK-CLLBR,WK-AMT1,WK-AMT2,WK-AMT1T,WK-AMT2T,
        // 011000                       WK-PCTL ,WK-CNT1,WK-CNT2,WK-CNT1T,WK-CNT2T.
        wkCllbr = 0;
        wkAmt1 = new BigDecimal(0);
        wkAmt2 = new BigDecimal(0);
        wkAmt1t = new BigDecimal(0);
        wkAmt2t = new BigDecimal(0);
        wkPctl = 0;
        wkCnt1 = 0;
        wkCnt2 = 0;
        wkCnt1t = 0;
        wkCnt2t = 0;
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr mainRtn");
        // 011800 0000-MAIN-RTN.

        //// SORT INPUT 段落：CS-SORTIN(讀收付累計檔，挑資料，並寫至SORTFL)
        //// 資料照 S-CLLBR 由小到大排序
        //// SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫報表檔及FD-BINB35

        // 011900     SORT    SORTFL
        // 012000             ASCENDING KEY        S-CLLBR
        // 012001     MEMORY SIZE 6 MODULES
        // 012002     DISK SIZE 50 MODULES
        // 012003             INPUT  PROCEDURE     CS-SORTIN
        csSortin();
        // 012200             OUTPUT PROCEDURE     CS-SORTOUT
        csSortout();
        // 012500 0000-MAIN-EXIT.
    }

    private void csSortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr csSortin");
        // 012800 CS-SORTIN     SECTION.
        // 012900 CS-SORTIN-RTN.

        //// FIND NEXT DB-CLBAF-DDS收付累計檔，若有誤(讀檔結束)，結束本節

        // 013000     FIND NEXT DB-CLBAF-DDS
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLBAF3 YYYMMDD={}", processDate);

        List<ClbafbyEntdyBus> lClbaf =
                clbafService.findbyEntdy(parse.string2Integer(processDate), 0, Integer.MAX_VALUE);
        // 013100       ON EXCEPTION
        if (Objects.isNull(lClbaf)) {
            // 013300          GO TO   CS-SORTIN-EXIT.
            return;
        }
        //// 代收日 不等於 本營業日 ，跳掉這筆資料
        //// GO TO CS-SORTIN-RTN找下一筆收付累計檔
        // 013400     IF        DB-CLBAF-DATE     NOT =  WK-YYYMMDD
        // 013500       GO TO   CS-SORTIN-RTN.
        for (ClbafbyEntdyBus tClbaf : lClbaf) {
            Boolean goToCsSortin999 = false;
            //// CODE(1:3)="121"，跳掉這筆資料
            //// GO TO CS-SORTIN-RTN找下一筆收付累計檔
            //
            // 013520*** 當代收類別前三位不為１２１時其性質為代收，在此處不予處理
            // 013540     MOVE      DB-CLBAF-CODE     TO     WK-CODE.
            wkCode = tClbaf.getCode();
            wkCode1 = wkCode.substring(0, 3);
            // 013560     IF        WK-CODE1         NOT =   "121"
            if (!"121".equals(wkCode1)) {
                // 013580       GO TO   CS-SORTIN-RTN.
                continue;
            }
            //// DB-CLBAF-CLLBR=999(該代收類別之總累計資料)，
            //// GO TO CS-SORTIN-999 寫貸方資料
            // 013600     IF        DB-CLBAF-CLLBR    =      999
            if (tClbaf.getCllbr() == 999) {
                // 013800       GO  TO  CS-SORTIN-999.
                goToCsSortin999 = true;
            }
            if (!goToCsSortin999) {
                // 搬相關欄位至SORT-REC(S-AMT1=DB-CLBAF-AMT,SCN1=DB-CLBAF-CNT,S-AMT2=0,S-CNT2=0)，並寫至SORTFL
                //// 累計DB-CLBAF-AMT、DB-CLBAF-CNT到WK-AMT1T、WK-CNT1T

                //// DB-CLBAF-AMT   該業務在當日之累計代收金額
                //// DB-CLBAF-CNT   該業務在當日之累計代收筆數
                //// DB-CLBAF-CLLBR 代收行

                // 014300     MOVE       0                TO     S-AMT2,S-CNT2.
                sAmt2 = new BigDecimal(0);
                sCnt2 = 0;
                // 014400     ADD        DB-CLBAF-AMT     TO     WK-AMT1,WK-AMT1T.
                wkAmt1 = wkAmt1.add(tClbaf.getAmt());
                wkAmt1t = wkAmt1t.add(tClbaf.getAmt());
                // 014500     ADD        DB-CLBAF-CNT     TO     WK-CNT1,WK-CNT1T.
                wkCnt1 = wkCnt1 + tClbaf.getCnt();
                wkCnt1t = wkCnt1t + tClbaf.getCnt();
                // 014600     MOVE       WK-AMT1          TO     S-AMT1.
                sAmt1 = wkAmt1;
                // 014700     MOVE       WK-CNT1          TO     S-CNT1.
                sCnt1 = wkCnt1;
                // 014800     MOVE       DB-CLBAF-CLLBR   TO     S-CLLBR.
                sCllbr = tClbaf.getCllbr();
                // 014900     RELEASE    SORT-REC.
                // 004300 01   SORT-REC.
                // 004400     03  S-CLLBR                PIC 9(03).
                // 004500     03  S-AMT1                 PIC 9(13).
                // 004600     03  S-AMT2                 PIC 9(13).
                // 004700     03  S-CNT1                 PIC 9(09).
                // 004800     03  S-CNT2                 PIC 9(09).
                sb = new StringBuilder();
                sb.append(formatUtil.pad9("" + sCllbr, 3));
                sb.append(formatUtil.pad9("" + sAmt1, 13));
                sb.append(formatUtil.pad9("" + sAmt2, 13));
                sb.append(formatUtil.pad9("" + sCnt1, 9));
                sb.append(formatUtil.pad9("" + sCnt2, 9));
                fileSortContents.add(sb.toString());
                // 015000     MOVE       0                TO     WK-AMT1,WK-CNT1.
                wkAmt1 = new BigDecimal(0);
                wkCnt1 = 0;

                //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔

                // 015100     GO TO      CS-SORTIN-RTN.
                continue;
            }
            // 015200 CS-SORTIN-999.

            //// 依代收類別FIND DB-CLMR-DDS事業單位基本資料檔，若有誤
            ////  顯示錯誤訊息，異常，結束程式

            // 015900     FIND       DB-CLMR-IDX1   AT DB-CLMR-CODE   = DB-CLBAF-CODE
            ClmrBus tClmr = clmrService.findById(tClbaf.getCode());
            // 016000       ON EXCEPTION
            if (Objects.isNull(tClmr)) {
                // 016100       DISPLAY "CAN'T USE CLBAF-CODE FIND CLMR-CODE="DB-CLBAF-CODE
                // 016200       CALL SYSTEM DMTERMINATE.
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "CAN'T USE CLBAF-CODE FIND CLMR-CODE={}",
                        tClbaf.getCode());
                // 014100       CALL SYSTEM DMTERMINATE. //TODO:DMTERMINATE?
                continue;
            }
            //// 搬相關欄位至SORT-REC(S-AMT1=0,S-CNT1=0,S-AMT2=DB-CLBAF-AMT,SCN2=DB-CLBAF-CNT)，並寫至SORTFL
            //// 累計DB-CLBAF-AMT、DB-CLBAF-CNT到WK-AMT2T、WK-CNT2T
            //// DB-CLBAF-AMT   該業務在當日之累計代收金額
            //// DB-CLBAF-CNT   該業務在當日之累計代收筆數

            // 016300     MOVE       DB-CLBAF-AMT        TO     WK-AMT2.
            // 016400     MOVE       DB-CLBAF-CNT        TO     WK-CNT2.
            wkAmt2 = tClbaf.getAmt();
            wkCnt2 = tClbaf.getCnt();

            //// DB-CLMR-PBRNO主辦分行
            // 016700     MOVE       DB-CLMR-PBRNO       TO     S-CLLBR.
            sCllbr = tClmr.getPbrno();
            // 016800     ADD        WK-AMT2             TO     WK-AMT2T.
            wkAmt2t = wkAmt2t.add(wkAmt2);
            // 016900     ADD        WK-CNT2             TO     WK-CNT2T.
            wkCnt2t = wkCnt2t + wkCnt2;
            // 017000     MOVE       0                   TO     S-AMT1,S-CNT1.
            sAmt1 = new BigDecimal(0);
            sCnt1 = 0;
            // 017100     MOVE       WK-AMT2             TO     S-AMT2.
            sAmt2 = wkAmt2;
            // 017200     MOVE       WK-CNT2             TO     S-CNT2.
            sCnt2 = wkCnt2;
            // 017300     RELEASE    SORT-REC.
            // 004300 01   SORT-REC.
            // 004400     03  S-CLLBR                PIC 9(03). 0-3
            // 004500     03  S-AMT1                 PIC 9(13). 3-16
            // 004600     03  S-AMT2                 PIC 9(13). 16-29
            // 004700     03  S-CNT1                 PIC 9(09). 29-38
            // 004800     03  S-CNT2                 PIC 9(09). 38-47
            sb = new StringBuilder();
            sb.append(formatUtil.pad9("" + sCllbr, 3));
            sb.append(formatUtil.pad9("" + sAmt1, 13));
            sb.append(formatUtil.pad9("" + sAmt2, 13));
            sb.append(formatUtil.pad9("" + sCnt1, 9));
            sb.append(formatUtil.pad9("" + sCnt2, 9));
            fileSortContents.add(sb.toString());
            // 017400     MOVE       0                   TO     WK-AMT2,WK-CNT2.

            //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔

            // 017500     GO TO      CS-SORTIN-RTN.
        }
        // 017600 CS-SORTIN-EXIT.
        // 寫暫存檔排序
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "BAF3Lsnr List={}",
                fileSortContents.toString());

        if (!fileSortContents.isEmpty()) {
            try {
                textFile.writeFileContent(fileSortflPath, fileSortContents, CHARSET2);
                upload(fileSortflPath, "DATA", "");
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            File tmpFile = new File(fileSortflPath);
            List<KeyRange> keyRanges = new ArrayList<>();
            keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
            externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET2);
        }
    }

    private void csSortout() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr csSortout");

        // 017900 CS-SORTOUT      SECTION.
        // 018000 CS-SORTOUT-RTN.
        // 018100**** 001  行不會有代付資料 , 所以可以用來判斷是否為第一筆
        //// 預設變數值WK-CLLBR=001
        //// 清變數值
        // 018200     MOVE       001            TO      WK-CLLBR.
        wkCllbr = 1;
        // 018300     MOVE       0              TO      WK-AMT1,WK-AMT2
        // 018400                                      ,WK-CNT1,WK-CNT2.
        wkAmt1 = new BigDecimal(0);
        wkAmt2 = new BigDecimal(0);
        wkCnt1 = 0;
        wkCnt2 = 0;
        // 018500 CS-SORTOUT-RETURN.
        if (!textFile.exists(fileSortflPath)) {
            return;
        }
        //// 將SORT後的記錄讀出，直到檔尾
        ////  若WK-CLLBR=001，表無資料，GO TO CS-SORTOUT-EXIT，結束本節

        ////  其他
        ////   若WK-PCTL=45
        ////    A.097-WRITEA-RTN 寫表頭
        ////    B.090-WRITED-RTN 寫報表明細&FD-BINB35
        ////    C.092-WRITE003-RTN 寫FD-BINB37(WK-BRNO=000,003,902,906之累計)
        ////    D.095-WRITET-RTN 寫表尾-總數
        ////    E.GO TO CS-SORTOUT-EXIT，結束本節
        ////   若WK-PCTL<>45
        ////    A.090-WRITED-RTN 寫報表明細&FD-BINB35
        ////    B.092-WRITE003-RTN 寫FD-BINB35(WK-BRNO=000,003,902,906之累計)
        ////    C.095-WRITET-RTN 寫表尾-總數
        ////    D.GO TO CS-SORTOUT-EXIT，結束本節

        // 018600     RETURN     SORTFL    AT END
        List<String> lines = textFile.readFileContent(fileSortflPath, CHARSET2);
        // 018700       IF       WK-CLLBR           =       001
        // 018800             GO TO   CS-SORTOUT-EXIT
        // 018900       ELSE

        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            //// 若CLLBR相同，累計S-...(筆數、金額)到WK-...；LOOP讀下一筆SORTFL
            //// 若CLLBR不相同，
            ////  若WK-CLLBR=001，表第1筆資料，往下一步驟執行
            ////  其他，表處理到不同分行資料，出表&寫檔
            String sCllbr = detail.substring(0, 3);
            String sAmt1 = detail.substring(3, 16);
            String sAmt2 = detail.substring(16, 29);
            String sCnt1 = detail.substring(29, 38);
            String sCnt2 = detail.substring(38, 47);
            // 020000     IF         WK-CLLBR       =       S-CLLBR
            if (wkCllbr == parse.string2Integer(sCllbr)) {
                // 020100       ADD      S-AMT1         TO      WK-AMT1
                wkAmt1 = wkAmt1.add(parse.string2BigDecimal(sAmt1));
                // 020200       ADD      S-CNT1         TO      WK-CNT1
                wkCnt1 = wkCnt1 + parse.string2Integer(sCnt1);
                // 020300       ADD      S-AMT2         TO      WK-AMT2
                wkAmt2 = wkAmt2.add(parse.string2BigDecimal(sAmt2));
                // 020400       ADD      S-CNT2         TO      WK-CNT2
                wkCnt2 = wkCnt2 + parse.string2Integer(sCnt2);
                // 020500       GO TO CS-SORTOUT-RETURN
                // 尾筆處理
                if (cnt == lines.size()) {
                    // 019000         IF         WK-PCTL        =       45
                    if (wkPctl == 45) {
                        // 019100                MOVE    0              TO      WK-PCTL
                        wkPctl = 0;
                        // 019200                PERFORM 097-WRITEA-RTN THRU    097-WRITEA-EXIT
                        writea097Rtn(PAGE_SEPARATOR);
                        // 019300                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 019350                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                        write003092Rtn();
                        // 019400                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                        writet095Rtn();
                        // 019500                GO TO   CS-SORTOUT-EXIT
                    } else {
                        // 019600         ELSE
                        // 019700                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 019750                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                        write003092Rtn();
                        // 019800                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                        writet095Rtn();
                        // 019900                GO TO   CS-SORTOUT-EXIT
                    }
                    // 019920         END-IF
                }
                continue;
            } else {
                // 020600     ELSE
                // 020700       IF       WK-CLLBR       =       001
                if (wkCllbr == 1) {
                    // 020800         NEXT SENTENCE
                } else {
                    // 020900       ELSE
                    //// WK-PCTL累加1
                    // 021000         ADD        1              TO      WK-PCTL
                    wkPctl = wkPctl + 1;
                    // 021100         IF         WK-PCTL        =   1   OR = 46
                    if (wkPctl == 1 || wkPctl == 46) {
                        // 021200           MOVE     1              TO      WK-PCTL
                        wkPctl = 1;
                        // 021300           PERFORM  097-WRITEA-RTN THRU    097-WRITEA-EXIT
                        writea097Rtn(PAGE_SEPARATOR);
                        // 021400           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 021500           MOVE     0       TO     WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2
                    } else {
                        // 021600         ELSE
                        // 021700           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 021800           MOVE     0       TO    WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2.
                        wkAmt1 = new BigDecimal(0);
                        wkAmt2 = new BigDecimal(0);
                        wkCnt1 = 0;
                        wkCnt2 = 0;
                    }
                }
            }
            ////  搬S-...(筆數、金額)到變數WK-...
            ////  保留S-CLLBR到變數WK-CLLBR
            // 021900     MOVE       S-AMT1         TO      WK-AMT1.
            wkAmt1 = parse.string2BigDecimal(sAmt1);
            // 022000     MOVE       S-CNT1         TO      WK-CNT1.
            wkCnt1 = parse.string2Integer(sCnt1);
            // 022100     MOVE       S-AMT2         TO      WK-AMT2.
            wkAmt2 = parse.string2BigDecimal(sAmt2);
            // 022200     MOVE       S-CNT2         TO      WK-CNT2.
            wkCnt2 = parse.string2Integer(sCnt2);
            // 022300     MOVE       S-CLLBR        TO      WK-CLLBR.
            wkCllbr = parse.string2Integer(sCllbr);
            //// LOOP讀下一筆SORTFL
            // 尾筆處理
            if (cnt == lines.size()) {
                // 019000         IF         WK-PCTL        =       45
                if (wkPctl == 45) {
                    // 019100                MOVE    0              TO      WK-PCTL
                    wkPctl = 0;
                    // 019200                PERFORM 097-WRITEA-RTN THRU    097-WRITEA-EXIT
                    writea097Rtn(PAGE_SEPARATOR);
                    // 019300                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                    writed090Rtn();
                    // 019350                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                    write003092Rtn();
                    // 019400                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                    writet095Rtn();
                    // 019500                GO TO   CS-SORTOUT-EXIT
                } else {
                    // 019600         ELSE
                    // 019700                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                    writed090Rtn();
                    // 019750                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                    write003092Rtn();
                    // 019800                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                    writet095Rtn();
                    // 019900                GO TO   CS-SORTOUT-EXIT
                }
                // 019920         END-IF
            }

            // 022400     GO TO CS-SORTOUT-RETURN.
        }

        textFile.deleteFile(fileSortflPath);
        // 022500 CS-SORTOUT-EXIT.
    }

    private void writea097Rtn(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr writea097Rtn");
        // 026200 097-WRITEA-RTN.
        //// 寫REPORTFL(表頭)
        // 026300     MOVE       SPACES         TO    REPORT-LINE.
        // 026400     WRITE      REPORT-LINE    AFTER PAGE.
        sb = new StringBuilder();
        sb.append(pageFg);
        file020Contents.add(sb.toString());

        // 026500     WRITE      REPORT-LINE    AFTER 2 LINES.
        file020Contents.add("");
        file020Contents.add("");

        // 026600     MOVE       WK-YYY         TO    WK-YYY-P.
        String wkYYYP = wkYYY;
        // 026700     MOVE       WK-MM          TO    WK-MM-P.
        String wkMMP = wkMM;
        // 026800     MOVE       WK-DD          TO    WK-DD-P.
        String wkDDP = wkDD;
        // 026900     WRITE      REPORT-LINE    FROM  WK-DATE-LINE.
        // 007400 01 WK-DATE-LINE.
        // 007500    02 FILLER                          PIC X(23) VALUE SPACES.
        // 007600    02 WK-YYY-P                        PIC 9(03).
        // 007700    02 FILLER                          PIC X(04) VALUE SPACES.
        // 007800    02 WK-MM-P                         PIC 9(02).
        // 007900    02 FILLER                          PIC X(04) VALUE SPACES.
        // 008000    02 WK-DD-P                         PIC 9(02).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 23));
        sb.append(formatUtil.pad9(wkYYYP, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkMMP, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkDDP, 2));
        file020Contents.add(sb.toString());

        // 027000     MOVE       SPACES         TO    REPORT-LINE.
        // 027100     WRITE      REPORT-LINE   BEFORE 3 LINES.
        sb = new StringBuilder();
        file020Contents.add("");
        file020Contents.add("");
        file020Contents.add("");

        // 027200 097-WRITEA-EXIT.
    }

    private void writed090Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr writed090Rtn");
        // 022900 090-WRITED-RTN.
        // 023000     MOVE       SPACES         TO    REPORT-LINE.
        // 023100     MOVE       WK-CLLBR       TO    WK-BRNO.
        int wkBrno = wkCllbr;
        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定營業單位中文名稱變數值；
        //// 若有誤，搬空白

        String wkBrnoN = formatUtil.padX("", 20);
        // 023200     FIND       DB-BCTL-ACCESS AT  DB-BCTL-BRNO = WK-BRNO
        bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(wkBrno);
        // 023300       ON EXCEPTION
        // 023400          MOVE  SPACES         TO    WK-BRNO-N
        // 023500          GO TO 090-NO.
        // 023600     MOVE       DB-BCTL-CHNAM  TO    WK-BRNO-N.
        if (!Objects.isNull(bctl)) {
            wkBrnoN =
                    bctl.getChnam() == null
                            ? formatUtil.padX("", 20)
                            : formatUtil.padX(bctl.getChnam(), 20);
        }
        // 023700 090-NO.

        //// 搬累計變數值WK-...至REPORT、FD-BINB-REC，並寫REPORTFL及FD-BINB35
        //
        // 023800****ADD FD-BINB FOR ONLINE-BINB USE
        // 023900     MOVE       WK-AMT1        TO    WK-AMT1-P,FD-BINB-DBAMT.
        BigDecimal wkAmt1P = wkAmt1;
        BigDecimal fdBinbDbamt = wkAmt1;
        // 024000     MOVE       WK-AMT2        TO    WK-AMT2-P,FD-BINB-CRAMT.
        BigDecimal wkAmt2P = wkAmt2;
        BigDecimal fdBinbCramt = wkAmt2;
        // 024100     MOVE       WK-CNT1        TO    WK-CNT1-P,FD-BINB-DBCNT.
        int wkCnt1P = wkCnt1;
        int fdBinbDbcnt = wkCnt1;
        // 024200     MOVE       WK-CNT2        TO    WK-CNT2-P,FD-BINB-CRCNT.
        int wkCnt2P = wkCnt2;
        int fdBinbCrcnt = wkCnt2;
        //
        //// 寫REPORTFL(報表明細)
        //
        // 024300     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE.
        // 008100 01 WK-DETAIL-LINE.
        // 008200    02 WK-BRNO                         PIC 9(03).
        // 008300    02 WK-BRNO-N                       PIC X(20).
        // 008400    02 WK-CNT1-P                       PIC Z(05)9.
        // 008500    02 WK-AMT1-P                       PIC Z(14)9.
        // 008600    02 WK-CNT2-P                       PIC Z(08)9.
        // 008700    02 WK-AMT2-P                       PIC Z(14)9.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9("" + wkBrno, 3));
        sb.append(cutStringByByteLength(wkBrnoN, 0, 20));
        sb.append(String.format("%6d", wkCnt1P));
        sb.append(
                String.format(
                        "%15s",
                        decimal020AmtFormat.format(wkAmt1P.setScale(2, RoundingMode.HALF_UP))));
        sb.append(String.format("%9d", wkCnt2P));
        sb.append(
                String.format(
                        "%15s",
                        decimal020AmtFormat.format(wkAmt2P.setScale(2, RoundingMode.HALF_UP))));
        file020Contents.add(sb.toString());

        // 024400     MOVE       WK-YYYMMDD     TO    FD-BINB-DATE.
        String fdBinbDate = processDate;
        // 024500     MOVE       WK-BRNO        TO    FD-BINB-BRNO.
        int fdBinbBrno = wkBrno;
        //
        //// 若 WK-BRNO=000,003,902,906，設定WK-003-HAS=1、累計資料至WK-003-...，
        //// GO TO 090-WRITED-EXIT不寫FD-BINB35
        //
        // 024510** 網銀、語音、行動銀行將行別改成 003
        // 024520     IF         WK-BRNO        = 000 OR = 003 OR = 902 OR = 906
        if (wkBrno == 0 || wkBrno == 3 || wkBrno == 902 || wkBrno == 906) {
            // 024530       MOVE     1              TO    WK-003-HAS
            wk003Has = 1;
            // 024540       ADD      FD-BINB-DBAMT  TO    WK-003-DBAMT
            wk003Dbamt = fdBinbDbamt;
            // 024550       ADD      FD-BINB-DBCNT  TO    WK-003-DBCNT
            wk003Dbcnt = fdBinbDbcnt;
            // 024560       ADD      FD-BINB-CRAMT  TO    WK-003-CRAMT
            wk003Cramt = fdBinbCramt;
            // 024570       ADD      FD-BINB-CRCNT  TO    WK-003-CRCNT
            wk003Crcnt = fdBinbCrcnt;
            // 024580       GO TO 090-WRITED-EXIT.
            return;
        }
        // 024600     MOVE       ZEROS          TO    FD-BINB-CURCD.
        String fdBinbCurcd = "";
        // 024700     MOVE       35             TO    FD-BINB-ONAPNO.
        String fdBinbOnapno = "35";
        //
        //// 寫FD-BINB35
        //
        // 024800     WRITE      FD-BINB-REC.
        // 003300 01   FD-BINB-REC.
        // 003400   05 FD-BINB-DATE              PIC 9(07).
        // 003500   05 FD-BINB-BRNO              PIC 9(03).
        // 003600   05 FD-BINB-CURCD             PIC 9(02).
        // 003700   05 FD-BINB-ONAPNO            PIC 9(02).
        // 003800   05 FD-BINB-DBCNT             PIC 9(05).
        // 003900   05 FD-BINB-DBAMT             PIC 9(13)V99.
        // 004000   05 FD-BINB-CRCNT             PIC 9(05).
        // 004100   05 FD-BINB-CRAMT             PIC 9(13)V99.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(fdBinbDate, 7));
        sb.append(formatUtil.pad9("" + fdBinbBrno, 3));
        sb.append(formatUtil.pad9(fdBinbCurcd, 2));
        sb.append(formatUtil.pad9(fdBinbOnapno, 2));
        sb.append(formatUtil.pad9("" + fdBinbDbcnt, 5));
        sb.append(
                decimalFormat
                        .format(fdBinbDbamt.setScale(2, RoundingMode.HALF_UP))
                        .replaceAll("\\.", ""));
        sb.append(formatUtil.pad9("" + fdBinbCrcnt, 5));
        sb.append(
                decimalFormat
                        .format(fdBinbCramt.setScale(2, RoundingMode.HALF_UP))
                        .replaceAll("\\.", ""));
        fileBINB35Contents.add(sb.toString());

        // 024900 090-WRITED-EXIT.
    }

    private void write003092Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr write003092Rtn");
        // 027500 092-WRITE003-RTN.
        //
        //// WK-BRNO=000,003,902,906之累計

        String fdBinbDate = "";
        String fdBinbBrno = "";
        BigDecimal fdBinbDbamt = BigDecimal.ZERO;
        int fdBinbDbcnt = 0;
        BigDecimal fdBinbCramt = BigDecimal.ZERO;
        int fdBinbCrcnt = 0;
        String fdBinbCurcd = "";
        String fdBinbOnapno = "";
        // 027600     IF         WK-003-HAS     =     1
        if (wk003Has == 1) {
            // 027700       MOVE     WK-YYYMMDD     TO    FD-BINB-DATE
            fdBinbDate = processDate;
            // 027800       MOVE     003            TO    FD-BINB-BRNO
            fdBinbBrno = "003";
            // 027900       MOVE     WK-003-DBAMT   TO    FD-BINB-DBAMT
            fdBinbDbamt = wk003Dbamt;
            // 028000       MOVE     WK-003-DBCNT   TO    FD-BINB-DBCNT
            fdBinbDbcnt = wk003Dbcnt;
            // 028100       MOVE     WK-003-CRAMT   TO    FD-BINB-CRAMT
            fdBinbCramt = wk003Cramt;
            // 028200       MOVE     WK-003-CRCNT   TO    FD-BINB-CRCNT
            fdBinbCrcnt = wk003Crcnt;
            // 028300       MOVE     ZEROS          TO    FD-BINB-CURCD
            fdBinbCurcd = "";
            // 028400       MOVE     35             TO    FD-BINB-ONAPNO
            fdBinbOnapno = "35";
        }
        //// 寫FD-BINB35
        //
        // 028500       WRITE    FD-BINB-REC.
        // 003300 01   FD-BINB-REC.
        // 003400   05 FD-BINB-DATE              PIC 9(07).
        // 003500   05 FD-BINB-BRNO              PIC 9(03).
        // 003600   05 FD-BINB-CURCD             PIC 9(02).
        // 003700   05 FD-BINB-ONAPNO            PIC 9(02).
        // 003800   05 FD-BINB-DBCNT             PIC 9(05).
        // 003900   05 FD-BINB-DBAMT             PIC 9(13)V99.
        // 004000   05 FD-BINB-CRCNT             PIC 9(05).
        // 004100   05 FD-BINB-CRAMT             PIC 9(13)V99.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(fdBinbDate, 7));
        sb.append(formatUtil.pad9(fdBinbBrno, 3));
        sb.append(formatUtil.pad9(fdBinbCurcd, 2));
        sb.append(formatUtil.pad9(fdBinbOnapno, 2));
        sb.append(formatUtil.pad9("" + fdBinbDbcnt, 5));
        sb.append(
                decimalFormat
                        .format(fdBinbDbamt.setScale(2, RoundingMode.HALF_UP))
                        .replaceAll("\\.", ""));
        sb.append(formatUtil.pad9("" + fdBinbCrcnt, 5));
        sb.append(
                decimalFormat
                        .format(fdBinbCramt.setScale(2, RoundingMode.HALF_UP))
                        .replaceAll("\\.", ""));
        fileBINB35Contents.add(sb.toString());
        // 028600 092-WRITE003-EXIT.
    }

    private void writet095Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF3Lsnr writet095Rtn");
        // 025100 095-WRITET-RTN.
        //
        //// 寫REPORTFL(表尾-總數)
        //
        // 025200     MOVE       SPACES         TO    REPORT-LINE.
        // 025300     COMPUTE    WK-PCTL = 45 - WK-PCTL.
        wkPctl = 45 - wkPctl;
        // 025400     WRITE      REPORT-LINE    AFTER WK-PCTL.
        for (int i = 0; i < wkPctl; i++) {
            file020Contents.add("");
        }
        // 025500     MOVE       WK-AMT1T       TO    WK-AMT1T-P.
        BigDecimal wkAmt1tP = wkAmt1t;
        // 025600     MOVE       WK-AMT2T       TO    WK-AMT2T-P.
        BigDecimal wkAmt2tP = wkAmt2t;
        // 025700     MOVE       WK-CNT1T       TO    WK-CNT1T-P.
        int wkCnt1tP = wkCnt1t;
        // 025800     MOVE       WK-CNT2T       TO    WK-CNT2T-P.
        int wkCnt2tP = wkCnt2t;
        // 025900     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE.
        // 008800 01 WK-TOTAL-LINE.
        // 008900    02 FILLER                          PIC X(20) VALUE SPACES.
        // 009000    02 WK-CNT1T-P                      PIC Z(08)9.
        // 009100    02 WK-AMT1T-P                      PIC Z(14)9.
        // 009200    02 WK-CNT2T-P                      PIC Z(08)9.
        // 009300    02 WK-AMT2T-P                      PIC Z(14)9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 20));
        sb.append(String.format("%9d", wkCnt1tP));
        sb.append(
                String.format(
                        "%15s",
                        decimal020AmtFormat.format(wkAmt1tP.setScale(2, RoundingMode.HALF_UP))));
        sb.append(String.format("%9d", wkCnt2tP));
        sb.append(
                String.format(
                        "%15s",
                        decimal020AmtFormat.format(wkAmt2tP.setScale(2, RoundingMode.HALF_UP))));
        file020Contents.add(sb.toString());
        // 026000 095-WRITET-EXIT.
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
        //        event.setPeripheryRequest();
    }

    private String cutStringByByteLength(String s, int startIndex, int endIndex) {
        if (s != null && startIndex >= 0 && endIndex > startIndex) {
            byte[] b = s.getBytes(Charset.forName("BIG5"));
            if (startIndex >= b.length) {
                return "";
            } else {
                endIndex = Math.min(endIndex, b.length);
                byte[] newBytes = Arrays.copyOfRange(b, startIndex, endIndex);
                return new String(newBytes, Charset.forName("BIG5"));
            }
        } else {
            return s;
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_020_NAME);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
