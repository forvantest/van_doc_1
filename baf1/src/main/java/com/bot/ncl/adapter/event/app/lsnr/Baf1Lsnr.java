/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Baf1;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.jpa.svc.CltotService;
import com.bot.ncl.mapper.ClbafMapper;
import com.bot.ncl.mapper.CltotMapper;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Baf1Lsnr")
@Scope("prototype")
public class Baf1Lsnr extends BatchListenerCase<Baf1> {

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    // 查詢分行控制檔
    private Bctl bctl;
    private Baf1 event;
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private CltotService cltotService;
    @Autowired private ClbafService clbafService;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    ClbafMapper clbafMapper = Mappers.getMapper(ClbafMapper.class);
    CltotMapper cltotMapper = Mappers.getMapper(CltotMapper.class);
    private List<String> fileBINB36Contents;
    private List<String> file001Contents;
    private List<String> sortContents;
    private String processDate;
    private int wkYYYMMDD;
    private String wkYYY;
    private String wkMM;
    private String wkDD;
    private int wkLYYYMMDD;
    private String tbsdy;

    private int cllbr;

    private static final String FILE_BINB36_NAME = "BINB36";
    private static final String FILE_001_NAME = "CL-BH-001";
    private static final String FILE_SORT_NAME = "SORTBAF1";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";

    private static final String FILE_DIR = "DLY/";

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private String fileBINB36Path;
    private String file001Path;
    private String sortPath;
    private StringBuilder sb = new StringBuilder();

    private int sCllbr = 0;
    private int sCnt1 = 0;
    private BigDecimal sAmt1 = BigDecimal.ZERO;
    private int sCnt2 = 0;
    private BigDecimal sAmt2 = BigDecimal.ZERO;

    private BigDecimal wkAmt1 = BigDecimal.ZERO;
    private BigDecimal wkAmt1T = BigDecimal.ZERO;
    private BigDecimal wkAmt2 = BigDecimal.ZERO;
    private BigDecimal wkAmt2T = BigDecimal.ZERO;
    private int wkPctl = 0;
    private int wkCnt1 = 0;
    private int wkCnt1T = 0;
    private int wkCnt2 = 0;
    private int wkCnt2T = 0;
    private int wkCllbr = 0;
    private int wkBrno = 0;
    private String wkBrnoN = "";

    private int wk003Has = 0;
    private BigDecimal wk003Dbamt = BigDecimal.ZERO;
    private int wk003Dbcnt = 0;
    private BigDecimal wk003Cramt = BigDecimal.ZERO;
    private int wk003Crcnt = 0;
    private static final String SPACE20 = "                    ";
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String PATH_SEPARATOR = File.separator;

    /*
    ex.
    @Autowired
    @Qualifier("GS15CLCMP_G092_O") private GS15CLCMP_G092_O gs15CLCMP_g092_o;
    */

