/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRECLBAF2;
import com.bot.ncl.dto.entities.ClbafbyCodeDateRangeBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CRECLBAF2Lsnr")
@Scope("prototype")
public class CRECLBAF2Lsnr extends BatchListenerCase<CRECLBAF2> {

    @Autowired private ClbafService clbafService;
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final String CHARSET = "UTF-8"; // 檔案編碼 產檔用
    private static final String FILE_OUTPUT_NAME = "CLBAF2"; // 檔名
    private String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // 產檔路徑

    private StringBuilder sb = new StringBuilder();
    private List<String> fileCRECLBAF2Contents; //  檔案內容CRECLBAF
    @Autowired private Parse parse;
    private CRECLBAF2 event;
    private Boolean stopRun = false;
    private String wkYYMMDD;
    private String wkKdate;
    private String wkSpdate;
    private String fdClbafDate;
    private String wkLmnYYMMDD;
    private String wkTmnYYMMDD;
    private String wkTmnMM;
    private int wkYYYY;
    private int wkClbafDate;
    private String fdClbafEntpno = "";
    private String wkCname = "";
    private int fdClbafPbrno = 0;
    private HashMap<String, String> codeCnameMap = new HashMap<String, String>();
    private HashMap<String, String> codeEntpnoMap = new HashMap<String, String>();
    private HashMap<String, Integer> codePbrnoMap = new HashMap<String, Integer>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CRECLBAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAF2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRECLBAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAF2Lsnr run()");
        init(event);
        if (stopRun) {
            return;
        }
        main();

