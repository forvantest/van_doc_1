/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRECLBAF;
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
@Component("CRECLBAFLsnr")
@Scope("prototype")
public class CRECLBAFLsnr extends BatchListenerCase<CRECLBAF> {

    @Autowired private ClbafService clbafService;
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final String CHARSET = "UTF-8"; // 檔案編碼 產檔用
    private static final String FILE_OUTPUT_NAME = "CLBAF"; // 檔名
    private String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileCRECLBAFContents; //  檔案內容CRECLBAF
    private CRECLBAF event;
    private String wkYYMMDD = "";
    private String wkKdate = "";
    private String lmndy = "";
    private String tmndy = "";
    private String wkLmnYYMM = "";
    private String wkTmnYYMM = "";
    private String wkCname = "";
    private String fdClbafEntpno = "";
    private int fdClbafPbrno = 0;
    private HashMap<String, String> codeCnameMap = new HashMap<String, String>();
    private HashMap<String, String> codeEntpnoMap = new HashMap<String, String>();
    private HashMap<String, Integer> codePbrnoMap = new HashMap<String, Integer>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CRECLBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRECLBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAFLsnr run()");
        init(event);
        main();

        try {
            textFile.writeFileContent(outputFilePath, fileCRECLBAFContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAFLsnr main");
        // 006900 0000-MAIN-RTN.

        //// 將DB-CLBAF-IDX1指標移至開始
        // 007000     SET     DB-CLBAF-IDX1    TO     BEGINNING.
        // 007100 FIND-CLBAF-LOOP.

        //// FIND DB-CLBAF-DDS收付累計檔，
        //// 正常，執行下一步
        //// 若有誤
        ////  若NOTFOUND，異常，結束程式
        ////  其他，顯示訊息，異常，結束程式
        // 007200     FIND NEXT DB-CLBAF-IDX1
        List<ClbafbyCodeDateRangeBus> lClbaf = new ArrayList<>();
        List<ClbafbyCodeDateRangeBus> lClbafTmnDate =
                clbafService.findbyCodeDateRange(
                        999,
                        parse.string2Integer(wkTmnYYMM + "01"),
                        parse.string2Integer(wkTmnYYMM + "31"),
                        0,
                        Integer.MAX_VALUE);

        // 007300          ON  EXCEPTION  IF     DMSTATUS(NOTFOUND)
        // 007400                     GO  TO   0000-MAIN-EXIT
        // 007500          ELSE   DISPLAY "FIND DB-CLBAF-DDS FAIL!!!"
        // 007600                 CALL SYSTEM DMTERMINATE.
        // 007700     MOVE DB-CLBAF-DATE TO WK-CLBAF-DATE.
        if (wkLmnYYMM != wkTmnYYMM) {
            List<ClbafbyCodeDateRangeBus> lClbafLmnDate =
                    clbafService.findbyCodeDateRange(
                            999,
                            parse.string2Integer(wkLmnYYMM + "01"),
                            parse.string2Integer(wkLmnYYMM + "31"),
                            0,
                            Integer.MAX_VALUE);

            if (lClbafLmnDate != null) {
                lClbaf.addAll(lClbafLmnDate);
            }
        }
        if (lClbafTmnDate != null) {
            lClbaf.addAll(lClbafTmnDate);
        }
        //// 如果代收年月等於本月年月或上月年月和銀行別小於999,執行下一步
        //// 否則，跳到FIND-CLBAF-LOOP
        // 007800     IF (WK-CLBAF-DATEYYMM  = WK-TMNYYMM OR WK-LMNYYMM)
        // 007900     AND DB-CLBAF-CLLBR     < 999
        // 008000        CONTINUE
        // 008100     ELSE
        // 008200        GO TO FIND-CLBAF-LOOP
        // 008300     END-IF.
        // 008400
        if (lClbaf.size() == 0) {
            return;
        }
        for (ClbafbyCodeDateRangeBus tClbaf : lClbaf) {
            //// IF DB-CLBAF-CNT = 0,跳到FIND-CLBAF-LOOP

            // 008500     IF DB-CLBAF-CNT = 0   GO TO FIND-CLBAF-LOOP.
            if (tClbaf.getCnt() == 0) {
                continue;
            }
            //// 搬資料庫資料到CLBAF-REC
            // 008700     MOVE DB-CLBAF-DATE   TO FD-CLBAF-DATE .
            int fdClbafDate = tClbaf.getEntdy();
            // 008800     MOVE DB-CLBAF-CODE   TO FD-CLBAF-CODE.
            String fdClbafCode = tClbaf.getCode();
            // 008900     MOVE DB-CLBAF-CNT    TO FD-CLBAF-CNT  .
            int fdClbafCnt = tClbaf.getCnt();
            // 009000     MOVE DB-CLBAF-AMT    TO FD-CLBAF-AMT  .
            BigDecimal fdClbafAmt = tClbaf.getAmt();
            // 009100     MOVE DB-CLBAF-CLLBR  TO FD-CLBAF-CLLBR .
            int fdClbafCllbr = tClbaf.getCllbr();
            // 009200     MOVE DB-CLBAF-TXTYPE TO FD-CLBAF-TXTYPE.
            String fdClbafTxtype = tClbaf.getTxtype();

            // 讀CLMR搬資料

            // 009400     PERFORM FIND-CLMR-RTN   THRU FIND-CLMR-EXIT.
            findClmr(fdClbafCode);
            //// 如果統編為空白或者"00000000",跳下一筆
            fdClbafEntpno = codeEntpnoMap.get(fdClbafCode);
            // 009500     IF FD-CLBAF-ENTPNO = SPACES OR "00000000" OR "0"
            if (fdClbafEntpno.isEmpty()
                    || "00000000".equals(fdClbafEntpno)
                    || "0".equals(fdClbafEntpno)) {
                // 009600        GO TO FIND-CLBAF-LOOP.
                continue;
            }
            //// 設定AS-STRCHK-RTN變數

            // 009700     MOVE 1               TO WC-STRCHK-FUNCD.
            // 009800     MOVE WK-CNAME        TO WC-STRCHK-INSTR.
            // 009900     MOVE 40              TO WC-STRCHK-INLEN.
            // 010000     MOVE 22              TO WC-STRCHK-INOUTLEN.
            //// 轉全形文字
            // 010100     PERFORM AS-STRCHK-RTN   THRU  AS-STRCHK-EXIT.
            // 010200     IF      WC-STRCHK-RTNCD =     0
            // 010300        MOVE WC-STRCHK-OUTSTR      TO    FD-CLBAF-CNAME
            // 010400     ELSE
            // 010500        MOVE SPACES                TO    FD-CLBAF-CNAME.

            String fdCname = halfToFullWidth(codeCnameMap.get(fdClbafCode));
            //// 寫檔FD-CLBAF

            // 010700     WRITE     CLBAF-REC.
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
            sb.append(formatUtil.pad9("" + codePbrnoMap.get(fdClbafCode), 3));
            sb.append(formatUtil.padX(fdClbafEntpno, 8));
            sb.append(formatUtil.pad9("" + fdClbafDate, 8));
            sb.append(formatUtil.padX(fdClbafCode, 6));
            sb.append(formatUtil.pad9("" + fdClbafCnt, 6));
            sb.append(formatUtil.pad9("" + fdClbafAmt, 13));
            sb.append(formatUtil.pad9("" + fdClbafCllbr, 3));
            sb.append(formatUtil.padX(fdClbafTxtype, 1));
            sb.append(formatUtil.padX(fdCname, 22));
            sb.append(formatUtil.padX("", 2));
            fileCRECLBAFContents.add(formatUtil.padX(sb.toString(), 70));

            // 010800     GO TO FIND-CLBAF-LOOP.
        }
        // 011000 0000-MAIN-EXIT.
    }

    private void findClmr(String code) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAFLsnr findClmr");
        // 011600  FIND-CLMR-RTN.

        //// 將DB-CLMR-IDX1指標移至開始
        // 011700     SET     DB-CLMR-IDX1    TO     BEGINNING.

        //// 依代收類別讀取事業單位基本資料檔，
        //// 正常，執行下一步
        //// 若有誤，
        //// 若找不到，結束本段程式
        //// 否則，顯示錯誤訊息，結束程式
        if (!codeCnameMap.containsKey(code)) {
            // 011800     FIND DB-CLMR-IDX1
            // 011900     AT   DB-CLMR-CODE = DB-CLBAF-CODE
            ClmrBus tClmr = clmrService.findById(code);
            // 012000          ON  EXCEPTION  IF     DMSTATUS(NOTFOUND)
            if (Objects.isNull(tClmr)) {
                // 012100                     GO  TO   FIND-CLMR-EXIT
                return;
            }
            // 012200          ELSE   DISPLAY "FIND DB-CLMR-DDS FAIL!!!"
            // 012300                 CALL SYSTEM DMTERMINATE.
            CltmrBus tCltmr = cltmrService.findById(code);
            if (Objects.isNull(tCltmr)) {
                // 012100                     GO  TO   FIND-CLMR-EXIT
                return;
            }
            codeCnameMap.put(code, tClmr.getCname());
            codeEntpnoMap.put(code, tCltmr.getEntpno());
            codePbrnoMap.put(code, tClmr.getPbrno());
        }
        // 搬單位中文名、統一編號、主辦分行
        // 012800      MOVE DB-CLMR-CNAME   TO WK-CNAME       .
        // 012900      MOVE DB-CLMR-ENTPNO  TO FD-CLBAF-ENTPNO.
        // 013000      MOVE DB-CLMR-PBRNO   TO FD-CLBAF-PBRNO.

        // 013200 FIND-CLMR-EXIT.
    }

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

