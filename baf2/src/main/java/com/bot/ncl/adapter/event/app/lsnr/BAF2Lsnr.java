/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.BAF2;
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
public class BAF2Lsnr extends BatchListenerCase<BAF2> {

    // 查詢分行控制檔
    private Bctl bctl;
    @Autowired private ClmrService clmrService;
    @Autowired private ClbafService clbafService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private Parse parse;
    private BAF2 event;
    private List<String> fileSortContents; // 檔案內容
    private List<String> fileBINB37Contents; // 檔案內容
    private List<String> file006Contents; // 報表內容
    private String processDate; // 批次日期(民國年yyyymmdd)
    private String tbsdy;
    private String wkYY;
    private String wkMM;
    private String wkDD;

    private static final String FILE_BINB37_NAME = "BINB37";
    private static final String FILE_006_NAME = "CL-BH-006";
    private static final String FILE_SORTFL_NAME = "BAF2SORTFL";

    private static final String FILE_DIR = "DLY/";

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir2;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private String PAGE_SEPARATOR = "\u000C";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET2 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String fileBINB37Path;
    private String file006Path;
    private String fileSortflPath;
    private DecimalFormat decimalFormat = new DecimalFormat("0000000000000.00");
    private DecimalFormat decimal006AmtFormat = new DecimalFormat("#########.00");

    private StringBuilder sb = new StringBuilder();

    private BigDecimal wkAmt1 = BigDecimal.ZERO;
    private BigDecimal wkAmt2 = BigDecimal.ZERO;
    private BigDecimal wkAmt1t = BigDecimal.ZERO;
    private BigDecimal wkAmt2t = BigDecimal.ZERO;
    private int wkPctl = 0;
    private int wkCnt1 = 0;
    private int wkCnt2 = 0;
    private int wkCnt1t = 0;
    private int wkCnt2t = 0;
    private BigDecimal sAmt1 = BigDecimal.ZERO;
    private BigDecimal sAmt2 = BigDecimal.ZERO;
    private int sCnt2 = 0;
    private int sCnt1 = 0;
    private int sCllbr = 0;
    // 004020 01 WK-CODE.
    // 004040    03  WK-CODE1                       PIC X(03).
    // 004060    03  WK-CODE2                       PIC X(03).
    private String wkCode = "";
    private String wkCode1 = "";
    private int wkCllbr = 0;
    private int wkBrno = 0;
    private int wk003Has = 0;
    private BigDecimal wk003Dbamt = BigDecimal.ZERO;
    private int wk003Dbcnt = 0;
    private BigDecimal wk003Cramt = BigDecimal.ZERO;
    private int wk003Crcnt = 0;

    /*
    ex.
    @Autowired
    @Qualifier("GS15CLCMP_G092_O") private GS15CLCMP_G092_O gs15CLCMP_g092_o;
    */