        try {
            textFile.writeFileContent(outputFilePath, fileCRECLBAF2Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAF2Lsnr main");
        // 009800 0000-MAIN-RTN.

        //// 將DB-CLBAF-IDX1指標移至開始
        // 009900     SET     DB-CLBAF-IDX1    TO     BEGINNING.
        // 010000 FIND-CLBAF-LOOP.

        //// FIND DB-CLBAF-DDS收付累計檔，
        //// 正常，執行下一步
        //// 若有誤
        ////  若NOTFOUND，異常，結束程式
        ////  其他，顯示訊息，異常，結束程式

        // 010100     FIND NEXT DB-CLBAF-IDX1
        // 010200          ON  EXCEPTION  IF     DMSTATUS(NOTFOUND)
        // 010300                     GO  TO   0000-MAIN-EXIT
        // 010400          ELSE   DISPLAY "FIND DB-CLBAF-DDS FAIL!!!"
        // 010500                 CALL SYSTEM DMTERMINATE.
        List<ClbafbyCodeDateRangeBus> lClbaf =
                clbafService.findbyCodeDateRange(
                        999, parse.string2Integer(wkLmnYYMMDD) + 1, 9991231, 0, Integer.MAX_VALUE);

        if (Objects.isNull(lClbaf)) {
            return;
        }
        for (ClbafbyCodeDateRangeBus tClbaf : lClbaf) {
            //// 搬代收日資料
            // 010600     MOVE DB-CLBAF-DATE TO WK-CLBAF-DATE.
            wkClbafDate = tClbaf.getEntdy();
            //// 如果代收日大於上個月月底和銀行別小於999，執行下一步
            //// 否則，跳到FIND-CLBAF-LOOP

            // 以下判斷為service的select條件
            // 010700     IF  DB-CLBAF-DATE      > WK-LMNYYMMDD
            // 010800     AND DB-CLBAF-CLLBR     < 999
            // 010900        CONTINUE
            // 011000     ELSE
            // 011100        GO TO FIND-CLBAF-LOOP
            // 011200     END-IF.

            // 011400     IF DB-CLBAF-CNT = 0  OR DB-CLBAF-DATE  > WK-TMNYYMMDD
            if (tClbaf.getCnt() == 0 || tClbaf.getEntdy() > parse.string2Integer(wkTmnYYMMDD)) {
                // 011500        GO TO FIND-CLBAF-LOOP.
                continue;
            }
            // 011600* 上半年統計加總且下半年總計加總，全部算入六月或 12 月底
            // 011700
            // 011800     IF DB-CLBAF-DATE > WK-SPDATE
            if (tClbaf.getEntdy() > parse.string2Integer(wkSpdate)) {
                // 011900        MOVE FD-BHDATE-TBSDY TO FD-CLBAF-DATE
                fdClbafDate = wkYYMMDD;
                // 012000     ELSE
            } else {
                // 012100        MOVE WK-SPDATE       TO FD-CLBAF-DATE
                fdClbafDate = wkSpdate;
                // 012200     END-IF.
            }
            // 012300

            //// 搬資料庫資料到CLBAF-REC
            // 012500     MOVE DB-CLBAF-CODE   TO FD-CLBAF-CODE.
            String fdClbafCode = tClbaf.getCode();
            // 012600     MOVE DB-CLBAF-CNT    TO FD-CLBAF-CNT  .
            int fdClbafCnt = tClbaf.getCnt();
            // 012700     MOVE DB-CLBAF-AMT    TO FD-CLBAF-AMT  .
            BigDecimal fdClbafAmt = tClbaf.getAmt();
            // 012800     MOVE DB-CLBAF-CLLBR  TO FD-CLBAF-CLLBR .
            int fdClbafCllbr = tClbaf.getCllbr();
            // 012900     MOVE DB-CLBAF-TXTYPE TO FD-CLBAF-TXTYPE.
            String fdClbafTxtype = tClbaf.getTxtype();
            // 013000

            //// 讀CLMR搬資料
            // 013100     PERFORM FIND-CLMR-RTN   THRU FIND-CLMR-EXIT.
            findClmr(tClbaf.getCode());
            //
            //// 如果統編為空白或者"00000000",跳下一筆
            //
            // 013200     IF FD-CLBAF-ENTPNO = SPACES OR "00000000" OR "0"
            if (fdClbafEntpno.isEmpty()
                    || "00000000".equals(fdClbafEntpno)
                    || "0".equals(fdClbafEntpno)) {
                // 013300        GO TO FIND-CLBAF-LOOP.
                continue;
            }
            // 013400* 目前只撈三個統編資料
            // 013500     IF FD-CLBAF-ENTPNO = "70565326" OR "80129529" OR "97025978"
            if ("70565326".equals(fdClbafEntpno)
                    || "80129529".equals(fdClbafEntpno)
                    || "97025978".equals(fdClbafEntpno)) {
                // 013550*****                  OR "66966073"
                // 013600        CONTINUE
                // 013700     ELSE
                // 013800        GO TO FIND-CLBAF-LOOP.
                continue;
            }
            // 013900

            //// 設定AS-STRCHK-RTN變數
            // 014000     MOVE 1               TO WC-STRCHK-FUNCD.
            // 014100     MOVE WK-CNAME        TO WC-STRCHK-INSTR.
            // 014200     MOVE 40              TO WC-STRCHK-INLEN.
            // 014300     MOVE 22              TO WC-STRCHK-INOUTLEN.
            //// 轉全形文字
            // 014400     PERFORM AS-STRCHK-RTN   THRU  AS-STRCHK-EXIT.
            // 014500     IF      WC-STRCHK-RTNCD =     0
            // 014600        MOVE WC-STRCHK-OUTSTR      TO    FD-CLBAF-CNAME
            // 014700     ELSE
            // 014800        MOVE SPACES                TO    FD-CLBAF-CNAME.
            // 014900
            String fdCname = halfToFullWidth(wkCname);
            //
            //// 寫檔FD-CLBAF
            //
            // 015000     WRITE     CLBAF-REC.
            // 01 CLBAF-REC TOTAL 70 BYTES
            //  03  FD-CLBAF-PBRNO	9(03)	主辦分行
            //  03  FD-CLBAF-ENTPNO	X(08)	統一編號
            //  03  FD-CLBAF-DATE	9(08)	代收日
            //  03  FD-CLBAF-CODE	X(06)	代收類別
            //  03  FD-CLBAF-CNT	9(06)	該業務在當日之累計代收筆數
            //  03  FD-CLBAF-AMT	9(13)	該業務在當日之累計代收金額
            //  03  FD-CLBAF-CLLBR	9(03)	代收行
            //  03  FD-CLBAF-TXTYPE	X(01)	交易類型
            //  03  FD-CLBAF-CNAME	X(22)	單位中文名
            //  03  FD-CLBAF-FILLER1	X(02)
            sb = new StringBuilder();
            sb.append(formatUtil.pad9("" + fdClbafPbrno, 3));
            sb.append(formatUtil.padX(fdClbafEntpno, 8));
            sb.append(formatUtil.pad9(fdClbafDate, 8));
            sb.append(formatUtil.padX(fdClbafCode, 6));
            sb.append(formatUtil.pad9("" + fdClbafCnt, 6));
            sb.append(formatUtil.pad9("" + fdClbafAmt, 13));
            sb.append(formatUtil.pad9("" + fdClbafCllbr, 3));
            sb.append(formatUtil.padX(fdClbafTxtype, 1));
            sb.append(formatUtil.padX(fdCname, 22));
            sb.append(formatUtil.padX("", 2));
            fileCRECLBAF2Contents.add(formatUtil.padX(sb.toString(), 70));
            // 015100     GO TO FIND-CLBAF-LOOP.
        }

        // 015300 0000-MAIN-EXIT.
    }

    private void findClmr(String code) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAF2Lsnr findClmr");
        // 015900  FIND-CLMR-RTN.

        //// 將DB-CLMR-IDX1指標移至開始
        // 016000     SET     DB-CLMR-IDX1    TO     BEGINNING.

        //// 依代收類別讀取事業單位基本資料檔，
        //// 正常，執行下一步
        //// 若有誤，
        //// 若找不到，結束本段程式
        //// 否則，顯示錯誤訊息，結束程式
        if (!codeEntpnoMap.containsKey(code)) {
            // 016100     FIND DB-CLMR-IDX1
            // 016200     AT   DB-CLMR-CODE = DB-CLBAF-CODE
            ClmrBus tClmr = clmrService.findById(code);
            // 016300          ON  EXCEPTION  IF     DMSTATUS(NOTFOUND)
            if (Objects.isNull(tClmr)) {
                // 016400                     GO  TO   FIND-CLMR-EXIT
                return;
            }

            CltmrBus tCltmr = cltmrService.findById(code);
            if (Objects.isNull(tCltmr)) {
                return;
            }
            // 016500          ELSE   DISPLAY "FIND DB-CLMR-DDS FAIL!!!"
            // 016600                 CALL SYSTEM DMTERMINATE.
            codeEntpnoMap.put(code, tCltmr.getEntpno());
            codeCnameMap.put(code, tClmr.getCname());
            codePbrnoMap.put(code, tClmr.getPbrno());
        }
        //// 搬單位中文名、統一編號、主辦分行

        // 017100      MOVE DB-CLMR-CNAME   TO WK-CNAME       .
        wkCname = codeCnameMap.get(code);
        // 017200      MOVE DB-CLMR-ENTPNO  TO FD-CLBAF-ENTPNO.
        fdClbafEntpno = codeEntpnoMap.get(code);
        // 017300      MOVE DB-CLMR-PBRNO   TO FD-CLBAF-PBRNO.
        fdClbafPbrno = codePbrnoMap.get(code);
        // 017400
        // 017500 FIND-CLMR-EXIT.
    }

