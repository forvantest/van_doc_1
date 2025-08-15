/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT1;
import com.bot.ncl.dto.entities.CldtlbyRangeEntdyBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("STAT1Lsnr")
@Scope("prototype")
public class STAT1Lsnr extends BatchListenerCase<STAT1> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT1;
    @Autowired private TextFileUtil textFileRPT016;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private ClmrService clmrService;

    // 批次日期
    private String wkTaskYYYMM;
    private STAT1 event;
    List<CldtlbyRangeEntdyBus> lCldtl = new ArrayList<CldtlbyRangeEntdyBus>();

    private static final String CHARSET = "UTF-8";
    private Map<String, String> textMap;
    private String filePath = "";
    private String fileNameSTAT1 = "STAT1";
    private String fileNameRPT016 = "RPT-016";
    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT1;
    private List<String> fileContentsRPT016;

    /*STAT1 參數*/
    String stat1Code = "";

    /*DBS 參數*/

    String dbsCode = "";

    String dbsTxtype = "";

    /*WORKING STORAGE 參數*/
    String pbrName = "";
    int wkSDate = 0;
    int wkEDate = 0;
    int wkTotCnt_C = 0;
    int wkTotCnt_M = 0;
    int wkTotCnt_A = 0;
    int wkTotCnt_V = 0;
    int wkTotCnt_R = 0;
    int wkTotCnt_E = 0;
    int wkTotCnt_I = 0;
    int wkTotCnt_J = 0;
    int wkTotCnt_X = 0;
    int wkTotCnt_B = 0;
    int wkTotCnt_F = 0;
    int wkTotCnt_H = 0;
    int wkTotCnt_1 = 0;
    BigDecimal wkTotAmt_C = BigDecimal.ZERO;
    BigDecimal wkTotAmt_M = BigDecimal.ZERO;
    BigDecimal wkTotAmt_A = BigDecimal.ZERO;
    BigDecimal wkTotAmt_V = BigDecimal.ZERO;
    BigDecimal wkTotAmt_R = BigDecimal.ZERO;
    BigDecimal wkTotAmt_E = BigDecimal.ZERO;
    BigDecimal wkTotAmt_I = BigDecimal.ZERO;
    BigDecimal wkTotAmt_J = BigDecimal.ZERO;
    BigDecimal wkTotAmt_X = BigDecimal.ZERO;
    BigDecimal wkTotAmt_B = BigDecimal.ZERO;
    BigDecimal wkTotAmt_F = BigDecimal.ZERO;
    BigDecimal wkTotAmt_H = BigDecimal.ZERO;
    BigDecimal wkTotAmt_1 = BigDecimal.ZERO;
    int wkSubCnt = 0;
    int wkTotCnt = 0;
    BigDecimal wkSubAmt = BigDecimal.ZERO;
    BigDecimal wkTotAmt = BigDecimal.ZERO;
    int wkTotCode = 0;
    String wkCode = "";
    String wkPbname = "";

    // 輸出結果的路徑

    // 功能：夜間批次 – 從代收明細資料庫挑出上個月代收明細的資料，及產生各縣市停車費月報所需之檔案
    // 讀DB-CLDTL-DDS收付明細檔，挑上個月代收資料，寫檔
    // 寫FD-STAT1("DATA/CL/BH/ANALY/STAT1")
    // 寫FD-RPT016("DATA/CL/BH/RPT/016")

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT1 event) {
        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT1Lsnr run()");

        fileNameSTAT1 = fileDir + fileNameSTAT1;
        fileNameRPT016 = fileDir + fileNameRPT016;

        fileContentsSTAT1 = new ArrayList<String>();
        fileContentsRPT016 = new ArrayList<String>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskYYYMM = textMap.get("WK_TASK_YYYMM"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), wkTaskYYYMM);

        // 設定日期
        //        008800     MOVE    WK-TASK-YYYMM   TO     WK-TASK-YYYMM-N.
        //        008900     MOVE    WK-TASK-YYYMM-N TO     WK-TEMP-SYYYMM,WK-TEMP-EYYYMM.
        //        009000     MOVE    WK-TEMP-SDATE   TO     WK-SDATE.
        //        009100     MOVE    WK-TEMP-EDATE   TO     WK-EDATE.

        int thisMonth = parse.string2Integer(wkTaskYYYMM) / 100;

        wkSDate = thisMonth * 100 + 1;
        wkEDate = thisMonth * 100 + 31;

        // 011500     FIND NEXT  DB-CLDTL-IDX3 AT DB-CLDTL-DATE  NOT <  WK-SDATE
        // 011600                             AND DB-CLDTL-DATE  NOT >  WK-EDATE
        // 011700       ON EXCEPTION
        // 011800          PERFORM  1000-FILE-RTN  THRU  1000-FILE-EXIT
        // 011900          PERFORM  2000-FILE-RTN  THRU  2000-FILE-EXIT
        // 012000          GO  TO   0000-MAIN-EXIT.
        // 不會有EXCEPTION的問題 行數011800、011900、012000 可略過
        lCldtl = cldtlService.findbyRangeEntdy(wkSDate, wkEDate, 0, 0, Integer.MAX_VALUE);

        // RPT016
        List<String> parkFeeCodeList =
                Arrays.asList(
                        "510020", "510030", "510100", "510110", "510090", "510120", "510130",
                        "510140", "510150", "510160", "510170", "510180", "510190", "510200",
                        "510210", "510220", "510080", "510230", "510240", "510250", "510260",
                        "510270", "510280", "510290", "510300");

        List<String> internetBankApplCodeList = new ArrayList<String>();
        for (int i = 330001; i <= 330100; i++) {
            internetBankApplCodeList.add(i + "");
        }

        // 0000-MAIN-RTN.

        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "lCldtl.size = " + lCldtl.size());
        int cnt = 0;
        for (CldtlbyRangeEntdyBus r : lCldtl) {

            // 012006* 當資料為各縣市停車費時，另外寫一資料檔以方便產生月報
            // 012008     IF         DB-CLDTL-CODE       =     "510020"
            // 012010        OR      DB-CLDTL-CODE       =     "510030"
            // 012012        OR      DB-CLDTL-CODE       =     "510100"
            // 012014        OR      DB-CLDTL-CODE       =     "510110"
            // 012016        OR      DB-CLDTL-CODE       =     "510090"
            // 012018        OR      DB-CLDTL-CODE       =     "510120"
            // 012020        OR      DB-CLDTL-CODE       =     "510130"
            // 012022        OR      DB-CLDTL-CODE       =     "510140"
            // 012024        OR      DB-CLDTL-CODE       =     "510150"
            // 012026        OR      DB-CLDTL-CODE       =     "510160"
            // 012028        OR      DB-CLDTL-CODE       =     "510170"
            // 012030        OR      DB-CLDTL-CODE       =     "510180"
            // 012032        OR      DB-CLDTL-CODE       =     "510190"
            // 012034        OR      DB-CLDTL-CODE       =     "510200"
            // 012036        OR      DB-CLDTL-CODE       =     "510210"
            // 012038        OR      DB-CLDTL-CODE       =     "510220"
            // 012040        OR      DB-CLDTL-CODE       =     "510080"
            // 012042        OR      DB-CLDTL-CODE       =     "510230"
            // 012044        OR      DB-CLDTL-CODE       =     "510240"
            // 012046        OR      DB-CLDTL-CODE       =     "510250"
            // 012048        OR      DB-CLDTL-CODE       =     "510260"
            // 012050        OR      DB-CLDTL-CODE       =     "510270"
            // 012052        OR      DB-CLDTL-CODE       =     "510280"
            // 012055        OR      DB-CLDTL-CODE       =     "510290"
            // 012060        OR      DB-CLDTL-CODE       =     "510300"
            // 012065        PERFORM PARK-FEE-RTN       THRU    PARK-FEE-EXIT.
            // 012085* 當資料為網路銀行申購基金時不列入分析
            // 012100     IF         DB-CLDTL-CODE  NOT  <     "330001"
            // 012150        AND     DB-CLDTL-CODE  NOT  >     "330100"
            // 012200        GO  TO  0000-MAIN-LOOP.
            dbsCode = r.getCode();
            if (parkFeeCodeList.contains(dbsCode)) {
                cnt++;
                // 處理RPT016資料
                _PARK_FEE(r);

                continue;
            }

            if (internetBankApplCodeList.contains(dbsCode)) {
                continue;
            } else {
                cnt++;
            }
            // 012300     IF         DB-CLDTL-CODE     NOT =   WK-CODE
            // 012400       IF       WK-CODE           =       SPACES
            // 012500         MOVE   DB-CLDTL-CODE     TO      WK-CODE
            // 012600       ELSE
            // 012700         PERFORM  1000-FILE-RTN   THRU  1000-FILE-EXIT.
            // 013000     PERFORM    1000-TXTYPE-RTN   THRU    1000-TXTYPE-EXIT.

            ApLogHelper.info(log, false, LogType.BATCH.getCode(), "dbsCode = " + dbsCode);
            ApLogHelper.info(log, false, LogType.BATCH.getCode(), "wkCode = " + wkCode);

            if (!dbsCode.equals(wkCode)) {

                if (cnt > 1) {
                    _1000_FILE(r);
                }
                wkCode = dbsCode;
            }
            _1000_TXTYPE(r);

            BigDecimal cldtlAmt =
                    r.getAmt() == null ? BigDecimal.ZERO : new BigDecimal(r.getAmt().toString());
            //// 累加筆數金額
            //
            // 013100     ADD        1                 TO      WK-SUBCNT.
            // 013200     ADD        DB-CLDTL-AMT      TO      WK-SUBAMT.

            wkSubCnt = wkSubCnt + 1;
            wkSubAmt = wkSubAmt.add(cldtlAmt);

            //// 累加總筆數總金額
            // 013300     ADD        1                 TO      WK-TOTCNT.
            // 013400     ADD        DB-CLDTL-AMT      TO      WK-TOTAMT.
            // 013500     GO TO      0000-MAIN-LOOP.
            wkTotCnt = wkTotCnt + 1;
            wkTotAmt = wkTotAmt.add(cldtlAmt);
        }

        // 執行最後一筆
        if (parkFeeCodeList.contains(dbsCode)) {
            // 處理RPT016資料
            _PARK_FEE(lCldtl.get(cnt - 1));
        }
        if (!internetBankApplCodeList.contains(dbsCode)) {
            _1000_FILE(lCldtl.get(cnt - 1));
        }

        // 0000-MAIN-EXIT.

        writeFile();
    }

    private void _1000_FILE(CldtlbyRangeEntdyBus r) {
        // 代收分析月報－以分行排序的檔案

        // 001700  FD  FD-STAT1
        // 001800      RECORD       CONTAINS      101     CHARACTERS
        // 001900      BLOCK        CONTAINS        1     RECORDS
        // 002000      VALUE   OF   FILENAME       IS      "DATA/CL/BH/ANALY/STAT1"
        // 002100                   SECURITYTYPE   IS      PUBLIC.
        // 002200  01  STAT1-REC.
        // 002300      03  STAT1-CODE                       PIC X(06).
        // 002400      03  STAT1-CODENAME                   PIC X(40).
        // 002500      03  STAT1-PBRNO                      PIC 9(03).
        // 002600      03  STAT1-PBRNAME                    PIC X(20).
        // 002700      03  STAT1-TXTYPE                     PIC X(12).
        // 002800      03  STAT1-TOTCNT                     PIC 9(07).
        // 002900      03  STAT1-TOTAMT                     PIC 9(12).
        // 003000      03  FILLER                           PIC X(01).

        //// 搬相關資料到STAT1-REC
        //
        // 014000     MOVE       SPACES            TO      STAT1-REC.
        // 014100     MOVE       WK-CODE           TO      STAT1-CODE.

        //        String stat1Code = "";
        String stat1CodeName = "";
        int stat1Pbrno = 0;
        String stat1PbrName = "";
        String stat1Txtype = "";
        int stat1TotCnt = 0;
        BigDecimal stat1TotAmt = BigDecimal.ZERO;
        stat1Code = wkCode;

        //// 依 代收類別 FIND NEXT 事業單位交易設定檔,若有誤顯示代收類別
        //
        // 014200     FIND       DB-CLMR-IDX1  AT  DB-CLMR-CODE = WK-CODE
        // 014300         ON EXCEPTION DISPLAY "CODE=" WK-CODE.
        // 014400     MOVE       DB-CLMR-CNAME     TO      STAT1-CODENAME.
        // 014500     MOVE       DB-CLMR-PBRNO     TO      STAT1-PBRNO.
        ClmrBus clmr = clmrService.findById(r.getCode());
        Bctl bctl = new Bctl();
        if (clmr == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CODE=" + wkCode);
        } else {
            stat1CodeName = clmr.getCname();
            stat1Pbrno = clmr.getPbrno();
            bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(clmr.getPbrno());
        }

        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定主辦分行變數值；
        //// 若有誤，搬"N"
        //
        // 014600     FIND       DB-BCTL-ACCESS AT DB-BCTL-BRNO = DB-CLMR-PBRNO
        // 014700         ON EXCEPTION
        // 014800            MOVE  "N"             TO      WK-PBRNAME.

        if (bctl == null) {
            wkPbname = "N";
        }

        //// IF WK-PBRNAME = "Y",搬主辦分行中文名稱,否則搬空白
        //
        // 014900     IF         WK-PBRNAME        =       "Y"
        // 015000       PERFORM  0000-CNV-RTN      THRU    0000-CNV-EXIT
        // 015100     ELSE
        // 015200       MOVE     SPACES            TO      STAT1-PBRNAME.

        if ("Y".equals(wkPbname)) {
            stat1PbrName = getPbrName(r.getPbrno());
        } else {
            stat1PbrName = "";
        }

        // 015300*** 判斷各帳務別是否有代收 ***
        // 015400     IF         WK-TOTCNT-C       >       0
        // 015500       MOVE     " 現金 "          TO      STAT1-TXTYPE
        // 015600       MOVE     WK-TOTCNT-C       TO      STAT1-TOTCNT
        // 015700       MOVE     WK-TOTAMT-C       TO      STAT1-TOTAMT
        // 015800       WRITE    STAT1-REC
        // 015900       MOVE     SPACES            TO      STAT1-REC.
        // 016000     IF         WK-TOTCNT-M       >       0
        // 016100       MOVE     " 轉帳 "          TO      STAT1-TXTYPE
        // 016200       MOVE     WK-TOTCNT-M       TO      STAT1-TOTCNT
        // 016300       MOVE     WK-TOTAMT-M       TO      STAT1-TOTAMT
        // 016400       WRITE    STAT1-REC
        // 016500       MOVE     SPACES            TO      STAT1-REC.
        // 016600     IF         WK-TOTCNT-A       >       0
        // 016700       MOVE     " 自動櫃員機 "    TO      STAT1-TXTYPE
        // 016800       MOVE     WK-TOTCNT-A       TO      STAT1-TOTCNT
        // 016900       MOVE     WK-TOTAMT-A       TO      STAT1-TOTAMT
        // 017000       WRITE    STAT1-REC
        // 017100       MOVE     SPACES            TO      STAT1-REC.
        // 017200     IF         WK-TOTCNT-V       >       0
        // 017300       MOVE     " 語音 "          TO      STAT1-TXTYPE
        // 017400       MOVE     WK-TOTCNT-V       TO      STAT1-TOTCNT
        // 017500       MOVE     WK-TOTAMT-V       TO      STAT1-TOTAMT
        // 017600       WRITE    STAT1-REC
        // 017700       MOVE     SPACES            TO      STAT1-REC.
        // 017800     IF         WK-TOTCNT-R       >       0
        // 017900       MOVE     " 匯款 "          TO      STAT1-TXTYPE
        // 018000       MOVE     WK-TOTCNT-R       TO      STAT1-TOTCNT
        // 018100       MOVE     WK-TOTAMT-R       TO      STAT1-TOTAMT
        // 018200       WRITE    STAT1-REC
        // 018300       MOVE     SPACES            TO      STAT1-REC.
        // 018400     IF         WK-TOTCNT-E       >       0
        // 018500       MOVE     " ＥＤＩ "        TO      STAT1-TXTYPE
        // 018600       MOVE     WK-TOTCNT-E       TO      STAT1-TOTCNT
        // 018700       MOVE     WK-TOTAMT-E       TO      STAT1-TOTAMT
        // 018800       WRITE    STAT1-REC
        // 018900       MOVE     SPACES            TO      STAT1-REC.
        // 019000     IF         WK-TOTCNT-I       >       0
        // 019100       MOVE     " 網路銀行 "      TO      STAT1-TXTYPE
        // 019200       MOVE     WK-TOTCNT-I       TO      STAT1-TOTCNT
        // 019300       MOVE     WK-TOTAMT-I       TO      STAT1-TOTAMT
        // 019400       WRITE    STAT1-REC
        // 019500       MOVE     SPACES            TO      STAT1-REC.
        // 019600     IF         WK-TOTCNT-J       >       0
        // 019700       MOVE     " 行動支付 "      TO      STAT1-TXTYPE
        // 019800       MOVE     WK-TOTCNT-J       TO      STAT1-TOTCNT
        // 019900       MOVE     WK-TOTAMT-J       TO      STAT1-TOTAMT
        // 020000       WRITE    STAT1-REC
        // 020100       MOVE     SPACES            TO      STAT1-REC.
        // 020200     IF         WK-TOTCNT-X       >       0
        // 020300       MOVE     " 金融ＸＭＬ "    TO      STAT1-TXTYPE
        // 020400       MOVE     WK-TOTCNT-X       TO      STAT1-TOTCNT
        // 020500       MOVE     WK-TOTAMT-X       TO      STAT1-TOTAMT
        // 020600       WRITE    STAT1-REC
        // 020700       MOVE     SPACES            TO      STAT1-REC.
        // 020800     IF         WK-TOTCNT-B       >       0
        // 020900       MOVE     " 批次扣繳 "      TO      STAT1-TXTYPE
        // 021000       MOVE     WK-TOTCNT-B       TO      STAT1-TOTCNT
        // 021100       MOVE     WK-TOTAMT-B       TO      STAT1-TOTAMT
        // 021200       WRITE    STAT1-REC
        // 021300       MOVE     SPACES            TO      STAT1-REC.
        // 021310     IF         WK-TOTCNT-F       >       0
        // 021320       MOVE     " 全國繳費跨 "    TO      STAT1-TXTYPE
        // 021330       MOVE     WK-TOTCNT-F       TO      STAT1-TOTCNT
        // 021340       MOVE     WK-TOTAMT-F       TO      STAT1-TOTAMT
        // 021350       WRITE    STAT1-REC
        // 021360       MOVE     SPACES            TO      STAT1-REC.
        // 021365     IF         WK-TOTCNT-H       >       0
        // 021370       MOVE     " 全國繳費自 "    TO      STAT1-TXTYPE
        // 021375       MOVE     WK-TOTCNT-H       TO      STAT1-TOTCNT
        // 021380       MOVE     WK-TOTAMT-H       TO      STAT1-TOTAMT
        // 021385       WRITE    STAT1-REC
        // 021390       MOVE     SPACES            TO      STAT1-REC.
        // 021391     IF         WK-TOTCNT-1       >       0
        // 021392       MOVE     " 交換 "          TO      STAT1-TXTYPE
        // 021393       MOVE     WK-TOTCNT-1       TO      STAT1-TOTCNT
        // 021394       MOVE     WK-TOTAMT-1       TO      STAT1-TOTAMT
        // 021395       WRITE    STAT1-REC
        // 021396       MOVE     SPACES            TO      STAT1-REC.
        // 021400     MOVE       " 小計 "          TO      STAT1-TXTYPE.
        // 021500     MOVE       WK-SUBCNT         TO      STAT1-TOTCNT.
        // 021600     MOVE       WK-SUBAMT         TO      STAT1-TOTAMT.
        //// 寫檔FD-STAT1
        // 021700     WRITE    STAT1-REC.

        if (wkTotCnt_C > 0) {
            stat1Txtype = " 現金 ";
            stat1TotCnt = wkTotCnt_C;
            stat1TotAmt = wkTotAmt_C;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_M > 0) {
            stat1Txtype = " 轉帳 ";
            stat1TotCnt = wkTotCnt_M;
            stat1TotAmt = wkTotAmt_M;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_A > 0) {
            stat1Txtype = " 自動櫃員機 ";
            stat1TotCnt = wkTotCnt_A;
            stat1TotAmt = wkTotAmt_A;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_V > 0) {
            stat1Txtype = " 語音 ";
            stat1TotCnt = wkTotCnt_V;
            stat1TotAmt = wkTotAmt_V;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_R > 0) {
            stat1Txtype = " 匯款 ";
            stat1TotCnt = wkTotCnt_R;
            stat1TotAmt = wkTotAmt_R;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_E > 0) {
            stat1Txtype = " ＥＤＩ ";
            stat1TotCnt = wkTotCnt_E;
            stat1TotAmt = wkTotAmt_E;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_I > 0) {
            stat1Txtype = " 網路銀行 ";
            stat1TotCnt = wkTotCnt_I;
            stat1TotAmt = wkTotAmt_I;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_J > 0) {
            stat1Txtype = " 行動支付 ";
            stat1TotCnt = wkTotCnt_J;
            stat1TotAmt = wkTotAmt_J;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_X > 0) {
            stat1Txtype = " 金融ＸＭＬ ";
            stat1TotCnt = wkTotCnt_X;
            stat1TotAmt = wkTotAmt_X;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_B > 0) {
            stat1Txtype = " 批次扣繳 ";
            stat1TotCnt = wkTotCnt_B;
            stat1TotAmt = wkTotAmt_B;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_F > 0) {
            stat1Txtype = " 全國繳費跨 ";
            stat1TotCnt = wkTotCnt_F;
            stat1TotAmt = wkTotAmt_F;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_H > 0) {
            stat1Txtype = " 全國繳費自 ";
            stat1TotCnt = wkTotCnt_H;
            stat1TotAmt = wkTotAmt_H;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }
        if (wkTotCnt_1 > 0) {
            stat1Txtype = " 交換 ";
            stat1TotCnt = wkTotCnt_1;
            stat1TotAmt = wkTotAmt_1;
            // 列印到STAT1檔案
            stat1Form(
                    stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);
        }

        // 初始化 STAT1 相關變數
        stat1Code = "";
        stat1CodeName = "";
        stat1Pbrno = 0;
        stat1PbrName = "";
        stat1Txtype = "";
        stat1TotCnt = 0;
        stat1TotAmt = BigDecimal.ZERO;

        stat1Txtype = " 小計 ";
        stat1TotCnt = wkSubCnt;
        stat1TotAmt = wkSubAmt;

        // 列印到STAT1檔案
        stat1Form(stat1CodeName, stat1Pbrno, stat1PbrName, stat1Txtype, stat1TotCnt, stat1TotAmt);

        // 021800     MOVE       "Y"               TO      WK-PBRNAME.
        // 021900     MOVE       DB-CLDTL-CODE     TO      WK-CODE.
        // 022000     ADD        1                 TO      WK-TOTCODE.
        // 022100     MOVE       0                 TO      WK-TOTCNT-C,WK-TOTAMT-C.
        // 022200     MOVE       0                 TO      WK-TOTCNT-M,WK-TOTAMT-M.
        // 022300     MOVE       0                 TO      WK-TOTCNT-A,WK-TOTAMT-A.
        // 022400     MOVE       0                 TO      WK-TOTCNT-V,WK-TOTAMT-V.
        // 022500     MOVE       0                 TO      WK-TOTCNT-R,WK-TOTAMT-R.
        // 022600     MOVE       0                 TO      WK-TOTCNT-E,WK-TOTAMT-E.
        // 022700     MOVE       0                 TO      WK-TOTCNT-I,WK-TOTAMT-I.
        // 022800     MOVE       0                 TO      WK-TOTCNT-J,WK-TOTAMT-J.
        // 022900     MOVE       0                 TO      WK-TOTCNT-X,WK-TOTAMT-X.
        // 023000     MOVE       0                 TO      WK-TOTCNT-B,WK-TOTAMT-B.
        // 023050     MOVE       0                 TO      WK-TOTCNT-F,WK-TOTAMT-F.
        // 023060     MOVE       0                 TO      WK-TOTCNT-H,WK-TOTAMT-H.
        // 023080     MOVE       0                 TO      WK-TOTCNT-1,WK-TOTAMT-1.
        // 023100     MOVE       0                 TO      WK-SUBCNT,WK-SUBAMT.
        // 023200 1000-FILE-EXIT.

        // 初始化 暫存變數
        wkCode = r.getCode();
        wkPbname = "Y";
        wkTotCode = 1;
        initWK(r);
    }

    private void stat1Form(
            String stat1CodeName,
            int stat1Pbrno,
            String stat1PbrName,
            String stat1Txtype,
            int stat1TotCnt,
            BigDecimal stat1TotAmt) {
        // 列印到STAT1檔案
        sb = new StringBuilder();

        sb.append(formatUtil.padX(stat1Code, 6));
        sb.append(formatUtil.padX(stat1CodeName, 40));
        sb.append(formatUtil.pad9(stat1Pbrno + "", 3));
        sb.append(formatUtil.padX(stat1PbrName, 20));
        sb.append(formatUtil.padX(stat1Txtype, 12));
        sb.append(formatUtil.pad9(stat1TotCnt + "", 12));
        sb.append(formatUtil.pad9(stat1TotAmt + "", 12));
        sb.append(formatUtil.padX("", 1));
        fileContentsSTAT1.add(sb.toString());
    }

    private void _PARK_FEE(CldtlbyRangeEntdyBus r) {
        // 各縣市停車費月報所需之檔案

        // 003005  FD  FD-RPT016
        // 003010        RECORD       CONTAINS       30     CHARACTERS
        // 003015        BLOCK        CONTAINS        1     RECORDS
        // 003020        VALUE   OF   FILENAME       IS     "DATA/CL/BH/RPT/016"
        // 003025        SECURITYTYPE   IS      PUBLIC.
        // 003030  01  RPT016-REC.
        // 003035      03  RPT016-CODE                      PIC X(06).
        // 003040      03  RPT016-DATE                      PIC 9(07).
        // 003045      03  RPT016-AMT                       PIC 9(10).
        // 003050      03  FILLER                           PIC X(07).

        // 034000 PARK-FEE-RTN.
        //// 搬相關資料到RPT016-REC
        //
        // 036000     MOVE       SPACES            TO      RPT016-REC.
        // 038000     MOVE       DB-CLDTL-CODE     TO      RPT016-CODE.
        // 040000     MOVE       DB-CLDTL-DATE     TO      RPT016-DATE.
        // 042000     MOVE       DB-CLDTL-AMT      TO      RPT016-AMT.

        String rpt016Code = "";
        int rpt016Date = 0;
        BigDecimal rpt016Amt = BigDecimal.ZERO;

        rpt016Code = r.getCode();
        rpt016Date = r.getEntdy();
        rpt016Amt = new BigDecimal(r.getAmt().toString());

        //// 寫檔FD-RPT016
        //
        // 044000     WRITE      RPT016-REC.
        // 046000 PARK-FEE-EXIT.

        sb = new StringBuilder();

        sb.append(formatUtil.padX(rpt016Code, 6));
        sb.append(formatUtil.pad9(rpt016Date + "", 7));
        sb.append(formatUtil.pad9(rpt016Amt + "", 10));
        sb.append(formatUtil.padX("", 7));
        fileContentsRPT016.add(sb.toString());
    }

    private void _1000_TXTYPE(CldtlbyRangeEntdyBus r) {
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "_1000_TXTYPE r= " + r.toString());
        // 024500 1000-TXTYPE-RTN.
        //
        //// 依帳務別種類,累加筆數金額
        //
        // 024600     IF         DB-CLDTL-TXTYPE   =       "C"
        // 024700       ADD      1                 TO      WK-TOTCNT-C
        // 024800       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-C
        // 024900       GO  TO   1000-TXTYPE-EXIT.
        // 025000     IF         DB-CLDTL-TXTYPE   =       "M"
        // 025100       ADD      1                 TO      WK-TOTCNT-M
        // 025200       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-M
        // 025300       GO  TO   1000-TXTYPE-EXIT.
        // 025400     IF         DB-CLDTL-TXTYPE   =       "A"
        // 025500       ADD      1                 TO      WK-TOTCNT-A
        // 025600       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-A
        // 025700       GO  TO   1000-TXTYPE-EXIT.
        // 025800     IF         DB-CLDTL-TXTYPE   =       "V"
        // 025900       ADD      1                 TO      WK-TOTCNT-V
        // 026000       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-V
        // 026100       GO  TO   1000-TXTYPE-EXIT.
        // 026200     IF         DB-CLDTL-TXTYPE   =       "R"
        // 026300       ADD      1                 TO      WK-TOTCNT-R
        // 026400       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-R
        // 026500       GO  TO   1000-TXTYPE-EXIT.
        // 026600     IF         DB-CLDTL-TXTYPE   =       "E"
        // 026700       ADD      1                 TO      WK-TOTCNT-E
        // 026800       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-E
        // 026900       GO  TO   1000-TXTYPE-EXIT.
        // 027000     IF         DB-CLDTL-TXTYPE   =       "I"
        // 027100       ADD      1                 TO      WK-TOTCNT-I
        // 027200       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-I
        // 027300       GO  TO   1000-TXTYPE-EXIT.
        // 027400     IF         DB-CLDTL-TXTYPE   =       "J"
        // 027500       ADD      1                 TO      WK-TOTCNT-J
        // 027600       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-J
        // 027700       GO  TO   1000-TXTYPE-EXIT.
        // 027800     IF         DB-CLDTL-TXTYPE   =       "X"
        // 027900       ADD      1                 TO      WK-TOTCNT-X
        // 028000       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-X
        // 028100       GO  TO   1000-TXTYPE-EXIT.
        // 028200     IF         DB-CLDTL-TXTYPE   =       "B"
        // 028300       ADD      1                 TO      WK-TOTCNT-B
        // 028400       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-B.
        // 028420     IF         DB-CLDTL-TXTYPE   =       "F" OR "P"
        // 028440       ADD      1                 TO      WK-TOTCNT-F
        // 028460       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-F.
        // 028470     IF         DB-CLDTL-TXTYPE   =       "H" OR "G"
        // 028480       ADD      1                 TO      WK-TOTCNT-H
        // 028490       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-H.
        // 028492     IF         DB-CLDTL-TXTYPE   =       "N"
        // 028494       ADD      1                 TO      WK-TOTCNT-1
        // 028496       ADD      DB-CLDTL-AMT      TO      WK-TOTAMT-1.
        // 028500 1000-TXTYPE-EXIT.
        dbsTxtype = r.getTxtype();
        BigDecimal amt =
                r.getAmt() == null ? BigDecimal.ZERO : new BigDecimal(r.getAmt().toString());

        if ("C".equals(dbsTxtype)) {
            wkTotCnt_C++;
            wkTotAmt_C = wkTotAmt_C.add(amt);
        }
        if ("M".equals(dbsTxtype)) {
            wkTotCnt_M++;
            wkTotAmt_M = wkTotAmt_M.add(amt);
        }
        if ("A".equals(dbsTxtype)) {
            wkTotCnt_A++;
            wkTotAmt_A = wkTotAmt_A.add(amt);
        }
        if ("V".equals(dbsTxtype)) {
            wkTotCnt_V++;
            wkTotAmt_V = wkTotAmt_V.add(amt);
        }
        if ("R".equals(dbsTxtype)) {
            wkTotCnt_R++;
            wkTotAmt_R = wkTotAmt_R.add(amt);
        }
        if ("E".equals(dbsTxtype)) {
            wkTotCnt_E++;
            wkTotAmt_E = wkTotAmt_E.add(amt);
        }
        if ("I".equals(dbsTxtype)) {
            wkTotCnt_I++;
            wkTotAmt_I = wkTotAmt_I.add(amt);
        }
        if ("J".equals(dbsTxtype)) {
            wkTotCnt_J++;
            wkTotAmt_J = wkTotAmt_J.add(amt);
        }
        if ("X".equals(dbsTxtype)) {
            wkTotCnt_X++;
            wkTotAmt_X = wkTotAmt_X.add(amt);
        }
        if ("B".equals(dbsTxtype)) {
            wkTotCnt_B++;
            wkTotAmt_B = wkTotAmt_B.add(amt);
        }
        if ("F".equals(dbsTxtype) || "P".equals(dbsTxtype)) {
            wkTotCnt_F++;
            wkTotAmt_F = wkTotAmt_F.add(amt);
        }
        if ("H".equals(dbsTxtype) || "G".equals(dbsTxtype)) {
            wkTotCnt_H++;
            wkTotAmt_H = wkTotAmt_H.add(amt);
        }
        if ("N".equals(dbsTxtype)) {
            wkTotCnt_1++;
            wkTotAmt_1 = wkTotAmt_1.add(amt);
        }
    }

    //    private void WORKING_STORAGE() {
    //        // 003200 DB BOTSRDB.
    //        // 003300 01 DB-CLMR-DDS.
    //        // 003400 01 DB-CLDTL-DDS.
    //        // 003500 01 DB-BCTL-DDS.
    //        // 003600 WORKING-STORAGE  SECTION.
    //        // 003700  01 WK-TASK-YYYMM-N                    PIC 9(05).
    //        // 003800  01 WK-TEMP-SDATE.
    //        // 003900    03 WK-TEMP-SYYYMM                   PIC 9(05).
    //        // 004000    03 WK-TEMP-SDD                      PIC 9(02) VALUE 01.
    //        // 004100  01 WK-TEMP-EDATE.
    //        // 004200    03 WK-TEMP-EYYYMM                   PIC 9(05).
    //        // 004300    03 WK-TEMP-EDD                      PIC 9(02) VALUE 31.
    //        // 004400  01 WK-SDATE                           PIC 9(07).
    //        // 004500  01 WK-EDATE                           PIC 9(07).
    //        // 004600  01 WK-CODE                            PIC X(06).
    //        // 004700  01 WK-TOTCNT-C                        PIC 9(06).
    //        // 004800  01 WK-TOTAMT-C                        PIC 9(12).
    //        // 004900  01 WK-TOTCNT-M                        PIC 9(06).
    //        // 005000  01 WK-TOTAMT-M                        PIC 9(12).
    //        // 005100  01 WK-TOTCNT-A                        PIC 9(06).
    //        // 005200  01 WK-TOTAMT-A                        PIC 9(12).
    //        // 005300  01 WK-TOTCNT-V                        PIC 9(06).
    //        // 005400  01 WK-TOTAMT-V                        PIC 9(12).
    //        // 005500  01 WK-TOTCNT-R                        PIC 9(06).
    //        // 005600  01 WK-TOTAMT-R                        PIC 9(12).
    //        // 005700  01 WK-TOTCNT-E                        PIC 9(06).
    //        // 005800  01 WK-TOTAMT-E                        PIC 9(12).
    //        // 005900  01 WK-TOTCNT-I                        PIC 9(06).
    //        // 006000  01 WK-TOTAMT-I                        PIC 9(12).
    //        // 006100  01 WK-TOTCNT-J                        PIC 9(06).
    //        // 006200  01 WK-TOTAMT-J                        PIC 9(12).
    //        // 006300  01 WK-TOTCNT-X                        PIC 9(06).
    //        // 006400  01 WK-TOTAMT-X                        PIC 9(12).
    //        // 006500  01 WK-TOTCNT-B                        PIC 9(06).
    //        // 006600  01 WK-TOTAMT-B                        PIC 9(12).
    //        // 006620  01 WK-TOTCNT-F                        PIC 9(06).
    //        // 006640  01 WK-TOTAMT-F                        PIC 9(12).
    //        // 006660  01 WK-TOTCNT-H                        PIC 9(06).
    //        // 006680  01 WK-TOTAMT-H                        PIC 9(12).
    //        // 006685  01 WK-TOTCNT-1                        PIC 9(06).
    //        // 006690  01 WK-TOTAMT-1                        PIC 9(12).
    //        // 006700  01 WK-SUBCNT                          PIC 9(12).
    //        // 006800  01 WK-SUBAMT                          PIC 9(12).
    //        // 006900  01 WK-TOTCNT                          PIC 9(07).
    //        // 007000  01 WK-TOTAMT                          PIC 9(12).
    //        // 007100  01 WK-TOT.
    //        // 007200   03 WK-TOTCODE                        PIC 9(06).
    //        // 007300   03 WK-TOTUNIT                        PIC X(04) VALUE  " 個 ".
    //        // 007400  01 WK-PBRNAME                         PIC X(01) VALUE  "Y".
    //        // 007500  01 WK-CLLBR-NAME-ORG.
    //        // 007600    03  WK-CLLBR-ORG-X   OCCURS   20    PIC X(01).
    //        // 007700  01 WK-CLLBR-NAME-TAG.
    //        // 007800    03  WK-CLLBR-TAG-X   OCCURS   20    PIC X(01).
    //        // 007900  77 WK-IDX                             PIC 9(02).
    //        // 008000  77 WK-TASK-YYYMM                      PIC 9(05) BINARY EXTENDED.
    //        // 008100*=========================================================
    //        // 008200 PROCEDURE        DIVISION  USING  WK-TASK-YYYMM.
    //
    //        for (CldtlbyRangeEntdyBus r : lCldtl) {
    //            sb = new StringBuilder();
    //
    //            sb.append(formatUtil.padX(r.getCode(), 6));
    //            sb.append(formatUtil.pad9(r.getEntdy() + "", 3));
    //            sb.append(formatUtil.pad9(r.getAmt() + "", 3));
    //            sb.append(formatUtil.padX("", 1));
    //
    //            //            fileContents.add(sb.toString());
    //
    //        }
    //    }

    private void writeFile() {

        textFileSTAT1.deleteFile(fileNameSTAT1);
        textFileRPT016.deleteFile(fileNameRPT016);

        try {
            textFileSTAT1.writeFileContent(fileNameSTAT1, fileContentsSTAT1, CHARSET);
            textFileRPT016.writeFileContent(fileNameRPT016, fileContentsRPT016, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private void initWK(CldtlbyRangeEntdyBus r) {
        //// 將DB-CLDTL-IDX3指標移至開始
        //
        // 010000     SET     DB-CLDTL-IDX3   TO    BEGINNING.
        //
        //// 清變數
        //

        // 010100     MOVE    0               TO    WK-TOTCNT-C,WK-TOTAMT-C.
        // 010200     MOVE    0               TO    WK-TOTCNT-M,WK-TOTAMT-M.
        // 010300     MOVE    0               TO    WK-TOTCNT-A,WK-TOTAMT-A.
        // 010400     MOVE    0               TO    WK-TOTCNT-V,WK-TOTAMT-V.
        // 010500     MOVE    0               TO    WK-TOTCNT-R,WK-TOTAMT-R.
        // 010600     MOVE    0               TO    WK-TOTCNT-E,WK-TOTAMT-E.
        // 010700     MOVE    0               TO    WK-TOTCNT-I,WK-TOTAMT-I.
        // 010800     MOVE    0               TO    WK-TOTCNT-J,WK-TOTAMT-J.
        // 010900     MOVE    0               TO    WK-TOTCNT-X,WK-TOTAMT-X.
        // 011000     MOVE    0               TO    WK-TOTCNT-B,WK-TOTAMT-B.
        // 011050     MOVE    0               TO    WK-TOTCNT-F,WK-TOTAMT-F.
        // 011060     MOVE    0               TO    WK-TOTCNT-H,WK-TOTAMT-H.
        // 011080     MOVE    0               TO    WK-TOTCNT-1,WK-TOTAMT-1.
        // 011100     MOVE    0               TO    WK-SUBCNT,WK-SUBAMT.
        // 011200     MOVE    0               TO    WK-TOTCNT,WK-TOTAMT,WK-TOTCODE.
        // 011300     MOVE    SPACES          TO    WK-CODE.

        wkTotCnt_C = 0;
        wkTotCnt_M = 0;
        wkTotCnt_A = 0;
        wkTotCnt_V = 0;
        wkTotCnt_R = 0;
        wkTotCnt_E = 0;
        wkTotCnt_I = 0;
        wkTotCnt_J = 0;
        wkTotCnt_X = 0;
        wkTotCnt_B = 0;
        wkTotCnt_F = 0;
        wkTotCnt_H = 0;
        wkTotCnt_1 = 0;
        wkTotAmt_C = BigDecimal.ZERO;
        wkTotAmt_M = BigDecimal.ZERO;
        wkTotAmt_A = BigDecimal.ZERO;
        wkTotAmt_V = BigDecimal.ZERO;
        wkTotAmt_R = BigDecimal.ZERO;
        wkTotAmt_E = BigDecimal.ZERO;
        wkTotAmt_I = BigDecimal.ZERO;
        wkTotAmt_J = BigDecimal.ZERO;
        wkTotAmt_X = BigDecimal.ZERO;
        wkTotAmt_B = BigDecimal.ZERO;
        wkTotAmt_F = BigDecimal.ZERO;
        wkTotAmt_H = BigDecimal.ZERO;
        wkTotAmt_1 = BigDecimal.ZERO;

        wkSubCnt = 0;
        wkTotCnt = 0;
        wkSubAmt = BigDecimal.ZERO;
        wkTotAmt = BigDecimal.ZERO;
        wkTotCode = 0;

        wkCode = "";
    }

    private String getPbrName(int pbrno) {
        String pbrName = "";
        if (pbrno == 5) {
            //    IF      WK-PBRNO-1 = 5
            //      MOVE " 公庫部 "     TO SD-PBRNAME
            pbrName = " 公庫部 ";
        } else if (pbrno == 9) {
            //    ELSE IF WK-PBRNO-1 = 9
            //      MOVE " 臺南分行 "   TO SD-PBRNAME
            pbrName = " 臺南分行 ";
        } else if (pbrno == 10) {
            //    ELSE IF WK-PBRNO-1 = 10
            //      MOVE " 臺中分行 "   TO SD-PBRNAME
            pbrName = " 臺中分行 ";
        } else if (pbrno == 11) {
            //    ELSE IF WK-PBRNO-1 = 11
            //      MOVE " 高雄分行 "   TO SD-PBRNAME
            pbrName = " 高雄分行 ";
        } else if (pbrno == 12) {
            //    ELSE IF WK-PBRNO-1 = 12
            //      MOVE " 基隆分行 "   TO SD-PBRNAME
            pbrName = " 基隆分行 ";
        } else if (pbrno == 14) {
            //    ELSE IF WK-PBRNO-1 = 14
            //      MOVE " 嘉義分行 "   TO SD-PBRNAME
            pbrName = " 嘉義分行 ";
        } else if (pbrno == 15) {
            //    ELSE IF WK-PBRNO-1 = 15
            //      MOVE " 新竹分行 "   TO SD-PBRNAME
            pbrName = " 新竹分行 ";
        } else if (pbrno == 16) {
            //    ELSE IF WK-PBRNO-1 = 16
            //      MOVE " 彰化分行 "   TO SD-PBRNAME
            pbrName = " 彰化分行 ";
        } else if (pbrno == 17) {
            //    ELSE IF WK-PBRNO-1 = 17
            //      MOVE " 屏東分行 "   TO SD-PBRNAME
            pbrName = " 屏東分行 ";
        } else if (pbrno == 18) {
            //    ELSE IF WK-PBRNO-1 = 18
            //      MOVE " 花蓮分行 "   TO SD-PBRNAME
            pbrName = " 花蓮分行 ";
        } else if (pbrno == 22) {
            //    ELSE IF WK-PBRNO-1 = 22
            //      MOVE " 宜蘭分行 "   TO SD-PBRNAME
            pbrName = " 宜蘭分行 ";
        } else if (pbrno == 23) {
            //    ELSE IF WK-PBRNO-1 = 23
            //      MOVE " 臺東分行 "   TO SD-PBRNAME
            pbrName = " 臺東分行 ";
        } else if (pbrno == 24) {
            //    ELSE IF WK-PBRNO-1 = 24
            //      MOVE " 澎湖分行 "   TO SD-PBRNAME
            pbrName = " 澎湖分行 ";
        } else if (pbrno == 25) {
            //    ELSE IF WK-PBRNO-1 = 25
            //      MOVE " 鳳山分行 "   TO SD-PBRNAME
            pbrName = " 鳳山分行 ";
        } else if (pbrno == 26) {
            //    ELSE IF WK-PBRNO-1 = 26
            //      MOVE " 桃園分行 "   TO SD-PBRNAME
            pbrName = " 桃園分行 ";
        } else if (pbrno == 27) {
            //    ELSE IF WK-PBRNO-1 = 27
            //      MOVE " 板橋分行 "   TO SD-PBRNAME
            pbrName = " 板橋分行 ";
        } else if (pbrno == 28) {
            //    ELSE IF WK-PBRNO-1 = 28
            //      MOVE " 新營分行 "   TO SD-PBRNAME
            pbrName = " 新營分行 ";
        } else if (pbrno == 29) {
            //    ELSE IF WK-PBRNO-1 = 29
            //      MOVE " 苗栗分行 "   TO SD-PBRNAME
            pbrName = " 苗栗分行 ";
        } else if (pbrno == 30) {
            //    ELSE IF WK-PBRNO-1 = 30
            //      MOVE " 豐原分行 "   TO SD-PBRNAME
            pbrName = " 豐原分行 ";
        } else if (pbrno == 31) {
            //    ELSE IF WK-PBRNO-1 = 31
            //      MOVE " 斗六分行 "   TO SD-PBRNAME
            pbrName = " 斗六分行 ";
        } else if (pbrno == 32) {
            //    ELSE IF WK-PBRNO-1 = 32
            //      MOVE " 南投分行 "   TO SD-PBRNAME
            pbrName = " 南投分行 ";
        } else if (pbrno == 42) {
            //    ELSE IF WK-PBRNO-1 = 42
            //      MOVE " 三重分行 "   TO SD-PBRNAME
            pbrName = " 三重分行 ";
        } else if (pbrno == 67) {
            //    ELSE IF WK-PBRNO-1 = 67
            //      MOVE " 太保分行 "   TO SD-PBRNAME
            pbrName = " 太保分行 ";
        } else if (pbrno == 68) {
            //    ELSE IF WK-PBRNO-1 = 68
            //      MOVE " 竹北分行 "   TO SD-PBRNAME
            pbrName = " 竹北分行 ";
        } else if (pbrno == 88) {
            //    ELSE IF WK-PBRNO-1 = 88
            //      MOVE " 潮洲分行 "   TO SD-PBRNAME
            pbrName = " 潮洲分行 ";
        } else {
            //    ELSE
            //      MOVE  SPACES        TO SD-PBRNAME.
            pbrName = " ";
        }
        return pbrName;
    }
}