    @Override
    public void onApplicationEvent(BAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in BAF2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(BAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr run()");
        init(event);

        // 009000     PERFORM 0000-MAIN-RTN   THRU    0000-MAIN-EXIT.
        mainRtn();

        batchResponse();
    }

    private void init(BAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr init");
        this.event = event;
        // 抓批次營業日
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 008600     MOVE    FD-BHDATE-TBSDY TO      WK-YYMMDD.
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkYY = processDate.substring(0, 3);
        wkMM = processDate.substring(3, 5);
        wkDD = processDate.substring(5, 7);
        fileBINB37Path =
                fileDir2
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_DIR
                        + FILE_BINB37_NAME;
        file006Path =
                fileDir2
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_006_NAME;
        fileSortflPath =
                fileDir2
                        + "DATA"
                        + File.separator
                        + processDate
                        + File.separator
                        + FILE_SORTFL_NAME;
        fileSortContents = new ArrayList<>();
        fileBINB37Contents = new ArrayList<>();
        file006Contents = new ArrayList<>();
        textFile.deleteFile(fileBINB37Path);
        textFile.deleteFile(fileSortflPath);
        textFile.deleteFile(file006Path);
        //// 清變數值
        //
        // 008800     MOVE    0     TO  WK-CLLBR,WK-AMT1,WK-AMT2,WK-AMT1T,WK-AMT2T,
        // 008900                       WK-PCTL ,WK-CNT1,WK-CNT2,WK-CNT1T,WK-CNT2T.
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr mainRtn");
        // 009700 0000-MAIN-RTN.

        //// SORT INPUT 段落：CS-SORTIN(讀收付累計檔，挑資料，並寫至SORTFL)
        //// 資料照 S-CLLBR 由小到大排序
        //// SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫報表檔及FD-BINB37)

        // 009800     SORT    SORTFL
        // 009900             ASCENDING KEY        S-CLLBR
        // 009901     MEMORY SIZE 6 MODULES
        // 009902     DISK SIZE 50 MODULES
        // 009903             INPUT  PROCEDURE     CS-SORTIN
        csSortin();
        // 010100             OUTPUT PROCEDURE     CS-SORTOUT
        csSortout();

        // 010200 0000-MAIN-EXIT.
    }

    private void csSortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr csSortin");
        // 010500 CS-SORTIN     SECTION.
        // 010600 CS-SORTIN-RTN.

        //// FIND NEXT DB-CLBAF-DDS收付累計檔，若有誤(讀檔結束)，結束本節

        // 010700     FIND NEXT    DB-CLBAF-DDS
        // 010800       ON EXCEPTION
        // 011000          GO TO   CS-SORTIN-EXIT.
        //// 代收日 不等於 本營業日 ，跳掉這筆資料
        //// GO TO CS-SORTIN-RTN找下一筆收付累計檔
        // 011100     IF         DB-CLBAF-DATE       NOT =  WK-YYMMDD
        // 011200       GO TO      CS-SORTIN-RTN.
        List<ClbafbyEntdyBus> lClbaf =
                clbafService.findbyEntdy(parse.string2Integer(processDate), 0, Integer.MAX_VALUE);
        if (Objects.isNull(lClbaf)) {
            return;
        }
        for (ClbafbyEntdyBus tClbaf : lClbaf) {
            Boolean goToCsSortin999 = false;
            //// CODE(1:3)="121"，跳掉這筆資料
            //// GO TO CS-SORTIN-RTN找下一筆收付累計檔

            // 011220* 代收類別前三位為１２１時為代付性質交易，不列入ＢＩＮＢ３７內容
            // 011240     MOVE     DB-CLBAF-CODE         TO     WK-CODE.
            wkCode = tClbaf.getCode();
            wkCode1 = wkCode.substring(0, 3);
            // 011260     IF       WK-CODE1              =      "121"
            if ("121".equals(wkCode1)) {
                // 011280       GO TO  CS-SORTIN-RTN.
                continue;
            }

            //// DB-CLBAF-CLLBR=999(該代收類別之總累計資料)，
            //// GO TO CS-SORTIN-999 寫貸方資料

            // 011300     IF       DB-CLBAF-CLLBR        =      999
            if (tClbaf.getCllbr() == 999) {
                // 011500       GO TO  CS-SORTIN-999.
                goToCsSortin999 = true;
            }
            if (!goToCsSortin999) {
                ////
                // 搬相關欄位至SORT-REC(S-AMT1=0,SCN1=0,S-AMT2=DB-CLBAF-CFEE2,S-CNT2=DB-CLBAF-CNT)，並寫至SORTFL
                //// 累計DB-CLBAF-CFEE2、DB-CLBAF-CNT到WK-AMT2T、WK-CNT2T

                // 012100     MOVE       0                   TO     S-AMT1,S-CNT1.
                sAmt1 = new BigDecimal(0);
                sCnt1 = 0;

                //// DB-CLBAF-CFEE2 該業務在當日之累計代收手續費(放代收行對主辦行應收手續費)
                //// DB-CLBAF-CNT   該業務在當日之累計代收筆數
                //// DB-CLBAF-CLLBR 代收行

                // 012420     ADD        DB-CLBAF-CFEE2      TO     WK-AMT2,WK-AMT2T.
                wkAmt2 = wkAmt2.add(tClbaf.getCfee2());
                wkAmt2t = wkAmt2t.add(tClbaf.getCfee2());
                // 012440     ADD        DB-CLBAF-CNT        TO     WK-CNT2,WK-CNT2T.
                wkCnt2 = wkCnt2 + tClbaf.getCnt();
                wkCnt2t = wkCnt2t + tClbaf.getCnt();
                // 012500     MOVE       WK-AMT2             TO     S-AMT2.
                sAmt2 = wkAmt2;
                // 012600     MOVE       WK-CNT2             TO     S-CNT2.
                sCnt2 = wkCnt2;
                // 012700     MOVE       DB-CLBAF-CLLBR      TO     S-CLLBR.
                sCllbr = tClbaf.getCllbr();
                // 012800     RELEASE    SORT-REC.
                // 002700 01   SORT-REC.
                // 002800     03  S-CLLBR                PIC 9(03).
                // 002900     03  S-AMT1                 PIC 9(13)V99.
                // 003000     03  S-AMT2                 PIC 9(13)V99.
                // 003100     03  S-CNT1                 PIC 9(09).
                // 003200     03  S-CNT2                 PIC 9(09).
                sb = new StringBuilder();
                sb.append(formatUtil.pad9("" + sCllbr, 3));
                sb.append(decimalFormat.format(sAmt1));
                sb.append(decimalFormat.format(sAmt2));
                sb.append(formatUtil.pad9("" + sCnt1, 9));
                sb.append(formatUtil.pad9("" + sCnt2, 9));
                fileSortContents.add(sb.toString());
                // 012900     MOVE       0                   TO     WK-AMT2,WK-CNT2.
                wkAmt2 = new BigDecimal(0);
                wkCnt2 = 0;

                //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔

                // 013000     GO TO         CS-SORTIN-RTN.
                continue;
            }
            // 013100 CS-SORTIN-999.

            //// 依代收類別FIND DB-CLMR-DDS事業單位基本資料檔，若有誤
            ////  顯示錯誤訊息，異常，結束程式

            // 013600     FIND       DB-CLMR-IDX1   AT DB-CLMR-CODE   = DB-CLBAF-CODE
            ClmrBus tClmr = clmrService.findById(tClbaf.getCode());
            // 013800       ON EXCEPTION
            if (Objects.isNull(tClmr)) {
                // 013900       DISPLAY "CAN'T USE CLBAF-CODE FIND CLMR-CODE="DB-CLBAF-CODE
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "CAN'T USE CLBAF-CODE FIND CLMR-CODE={}",
                        tClbaf.getCode());
                // 014100       CALL SYSTEM DMTERMINATE. //TODO:DMTERMINATE?
                continue;
            }
            //// 搬相關欄位至SORT-REC(S-AMT1=DB-CLBAF-CFEE2,S-CNT1=DB-CLBAF-CNT,S-AMT2=0,SCN2=0)，並寫至SORTFL
            //// 累計DB-CLBAF-CFEE2、DB-CLBAF-CNT到WK-AMT1T、WK-CNT1T

            //// DB-CLBAF-CFEE2 該業務在當日之累計代收手續費(放代收行對主辦行應收手續費)
            //// DB-CLBAF-CNT   該業務在當日之累計代收筆數

            // 014960     MOVE       DB-CLBAF-CFEE2      TO     WK-AMT1.
            // 014980     MOVE       DB-CLBAF-CNT        TO     WK-CNT1.
            wkAmt1 = tClbaf.getCfee2();
            wkCnt1 = tClbaf.getCnt();

            //// DB-CLMR-PBRNO主辦分行

            // 015800     MOVE       DB-CLMR-PBRNO       TO     S-CLLBR.
            sCllbr = tClmr.getPbrno();
            // 015900     ADD        WK-AMT1             TO     WK-AMT1T.
            wkAmt1t = wkAmt1t.add(wkAmt1);
            // 016000     ADD        WK-CNT1             TO     WK-CNT1T.
            wkCnt1t = wkCnt1t + wkCnt1;
            // 016100     MOVE       0                   TO     S-AMT2,S-CNT2.
            sAmt2 = new BigDecimal(0);
            sCnt2 = 0;
            // 016200     MOVE       WK-AMT1             TO     S-AMT1.
            sAmt1 = wkAmt1;
            // 016300     MOVE       WK-CNT1             TO     S-CNT1.
            sCnt1 = wkCnt1;
            // 016400     RELEASE    SORT-REC.
            // 002800     03  S-CLLBR                PIC 9(03).  0-3
            // 002900     03  S-AMT1                 PIC 9(13)V99. 3-19
            // 003000     03  S-AMT2                 PIC 9(13)V99. 19-35
            // 003100     03  S-CNT1                 PIC 9(09). 35-44
            // 003200     03  S-CNT2                 PIC 9(09). 44-53
            sb = new StringBuilder();
            sb.append(formatUtil.pad9("" + sCllbr, 3));
            sb.append(decimalFormat.format(sAmt1));
            sb.append(decimalFormat.format(sAmt2));
            sb.append(formatUtil.pad9("" + sCnt1, 9));
            sb.append(formatUtil.pad9("" + sCnt2, 9));
            fileSortContents.add(sb.toString());
            // 016500     MOVE       0                   TO     WK-AMT1,WK-CNT1.
            wkAmt1 = new BigDecimal(0);
            wkCnt1 = 0;

            //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔

            // 016600     GO TO      CS-SORTIN-RTN.
        }
        // 016700 CS-SORTIN-EXIT.
        // 寫暫存檔排序
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr csSortout");
        // 017000 CS-SORTOUT      SECTION.
        // 017100 CS-SORTOUT-RTN.