    @Override
    public void onApplicationEvent(Baf1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in BAF1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Baf1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "BAF1Lsnr run()");
        init(event);
        _0000_main();
        //// 關FD檔、刪除CLBAF一年前資料、清除CLMR軋帳設定、關DB、結束程式
        // 009000     CLOSE   REPORTFL        WITH    SAVE.
        try {
            textFile.writeFileContent(file001Path, file001Contents, CHARSET_BIG5);
            upload(file001Path, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 009050     CLOSE   FD-BINB36       WITH    SAVE.
        try {
            textFile.writeFileContent(fileBINB36Path, fileBINB36Contents, CHARSET);
            upload(fileBINB36Path, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 009100     DISPLAY "SYM/CL/BH/BAF1 GENERATE REPORT 001 OK".
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/BAF1 GENERATE REPORT 001 OK");

        //// 執行099-DEL-RTN，刪除一年前帳務檔資料(刪除DB-CLBAF-DATE<WK-LYYYMMDD之資料)
        // 009150* 帳務檔，保留一年
        // 009200     PERFORM 099-DEL-RTN     THRU    099-DEL-EXIT.
        // 009300     DISPLAY "SYM/CL/BH/BAF1 MAINTAIN DB-CLBAF-DDS OK".
        _099_del();
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/BAF1 MAINTAIN DB-CLBAF-DDS OK");

        //// 執行100-AFCBV-RTN，清除CLMR軋帳記號
        // 009400     PERFORM 100-AFCBV-RTN   THRU    100-AFCBV-EXIT.
        // 009500     DISPLAY "SYM/CL/BH/BAF1 MAINTAIN DB-CLMR-DDS OK".
        _100_Afcbv();
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/BAF1 MAINTAIN DB-CLMR-DDS OK");

        // 009600     CLOSE   BOTSRDB.
        // 009800     STOP RUN.
        batchResponse();
    }

    private void init(Baf1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        //// 讀批次日期檔，設定營業日變數值；若讀不到，顯示訊息，結束程式
        //// 搬FD-BHDATE-TBSDY 9(08) 給 WK-YYYMMDD 9(07),WK-LYYYMMDD 9(07)
        //// WK-LYYYMMDD=WK-YYYMMDD - 10000(本營業日往前推1年)
        //
        // 008200     READ    FD-BHDATE AT END        DISPLAY
        // 008201     "READ FD-BHDATE ERROR"
        // 008300        STOP RUN.
        // 008400     MOVE    FD-BHDATE-TBSDY TO      WK-YYYMMDD WK-LYYYMMDD.
        // 008450     COMPUTE WK-LYYYMMDD = WK-LYYYMMDD - 10000.
        wkYYYMMDD = parse.string2Integer(processDate);
        wkLYYYMMDD = parse.string2Integer(processDate);
        wkLYYYMMDD = wkLYYYMMDD - 10000;
        // 004900 01 WK-YYYMMDD                          PIC 9(07).
        // 005000 01 WK-YYYMMDD-R     REDEFINES  WK-YYYMMDD.
        // 005100    02 WK-YYY                          PIC 9(03).
        // 005200    02 WK-MM                           PIC 9(02).
        // 005300    02 WK-DD                           PIC 9(02).
        wkYYY = formatUtil.pad9("" + wkYYYMMDD, 8).substring(1, 4);
        wkMM = formatUtil.pad9("" + wkYYYMMDD, 8).substring(4, 6);
        wkDD = formatUtil.pad9("" + wkYYYMMDD, 8).substring(6, 8);

        fileBINB36Contents = new ArrayList<>();
        file001Contents = new ArrayList<>();
        sortContents = new ArrayList<>();
        fileBINB36Path =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_DIR
                        + FILE_BINB36_NAME;
        file001Path =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_001_NAME;
        sortPath =
                fileDir + "DATA" + File.separator + processDate + File.separator + FILE_SORT_NAME;
        textFile.deleteFile(file001Path);
        textFile.deleteFile(sortPath);

        //// 清變數值
        // 008600     MOVE    0     TO  WK-CLLBR,WK-AMT1,WK-AMT2,WK-AMT1T,WK-AMT2T,
        // 008700                       WK-PCTL ,WK-CNT1,WK-CNT2,WK-CNT1T,WK-CNT2T.
        wkCllbr = 0;
        wkAmt1 = new BigDecimal(0);
        wkAmt2 = new BigDecimal(0);
        wkAmt1T = new BigDecimal(0);
        wkAmt2T = new BigDecimal(0);
        wkPctl = 0;
        wkCnt1 = 0;
        wkCnt2 = 0;
        wkCnt1T = 0;
        wkCnt2T = 0;
    }

    private void _0000_main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _0000_main");
        //// SORT INPUT 段落：CS-SORTIN(讀收付累計檔，挑資料，並寫至SORTFL)
        //// 資料照 S-CLLBR 由小到大排序
        //// SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫報表檔及FD-BINB36)
        //
        // 010000     SORT    SORTFL
        // 010100             ASCENDING KEY        S-CLLBR
        // 010101     MEMORY SIZE 6 MODULES
        // 010102     DISK SIZE 50 MODULES
        // 010103             INPUT  PROCEDURE     CS-SORTIN
        csSortin();

        // 010300             OUTPUT PROCEDURE     CS-SORTOUT
        csSortout();
    }

    private void csSortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr csSortin");

        // 010800 CS-SORTIN-RTN.
        //// FIND NEXT DB-CLBAF-DDS收付累計檔，若有誤(讀檔結束)，結束本節
        // 010900     FIND NEXT DB-CLBAF-DDS
        // 011000       ON EXCEPTION
        // 011200          GO TO   CS-SORTIN-EXIT.
        //// 代收日 不等於 本營業日 ，跳掉這筆資料
        //// GO TO CS-SORTIN-RTN找下一筆收付累計檔
        // 011300     IF        DB-CLBAF-DATE     NOT =  WK-YYYMMDD
        // 011400       GO TO   CS-SORTIN-RTN.
        List<ClbafbyEntdyBus> ClbafList =
                clbafService.findbyEntdy(parse.string2Integer(processDate), 0, Integer.MAX_VALUE);
        if (Objects.isNull(ClbafList)) {
            return;
        }
        for (ClbafbyEntdyBus tClbaf : ClbafList) {
            boolean goToSortin999 = false;
            //// CODE(1:3)="121"，跳掉這筆資料
            //// GO TO CS-SORTIN-RTN找下一筆收付累計檔
            // 011800* 代收類別前三位為１２１時為代付性質交易，本程式不處理，即不入
            // 011900* ＢＩＮＢ３６
            // 012000     MOVE       DB-CLBAF-CODE    TO     WK-CODE.
            // 012100     IF         WK-CODE1         =      "121"
            String code = tClbaf.getCode();
            if ("121".equals(code.substring(0, 3))) {
                continue;
                // 012200        GO  TO  CS-SORTIN-RTN.
            }

            //// DB-CLBAF-CLLBR=999(該代收類別之總累計資料)，
            //// GO TO CS-SORTIN-999 寫貸方資料
            // 012220     IF         DB-CLBAF-CLLBR   =      999
            if (tClbaf.getCllbr() == 999) {
                // 012240        GO  TO  CS-SORTIN-999.
                goToSortin999 = true;
            }
            //// 搬相關欄位至SORT-REC(S-AMT1=0,SCN1=0,S-AMT2=DB-CLBAF-AMT,S-CNT2=DB-CLBAF-CNT)，並寫至SORTFL
            //// 累計DB-CLBAF-AMT、DB-CLBAF-CNT到WK-AMT2T、WK-CNT2T
            if (!goToSortin999) {
                // 012300     MOVE       0                TO     S-AMT1,S-CNT1.
                sAmt1 = new BigDecimal(0);
                sCnt1 = 0;
                //// DB-CLBAF-AMT   該業務在當日之累計代收金額
                //// DB-CLBAF-CNT   該業務在當日之累計代收筆數
                //// DB-CLBAF-CLLBR 代收行
                //
                // 012720     ADD        DB-CLBAF-AMT     TO     WK-AMT2,WK-AMT2T.
                // 012740     ADD        DB-CLBAF-CNT     TO     WK-CNT2,WK-CNT2T.
                // 012800     MOVE       WK-AMT2          TO     S-AMT2.
                // 012900     MOVE       WK-CNT2          TO     S-CNT2.
                // 013000     MOVE       DB-CLBAF-CLLBR   TO     S-CLLBR.
                wkAmt2 = wkAmt2.add(tClbaf.getAmt());
                wkAmt2T = wkAmt2T.add(tClbaf.getAmt());
                wkCnt2 = wkCnt2 + tClbaf.getCnt();
                wkCnt2T = wkCnt2T + tClbaf.getCnt();
                sAmt2 = wkAmt2;
                sCnt2 = wkCnt2;
                sCllbr = tClbaf.getCllbr();
                // 013100     RELEASE    SORT-REC.
                // 002500 01   SORT-REC.
                // 002600     03  S-CLLBR                PIC 9(03).
                // 002700     03  S-AMT1                 PIC 9(13).
                // 002800     03  S-AMT2                 PIC 9(13).
                // 002900     03  S-CNT1                 PIC 9(09).
                // 003000     03  S-CNT2                 PIC 9(09).
                sb = new StringBuilder();
                sb.append(formatUtil.pad9("" + sCllbr, 3));
                sb.append(formatUtil.pad9("" + sAmt1, 13));
                sb.append(formatUtil.pad9("" + sAmt2, 13));
                sb.append(formatUtil.pad9("" + sCnt1, 9));
                sb.append(formatUtil.pad9("" + sCnt2, 9));
                sortContents.add(sb.toString());

                // 013200     MOVE       0                TO     WK-AMT2,WK-CNT2.
                wkAmt2 = new BigDecimal(0);
                wkCnt2 = 0;

                //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔
                // 013300     GO TO      CS-SORTIN-RTN.
                continue;
            }
            // 013400 CS-SORTIN-999.

            //// 依代收類別FIND DB-CLMR-DDS事業單位基本資料檔，若有誤
            ////  顯示錯誤訊息，異常，結束程式
            // 013900     FIND       DB-CLMR-IDX1   AT DB-CLMR-CODE   = DB-CLBAF-CODE
            // 014100       ON EXCEPTION
            // 014200       DISPLAY "CAN'T USE CLBAF-CODE FIND CLMR-CODE="DB-CLBAF-CODE
            // 014400       CALL SYSTEM DMTERMINATE.

            //// 搬相關欄位至SORT-REC(S-AMT1=DB-CLBAF-AMT,S-CNT1=DB-CLBAF-CNT,S-AMT2=0,SCN2=0)，並寫至SORTFL
            //// 累計DB-CLBAF-AMT、DB-CLBAF-CNT到WK-AMT1T、WK-CNT1T
            //// DB-CLBAF-AMT   該業務在當日之累計代收金額
            //// DB-CLBAF-CNT   該業務在當日之累計代收筆數
            // 015160     MOVE       DB-CLBAF-AMT        TO     WK-AMT1.
            // 015180     MOVE       DB-CLBAF-CNT        TO     WK-CNT1.
            wkAmt1 = tClbaf.getAmt();
            wkCnt1 = tClbaf.getCnt();

            //// DB-CLMR-PBRNO主辦分行
            // 015800     MOVE       DB-CLMR-PBRNO       TO     S-CLLBR.
            // 015900     ADD        WK-AMT1             TO     WK-AMT1T.
            // 015950     ADD        WK-CNT1             TO     WK-CNT1T.
            // 016000     MOVE       0                   TO     S-AMT2,S-CNT2.
            // 016100     MOVE       WK-AMT1             TO     S-AMT1.
            // 016150     MOVE       WK-CNT1             TO     S-CNT1.
            sCllbr = tClbaf.getPbrno();
            wkAmt1T = wkAmt1T.add(wkAmt1);
            wkCnt1T = wkCnt1T + wkCnt1;
            sAmt2 = new BigDecimal(0);
            sCnt2 = 0;
            sAmt1 = wkAmt1;
            sCnt1 = wkCnt1;
            // 016200     RELEASE    SORT-REC.
            // 002500 01   SORT-REC.
            // 002600     03  S-CLLBR                PIC 9(03).
            // 002700     03  S-AMT1                 PIC 9(13).
            // 002800     03  S-AMT2                 PIC 9(13).
            // 002900     03  S-CNT1                 PIC 9(09).
            // 003000     03  S-CNT2                 PIC 9(09).
            sb = new StringBuilder();
            sb.append(formatUtil.pad9("" + sCllbr, 3));
            sb.append(formatUtil.pad9("" + sAmt1, 13));
            sb.append(formatUtil.pad9("" + sAmt2, 13));
            sb.append(formatUtil.pad9("" + sCnt1, 9));
            sb.append(formatUtil.pad9("" + sCnt2, 9));
            sortContents.add(sb.toString());
            // 016300     MOVE       0                   TO     WK-AMT1,WK-CNT1.
            wkAmt1 = new BigDecimal(0);
            wkCnt1 = 0;
            //// GO TO CS-SORTIN-RTN讀下一筆收付累計檔
            // 016400     GO  TO     CS-SORTIN-RTN.
        }
        try {
            textFile.writeFileContent(sortPath, sortContents, CHARSET);
            upload(sortPath, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
        // 016500 CS-SORTIN-EXIT.
    }

    private void csSortout() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr csSortout");
        // 016900 CS-SORTOUT-RTN.

        //// 預設變數值WK-CLLBR=001用來判斷是否為第一筆
        //// 清變數值
        // 017060     MOVE       001            TO      WK-CLLBR.
        // 017070     MOVE       0              TO               WK-AMT1,WK-AMT2
        // 017080                                            ,WK-CNT1,WK-CNT2.
        wkCllbr = 1;
        wkAmt1 = new BigDecimal(0);
        wkAmt2 = new BigDecimal(0);
        wkCnt1 = 0;
        wkCnt2 = 0;
        // 017100 CS-SORTOUT-RETURN.
        //// 將SORT後的記錄讀出，直到檔尾
        ////  若WK-CLLBR=001，表無資料，GO TO CS-SORTOUT-EXIT，結束本節
        ////  其他
        ////   若WK-PCTL=45
        ////    A.097-WRITEA-RTN 寫表頭
        ////    B.090-WRITED-RTN 寫報表明細&FD-BINB36
        ////    C.092-WRITE003-RTN 寫FD-BINB36(WK-BRNO=000,003,902,906之累計)
        ////    D.095-WRITET-RTN 寫表尾-總數
        ////    E.GO TO CS-SORTOUT-EXIT，結束本節
        ////   若WK-PCTL<>45
        ////    A.090-WRITED-RTN 寫報表明細&FD-BINB36
        ////    B.092-WRITE003-RTN 寫FD-BINB36(WK-BRNO=000,003,902,906之累計)
        ////    C.095-WRITET-RTN 寫表尾-總數
        ////    D.GO TO CS-SORTOUT-EXIT，結束本節
        List<String> lines = textFile.readFileContent(sortPath, CHARSET);

        // 017200     RETURN     SORTFL    AT END
        int sortCllbr = 0;
        BigDecimal sortAmt1 = BigDecimal.ZERO;
        BigDecimal sortAmt2 = BigDecimal.ZERO;
        int sortCnt1 = 0;
        int sortCnt2 = 0;
        for (String detail : lines) {
            // 03 S-CLLBR	9(03) 0-3
            // 03 S-AMT1	9(13) 3-16
            // 03 S-AMT2	9(13) 16-29
            // 03 S-CNT1	9(09) 29-38
            // 03 S-CNT2	9(09) 38-47
            sortCllbr = parse.string2Integer(detail.substring(0, 3));
            sortAmt1 = parse.string2BigDecimal(detail.substring(3, 16));
            sortAmt2 = parse.string2BigDecimal(detail.substring(16, 29));
            sortCnt1 = parse.string2Integer(detail.substring(29, 38));
            sortCnt2 = parse.string2Integer(detail.substring(38, 47));
            //// 若CLLBR相同，累計S-...(筆數、金額)到WK-...；LOOP讀下一筆SORTFL
            //// 若CLLBR不相同，
            ////  若WK-CLLBR=001，表第1筆資料，往下一步驟執行
            ////  其他，表處理到不同分行資料，出表&寫檔
            // 017600     IF         WK-CLLBR       =       S-CLLBR
            if (wkCllbr == sortCllbr) {
                // 017700       ADD      S-AMT1         TO      WK-AMT1
                // 017750       ADD      S-CNT1         TO      WK-CNT1
                // 017800       ADD      S-AMT2         TO      WK-AMT2
                // 017850       ADD      S-CNT2         TO      WK-CNT2
                wkAmt1 = wkAmt1.add(sortAmt1);
                wkCnt1 = wkCnt1 + sortCnt1;
                wkAmt2 = wkAmt2.add(sortAmt2);
                wkCnt2 = wkCnt2 + sortCnt2;
                // 017900       GO TO CS-SORTOUT-RETURN
                continue;
            } else {
                // 018000     ELSE
                // 018150       IF       WK-CLLBR       =       001
                if (wkCllbr == 1) {
                    // 018200         NEXT SENTENCE
                } else {
                    // 018300       ELSE
                    //// WK-PCTL累加1
                    // 018400         ADD        1              TO      WK-PCTL
                    wkPctl = wkPctl + 1;
                    //// WK-PCTL=1,46
                    ////  A.097-WRITEA-RTN 寫表頭
                    ////  B.090-WRITED-RTN 寫報表明細&FD-BINB36
                    ////  C.清變數值
                    //// 其他
                    ////  A.090-WRITED-RTN 寫報表明細&FD-BINB36
                    ////  B.清變數值
                    // 018500         IF         WK-PCTL        =   1   OR = 46
                    if (wkPctl == 1 || wkPctl == 46) {
                        // 018600           MOVE     1              TO      WK-PCTL
                        // 018700           PERFORM  097-WRITEA-RTN THRU    097-WRITEA-EXIT
                        // 018800           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        // 018900           MOVE     0       TO     WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2
                        wkPctl = 1;
                        _097_Writea();
                        _090_Writed();
                        wkAmt1 = new BigDecimal(0);
                        wkAmt2 = new BigDecimal(0);
                        wkCnt1 = 0;
                        wkCnt2 = 0;
                    } else {
                        // 019000         ELSE
                        // 019100           PERFORM  090-WRITED-RTN THRU    090-WRITED-EXIT
                        // 019150           MOVE     0       TO    WK-AMT1,WK-AMT2,WK-CNT1,WK-CNT2.
                        _090_Writed();
                        wkAmt1 = new BigDecimal(0);
                        wkAmt2 = new BigDecimal(0);
                        wkCnt1 = 0;
                        wkCnt2 = 0;
                    }
                }
                ////  搬S-...(筆數、金額)到變數WK-...
                ////  保留S-CLLBR到變數WK-CLLBR
                // 019300     MOVE       S-AMT1         TO      WK-AMT1.
                // 019350     MOVE       S-CNT1         TO      WK-CNT1.
                // 019400     MOVE       S-AMT2         TO      WK-AMT2.
                // 019450     MOVE       S-CNT2         TO      WK-CNT2.
                // 019500     MOVE       S-CLLBR        TO      WK-CLLBR.
                wkAmt1 = sortAmt1;
                wkCnt1 = sortCnt1;
                wkAmt2 = sortAmt2;
                wkCnt2 = sortCnt2;
                wkCllbr = sortCllbr;
                //// LOOP讀下一筆SORTFL
                // 019600     GO TO CS-SORTOUT-RETURN.
            }
        }
        // 017206       IF       WK-CLLBR           =       001
        if (wkCllbr == 1) {
            // 017210             GO TO   CS-SORTOUT-EXIT
            return;
        } else {
            // 017215       ELSE
            // 017220         IF         WK-PCTL        =       45
            if (wkPctl == 45) {
                // 017230                MOVE    0              TO      WK-PCTL
                // 017240                PERFORM 097-WRITEA-RTN THRU    097-WRITEA-EXIT
                // 017300                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                // 017350                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                // 017400                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                wkPctl = 0;
                _097_Writea();
                _090_Writed();
                _092_write003();
                _095_writet();

                // 017500                GO TO   CS-SORTOUT-EXIT
                return;
            } else {
                // 017520         ELSE
                // 017540                PERFORM 090-WRITED-RTN THRU    090-WRITED-EXIT
                // 017550                PERFORM 092-WRITE003-RTN THRU  092-WRITE003-EXIT
                // 017560                PERFORM 095-WRITET-RTN THRU    095-WRITET-EXIT
                _090_Writed();
                _092_write003();
                _095_writet();

                // 017580                GO TO   CS-SORTOUT-EXIT
                return;
                // 017585         END-IF
            }
            // 017590       END-IF.
        }
        // 019700 CS-SORTOUT-EXIT.
    }

    private void _097_Writea() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _097_Writea");
        // 022400 097-WRITEA-RTN.
        //// 寫REPORTFL(表頭)

        // 022500     MOVE       SPACES         TO    REPORT-LINE.
        // 022600     WRITE      REPORT-LINE    AFTER PAGE.
        // 022700     WRITE      REPORT-LINE    AFTER 2 LINES.
        file001Contents.add(PAGE_SEPARATOR);
        file001Contents.add("");
        file001Contents.add("");
        // 022800     MOVE       WK-YYY         TO    WK-YYY-P.
        // 022900     MOVE       WK-MM          TO    WK-MM-P.
        // 023000     MOVE       WK-DD          TO    WK-DD-P.
        // 023100     WRITE      REPORT-LINE    FROM  WK-DATE-LINE.
        // 005400 01 WK-DATE-LINE.
        // 005500    02 FILLER                          PIC X(23) VALUE SPACES.
        // 005600    02 WK-YYY-P                        PIC 9(03).
        // 005700    02 FILLER                          PIC X(04) VALUE SPACES.
        // 005800    02 WK-MM-P                         PIC 9(02).
        // 005900    02 FILLER                          PIC X(04) VALUE SPACES.
        // 006000    02 WK-DD-P                         PIC 9(02).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 23));
        sb.append(formatUtil.pad9(wkYYY, 3));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkMM, 2));
        sb.append(formatUtil.padX("", 4));
        sb.append(formatUtil.pad9(wkDD, 2));
        file001Contents.add(sb.toString());
        // 023200     MOVE       SPACES         TO    REPORT-LINE.
        // 023300     WRITE      REPORT-LINE   BEFORE 3 LINES.
        file001Contents.add("");
        file001Contents.add("");
        file001Contents.add("");
        file001Contents.add("");
        // 023400 097-WRITEA-EXIT.
    }

    private void _090_Writed() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _090_Writed");
        // 020100 090-WRITED-RTN.
        // 020200     MOVE       SPACES         TO    REPORT-LINE.
        // 020300     MOVE       WK-CLLBR       TO    WK-BRNO.
        wkBrno = wkCllbr;

        //// FIND DB-BCTL-ACCESS營業單位控制檔，設定營業單位中文名稱變數值；
        //// 若有誤，搬空白
        // 020400     FIND       DB-BCTL-ACCESS AT  DB-BCTL-BRNO = WK-BRNO
        // 020500       ON EXCEPTION
        // 020600          MOVE  SPACES         TO    WK-BRNO-N
        // 020700          GO TO 090-NO.
        // 020800     MOVE       DB-BCTL-CHNAM  TO    WK-BRNO-N.
        // 020900 090-NO.

        bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(wkBrno);
        if (!Objects.isNull(bctl)) {
            wkBrnoN = bctl.getChnam() == null ? SPACE20 : formatUtil.padX(bctl.getChnam(), 20);
        }
        //// 搬累計變數值WK-...至REPORT、FD-BINB-REC，並寫REPORTFL及FD-BINB36

        // 020950****ADD FD-BINB FOR ONLINE-BINB USE
        // 021000     MOVE       WK-AMT1        TO    WK-AMT1-P,FD-BINB-DBAMT.
        // 021100     MOVE       WK-AMT2        TO    WK-AMT2-P,FD-BINB-CRAMT.
        // 021120     MOVE       WK-CNT1        TO    WK-CNT1-P,FD-BINB-DBCNT.
        // 021140     MOVE       WK-CNT2        TO    WK-CNT2-P,FD-BINB-CRCNT.
        BigDecimal wkAmt1P = wkAmt1;
        BigDecimal fdBinbDbamt = wkAmt1;
        BigDecimal wkAmt2P = wkAmt2;
        BigDecimal fdBinbCramt = wkAmt2;
        int wkCnt1P = wkCnt1;
        int fdBinbDbcnt = wkCnt1;
        int wkCnt2P = wkCnt2;
        int fdBinbCrcnt = wkCnt2;
        //// 寫REPORTFL(報表明細)

        // 021200     WRITE      REPORT-LINE    FROM  WK-DETAIL-LINE.
        // 006100 01 WK-DETAIL-LINE.
        // 006200    02 WK-BRNO                         PIC 9(03).
        // 006300    02 WK-BRNO-N                       PIC X(20).
        // 006400    02 WK-CNT1-P                       PIC Z(05)9.
        // 006500    02 WK-AMT1-P                       PIC Z(14)9.
        // 006600    02 WK-CNT2-P                       PIC Z(08)9.
        // 006700    02 WK-AMT2-P                       PIC Z(14)9.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9("" + wkBrno, 3));
        sb.append(formatUtil.padX(cutStringByByteLength(wkBrnoN, 0, 20), 20));
        sb.append(reportUtil.customFormat("" + wkCnt1P, "ZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkAmt1P, "ZZZZZZZZZZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkCnt2P, "ZZZZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkAmt2P, "ZZZZZZZZZZZZZZ9"));
        file001Contents.add(sb.toString());
        // 021210     MOVE       WK-YYYMMDD      TO    FD-BINB-DATE.
        // 021220     MOVE       WK-BRNO        TO    FD-BINB-BRNO.
        String fdBinbDate = formatUtil.pad9("" + wkYYYMMDD, 7);
        String fdBinbBrno = formatUtil.pad9("" + wkBrno, 3);

        //// 若 WK-BRNO=000,003,902,906，設定WK-003-HAS=1、累計資料至WK-003-...，
        //// GO TO 090-WRITED-EXIT不寫FD-BINB36

        // 021232     IF         WK-BRNO        = 000 OR = 003 OR = 902 OR = 906
        if (wkBrno == 0 || wkBrno == 3 || wkBrno == 902 || wkBrno == 906) {
            // 021236       MOVE     1              TO    WK-003-HAS
            // 021238       ADD      FD-BINB-DBAMT  TO    WK-003-DBAMT
            // 021240       ADD      FD-BINB-DBCNT  TO    WK-003-DBCNT
            // 021242       ADD      FD-BINB-CRAMT  TO    WK-003-CRAMT
            // 021244       ADD      FD-BINB-CRCNT  TO    WK-003-CRCNT
            wk003Has = 1;
            wk003Dbamt = wk003Dbamt.add(fdBinbDbamt);
            wk003Dbcnt = wk003Dbcnt + fdBinbDbcnt;
            wk003Cramt = wk003Cramt.add(fdBinbCramt);
            wk003Crcnt = wk003Crcnt + fdBinbCrcnt;
            // 021246       GO TO 090-WRITED-EXIT.
            return;
        }
        // 021258     MOVE       ZEROS          TO    FD-BINB-CURCD.
        // 021260     MOVE       36             TO    FD-BINB-ONAPNO.
        String fdBinbCurcd = "0";
        String fdBinbOnapno = "36";

        //// 寫FD-BINB36
        // 021262     WRITE      FD-BINB-REC.
        // 002320 01   FD-BINB-REC.
        // 002325   05 FD-BINB-DATE              PIC 9(07).
        // 002330   05 FD-BINB-BRNO              PIC 9(03).
        // 002335   05 FD-BINB-CURCD             PIC 9(02).
        // 002340   05 FD-BINB-ONAPNO            PIC 9(02).
        // 002345   05 FD-BINB-DBCNT             PIC 9(05).
        // 002350   05 FD-BINB-DBAMT             PIC 9(13)V99.
        // 002355   05 FD-BINB-CRCNT             PIC 9(05).
        // 002360   05 FD-BINB-CRAMT             PIC 9(13)V99.
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(fdBinbDate, 7));
        sb.append(formatUtil.pad9(fdBinbBrno, 3));
        sb.append(formatUtil.pad9(fdBinbCurcd, 2));
        sb.append(formatUtil.pad9(fdBinbOnapno, 2));
        sb.append(formatUtil.pad9("" + fdBinbDbcnt, 5));
        sb.append(reportUtil.customFormat("" + fdBinbDbamt, "9999999999999.99").replace(".", ""));
        sb.append(formatUtil.pad9("" + fdBinbCrcnt, 5));
        sb.append(reportUtil.customFormat("" + fdBinbCramt, "9999999999999.99").replace(".", ""));
        fileBINB36Contents.add(sb.toString());

        // 021300 090-WRITED-EXIT.
    }

    private void _092_write003() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _092_write003");
        // 021405 092-WRITE003-RTN.
        //// WK-BRNO=000,003,902,906之累計

        // 021410     IF         WK-003-HAS     =     1
        if (wk003Has == 1) {
            // 021415       MOVE     WK-YYYMMDD      TO    FD-BINB-DATE
            // 021420       MOVE     003            TO    FD-BINB-BRNO
            // 021425       MOVE     WK-003-DBAMT   TO    FD-BINB-DBAMT
            // 021430       MOVE     WK-003-DBCNT   TO    FD-BINB-DBCNT
            // 021435       MOVE     WK-003-CRAMT   TO    FD-BINB-CRAMT
            // 021440       MOVE     WK-003-CRCNT   TO    FD-BINB-CRCNT
            // 021445       MOVE     ZEROS          TO    FD-BINB-CURCD
            // 021450       MOVE     36             TO    FD-BINB-ONAPNO
            //// 寫FD-BINB36
            // 021455       WRITE    FD-BINB-REC.
            // 002320 01   FD-BINB-REC.
            // 002325   05 FD-BINB-DATE              PIC 9(07).
            // 002330   05 FD-BINB-BRNO              PIC 9(03).
            // 002335   05 FD-BINB-CURCD             PIC 9(02).
            // 002340   05 FD-BINB-ONAPNO            PIC 9(02).
            // 002345   05 FD-BINB-DBCNT             PIC 9(05).
            // 002350   05 FD-BINB-DBAMT             PIC 9(13)V99.
            // 002355   05 FD-BINB-CRCNT             PIC 9(05).
            // 002360   05 FD-BINB-CRAMT             PIC 9(13)V99.
            sb = new StringBuilder();
            sb.append(formatUtil.pad9("" + wkYYYMMDD, 7));
            sb.append(formatUtil.pad9("003", 3));
            sb.append(formatUtil.pad9("0", 2));
            sb.append(formatUtil.pad9("36", 2));
            sb.append(formatUtil.pad9("" + wk003Dbcnt, 5));
            sb.append(
                    reportUtil.customFormat("" + wk003Dbamt, "9999999999999.99").replace(".", ""));
            sb.append(formatUtil.pad9("" + wk003Crcnt, 5));
            sb.append(
                    reportUtil.customFormat("" + wk003Cramt, "9999999999999.99").replace(".", ""));
            fileBINB36Contents.add(sb.toString());
        }
        // 021460 092-WRITE003-EXIT.
    }

    private void _095_writet() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _095_writet");
        // 021500 095-WRITET-RTN.
        //// 寫REPORTFL(表尾-總數)
        wkPctl = 45 - wkPctl;
        // 021600     MOVE       SPACES         TO    REPORT-LINE.
        // 021700     COMPUTE    WK-PCTL = 45 - WK-PCTL.

        for (int i = 0; i <= wkPctl; i++) {
            file001Contents.add("");
        }
        // 021800     WRITE      REPORT-LINE    AFTER WK-PCTL.
        // 021900     MOVE       WK-AMT1T       TO    WK-AMT1T-P.
        // 022000     MOVE       WK-AMT2T       TO    WK-AMT2T-P.
        // 022020     MOVE       WK-CNT1T       TO    WK-CNT1T-P.
        // 022040     MOVE       WK-CNT2T       TO    WK-CNT2T-P.
        // 022100     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE.
        // 006800 01 WK-TOTAL-LINE.
        // 006900    02 FILLER                          PIC X(20) VALUE SPACES.
        // 007000    02 WK-CNT1T-P                      PIC Z(08)9.
        // 007100    02 WK-AMT1T-P                      PIC Z(14)9.
        // 007200    02 WK-CNT2T-P                      PIC Z(08)9.
        // 007300    02 WK-AMT2T-P                      PIC Z(14)9.
        sb = new StringBuilder();
        sb.append(SPACE20);
        sb.append(reportUtil.customFormat("" + wkCnt1T, "ZZZZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkAmt1T, "ZZZZZZZZZZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkCnt2T, "ZZZZZZZZ9"));
        sb.append(reportUtil.customFormat("" + wkAmt2T, "ZZZZZZZZZZZZZZ9"));
        file001Contents.add(sb.toString());

        // 022200 095-WRITET-EXIT.
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

    private void _099_del() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _099_del");
        // 023600 099-DEL-RTN.
        //// 將DB-CLBAF-DDS指標移至開始
        //// WK-DELCNT刪檔筆數清為0

        // 023700     SET   DB-CLBAF-DDS   TO     BEGINNING.
        // 023740     MOVE  0              TO     WK-DELCNT.
        //        int wkDelcnt = 0;
        // 023800 099-DEL-LOOP.
        // 023805**Deadlock Begin
        // 023900**   LOCK  NEXT DB-CLBAF-DDS
        //// FIND NEXT DB-CLBAF-DDS收付累計檔，若有誤
        ////  若NOTFOUND：若刪檔筆數大於0，跳至099-ETR-RTN，否則(刪檔筆數等於0)，無刪除資料，跳至099-DEL-EXIT，結束本段落
        ////  否則跳至099-DEL-EXIT，結束本段落
        // 023905     FIND  NEXT DB-CLBAF-DDS
        // 023910**Deadlock End
        // 024000       ON  EXCEPTION
        // 024010           IF DMSTATUS(NOTFOUND)
        // 024020              IF WK-DELCNT  >  0
        // 024030                 GO TO 099-ETR-RTN
        // 024040              ELSE
        // 024050                 GO TO 099-DEL-EXIT
        // 024060           ELSE
        // 024070                 GO TO 099-DEL-EXIT.
        //// 代收日>=WK-LYYYMMDD(最近一年資料)，不處理，LOOP讀下一筆CLBAF
        // 024100     IF       DB-CLBAF-DATE     >=   WK-LYYYMMDD
        // 024200        GO TO 099-DEL-LOOP.
        // 024300*
        List<ClbafbyEntdyforBaf1Bus> lClbaf =
                clbafService.findbyEntdyforBaf1(wkLYYYMMDD, 0, Integer.MAX_VALUE);
        if (Objects.isNull(lClbaf)) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "lClbaf is null");
            return;
        }
        List<ClbafBus> dClbaf = clbafMapper.toClbafBusLi_11(lClbaf);
        try {
            clbafService.deleteAllSilent(dClbaf);
        } catch (LogicException e) {
            throw new LogicException("", "刪除收付累計檔發生錯誤");
        }
        // 024320 099-BTR-RTN.
        //        for (ClbafbyEntdyforBaf1Bus tClbaf : lClbaf) {
        // 024321**Deadlock Begin
        //// LOCK DB-CLBAF-DDS收付累計檔，若有誤
        ////  若DEADLOCK，顯示訊息，等待1秒，GO TO 099-BTR-RTN再次LOCK
        ////  其他，異常，結束程式
        //
        // 024322     LOCK    DB-CLBAF-DDS
        // 024323       ON    EXCEPTION
        // 024324       IF    DMSTATUS(DEADLOCK)
        // 024325             DISPLAY "[DEADLOCK CATEGORY]=" DMSTATUS(DMCATEGORY)
        // 024326             DISPLAY "[099-BTR CLBAF LOCK ERRTYPE]="
        // 024327                                            DMSTATUS(DMERRORTYPE)
        // 024328             WAIT(1)
        // 024329             GO TO 099-BTR-RTN
        // 024330       ELSE
        // 024331             CALL SYSTEM DMTERMINATE.
        // 024332

        //// 逐筆刪除，所以WK-DELCNT都會是0???
        // 024340     IF      WK-DELCNT     =     0
        //// BEGIN-TRANSACTION開始交易，若有誤
        ////  若DEADLOCK，顯示訊息，等待1秒，GO TO 099-BTR-RTN再次LOCK
        ////  其他，結束099-DEL-RTN
        // 024360     BEGIN-TRANSACTION NO-AUDIT RESTART-DST ON EXCEPTION
        // 024364       IF    DMSTATUS(DEADLOCK)
        // 024366             DISPLAY "[DEADLOCK CATEGORY]=" DMSTATUS(DMCATEGORY)
        // 024368             DISPLAY "[099-BTR CLBAF BTR ERRTYPE]="
        // 024370                                            DMSTATUS(DMERRORTYPE)
        // 024372             WAIT(1)
        // 024374             GO  TO  099-BTR-RTN
        // 024376       ELSE
        // 024378**Deadlock End
        // 024380             GO  TO  099-DEL-EXIT.
        //
        //// DELETE DB-CLBAF-DDS收付累計檔，若有誤，結束099-DEL-RTN
        //
        // 024400     DELETE  DB-CLBAF-DDS  ON EXCEPTION  GO TO 099-DEL-EXIT.
        // CLLBR,ENTDY,CODE,PBRNO,CRDB,TXTYPE,CURCD
        //            ClbafId clbafId = new ClbafId();
        //            clbafId.setCllbr(tClbaf.getCllbr());
        //            clbafId.setEntdy(tClbaf.getEntdy());
        //            clbafId.setCode(tClbaf.getCode());
        //            clbafId.setPbrno(tClbaf.getPbrno());
        //            clbafId.setCrdb(tClbaf.getCrdb());
        //            clbafId.setTxtype(tClbaf.getTxtype());
        //            clbafId.setCurcd(tClbaf.getCurcd());
        //            ClbafBus delClbaf = clbafService.holdById(clbafId);
        //            if (Objects.isNull(delClbaf)) {
        //                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "ClbafBus is
        // null");
        //                continue;
        //            }

        //// 刪檔筆數累加1
        // 024500     ADD     1         TO     WK-DELCNT.
        //            wkDelcnt = wkDelcnt + 1;
        //// 逐筆刪除，所以WK-DELCNT都會是1???
        //
        // 024520     IF    WK-DELCNT    <      1      GO  TO   099-DEL-LOOP.
        // 024530 099-ETR-RTN.

        //// END-TRANSACTION確認交易，若有誤，結束099-DEL-RTN

        // 024540     END-TRANSACTION   NO-AUDIT RESTART-DST ON EXCEPTION
        // 024560             GO  TO  099-DEL-EXIT.
        //
        //// 確認交易完成，資料確認刪除
        //// WK-DELCNT刪檔筆數清為0
        // 024570     MOVE    0   TO    WK-DELCNT.

        //// LOOP讀下一筆CLBAF

        // 024580     GO  TO  099-DEL-LOOP.
        //        }
        // 024600 099-DEL-EXIT.
    }

    private void _100_Afcbv() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Baf1Lsnr _100_Afcbv");
        // 025400 100-AFCBV-RTN.
        //// 將DDB-CLMR-DDS指標移至開始
        // 025500     SET   DB-CLMR-DDS        TO     BEGINNING.
        // 025600 100-LOOP.
        // 025605**Deadlock Begin
        // 025700**   LOCK  NEXT DB-CLMR-DDS   ON EXCEPTION GO TO  100-AFCBV-EXIT.
        //
        //// FIND NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至100-AFCBV-EXIT，結束本段落
        //
        // 025705     FIND  NEXT DB-CLMR-DDS   ON EXCEPTION GO TO  100-AFCBV-EXIT.
        // 025710
        // 025715 100-LOCK.
        //
        //// LOCK DB-CLMR-DDS事業單位基本資料檔，若有誤
        ////  若DEADLOCK，顯示訊息，等待1秒，GO TO 100-LOCK再次LOCK
        ////  其他，異常，結束程式
        //
        // 025720     LOCK    DB-CLMR-DDS
        // 025725       ON    EXCEPTION
        // 025730       IF    DMSTATUS(DEADLOCK)
        // 025735             DISPLAY "[DEADLOCK CATEGORY]=" DMSTATUS(DMCATEGORY)
        // 025740             DISPLAY "[100-AFCBV CLMR LOCK ERRTYPE]="
        // 025745                                            DMSTATUS(DMERRORTYPE)
        // 025750             WAIT(1)
        // 025755             GO  TO  100-LOCK
        // 025760       ELSE
        // 025765             CALL SYSTEM DMTERMINATE.
        // 025770**Deadlock End
        //
        //// 清DB-CLMR-DDS'S值
        //
        // 025800     MOVE       0             TO     DB-CLMR-AFCBV.		<-軋帳記號
        // 025850     MOVE       0             TO     DB-CLMR-TOTAMT.	<-累計金額
        // 025870     MOVE      "0"            TO     DB-CLMR-PRTTYP.
        // 025875**Deadlock Begin
        //
        //// BEGIN-TRANSACTION開始交易，若有誤
        ////  若DEADLOCK，顯示訊息，等待1秒，GO TO 100-LOCK再次LOCK
        ////  其他，異常，結束程式
        //
        // 025900     BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 025905       ON    EXCEPTION
        // 025910       IF    DMSTATUS(DEADLOCK)
        // 025915             DISPLAY "[DEADLOCK CATEGORY]=" DMSTATUS(DMCATEGORY)
        // 025920             DISPLAY "[100-AFCBV CLMR BTR ERRTYPE]="
        // 025925                                            DMSTATUS(DMERRORTYPE)
        // 025930             WAIT(1)
        // 025935             GO  TO  100-LOCK
        // 025940       ELSE
        // 025945             CALL SYSTEM DMTERMINATE.
        // 025950**Deadlock End
        //
        //// STORE  DB-CLMR-DDS事業單位基本資料檔
        //
        // 026000     STORE  DB-CLMR-DDS.
        // 026100     END-TRANSACTION NO-AUDIT RESTART-DST.
        //
        //// LOOP讀下一筆CLMR
        //
        // 026200     GO TO  100-LOOP.

        List<ClmrBus> clmrBusList = clmrService.findAll(0, Integer.MAX_VALUE);
        if (Objects.isNull(clmrBusList) || clmrBusList.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "clmrBus is null");
            return;
        }
        for (ClmrBus clmrBus : clmrBusList) {
            String code = clmrBus.getCode();
            clmrBus.setAfcbv(0);
            List<CltotbyCodeBus> lCltotcode = cltotService.findbyCode(code, 0, Integer.MAX_VALUE);
            if (Objects.isNull(lCltotcode) || lCltotcode.isEmpty()) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "clmrBus is null");
                continue;
            }
            List<CltotBus> lCltot = cltotMapper.toCltotBusLi_00(lCltotcode);
            for (CltotBus cltotBus : lCltot) {
                cltotBus.setPayamt(new BigDecimal(0));
                cltotBus.setRcvamt(new BigDecimal(0));
            }
            try {
                cltotService.updateAllSilent(lCltot);
            } catch (LogicException e) {
                throw new LogicException("", "更新事業單位累計檔發生錯誤");
            }
            updateCLTMR(code);
        }

        try {
            clmrService.updateAllSilent(clmrBusList);
        } catch (LogicException e) {
            throw new LogicException("", "更新事業單位交易設定檔發生錯誤");
        }
        // 026300 100-AFCBV-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private void updateCLTMR(String code) {
        CltmrBus tCltmr = cltmrService.holdById(code);
        if (Objects.isNull(tCltmr)) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "CltmrBus is null");
            return;
        }
        tCltmr.setPrtype("0");
        try {
            cltmrService.update(tCltmr);
        } catch (LogicException e) {
            throw new LogicException("", "更新事業單位基本資料檔發生錯誤");
        }
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
        responseTextMap.put("RPTNAME", FILE_001_NAME);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
