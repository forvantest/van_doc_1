/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT2;
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
@Component("STAT2Lsnr")
@Scope("prototype")
public class STAT2Lsnr extends BatchListenerCase<STAT2> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT2;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private ClmrService clmrService;

    private Map<String, String> textMap;
    // 批次日期
    private String wkTaskYYYMM;
    private STAT2 event;
    List<CldtlCldtlIdx2Bus> lCldtl = new ArrayList<CldtlCldtlIdx2Bus>();

    private static final String CHARSET = "Big5";
    private String fileNameSTAT2 = "STAT2";

    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT2;

    /*STAT2 參數*/
    String STAT2Code = "";

    /*DBS 參數*/

    String dbsCode = "";

    String dbsTxtype = "";

    private int cnt = 0;
    /*WORKING STORAGE 參數*/

    int wkCllbr = 0;
    String wkCllbrC = "";
    int wkSDate = 0;
    int wkEDate = 0;
    BigDecimal wkTotCfee = BigDecimal.ZERO;
    BigDecimal wkTotCnt_C = BigDecimal.ZERO;
    BigDecimal wkTotAmt_C = BigDecimal.ZERO;
    BigDecimal wkTotCnt_M = BigDecimal.ZERO;
    BigDecimal wkTotAmt_M = BigDecimal.ZERO;
    BigDecimal wkTotCntAuto = BigDecimal.ZERO;
    BigDecimal wkTotAmtAuto = BigDecimal.ZERO;
    BigDecimal wkTotCnt = BigDecimal.ZERO;
    BigDecimal wkTotAmt = BigDecimal.ZERO;

    int wkBrno = 0;
    String wkActno = "";
    BigDecimal wkSubCntAuto = BigDecimal.ZERO;
    BigDecimal wkSubAmtAuto = BigDecimal.ZERO;
    BigDecimal wkSubCnt_C = BigDecimal.ZERO;
    BigDecimal wkSubAmt_C = BigDecimal.ZERO;
    BigDecimal wkSubCnt_M = BigDecimal.ZERO;
    BigDecimal wkSubAmt_M = BigDecimal.ZERO;
    BigDecimal wkSubCnt = BigDecimal.ZERO;
    BigDecimal wkSubAmt = BigDecimal.ZERO;
    BigDecimal wkSubCfee = BigDecimal.ZERO;

    /** STAT2_REC *************************** */
    int stat2Cllbr = 0;

    String stat2Cllbrc = "";
    BigDecimal stat2_C_Cnt = BigDecimal.ZERO;
    BigDecimal stat2_C_Amt = BigDecimal.ZERO;
    BigDecimal stat2_M_Cnt = BigDecimal.ZERO;
    BigDecimal stat2_M_Amt = BigDecimal.ZERO;
    BigDecimal stat2_Auto_Cnt = BigDecimal.ZERO;
    BigDecimal stat2_Auto_Amt = BigDecimal.ZERO;
    BigDecimal stat2_TotCnt = BigDecimal.ZERO;
    BigDecimal stat2_TotAmt = BigDecimal.ZERO;
    BigDecimal stat2CntRate1 = BigDecimal.ZERO;
    String stat2CntRate2 = "";
    BigDecimal stat2CntRate3 = BigDecimal.ZERO;
    String stat2CntRate4 = "";
    BigDecimal stat2AmtRate1 = BigDecimal.ZERO;
    String stat2AmtRate2 = "";
    BigDecimal stat2AmtRate3 = BigDecimal.ZERO;
    String stat2AmtRate4 = "";
    String filler = "";
    BigDecimal stat2Cfee = BigDecimal.ZERO;

    int sCllbr = 0;
    String sTxtype = "";
    BigDecimal sAmt = BigDecimal.ZERO;
    BigDecimal sCfee = BigDecimal.ZERO;

    // 功能：夜間批次 –產生代收分析月報－以分行排序的檔案
    // 讀DB-CLDTL-DDS收付明細檔，挑本月代收資料，寫檔(FD-STAT2)
    // 寫FD-STAT2("DATA/CL/BH/ANALY/STAT2")

    // 001200 FILE-CONTROL.
    // 001300     SELECT  FD-STAT2  ASSIGN  TO  DISK.
    // 001400     SELECT  SORTFL    ASSIGN  TO  SORT DISK. (可以不用產?)

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT2 event) {
        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT2Lsnr run()");

        fileNameSTAT2 = fileDir + fileNameSTAT2;
        fileContentsSTAT2 = new ArrayList<String>();

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

        // 1071101/100 = 10711
        int thisMonth = parse.string2Integer(wkTaskYYYMM) / 100;

        wkSDate = thisMonth * 100 + 1;
        wkEDate = thisMonth * 100 + 31;

        // DB-CLDTL-IDX2                     SET OF DB-CLDTL-DDS                   01577100
        //        KEY IS ( DB-CLDTL-DATE, DB-CLDTL-CLLBR,                         01577200
        //                 DB-CLDTL-CODE )                  DUPLICATES LAST;      01577300

        // 清變數
        initWK();
        // 執行主程式
        _0000_MAIN();
        // 寫入檔案
        writeFile();
    }

    private void _0000_MAIN() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "_0000_MAIN....");
        // 查尋資料(排序)
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkSDate.... = " + wkSDate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEDate.... = " + wkEDate);
        lCldtl = cldtlService.findCldtlIdx2(wkSDate, wkEDate, 0, 0, Integer.MAX_VALUE);
        if (Objects.isNull(lCldtl)) {
            return;
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lCldtl.size...=" + lCldtl.size());
        // tmpSort
        // 003100 01   SORT-REC.
        // 003200  03  S-CLLBR                   PIC 9(03).
        // 003300  03  S-TXTYPE                  PIC X(01).
        // 003400  03  S-AMT                     PIC 9(10).
        // 003450  03  S-CFEE                    PIC 9(03)V9.
        int dbCldtlCodeInt = 0;
        for (CldtlCldtlIdx2Bus r : lCldtl) {
            String dbCldtlCode = r.getCode().isEmpty() ? "" : r.getCode();
            String dbCldtlTxType = r.getTxtype().isEmpty() ? "" : r.getTxtype();
            String dbCldtlActno =
                    r.getActno() == null || r.getActno().isEmpty() ? "0" : r.getActno();
            int dbCldtlCllbr = r.getCllbr();
            int dbCldtlPbrno = r.getPbrno();
            BigDecimal dbsCldtlAmt =
                    r.getAmt().compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : new BigDecimal(r.getAmt().toString());
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlCode=" + dbCldtlCode);
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "dbCldtlTxType=" + dbCldtlTxType);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlActno=" + dbCldtlActno);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlCllbr=" + dbCldtlCllbr);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbCldtlPbrno=" + dbCldtlPbrno);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbsCldtlAmt=" + dbsCldtlAmt);
            // 009500 CS-SORTIN-RTN.
            //
            //// 依 代收日(大於等於本月1日,小於等於本月31日) FIND NEXT 收付明細檔
            //// 正常，則執行下一步驟
            //// 否則跳到CS-SORTIN-EXIT
            //
            // 009600     FIND NEXT  DB-CLDTL-IDX2 AT DB-CLDTL-DATE NOT <  WK-SDATE
            // 009700                             AND DB-CLDTL-DATE NOT >  WK-EDATE
            // 009800       ON EXCEPTION
            // 009900       GO TO     CS-SORTIN-EXIT.
            // 009910* 代收類別為１２１ＸＸＸ時為代付性質交易，不列入代收分析
            // 009920     IF          DB-CLDTL-CODE    NOT  <    "121000"
            // 009930         AND     DB-CLDTL-CODE    NOT  >    "121999"
            // 009940                 GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                dbCldtlCodeInt = parse.string2Integer(dbCldtlCode);
                if (dbCldtlCodeInt >= 121000 && dbCldtlCodeInt <= 121999) {
                    continue;
                }
            }
            // 009950* 當資料為代繳申請或網路銀行申購基金時不列入分析資料
            // 010000     IF          DB-CLDTL-TXTYPE    =       "U"
            // 010100        GO  TO   CS-SORTIN-RTN.
            if ("U".equals(dbCldtlTxType)) {
                continue;
            }
            // 010105     IF          DB-CLDTL-CODE    NOT  <     "330001"
            // 010106         AND     DB-CLDTL-CODE    NOT  >     "330100"
            // 010110        GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                if (dbCldtlCodeInt >= 330001 && dbCldtlCodeInt <= 330100) {
                    continue;
                }
            }
            // 010112* 代收類別為６ＸＸＸＸＸ時為全國性繳費補入交易，不列入代收分析
            // 010114     IF          DB-CLDTL-CODE    NOT  <    "600000"
            // 010116         AND     DB-CLDTL-CODE    NOT  >    "699999"
            // 010118                 GO  TO   CS-SORTIN-RTN.
            if (isNotALlNumbers(dbCldtlCode)) {
                if (dbCldtlCodeInt >= 600000 && dbCldtlCodeInt <= 699999) {
                    continue;
                }
            }
            // 010120* 開始列入分析
            // 010122     IF        ( DB-CLDTL-TXTYPE    =  "I"  OR = "V"  OR = "W" )
            // 010130          AND  ( DB-CLDTL-ACTNO NOT =  SPACES )
            // 010140        MOVE     DB-CLDTL-ACTNO    TO       WK-ACTNO
            // 010150        IF       WK-BRNO        NOT =       0
            // 010160           MOVE  WK-BRNO           TO       S-CLLBR
            // 010165        ELSE
            // 010166* 當ＷＫ－ＢＲＮＯ為０時代表網路ＡＴＭ他行卡交易，代收行為主辦行
            // 010170           MOVE  DB-CLMR-PBRNO     TO       S-CLLBR
            // 010180     ELSE
            // 010200        MOVE     DB-CLDTL-CLLBR    TO       S-CLLBR.
            if (("I".equals(dbCldtlTxType)
                            || "V".equals(dbCldtlTxType)
                            || "W".equals(dbCldtlTxType))
                    && (!dbCldtlActno.isEmpty())) {
                wkActno = dbCldtlActno;
                if (wkBrno != 0) {
                    sCllbr = wkBrno;
                } else {
                    sCllbr = dbCldtlPbrno;
                }
            } else {
                sCllbr = dbCldtlCllbr;
            }
            wkCllbr = sCllbr;
            // ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sCllbr...=" +
            // sCllbr);
            // 010300     MOVE        DB-CLDTL-TXTYPE   TO       S-TXTYPE.
            // 010400     MOVE        DB-CLDTL-AMT      TO       S-AMT.
            sTxtype = dbCldtlTxType;
            sAmt = dbsCldtlAmt;
            // 010420     IF          DB-CLDTL-TXTYPE   = "A"  OR = "R"  OR = "E"
            // 010440            OR = "V"  OR = "W"  OR = "I"  OR = "B"  OR = "X"
            // 010450       PERFORM   0000-CLMR-RTN     THRU     0000-CLMR-EXIT
            // 010460     ELSE
            // 010480       MOVE      0                 TO       S-CFEE.
            if (("A".equals(dbCldtlTxType)
                    || "R".equals(dbCldtlTxType)
                    || "E".equals(dbCldtlTxType)
                    || "V".equals(dbCldtlTxType)
                    || "W".equals(dbCldtlTxType)
                    || "I".equals(dbCldtlTxType)
                    || "B".equals(dbCldtlTxType)
                    || "X".equals(dbCldtlTxType))) {
                // 因CLDTL 有轉入Cfee2 直接取值
                //                sCfee = r.getCfee2();
            } else {
                sCfee = BigDecimal.ZERO;
            }
            // 010482* 若為跨行ＥＤＩ時，此筆列入主辦行
            // 010484     IF          DB-CLDTL-TXTYPE   =        "E"
            // 010486         AND     DB-CLDTL-CLLBR    =        000
            // 010488         MOVE    DB-CLMR-PBRNO     TO       S-CLLBR.
            if ("E".equals(dbCldtlTxType) && dbCldtlCllbr == 0) {
                sCllbr = dbCldtlCllbr;
            }
            // 010500     RELEASE     SORT-REC.
            // 010600     GO TO CS-SORTIN-RTN.
            // 010700 CS-SORTIN-EXIT.
            _SORTOUT();
        }
        if (wkCllbr != 0) {
            _1000_DTL();
            _2000_TOT();
        }
    }

    private void STAT2Form(
            String STAT2CodeName,
            int STAT2Pbrno,
            String STAT2PbrName,
            String STAT2Txtype,
            int STAT2TotCnt,
            BigDecimal STAT2TotAmt) {
        // 列印到STAT2檔案
        sb = new StringBuilder();

        sb.append(formatUtil.padX(STAT2Code, 6));
        sb.append(formatUtil.padX(STAT2CodeName, 40));
        sb.append(formatUtil.pad9(STAT2Pbrno + "", 3));
        sb.append(formatUtil.padX(STAT2PbrName, 20));
        sb.append(formatUtil.padX(STAT2Txtype, 12));
        sb.append(formatUtil.pad9(STAT2TotCnt + "", 12));
        sb.append(formatUtil.pad9(STAT2TotAmt + "", 12));
        sb.append(formatUtil.padX("", 1));
        fileContentsSTAT2.add(sb.toString());
    }

    private void writeFile() {

        textFileSTAT2.deleteFile(fileNameSTAT2);

        try {
            textFileSTAT2.writeFileContent(fileNameSTAT2, fileContentsSTAT2, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private void initWK() {
        // 007650     MOVE    0                TO    WK-TOTCFEE.
        //        007700     MOVE    0                TO    WK-TOTCNT-C,WK-TOTAMT-C.
        //        007720     MOVE    0                TO    WK-TOTCNT-M,WK-TOTAMT-M.
        //        007740     MOVE    0                TO    WK-TOTCNT-AUTO,WK-TOTAMT-AUTO.
        //        007750     MOVE    0                TO    WK-TOTCNT,WK-TOTAMT.
        //        007800     MOVE    0                TO    WK-CLLBR.
        wkTotCnt_C = BigDecimal.ZERO;
        wkTotAmt_C = BigDecimal.ZERO;
        wkTotCnt_M = BigDecimal.ZERO;
        wkTotAmt_M = BigDecimal.ZERO;
        wkTotCntAuto = BigDecimal.ZERO;
        wkTotAmtAuto = BigDecimal.ZERO;
        wkTotCnt = BigDecimal.ZERO;
        wkTotAmt = BigDecimal.ZERO;
        wkCllbr = 0;
    }

    private void _SORTOUT() {

        //        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortRecExec....");
        //            003100 01   SORT-REC.
        //            003200  03  S-CLLBR                   PIC 9(03).
        //            003300  03  S-TXTYPE                  PIC X(01).
        //            003400  03  S-AMT                     PIC 9(10).
        //            003450  03  S-CFEE                    PIC 9(03)V9.

        // 011100 CS-SORTOUT-RTN.
        //
        //// 清變數
        //
        // 011200     MOVE    0                TO    WK-SUBCNT-AUTO,WK-SUBAMT-AUTO.
        // 011300     MOVE    0                TO    WK-SUBCNT-C,WK-SUBAMT-C.
        // 011350     MOVE    0                TO    WK-SUBCNT-M,WK-SUBAMT-M.
        // 011400     MOVE    0                TO    WK-SUBCNT,WK-SUBAMT.
        // 011450     MOVE    0                TO    WK-SUBCFEE.
        // 011500 0000-LOOP.

        wkSubCntAuto = BigDecimal.ZERO;
        wkSubAmtAuto = BigDecimal.ZERO;
        wkSubCnt_C = BigDecimal.ZERO;
        wkSubAmt_C = BigDecimal.ZERO;
        wkSubCnt_M = BigDecimal.ZERO;
        wkSubAmt_M = BigDecimal.ZERO;
        wkSubCnt = BigDecimal.ZERO;
        wkSubAmt = BigDecimal.ZERO;
        wkSubCfee = BigDecimal.ZERO;

        //// 將SORT後的記錄讀出，直到檔尾
        ////  若WK-CLLBR=0，表無資料，GO TO CS-SORTOUT-EXIT，結束本節
        ////  其他
        ////    A.執行1000-DTL-RTN 寫FD-STAT2(明細)
        ////    B.執行2000-TOT-RTN 寫FD-STAT2(合計)
        ////    C.GO TO CS-SORTOUT-EXIT，結束本節
        //
        // 011600     RETURN     SORTFL      AT END
        // 011620       IF       WK-CLLBR         =        0
        // 011640         GO TO  CS-SORTOUT-EXIT
        // 011660       ELSE
        // 011700         PERFORM  1000-DTL-RTN   THRU   1000-DTL-EXIT
        // 011800         PERFORM  2000-TOT-RTN   THRU   2000-TOT-EXIT
        // 011900         GO TO CS-SORTOUT-EXIT
        // 011950       END-IF.

        //// 若CLLBR不同
        ////  若WK-CLLBR=0，WK-CLLBR設定為S-CLLBR
        ////  其他
        ////   A.執行1000-DTL-RTN 寫FD-STAT2(明細)
        //
        //
        // 012000     IF         S-CLLBR           NOT =   WK-CLLBR
        // 012100       IF       WK-CLLBR          =       0
        // 012200         MOVE   S-CLLBR           TO      WK-CLLBR
        // 012300       ELSE
        // 012400         PERFORM  1000-DTL-RTN   THRU     1000-DTL-EXIT.
        if (sCllbr != wkCllbr) {
            if (wkCllbr == 0) {
                wkCllbr = sCllbr;
            } else {
                _1000_DTL();
            }
        }

        //// 根據不同TXTYPE,累加筆數金額
        //
        // 012700     IF         S-TXTYPE          =       "C"
        // 012800       ADD      1                 TO      WK-SUBCNT-C
        // 012900       ADD      1                 TO      WK-TOTCNT-C
        // 012920       ADD      1                 TO      WK-SUBCNT
        // 012940       ADD      1                 TO      WK-TOTCNT
        // 012960       ADD      S-AMT             TO      WK-SUBAMT
        // 012980       ADD      S-AMT             TO      WK-TOTAMT
        // 013100       ADD      S-AMT             TO      WK-SUBAMT-C
        // 013200       ADD      S-AMT             TO      WK-TOTAMT-C
        // 013350       GO  TO   0000-LOOP.
        // 013400     IF         S-TXTYPE          =       "M"
        // 013500       ADD      1                 TO      WK-SUBCNT-M
        // 013600       ADD      1                 TO      WK-TOTCNT-M
        // 013620       ADD      1                 TO      WK-SUBCNT
        // 013640       ADD      1                 TO      WK-TOTCNT
        // 013660       ADD      S-AMT             TO      WK-SUBAMT
        // 013680       ADD      S-AMT             TO      WK-TOTAMT
        // 013800       ADD      S-AMT             TO      WK-SUBAMT-M
        // 013900       ADD      S-AMT             TO      WK-TOTAMT-M
        // 014100       GO  TO   0000-LOOP.
        // 014110     ADD        1                 TO      WK-SUBCNT-AUTO.
        // 014120     ADD        1                 TO      WK-TOTCNT-AUTO.
        // 014122     ADD        1                 TO      WK-SUBCNT.
        // 014124     ADD        1                 TO      WK-TOTCNT.
        // 014126     ADD        S-AMT             TO      WK-SUBAMT.
        // 014128     ADD        S-AMT             TO      WK-TOTAMT.
        // 014140     ADD        S-AMT             TO      WK-SUBAMT-AUTO.
        // 014150     ADD        S-AMT             TO      WK-TOTAMT-AUTO.
        // 014160     ADD        S-CFEE            TO      WK-SUBCFEE,WK-TOTCFEE.
        // 014170     GO   TO    0000-LOOP.
        // 014200 CS-SORTOUT-EXIT.

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

        } else {
            wkSubCntAuto = wkSubCntAuto.add(BigDecimal.ONE);
            wkTotCntAuto = wkSubCntAuto.add(BigDecimal.ONE);
            wkSubCnt = wkSubCnt.add(BigDecimal.ONE);
            wkTotCnt = wkTotCnt.add(BigDecimal.ONE);

            wkSubAmtAuto = wkSubAmtAuto.add(sAmt);
            wkTotAmtAuto = wkTotAmtAuto.add(sAmt);
            wkSubAmt = wkSubAmt.add(sAmt);
            wkTotAmt = wkTotAmt.add(sAmt);

            wkSubCfee = sCfee;
            wkTotCfee = sCfee;
        }
    }

    private void init_STAT2_REC() {

        // 002300  01  STAT2-REC.
        // 002400      03  STAT2-CLLBR                      PIC 9(03).
        // 002450      03  FILLER01                         PIC X(01).
        // 002500      03  STAT2-CLLBRC                     PIC X(20).
        // 002600      03  FILLER02                         PIC X(01).
        // 002700      03  STAT2-C-CNT                      PIC 9(06).
        // 002750      03  FILLER03                         PIC X(01).
        // 002800      03  STAT2-C-AMT                      PIC 9(12).
        // 002802      03  FILLER04                         PIC X(01).
        // 002805      03  STAT2-M-CNT                      PIC 9(06).
        // 002807      03  FILLER05                         PIC X(01).
        // 002810      03  STAT2-M-AMT                      PIC 9(12).
        // 002812      03  FILLER06                         PIC X(01).
        // 002815      03  STAT2-AUTO-CNT                   PIC 9(06).
        // 002817      03  FILLER07                         PIC X(01).
        // 002820      03  STAT2-AUTO-AMT                   PIC 9(12).
        // 002822      03  FILLER08                         PIC X(01).
        // 002825      03  STAT2-TOTCNT                     PIC 9(06).
        // 002827      03  FILLER09                         PIC X(01).
        // 002830      03  STAT2-TOTAMT                     PIC 9(12).
        // 002832      03  FILLER10                         PIC X(01).
        // 002835      03  STAT2-CNT-RATE1                  PIC 9(03).
        // 002840      03  STAT2-CNT-RATE2                  PIC X(01).
        // 002845      03  STAT2-CNT-RATE3                  PIC 9(02).
        // 002850      03  STAT2-CNT-RATE4                  PIC X(01).
        // 002852      03  FILLER11                         PIC X(01).
        // 002855      03  STAT2-AMT-RATE1                  PIC 9(03).
        // 002860      03  STAT2-AMT-RATE2                  PIC X(01).
        // 002865      03  STAT2-AMT-RATE3                  PIC 9(02).
        // 002870      03  STAT2-AMT-RATE4                  PIC X(01).
        // 002872      03  FILLER12                         PIC X(01).
        // 002875      03  STAT2-CFEE                       PIC 9(08).
        // 002900      03  FILLER                           PIC X(03).

        stat2Cllbr = 0;
        stat2Cllbrc = "";
        stat2_C_Cnt = BigDecimal.ZERO;
        stat2_C_Amt = BigDecimal.ZERO;
        stat2_M_Cnt = BigDecimal.ZERO;
        stat2_M_Amt = BigDecimal.ZERO;
        stat2_Auto_Cnt = BigDecimal.ZERO;
        stat2_Auto_Amt = BigDecimal.ZERO;
        stat2_TotCnt = BigDecimal.ZERO;
        stat2_TotAmt = BigDecimal.ZERO;

        stat2CntRate1 = BigDecimal.ZERO;
        stat2CntRate2 = "";
        stat2CntRate3 = BigDecimal.ZERO;
        stat2CntRate4 = "";

        stat2AmtRate1 = BigDecimal.ZERO;
        stat2AmtRate2 = "";
        stat2AmtRate3 = BigDecimal.ZERO;
        stat2AmtRate4 = "";

        stat2Cfee = BigDecimal.ZERO;
    }

    private void write_STAT2_REC() {

        // 002300  01  STAT2-REC.
        // 002400      03  STAT2-CLLBR                      PIC 9(03).
        // 002450      03  FILLER01                         PIC X(01).
        // 002500      03  STAT2-CLLBRC                     PIC X(20).
        // 002600      03  FILLER02                         PIC X(01).
        // 002700      03  STAT2-C-CNT                      PIC 9(06).
        // 002750      03  FILLER03                         PIC X(01).
        // 002800      03  STAT2-C-AMT                      PIC 9(12).
        // 002802      03  FILLER04                         PIC X(01).
        // 002805      03  STAT2-M-CNT                      PIC 9(06).
        // 002807      03  FILLER05                         PIC X(01).
        // 002810      03  STAT2-M-AMT                      PIC 9(12).
        // 002812      03  FILLER06                         PIC X(01).
        // 002815      03  STAT2-AUTO-CNT                   PIC 9(06).
        // 002817      03  FILLER07                         PIC X(01).
        // 002820      03  STAT2-AUTO-AMT                   PIC 9(12).
        // 002822      03  FILLER08                         PIC X(01).
        // 002825      03  STAT2-TOTCNT                     PIC 9(06).
        // 002827      03  FILLER09                         PIC X(01).
        // 002830      03  STAT2-TOTAMT                     PIC 9(12).
        // 002832      03  FILLER10                         PIC X(01).
        // 002835      03  STAT2-CNT-RATE1                  PIC 9(03).
        // 002840      03  STAT2-CNT-RATE2                  PIC X(01).
        // 002845      03  STAT2-CNT-RATE3                  PIC 9(02).
        // 002850      03  STAT2-CNT-RATE4                  PIC X(01).
        // 002852      03  FILLER11                         PIC X(01).
        // 002855      03  STAT2-AMT-RATE1                  PIC 9(03).
        // 002860      03  STAT2-AMT-RATE2                  PIC X(01).
        // 002865      03  STAT2-AMT-RATE3                  PIC 9(02).
        // 002870      03  STAT2-AMT-RATE4                  PIC X(01).
        // 002872      03  FILLER12                         PIC X(01).
        // 002875      03  STAT2-CFEE                       PIC 9(08).
        // 002900      03  FILLER                           PIC X(03).

        sb = new StringBuilder();
        sb.append(formatUtil.pad9(stat2Cllbr + "", 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(stat2Cllbrc, 20));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_C_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_C_Amt + "", 12));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_M_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_M_Amt + "", 12));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_Auto_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_Auto_Amt + "", 12));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2_TotAmt + "", 12));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2CntRate1.intValue() + "", 3));
        sb.append(formatUtil.padX(stat2CntRate2, 1));
        sb.append(formatUtil.pad9(stat2CntRate3.intValue() + "", 2));
        sb.append(formatUtil.padX(stat2CntRate4, 1));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2AmtRate1.intValue() + "", 3));
        sb.append(formatUtil.padX(stat2AmtRate2, 1));
        sb.append(formatUtil.pad9(stat2AmtRate3.intValue() + "", 2));
        sb.append(formatUtil.padX(stat2AmtRate4, 1));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(stat2Cfee + "", 8));
        sb.append(formatUtil.padX("", 3));

        fileContentsSTAT2.add(sb.toString());

        stat2Cllbr = 0;
        stat2Cllbrc = "";
        stat2_C_Cnt = BigDecimal.ZERO;
        stat2_C_Amt = BigDecimal.ZERO;
        stat2_M_Cnt = BigDecimal.ZERO;
        stat2_M_Amt = BigDecimal.ZERO;
        stat2_Auto_Cnt = BigDecimal.ZERO;
        stat2_Auto_Amt = BigDecimal.ZERO;
        stat2_TotCnt = BigDecimal.ZERO;
        stat2_TotAmt = BigDecimal.ZERO;
        // ????????
        stat2CntRate1 = BigDecimal.ZERO;
        stat2CntRate2 = "";
        stat2CntRate3 = BigDecimal.ZERO;
        stat2CntRate4 = "";

        stat2AmtRate1 = BigDecimal.ZERO;
        stat2AmtRate2 = "";
        stat2AmtRate3 = BigDecimal.ZERO;
        stat2AmtRate4 = "";

        stat2Cfee = BigDecimal.ZERO;
    }

    private void _1000_DTL() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "_1000_DTL....");

        // 014600 1000-DTL-RTN.
        //
        //// 搬相關資料到STAT2-REC(明細)
        //
        // 014700     MOVE       SPACES            TO      STAT2-REC.
        // 014800     MOVE       WK-CLLBR          TO      STAT2-CLLBR.
        init_STAT2_REC();
        stat2Cllbr = wkCllbr;
        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定代收行變數值
        //// 若有誤，搬"N"

        Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(sCllbr);
        if (bctl == null) {
            wkCllbrC = "N";
        } else {
            wkCllbr = parse.string2Integer(bctl.getBrno());
            wkCllbrC = "Y";
        }

        // 014900     FIND       DB-BCTL-ACCESS AT  DB-BCTL-BRNO = WK-CLLBR
        // 015000       ON EXCEPTION
        // 015100          MOVE  "N"              TO      WK-CLLBRC.
        // 015200     IF         WK-CLLBRC        =       "Y"
        // 015300       PERFORM  0000-CNV-RTN     THRU    0000-CNV-EXIT
        // 015400     ELSE
        // 015500       MOVE     SPACES           TO      STAT2-CLLBRC.
        // 015800     MOVE       WK-SUBCNT-C      TO      STAT2-C-CNT.
        // 015900     MOVE       WK-SUBAMT-C      TO      STAT2-C-AMT.
        // 016400     MOVE       WK-SUBCNT-M      TO      STAT2-M-CNT.
        // 016500     MOVE       WK-SUBAMT-M      TO      STAT2-M-AMT.
        // 017000     MOVE       WK-SUBCNT-AUTO   TO      STAT2-AUTO-CNT.
        // 017100     MOVE       WK-SUBAMT-AUTO   TO      STAT2-AUTO-AMT.
        // 017200     MOVE       WK-SUBCNT        TO      STAT2-TOTCNT.
        // 017300     MOVE       WK-SUBAMT        TO      STAT2-TOTAMT.
        if ("Y".equals(wkCllbrC)) {
            // 待確認
            stat2Cllbrc = getPbrName(parse.string2Integer(bctl.getBrno()));
        } else {
            stat2Cllbrc = "";
            stat2_C_Cnt = wkSubCnt_C;
            stat2_C_Amt = wkSubAmt_C;
            stat2_M_Cnt = wkSubCnt_M;
            stat2_M_Amt = wkSubAmt_M;
            stat2_Auto_Cnt = wkSubCntAuto;
            stat2_Auto_Amt = wkSubAmtAuto;
            stat2_TotCnt = wkTotCnt;
            stat2_TotAmt = wkTotAmt;
        }

        // 017302     IF         WK-SUBCNT        =       0
        // 017304       MOVE     0                TO      STAT2-CNT-RATE1
        // 017306     ELSE
        // 017308       COMPUTE STAT2-CNT-RATE1 = WK-SUBCNT-AUTO / WK-SUBCNT * 100.
        // 017310     MOVE       "."              TO      STAT2-CNT-RATE2.
        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat2CntRate1 = BigDecimal.ZERO;
        } else {
            stat2CntRate1 = wkSubCntAuto.divide(wkSubCnt).multiply(new BigDecimal("100"));
        }
        stat2CntRate2 = ".";

        // 017312     IF         WK-SUBCNT        =       0
        // 017314       MOVE     0                TO      STAT2-CNT-RATE3
        // 017316     ELSE
        // 017318       COMPUTE  STAT2-CNT-RATE3  =  ( WK-SUBCNT-AUTO / WK-SUBCNT *
        // 017320                  100 - STAT2-CNT-RATE1 ) * 100.
        // 017325     MOVE       "%"              TO      STAT2-CNT-RATE4.

        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat2CntRate3 = BigDecimal.ZERO;
        } else {
            stat2CntRate3 =
                    wkSubCntAuto
                            .divide(wkSubCnt)
                            .multiply(new BigDecimal("100"))
                            .subtract(stat2CntRate1)
                            .multiply(new BigDecimal("100"));
        }
        stat2CntRate4 = "%";
        // 017326     IF         WK-SUBAMT        =       0
        // 017328       MOVE     0                TO      STAT2-AMT-RATE1
        // 017330     ELSE
        // 017332       COMPUTE STAT2-AMT-RATE1 = WK-SUBAMT-AUTO / WK-SUBAMT * 100.
        // 017335     MOVE       "."              TO      STAT2-AMT-RATE2.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat2AmtRate1 = BigDecimal.ZERO;
        } else {
            stat2AmtRate1 = wkSubAmtAuto.divide(wkSubAmt).multiply(new BigDecimal("100"));
        }
        stat2AmtRate2 = ".";

        // 017336     IF         WK-SUBAMT        =       0
        // 017338       MOVE     0                TO      STAT2-AMT-RATE3
        // 017340     ELSE
        // 017342       COMPUTE  STAT2-AMT-RATE3  =  ( WK-SUBAMT-AUTO / WK-SUBAMT *
        // 017345                  100 - STAT2-AMT-RATE1 ) * 100.
        // 017350     MOVE       "%"              TO      STAT2-AMT-RATE4.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat2AmtRate3 = BigDecimal.ZERO;
        } else {
            stat2AmtRate3 =
                    wkSubAmtAuto
                            .divide(wkSubAmt)
                            .multiply(new BigDecimal("100"))
                            .subtract(stat2AmtRate1)
                            .multiply(new BigDecimal("100"));
        }
        stat2AmtRate4 = "%";
        // 017355     MOVE       WK-SUBCFEE       TO      STAT2-CFEE.
        // 017357     PERFORM    1200-INIT-RTN    THRU    1200-INIT-EXIT.

        stat2Cfee = wkSubCfee;
        _1200_INIT();

        //// 寫檔FD-STAT2
        //
        // 017360     WRITE      STAT2-REC.
        // 017400     MOVE       S-CLLBR          TO      WK-CLLBR.
        // 017500     MOVE       "Y"         TO    WK-CLLBRC.
        // 017600     MOVE       0           TO    WK-SUBCNT-AUTO,WK-SUBAMT-AUTO.
        // 017700     MOVE       0           TO    WK-SUBCNT-C,WK-SUBAMT-C.
        // 017750     MOVE       0           TO    WK-SUBCNT-M,WK-SUBAMT-M.
        // 017800     MOVE       0           TO    WK-SUBCNT,WK-SUBAMT,WK-SUBCFEE.
        // 017900 1000-DTL-EXIT.
        write_STAT2_REC();
        wkCllbr = sCllbr;
        wkCllbrC = "Y";
        wkSubCntAuto = BigDecimal.ZERO;
        wkSubAmtAuto = BigDecimal.ZERO;
        wkSubCnt_C = BigDecimal.ZERO;
        wkSubAmt_C = BigDecimal.ZERO;
        wkSubCnt_M = BigDecimal.ZERO;
        wkSubAmt_M = BigDecimal.ZERO;
        wkSubCnt = BigDecimal.ZERO;
        wkSubAmt = BigDecimal.ZERO;
        wkSubCfee = BigDecimal.ZERO;
    }

    private void _2000_TOT() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "_2000_TOT....");

        // 018200 2000-TOT-RTN.
        //
        //// 搬相關資料到STAT2-REC(合計)
        //
        // 018220     MOVE       SPACES           TO      STAT2-REC.
        // 018240     MOVE       WK-TOTCNT-C      TO      STAT2-C-CNT.
        // 018260     MOVE       WK-TOTAMT-C      TO      STAT2-C-AMT.
        // 018280     MOVE       WK-TOTCNT-M      TO      STAT2-M-CNT.
        // 018300     MOVE       WK-TOTAMT-M      TO      STAT2-M-AMT.
        // 018320     MOVE       WK-TOTCNT-AUTO   TO      STAT2-AUTO-CNT.
        // 018340     MOVE       WK-TOTAMT-AUTO   TO      STAT2-AUTO-AMT.
        // 018360     MOVE       WK-TOTCNT        TO      STAT2-TOTCNT.
        // 018380     MOVE       WK-TOTAMT        TO      STAT2-TOTAMT.
        init_STAT2_REC();
        stat2_C_Cnt = wkSubCnt_C;
        stat2_C_Amt = wkSubAmt_C;
        stat2_M_Cnt = wkSubCnt_M;
        stat2_M_Amt = wkSubAmt_M;
        stat2_Auto_Cnt = wkSubCntAuto;
        stat2_Auto_Amt = wkSubAmtAuto;
        stat2_TotCnt = wkTotCnt;
        stat2_TotAmt = wkTotAmt;

        // 018385     IF         WK-TOTCNT        =       0
        // 018390       MOVE     0                TO      STAT2-CNT-RATE1
        // 018395     ELSE
        // 018400       COMPUTE STAT2-CNT-RATE1 = WK-TOTCNT-AUTO / WK-TOTCNT * 100.
        // 018420     MOVE       "."              TO      STAT2-CNT-RATE2.
        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat2CntRate1 = BigDecimal.ZERO;
        } else {
            stat2CntRate1 = wkTotCntAuto.divide(wkTotCnt).multiply(new BigDecimal("100"));
        }
        stat2CntRate2 = ".";

        // 018425     IF         WK-TOTCNT        =       0
        // 018430       MOVE     0                TO      STAT2-CNT-RATE3
        // 018435     ELSE
        // 018440       COMPUTE  STAT2-CNT-RATE3  =  ( WK-TOTCNT-AUTO / WK-TOTCNT *
        // 018460                  100 - STAT2-CNT-RATE1 ) * 100.
        // 018480     MOVE       "%"              TO      STAT2-CNT-RATE4.
        if (wkSubCnt.compareTo(BigDecimal.ZERO) == 0) {
            stat2CntRate3 = BigDecimal.ZERO;
        } else {
            stat2CntRate3 =
                    wkSubCntAuto
                            .divide(wkSubCnt)
                            .multiply(new BigDecimal("100"))
                            .subtract(stat2CntRate1)
                            .multiply(new BigDecimal("100"));
        }
        stat2CntRate4 = "%";
        // 018485     IF         WK-TOTAMT        =       0
        // 018490       MOVE     0                TO      STAT2-AMT-RATE1
        // 018495     ELSE
        // 018500       COMPUTE STAT2-AMT-RATE1 = WK-TOTAMT-AUTO / WK-TOTAMT * 100.
        // 018520     MOVE       "."              TO      STAT2-AMT-RATE2.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat2AmtRate1 = BigDecimal.ZERO;
        } else {
            stat2AmtRate1 = wkSubAmtAuto.divide(wkSubAmt).multiply(new BigDecimal("100"));
        }
        stat2AmtRate2 = ".";
        // 018525     IF         WK-TOTAMT        =       0
        // 018530       MOVE     0                TO      STAT2-AMT-RATE3
        // 018535     ELSE
        // 018540       COMPUTE  STAT2-AMT-RATE3  =  ( WK-TOTAMT-AUTO / WK-TOTAMT *
        // 018560                  100 - STAT2-AMT-RATE1 ) * 100.
        // 018580     MOVE       "%"              TO      STAT2-AMT-RATE4.
        if (wkSubAmt.compareTo(BigDecimal.ZERO) == 0) {
            stat2AmtRate3 = BigDecimal.ZERO;
        } else {
            stat2AmtRate3 =
                    wkSubAmtAuto
                            .divide(wkSubAmt)
                            .multiply(new BigDecimal("100"))
                            .subtract(stat2AmtRate1)
                            .multiply(new BigDecimal("100"));
        }
        stat2AmtRate4 = "%";
        // 018600     MOVE       WK-TOTCFEE       TO      STAT2-CFEE.
        // 018650     MOVE       "   合　計   "   TO      STAT2-CLLBRC.
        // 018670     PERFORM    1200-INIT-RTN    THRU    1200-INIT-EXIT.

        stat2Cfee = wkTotCfee;
        stat2Cllbrc = "   合　計   ";
        _1200_INIT();

        //// 寫檔FD-STAT2
        //
        // 018700     WRITE      STAT2-REC.
        // 018900 2000-TOT-EXIT.
        init_STAT2_REC();
    }

    private void _1200_INIT() {
        // 038200 1200-INIT-RTN.
        //
        //// 加","
        //
        // 038300     MOVE        ","             TO   FILLER01,FILLER02,FILLER03,
        // 038400                                      FILLER04,FILLER05,FILLER06,
        // 038500                                      FILLER07,FILLER08,FILLER09,
        // 038600                                      FILLER10,FILLER11,FILLER12.
        // 038700 1200-INIT-EXIT.
        filler = ",";
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

    private boolean isNotALlNumbers(String text) {
        // 字串中 是否皆為數字
        if (text.matches("[+-]?\\d*(\\.\\d+)?")) {
            return true;
        } else {
            return false;
        }
    }
}