        //// 預設變數值WK-CLLBR=001
        //// 清變數值

        // 017320     MOVE       001            TO      WK-CLLBR.
        wkCllbr = 1;
        // 017340     MOVE       0              TO               WK-AMT1,WK-AMT2
        // 017360                                            ,WK-CNT1,WK-CNT2.
        wkAmt1 = new BigDecimal(0);
        wkAmt2 = new BigDecimal(0);
        wkCnt1 = 0;
        wkCnt2 = 0;
        // 017400 CS-SORTOUT-RETURN.
        if (!textFile.exists(fileSortflPath)) {
            return;
        }
        List<String> lines = textFile.readFileContent(fileSortflPath, CHARSET2);
        //// 將SORT後的記錄讀出，直到檔尾
        ////  若WK-CLLBR=001，表無資料，GO TO CS-SORTOUT-EXIT，結束本節
        ////  其他
        ////   若WK-PCTL=45
        ////    A.097-WRITEA-RTN 寫表頭
        ////    B.090-WRITED-RTN 寫報表明細&FD-BINB37
        ////    C.092-WRITE003-RTN 寫FD-BINB37(WK-BRNO=000,003,902,906之累計)
        ////    D.095-WRITET-RTN 寫表尾-總數
        ////    E.GO TO CS-SORTOUT-EXIT，結束本節
        ////   若WK-PCTL<>45
        ////    A.090-WRITED-RTN 寫報表明細&FD-BINB37
        ////    B.092-WRITE003-RTN 寫FD-BINB37(WK-BRNO=000,003,902,906之累計)
        ////    C.095-WRITET-RTN 寫表尾-總數
        ////    D.GO TO CS-SORTOUT-EXIT，結束本節