    private void init(CRECLBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRECLBAFLsnr init");
        this.event = event;
        //// 設定日期
        // 005400     MOVE    FD-BHDATE-TBSDY      TO     WK-YYMMDD WK-KDATE.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 批次日期(民國年yyyymmdd)
        wkYYMMDD = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        wkKdate = getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱
        // 005500     MOVE    FD-BHDATE-LMNYY      TO     WK-LMNYYMM(1:4).
        lmndy = getrocdate(event.getAggregateBuffer().getTxCom().getLmndy());
        wkLmnYYMM = lmndy.substring(0, 3);
        // 005600     MOVE    FD-BHDATE-LMNMM      TO     WK-LMNYYMM(5:2).
        wkLmnYYMM = wkLmnYYMM + lmndy.substring(3, 5);
        // 005700     MOVE    FD-BHDATE-TMNYY      TO     WK-TMNYYMM(1:4).
        tmndy = getrocdate(event.getAggregateBuffer().getTxCom().getTmndy());
        wkTmnYYMM = tmndy.substring(0, 3);
        // 005800     MOVE    FD-BHDATE-TMNMM      TO     WK-TMNYYMM(5:2).
        wkTmnYYMM = wkTmnYYMM + tmndy.substring(3, 5);

        //// 設定檔名
        // 005900     CHANGE ATTRIBUTE FILENAME OF FD-CLBAF TO WK-CLBAFDIR.
        outputFilePath = fileDir + FILE_OUTPUT_NAME + PATH_SEPARATOR + wkKdate;

        fileCRECLBAFContents = new ArrayList<>();
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
