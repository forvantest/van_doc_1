/* (C) 2023 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.PUTH;
import com.bot.ncl.dto.entities.CldtlByCodeEntdyRangeBus;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.ncl.util.fileVo.FilePUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("PUTHLsnr")
@Scope("prototype")
public class PUTHLsnr extends BatchListenerCase<PUTH> {

    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private CldtlService cldtlService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private ExternalSortUtil externalSortUtil;

    @Autowired private TextFileUtil textFile;

    @Autowired private Parse parse;

    @Autowired private FormatUtil formatUtil;

    @Autowired private DateUtil dateUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private FsapSync fsapSync;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FilePUTH filePuth;

    private PUTH event;
    private Map<String, String> labelMap;
    private static final String FILE_NAME = "PUTH";

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String filePath;

    private List<String> fileContents;

    private int pageIndex;

    private String putname;
    private String putsend;
    private String putform;

    private int msg2;
    private String prtype;
    private TxBizDate fdClndr;
    private String processDate;
    private String wkFsapYYYYMMDD;
    private int puttype;

    // WK-YYMMDD
    private int tbsdy;

    // FD-BHDATE-NBSDY
    private int nbsdy;

    // FD-BHDATE-LBSDY
    private int lbsdy;

    // FD-BHDATE-FNBSDY
    private int fnbsdy;

    // WK-DATE 挑檔日
    private int putDate;

    // WK-LPUTDT 上次挑檔日
    int lputdt;

    // WK-RTN
    // 1: 當日挑檔
    // 2: 當日雖為營業日但延後挑檔
    // 3: 退稅憑單挑檔，從上次挑檔日挑到星期五或六為止
    private int pickType = 0;
    // WK-FLG 每周正常挑檔 (挑檔日期=設定的星期幾 或 假日提前挑檔 )
    private int weekFlag = 0;

    // WK-CODE
    private String code;
    private CltmrBus cltmr;
    private ClmcBus clmc;
    private ClmrBus clmr;

    private int cldtlPageIndex;

    // 01  WK-TABLE.
    //     05  FILLER                   PIC X(33) VALUE
    //        "005011012027022026068015018038039".
    //     05  FILLER                   PIC X(42) VALUE
    //        "010029030032016031009014067028025017023024".
    // 01  WK-TABLE-R         REDEFINES WK-TABLE.
    //     05  WK-TAB            OCCURS 25.
    //         10  WK-KINBR        PIC 9(03).
    // *005 011 012 027 022 026 068 015 018 038 039
    // *010 029 030 032 016 031 009 014 067 028 025 017 023 024
    private final List<String> WK_KINBR =
            Arrays.asList(
                    "005", "011", "012", "027", "022", "026", "068", "015", "018", "038", "039",
                    "010", "029", "030", "032", "016", "031", "009", "014", "067", "028", "025",
                    "017", "023", "024");

    // 01  WK-TABLE-121454.
    //     05  FILLER                   PIC X(33) VALUE
    //        "005009010011012014015016017018022".
    //     05  FILLER                   PIC X(42) VALUE
    //        "023024025026027028029030031032042067068088".
    // 01  WK-TABLE-121454-R  REDEFINES WK-TABLE-121454.
    //     05  WK-TAB-121454     OCCURS 26.
    //         10  WK-KINBR-121454 PIC 9(03).
    // *005 009 010 011 012 014 015 016 017 018 022
    // *023 024 025 026 027 028 029 030 031 032 042 067 068 088
    private final List<String> WK_KINBR_121454 =
            Arrays.asList(
                    "005", "009", "010", "011", "012", "014", "015", "016", "017", "018", "022",
                    "023", "024", "025", "026", "027", "028", "029", "030", "031", "032", "042",
                    "067", "068", "088");

    @Override
    public void onApplicationEvent(PUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in PUTHLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(PUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PUTHLsnr run()");

        // 初始化
        init(event);

        // 0000-MAIN-RTN
        queryClmrAndCldtlThenWriteFile();

        copyData("SORTIN_PUTH");
        sortOut();
        // 參考WFL_CL_1530_CLONB
        // IF FILE DATA/CL/BH/PUTH IS RESIDENT THEN                                      00010900
        // COPY DATA/CL/BH/PUTH AS DATA/CL/BH/TTPUTH;                                  00011000
        copyData("TTPUTH");

        upload(filePath);

        //        batchResponse();
    }

    private void copyData(String copyfileName) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "copyData()");
        List<String> puthList = textFile.readFileContent(filePath, CHARSET);
        String ttputh =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + copyfileName;
        textFile.deleteFile(ttputh);
        textFile.writeFileContent(ttputh, puthList, CHARSET);
        //        upload(ttputh);
    }

    private void init(PUTH event) {
        this.event = event;
        fileContents = new ArrayList<>();
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 作業日期(民國年yyyymmdd)
        processDate = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1);
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);

        tbsdy = parse.string2Integer(labelMap.get("PROCESS_DATE"));
        nbsdy = parse.string2Integer(labelMap.get("NBSDY"));
        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();
        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        try {
            List<TxBizDate> txBizDates =
                    fsapSync.sy202ForAp(event.getPeripheryRequest(), processDate, processDate);
            if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
                fdClndr = txBizDates.get(0);
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "fdClndr = [{}]", fdClndr.toString());
                tbsdy = fdClndr.getTbsdy();
                nbsdy = fdClndr.getNbsdy();
                fnbsdy = fdClndr.getFnbsdy();
                lbsdy = fdClndr.getLbsdy();
            }
        } catch (Exception e) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "UNAVAILABLE");
        }
        putDate = tbsdy;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "batchDate = " + processDate);
        filePath = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        textFile.deleteFile(filePath);
        pageIndex = 0;
        lputdt = 0;
    }

    // 查詢CLMR
    private void queryClmrAndCldtlThenWriteFile() {
        int pageCnts = 10000;
        List<ClmrBus> clmrList = clmrService.findAll(pageIndex, pageCnts);
        if (clmrList == null || clmrList.isEmpty()) {
            return;
        }

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pageIndex = {}", pageIndex);
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "clmrList size = {}", clmrList.size());
        Date startTime = new Date();
        // CS-SORTIN-RTN
        //        ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
        for (ClmrBus clmrBus : clmrList) {
            clmr = clmrBus;
            pickType = 0;
            cldtlPageIndex = 0;
            weekFlag = 0;
            code = clmrBus.getCode();
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "code = {}", code);
            cltmr = cltmrService.findById(code);
            if (Objects.isNull(cltmr)) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "cltmr is null.");
                continue;
            }
            putname = cltmr.getPutname();
            if (Objects.isNull(putname)) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putname is null.");
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putname = {}", putname);
            clmc = clmcService.findById(putname);
            if (Objects.isNull(clmc)) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmc is null.");
                continue;
            }
            putsend = clmc.getPutsend();
            putform = clmc.getPutform();
            puttype = clmc.getPuttype();
            msg2 = clmc.getMsg2();
            prtype = cltmr.getPrtype();

            validateAndQueryCldtl();
            startTime = batchUtil.refreshBatchTransaction(this.batchTransaction, startTime);
        }
        // 2024-01-18 Wei: 當此頁筆數與每頁筆數相同時,視為可能還有資料,遞迴處理
        if (clmrList.size() == pageCnts) {
            pageIndex++;
            queryClmrAndCldtlThenWriteFile(); // next page
        } else {
            // clmr已無資料,將剩餘資料寫入檔案
            if (!fileContents.isEmpty()) {
                writeFile();
                fileContents = new ArrayList<>();
            }
        }
    }

    private void validateAndQueryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "validateAndQueryCldtl");

        // 若DB-CLMR-CYCK1銷帳媒體產生週期=5(無此需求)，跳掉這筆資料
        if (clmc.getCyck1() == 5) {
            return;
        }

        // 保留 上次CYC1挑檔日 到變數WK-LPUTDT
        // 若上次CYC1挑檔日=本營業日(重跑，不用再判斷???)
        //   A.保留 上上次CYC1挑檔日 到變數WK-LPUTDT
        //   B.GO TO CS-INLOOP-BEGIN 讀DB-CLDTL-DDS收付明細檔
        // 否則，執行020-CHK-RTN，以利判斷此代收類別需挑檔否
        putDate = tbsdy;
        lputdt = cltmr.getLputdt();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lputdt = {}", lputdt);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        if (lputdt == tbsdy) {
            lputdt = cltmr.getLlputdt();
            // CS-INLOOP-BEGIN
            queryCldtl(code, lputdt, putDate);
            // 這是重跑的情況,不會更新CLTMR或CLMC資料庫
            return;
        } else {
            // 020-CHK-RTN
            chk();
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "after chk() , pickType = {}", pickType);
        }

        // 判斷是否需設定延後挑檔 或 退稅憑單挑檔(DB-CLMR-CODE="121444")
        if (code.equals("121444")) {
            // CHKBCTL-RTN
            //  25個分行只要有一個分行沒有營業則2-不挑檔，否則為3-退稅憑單挑檔
            chkbctl();
        } else if (code.equals("121454")) {
            // CHKBCTL-121454-RTN
            //  25個分行只要有一個分行沒有營業則2-不挑檔，否則為1-當日挑檔
            chkbctl121454();
        } else {
            // 058-BCTLCHK-RTN
            // 特殊代收類別主辦行沒有營業則2-不挑檔
            bctlchk();
        }

        //  WK-RTN=1(當日挑檔),3(退稅憑單挑檔，從上次挑檔日挑到星期五或六為止)
        //   A.更新事業單位基本資料檔，B.GO TO CS-INLOOP-BEGIN讀DB-CLDTL-DDS收付明細檔
        //  WK-RTN=2(延後挑檔)
        //   A.更新事業單位基本資料檔，B.GO TO CS-SORTIN-RTN找下一筆事業單位基本資料檔
        //  其他，GO TO CS-SORTIN-RTN找下一筆事業單位基本資料檔
        switch (pickType) {
            case 1:
                // IF      WK-RTN              =       1
                // MOVE  WK-LPUTDT           TO      DB-CLMR-LLPUTDT
                // MOVE  WK-YYMMDD           TO      DB-CLMR-LPUTDT
                // PERFORM 030-STRCLMR-RTN   THRU    030-STRCLMR-EXIT
                // GO TO CS-INLOOP-BEGIN
                CltmrBus holdCltmr = cltmrService.holdById(code);
                if (!Objects.isNull(holdCltmr)) {
                    holdCltmr.setLlputdt(lputdt);
                    holdCltmr.setLputdt(putDate);
                    cltmrService.update(holdCltmr);
                }
                // CS-INLOOP-BEGIN
                queryCldtl(code, lputdt, putDate);
                break;
            case 2:
                // ELSE IF WK-RTN              =       2
                // MOVE  FD-BHDATE-NBSDY     TO      DB-CLMR-TPUTDT
                // PERFORM 030-STRCLMR-RTN   THRU    030-STRCLMR-EXIT
                // GO TO  CS-SORTIN-RTN
                ClmcBus holdClmc = clmcService.holdById(putname);
                if (!Objects.isNull(holdClmc)) {
                    holdClmc.setTputdt(nbsdy);
                    clmcService.update(holdClmc);
                }
                return;
            case 3:
                // PERFORM  CHKRDAY-RTN      THRU    CHKRDAY-EXIT
                // MOVE  WK-LPUTDT           TO      DB-CLMR-LLPUTDT
                // MOVE  WK-DATE             TO      DB-CLMR-LPUTDT WK-YYMMDD
                // PERFORM 030-STRCLMR-RTN   THRU    030-STRCLMR-EXIT
                chkrday();
                holdCltmr = cltmrService.holdById(code);
                if (!Objects.isNull(holdCltmr)) {
                    holdCltmr.setLlputdt(lputdt);
                    holdCltmr.setLputdt(putDate);
                    cltmrService.update(holdCltmr);
                }
                // GO TO CS-INLOOP-BEGIN
                queryCldtl(code, lputdt, putDate);
                return;
            default:
                // pickType不在預期範圍
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "unexpected pickType = " + pickType);
                return;
        }
    }

    // CHKBCTL-RTN
    private void chkbctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkbctl() CHKBCTL-RTN.");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pickType = {}", pickType);
        // * 無需挑檔時先離開，需挑檔才往下做
        //     IF  WK-RTN = 0     OR K = 25
        //         GO             TO      CHKBCTL-EXIT.

        if (pickType == 0) {
            return;
        }
        //     ADD     1                    TO      K.
        // **   IF      WK-KINBR(K) = 005 OR 011 OR 018 OR 038 OR 039 OR 010
        // **                      OR 023 OR 024
        // **           GO                   TO     CHKBCTL-RTN.

        // 找營業單位控制檔，共25筆，或有不營業單位，才結束
        //     IF      K                    >       25
        //             GO                   TO      CHKBCTL-EXIT.

        // 將DB-BCTL-ACCESS指標移至開始
        //     SET     DB-BCTL-ACCESS        OF   DB-BCTL-DDS TO BEGINNING.

        // FIND DB-BCTL-ACCESS營業單位控制檔，若有誤
        //  若找無資料，顯示訊息，GO TO CHKBCTL-EXIT，結束判斷
        //  其他，顯示錯誤訊息，執行DB99-DMERROR-RTN
        //     FIND    DB-BCTL-ACCESS        OF   DB-BCTL-DDS
        //          AT DB-BCTL-BRNO          =    WK-KINBR(K)
        //          ON EXCEPTION
        //          IF DMSTATUS(NOTFOUND)
        //             DISPLAY "CHKBCTL NOTFOUND WK-KINBR(K):" WK-KINBR(K)
        //             GO TO   CHKBCTL-EXIT
        //          ELSE
        //             DISPLAY "CHKBCTL FAIL WK-KINBR(K):" WK-KINBR(K)
        //             MOVE SPACES             TO WC-ERRMSG-REC
        //             MOVE "FNBCTL"           TO WC-ERRMSG-MAIN
        //             MOVE WK-KINBR(K)        TO WC-ERRMSG-SUB
        //             MOVE 2                  TO WC-ERRMSG-TXRSUT
        //             PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT.

        //  票據:25個分行只要有一個分行沒有營業則2-不挑檔，否則為3-退稅憑單挑檔
        int findKinbr;
        for (int i = 0; i < 25; i++) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "i = {}", i);

            if (parse.isNumeric(WK_KINBR.get(i))) {
                findKinbr = parse.string2Integer(WK_KINBR.get(i));
            } else {
                findKinbr = 0;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findKinbr = {}", findKinbr);
            Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(findKinbr);
            if (Objects.isNull(bctl)) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "CHKBCTL FAIL WK-KINBR(i):{}",
                        findKinbr);
                return;
            }
            // DB-BCTL-RU1TM=86340000不營業，設定延後挑檔，結束判斷
            //     IF      DB-BCTL-RU1TM         =    86340000
            //             MOVE  2               TO   WK-RTN
            //             GO TO   CHKBCTL-EXIT.
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "bctl.getRu1tm() = {}", bctl.getRu1tm());
            if (bctl.getRu1tm() == 86340000) {
                pickType = 2;
                return;
            }
            // 設定退稅憑單挑檔
            //     MOVE  3               TO   WK-RTN.
            pickType = 3;
            // 找下一筆營業單位控制檔
            //     GO                    TO   CHKBCTL-RTN.
            // CHKBCTL-EXIT.
            //     EXIT.
        }
    }

    // CHKBCTL-121454-RTN
    private void chkbctl121454() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "chkbctl121454() CHKBCTL-121454-RTN.");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pickType = {}", pickType);
        // * 無需挑檔時先離開，需挑檔才往下做
        //     IF  WK-RTN = 0     OR K = 25
        //         GO             TO      CHKBCTL-121454-EXIT.
        //     ADD     1                    TO      K.
        if (pickType == 0) {
            return;
        }

        // 找營業單位控制檔，共25筆，或有不營業單位，才結束
        //     IF      K                    >       25
        //             GO                   TO      CHKBCTL-121454-EXIT.
        // 將DB-BCTL-ACCESS指標移至開始
        //     SET     DB-BCTL-ACCESS        OF   DB-BCTL-DDS TO BEGINNING.
        // FIND DB-BCTL-ACCESS營業單位控制檔，若有誤
        //  若找無資料，顯示訊息，GO TO CHKBCTL-121454-EXIT，結束判斷
        //  其他，顯示錯誤訊息，執行DB99-DMERROR-RTN
        //     FIND    DB-BCTL-ACCESS        OF   DB-BCTL-DDS
        //          AT DB-BCTL-BRNO          =    WK-KINBR-121454(K)
        //          ON EXCEPTION
        //          IF DMSTATUS(NOTFOUND)
        //  DISPLAY "121454 NOTFOUND WK-KINBR-121454(K):" WK-KINBR-121454(K)
        //             GO TO   CHKBCTL-121454-EXIT
        //          ELSE
        //   DISPLAY "121454 FAIL WK-KINBR-121454(K):" WK-KINBR-121454(K)
        //             MOVE SPACES             TO WC-ERRMSG-REC
        //             MOVE "FNBCTL"           TO WC-ERRMSG-MAIN
        //             MOVE WK-KINBR-121454(K) TO WC-ERRMSG-SUB
        //             MOVE 2                  TO WC-ERRMSG-TXRSUT
        //             PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT.

        //  25個分行只要有一個分行沒有營業則2-不挑檔，否則為1-當日挑檔
        int findKinbr;
        for (int i = 0; i < 25; i++) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "i = {}", i);
            if (parse.isNumeric(WK_KINBR_121454.get(i))) {
                findKinbr = parse.string2Integer(WK_KINBR_121454.get(i));
            } else {
                findKinbr = 0;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findKinbr = {}", findKinbr);
            Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(findKinbr);
            if (Objects.isNull(bctl)) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "chkbctl121454 FAIL wkKinbr121454(K):{}",
                        findKinbr);
                return;
            }

            // DB-BCTL-RU1TM=86340000不營業，設定延後挑檔，結束判斷
            //     IF      DB-BCTL-RU1TM         =    86340000
            //             MOVE  2               TO   WK-RTN
            //             GO TO   CHKBCTL-121454-EXIT.
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "bctl.getRu1tm() = {}", bctl.getRu1tm());
            if (bctl.getRu1tm() == 86340000) {
                pickType = 2;
                return;
            }

            // 設定當日挑檔
            //     MOVE  1               TO   WK-RTN.
            pickType = 1;

            // 找下一筆營業單位控制檔
            //     GO                    TO   CHKBCTL-121454-RTN.
        }

        // CHKBCTL-121454-EXIT.
        //     EXIT.
    }

    // 058-BCTLCHK-RTN
    private void bctlchk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "bctlchk() 058-BCTLCHK-RTN.");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pickType = {}", pickType);
        //     MOVE    DB-CLMR-CODE          TO     WK-CODE.
        // 特定代收類別，且需挑檔時，才需再判斷是否需延後挑檔
        // 其餘GO TO 058-BCTLCHK-EXIT，結束判斷
        //     IF    ( WK-CODE1 NOT < "11011" AND  WK-CODE1 NOT > "11115")
        //        OR ( WK-CODE1 =     "11331"                            )
        //        OR ( WK-CODE1 NOT < "11521" AND  WK-CODE1 NOT > "11525")
        //        OR ( WK-CODE =     "366ZA9") OR (WK-CODE =     "115892")
        //        OR ( WK-CODE =     "366ZB9") OR (WK-CODE =     "115902")
        //        OR ( WK-CODE =     "366309") OR (WK-CODE =     "115702")
        //       IF  WK-RTN                  =     0
        //         GO TO  058-BCTLCHK-EXIT
        //       ELSE
        //         NEXT SENTENCE
        //     ELSE
        //         GO TO  058-BCTLCHK-EXIT.
        // *
        String codeLeft5 = code.substring(0, 5);
        int wkCode1 = parseIntegerWithoutError(codeLeft5);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode1 = {}", wkCode1);
        if ((wkCode1 >= 11011 && wkCode1 <= 11115)
                || (wkCode1 == 11331)
                || (wkCode1 >= 11521 && wkCode1 <= 11525)
                || code.equals("366ZA9")
                || code.equals("115892")
                || code.equals("366ZB9")
                || code.equals("115902")
                || code.equals("366309")
                || code.equals("115702")) {
            if (pickType == 0) {
                return;
            } else {
                // NEXT SENTENCE
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "NEXT SENTENCE");
            }
        } else {
            return;
        }
        // 依DB-CLMR-PBRNO主辦分行 FIND DB-BCTL-ACCESS，若有誤
        //  若 找不到，顯示訊息，GO TO 058-BCTLCHK-EXIT，結束判斷
        //  其餘，顯示錯誤訊息，執行DB99-DMERROR-RTN
        //     SET     DB-BCTL-ACCESS        OF   DB-BCTL-DDS TO BEGINNING.
        //     FIND    DB-BCTL-ACCESS        OF   DB-BCTL-DDS
        //          AT DB-BCTL-BRNO          =    DB-CLMR-PBRNO	<-主辦分行
        //          ON EXCEPTION
        //          IF DMSTATUS(NOTFOUND)
        //       DISPLAY "058-BCTLCHK NOTFOUND DB-CLMR-PBRNO:" DB-CLMR-PBRNO
        //             GO TO   058-BCTLCHK-EXIT
        //          ELSE
        //           DISPLAY "058-BCTLCHK FAIL DB-CLMR-PBRNO:" DB-CLMR-PBRNO
        //             MOVE SPACES             TO WC-ERRMSG-REC
        //             MOVE "FNBCTL"           TO WC-ERRMSG-MAIN
        //             MOVE DB-CLMR-PBRNO      TO WC-ERRMSG-SUB
        //             MOVE 2                  TO WC-ERRMSG-TXRSUT
        //             PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT.
        int pbrno = clmr.getPbrno();
        Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(pbrno);

        if (Objects.isNull(bctl)) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "chkbctl121454 FAIL pbrno:{}", pbrno);
            return;
        }
        // *    DISPLAY "DB-BCTL-RU1TM="DB-BCTL-RU1TM.
        // DB-BCTL-RU1TM=86340000不營業，設定延後挑檔
        //     IF      DB-BCTL-RU1TM         =    86340000
        //             MOVE  2               TO   WK-RTN.
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "bctl.getRu1tm() = {}", bctl.getRu1tm());
        if (bctl.getRu1tm() == 86340000) {
            pickType = 2;
        }
        // 058-BCTLCHK-EXIT.
        //     EXIT.
    }

    private int parseIntegerWithoutError(String input) {
        if (parse.isNumeric(input)) {
            return parse.string2Integer(input);
        } else {
            return 0;
        }
    }

    // 020-CHK-RTN
    private void chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk()");
        // 此段落為判斷挑檔與否
        // 清挑檔記號為0(0-不挑檔、1-挑檔)
        // MOVE      0                   TO     WK-RTN.
        pickType = 0;
        // 銷帳媒體產生週期=5，無此需求，結束挑檔判斷
        // IF        DB-CLMR-CYCK1       =      5
        //     GO TO 020-CHK-EXIT.
        if (clmc.getCyck1() == 5) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "clmc.getCyck1() = {}", clmc.getCyck1());
            return;
        }
        // DB-CLMR-TPUTDT臨時挑檔日>=本營業日，
        // A.執行027-CHKTPUTDT-RTN挑檔判斷，B.若需挑檔則結束挑檔判斷，否則執行下一步驟
        // IF        DB-CLMR-TPUTDT      NOT <  WK-YYMMDD
        //   PERFORM 027-CHKTPUTDT-RTN   THRU   027-CHKTPUTDT-EXIT
        //   IF      WK-RTN              =      1
        //           GO TO 020-CHK-EXIT.
        if (clmc.getTputdt() >= tbsdy) {
            chkTputdt();
            if (pickType == 1) {
                return;
            }
        }
        // 銷帳媒體產生週期=0 & 銷帳媒體產生週期日=0(以指定日期要媒體) & DB-CLMR-PUTDT指定挑檔日>=本營業日
        // A.執行028-CHKPUTDT-RTN挑檔判斷，B.結束挑檔判斷
        // IF        DB-CLMR-CYCK1       =      0
        //    AND    DB-CLMR-CYCNO1      =      0
        //    AND    DB-CLMR-PUTDT       NOT <  WK-YYMMDD
        //   PERFORM 028-CHKPUTDT-RTN  THRU   028-CHKPUTDT-EXIT
        //   GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 0 && clmc.getCycno1() == 0 && clmc.getPutdt() >= this.tbsdy) {
            chkPutdt();
            return;
        }
        // 銷帳媒體產生週期=1(週期日數)，A.執行021-RAN-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      1
        //       PERFORM 021-RAN-RTN         THRU   021-RAN-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 1) {
            ran();
            return;
        }

        // 銷帳媒體產生週期=2(星期幾)，A.執行022-WEEK-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      2
        //       PERFORM 022-WEEK-RTN        THRU   022-WEEK-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 2) {
            week();
            return;
        }

        // 銷帳媒體產生週期=3(每月幾日)，A.執行023-MON-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      3
        //       PERFORM 023-MON-RTN         THRU   023-MON-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 3) {
            mon();
            return;
        }

        // 銷帳媒體產生週期=4(每旬)& 銷帳媒體產生週期日=10，A.執行024-CHK10-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      4
        //           AND DB-CLMR-CYCNO1      =      10
        //       PERFORM 024-CHK10-RTN       THRU   024-CHK10-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 4 && clmc.getCycno1() == 10) {
            chk10();
            return;
        }

        // 銷帳媒體產生週期=4(每旬)& 銷帳媒體產生週期日=15，A.執行025-CHK15-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      4
        //           AND DB-CLMR-CYCNO1      =      15
        //       PERFORM 025-CHK15-RTN       THRU   025-CHK15-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 4 && clmc.getCycno1() == 15) {
            chk15();
            return;
        }

        // 銷帳媒體產生週期=4(每旬)& 銷帳媒體產生週期日=30，A.執行026-CHK30-RTN挑檔判斷，B.結束挑檔判斷
        //     IF        DB-CLMR-CYCK1       =      4
        //           AND DB-CLMR-CYCNO1      =      30
        //       PERFORM 026-CHK30-RTN       THRU   026-CHK30-EXIT
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck1() == 4 && clmc.getCycno1() == 30) {
            chk30();
            return;
        }

        // 銷帳媒體產生週期 & 銷帳媒體產生週期日 非以上條件時，顯示錯誤訊息
        //     DISPLAY  "020-CHK-RTN ERROR" DB-CLMR-CYCK1 , DB-CLMR-CYCNO1,
        //                                  DB-CLMR-PUTDT.
        //     MOVE      0                   TO     WK-RTN.
        // 020-CHK-EXIT.
        //     EXIT.
        ApLogHelper.error(
                log,
                false,
                LogType.NORMAL.getCode(),
                "chk() ERROR clmc.getCyck1() = {} , clmc.getCycno1() = {} , clmc.getPutdt() = {} ,"
                        + " tbsdy = {} , nbsdy = {}",
                clmc.getCyck1(),
                clmc.getCycno1(),
                clmc.getPutdt(),
                tbsdy,
                nbsdy);
    }

    // 021-RAN-RTN
    private void ran() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ran()");
        // 銷帳媒體產生週期=1(週期日數)之判斷
        // 銷帳媒體產生週期日=1(每日皆需挑檔)，挑檔記號=1，結束判斷
        //     MOVE    DB-CLMR-CYCNO1      TO     WK-CYCNO1.
        // * 若銷帳媒體產生週期為１０１時，不需檢查，每日皆需挑檔
        //     IF      WK-CYCNO1           =      1
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 021-RAN-EXIT.
        int cycno1 = clmc.getCycno1();
        if (cycno1 == 1) {
            pickType = 1;
            return;
        }

        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 021-LOOP1.
        // 往上一日期找日曆檔
        //     SUBTRACT 1                  FROM   WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 上一日期是營業日，週期日數減1，減到0為止；
        // 若週期日數>0或上一日期是非營業日時，再往上一日期找
        //  週期日數=0時，
        //   若FD-CLNDR-TBSDY=DB-CLMR-LPUTDT上次CYC1挑檔日，挑檔記號=1
        //   否則，挑檔記號=0
        //       SUBTRACT 1                FROM   WK-CYCNO1
        //       IF       WK-CYCNO1        =      0
        // **--  若是 BATCH 有漏跑過 , 此 CHECK 將不過 -------------*
        //         IF     FD-CLNDR-TBSDY   =      DB-CLMR-LPUTDT
        //           MOVE 1                TO     WK-RTN
        //         ELSE
        //           MOVE 0                TO     WK-RTN
        //       ELSE
        //         GO TO  021-LOOP1
        //     ELSE
        // 上一日期是非營業日，再往上一日期找
        //         GO TO  021-LOOP1.
        // 021-RAN-EXIT.
        //     EXIT.
        int resultRocDay = findRocBusinessDate(tbsdy, 0 - cycno1);

        if (resultRocDay == cltmr.getLputdt()) {
            pickType = 1;
            return;
        }
        pickType = 0;
    }

    /**
     * Finds a ROC business date relative to a specified date.
     *
     * @param tbsdy The reference ROC business date in YYYYMMDD format.
     * @param n The number of business days to move from the reference date. A negative number moves
     *     backwards in time (past), while a positive number moves forwards (future). For example,
     *     -1 refers to the previous business day, and 1 refers to the next business day.
     * @return The calculated ROC business date in YYYYMMDD format.
     */
    private int findRocBusinessDate(int tbsdy, int n) {
        // 待測通
        // fsapSync.sy202ForAp(event.getPeripheryRequest(), "20240101", "20230103");
        // todo:使用小潘工具算營業日
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDays(n);
        dateUtil.getCalenderDay(dateDto);
        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(
                        event.getPeripheryRequest(),
                        dateUtil.getNowStringRoc(),
                        dateUtil.getNowStringRoc());
        if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
            fdClndr = txBizDates.get(0);
            return fdClndr.getTbsdy();
        }
        return 0;
    }

    // 022-WEEK-RTN
    private void week() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "week()");
        // 銷帳媒體產生週期=2(星期幾)之判斷
        //     MOVE    DB-CLMR-CODE        TO     WK-CODE.

        // 特定代收類別，A.執行051-TAXPUTH-RTN判斷需挑檔否，B.結束判斷
        // WK-CODE1=code(1:5)
        //     IF    ( WK-CODE1 NOT < "11011" AND  WK-CODE1 NOT > "11115")
        //        OR ( WK-CODE1 =     "11331"                            )
        //        OR ( WK-CODE1 NOT < "11521" AND  WK-CODE1 NOT > "11525")
        //           PERFORM  051-TAXPUTH-RTN  THRU  051-TAXPUTH-EXIT
        //           GO  TO   022-WEEK-EXIT.
        String codeLeft5 = code.substring(0, 5);
        if (parse.isNumeric(codeLeft5)) {
            int wkCode1 = parse.string2Integer(codeLeft5);
            if ((wkCode1 >= 11011 && wkCode1 <= 11115)
                    || (wkCode1 == 11331)
                    || (wkCode1 >= 11521 && wkCode1 <= 11525)) {
                taxputh();
                return;
            }
        }

        // 銷帳媒體產生週期日=星期幾FD-BHDATE-WEEKDY，
        //  A.挑檔記號=1，B.跟主檔設定週期一致(WK-FLG=1)，C.結束判斷
        // * 跟主檔設定週期一致，將退稅依的 WK-FLG 設定成 1
        //     IF      DB-CLMR-CYCNO1      =      FD-BHDATE-WEEKDY
        //       MOVE  1                   TO     WK-FLG
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT.
        int weekdy = getWeekdy(tbsdy);
        if (clmc.getCycno1() == weekdy) {
            weekFlag = 1;
            pickType = 1;
            return;
        }

        // 往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔

        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 022-LOOP1.

        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1

        // 下一日期是營業日，挑檔記號=0，結束判斷

        //       MOVE  0                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT
        //     ELSE

        // 下一日期是非營業日，
        //  若符合條件，A.挑檔記號=1，B.跟主檔設定週期一致(WK-FLG=1)，C.結束判斷；否則再往下一日期找

        // * 假如下一日期是非營業日，也可提早挑檔
        //      IF     DB-CLMR-CYCNO1      =      FD-CLNDR-WEEKDY
        //       MOVE  1                   TO     WK-FLG
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT
        //      ELSE
        //       GO TO 022-LOOP1.
        // 022-WEEK-EXIT.
        //     EXIT.

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), "" + tbsdy, "" + nbsdy);
        for (TxBizDate tBizDate : txBizDates) {
            if (tBizDate.getTbsdy() > tbsdy && tBizDate.getTbsdy() < nbsdy) {
                if (tBizDate.getHoliday() == 1) {
                    if (clmc.getCycno1() == tBizDate.getWeekdy()) {
                        weekFlag = 1;
                        pickType = 1;
                        return;
                    }
                }
            }
        }
    }

    // 023-MON-RTN
    private void mon() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mon()");
        // 銷帳媒體產生週期=3(每月幾日)之判斷
        // 銷帳媒體產生週期日=FD-BHDATE-TBSDD，挑檔記號=1，結束判斷
        //     IF      DB-CLMR-CYCNO1      =      FD-BHDATE-TBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 023-MON-EXIT.
        int tbsdd = tbsdy % 100;
        if (tbsdd == clmc.getCycno1()) {
            pickType = 1;
            return;
        }

        // 若日期不符，往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 023-LOOP1.
        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 下一日期是營業日，挑檔記號=0，結束判斷
        //       MOVE  0                   TO     WK-RTN
        //       GO TO 023-MON-EXIT
        //     ELSE
        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     DB-CLMR-CYCNO1      =      FD-CLNDR-TBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 023-MON-EXIT
        //      ELSE
        //       GO TO 023-LOOP1.
        // 023-MON-EXIT.
        //     EXIT.
        int nbsdd = nbsdy % 100;
        if (tbsdd < nbsdd && clmc.getCycno1() != 0) { // case for 同月
            if (tbsdd < clmc.getCycno1() && clmc.getCycno1() < nbsdd) {
                // 目標日期為非營業日
                pickType = 1;
                return;
            }
        } else if (tbsdd > nbsdd && clmc.getCycno1() != 0) { // case for 跨月
            // 舉例,若某一年過農曆年時跨國曆一月底
            // 可能會出現本營業日為0128(除夕前最後一天上班日)
            // tbsdd = 28
            // 下營業日為0204(初五開工日)
            // nbsdd = 04
            // 此時, 挑檔日cycno1 設定為 (29,30,31,01,02,03) 者,皆要挑檔
            if (tbsdd < clmc.getCycno1() || clmc.getCycno1() < nbsdd) {
                // 目標日期為非營業日
                pickType = 1;
                return;
            }
        }
        pickType = 0;
    }

    // 024-CHK10-RTN
    private void chk10() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk10()");
        // 銷帳媒體產生週期=4(每旬) & 銷帳媒體產生週期日=10 之判斷
        // 銷帳媒體產生週期日=10 OR 20 OR 本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =  10 OR 20 OR FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 024-CHK10-EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        if (tbsdd == 10 || tbsdd == 20 || tbsdd == fnbsdd) {
            pickType = 1;
            return;
        }
        // 若日期不符，往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 024-LOOP1.
        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 下一日期是營業日，挑檔記號=0，結束判斷
        //       MOVE  0                   TO     WK-RTN
        //       GO TO 024-CHK10-EXIT
        //     ELSE
        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     FD-CLNDR-TBSDD      =      10  OR  20
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 024-CHK10-EXIT
        //      ELSE
        //       GO TO 024-LOOP1.
        // 024-CHK10-EXIT.
        //     EXIT.
        int nbsdd = nbsdy % 100;
        // 10號跟20號在非營業日的情況,挑檔
        if ((tbsdd < 10 && 10 < nbsdd) || (tbsdd < 20 && 20 < nbsdd)) {
            pickType = 1;
            return;
        }
        pickType = 0;
    }

    // 025-CHK15-RTN
    private void chk15() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk15()");
        // 銷帳媒體產生週期=4(每旬) & 銷帳媒體產生週期日=15 之判斷
        // 銷帳媒體產生週期日=15 OR 本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =  15 OR FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 025-CHK15-EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        if (tbsdd == 15 || tbsdd == fnbsdd) {
            pickType = 1;
            return;
        }
        // 若日期不符，往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 025-LOOP1.
        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 下一日期是營業日，挑檔記號=0，結束判斷
        //       MOVE  0                   TO     WK-RTN
        //       GO TO 025-CHK15-EXIT
        //     ELSE
        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     FD-CLNDR-TBSDD      =      15
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 025-CHK15-EXIT
        //      ELSE
        //       GO TO 025-LOOP1.
        // 025-CHK15-EXIT.
        //     EXIT.
        int nbsdd = nbsdy % 100;
        // 15號在非營業日的情況,挑檔
        if (tbsdd < 15 && 15 < nbsdd) {
            pickType = 1;
            return;
        }
        pickType = 0;
    }

    // 026-CHK30-RTN
    private void chk30() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk30()");
        // 銷帳媒體產生週期日=本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =      FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 026-CHK30-EXIT.
        // 026-CHK30-EXIT.
        //     EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        if (tbsdd == fnbsdd) {
            pickType = 1;
            return;
        }
        pickType = 0;
    }

    // 027-CHKTPUTDT-RTN
    private void chkTputdt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkTputdt()");
        // 臨時挑檔日之判斷
        // DB-CLMR-TPUTDT臨時挑檔日=本營業日，挑檔記號=1，結束判斷
        //     MOVE    DB-CLMR-CODE        TO     WK-CODE.
        //     IF      DB-CLMR-TPUTDT      =      WK-YYMMDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 027-CHKTPUTDT-EXIT.
        // 若日期不符，往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 027-LOOP1.
        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1

        // 下一日期是營業日，挑檔記號=0，結束判斷
        //       MOVE  0                   TO     WK-RTN
        //       GO TO 027-CHKTPUTDT-EXIT
        //     ELSE
        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     DB-CLMR-TPUTDT      =      FD-CLNDR-TBSDY
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 027-CHKTPUTDT-EXIT
        //      ELSE
        //       GO TO 027-LOOP1.
        // 027-CHKTPUTDT-EXIT.

        if (clmc.getTputdt() >= tbsdy && clmc.getTputdt() < nbsdy) {
            pickType = 1;
        }
    }

    // 028-CHKPUTDT-RTN
    private void chkPutdt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkPutdt()");
        // 銷帳媒體產生週期=0 & 銷帳媒體產生週期日=0(以指定日期要媒體)之判斷
        // DB-CLMR-PUTDT指定挑檔日=本營業日，挑檔記號=1，結束判斷
        //     IF      DB-CLMR-PUTDT       =      WK-YYMMDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 028-CHKPUTDT-EXIT.
        // 若日期不符，往下一日期找日曆檔，假如下一日期是非營業日，也可提早挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 028-LOOP1.
        // 往下一日期找日曆檔
        // 假如下一日期是非營業日，也可提早挑檔
        //     ADD     1                   TO     WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 下一日期是營業日，挑檔記號=0，結束判斷
        //       MOVE  0                   TO     WK-RTN
        //       GO TO 028-CHKPUTDT-EXIT
        //     ELSE
        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     DB-CLMR-PUTDT      =      FD-CLNDR-TBSDY
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 028-CHKPUTDT-EXIT
        //      ELSE
        //       GO TO 028-LOOP1.
        // 028-CHKPUTDT-EXIT.
        //     EXIT.
        if (clmc.getPutdt() >= tbsdy && clmc.getPutdt() < nbsdy) {
            pickType = 1;
        }
    }

    // 051-TAXPUTH-RTN
    private void taxputh() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxputh()");
        // *  稅費解繳作業媒體產生採用的是延後挑檔機制
        // *  地方稅為每週解繳一次 / 非直轄市國稅每週一四 / 042100*  直轄市國稅每日解繳
        // *  除燃料稅外其他類稅款均需在次年的第一個營業日解繳
        // *  代收起訖日期 : 年末最後一週的第一個營業日到 12/31
        // *  另稅款單位於每月月初挑檔之需求
        // *  其餘說明可參考稅費解繳媒體給檔說明文件
        //     MOVE    0                   TO     WK-RTN.
        //     MOVE    DB-CLMR-CODE        TO     WK-CODE.
        // WK-CODE1=code(1:5)、WK-CODE1-2=code(5:1)
        pickType = 0;
        String wkCode1 = code.substring(0, 5);
        String wkCode12 = code.substring(4, 5);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode1 = {}", wkCode1);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode12 = {}", wkCode12);
        // *  稅費代收週挑檔
        // *  非直轄市地方稅和燃料費 : 每週一次
        //     IF  (( WK-CODE1 NOT < "11011" AND  WK-CODE1 NOT > "11115"  )
        //      AND ( WK-CODE1-2 = "2" OR "3" OR "7" OR "8" OR "1" OR "6" ))
        //      OR (  WK-CODE1 =   "11331"  OR  "11521"                    )
        //     PERFORM 054-TAXWEEK-RTN     THRU   054-TAXWEEK-EXIT.
        int intWkCode1;
        int intWkCode12;
        if (parse.isNumeric(wkCode1) && parse.isNumeric(wkCode12)) {
            intWkCode1 = parse.string2Integer(wkCode1);
            intWkCode12 = parse.string2Integer(wkCode12);
            if (((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                            && (intWkCode12 == 2
                                    || intWkCode12 == 3
                                    || intWkCode12 == 7
                                    || intWkCode12 == 8
                                    || intWkCode12 == 1
                                    || intWkCode12 == 6))
                    || (intWkCode1 == 11331 || intWkCode1 == 11521)) {
                taxweek054();
            }
        } else {
            // 若收付類別有非數字型態,結束此段程式
            return;
        }

        // *  非直轄市國稅及直轄市地方稅 : 每週一四
        //     IF  (( WK-CODE1 NOT < "11011" AND  WK-CODE1 NOT > "11115"  )
        //      AND ( WK-CODE1-2 = "4" OR "5" OR "9" OR "0"               ))
        //      OR (  WK-CODE1 =   "11523"                                 )
        //     PERFORM 055-TAXWEEK-RTN     THRU   055-TAXWEEK-EXIT.
        if (((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                        && (intWkCode12 == 4
                                || intWkCode12 == 5
                                || intWkCode12 == 9
                                || intWkCode12 == 0))
                || (intWkCode1 == 11523)) {
            taxweek055();
        }

        // *  稅費代收月挑檔
        //     IF      WK-CODE1            =      "11012"
        //                             OR  =      "11013"
        //                             OR  =      "11107"
        //                             OR  =      "11108"
        //                             OR  =      "11031"
        //                             OR  =      "11032"
        //                             OR  =      "11033"
        //     PERFORM 056-TAXMON-RTN      THRU   056-TAXMON-EXIT.

        if (intWkCode1 == 11012
                || intWkCode1 == 11013
                || intWkCode1 == 11107
                || intWkCode1 == 11108
                || intWkCode1 == 11031
                || intWkCode1 == 11032
                || intWkCode1 == 11033) {
            taxmon();
        }

        // 059-TAXCLOSE-RTN強迫設定挑檔條件比057-TAXYEAR-RTN寬，所以057-TAXYEAR-RTN變多餘的

        // *  稅費代收年挑檔
        //     IF     FD-BHDATE-TBSMM      =      01
        //       PERFORM 057-TAXYEAR-RTN   THRU   057-TAXYEAR-EXIT.

        int tbsmm = tbsdy / 100 % 10000;
        if (tbsmm == 1) {
            taxyear();
        }

        // *  新制稅款代收上線  99/07/02  稅類代收類別強迫挑檔
        //     PERFORM 059-TAXCLOSE-RTN    THRU   059-TAXCLOSE-EXIT.
        // 051-TAXPUTH-EXIT.
        //     EXIT.
        taxclose();
    }

    // 054-TAXWEEK-RTN
    private void taxweek054() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxweek054()");
        // * 今天星期若合主檔週期符合就挑檔
        //     IF      DB-CLMR-CYCNO1      =      FD-BHDATE-WEEKDY
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 054-TAXWEEK-EXIT.
        int weekdy = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "weekdy = {}", weekdy);
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "clmc.getCycno1() = {}", clmc.getCycno1());
        if (clmc.getCycno1() == weekdy) {
            pickType = 1;
            return;
        }
        if (cltmr.getLputdt() >= tbsdy || cltmr.getLputdt() == 0) {
            return;
        }
        // * 若今天星期大於挑檔週期且尚未挑檔　就需挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // WK-DATE-COUNT多餘
        //     MOVE    0                   TO     WK-DATE-COUNT.
        // 往上一日期找日曆資料
        // 054-TAXWEEK-LOOP.
        //     SUBTRACT 1                  FROM   WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        //     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        //             GO TO 0000-END-RTN.
        // 上次挑檔日小於這一日
        //      IF DB-CLMR-LPUTDT          <      FD-CLNDR-TBSDY
        //         IF  FD-CLNDR-HOLIDY     =      1
        // 這一日是非營業日
        //  若符合主檔週期條件，挑檔記號=1，結束判斷；否則再往上一日期找
        //           IF DB-CLMR-CYCNO1     =      FD-CLNDR-WEEKDY
        //             MOVE  1                 TO     WK-RTN
        //             GO TO 054-TAXWEEK-EXIT
        //           ELSE
        //             GO TO 054-TAXWEEK-LOOP
        //         ELSE
        // 這一日是營業日，再往上一日期找
        //             GO TO 054-TAXWEEK-LOOP
        //      ELSE
        // 上次挑檔日大於等於這一日，結束判斷
        //             GO TO 054-TAXWEEK-EXIT.
        // 054-TAXWEEK-EXIT.
        //     EXIT.
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "cltmr.getLputdt()  = {}", cltmr.getLputdt());
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lbsdy = {}", lbsdy);
        // 依週期差找設定cycno1的日期，如為上次挑檔日小於該日且該日為假日則挑檔

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(
                        event.getPeripheryRequest(), "" + tbsdy, "" + cltmr.getLputdt());
        for (TxBizDate tBizDate : txBizDates) {
            if (tBizDate.getTbsdy() < tbsdy && tBizDate.getTbsdy() > cltmr.getLputdt()) {
                if (tBizDate.getHoliday() == 1) {
                    if (clmc.getCycno1() == tBizDate.getWeekdy()) {
                        pickType = 1;
                        return;
                    }
                }
            }
        }
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    // 055-TAXWEEK-RTN
    private void taxweek055() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxweek055()");
        // * 本日等於星期四或星期一就挑檔
        //     IF      FD-BHDATE-WEEKDY    =      4  OR  =  1
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 055-TAXWEEK-EXIT.
        int weekdy = this.getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "weekdy = {}", weekdy);
        if (weekdy == 4 || weekdy == 1) {
            pickType = 1;
        }
        if (cltmr.getLputdt() >= tbsdy || cltmr.getLputdt() == 0) {
            return;
        }
        // * 若今天星期大於挑檔週期且尚未挑檔　就需挑檔
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        //     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // WK-DATE-COUNT多餘
        //     MOVE    0                   TO     WK-DATE-COUNT.
        // 往上一日期找日曆資料
        // 055-TAXWEEK-LOOP.
        //     SUBTRACT 1                  FROM   WK-CLNDR-KEY.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        //     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        //             GO TO 0000-END-RTN.
        // 上次挑檔日小於這一日
        //      IF DB-CLMR-LPUTDT          <      FD-CLNDR-TBSDY
        //         IF  FD-CLNDR-HOLIDY     =      1
        // 這一日是非營業日
        //  若這一日是星期四或一，挑檔記號=1，結束判斷；否則再往上一日期找
        //           IF FD-CLNDR-WEEKDY    =      4   OR  = 1
        //             MOVE  1                 TO     WK-RTN
        //             GO TO 055-TAXWEEK-EXIT
        //           ELSE
        //             GO TO 055-TAXWEEK-LOOP
        //         ELSE
        // 這一日是營業日，再往上一日期找
        //             GO TO 055-TAXWEEK-LOOP
        //      ELSE
        // 上次挑檔日 大於等於 這一日
        //             GO TO 055-TAXWEEK-EXIT.

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(
                        event.getPeripheryRequest(), "" + tbsdy, "" + cltmr.getLputdt());
        for (TxBizDate tBizDate : txBizDates) {
            if (tBizDate.getTbsdy() < tbsdy && tBizDate.getTbsdy() > cltmr.getLputdt()) {
                if (tBizDate.getHoliday() == 1) {
                    if (tBizDate.getWeekdy() == 4 || tBizDate.getWeekdy() == 1) {
                        pickType = 1;
                        return;
                    }
                }
            }
        }
        // 055-TAXWEEK-EXIT.
        //     EXIT.
    }

    // 056-TAXMON-RTN
    private void taxmon() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxmon()");
        // 每月1號或跨月第一個營業日時(本營業日月數 <>上營業日月數)，強迫挑檔(挑檔記號=1)，結束判斷
        //     IF      FD-BHDATE-TBSDD     =      1
        //       MOVE  1                 TO     WK-RTN
        //       GO TO 056-TAXMON-EXIT.
        int tbsdd = tbsdy % 100;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdd = {}", tbsdd);
        if (tbsdd == 1) {
            pickType = 1;
        }
        //     IF      FD-BHDATE-TBSMM     NOT =  FD-BHDATE-LBSMM
        //         MOVE  1                 TO     WK-RTN
        //         GO TO 056-TAXMON-EXIT.
        int tbsmm = tbsdy / 100 % 100;
        int lbsmm = lbsdy / 100 % 100;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lbsdy = {}", lbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsmm = {}", tbsmm);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lbsmm = {}", lbsmm);
        if (tbsmm != lbsmm) {
            pickType = 1;
        }
        // 056-TAXMON-EXIT.
        //     EXIT.
    }

    // 057-TAXYEAR-RTN
    private void taxyear() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxyear()");
        // WK-CODE(5:1)="1","6"，不強迫
        // 其他，跨年度第一個營業日時(本營業日年度 <>上營業日年度)，強迫挑檔(挑檔記號=1)，結束判斷
        //     MOVE    DB-CLMR-CODE        TO     WK-CODE.
        //     IF      WK-CODE1-2          = "1"  OR = "6"
        //       GO TO 057-TAXYEAR-EXIT.
        //     IF      FD-BHDATE-TBSYY     NOT =  FD-BHDATE-LBSYY
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 057-TAXYEAR-EXIT.
        // 057-TAXYEAR-EXIT.
        //     EXIT.
        String wkCode12 = code.substring(4, 5);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode12 = {}", wkCode12);
        if (wkCode12.equals("1") || wkCode12.equals("6")) {
            return;
        }
        int tbsyy = tbsdy / 10000;
        int lbsyy = lbsdy / 10000;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsyy = {}", tbsyy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lbsyy = {}", lbsyy);
        if (tbsyy != lbsyy) {
            pickType = 1;
        }
    }

    // 059-TAXCLOSE-RTN
    private void taxclose() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxclose()");
        //     MOVE    DB-CLMR-CODE        TO     WK-CODE.
        // WK-CODE(5:1)="1","6"，不強迫
        // 其他，新制稅款代收上線 99/07/02以後  稅類代收類別強迫挑檔(挑檔記號=1)，結束判斷
        //     IF      WK-CODE1-2          = "1"  OR = "6"
        //       GO TO 059-TAXCLOSE-EXIT.
        //     IF (    FD-BHDATE-TBSDY     =      990702  )  OR
        //        (    FD-BHDATE-TBSDY     >      990702  )
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 059-TAXCLOSE-EXIT.
        // 059-TAXCLOSE-EXIT.
        //     EXIT.
        String wkCode12 = code.substring(4, 5);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode12 = {}", wkCode12);
        if (wkCode12.equals("1") || wkCode12.equals("6")) {
            return;
        }
        if (tbsdy >= 990702) {
            pickType = 1;
        }
    }

    // CS-INLOOP-BEGIN
    private void queryCldtl(String code, int findEntdyStart, int findEntdyEnd) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "queryCldtl()");
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "findEntdyStart = {}", findEntdyStart);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findEntdyEnd = {}", findEntdyEnd);

        int cldtlPageCnt = 10000;
        List<CldtlByCodeEntdyRangeBus> cldtlList =
                cldtlService.findByCodeEntdyRange(
                        code, findEntdyStart, findEntdyEnd, 0, cldtlPageIndex, cldtlPageCnt);
        if (Objects.isNull(cldtlList) || cldtlList.isEmpty()) {
            if (cldtlPageIndex == 0) {
                if (msg2 == 1 && "0".equals(prtype)) {
                    // 若為第一次查詢就沒資料,需寫一筆該收付類別沒資料
                    StringBuilder sb;
                    sb = new StringBuilder();
                    sb.append(formatUtil.pad9("" + puttype, 2));
                    sb.append(putsend);
                    sb.append(putform);
                    sb.append(putname);
                    sb.append(code);
                    if (putname.equals(code)) {
                        sb.append(formatUtil.padX(" NO DATA 1", 26));
                    } else {
                        sb.append(formatUtil.padX(" NO DATA 0", 26));
                    }
                    sb.append(formatUtil.pad9("", 8));
                    sb.append(formatUtil.pad9("", 6));
                    sb.append(formatUtil.pad9("", 12));
                    sb.append(formatUtil.pad9("", 3));
                    sb.append(formatUtil.pad9("", 8));
                    sb.append(formatUtil.padX("", 40));
                    sb.append(formatUtil.pad9("", 8));
                    sb.append(formatUtil.padX("", 1));
                    sb.append(formatUtil.pad9("", 6));
                    sb.append(formatUtil.pad9("" + clmr.getPbrno(), 3));
                    // PUTH 每一行 180 byte
                    fileContents.add(formatUtil.padX(sb.toString(), 180));
                }
            }
            return;
        }
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "cldtlPageIndex = {}", cldtlPageIndex);
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "cldtlList size = {}", cldtlList.size());
        for (CldtlByCodeEntdyRangeBus cldtl : cldtlList) {
            filePuth.setPuttype("" + puttype);
            filePuth.setPutname(putsend + putform + putname);
            filePuth.setCode(code);
            filePuth.setRcptid(cldtl.getRcptid());
            filePuth.setEntdy("" + cldtl.getEntdy());
            filePuth.setTime("" + cldtl.getTime());
            filePuth.setCllbr("" + cldtl.getCllbr());
            filePuth.setLmtdate("" + cldtl.getLmtdate());
            filePuth.setAmt("" + cldtl.getAmt());
            filePuth.setUserdata(processUserData(cldtl.getUserdata()));
            filePuth.setSitdate("" + cldtl.getSitdate());
            filePuth.setTxtype(cldtl.getTxtype());
            filePuth.setSerino("" + cldtl.getSerino());
            filePuth.setPbrno("" + cldtl.getPbrno());
            //            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); //
            // 設定解析環境
            fileContents.add(vo2TextFormatter.formatRS(filePuth, false));
            // 若筆數已達10000筆,先寫入檔案再繼續查
            if (fileContents.size() >= 10000) {
                writeFile();
                fileContents = new ArrayList<>();
            }
        }
        // 2024-01-18 Wei: 當此頁筆數與每頁筆數相同時,視為可能還有資料,遞迴處理
        if (cldtlList.size() == cldtlPageCnt) {
            cldtlPageIndex++;
            queryCldtl(code, findEntdyStart, findEntdyEnd);
        }
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePath = " + filePath);
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
        ApLogHelper.error(
                log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath =
                File.separator
                        + wkFsapYYYYMMDD
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }

    private void chkrday() {
        // 3-退稅憑單挑檔
        // CHKRDAY-RTN.
        // 如果是每周正常挑檔則不處理
        //     IF      WK-FLG   =  1
        //       MOVE  1                   TO     WK-RTN
        //       GO TO CHKRDAY-EXIT.
        if (weekFlag == 1) {
            pickType = 1;
            return;
        }
        // 每周的非正常挑檔
        // 把日期指到下營業日，如果日期檔的本營業日為挑檔週期則變更上次挑檔日為日期檔的營業日，否則為日期檔的上上營業日
        //       MOVE   WK-YYMMDD          TO     WK-DATE.
        putDate = tbsdy;

        // CHKRDAY-LOOP1.
        // 指到日期檔的上營業日為本日(下營業日)
        // 如果下營業日為挑檔週期
        //     PERFORM 050-POINTTBSDY-RTN  THRU   050-POINTTBSDY-EXIT.
        fdClndr = pointnbsdy();
        //     IF     DB-CLMR-CYCNO1      =      FD-CLNDR-WEEKDY
        if (clmc.getCycno1() == fdClndr.getWeekdy()) {
            //       MOVE  FD-CLNDR-TBSDY      TO     WK-DATE
            //       MOVE  1                   TO     WK-RTN
            //       GO TO CHKRDAY-EXIT
            putDate = fdClndr.getTbsdy();
            pickType = 1;
        } else {
            //     ELSE
            //       MOVE  FD-CLNDR-LLBSDY     TO     WK-DATE
            //       MOVE  1                   TO     WK-RTN.
            putDate = fdClndr.getLlbsdy();
            pickType = 1;
        }
        // CHKRDAY-EXIT.
        // EXIT.
    }

    private TxBizDate pointnbsdy() {
        // 把日期指到下營業日
        // 050-POINTTBSDY-RTN.
        //     MOVE    1                   TO     WK-CLNDR-KEY.
        // 050-LOOP.
        //     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //          GO TO 0000-END-RTN.
        //     IF      FD-CLNDR-LBSDY      NOT =  WK-DATE
        //      ADD    1                   TO     WK-CLNDR-KEY
        //      GO TO  050-LOOP.
        // 050-POINTTBSDY-EXIT.
        //     EXIT.

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), "" + nbsdy, "" + nbsdy);
        if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
            fdClndr = txBizDates.get(0);
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "TxBizDate notfound");
        }
        return fdClndr;
    }

    private void sortOut() {
        if (!textFile.exists(filePath)) {
            return;
        }
        // DESCENDING KEY  S-PUTTYPE S-PUTNAME S-RCPTID         02/05/17
        File tmpFile = new File(filePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.DESC));
        keyRanges.add(new KeyRange(3, 8, SortBy.DESC));
        keyRanges.add(new KeyRange(17, 26, SortBy.DESC));
        keyRanges.add(new KeyRange(43, 1, SortBy.DESC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);

        List<String> line = textFile.readFileContent(filePath, CHARSET);
        String wkPuttye = "";
        String wkPutName = "";
        fileContents = new ArrayList<>();
        for (String detail : line) {
            text2VoFormatter.formatCC(detail, filePuth);

            if (wkPuttye.equals(filePuth.getPuttype()) && wkPutName.equals(filePuth.getPutname())) {
                //無用資料
                if(filePuth.getRcptid()==null||filePuth.getRcptid().isEmpty()){
                    continue;
                }
                if (" NO DATA".equals(filePuth.getRcptid().substring(0, 8))) {
                    wkPutName = filePuth.getPutname();
                    wkPuttye = filePuth.getPuttype();
                    continue;
                }
            }
            if (" NO DATA".equals(filePuth.getRcptid().substring(0, 8))) {
                filePuth.setRcptid(" NO DATA");
            }
            fileContents.add(vo2TextFormatter.formatRS(filePuth, false));
            wkPutName = filePuth.getPutname();
            wkPuttye = filePuth.getPuttype();
        }
        if (!fileContents.isEmpty()) {
            textFile.deleteFile(filePath);
            writeFile();
            fileContents = new ArrayList<>();
        }
    }

    public String processUserData(String userdata) {
        if (userdata == null || userdata.isEmpty()) {
            return padToBig5Length("", 40); // 防止 null 或空字串
        }

        Charset big5 = Charset.forName("Big5");
        StringBuilder newUserData = new StringBuilder();
        int currentByteLength = 0;
        boolean lastCharWasChinese = false; // 標記上一個字是否為中文

        for (char ch : userdata.toCharArray()) {
            byte[] charBytes = String.valueOf(ch).getBytes(big5);
            int charLength = charBytes.length; // 取得該字元的 Big5 長度

            if (isBig5Chinese(charBytes)) {
                // 如果前一個字不是中文，則在前面加空白
                if (!lastCharWasChinese) {
                    newUserData.append(' ');
                    currentByteLength += 1;
                }

                newUserData.append(ch);
                currentByteLength += charLength;
                lastCharWasChinese = true; // 更新標記
            } else {
                // 如果前一個字是中文，則在後面加空白
                if (lastCharWasChinese) {
                    newUserData.append(' ');
                    currentByteLength += 1;
                }

                newUserData.append(ch);
                currentByteLength += charLength;
                lastCharWasChinese = false; // 更新標記
            }

            // 如果加上這個字元後超過 40 bytes，就停止
            if (currentByteLength >= 40) {
                break;
            }
        }

        // 如果最後一個字是中文，補上空白
        if (lastCharWasChinese) {
            newUserData.append(' ');
        }

        // 確保最終長度為 40 bytes
        return formatUtil.padX(newUserData.toString(), 40);
    }

    // 判斷是否為 Big5 中文字
    private static boolean isBig5Chinese(byte[] charBytes) {
        if (charBytes.length == 2) {
            int lead = charBytes[0] & 0xFF;
            int trail = charBytes[1] & 0xFF;
            return (lead >= 0xA1 && lead <= 0xF9)
                    && ((trail >= 0x40 && trail <= 0x7E) || (trail >= 0xA1 && trail <= 0xFE));
        }
        return false;
    }

    // 調整字串長度為指定的 Big5 byte 數
    private static String padToBig5Length(String str, int targetLength) {
        Charset big5 = Charset.forName("Big5");
        byte[] bytes = str.getBytes(big5);
        if (bytes.length == targetLength) {
            return str; // 剛好 40 bytes
        } else if (bytes.length > targetLength) {
            // 超過 40 bytes，需裁切
            return truncateToBig5Length(str, targetLength);
        } else {
            // 不足 40 bytes，補空白
            StringBuilder sb = new StringBuilder(str);
            while (sb.toString().getBytes(big5).length < targetLength) {
                sb.append(' ');
            }
            return sb.toString();
        }
    }

    // 截斷字串，確保 Big5 長度不超過指定 byte 數
    private static String truncateToBig5Length(String str, int maxLength) {
        Charset big5 = Charset.forName("Big5");
        byte[] bytes = str.getBytes(big5);
        StringBuilder sb = new StringBuilder();
        int byteCount = 0;

        for (char ch : str.toCharArray()) {
            byte[] chBytes = String.valueOf(ch).getBytes(big5);
            if (byteCount + chBytes.length > maxLength) {
                break; // 超過目標長度，停止加入
            }
            sb.append(ch);
            byteCount += chBytes.length;
        }

        return sb.toString();
    }
}