        // 017500     RETURN     SORTFL    AT END
        // 017650       IF       WK-CLLBR       =       001
        if (lines.isEmpty()) {
            // 017700            GO TO CS-SORTOUT-EXIT
            return;
        }
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            //// 若CLLBR相同，累計S-...(筆數、金額)到WK-...；LOOP讀下一筆SORTFL
            //// 若CLLBR不相同，
            ////  若WK-CLLBR=001，表第1筆資料，往下一步驟執行
            ////  其他，表處理到不同分行資料，出表&寫檔
            String sCllbr = detail.substring(0, 3);
            String sAmt1 = detail.substring(3, 19);
            String sAmt2 = detail.substring(19, 35);
            String sCnt1 = detail.substring(35, 44);
            String sCnt2 = detail.substring(44, 53);
            // 018900     IF         WK-CLLBR       =       S-CLLBR
            if (wkCllbr == parse.string2Integer(sCllbr)) {
                // 019000       ADD      S-AMT1         TO      WK-AMT1
                wkAmt1 = wkAmt1.add(parse.string2BigDecimal(sAmt1));
                // 019100       ADD      S-CNT1         TO      WK-CNT1
                wkCnt1 = wkCnt1 + parse.string2Integer(sCnt1);
                // 019200       ADD      S-AMT2         TO      WK-AMT2
                wkAmt2 = wkAmt2.add(parse.string2BigDecimal(sAmt2));
                // 019300       ADD      S-CNT2         TO      WK-CNT2
                wkCnt2 = wkCnt2 + parse.string2Integer(sCnt2);
                // 019400       GO TO CS-SORTOUT-RETURN
                // 尾筆處理
                if (cnt == lines.size()) {
                    // 017900         IF     WK-PCTL        =       45
                    if (wkPctl == 45) {
                        // 018000                MOVE    0              TO      WK-PCTL
                        wkPctl = 0;
                        // 018100                PERFORM 097-WRITEA-RTN THRU    097-WRITEA-EXIT
                        writea097Rtn(PAGE_SEPARATOR);
                        // 018200                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 018250                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                        write003092Rtn();
                        // 018300                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                        writet095Rtn();
                        // 018400                GO TO   CS-SORTOUT-EXIT
                        return;
                    } else {
                        // 018500         ELSE
                        // 018600                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 018650                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                        write003092Rtn();
                        // 018700                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                        writet095Rtn();
                        // 018800                GO TO   CS-SORTOUT-EXIT

                        // 018820         END-IF
                    }
                    // 018850       END-IF.
                }
                continue;
            } else {
                // 019500     ELSE
                // 019650       IF       WK-CLLBR       =       001
                if (wkCllbr == 1) {
                    // 019700         NEXT SENTENCE
                } else {
                    // 019800       ELSE
                    //// WK-PCTL累加1
                    // 019900         ADD        1              TO      WK-PCTL
                    wkPctl = wkPctl + 1;
                    //// WK-PCTL=1,46
                    ////  A.097-WRITEA-RTN 寫表頭
                    ////  B.090-WRITED-RTN 寫報表明細&FD-BINB37
                    ////  C.清變數值
                    //// 其他
                    ////  A.090-WRITED-RTN 寫報表明細&FD-BINB37
                    ////  B.清變數值
                    //
                    // 020000         IF         WK-PCTL        =   1   OR = 46
                    if (wkPctl == 1 || wkPctl == 46) {
                        // 020100           MOVE     1              TO      WK-PCTL
                        wkPctl = 1;
                        // 020200           PERFORM  097-WRITEA-RTN THRU    097-WRITEA-EXIT
                        writea097Rtn(PAGE_SEPARATOR);
                        // 020300           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 020400           MOVE     0       TO     WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2
                        wkAmt1 = new BigDecimal(0);
                        wkAmt2 = new BigDecimal(0);
                        wkCnt1 = 0;
                        wkCnt2 = 0;
                    } else {
                        // 020500         ELSE
                        // 020600           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        writed090Rtn();
                        // 020700           MOVE     0       TO    WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2.
                        wkAmt1 = new BigDecimal(0);
                        wkAmt2 = new BigDecimal(0);
                        wkCnt1 = 0;
                        wkCnt2 = 0;
                    }
                }
            }
            ////  搬S-...(筆數、金額)到變數WK-...
            ////  保留S-CLLBR到變數WK-CLLBR
            //
            // 020800     MOVE       S-AMT1         TO      WK-AMT1.
            wkAmt1 = parse.string2BigDecimal(sAmt1);
            // 020900     MOVE       S-CNT1         TO      WK-CNT1.
            wkCnt1 = parse.string2Integer(sCnt1);
            // 021000     MOVE       S-AMT2         TO      WK-AMT2.
            wkAmt2 = parse.string2BigDecimal(sAmt2);
            // 021100     MOVE       S-CNT2         TO      WK-CNT2.
            wkCnt2 = parse.string2Integer(sCnt2);
            // 021200     MOVE       S-CLLBR        TO      WK-CLLBR.
            wkCllbr = parse.string2Integer(sCllbr);