    private void init(CRECLBAF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAF2Lsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        //// 設定日期
        // 005700     MOVE    FD-BHDATE-TBSDY      TO     WK-YYMMDD WK-KDATE.
        wkYYMMDD = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        wkKdate = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        // 005800     MOVE    FD-BHDATE-LMNYY      TO     WK-LMNYYMMDD(1:4).
        // 005900     MOVE    FD-BHDATE-LMNMM      TO     WK-LMNYYMMDD(5:2).
        // 006000     MOVE    FD-BHDATE-LMNDD      TO     WK-LMNYYMMDD(7:2).
        wkLmnYYMMDD = getrocdate(event.getAggregateBuffer().getTxCom().getLmndy());
        // 006100     MOVE    FD-BHDATE-TMNYY      TO     WK-TMNYYMMDD(1:4).
        // 006200     MOVE    FD-BHDATE-TMNMM      TO     WK-TMNYYMMDD(5:2).
        // 006300     MOVE    FD-BHDATE-TMNDD      TO     WK-TMNYYMMDD(7:2).
        wkTmnYYMMDD = getrocdate(event.getAggregateBuffer().getTxCom().getTmndy());
        wkTmnMM = wkTmnYYMMDD.substring(3, 5);
        // 006400     MOVE    FD-BHDATE-TBSDY      TO     WK-SPDATE.
        wkSpdate = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        // 006500**   DISPLAY "FD-BHDATE-TBSDY =" FD-BHDATE-TBSDY.
        // 006600**   DISPLAY "FD-BHDATE-TMNDY =" FD-BHDATE-TMNDY.
        //
        //// 今年6月挑去年0630到1231
        //// 今年12月挑今年0101到0630
        // 006700     IF      FD-BHDATE-TBSDY      =  FD-BHDATE-TMNDY
        if (wkYYMMDD.equals(wkTmnYYMMDD)) {
            // 006800       IF    FD-BHDATE-TMNMM      =      06
            if ("06".equals(wkTmnMM)) {
                // 006900             MOVE   FD-BHDATE-LMNYY      TO  WK-YYYY
                wkYYYY = parse.string2Integer(wkLmnYYMMDD.substring(0, 3));
                // 007000             COMPUTE WK-YYYY      =      WK-YYYY - 1
                wkYYYY = wkYYYY - 1;
                // 007100             MOVE    WK-YYYY      TO     WK-LMNYYMMDD(1:4)
                // 007200             MOVE    0630         TO     WK-LMNYYMMDD(5:4)
                wkLmnYYMMDD = wkYYYY + "0630";
                // 007300             MOVE    WK-YYYY      TO     WK-SPDATE(1:4)
                // 007400             MOVE    1231         TO     WK-SPDATE(5:4)
                wkSpdate = wkYYYY + "1231";
                // 007500       END-IF
            }
            // 007600       IF    FD-BHDATE-TMNMM      =      12
            if ("12".equals(wkTmnMM)) {
                // 007700             MOVE   0101          TO     WK-LMNYYMMDD(5:4)
                wkLmnYYMMDD = wkLmnYYMMDD.substring(0, 3) + "0101";
                // 007800             MOVE   0630          TO     WK-SPDATE(5:4)
                wkSpdate = wkSpdate.substring(0, 3) + "0630";
                // 007900       END-IF
            }
            // 008000     END-IF.
        }
        //// 本營業日不等於本月月底或本月不是6和12，直接結束程式
        // 008400     IF   FD-BHDATE-TBSDY    NOT=  FD-BHDATE-TMNDY
        // 008500     OR  (FD-BHDATE-TMNMM NOT = 06 AND 12)

        if (!wkYYMMDD.equals(wkTmnYYMMDD) || !("06".equals(wkTmnMM) || "12".equals(wkTmnMM))) {
            // 008600       STOP RUN.
            stopRun = true;
            return;
        }
        //// 設定檔名
        // 008800     CHANGE ATTRIBUTE FILENAME OF FD-CLBAF TO WK-CLBAFDIR.
        outputFilePath = fileDir + FILE_OUTPUT_NAME + PATH_SEPARATOR + wkKdate + ".";

        fileCRECLBAF2Contents = new ArrayList<>();
    }

    // 轉全形文字
    private String halfToFullWidth(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 0x0021 && c <= 0x007E) { // 檢查是否為半形字符的 Unicode 範圍
                sb.append((char) (c + 0xFEE0)); // 將半形字符轉換為全形字符
            } else {
                sb.append(c); // 如果不是半形字符，則保留原字符
            }
        }
        return sb.toString();
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
