/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT4;
import com.bot.ncl.dto.entities.CldtlCldtlIdx2Bus;
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
import java.math.RoundingMode;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("STAT4Lsnr")
@Scope("prototype")
public class STAT4Lsnr extends BatchListenerCase<STAT4> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT4;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private ClmrService clmrService;

    private Map<String, String> textMap;
    private int tmpCnt = 0;
    private int cnt = 0;
    // 批次日期
    private String wkTaskYYYMM;
    int wkSDate = 0;
    int wkEDate = 0;
    private STAT4 event;
    List<CldtlCldtlIdx2Bus> lCldtl = new ArrayList<CldtlCldtlIdx2Bus>();

    List<CldtlCldtlIdx2Bus> tmplCldtl = new ArrayList<CldtlCldtlIdx2Bus>();
    List<Map<String, String>> listResult = new ArrayList<Map<String, String>>();

    Map<String, String> mapResult = new HashMap<String, String>();
    private static final String CHARSET = "Big5";
    private String fileNameSTAT4 = "STAT4";

    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT4;

    // working storage
    int wkCllbr = 0;
    String wkCllbrC = "";
    String wkBrno = "";
    String wkActno = "";
    BigDecimal wkSubCnt_V = BigDecimal.ZERO;
    BigDecimal wkSubAmt_V = BigDecimal.ZERO;
    BigDecimal wkSubCnt_I = BigDecimal.ZERO;
    BigDecimal wkSubAmt_I = BigDecimal.ZERO;
    BigDecimal wkSubCnt_C = BigDecimal.ZERO;
    BigDecimal wkSubAmt_C = BigDecimal.ZERO;
    BigDecimal wkSubCnt_M = BigDecimal.ZERO;
    BigDecimal wkSubAmt_M = BigDecimal.ZERO;
    BigDecimal wkSubCnt = BigDecimal.ZERO;
    BigDecimal wkSubAmt = BigDecimal.ZERO;
    BigDecimal wkSubCfee = BigDecimal.ZERO;

    BigDecimal wkTotCfee = BigDecimal.ZERO;
    BigDecimal wkTotCnt_V = BigDecimal.ZERO;
    BigDecimal wkTotAmt_V = BigDecimal.ZERO;
    BigDecimal wkTotCnt_I = BigDecimal.ZERO;
    BigDecimal wkTotAmt_I = BigDecimal.ZERO;
    BigDecimal wkTotCnt_C = BigDecimal.ZERO;
    BigDecimal wkTotAmt_C = BigDecimal.ZERO;
    BigDecimal wkTotCnt_M = BigDecimal.ZERO;
    BigDecimal wkTotAmt_M = BigDecimal.ZERO;
    BigDecimal wkSubCntAuto = BigDecimal.ZERO;
    BigDecimal wkSubAmtAuto = BigDecimal.ZERO;
    BigDecimal wkTotCntAuto = BigDecimal.ZERO;
    BigDecimal wkTotAmtAuto = BigDecimal.ZERO;
    BigDecimal wkTotCnt = BigDecimal.ZERO;
    BigDecimal wkTotAmt = BigDecimal.ZERO;

    /** stat4_REC *************************** */
    int stat4Cllbr = 0;

    String stat4Cllbrc = "";
    BigDecimal stat4_C_Cnt = BigDecimal.ZERO;
    BigDecimal stat4_C_Amt = BigDecimal.ZERO;
    BigDecimal stat4_M_Cnt = BigDecimal.ZERO;
    BigDecimal stat4_M_Amt = BigDecimal.ZERO;
    BigDecimal stat4_V_Cnt = BigDecimal.ZERO;
    BigDecimal stat4_V_Amt = BigDecimal.ZERO;
    BigDecimal stat4_I_Cnt = BigDecimal.ZERO;
    BigDecimal stat4_I_Amt = BigDecimal.ZERO;
    BigDecimal stat4_TotCnt = BigDecimal.ZERO;
    BigDecimal stat4_TotAmt = BigDecimal.ZERO;
    BigDecimal stat4CntRate1 = BigDecimal.ZERO;
    String stat4CntRate2 = "";
    BigDecimal stat4CntRate3 = BigDecimal.ZERO;
    String stat4CntRate4 = "";
    BigDecimal stat4AmtRate1 = BigDecimal.ZERO;
    String stat4AmtRate2 = "";
    BigDecimal stat4AmtRate3 = BigDecimal.ZERO;
    String stat4AmtRate4 = "";
    String filler = "";
    BigDecimal stat4Cfee = BigDecimal.ZERO;

    // S
    int sCllbr = 0;
    String sTxtype = "";
    BigDecimal sAmt = BigDecimal.ZERO;
    BigDecimal sCfee = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT4 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT4Lsnr");
        this.event = event;
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT4 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT4Lsnr run()");
        fileNameSTAT4 = fileDir + fileNameSTAT4;
        fileContentsSTAT4 = new ArrayList<String>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkTaskYYYMM = textMap.get("WK_TASK_YYYMM"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), wkTaskYYYMM);

        // 1071101/100 = 10711
        int thisMonth = parse.string2Integer(wkTaskYYYMM) / 100;

        wkSDate = thisMonth * 100 + 1;
        wkEDate = thisMonth * 100 + 31;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkSDate.... = " + wkSDate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEDate.... = " + wkEDate);

        // 010100 0000-START-RTN.
        //
        //// 開啟檔案
        //
        // 010200     OPEN INQUIRY BOTSRDB.
        // 010300     OPEN OUTPUT  FD-STAT4.
        // 010400     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        //
        //// 設定日期
        //
        // 010500     MOVE    WK-TASK-YYYMM    TO    WK-TASK-YYYMM-N.
        // 010600     MOVE    WK-TASK-YYYMM-N  TO    WK-TEMP-SYYYMM,WK-TEMP-EYYYMM.
        // 010700     MOVE    WK-TEMP-SDATE    TO    WK-SDATE.
        // 010800     MOVE    WK-TEMP-EDATE    TO    WK-EDATE.
        //
        //// 將DB-CLDTL-IDX2指標移至開始
        //
        // 010900     SET     DB-CLDTL-IDX2    TO    BEGINNING.
        //
        //// 清變數
        //
        // 011000     MOVE    0                TO    WK-TOTCFEE.
        // 011100     MOVE    0                TO    WK-TOTCNT-C,WK-TOTAMT-C.
        // 011200     MOVE    0                TO    WK-TOTCNT-M,WK-TOTAMT-M.
        // 011300     MOVE    0                TO    WK-TOTCNT-V,WK-TOTAMT-V,
        // 011350                                    WK-TOTCNT-I,WK-TOTAMT-I.
        // 011400     MOVE    0                TO    WK-TOTCNT,WK-TOTAMT.
        // 011500     MOVE    0                TO    WK-CLLBR.
        //
        //// 執行0000-MAIN-RTN
        //
        // 011600     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        // 011700 0000-END-RTN.
        _0000_MAIN();
        writeFile();
    }

    private void _0000_MAIN() {
        // 012200 0000-MAIN-RTN.
        //
        //// SORT INPUT 段落：CS-SORTIN(讀收付明細檔，挑代收日(大於等於本月1日,小於等於本月31日)，並寫至SORTFL)
        //// 資料照 S-CLLBR 由小到大排序
        //// SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫檔)
        //
        // 012300     SORT    SORTFL
        // 012400             ASCENDING KEY        S-CLLBR
        // 012401     MEMORY SIZE 6 MODULES
        // 012402     DISK SIZE 50 MODULES
        // 012403             INPUT  PROCEDURE     CS-SORTIN
        // 012600             OUTPUT PROCEDURE     CS-SORTOUT
        // 012800     .
        // 012900 0000-MAIN-EXIT.

        // ********SORTEL 可省略 SQL語句已上做處理了
        // ********SORTEL 跟 MAIN 放在同一個方法裡處理
        // CS-SORTIN == 0000-MAIN

        // 013300 CS-SORTIN-RTN.
        //
        //// 依 代收日(大於等於本月1日,小於等於本月31日) FIND NEXT 收付明細檔
        //// 正常，則執行下一步驟，否則跳到CS-SORTIN-EXIT
        //
        // 013400     FIND NEXT  DB-CLDTL-IDX2 AT DB-CLDTL-DATE NOT <  WK-SDATE
        // 013500                             AND DB-CLDTL-DATE NOT >  WK-EDATE
        // 013600       ON EXCEPTION
        // 013700       GO TO     CS-SORTIN-EXIT.

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "_0000_MAIN(CS-SORTIN)");
        lCldtl = cldtlService.findCldtlIdx2(wkSDate, wkEDate, 0, 0, Integer.MAX_VALUE);

        if (lCldtl == null) {
            return;
        }

        // 因為會有網銀語音繳款帳號前三碼(分行代號) 跟 代收行 會不同，要另外暫存後再做排序
        for (CldtlCldtlIdx2Bus r : lCldtl) {
            String dbCldtlTxType = r.getTxtype().isEmpty() ? "" : r.getTxtype().trim();
            String dbCldtlActno = r.getActno().isEmpty() ? "" : r.getActno().trim();
            String dbCldtlBrno = dbCldtlActno.isEmpty() ? "000" : dbCldtlActno.substring(0, 3);
            int dbCldtlCllbr = r.getCllbr();

            if ("C".equals(dbCldtlTxType)) {

            } else if ("M".equals(dbCldtlTxType)) {

            } else if ("I".equals(dbCldtlTxType)) {

            } else if ("G".equals(dbCldtlTxType)) {

            } else if ("V".equals(dbCldtlTxType)) {

            } else if ("W".equals(dbCldtlTxType)) {

            } else {
                continue;
            }

            if (("I".equals(dbCldtlTxType)
                            || "V".equals(dbCldtlTxType)
                            || "W".equals(dbCldtlTxType))
                    && !dbCldtlActno.isEmpty()) {
                sCllbr = parse.string2Integer(dbCldtlBrno);

            } else {
                sCllbr = dbCldtlCllbr;
            }
            if ("000".equals(dbCldtlBrno)) {
                sCllbr = dbCldtlCllbr;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlActno =" + dbCldtlActno);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlBrno =" + dbCldtlBrno);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "before =" + dbCldtlCllbr);
            r.setCllbr(sCllbr);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "after =" + r.getCllbr());
            tmplCldtl.add(r);
        }

        tmplCldtl.sort(
                (c1, c2) -> {
                    if (c1.getCllbr() - c2.getCllbr() != 0) {
                        return c1.getCllbr() - c2.getCllbr();
                    } else {
                        return 0;
                    }
                });

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lCldtl.size...=" + lCldtl.size());
        int dbCldtlCodeInt = 0;
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "tmplCldtl.size...=" + tmplCldtl.size());
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tmplCldtl=" + tmplCldtl);
        // 分行別排序正確後
        for (CldtlCldtlIdx2Bus r : tmplCldtl) {
            cnt++;

            String dbCldtlCode = r.getCode().isEmpty() ? "" : r.getCode().trim();
            String dbCldtlTxType = r.getTxtype().isEmpty() ? "" : r.getTxtype().trim();
            String dbCldtlActno = r.getActno().isEmpty() ? "" : r.getActno().trim();
            String dbCldtlBrno = dbCldtlActno.isEmpty() ? "000" : r.getActno().substring(0, 3);
            int dbCldtlCllbr = r.getCllbr();
            BigDecimal dbsCldtlAmt =
                    r.getAmt().compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : new BigDecimal(r.getAmt().toString());
            // 013800* 代收類別為１２１ＸＸＸ時為代付性質交易，不列入代收分析
            // 013900     IF          DB-CLDTL-CODE    NOT  <    "121000"
            // 014000         AND     DB-CLDTL-CODE    NOT  >    "121999"
            // 014100                 GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                dbCldtlCodeInt = parse.string2Integer(dbCldtlCode);
                if (dbCldtlCodeInt >= 121000 && dbCldtlCodeInt <= 121999) {
                    //                    continue;
                }
            }
            // 014200* 當資料為批次扣繳時不列入分析資料
            // 014300     IF          DB-CLDTL-TXTYPE    =       "B"
            // 014400        GO  TO   CS-SORTIN-RTN.
            if ("B".equals(dbCldtlTxType)) {
                continue;
            }
            // 014500* 當資料為自動櫃員機或匯款時或ＥＤＩ時或全國性繳費或金融ＸＭＬ時
            // 014550*  不列入分析資料
            // 014600     IF          DB-CLDTL-TXTYPE = "A" OR = "R" OR = "E" OR = "F"
            // 014650                              OR = "X" OR = "P" OR = "J" OR = "H"
            // 014700        GO  TO   CS-SORTIN-RTN.
            if ("A".equals(dbCldtlTxType)
                    || "R".equals(dbCldtlTxType)
                    || "E".equals(dbCldtlTxType)
                    || "F".equals(dbCldtlTxType)
                    || "X".equals(dbCldtlTxType)
                    || "P".equals(dbCldtlTxType)
                    || "J".equals(dbCldtlTxType)
                    || "H".equals(dbCldtlTxType)) {
                continue;
            }
            // 014800* 當資料為代繳申請或網路銀行申購基金時不列入分析資料
            // 014900     IF          DB-CLDTL-TXTYPE    =       "U"
            // 015000        GO  TO   CS-SORTIN-RTN.
            if ("U".equals(dbCldtlTxType)) {
                continue;
            }
            // 015100     IF          DB-CLDTL-CODE    NOT  <     "330001"
            // 015200         AND     DB-CLDTL-CODE    NOT  >     "330100"
            // 015300        GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                if (dbCldtlCodeInt >= 330001 && dbCldtlCodeInt <= 330100) {
                    continue;
                }
            }
            // 015320     IF          DB-CLDTL-CODE    NOT  <     "600000"
            // 015340         AND     DB-CLDTL-CODE    NOT  >     "699999"
            // 015360        GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                if (dbCldtlCodeInt >= 600000 && dbCldtlCodeInt <= 699999) {
                    continue;
                }
            }
            // 015380* 開始列入分析，但網路ＡＴＭ他行卡即ＷＫ－ＢＲＮＯ為０時不分析
            // 015400     IF        ( DB-CLDTL-TXTYPE    =  "I"  OR = "V"  OR = "W" )
            // 015500          AND  ( DB-CLDTL-ACTNO NOT =  SPACES )
            // 015600        MOVE     DB-CLDTL-ACTNO    TO       WK-ACTNO
            // 015620        IF       WK-BRNO           =        0
            // 015640          GO  TO CS-SORTIN-RTN
            // 015660        ELSE
            // 015700          MOVE   WK-BRNO           TO       S-CLLBR
            // 015800     ELSE
            // 015900        MOVE     DB-CLDTL-CLLBR    TO       S-CLLBR.
            //            this.log("dbCldtlTxType", dbCldtlTxType);
            //            if (("I".equals(dbCldtlTxType)
            //                            || "V".equals(dbCldtlTxType)
            //                            || "W".equals(dbCldtlTxType))
            //                    && !dbCldtlActno.isEmpty()) {
            //                wkActno = dbCldtlActno;
            //                wkBrno = dbCldtlBrno;
            //                if ("000".equals(wkBrno)) {
            //                    continue;
            //                } else {
            //                    sCllbr = parse.string2Integer(wkBrno);
            //                }
            //
            //            } else {
            //                sCllbr = dbCldtlCllbr;
            //            }
            //            this.log("dbCldtlActno", dbCldtlActno);
            //            this.log("dbCldtlBrno", dbCldtlBrno);
            //            this.log("sCllbr11", sCllbr + "");

            // 016000     MOVE        DB-CLDTL-TXTYPE   TO       S-TXTYPE.
            // 016100     MOVE        DB-CLDTL-AMT      TO       S-AMT.
            sTxtype = dbCldtlTxType;
            sAmt = dbsCldtlAmt;
            // 016200     IF          DB-CLDTL-TXTYPE   = "I"  OR = "V"  OR = "W"
            // 016300                              OR   = "G"
            // 016400       PERFORM   0000-CLMR-RTN     THRU     0000-CLMR-EXIT
            // 016500     ELSE
            // 016600       MOVE      0                 TO       S-CFEE.
            // 016700     RELEASE     SORT-REC.
            // 016800     GO TO CS-SORTIN-RTN.
            // 016900 CS-SORTIN-EXIT.
            if ("I".equals(dbCldtlTxType)
                    || "V".equals(dbCldtlTxType)
                    || "W".equals(dbCldtlTxType)
                    || "G".equals(dbCldtlTxType)) {
                ClmrBus clmrbus = clmrService.findById(dbCldtlCode);
                if (!Objects.isNull(clmrbus)) {
                    sCfee = clmrbus.getAmt();
                }
            } else {
                sCfee = BigDecimal.ZERO;
            }

            _SORTIN_RTN();
        }
        _1000_DTL();
        addList();
        _2000_TOT();
        addList();

        listResult.sort(
                (c1, c2) -> {
                    int cllbr1 = parse.string2Integer(c1.get("stat4Cllbr"));
                    int cllbr2 = parse.string2Integer(c2.get("stat4Cllbr"));
                    if (cllbr1 - cllbr2 != 0) {
                        return cllbr1 - cllbr2;
                    } else {
                        return 0;
                    }
                });

        cnt = 0;
        this.log("listResult", listResult.toString());
        for (Map<String, String> r : listResult) {
            cnt++;

            write_STAT4_REC(r);
        }
    }

    private void _SORTIN_RTN() {

        // 017300 CS-SORTOUT-RTN.
        //
        //// 清變數
        //
        // 017400     MOVE    0                TO    WK-SUBCNT-V,WK-SUBAMT-V,
        // 017450                                    WK-SUBCNT-I,WK-SUBAMT-I.
        // 017500     MOVE    0                TO    WK-SUBCNT-C,WK-SUBAMT-C.
        // 017600     MOVE    0                TO    WK-SUBCNT-M,WK-SUBAMT-M.
        // 017700     MOVE    0                TO    WK-SUBCNT,WK-SUBAMT.
        // 017800     MOVE    0                TO    WK-SUBCFEE.
        //        wkSubCnt_V = BigDecimal.ZERO;
        //        wkSubAmt_V = BigDecimal.ZERO;
        //        wkSubCnt_I = BigDecimal.ZERO;
        //        wkSubAmt_I = BigDecimal.ZERO;
        //        wkSubCnt_C = BigDecimal.ZERO;
        //        wkSubAmt_C = BigDecimal.ZERO;
        //        wkSubCnt_M = BigDecimal.ZERO;
        //        wkSubAmt_M = BigDecimal.ZERO;
        //        wkSubCnt = BigDecimal.ZERO;
        //        wkSubAmt = BigDecimal.ZERO;
        //        wkSubCfee = BigDecimal.ZERO;

        // 017900 0000-LOOP.

        //// 將SORT後的記錄讀出，直到檔尾
        ////  若WK-CLLBR=0，表無資料，GO TO CS-SORTOUT-EXIT，結束本節
        ////  其他
        ////    A.執行1000-DTL-RTN 寫FD-STAT4(明細)
        ////    B.執行2000-TOT-RTN 寫FD-STAT4(合計)
        ////    C.GO TO CS-SORTOUT-EXIT，結束本節
        //
        // 018000     RETURN     SORTFL      AT END
        // 018100       IF       WK-CLLBR         =        0
        // 018200         GO TO  CS-SORTOUT-EXIT
        // 018300       ELSE
        // 018400         PERFORM  1000-DTL-RTN   THRU   1000-DTL-EXIT
        // 018500         PERFORM  2000-TOT-RTN   THRU   2000-TOT-EXIT
        // 018600         GO TO CS-SORTOUT-EXIT
        // 018650       END-IF.

        // 以上可省略

        //// 若CLLBR不同
        ////  若WK-CLLBR=0，WK-CLLBR設定為S-CLLBR
        ////  其他
        ////   A.執行1000-DTL-RTN 寫FD-STAT4(明細)
        //
        // 018700     IF         S-CLLBR           NOT =   WK-CLLBR
        // 018800       IF       WK-CLLBR          =       0
        // 018900         MOVE   S-CLLBR           TO      WK-CLLBR
        // 019000       ELSE
        // 019100         PERFORM  1000-DTL-RTN   THRU     1000-DTL-EXIT.

        //        if (!(sCllbr + "").equals(wkCllbr + "")) {
        //            if (wkCllbr == 0) {
        //                wkCllbr = sCllbr;
        //            } else {
        //                _1000_DTL();
        //            }
        //        }

        //// 依帳務別種類,累加筆數金額
        //
        // 019400     IF         S-TXTYPE          =       "C"
        // 019500       ADD      1                 TO      WK-SUBCNT-C
        // 019600       ADD      1                 TO      WK-TOTCNT-C
        // 019700       ADD      1                 TO      WK-SUBCNT
        // 019800       ADD      1                 TO      WK-TOTCNT
        // 019900       ADD      S-AMT             TO      WK-SUBAMT
        // 020000       ADD      S-AMT             TO      WK-TOTAMT
        // 020100       ADD      S-AMT             TO      WK-SUBAMT-C
        // 020200       ADD      S-AMT             TO      WK-TOTAMT-C
        // 020300       GO  TO   0000-LOOP.
        // 020400     IF         S-TXTYPE          =       "M"
        // 020500       ADD      1                 TO      WK-SUBCNT-M
        // 020600       ADD      1                 TO      WK-TOTCNT-M
        // 020700       ADD      1                 TO      WK-SUBCNT
        // 020800       ADD      1                 TO      WK-TOTCNT
        // 020900       ADD      S-AMT             TO      WK-SUBAMT
        // 021000       ADD      S-AMT             TO      WK-TOTAMT
        // 021100       ADD      S-AMT             TO      WK-SUBAMT-M
        // 021200       ADD      S-AMT             TO      WK-TOTAMT-M
        // 021300       GO  TO   0000-LOOP.
        // 021350     IF         S-TXTYPE          =       "I" OR = "G"
        // 021400       ADD      1                 TO      WK-SUBCNT-I
        // 021500       ADD      1                 TO      WK-TOTCNT-I
        // 021600       ADD      1                 TO      WK-SUBCNT
        // 021700       ADD      1                 TO      WK-TOTCNT
        // 021800       ADD      S-AMT             TO      WK-SUBAMT
        // 021900       ADD      S-AMT             TO      WK-TOTAMT
        // 022000       ADD      S-AMT             TO      WK-SUBAMT-I
        // 022100       ADD      S-AMT             TO      WK-TOTAMT-I
        // 022200       ADD      S-CFEE            TO      WK-SUBCFEE,WK-TOTCFEE
        // 022300       GO  TO   0000-LOOP.
        // 022305     IF         S-TXTYPE          =       "V" OR = "W"
        // 022310       ADD      1                 TO      WK-SUBCNT-V
        // 022315       ADD      1                 TO      WK-TOTCNT-V
        // 022320       ADD      1                 TO      WK-SUBCNT
        // 022325       ADD      1                 TO      WK-TOTCNT
        // 022330       ADD      S-AMT             TO      WK-SUBAMT
        // 022335       ADD      S-AMT             TO      WK-TOTAMT
        // 022340       ADD      S-AMT             TO      WK-SUBAMT-V
        // 022345       ADD      S-AMT             TO      WK-TOTAMT-V
        // 022350       GO  TO   0000-LOOP.
        // 022400 CS-SORTOUT-EXIT.
        if ("C".equals(sTxtype)) {
            wkSubCnt_C = wkSubCnt_C.add(BigDecimal.ONE);
            wkTotCnt_C = wkTotCnt_C.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_C = wkSubAmt_C.add(sAmt);
            wkTotAmt_C = wkTotAmt_C.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        } else if ("M".equals(sTxtype)) {
            wkSubCnt_M = wkSubCnt_M.add(BigDecimal.ONE);
            wkTotCnt_M = wkTotCnt_M.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_M = wkSubAmt_M.add(sAmt);
            wkTotAmt_M = wkTotAmt_M.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        } else if ("I".equals(sTxtype)) {
            wkSubCnt_I = wkSubCnt_I.add(BigDecimal.ONE);
            wkTotCnt_I = wkTotCnt_I.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_I = wkSubAmt_I.add(sAmt);
            wkTotAmt_I = wkTotAmt_I.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        } else if ("G".equals(sTxtype)) {
            wkSubCnt_I = wkSubCnt_I.add(BigDecimal.ONE);
            wkTotCnt_I = wkTotCnt_I.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_I = wkSubAmt_I.add(sAmt);
            wkTotAmt_I = wkTotAmt_I.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        } else if ("V".equals(sTxtype)) {
            wkSubCnt_V = wkSubCnt_V.add(BigDecimal.ONE);
            wkTotCnt_V = wkTotCnt_V.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_V = wkSubAmt_V.add(sAmt);
            wkTotAmt_V = wkTotAmt_V.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        } else if ("W".equals(sTxtype)) {
            wkSubCnt_V = wkSubCnt_V.add(BigDecimal.ONE);
            wkTotCnt_V = wkTotCnt_V.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_V = wkSubAmt_V.add(sAmt);
            wkTotAmt_V = wkTotAmt_V.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        }

        //// 若CLLBR不同
        ////  若WK-CLLBR=0，WK-CLLBR設定為S-CLLBR
        ////  其他
        ////   A.執行1000-DTL-RTN 寫FD-STAT4(明細)
        //
        // 018700     IF         S-CLLBR           NOT =   WK-CLLBR
        // 018800       IF       WK-CLLBR          =       0
        // 018900         MOVE   S-CLLBR           TO      WK-CLLBR
        // 019000       ELSE
        // 019100         PERFORM  1000-DTL-RTN   THRU     1000-DTL-EXIT.
        this.log("sCllbr00", sCllbr + "");
        this.log("wkCllbr00", wkCllbr + "");
        if (!(sCllbr + "").equals(wkCllbr + "")) {

            wkCllbr = sCllbr;
            _1000_DTL();
            if (tmpCnt > 0) {

                addList();

                wkCllbrC = "Y";
                wkSubCnt_V = BigDecimal.ZERO;
                wkSubAmt_V = BigDecimal.ZERO;
                wkSubCnt_I = BigDecimal.ZERO;
                wkSubAmt_I = BigDecimal.ZERO;
                wkSubCnt_C = BigDecimal.ZERO;
                wkSubAmt_C = BigDecimal.ZERO;
                wkSubCnt_M = BigDecimal.ZERO;
                wkSubAmt_M = BigDecimal.ZERO;
                wkSubCnt = BigDecimal.ZERO;
                wkSubAmt = BigDecimal.ZERO;
                wkSubCfee = BigDecimal.ZERO;
            }
            tmpCnt++;
        }
    }

    private void _1000_DTL() {
        // 022800 1000-DTL-RTN.
        //
        //// 搬相關資料到STAT4-REC
        //
        // 022900     MOVE       SPACES            TO      STAT4-REC.
        // 023000     MOVE       WK-CLLBR          TO      STAT4-CLLBR.

        init_STAT4_REC();
        stat4Cllbr = wkCllbr;

        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定代收行變數值
        //// 若有誤，搬"N"
        //
        // 023100     FIND       DB-BCTL-ACCESS AT  DB-BCTL-BRNO = WK-CLLBR
        // 023200       ON EXCEPTION
        // 023300          MOVE  "N"              TO      WK-CLLBRC.
        // 023400     IF         WK-CLLBRC        =       "Y"
        // 023500       PERFORM  0000-CNV-RTN     THRU    0000-CNV-EXIT
        // 023600     ELSE
        // 023700       MOVE     SPACES           TO      STAT4-CLLBRC.
        //        this.log("sCllbr", sCllbr + "");
        Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(sCllbr);
        //        this.log("bctl", bctl + "");
        if (!Objects.isNull(bctl)) {
            wkCllbr = parse.string2Integer(bctl.getBrno());
            stat4Cllbrc = getPbrName(wkCllbr);
        }

        // 023800     MOVE       WK-SUBCNT-C      TO      STAT4-C-CNT.
        // 023900     MOVE       WK-SUBAMT-C      TO      STAT4-C-AMT.
        // 024000     MOVE       WK-SUBCNT-M      TO      STAT4-M-CNT.
        // 024100     MOVE       WK-SUBAMT-M      TO      STAT4-M-AMT.
        // 024200     MOVE       WK-SUBCNT-V      TO      STAT4-V-CNT.
        // 024300     MOVE       WK-SUBAMT-V      TO      STAT4-V-AMT.
        // 024320     MOVE       WK-SUBCNT-I      TO      STAT4-I-CNT.
        // 024340     MOVE       WK-SUBAMT-I      TO      STAT4-I-AMT.
        // 024400     MOVE       WK-SUBCNT        TO      STAT4-TOTCNT.
        // 024500     MOVE       WK-SUBAMT        TO      STAT4-TOTAMT.
        // 024520     COMPUTE    WK-SUBCNT-AUTO   =  WK-SUBCNT-V  +  WK-SUBCNT-I.
        // 024540     COMPUTE    WK-SUBAMT-AUTO   =  WK-SUBAMT-V  +  WK-SUBAMT-I.
        stat4_C_Cnt = wkSubCnt_C;
        stat4_C_Amt = wkSubAmt_C;
        stat4_M_Cnt = wkSubCnt_M;
        stat4_M_Amt = wkSubAmt_M;
        stat4_V_Cnt = wkSubCnt_V;
        stat4_V_Amt = wkSubAmt_V;
        stat4_I_Cnt = wkSubCnt_I;
        stat4_I_Amt = wkSubAmt_I;
        stat4_TotCnt = wkSubCnt;
        stat4_TotAmt = wkSubAmt;
        wkSubCntAuto = wkSubCnt_V.add(wkSubCnt_I);
        wkSubAmtAuto = wkSubAmt_V.add(wkSubAmt_I);
        // 024600     IF         WK-SUBCNT        =       0
        // 024700       MOVE     0                TO      STAT4-CNT-RATE1
        // 024800     ELSE
        // 024900       COMPUTE STAT4-CNT-RATE1 = WK-SUBCNT-AUTO / WK-SUBCNT * 100.
        // 025000     MOVE       "."              TO      STAT4-CNT-RATE2.
        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat4CntRate1 = BigDecimal.ZERO;
        } else {
            stat4CntRate1 =
                    wkSubCntAuto
                            .divide(wkSubCnt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
        }
        stat4CntRate2 = ".";
        // 025100     IF         WK-SUBCNT        =       0
        // 025200       MOVE     0                TO      STAT4-CNT-RATE3
        // 025300     ELSE
        // 025400       COMPUTE  STAT4-CNT-RATE3  =  ( WK-SUBCNT-AUTO / WK-SUBCNT *
        // 025500                  100 - STAT4-CNT-RATE1 ) * 100.
        // 025600     MOVE       "%"              TO      STAT4-CNT-RATE4.
        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat4CntRate3 = BigDecimal.ZERO;
        } else {

            stat4CntRate3 =
                    wkSubCntAuto
                            .divide(wkSubCnt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
            stat4CntRate3 = stat4CntRate3.subtract(stat4AmtRate1);
            stat4CntRate3 = stat4CntRate3.multiply(new BigDecimal("100"));
        }
        stat4CntRate4 = "%";
        // 025700     IF         WK-SUBAMT        =       0
        // 025800       MOVE     0                TO      STAT4-AMT-RATE1
        // 025900     ELSE
        // 026000       COMPUTE STAT4-AMT-RATE1 = WK-SUBAMT-AUTO / WK-SUBAMT * 100.
        // 026100     MOVE       "."              TO      STAT4-AMT-RATE2.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat4AmtRate1 = BigDecimal.ZERO;
        } else {
            stat4AmtRate1 =
                    wkSubAmtAuto
                            .divide(wkSubAmt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
        }
        stat4AmtRate2 = ".";
        // 026200     IF         WK-SUBAMT        =       0
        // 026300       MOVE     0                TO      STAT4-AMT-RATE3
        // 026400     ELSE
        // 026500       COMPUTE  STAT4-AMT-RATE3  =  ( WK-SUBAMT-AUTO / WK-SUBAMT *
        // 026600                  100 - STAT4-AMT-RATE1 ) * 100.
        // 026700     MOVE       "%"              TO      STAT4-AMT-RATE4.
        // 026800     MOVE       WK-SUBCFEE       TO      STAT4-CFEE.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat4AmtRate3 = BigDecimal.ZERO;
        } else {

            stat4AmtRate3 =
                    wkSubAmtAuto
                            .divide(wkSubAmt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
            stat4AmtRate3 = stat4AmtRate3.subtract(stat4AmtRate1);
            stat4AmtRate3 = stat4AmtRate3.multiply(new BigDecimal("100"));
        }
        stat4AmtRate4 = "%";
        stat4Cfee = wkSubCfee;
        //// 寫檔FD-STAT4-REC(明細)
        //
        // 026900     WRITE      STAT4-REC.
        // 027000     MOVE       S-CLLBR          TO      WK-CLLBR.
        // 027100     MOVE       "Y"         TO    WK-CLLBRC.
        // 027200     MOVE       0           TO    WK-SUBCNT-V,WK-SUBAMT-V,
        // 027250                                  WK-SUBCNT-I,WK-SUBAMT-I.
        // 027300     MOVE       0           TO    WK-SUBCNT-C,WK-SUBAMT-C.
        // 027400     MOVE       0           TO    WK-SUBCNT-M,WK-SUBAMT-M.
        // 027500     MOVE       0           TO    WK-SUBCNT,WK-SUBAMT,WK-SUBCFEE.
        // 027600 1000-DTL-EXIT.
        // 因為會有網銀語音繳款帳號前三碼(分行代號) 跟 代收行 會不同，要另外暫存後再做排序

    }

    private void addList() {
        mapResult = new HashMap<String, String>();
        mapResult.put("stat4Cllbr", stat4Cllbr + "");
        mapResult.put("stat4Cllbrc", stat4Cllbrc + "");
        mapResult.put("stat4_C_Cnt", stat4_C_Cnt + "");
        mapResult.put("stat4_C_Amt", stat4_C_Amt + "");
        mapResult.put("stat4_M_Cnt", stat4_M_Cnt + "");
        mapResult.put("stat4_M_Amt", stat4_M_Amt + "");
        mapResult.put("stat4_V_Cnt", stat4_V_Cnt + "");
        mapResult.put("stat4_V_Amt", stat4_V_Amt + "");
        mapResult.put("stat4_I_Cnt", stat4_I_Cnt + "");
        mapResult.put("stat4_I_Amt", stat4_I_Amt + "");
        mapResult.put("stat4_TotCnt", stat4_TotCnt + "");
        mapResult.put("stat4_TotAmt", stat4_TotAmt + "");
        mapResult.put("stat4CntRate1", stat4CntRate1.intValue() + "");
        mapResult.put("stat4CntRate2", stat4CntRate2 + "");
        mapResult.put("stat4CntRate3", stat4CntRate3.intValue() + "");
        mapResult.put("stat4CntRate4", stat4CntRate4 + "");
        mapResult.put("stat4AmtRate1", stat4AmtRate1.intValue() + "");
        mapResult.put("stat4AmtRate2", stat4AmtRate2 + "");
        mapResult.put("stat4AmtRate3", stat4AmtRate3.intValue() + "");
        mapResult.put("stat4AmtRate4", stat4AmtRate4 + "");
        mapResult.put("stat4Cfee", stat4Cfee + "");
        listResult.add(mapResult);
    }

    private void _2000_TOT() {

        // 027900 2000-TOT-RTN.
        //
        //// 搬相關資料到STAT4-REC
        //
        // 028000     MOVE       SPACES           TO      STAT4-REC.
        // 028100     MOVE       WK-TOTCNT-C      TO      STAT4-C-CNT.
        // 028200     MOVE       WK-TOTAMT-C      TO      STAT4-C-AMT.
        // 028300     MOVE       WK-TOTCNT-M      TO      STAT4-M-CNT.
        // 028400     MOVE       WK-TOTAMT-M      TO      STAT4-M-AMT.
        // 028500     MOVE       WK-TOTCNT-V      TO      STAT4-V-CNT.
        // 028600     MOVE       WK-TOTAMT-V      TO      STAT4-V-AMT.
        // 028620     MOVE       WK-TOTCNT-I      TO      STAT4-I-CNT.
        // 028640     MOVE       WK-TOTAMT-I      TO      STAT4-I-AMT.
        // 028700     MOVE       WK-TOTCNT        TO      STAT4-TOTCNT.
        // 028800     MOVE       WK-TOTAMT        TO      STAT4-TOTAMT.
        // 028820     COMPUTE    WK-TOTCNT-AUTO   =  WK-TOTCNT-V + WK-TOTCNT-I.
        // 028840     COMPUTE    WK-TOTAMT-AUTO   =  WK-TOTAMT-V + WK-TOTAMT-I.
        //        init_STAT4_REC();
        stat4_C_Cnt = wkTotCnt_C;
        stat4_C_Amt = wkTotAmt_C;
        stat4_M_Cnt = wkTotCnt_M;
        stat4_M_Amt = wkTotAmt_M;
        stat4_V_Cnt = wkTotCnt_V;
        stat4_V_Amt = wkTotAmt_V;
        stat4_I_Cnt = wkTotCnt_I;
        stat4_I_Amt = wkTotAmt_I;
        stat4_TotCnt = wkTotCnt;
        stat4_TotAmt = wkTotAmt;
        wkTotCntAuto = wkTotCnt_V.add(wkTotCnt_I);
        wkTotAmtAuto = wkTotAmt_V.add(wkTotAmt_I);
        // 028900     IF         WK-TOTCNT        =       0
        // 029000       MOVE     0                TO      STAT4-CNT-RATE1
        // 029100     ELSE
        // 029200       COMPUTE STAT4-CNT-RATE1 = WK-TOTCNT-AUTO / WK-TOTCNT * 100.
        // 029300     MOVE       "."              TO      STAT4-CNT-RATE2.
        if (wkTotCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat4CntRate1 = BigDecimal.ZERO;
        } else {
            stat4CntRate1 =
                    wkTotCntAuto
                            .divide(wkTotCnt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
        }
        stat4CntRate2 = ".";
        // 029400     IF         WK-TOTCNT        =       0
        // 029500       MOVE     0                TO      STAT4-CNT-RATE3
        // 029600     ELSE
        // 029700       COMPUTE  STAT4-CNT-RATE3  =  ( WK-TOTCNT-AUTO / WK-TOTCNT *
        // 029800                  100 - STAT4-CNT-RATE1 ) * 100.
        // 029900     MOVE       "%"              TO      STAT4-CNT-RATE4.
        if (wkTotCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat4CntRate3 = BigDecimal.ZERO;
        } else {

            stat4CntRate3 =
                    wkTotAmtAuto
                            .divide(wkTotAmt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
            stat4CntRate3 = stat4CntRate3.subtract(stat4AmtRate1);
            stat4CntRate3 = stat4CntRate3.multiply(new BigDecimal("100"));
        }
        stat4CntRate4 = "%";
        // 030000     IF         WK-TOTAMT        =       0
        // 030100       MOVE     0                TO      STAT4-AMT-RATE1
        // 030200     ELSE
        // 030300       COMPUTE STAT4-AMT-RATE1 = WK-TOTAMT-AUTO / WK-TOTAMT * 100.
        // 030400     MOVE       "."              TO      STAT4-AMT-RATE2.
        if (wkTotCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat4AmtRate1 = BigDecimal.ZERO;
        } else {
            stat4AmtRate1 =
                    wkTotAmtAuto
                            .divide(wkTotAmt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
        }
        stat4AmtRate2 = ".";
        // 030500     IF         WK-TOTAMT        =       0
        // 030600       MOVE     0                TO      STAT4-AMT-RATE3
        // 030700     ELSE
        // 030800       COMPUTE  STAT4-AMT-RATE3  =  ( WK-TOTAMT-AUTO / WK-TOTAMT *
        // 030900                  100 - STAT4-AMT-RATE1 ) * 100.
        // 031000     MOVE       "%"              TO      STAT4-AMT-RATE4.
        if (wkTotAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat4AmtRate3 = BigDecimal.ZERO;
        } else {
            stat4AmtRate3 =
                    wkTotAmtAuto
                            .divide(wkTotAmt, 2, RoundingMode.DOWN)
                            .multiply(new BigDecimal("100"));
            stat4AmtRate3 = stat4AmtRate3.subtract(stat4AmtRate1);
            stat4AmtRate3 = stat4AmtRate3.multiply(new BigDecimal("100"));
        }
        stat4AmtRate4 = "%";
        // 031100     MOVE       WK-TOTCFEE       TO      STAT4-CFEE.
        // 031200     MOVE       "   合　計   "   TO      STAT4-CLLBRC.
        stat4Cfee = wkTotCfee;
        stat4Cllbrc = "   合　計   ";
        //// 寫檔FD-STAT4(合計)
        //
        // 031300     WRITE      STAT4-REC.
        // 031400 2000-TOT-EXIT.
        //        addList();
    }

    private void init_STAT4_REC() {
        // 002400  01  STAT4-REC.
        // 002500      03  STAT4-CLLBR                      PIC 9(03).
        // 002600      03  STAT4-CLLBRC                     PIC X(20).
        // 002700      03  STAT4-C-CNT                      PIC 9(06).
        // 002800      03  STAT4-C-AMT                      PIC 9(12).
        // 002900      03  STAT4-M-CNT                      PIC 9(06).
        // 003000      03  STAT4-M-AMT                      PIC 9(12).
        // 003100      03  STAT4-V-CNT                      PIC 9(06).
        // 003200      03  STAT4-V-AMT                      PIC 9(12).
        // 003220      03  STAT4-I-CNT                      PIC 9(06).
        // 003240      03  STAT4-I-AMT                      PIC 9(12).
        // 003300      03  STAT4-TOTCNT                     PIC 9(06).
        // 003400      03  STAT4-TOTAMT                     PIC 9(12).
        // 003500      03  STAT4-CNT-RATE1                  PIC 9(03).
        // 003600      03  STAT4-CNT-RATE2                  PIC X(01).
        // 003700      03  STAT4-CNT-RATE3                  PIC 9(02).
        // 003800      03  STAT4-CNT-RATE4                  PIC X(01).
        // 003900      03  STAT4-AMT-RATE1                  PIC 9(03).
        // 004000      03  STAT4-AMT-RATE2                  PIC X(01).
        // 004100      03  STAT4-AMT-RATE3                  PIC 9(02).
        // 004200      03  STAT4-AMT-RATE4                  PIC X(01).
        // 004300      03  STAT4-CFEE                       PIC 9(08).
        // 004400      03  FILLER                           PIC X(15).

        stat4Cllbr = 0;
        stat4Cllbrc = "";
        stat4_C_Cnt = BigDecimal.ZERO;
        stat4_C_Amt = BigDecimal.ZERO;
        stat4_M_Cnt = BigDecimal.ZERO;
        stat4_M_Amt = BigDecimal.ZERO;
        stat4_V_Cnt = BigDecimal.ZERO;
        stat4_V_Amt = BigDecimal.ZERO;
        stat4_I_Cnt = BigDecimal.ZERO;
        stat4_I_Amt = BigDecimal.ZERO;
        stat4_TotCnt = BigDecimal.ZERO;
        stat4_TotAmt = BigDecimal.ZERO;
        stat4CntRate1 = BigDecimal.ZERO;
        stat4CntRate2 = "";
        stat4CntRate3 = BigDecimal.ZERO;
        stat4CntRate4 = "";
        stat4AmtRate1 = BigDecimal.ZERO;
        stat4AmtRate2 = "";
        stat4AmtRate3 = BigDecimal.ZERO;
        stat4AmtRate4 = "";
        stat4Cfee = BigDecimal.ZERO;
    }

    private void write_STAT4_REC(Map<String, String> r) {
        // 002400  01  STAT4-REC.
        // 002500      03  STAT4-CLLBR                      PIC 9(03).
        // 002600      03  STAT4-CLLBRC                     PIC X(20).
        // 002700      03  STAT4-C-CNT                      PIC 9(06).
        // 002800      03  STAT4-C-AMT                      PIC 9(12).
        // 002900      03  STAT4-M-CNT                      PIC 9(06).
        // 003000      03  STAT4-M-AMT                      PIC 9(12).
        // 003100      03  STAT4-V-CNT                      PIC 9(06).
        // 003200      03  STAT4-V-AMT                      PIC 9(12).
        // 003220      03  STAT4-I-CNT                      PIC 9(06).
        // 003240      03  STAT4-I-AMT                      PIC 9(12).
        // 003300      03  STAT4-TOTCNT                     PIC 9(06).
        // 003400      03  STAT4-TOTAMT                     PIC 9(12).
        // 003500      03  STAT4-CNT-RATE1                  PIC 9(03).
        // 003600      03  STAT4-CNT-RATE2                  PIC X(01).
        // 003700      03  STAT4-CNT-RATE3                  PIC 9(02).
        // 003800      03  STAT4-CNT-RATE4                  PIC X(01).
        // 003900      03  STAT4-AMT-RATE1                  PIC 9(03).
        // 004000      03  STAT4-AMT-RATE2                  PIC X(01).
        // 004100      03  STAT4-AMT-RATE3                  PIC 9(02).
        // 004200      03  STAT4-AMT-RATE4                  PIC X(01).
        // 004300      03  STAT4-CFEE                       PIC 9(08).
        // 004400      03  FILLER                           PIC X(15).

        this.log("stat4Cllbr", stat4Cllbr + "");
        this.log("stat4Cllbrc", stat4Cllbrc + "");
        this.log("stat4_C_Cnt", stat4_C_Cnt + "");
        this.log("stat4_C_Amt", stat4_C_Amt + "");
        this.log("stat4_M_Cnt", stat4_M_Cnt + "");
        this.log("stat4_M_Amt", stat4_M_Amt + "");
        this.log("stat4_V_Cnt", stat4_V_Cnt + "");
        this.log("stat4_V_Amt", stat4_V_Amt + "");
        this.log("stat4_I_Cnt", stat4_I_Cnt + "");
        this.log("stat4_I_Amt", stat4_I_Amt + "");
        this.log("stat4_TotCnt", stat4_TotCnt + "");
        this.log("stat4_TotAmt", stat4_TotAmt + "");
        this.log("stat4CntRate1", stat4CntRate1 + "");
        this.log("stat4CntRate2", stat4CntRate2 + "");
        this.log("stat4CntRate3", stat4CntRate3 + "");
        this.log("stat4CntRate4", stat4CntRate4 + "");
        this.log("stat4AmtRate1", stat4AmtRate1 + "");
        this.log("stat4AmtRate2", stat4AmtRate2 + "");
        this.log("stat4AmtRate3", stat4AmtRate3 + "");
        this.log("stat4AmtRate4", stat4AmtRate4 + "");
        this.log("stat4Cfee", stat4Cfee + "");
        sb = new StringBuilder();
        if (cnt == listResult.size()) {
            sb.append("   ");
            sb.append(formatUtil.padX("   合　計   ", 20));
        } else {
            sb.append(formatUtil.pad9(r.get("stat4Cllbr") + "", 3));
            sb.append(
                    formatUtil.padX(
                            getPbrName(parse.string2Integer(r.get("stat4Cllbr").toString())), 20));
        }

        sb.append(formatUtil.pad9(r.get("stat4_C_Cnt") + "", 6));
        sb.append(formatUtil.pad9(r.get("stat4_C_Amt") + "", 12));
        sb.append(formatUtil.pad9(r.get("stat4_M_Cnt") + "", 6));
        sb.append(formatUtil.pad9(r.get("stat4_M_Amt") + "", 12));
        sb.append(formatUtil.pad9(r.get("stat4_V_Cnt") + "", 6));
        sb.append(formatUtil.pad9(r.get("stat4_V_Amt") + "", 12));
        sb.append(formatUtil.pad9(r.get("stat4_I_Cnt") + "", 6));
        sb.append(formatUtil.pad9(r.get("stat4_I_Amt") + "", 12));
        sb.append(formatUtil.pad9(r.get("stat4_TotCnt") + "", 6));
        sb.append(formatUtil.pad9(r.get("stat4_TotAmt") + "", 12));
        sb.append(formatUtil.pad9(r.get("stat4CntRate1") + "", 3));
        sb.append(formatUtil.padX(r.get("stat4CntRate2") + "", 1));
        sb.append(formatUtil.pad9(r.get("stat4CntRate3") + "", 2));
        sb.append(formatUtil.padX(r.get("stat4CntRate4") + "", 1));
        sb.append(formatUtil.pad9(r.get("stat4AmtRate1") + "", 3));
        sb.append(formatUtil.padX(r.get("stat4AmtRate2") + "", 1));
        sb.append(formatUtil.pad9(r.get("stat4AmtRate3") + "", 2));
        sb.append(formatUtil.padX(r.get("stat4AmtRate4") + "", 1));
        sb.append(formatUtil.pad9(r.get("stat4Cfee") + "", 8));
        sb.append(formatUtil.padX("", 15));
        fileContentsSTAT4.add(sb.toString());
    }

    private void writeFile() {

        textFileSTAT4.deleteFile(fileNameSTAT4);

        try {
            textFileSTAT4.writeFileContent(fileNameSTAT4, fileContentsSTAT4, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private boolean isNotALlNumbers(String text) {
        // 字串中 是否皆為數字
        if (text.matches("[+-]?\\d*(\\.\\d+)?")) {
            return true;
        } else {
            return false;
        }
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

    private void log(String col, String text) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), col + "=" + text);
    }
}