            //// LOOP讀下一筆SORTFL

            // 尾筆處理
            if (cnt == lines.size()) {
                // 017900         IF     WK-PCTL        =       45
                if (wkPctl == 45) {
                    // 018000                MOVE    0              TO      WK-PCTL
                    wkPctl = 0;
                    // 018100                PERFORM 097-WRITEA-RTN THRU    097-WRITEA-EXIT
                    writea097Rtn(PAGE_SEPARATOR);
                    // 018200                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                    writed090Rtn();
                    // 018250                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                    write003092Rtn();
                    // 018300                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                    writet095Rtn();
                    // 018400                GO TO   CS-SORTOUT-EXIT
                    return;
                } else {
                    // 018500         ELSE
                    // 018600                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                    writed090Rtn();
                    // 018650                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                    write003092Rtn();
                    // 018700                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                    writet095Rtn();
                    // 018800                GO TO   CS-SORTOUT-EXIT

                    // 018820         END-IF
                }
                // 018850       END-IF.
            }
            // 021300     GO TO CS-SORTOUT-RETURN.
        }

        try {
            textFile.writeFileContent(file006Path, file006Contents, CHARSET2);
            upload(file006Path, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(fileBINB37Path, fileBINB37Contents, CHARSET);
            upload(fileBINB37Path, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(fileSortflPath);
        // 021400 CS-SORTOUT-EXIT.
    }

    private void writea097Rtn(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr writea097Rtn");
        // 024500 097-WRITEA-RTN.

        //// 寫REPORTFL(表頭)

        // 024600     MOVE       SPACES         TO    REPORT-LINE.
        // 024700     WRITE      REPORT-LINE    AFTER PAGE.
        sb = new StringBuilder();
        sb.append(pageFg);
        file006Contents.add(sb.toString());

        // 024800     WRITE      REPORT-LINE    AFTER 2 LINES.
        file006Contents.add("");
        file006Contents.add("");

        // 024900     MOVE       WK-YY          TO    WK-YY-P.
        String wkYYP = wkYY;
        // 025000     MOVE       WK-MM          TO    WK-MM-P.
        String wkMMP = wkMM;
        // 025100     MOVE       WK-DD          TO    WK-DD-P.
        String wkDDP = wkDD;
        // 025200     WRITE      REPORT-LINE    FROM  WK-DATE-LINE.
        // 005600 01 WK-DATE-LINE.
        // 005700    02 FILLER                          PIC X(23) VALUE SPACES.
        // 005800    02 WK-YY-P                         PIC 9(03).
        // 005900    02 FILLER                          PIC X(04) VALUE SPACES.
        // 006000    02 WK-MM-P                         PIC 9(02).
        // 006100    02 FILLER                          PIC X(04) VALUE SPACES.
        // 006200    02 WK-DD-P                         PIC 9(02).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 23));
        sb.append(formatUtil.pad9(wkYYP, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkMMP, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkDDP, 2));
        file006Contents.add(sb.toString());

        // 025300     MOVE       SPACES         TO    REPORT-LINE.
        // 025400     WRITE      REPORT-LINE   BEFORE 3 LINES.
        file006Contents.add("");
        file006Contents.add("");
        file006Contents.add("");
        file006Contents.add("");

        // 025500 097-WRITEA-EXIT.
    }

    private void writed090Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr writed090Rtn");
        // 021800 090-WRITED-RTN.
        // 021900     MOVE       SPACES         TO    REPORT-LINE.
        // 022000     MOVE       WK-CLLBR       TO    WK-BRNO.
        wkBrno = wkCllbr;

        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定營業單位中文名稱變數值；
        //// 若有誤，搬空白
        String wkBrnoN = formatUtil.padX("", 20);
        // 022100     FIND       DB-BCTL-ACCESS AT  DB-BCTL-BRNO = WK-BRNO
        bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(wkBrno);
        // 022200       ON EXCEPTION
        // 022300          MOVE  SPACES         TO    WK-BRNO-N
        // 022400          GO TO 090-NO.
        // 022500     MOVE       DB-BCTL-CHNAM  TO    WK-BRNO-N.
        if (!Objects.isNull(bctl)) {
            wkBrnoN =
                    bctl.getChnam() == null
                            ? formatUtil.padX("", 20)
                            : formatUtil.padX(bctl.getChnam(), 20);
        }
        // 022600 090-NO.

        //// 搬累計變數值WK-...至REPORT、FD-BINB-REC，並寫REPORTFL及FD-BINB37

        // 022650** WRITE FD-BINB FOR ONLINE-BINB USE
        // 022700     MOVE       WK-AMT1        TO    WK-AMT1-P,FD-BINB-CRAMT.
        BigDecimal wkAmt1P = wkAmt1;
        BigDecimal fdBinbCramt = wkAmt1;
        // 022800     MOVE       WK-AMT2        TO    WK-AMT2-P,FD-BINB-DBAMT.
        BigDecimal wkAmt2P = wkAmt2;
        BigDecimal fdBinbDbamt = wkAmt2;
        // 022900     MOVE       WK-CNT1        TO    WK-CNT1-P,FD-BINB-CRCNT.
        int wkCnt1P = wkCnt1;
        int fdBinbCrcnt = wkCnt1;
        // 023000     MOVE       WK-CNT2        TO    WK-CNT2-P,FD-BINB-DBCNT.
        int wkCnt2P = wkCnt2;
        int fdBinbDbcnt = wkCnt2;

        //// 寫REPORTFL(報表明細)
        // 023100     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE.
        // 006300 01 WK-DETAIL-LINE.
        // 006400    02 WK-BRNO                         PIC 9(03).
        // 006500    02 WK-BRNO-N                       PIC X(20).
        // 006600    02 WK-CNT2-P                       PIC Z(05)9.
        // 006700    02 WK-AMT2-P                       PIC Z(12).99.
        // 006800    02 WK-CNT1-P                       PIC Z(08)9.
        // 006900    02 WK-AMT1-P                       PIC Z(12).99.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9("" + wkBrno, 3));
        sb.append(cutStringByByteLength(wkBrnoN, 0, 20));
        sb.append(String.format("%6d", wkCnt2P));
        sb.append(
                String.format(
                        "%15s",
                        decimal006AmtFormat.format(wkAmt2P.setScale(2, RoundingMode.HALF_UP))));
        sb.append(String.format("%9d", wkCnt1P));
        sb.append(
                String.format(
                        "%15s",
                        decimal006AmtFormat.format(wkAmt1P.setScale(2, RoundingMode.HALF_UP))));
        file006Contents.add(sb.toString());

        // 023110     MOVE       WK-YYMMDD      TO    FD-BINB-DATE.
        String fdBinbDate = processDate;
        // 023120     MOVE       WK-BRNO        TO    FD-BINB-BRNO.
        int fdBinbBrno = wkBrno;

        //// 若 WK-BRNO=000,003,902,906，設定WK-003-HAS=1、累計資料至WK-003-...，
        //// GO TO 090-WRITED-EXIT不寫FD-BINB37

        // 023126     IF         WK-BRNO        =  000 OR = 003 OR = 902 OR = 906
        if (wkBrno == 0 || wkBrno == 3 || wkBrno == 902 || wkBrno == 906) {
            // 023128       MOVE     1              TO    WK-003-HAS
            wk003Has = 1;
            // 023130       ADD      FD-BINB-DBAMT  TO    WK-003-DBAMT
            wk003Dbamt = wk003Dbamt.add(fdBinbDbamt);
            // 023132       ADD      FD-BINB-DBCNT  TO    WK-003-DBCNT
            wk003Dbcnt = wk003Dbcnt + fdBinbDbcnt;
            // 023134       ADD      FD-BINB-CRAMT  TO    WK-003-CRAMT
            wk003Cramt = wk003Cramt.add(fdBinbCramt);
            // 023136       ADD      FD-BINB-CRCNT  TO    WK-003-CRCNT
            wk003Crcnt = wk003Crcnt + fdBinbCrcnt;
            // 023138       GO TO 090-WRITED-EXIT.
            return;
        }
        // 023150     MOVE       ZEROS          TO    FD-BINB-CURCD.
        int fdBinbCurcd = 0;
        // 023152     MOVE       37             TO    FD-BINB-ONAPNO.
        int fdBinbOnapno = 37;
        //
        //// 寫FD-BINB37
        //
        // 023154     WRITE      FD-BINB-REC.
        // 002520 01   FD-BINB-REC.
        // 002525   05 FD-BINB-DATE              PIC 9(07).
        // 002530   05 FD-BINB-BRNO              PIC 9(03).
        // 002535   05 FD-BINB-CURCD             PIC 9(02).
        // 002540   05 FD-BINB-ONAPNO            PIC 9(02).
        // 002545   05 FD-BINB-DBCNT             PIC 9(05).
        // 002550   05 FD-BINB-DBAMT             PIC 9(13)V99.
        // 002555   05 FD-BINB-CRCNT             PIC 9(05).
        // 002560   05 FD-BINB-CRAMT             PIC 9(13)V99.
        // 002600 SD   SORTFL.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(fdBinbDate, 7));
        sb.append(formatUtil.pad9("" + fdBinbBrno, 3));
        sb.append(formatUtil.pad9("" + fdBinbCurcd, 2));
        sb.append(formatUtil.pad9("" + fdBinbOnapno, 2));
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
        fileBINB37Contents.add(sb.toString());

        // 023156 090-WRITED-EXIT.
    }

    private void write003092Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr write003092Rtn");
        // 023160 092-WRITE003-RTN.

        //// WK-BRNO=000,003,902,906之累計

        // 023162     IF         WK-003-HAS     =     1
        String fdBinbDate = "";
        String fdBinbBrno = "";
        BigDecimal fdBinbDbamt = BigDecimal.ZERO;
        int fdBinbDbcnt = 0;
        BigDecimal fdBinbCramt = BigDecimal.ZERO;
        int fdBinbCrcnt = 0;
        String fdBinbCurcd = "";
        String fdBinbOnapno = "";
        if (wk003Has == 1) {
            // 023164       MOVE     WK-YYMMDD      TO    FD-BINB-DATE
            // 023166       MOVE     003            TO    FD-BINB-BRNO
            // 023168       MOVE     WK-003-DBAMT   TO    FD-BINB-DBAMT
            // 023170       MOVE     WK-003-DBCNT   TO    FD-BINB-DBCNT
            // 023172       MOVE     WK-003-CRAMT   TO    FD-BINB-CRAMT
            // 023174       MOVE     WK-003-CRCNT   TO    FD-BINB-CRCNT
            // 023176       MOVE     ZEROS          TO    FD-BINB-CURCD
            // 023178       MOVE     37             TO    FD-BINB-ONAPNO
            fdBinbDate = processDate;
            fdBinbBrno = "003";
            fdBinbDbamt = wk003Dbamt;
            fdBinbDbcnt = wk003Dbcnt;
            fdBinbCramt = wk003Cramt;
            fdBinbCrcnt = wk003Crcnt;
            fdBinbCurcd = "0";
            fdBinbOnapno = "37";
        }
        //// 寫FD-BINB37
        //
        // 023180       WRITE    FD-BINB-REC.
        // 002520 01   FD-BINB-REC.
        // 002525   05 FD-BINB-DATE              PIC 9(07).
        // 002530   05 FD-BINB-BRNO              PIC 9(03).
        // 002535   05 FD-BINB-CURCD             PIC 9(02).
        // 002540   05 FD-BINB-ONAPNO            PIC 9(02).
        // 002545   05 FD-BINB-DBCNT             PIC 9(05).
        // 002550   05 FD-BINB-DBAMT             PIC 9(13)V99.
        // 002555   05 FD-BINB-CRCNT             PIC 9(05).
        // 002560   05 FD-BINB-CRAMT             PIC 9(13)V99.
        // 002600 SD   SORTFL.
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
        fileBINB37Contents.add(sb.toString());

        // 023200 092-WRITE003-EXIT.
    }

    private void writet095Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF2Lsnr write003092Rtn");
        // 023400 095-WRITET-RTN.

        //// 寫REPORTFL(表尾-總數)

        // 023500     MOVE       SPACES         TO    REPORT-LINE.
        // 023600     COMPUTE    WK-PCTL = 45 - WK-PCTL.
        wkPctl = 45 - wkPctl;
        // 023700     WRITE      REPORT-LINE    AFTER WK-PCTL.
        for (int i = 0; i < wkPctl; i++) {
            file006Contents.add("");
        }

        // 023800     MOVE       WK-AMT1T       TO    WK-AMT1T-P.
        BigDecimal wkAmt1tP = wkAmt1t;
        // 023900     MOVE       WK-AMT2T       TO    WK-AMT2T-P.
        BigDecimal wkAmt2tP = wkAmt2t;
        // 024000     MOVE       WK-CNT1T       TO    WK-CNT1T-P.
        int wkCnt1tP = wkCnt1t;
        // 024100     MOVE       WK-CNT2T       TO    WK-CNT2T-P.
        int wkCnt2tP = wkCnt2t;
        // 024200     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE.
        // 007000 01 WK-TOTAL-LINE.
        // 007100    02 FILLER                          PIC X(20) VALUE SPACES.
        // 007200    02 WK-CNT2T-P                      PIC Z(08)9.
        // 007300    02 WK-AMT2T-P                      PIC Z(12).99.
        // 007400    02 WK-CNT1T-P                      PIC Z(08)9.
        // 007500    02 WK-AMT1T-P                      PIC Z(12).99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 20));
        sb.append(String.format("%9d", wkCnt2tP));
        sb.append(
                String.format(
                        "%15s",
                        decimal006AmtFormat.format(wkAmt2tP.setScale(2, RoundingMode.HALF_UP))));
        sb.append(String.format("%9d", wkCnt1tP));
        sb.append(
                String.format(
                        "%15s",
                        decimal006AmtFormat.format(wkAmt1tP.setScale(2, RoundingMode.HALF_UP))));
        file006Contents.add(sb.toString());

        // 024300 095-WRITET-EXIT.
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
        responseTextMap.put("RPTNAME", FILE_006_NAME);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
