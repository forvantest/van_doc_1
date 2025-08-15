/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT5;
import com.bot.ncl.dto.entities.CldtlCldtlIdx2Bus;
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
@Component("STAT5Lsnr")
@Scope("prototype")
public class STAT5Lsnr extends BatchListenerCase<STAT5> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT5;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private ClmrService clmrService;
    List<CldtlCldtlIdx2Bus> lCldtl = new ArrayList<CldtlCldtlIdx2Bus>();
    List<CldtlCldtlIdx2Bus> tmplCldtl = new ArrayList<CldtlCldtlIdx2Bus>();
    List<Map<String, String>> listResult = new ArrayList<Map<String, String>>();
    Map<String, String> mapResult = new HashMap<String, String>();

    private Map<String, String> textMap;
    private int cnt = 0;
    // 批次日期
    private String wkTaskYYYMM;
    int wkSDate = 0;
    int wkEDate = 0;
    private STAT5 event;

    private static final String CHARSET = "Big5";
    private String fileNameSTAT5 = "STAT5";
    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT5;

    private int tmpCnt = 0;
    // S
    int sCllbr = 0;
    String sTxtype = "";
    BigDecimal sAmt = BigDecimal.ZERO;
    BigDecimal sCfee = BigDecimal.ZERO;

    // wk
    int wkCllbr = 0;
    String wkCllbrC = "";
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

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT5 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT5Lsnr");
        this.event = event;
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT5 event) {

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT5Lsnr run()");

        fileNameSTAT5 = fileDir + fileNameSTAT5;
        fileContentsSTAT5 = new ArrayList<String>();

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
        _0000_MAIN();
        writeFile();
    }

    private void _0000_MAIN() {

        _SORTIN();
    }

    private void _SORTIN() {
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
        // 013800* 代收類別 ONLY FOR  健保臨櫃
        // 013900     IF        ( DB-CLDTL-CODE    NOT  =    "366009")
        // 014000           AND ( DB-CLDTL-CODE    NOT  =    "115265")
        // 014100                 GO  TO   CS-SORTIN-RTN.
        //// 搬相關欄位至SORT-REC
        //
        // 015900     MOVE        DB-CLDTL-CLLBR    TO       S-CLLBR.
        // 016000     MOVE        DB-CLDTL-TXTYPE   TO       S-TXTYPE.
        // 016100     MOVE        DB-CLDTL-AMT      TO       S-AMT.
        // 016700     RELEASE     SORT-REC.
        // 016800     GO TO CS-SORTIN-RTN.
        // 016900 CS-SORTIN-EXIT.
        cnt = 0;
        this.log("lCldtl.size", lCldtl.size() + "");
        for (CldtlCldtlIdx2Bus r : lCldtl) {
            cnt++;

            String dbCldtlCode = r.getCode().isEmpty() ? "" : r.getCode().trim();
            sCllbr = r.getCllbr();
            sTxtype = r.getTxtype().isEmpty() ? "" : r.getTxtype().trim();

            sAmt =
                    r.getAmt().compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : new BigDecimal(r.getAmt().toString());

            if (!"115265".equals(dbCldtlCode)) {
                if (!"366009".equals(dbCldtlCode)) {
                    continue;
                }
            }

            if (!"C".equals(sTxtype)) {
                if (!"M".equals(sTxtype)) {
                    continue;
                }
            }
            _SORTIN_RTN();
        }
        // 最後一組
        _1000_DTL();
        write_STAT4_REC();
        cnt++;
        // 總計
        _2000_TOT();
        write_STAT4_REC();
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
        //
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

        // 以上 這段省略 for迴圈 即可處理

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

        if (!(sCllbr + "").equals(wkCllbr + "")) {
            wkCllbr = sCllbr;
            _1000_DTL();
            if (tmpCnt > 0) {

                //// 寫檔FD-STAT4
                //
                // 026900     WRITE      STAT4-REC.

                // 027000     MOVE       S-CLLBR          TO      WK-CLLBR.
                // 027100     MOVE       "Y"         TO    WK-CLLBRC.
                write_STAT4_REC();
            }
            tmpCnt++;
        }

        //// IF S-TXTYPE  =  "C"  OR "M",累加筆數金額
        // 019400     IF         S-TXTYPE          =       "C"  OR "M"
        // 019500       ADD      1                 TO      WK-SUBCNT-C
        // 019600       ADD      1                 TO      WK-TOTCNT-C
        // 019700       ADD      1                 TO      WK-SUBCNT
        // 019800       ADD      1                 TO      WK-TOTCNT
        // 019900       ADD      S-AMT             TO      WK-SUBAMT
        // 020000       ADD      S-AMT             TO      WK-TOTAMT
        // 020100       ADD      S-AMT             TO      WK-SUBAMT-C
        // 020200       ADD      S-AMT             TO      WK-TOTAMT-C
        // 020300       GO  TO   0000-LOOP.
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
            wkSubCnt_C = wkSubCnt_C.add(BigDecimal.ONE);
            wkTotCnt_C = wkTotCnt_C.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmt_C = wkSubAmt_C.add(sAmt);
            wkTotAmt_C = wkTotAmt_C.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);
        }
    }

    private void _1000_DTL() {
        // 022800 1000-DTL-RTN.
        //
        //// 搬相關資料到STAT4-REC(明細)
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
            stat4CntRate3 = stat4CntRate3.subtract(stat4CntRate1);
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
        // 026800     MOVE       WK-SUBCFEE       TO      STAT4-CFEE.
        stat4Cfee = wkSubCfee;
    }

    private void _2000_TOT() {

        //// 搬關資料到STAT4-REC(合計)
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
        init_STAT4_REC();
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

        //// 寫檔FD-STAT4
        //
        // 031300     WRITE      STAT4-REC.

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

    private void write_STAT4_REC() {
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
        //        this.log("stat4Cllbrc", stat4Cllbrc + "");
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

        if (cnt == (lCldtl.size() + 1)) {

            sb.append(formatUtil.padX("", 3));
            sb.append(formatUtil.padX("   合　計   ", 20));

        } else {
            sb.append(formatUtil.pad9(stat4Cllbr + "", 3));
            sb.append(formatUtil.padX(getPbrName(parse.string2Integer(stat4Cllbr + "")), 20));
        }

        sb.append(formatUtil.pad9(stat4_C_Cnt + "", 6));
        sb.append(formatUtil.pad9(stat4_C_Amt + "", 12));
        sb.append(formatUtil.pad9(stat4_M_Cnt + "", 6));
        sb.append(formatUtil.pad9(stat4_M_Amt + "", 12));
        sb.append(formatUtil.pad9(stat4_V_Cnt + "", 6));
        sb.append(formatUtil.pad9(stat4_V_Amt + "", 12));
        sb.append(formatUtil.pad9(stat4_I_Cnt + "", 6));
        sb.append(formatUtil.pad9(stat4_I_Amt + "", 12));
        sb.append(formatUtil.pad9(stat4_TotCnt + "", 6));
        sb.append(formatUtil.pad9(stat4_TotAmt + "", 12));
        sb.append(formatUtil.pad9(stat4CntRate1 + "", 3));
        sb.append(formatUtil.padX(stat4CntRate2 + "", 1));
        sb.append(formatUtil.pad9(stat4CntRate3 + "", 2));
        sb.append(formatUtil.padX(stat4CntRate4 + "", 1));
        sb.append(formatUtil.pad9(stat4AmtRate1 + "", 3));
        sb.append(formatUtil.padX(stat4AmtRate2 + "", 1));
        sb.append(formatUtil.pad9(stat4AmtRate3 + "", 2));
        sb.append(formatUtil.padX(stat4AmtRate4 + "", 1));
        sb.append(formatUtil.pad9(stat4Cfee + "", 8));
        sb.append(formatUtil.padX("", 15));
        fileContentsSTAT5.add(sb.toString());

        //
        //// 清變數
        //
        // 027200     MOVE       0           TO    WK-SUBCNT-V,WK-SUBAMT-V,
        // 027250                                  WK-SUBCNT-I,WK-SUBAMT-I.
        // 027300     MOVE       0           TO    WK-SUBCNT-C,WK-SUBAMT-C.
        // 027400     MOVE       0           TO    WK-SUBCNT-M,WK-SUBAMT-M.
        // 027500     MOVE       0           TO    WK-SUBCNT,WK-SUBAMT,WK-SUBCFEE.
        // 027600 1000-DTL-EXIT.

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

    private void writeFile() {

        textFileSTAT5.deleteFile(fileNameSTAT5);

        try {
            textFileSTAT5.writeFileContent(fileNameSTAT5, fileContentsSTAT5, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private void log(String col, String text) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), col + "=" + text);
    }
}
