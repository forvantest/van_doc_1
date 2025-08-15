/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTCONVBAF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Component("OUTCONVBAFLsnr")
@Scope("prototype")
public class OUTCONVBAFLsnr extends BatchListenerCase<OUTCONVBAF> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Parse parse;
    private OUTCONVBAF event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CL022_FILE_PATH = "CL022\\003"; // 目錄
    private static final String FILE_NAME_UPDBAF = "UPDBAF."; // 產檔檔名
    private static final String FILE_NAME_KPUTH = "KPUTH."; // 讀檔檔名
    private String wkUpddir; // 產檔路徑
    private String wkPutdir; // 讀檔路徑
    private String PATH_SEPARATOR = File.separator;
    private StringBuilder sb = new StringBuilder();
    private List<String> fileUPDBAFContents; //  檔案內容

    private Map<String, String> textMap;
    // ----tita----
    private int wkTaskRdate;
    private int wkTaskDate;

    // ----wk----
    private int wkDate;
    private int wkUdate;
    private int wkPdate;
    private String wkPriorCode;
    private BigDecimal wkTotamt;
    private BigDecimal wkTotfee;
    private BigDecimal wkTotipal;
    private BigDecimal wkTaskAmt;
    private BigDecimal wkTaskFee;
    private BigDecimal wkTaskIpal;
    private BigDecimal wkTaskFeecost;
    private int wkTotcnt;
    private int wkPriorPbrno;
    private BigDecimal wkEfcscount;
    // ----KPUTH----
    private String kputhCode;
    private int kputhPbrno;
    private BigDecimal kputhAmt;
    private String kputhTxtype;
    private String kputhSmserno;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTCONVBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTCONVBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr run()");
        init(event);
        //// 若FD-KPUTH檔案不存在，跳到0000-END-RTN 顯示訊息、結束程式
        // 006300     IF  ATTRIBUTE  RESIDENT  OF FD-KPUTH  IS  NOT = VALUE(TRUE)
        // 006400       GO TO 0000-END-RTN.
        if (textFile.exists(wkPutdir)) {
            //// 執行0000-MAIN-RTN，讀FD-KPUTH，不同代收類別，寫檔FD-UPDBAF
            // 006600     PERFORM 0000-MAIN-RTN       THRU   0000-MAIN-EXIT.
            main();
        }

        // 006700 0000-END-RTN.
        //
        //// 顯示訊息、結束程式
        //
        // 006800     DISPLAY   "SYM/CL/BH/OUTING/CONVBAF : COMPLETED".
        // 006900     STOP RUN.
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/OUTING/CONVBAF : COMPLETED");
    }

    private void init(OUTCONVBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr init.....");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = parse.string2Integer(textMap.get("WK_TASK_DATE"));
        wkTaskRdate = parse.string2Integer(textMap.get("RGDAY"));
        //// 搬接收參數-原訂入帳日 給WK-PUTDIR、WK-UPDDIR檔名變數值
        // 005700     MOVE    WK-TASK-RDATE        TO     WK-PDATE   ,
        // 005800                                         WK-UDATE   .
        wkPdate = wkTaskRdate;
        wkUdate = wkTaskRdate;
        //// 搬接收參數-實際入帳日 給WK-DATE
        // 005900     MOVE    WK-TASK-DATE         TO     WK-DATE    .
        wkDate = wkTaskDate;

        //// 設定檔名
        ////  WK-UPDDIR="DATA/GN/DWL/CL022/003/"+WK-UDATE 9(07)+"/UPDBAF."
        ////  WK-PUTDIR="DATA/GN/DWL/CL022/003/"+WK-PDATE 9(07)+"/KPUTH."
        // 006100     CHANGE  ATTRIBUTE FILENAME  OF FD-UPDBAF  TO  WK-UPDDIR.
        // 006200     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH   TO  WK-PUTDIR .
        wkUpddir =
                fileDir
                        + CL022_FILE_PATH
                        + PATH_SEPARATOR
                        + wkUdate
                        + PATH_SEPARATOR
                        + FILE_NAME_UPDBAF;
        wkPutdir =
                fileDir
                        + CL022_FILE_PATH
                        + PATH_SEPARATOR
                        + wkPdate
                        + PATH_SEPARATOR
                        + FILE_NAME_KPUTH;

        fileUPDBAFContents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr main.....");
        // 007100 0000-MAIN-RTN.
        //
        //// 開啟檔案
        //
        // 007200     OPEN     INPUT              FD-KPUTH .
        // 007300     OPEN     OUTPUT             FD-UPDBAF.
        //
        //// 清變數值
        // 007400     MOVE     SPACES             TO     KPUTH-REC ,
        // 007500                                        WK-PRIOR-CODE.
        // 007600     MOVE     0                  TO     WK-TOTAMT,WK-TOTFEE ,
        // 007650                                        WK-TOTCNT,WK-TOTIPAL,
        // 007700                                        WK-PRIOR-PBRNO.
        wkPriorCode = "";
        wkTotamt = new BigDecimal(0);
        wkTotfee = new BigDecimal(0);
        wkTotipal = new BigDecimal(0);
        wkTotcnt = 0;
        wkPriorPbrno = 0;

        // SORT OBJ/CL/BH/OUTING/SORT/CL022
        File tmpFile = new File(wkPutdir);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(119, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(112, 1, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);

        // 007800 READ-LOOP.
        //// 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-LAST
        // 007900     READ     FD-KPUTH AT END    GO TO  0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 03 KPUTH-PUTFILE
            //  05 KPUTH-PUTTYPE	9(02)	媒體種類 0-2
            //  05 KPUTH-PUTNAME	X(08)	媒體檔名 2-10
            // 03 KPUTH-PUTFILE-R1	REDEFINES KPUTH-PUTFILE
            //  05 KPUTH-ENTPNO	X(10)	 0-10
            // 03 KPUTH-CODE	X(06)	代收類別 10-16
            // 03 KPUTH-RCPTID	X(16)	銷帳號碼 16-32
            // 03 KPUTH-DATE	9(07)	代收日 32-39
            // 03 KPUTH-TIME	9(06)	代收時間 39-45
            // 03 KPUTH-CLLBR	9(03)	代收行 45-48
            // 03 KPUTH-LMTDATE	9(06)	繳費期限 48-54
            // 03 KPUTH-AMT	9(10)	繳費金額 54-64
            // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
            // 03 KPUTH-USERDATE-R1	REDEFINES KPUTH-USERDATA
            //  05 KPUTH-SMSERNO	X(03)	代收簡碼??? 64-67
            //  05 KPUTH-RETAILNO	X(08)	 67-75
            //  05 KPUTH-BARCODE3	X(15)	 75-90
            //  05 KPUTH-FILLER	X(14)	90-104
            // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
            // 03 KPUTH-TXTYPE	X(01)	帳務別 111-112
            // 03 KPUTH-SERINO	9(06)	交易明細流水序號 112-118
            // 03 KPUTH-PBRNO	9(03)	主辦分行 118-121
            // 03 KPUTH-UPLDATE	9(07)	上傳日 121-128
            // 03 KPUTH-FEETYPE	9(01)	 128-129
            // 03 KPUTH-FEEO2L	9(05)V99	129-136
            // 03 FILEER	X(02)	 136-138
            // 03 FILLER	X(02)	 138-140
            kputhCode = detail.substring(10, 16);
            kputhPbrno = parse.string2Integer(detail.substring(118, 121));
            kputhAmt = parse.string2BigDecimal(detail.substring(54, 64));
            kputhTxtype = detail.substring(111, 112);
            kputhSmserno = detail.substring(64, 67);
            //// 執行CODE-SWH-RTN，依代收類別是否相同，判斷是否寫檔及相關處理
            // 008000     PERFORM  CODE-SWH-RTN       THRU   CODE-SWH-EXIT.
            codeSwh();
            //// LOOP讀下一筆FD-KPUTH

            // 008100     GO TO  READ-LOOP.
            if (cnt == lines.size()) {
                // 008200 0000-MAIN-LAST.

                //// 執行FILE-DTL-RTN，寫檔FD-UPDBAF
                // 008300     PERFORM  FILE-DTL-RTN       THRU   FILE-DTL-EXIT.
                fileDtl();
                //// 關閉檔案
                // 008400     CLOSE    FD-KPUTH   .
                // 008500     CLOSE    FD-UPDBAF WITH SAVE.
                try {
                    textFile.writeFileContent(wkUpddir, fileUPDBAFContents, CHARSET);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
            }
        }
        // 008600 0000-MAIN-EXIT.
    }

    private void codeSwh() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr codeSwh.....");
        // 008900 CODE-SWH-RTN.

        //// 首筆或不同主辦行之首筆
        ////  A.搬代收類別給WK-PRIOR-CODE、搬主辦行給WK-PRIOR-PBRNO
        ////  B.執行1100-AMT-RTN，本金計算
        ////  C.執行1200-FEE-RTN，手續費收入(分潤金額)計算
        ////  D.執行1400-IPAL-RTN，內部損益計算
        ////  E.累計筆數、金額
        ////  F.結束本段落

        // 009000     IF        WK-PRIOR-CODE     =      SPACES
        if (wkPriorCode.isEmpty()) {
            // 009100       MOVE    KPUTH-CODE        TO     WK-PRIOR-CODE
            // 009200       MOVE    KPUTH-PBRNO       TO     WK-PRIOR-PBRNO
            wkPriorCode = kputhCode;
            wkPriorPbrno = kputhPbrno;
            // 009300       PERFORM 1100-AMT-RTN      THRU   1100-AMT-EXIT
            amt();
            // 009400       PERFORM 1200-FEE-RTN      THRU   1200-FEE-EXIT
            fee();
            // 009460       PERFORM 1400-IPAL-RTN     THRU   1400-IPAL-EXIT
            ipal();
            // 009470       ADD     1                 TO     WK-TOTCNT
            // 009500       ADD     WK-TASK-AMT       TO     WK-TOTAMT
            // 009550       ADD     WK-TASK-FEE       TO     WK-TOTFEE
            // 009590       ADD     WK-TASK-IPAL      TO     WK-TOTIPAL
            wkTotcnt = wkTotcnt + 1;
            wkTotamt = wkTotamt.add(wkTaskAmt);
            wkTotfee = wkTotfee.add(wkTaskFee);
            wkTotipal = wkTotipal.add(wkTaskIpal);
            // 009600       GO TO   CODE-SWH-EXIT.
            return;
        }
        //// 代收類別不同時
        ////  A.執行FILE-DTL-RTN，寫檔FD-UPDBAF
        ////  B.累計筆數、金額清0
        ////  C.搬代收類別給WK-PRIOR-CODE、搬主辦行給WK-PRIOR-PBRNO
        ////  D.執行1100-AMT-RTN，本金計算
        ////  E.執行1200-FEE-RTN，手續費收入(分潤金額)計算
        ////  F.執行1400-IPAL-RTN，內部損益計算
        ////  G.累計筆數、金額
        ////  H.結束本段落
        //
        // 009800     IF        KPUTH-CODE        NOT =  WK-PRIOR-CODE
        if (!kputhCode.equals(wkPriorCode)) {
            // 009900       PERFORM FILE-DTL-RTN      THRU   FILE-DTL-EXIT
            fileDtl();
            // 010000       MOVE    0                 TO     WK-TOTAMT,WK-TOTFEE,
            // 010050                                        WK-TOTIPAL,WK-TOTCNT
            // 010100       MOVE    KPUTH-CODE        TO     WK-PRIOR-CODE
            // 010200       MOVE    KPUTH-PBRNO       TO     WK-PRIOR-PBRNO
            wkTotamt = new BigDecimal(0);
            wkTotfee = new BigDecimal(0);
            wkTotipal = new BigDecimal(0);
            wkTotcnt = 0;
            wkPriorCode = kputhCode;
            wkPriorPbrno = kputhPbrno;
            // 010300       PERFORM 1100-AMT-RTN      THRU   1100-AMT-EXIT
            amt();
            // 010400       PERFORM 1200-FEE-RTN      THRU   1200-FEE-EXIT
            fee();
            // 010460       PERFORM 1400-IPAL-RTN     THRU   1400-IPAL-EXIT
            ipal();
            // 010470       ADD     1                 TO     WK-TOTCNT
            // 010500       ADD     WK-TASK-AMT       TO     WK-TOTAMT
            // 010550       ADD     WK-TASK-FEE       TO     WK-TOTFEE
            // 010590       ADD     WK-TASK-IPAL      TO     WK-TOTIPAL
            wkTotcnt = wkTotcnt + 1;
            wkTotamt = wkTotamt.add(wkTaskAmt);
            wkTotfee = wkTotfee.add(wkTaskFee);
            wkTotipal = wkTotipal.add(wkTaskIpal);
            // 010600       GO TO   CODE-SWH-EXIT
        } else {
            // 010700     ELSE
            //
            //// 代收類別相同時
            ////  A.搬代收類別給WK-PRIOR-CODE、搬主辦行給WK-PRIOR-PBRNO
            ////  B.執行1100-AMT-RTN，本金計算
            ////  C.執行1200-FEE-RTN，手續費收入(分潤金額)計算
            ////  D.執行1400-IPAL-RTN，內部損益計算
            ////  E.累計筆數、金額
            ////  F.結束本段落
            //
            // 010800       MOVE    KPUTH-CODE        TO     WK-PRIOR-CODE
            // 010900       MOVE    KPUTH-PBRNO       TO     WK-PRIOR-PBRNO
            wkPriorCode = kputhCode;
            wkPriorPbrno = kputhPbrno;
            // 011000       PERFORM 1100-AMT-RTN      THRU   1100-AMT-EXIT
            amt();
            // 011100       PERFORM 1200-FEE-RTN      THRU   1200-FEE-EXIT
            fee();
            // 011160       PERFORM 1400-IPAL-RTN     THRU   1400-IPAL-EXIT
            ipal();
            // 011170       ADD     1                 TO     WK-TOTCNT
            // 011200       ADD     WK-TASK-AMT       TO     WK-TOTAMT
            // 011250       ADD     WK-TASK-FEE       TO     WK-TOTFEE
            // 011290       ADD     WK-TASK-IPAL      TO     WK-TOTIPAL
            wkTotcnt = wkTotcnt + 1;
            wkTotamt = wkTotamt.add(wkTaskAmt);
            wkTotfee = wkTotfee.add(wkTaskFee);
            wkTotipal = wkTotipal.add(wkTaskIpal);
            // 011300       GO TO   CODE-SWH-EXIT.
            // 011400
        }
        // 011500 CODE-SWH-EXIT.
    }

    private void amt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr amt.....");
        // 013000 1100-AMT-RTN.
        // 013050* 本金計算 ; 營業部撥款主辦行 BY 連往 36
        // 013500     COMPUTE WK-TASK-AMT       =   KPUTH-AMT                  .
        wkTaskAmt = kputhAmt;
        // 020000 1100-AMT-EXIT.
    }

    private void fee() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr fee.....");
        // 020300 1200-FEE-RTN.
        //
        //// 手續費收入計算
        //
        // 020350* 主辦行淨賺手續費收入計算 ; 核算主辦行當日淨額存入會用到
        // 020370     MOVE  0                TO   WK-TASK-FEE.
        wkTaskFee = new BigDecimal(0);
        // 020400     IF    (  KPUTH-TXTYPE  ="K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 020500       AND (  KPUTH-SMSERNO      =   "634" OR "63D" OR "63E" OR
        // 020600                                     "6T3" OR "AG0" OR "AG1" OR
        // 020700                                     "6II" OR "MP1" OR "63T" OR
        // 020800                                     "6D1" OR "6T4" OR "63A" OR
        // 020850                                     "63B" OR "63P" OR "MP5" OR
        // 020870                                     "AG4" OR "63V" OR "63S"    )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("634".equals(kputhSmserno)
                        || "63D".equals(kputhSmserno)
                        || "63E".equals(kputhSmserno)
                        || "6T3".equals(kputhSmserno)
                        || "AG0".equals(kputhSmserno)
                        || "AG1".equals(kputhSmserno)
                        || "6II".equals(kputhSmserno)
                        || "MP1".equals(kputhSmserno)
                        || "63T".equals(kputhSmserno)
                        || "6D1".equals(kputhSmserno)
                        || "6T4".equals(kputhSmserno)
                        || "63A".equals(kputhSmserno)
                        || "63B".equals(kputhSmserno)
                        || "63P".equals(kputhSmserno)
                        || "MP5".equals(kputhSmserno)
                        || "AG4".equals(kputhSmserno)
                        || "63V".equals(kputhSmserno)
                        || "63S".equals(kputhSmserno))) {
            // 020900       COMPUTE WK-TASK-FEE       =   0                          .
            wkTaskFee = new BigDecimal(0);
        }
        // 021000
        // 021100     IF    (  KPUTH-TXTYPE  = "K" OR ="L" OR ="N" OR ="O"       )
        // 021200       AND (  KPUTH-SMSERNO      =   "AG2" OR "63F" OR "63G" OR
        // 021250                                     "63R"                      )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && ("AG2".equals(kputhSmserno)
                        || "63F".equals(kputhSmserno)
                        || "63G".equals(kputhSmserno)
                        || "63R".equals(kputhSmserno))) {
            // 021300       COMPUTE WK-TASK-FEE       =   2                          .
            wkTaskFee = new BigDecimal(2);
        }
        // 021500     IF    (  KPUTH-TXTYPE  = "K" OR ="L" OR ="N" OR ="O"       )
        // 021600       AND (  KPUTH-SMSERNO      =   "6IJ" OR "C69"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && ("6IJ".equals(kputhSmserno) || "C69".equals(kputhSmserno))) {
            // 021700       COMPUTE WK-TASK-FEE       =   3                          .
            wkTaskFee = new BigDecimal(3);
        }
        // 021900     IF    (  KPUTH-TXTYPE  = "K" OR ="L" OR ="N" OR ="O"       )
        // 022000       AND (  KPUTH-SMSERNO      =   "AG3" OR "AG5"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && ("AG3".equals(kputhSmserno) || "AG5".equals(kputhSmserno))) {
            // 022100       COMPUTE WK-TASK-FEE       =   4                          .
            wkTaskFee = new BigDecimal(4);
        }
        // 022200* 農金管道
        // 022210     IF    (  KPUTH-TXTYPE  = "X"                               )
        // 022220       AND (  KPUTH-SMSERNO =   "AG2" OR "63F" OR "63G" OR
        // 022230                       "63R" OR "6IJ" OR "C69" OR "AG3" OR "AG5")
        if (("X".equals(kputhTxtype))
                && ("AG2".equals(kputhSmserno)
                        || "63F".equals(kputhSmserno)
                        || "63G".equals(kputhSmserno)
                        || "63R".equals(kputhSmserno)
                        || "6IJ".equals(kputhSmserno)
                        || "C69".equals(kputhSmserno)
                        || "AG3".equals(kputhSmserno)
                        || "AG5".equals(kputhSmserno))) {
            // 022240       COMPUTE WK-TASK-FEE  =   0                               .
            wkTaskFee = new BigDecimal(0);
        }
        // 022300     IF    (  KPUTH-TXTYPE  = "Q" OR ="T" OR ="U" OR ="Y" OR
        // 022350                            = "Z" OR ="2"                       )
        if ("Q".equals(kputhTxtype)
                || "T".equals(kputhTxtype)
                || "U".equals(kputhTxtype)
                || "Y".equals(kputhTxtype)
                || "Z".equals(kputhTxtype)
                || "2".equals(kputhTxtype)) {
            // 022400       COMPUTE WK-TASK-FEE  =   0                               .
            wkTaskFee = new BigDecimal(0);
        }
        // 022520     IF       KPUTH-TXTYPE  = "3"
        if ("3".equals(kputhTxtype)) {
            // 022540       MOVE   KPUTH-SMSERNO  TO  WK-EFCSCOUNT
            // 022560       MULTIPLY  0.32        BY  WK-EFCSCOUNT
            // 022570                         GIVING WK-TASK-FEE     ROUNDED.
            wkEfcscount = parse.string2BigDecimal(kputhSmserno);
            wkTaskFee =
                    wkEfcscount.multiply(new BigDecimal("0.32")).setScale(0, RoundingMode.HALF_UP);
            // 022571
        }
        // 022572*LINE PAY=4 , JKOS=7 , EASY CARD= 5 , ICASH=6 , PXPAY=8
        // 022573     IF      (KPUTH-TXTYPE  = "4" OR "5" OR "6" OR "7" OR "8")
        // 022574     AND      KPUTH-SMSERNO(1:1) = "A"
        if (("4".equals(kputhTxtype)
                        || "5".equals(kputhTxtype)
                        || "6".equals(kputhTxtype)
                        || "7".equals(kputhTxtype)
                        || "8".equals(kputhTxtype))
                && "A".equals(kputhSmserno.substring(0, 1))) {
            // 022579       COMPUTE WK-TASK-FEE  =   0   .
            wkTaskFee = new BigDecimal(0);
        }
        // 022600 1200-FEE-EXIT.
    }

    private void ipal() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr ipal.....");
        // 029000 1400-IPAL-RTN.
        //
        //// WK-TASK-FEECOST後續沒用，只看WK-TASK-IPAL(內部損益)
        //
        // 029100* 核算聯行往來 37( 內部損益 )
        // 029200* 在以往  聯往３７會等同於營業部的手續費用但在偉大的電金部規劃下
        // 029300* 聯往３７已經和營業部手續費用脫鉤 , 從農金且內含手續費類別
        // 029400* 營業部會向主辦行每筆多拿 3 元
        // 029500*  主辦行 - 借 : 內部損益  X 元      借 : 聯往 36     Z+Y 元
        // 029600*            貸 : 聯往 37   X 元       貸 : 手續費收入    Y 元
        // 029700*                                           存款          Z 元
        // 029800*-----------------------------------------------------------------
        // 029900*  營業部 - 借 : 存款      Z 元         借 : 聯往 37   X 元
        // 030000*                手續費用  Y 元          貸 : 內部損益  X 元
        // 030100*             貸 : 聯往 36  Z+Y 元
        // 030200* X-Y=3 元
        // 030250     MOVE  0               TO   WK-TASK-IPAL.
        wkTaskIpal = new BigDecimal(0);
        // 030300     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 030400       AND (  KPUTH-SMSERNO      =   "634" OR "63D" OR "63E" OR
        // 030500                                     "6T3" OR "AG0" OR "AG1" OR
        // 030600                                     "6II" OR "MP1" OR "63A" OR
        // 030700                                     "MP5" OR "MU1" OR "MU2" OR
        // 030720                                     "MU3" OR "MU4" OR "MU5" OR
        // 030740                                     "MU6" OR "MU7" OR "MU8" OR
        // 030760                                     "MU9" OR "MV1" OR "AG4" OR
        // 030780                                     "63V" OR "63S"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("634".equals(kputhSmserno)
                        || "63D".equals(kputhSmserno)
                        || "63E".equals(kputhSmserno)
                        || "6T3".equals(kputhSmserno)
                        || "AG0".equals(kputhSmserno)
                        || "AG1".equals(kputhSmserno)
                        || "AII".equals(kputhSmserno)
                        || "MP1".equals(kputhSmserno)
                        || "63A".equals(kputhSmserno)
                        || "MP5".equals(kputhSmserno)
                        || "MU1".equals(kputhSmserno)
                        || "MU2".equals(kputhSmserno)
                        || "MU3".equals(kputhSmserno)
                        || "MU4".equals(kputhSmserno)
                        || "MU5".equals(kputhSmserno)
                        || "MU6".equals(kputhSmserno)
                        || "MU7".equals(kputhSmserno)
                        || "MU8".equals(kputhSmserno)
                        || "MU9".equals(kputhSmserno)
                        || "MV1".equals(kputhSmserno)
                        || "AG4".equals(kputhSmserno)
                        || "63V".equals(kputhSmserno)
                        || "63S".equals(kputhSmserno))) {
            // 030800       COMPUTE WK-TASK-IPAL      =   0                          .
            wkTaskIpal = new BigDecimal(0);
        }

        // 031000     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 031100       AND (  KPUTH-SMSERNO      =   "6D1" OR "AG2" OR "AG3" OR
        // 031200                                     "63F" OR "AG5"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("6D1".equals(kputhSmserno)
                        || "AG2".equals(kputhSmserno)
                        || "AG3".equals(kputhSmserno)
                        || "63F".equals(kputhSmserno)
                        || "AG5".equals(kputhSmserno))) {
            // 031300       COMPUTE WK-TASK-IPAL      =   6                          .
            wkTaskIpal = new BigDecimal(6);
        }

        // 031340     IF    (  KPUTH-TXTYPE = "X "          )
        // 031360       AND (  KPUTH-SMSERNO      =   "63F" )
        if ("X".equals(kputhTxtype) && "63F".equals(kputhSmserno)) {
            // 031380       COMPUTE WK-TASK-IPAL      =   8                          .
            wkTaskIpal = new BigDecimal(8);
        }

        // 031500     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 031600       AND (  KPUTH-SMSERNO      =   "6IJ" OR "C69"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("6IJ".equals(kputhSmserno) || "C69".equals(kputhSmserno))) {
            // 031700       COMPUTE WK-TASK-IPAL      =   7                          .
            wkTaskIpal = new BigDecimal(7);
        }

        // 031900     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O"        )
        // 032000       AND (  KPUTH-SMSERNO      =   "63G" OR "63R"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && ("63G".equals(kputhSmserno) || "63R".equals(kputhSmserno))) {
            // 032100       COMPUTE WK-TASK-IPAL      =   10                         .
            wkTaskIpal = new BigDecimal(10);
        }

        // 032300     IF    (  KPUTH-TXTYPE       = "X"                          )
        // 032400       AND (  KPUTH-SMSERNO      =   "63G" OR "63R"             )
        if ("X".equals(kputhTxtype) && ("63G".equals(kputhSmserno) || "63R".equals(kputhSmserno))) {
            // 032500       COMPUTE WK-TASK-IPAL      =   12                         .
            wkTaskIpal = new BigDecimal(12);
        }

        // 032700     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 032800       AND (  KPUTH-SMSERNO      =   "63B" OR "63T"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("63B".equals(kputhSmserno) || "63T".equals(kputhSmserno))) {
            // 032900       COMPUTE WK-TASK-IPAL      =   15                         .
            wkTaskIpal = new BigDecimal(15);
        }

        // 033100     IF    (  KPUTH-TXTYPE = "K" OR ="L" OR ="N" OR ="O" OR ="X")
        // 033200       AND (  KPUTH-SMSERNO      =   "6T4" OR "63P"             )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype)
                        || "X".equals(kputhTxtype))
                && ("6T4".equals(kputhSmserno) || "63P".equals(kputhSmserno))) {
            // 033300       COMPUTE WK-TASK-IPAL      =   18                         .
            wkTaskIpal = new BigDecimal(18);
        }

        // 033410     IF    (  KPUTH-TXTYPE  = "K" OR ="L" OR ="N" OR ="O"        )
        // 033420       AND (  KPUTH-SMSERNO      =   "MU0"                       )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && "MU0".equals(kputhSmserno)) {
            // 033430       COMPUTE WK-TASK-FEECOST   =   15                         .
            wkTaskFeecost = new BigDecimal(15);
        }

        // 033450     IF    (  KPUTH-TXTYPE  = "K" OR ="L" OR ="N" OR ="O"        )
        // 033460       AND (  KPUTH-SMSERNO      =   "MV0"                       )
        if (("K".equals(kputhTxtype)
                        || "L".equals(kputhTxtype)
                        || "N".equals(kputhTxtype)
                        || "O".equals(kputhTxtype))
                && "MV0".equals(kputhSmserno)) {
            // 033470       COMPUTE WK-TASK-FEECOST   =   20                         .
            wkTaskFeecost = new BigDecimal(20);
        }

        // 033500     IF    (  KPUTH-TXTYPE  = "Q" OR ="T" OR ="Y" OR ="Z" OR ="2")
        if ("Q".equals(kputhTxtype)
                || "T".equals(kputhTxtype)
                || "Y".equals(kputhTxtype)
                || "Z".equals(kputhTxtype)
                || "2".equals(kputhTxtype)) {
            // 033600       COMPUTE WK-TASK-IPAL      =   0                          .
            wkTaskIpal = new BigDecimal(0);
        }

        // 033800     IF    (  KPUTH-TXTYPE       =   "U"                        )
        // 033900       AND (( KPUTH-AMT > 5  )   AND ( KPUTH-AMT < 101 )        )
        // 034000       AND (  KPUTH-SMSERNO      =   "198" OR "179"             )
        if ("U".equals(kputhTxtype)
                && (kputhAmt.compareTo(new BigDecimal(5)) > 0
                        && kputhAmt.compareTo(new BigDecimal(101)) < 0)
                && ("198".equals(kputhSmserno) || "179".equals(kputhSmserno))) {
            // 034100       COMPUTE WK-TASK-IPAL      =   5                          .
            wkTaskIpal = new BigDecimal(5);
        }

        // 034300     IF    (  KPUTH-TXTYPE       =   "U"                        )
        // 034400       AND (( KPUTH-AMT > 100 )  AND ( KPUTH-AMT < 1001)        )
        // 034500       AND (  KPUTH-SMSERNO      =   "198" OR "179"             )
        if ("U".equals(kputhTxtype)
                && (kputhAmt.compareTo(new BigDecimal(100)) > 0
                        && kputhAmt.compareTo(new BigDecimal(1001)) < 0)
                && ("198".equals(kputhSmserno) || "179".equals(kputhSmserno))) {
            // 034600       COMPUTE WK-TASK-IPAL      =   10                         .
            wkTaskIpal = new BigDecimal(10);
        }

        // 034800     IF    (  KPUTH-TXTYPE       =   "U"                        )
        // 034900       AND (( KPUTH-AMT > 1000)                                 )
        // 035000       AND (  KPUTH-SMSERNO      =   "198" OR "179"             )
        if ("U".equals(kputhTxtype)
                && kputhAmt.compareTo(new BigDecimal(1000)) > 0
                && ("198".equals(kputhSmserno) || "179".equals(kputhSmserno))) {
            // 035100       COMPUTE WK-TASK-IPAL      =   15                         .
            wkTaskIpal = new BigDecimal(15);
        }

        // 035300     IF    (  KPUTH-TXTYPE       =   "U"                        )
        // 035400       AND (( KPUTH-AMT >   6 )  AND ( KPUTH-AMT <20001)        )
        // 035500       AND (  KPUTH-SMSERNO      =   "501"                      )
        if ("U".equals(kputhTxtype)
                && (kputhAmt.compareTo(new BigDecimal(6)) > 0
                        && kputhAmt.compareTo(new BigDecimal(20001)) < 0)
                && "501".equals(kputhSmserno)) {
            // 035600       COMPUTE WK-TASK-IPAL      =   6                          .
            wkTaskIpal = new BigDecimal(6);
        }

        // 035800     IF    (  KPUTH-TXTYPE       =   "U"                        )
        // 035900       AND (( KPUTH-AMT >20000)                                 )
        // 036000       AND (  KPUTH-SMSERNO      =   "501"                      )
        if ("U".equals(kputhTxtype)
                && kputhAmt.compareTo(new BigDecimal(20000)) > 0
                && "501".equals(kputhSmserno)) {
            // 036100       COMPUTE WK-TASK-IPAL      =   15                         .
            wkTaskIpal = new BigDecimal(15);
        }

        // 036140     IF       KPUTH-TXTYPE       =   "3"
        if ("3".equals(kputhTxtype)) {
            // 036170       SUBTRACT WK-TASK-FEE     FROM WK-EFCSCOUNT
            // 036180                       GIVING WK-TASK-IPAL.
            wkTaskIpal = wkEfcscount.subtract(wkTaskFee);
        }
        // 036182*LINE PAY=4 , JKOS=7 , EASY CARD= 5 , ICASH=6 , PXPAY=8
        // 036183     IF      (KPUTH-TXTYPE  = "4" OR "5" OR "6" OR "7" OR "8" )
        // 036184     AND      KPUTH-SMSERNO(1:1) = "A"
        if (("4".equals(kputhTxtype)
                        || "5".equals(kputhTxtype)
                        || "6".equals(kputhTxtype)
                        || "7".equals(kputhTxtype)
                        || "8".equals(kputhTxtype))
                && "A".equals(kputhSmserno.substring(0, 1))) {
            // 036189       COMPUTE WK-TASK-IPAL      =   0 .
            wkTaskIpal = new BigDecimal(0);
        }
        // 036200* 兒少代收類別不收手續費
        // 036220     IF  KPUTH-CODE="191904"
        if ("191904".equals(kputhCode)) {
            // 036240       COMPUTE WK-TASK-IPAL      =   0                          .
            wkTaskIpal = new BigDecimal(0);
        }
        // 036300 1400-IPAL-EXIT.
    }

    private void fileDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVBAFLsnr fileDtl.....");
        // 011800 FILE-DTL-RTN.

        //// 搬相關資料給UPDBAF-REC...
        //// UPDBAF-TXTYPE帳務別
        // 011900       MOVE   SPACE              TO     UPDBAF-REC
        // 012000       MOVE   WK-PRIOR-CODE      TO     UPDBAF-CODE
        // 012100       MOVE   WK-PRIOR-PBRNO     TO     UPDBAF-PBRNO
        // 012200       MOVE   WK-DATE            TO     UPDBAF-UPDDATE
        // 012300       MOVE   "K"                TO     UPDBAF-TXTYPE
        // 012400       MOVE   WK-TOTAMT          TO     UPDBAF-AMT
        // 012420       MOVE   WK-TOTCNT          TO     UPDBAF-CNT
        // 012450       MOVE   WK-TOTFEE          TO     UPDBAF-FEE
        // 012490       MOVE   WK-TOTIPAL         TO     UPDBAF-IPAL
        String updbafCode = wkPriorCode;
        int updbafPbrno = wkPriorPbrno;
        int updbafUpddate = wkDate;
        String updbafTxtype = "K";
        BigDecimal updbafAmt = wkTotamt;
        int updbafCnt = wkTotcnt;
        BigDecimal updbafFee = wkTotfee;
        BigDecimal updbafIpal = wkTotipal;
        //// 寫檔FD-UPDBAF

        sb = new StringBuilder();
        // 012500       WRITE  UPDBAF-REC.
        // 01 UPDBAF-REC TOTAL 60 BYTES
        sb.append(formatUtil.padX(updbafCode, 6));
        sb.append(formatUtil.pad9("" + updbafPbrno, 3));
        sb.append(formatUtil.pad9("" + updbafFee, 6));
        sb.append(formatUtil.pad9("" + updbafCnt, 5));
        sb.append(formatUtil.pad9("" + updbafUpddate, 8));
        sb.append(formatUtil.padX(updbafTxtype, 1));
        sb.append(formatUtil.pad9("" + updbafAmt, 13));
        sb.append(formatUtil.pad9("" + updbafIpal, 6));
        sb.append(formatUtil.padX("", 12));

        fileUPDBAFContents.add(formatUtil.padX(sb.toString(), 60));
        // 012600
        // 012700 FILE-DTL-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
