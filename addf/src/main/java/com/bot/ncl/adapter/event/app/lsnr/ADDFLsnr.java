/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.ADDF;
import com.bot.ncl.adapter.in.svc.ADDF__I;
import com.bot.ncl.adapter.out.svc.ADDF_S000_O;
import com.bot.ncl.dto.entities.CldtlbyCodeListRangeEntdyBus;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmcService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ADDFLsnr")
@Scope("prototype")
public class ADDFLsnr extends BatchListenerCase<ADDF> {

    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private CldtlService cldtlService;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;

    private ADDF event;
    private ADDF__I xputftita;
    private ClmrBus tClmr;
    private CltmrBus tCltmr;
    private ClmcBus tClmc;

    @Autowired
    @Qualifier("ADDF.S000.O") private ADDF_S000_O addf_S000_O;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String FILE_OUTPUT_NAME = "ADDF"; // 檔名
    private String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // 產檔路徑

    private StringBuilder sb = new StringBuilder();
    private List<String> fileADDFContents; //  檔案內容

    private List<String> codeList = new ArrayList<>();
    private String wkTaskCode = "";
    private String wkTaskCodeX = "";
    private int wkRtn = 0;
    private int wkTaskBdate = 0;
    private int wkTaskEdate = 0;
    private int wkTaskBdateN = 0;
    private int wkTaskEdateN = 0;
    private int wkTotcnt;
    private BigDecimal wkTotamt;
    private int wkFdate = 0;
    private int wkPuttype = 0;
    private String wkPutname = "";
    private String wkPutfile = "";
    private String wkTaskPutfile = "";
    private int wkTaskPbrno = 0;
    private String addfCtl = "";
    private String addfCode = "";
    private BigDecimal addfTotamt = BigDecimal.ZERO;
    private int addfTotcnt = 0;
    private int addfEdate = 0;
    private int addfBdate = 0;
    private String addfRcptid = "";
    private int addfDate = 0;
    private int addfTime = 0;
    private BigDecimal addfOldamt = BigDecimal.ZERO;
    private int addfCllbr = 0;
    private int addfLmtdate = 0;
    private String addfUserdata = "";
    private int addfSitdate = 0;
    private String addfTxtype = "";
    private BigDecimal addfAmt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(ADDF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(ADDF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr run()");
        init(event);

        //// 執行0000-MAIN-RTN，主程式
        // 009300     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.
        mainRtn();
        //// 關閉檔案BOTSRDB
        //
        // 009600     CLOSE   BOTSRDB.
        //
        //// WK-RTN挑檔記號(0.挑檔 1.不挑檔)
        //// 若WK-RTN=0，關閉檔案FD-ADDF
        //
        // 009650     IF      WK-RTN         =       0
        if (wkRtn == 0) {
            // 009700       CLOSE   FD-ADDF    WITH SAVE.
            try {
                textFile.writeFileContent(outputFilePath, fileADDFContents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        //// 結束程式
        //
        // 009800     STOP RUN.

        // 初始化 TOTA
        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "initial tota ...");
        addf_S000_O.getLabel().initLabel(xputftita);

        // 設定 TOTA
        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "setting tota ...");
        addf_S000_O.getLabel().setMType("S");
        addf_S000_O.getLabel().setMsgNo("000");
        addf_S000_O.setPutfile(wkTaskPutfile);
        addf_S000_O.setPbrno("" + wkTaskPbrno);

        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "return tota ...");
        event.getResponseCases().add(addf_S000_O);
    }

    private void init(ADDF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr init");
        this.event = event;
        fileADDFContents = new ArrayList<>();
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        // REMOVE  DATA/CL/BH/ADDF/=
        textFile.deleteDir(fileDir + "ADDF");

        // A.接收參數:WK-TASK-CODE  X(06)                 代收類別
        //            WK-TASK-BDATE 9(07) BINARY EXTENDED 起日
        //            WK-TASK-EDATE 9(07) BINARY EXTENDED 迄日
        //   回傳參數:WK-TASK-PUTFILE X(10) PUTTYPE+PUTNAME
        //            WK-TASK-PBRNO   X(03) 主辦行
        xputftita = this.event.getAddf__I();
        // TODO:此交易有TOTA

        wkTaskCode = textMap.get("CODE"); // TODO: 待確認BATCH參數名稱
        wkTaskBdate = parse.string2Integer(textMap.get("BDATE")); // TODO: 待確認BATCH參數名稱
        wkTaskEdate = parse.string2Integer(textMap.get("EDATE")); // TODO: 待確認BATCH參數名稱
        //// 搬接收參數給WK-...
        //
        // 008900     MOVE    WK-TASK-BDATE  TO      WK-TASK-BDATE-N.
        wkTaskBdateN = wkTaskBdate;
        // 009000     MOVE    WK-TASK-EDATE  TO      WK-TASK-EDATE-N.
        wkTaskEdateN = wkTaskEdate;
        // 009100     MOVE    WK-TASK-CODE   TO      WK-TASK-CODE-X.
        wkTaskCodeX = wkTaskCode;

        //// 筆數、金額清0
        // 009200     MOVE    0              TO      WK-TOTCNT,WK-TOTAMT.
        wkTotcnt = 0;
        wkTotamt = new BigDecimal(0);
    }

    private void findclmrRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr findclmrRtn");
        // 015800 5000-FINDCLMR-RTN.

        //// WK-RTN挑檔記號(0.挑檔 1.不挑檔)

        //// 依接收之代收類別FIND DB-CLMR-IDX1事業單位基本資料檔，若有誤
        ////  若NOTFOUND：A.顯示錯誤訊息、B.ACCEPT???、C.設定WK-RTN為1、D.結束本段落
        ////  否則CALL SYSTEM DMTERMINATE，異常，結束程式

        // 015900     FIND      DB-CLMR-IDX1      AT  DB-CLMR-CODE=WK-TASK-CODE-X
        tClmr = clmrService.findById(wkTaskCodeX);
        // 016000      ON EXCEPTION  IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(tClmr)) {
            // 016100                       DISPLAY "FINDCLMR NOT FOUND"
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FINDCLMR NOT FOUND");
            // 016200                       ACCEPT  NOT-FIND-THAT-CLMR-CODE
            // 016300                       MOVE    1   TO      WK-RTN
            // 016400                       GO TO   5000-FINDCLMR-EXIT
            // 016500                    ELSE
            // 016600                       CALL SYSTEM DMTERMINATE.
            wkRtn = 1;
            return;
        }
        tCltmr = cltmrService.findById(tClmr.getCode());
        if (Objects.isNull(tCltmr)) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "cltmr is null");
            wkRtn = 1;
            return;
        }
        tClmc = clmcService.findById(tCltmr.getPutname());
        if (Objects.isNull(tClmc)) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "clmc is null, cltmr.getPutname() = {}",
                    tClmc.getPutname());
            wkRtn = 1;
            return;
        }
        //// 若上次CYC1挑檔日<接收參數-迄日
        ////  若 以指定日期要媒體(銷帳媒體產生週期=0 & 銷帳媒體產生週期日=0) & (指定挑檔日>=接收參數-迄日 or 臨時挑檔日>=接收參數-迄日)
        ////     WK-RTN設為0
        ////  其他，A.顯示訊息、B.ACCEPT、C.WK-RTN設為1
        //// 若上次CYC1挑檔日>=接收參數-迄日，WK-RTN設為0
        //
        // 016700     IF        DB-CLMR-LPUTDT    <       WK-TASK-EDATE-N
        if (tCltmr.getLputdt() < wkTaskEdateN) {
            // 016710       IF      DB-CLMR-CYCK1     =       0
            // 016720           AND DB-CLMR-CYCNO1    =       0
            // 016730           AND (DB-CLMR-PUTDT    NOT <   WK-TASK-EDATE-N
            // 016740            OR  DB-CLMR-TPUTDT   NOT <   WK-TASK-EDATE-N)
            if (tClmc.getCyck1() == 0
                    && tClmc.getCycno1() == 0
                    && (tClmc.getPutdt() >= wkTaskEdateN || tClmc.getTputdt() >= wkTaskEdateN)) {
                // 016750         MOVE  0                 TO      WK-RTN
                wkRtn = 0;
            } else {
                // 016760       ELSE
                // 016800         DISPLAY "LPUTDT < END-DATE"
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "LPUTDT < END-DATE");
                // 016900         ACCEPT  EDATE-CANT-GRATER-THAN-LPUTDT
                // 017000         MOVE  1                 TO      WK-RTN
                wkRtn = 1;
            }
        } else {
            // 017100     ELSE
            // 017200       MOVE    0                 TO      WK-RTN.
            wkRtn = 0;
        }
        // 017300 5000-FINDCLMR-EXIT.
    }

    private void waddf1Rtn(CldtlbyCodeListRangeEntdyBus tCldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr mainRtn");
        // 014400 4000-WADDF1-RTN.
        //
        //// 搬DB-CLDTL-...給ADDF-REC...
        //// 寫檔FD-ADDF(明細 CTL=21)
        // 014500     MOVE      SPACES            TO      ADDF-REC.
        // 014600     MOVE      21                TO      ADDF-CTL.
        addfCtl = "21";
        // 014700     MOVE      DB-CLDTL-CODE     TO      ADDF-CODE.
        addfCode = tCldtl.getCode();
        // 014800     MOVE      DB-CLDTL-RCPTID   TO      ADDF-RCPTID.
        addfRcptid = tCldtl.getRcptid();
        // 014900     MOVE      DB-CLDTL-DATE     TO      ADDF-DATE.
        addfDate = tCldtl.getEntdy();
        // 015000     MOVE      DB-CLDTL-TIME     TO      ADDF-TIME.
        addfTime = tCldtl.getTime();
        // 015100     MOVE      DB-CLDTL-AMT      TO      ADDF-OLDAMT.
        addfOldamt = tCldtl.getAmt();
        // 015200     MOVE      DB-CLDTL-CLLBR    TO      ADDF-CLLBR.
        addfCllbr = tCldtl.getCllbr();
        // 015300     MOVE      DB-CLDTL-LMTDATE  TO      ADDF-LMTDATE.
        addfLmtdate = tCldtl.getLmtdate();
        // 015400     MOVE      DB-CLDTL-USERDATA TO      ADDF-USERDATA.
        addfUserdata = tCldtl.getUserdata();
        // 015450     MOVE      DB-CLDTL-SITDATE  TO      ADDF-SITDATE.
        addfSitdate = tCldtl.getSitdate();
        // 015460     MOVE      DB-CLDTL-TXTYPE   TO      ADDF-TXTYPE.
        addfTxtype = tCldtl.getTxtype();
        // 015480     MOVE      DB-CLDTL-AMT      TO      ADDF-AMT.
        addfAmt = tCldtl.getAmt();
        // 015500     WRITE     ADDF-REC.
        // 002200 01  ADDF-REC.
        // 002300     03  ADDF-CTL                         PIC 9(02).
        // 002400     03  ADDF-CODE                        PIC X(06).
        // 002500     03  ADDF-DATA.
        // 002600      05 ADDF-RCPTID                      PIC X(16).
        // 002700      05 ADDF-DATE                        PIC 9(06).
        // 002800      05 ADDF-TIME                        PIC 9(06).
        // 002900      05 ADDF-CLLBR                       PIC 9(03).
        // 003000      05 ADDF-LMTDATE                     PIC 9(06).
        // 003100      05 ADDF-OLDAMT                      PIC 9(08).
        // 003200      05 ADDF-USERDATA                    PIC X(40).
        // 003250      05 ADDF-SITDATE                     PIC 9(06).
        // 003260      05 ADDF-TXTYPE                      PIC X(01).
        // 003280      05 ADDF-AMT                         PIC 9(10).
        // 003300      05 FILLER                           PIC X(10).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(addfCtl, 2));
        sb.append(formatUtil.padX(addfCode, 6));
        sb.append(formatUtil.padX(addfRcptid, 16));
        sb.append(formatUtil.pad9("" + addfDate, 6));
        sb.append(formatUtil.pad9("" + addfTime, 6));
        sb.append(formatUtil.pad9("" + addfCllbr, 3));
        sb.append(formatUtil.pad9("" + addfLmtdate, 6));
        sb.append(formatUtil.pad9("" + addfOldamt, 8));
        sb.append(formatUtil.padX(addfUserdata, 40));
        sb.append(formatUtil.pad9("" + addfSitdate, 6));
        sb.append(formatUtil.padX(addfTxtype, 1));
        sb.append(formatUtil.pad9("" + addfAmt, 10));
        sb.append(formatUtil.padX("", 10));
        fileADDFContents.add(sb.toString());

        // 015600 4000-WADDF1-EXIT.
    }

    private void waddf2Rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr waddf2Rtn");
        // 013300 3000-WADDF2-RTN.
        //// 搬相關欄位給ADDF-REC...
        //// 寫檔FD-ADDF(彙總 CTL=22)
        // 013400     MOVE      SPACES            TO      ADDF-REC.
        // 013500     MOVE      22                TO      ADDF-CTL.
        addfCtl = "22";
        // 013600     MOVE      WK-TASK-CODE-X    TO      ADDF-CODE.
        addfCode = wkTaskCodeX;
        // 013700     MOVE      WK-TASK-BDATE-N   TO      ADDF-BDATE.
        addfBdate = wkTaskBdateN;
        // 013800     MOVE      WK-TASK-EDATE-N   TO      ADDF-EDATE.
        addfEdate = wkTaskEdateN;
        // 013900     MOVE      WK-TOTCNT         TO      ADDF-TOTCNT.
        addfTotcnt = wkTotcnt;
        // 014000     MOVE      WK-TOTAMT         TO      ADDF-TOTAMT.
        addfTotamt = wkTotamt;
        // 014100     WRITE     ADDF-REC.
        // 002200 01  ADDF-REC.
        // 002300     03  ADDF-CTL                         PIC 9(02).
        // 002400     03  ADDF-CODE                        PIC X(06).
        // 002500     03  ADDF-DATA.
        // 003400     03  ADDF-DATA-R       REDEFINES  ADDF-DATA.
        // 003500      05 ADDF-BDATE                       PIC 9(06).
        // 003600      05 ADDF-EDATE                       PIC 9(06).
        // 003700      05 ADDF-TOTCNT                      PIC 9(06).
        // 003800      05 ADDF-TOTAMT                      PIC 9(13).
        // 003900      05 FILLER                           PIC X(81).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(addfCtl, 2));
        sb.append(formatUtil.padX(addfCode, 6));
        sb.append(formatUtil.pad9("" + addfBdate, 6));
        sb.append(formatUtil.pad9("" + addfEdate, 6));
        sb.append(formatUtil.pad9("" + addfTotcnt, 6));
        sb.append(formatUtil.pad9("" + addfTotamt, 13));
        sb.append(formatUtil.padX("", 81));
        fileADDFContents.add(sb.toString());

        // 014200 3000-WADDF2-EXIT.
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ADDFLsnr mainRtn");
        // 010000 0000-MAIN-RTN.

        //// 執行5000-FINDCLMR-RTN，依接收之代收類別FIND DB-CLMR-IDX1事業單位基本資料檔，設定WK-RTN
        // 010100     PERFORM 5000-FINDCLMR-RTN  THRU  5000-FINDCLMR-EXIT.
        findclmrRtn();
        //// WK-RTN挑檔記號(0.挑檔 1.不挑檔)
        //// 若WK-RTN=1，結束本段落
        //
        // 010200     IF      WK-RTN           =       1
        if (wkRtn == 1) {
            // 010300         GO TO  0000-MAIN-EXIT.
            return;
        }
        //
        //// 設定FD-ADDF檔名變數值
        ////  WK-PUTDIR="DATA/CL/BH/ADDF/"+WK-FDATE+"/"+WK-PUTFILE+".".
        //// 搬DB-CLMR-...給輸出參數
        //
        // 010400     MOVE    WK-TASK-EDATE-N     TO   WK-FDATE.
        wkFdate = wkTaskEdateN;
        // 010500     MOVE    DB-CLMR-PUTTYPE     TO   WK-PUTTYPE.
        wkPuttype = tClmc.getPuttype();
        // 010600     MOVE    DB-CLMR-PUTNAME     TO   WK-PUTNAME.
        wkPutname = tClmc.getPutname();
        wkPutfile = formatUtil.padX("" + wkPuttype, 2) + formatUtil.padX(wkPutname, 8);
        // 010620     MOVE    WK-PUTFILE          TO   WK-TASK-PUTFILE.
        wkTaskPutfile = wkPutfile;
        // 010640     MOVE    DB-CLMR-PBRNO       TO   WK-TASK-PBRNO.
        wkTaskPbrno = tClmr.getPbrno();

        //// 設定FD-ADDF檔名
        // 010700     CHANGE  ATTRIBUTE FILENAME OF FD-ADDF TO WK-PUTDIR.
        outputFilePath =
                fileDir
                        + FILE_OUTPUT_NAME
                        + PATH_SEPARATOR
                        + formatUtil.pad9("" + wkFdate, 6)
                        + PATH_SEPARATOR
                        + wkPutfile
                        + ".";

        //// 開新檔FD-ADDF
        // 010800     OPEN    OUTPUT    FD-ADDF.

        //// 將DB-CLDTL-IDX3指標移至開始
        //
        // 010850     SET   DB-CLDTL-IDX3  TO    BEGINNING.
        // 010900 0000-FINDCLDTL-LOOP.

        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 接收參數-代收類別&代收日>=接收參數-起日&代收日<=接收參數-迄日 FIND NEXT 收付明細檔，若有誤
        ////  若找不到
        ////    若WK-TOTCNT=0，A.顯示訊息、B.ACCEPT??? 、C.結束本段落
        ////    若WK-TOTCNT>0，A.執行3000-WADDF2-RTN，寫檔FD-ADDF(彙總 CTL=22)、B.結束本段落
        ////  其他資料庫錯誤，A.顯示錯誤訊息、B.ACCEPT??? 、C.結束本段落
        //
        // 011000     FIND  NEXT  DB-CLDTL-IDX3  AT DB-CLDTL-CODE = WK-TASK-CODE-X
        // 011100                          AND DB-CLDTL-DATE NOT < WK-TASK-BDATE-N
        // 011200                          AND DB-CLDTL-DATE NOT > WK-TASK-EDATE-N
        codeList.add(wkTaskCodeX);
        List<CldtlbyCodeListRangeEntdyBus> lCldtl =
                cldtlService.findbyCodeListRangeEntdy(
                        codeList, wkTaskBdateN, wkTaskEdateN, 0, 0, Integer.MAX_VALUE);
        // 011300       ON EXCEPTION
        if (Objects.isNull(lCldtl)) {
            // 011400         IF  DMSTATUS(NOTFOUND)
            // 011500           IF WK-TOTCNT  =    0
            // 011600              DISPLAY "THERE IS NO DATA AT THAT RANGE"
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "THERE IS NO DATA AT THAT RANGE");
            // 011700              ACCEPT  THERE-IS-NO-DATA-AT-THAT-RANGE
            // 011800              GO TO   0000-MAIN-EXIT
            return;
        }
        int cnt = 0;
        for (CldtlbyCodeListRangeEntdyBus tCldtl : lCldtl) {
            cnt++;
            //// 執行4000-WADDF1-RTN，A.搬DB-CLDTL-...給ADDF-REC...、B.寫檔FD-ADDF(明細 CTL=21)
            // 012600     PERFORM     4000-WADDF1-RTN     THRU   4000-WADDF1-EXIT.
            waddf1Rtn(tCldtl);
            //// 累計筆數、金額
            // 012700     ADD         DB-CLDTL-AMT        TO     WK-TOTAMT.
            wkTotamt = wkTotamt.add(tCldtl.getAmt());
            // 012800     ADD         1                   TO     WK-TOTCNT.
            wkTotcnt = wkTotcnt + 1;

            // 尾筆處理
            if (cnt == lCldtl.size()) {
                // 012000              PERFORM 3000-WADDF2-RTN    THRU 3000-WADDF2-EXIT
                waddf2Rtn();
                // 012100              GO TO   0000-MAIN-EXIT
            }
            //// LOOP讀下一筆CLDTL
            // 012900     GO TO       0000-FINDCLDTL-LOOP.
        }
        // 013000 0000-MAIN-EXIT.
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
