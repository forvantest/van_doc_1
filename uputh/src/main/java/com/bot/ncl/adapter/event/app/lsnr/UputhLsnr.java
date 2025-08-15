/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Uputh;
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
import com.bot.ncl.util.fileVo.FileSortUPUTH;
import com.bot.ncl.util.fileVo.FileUPUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.exception.dbs.UpDateNAException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
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
@Component("UputhLsnr")
@Scope("prototype")
public class UputhLsnr extends BatchListenerCase<Uputh> {
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private CldtlService cldtlService;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private TextFileUtil textFile;

    @Autowired private ExternalSortUtil externalSortUtil;

    @Autowired private DateUtil dateUtil;

    @Autowired private FileSortUPUTH fileSortUputh;

    @Autowired private FileUPUTH fileUputh;

    @Autowired private BatchUtil batchUtil;
    @Autowired private FsapSync fsapSync;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Uputh event;

    private int clmrPageIndex;

    private int cldtlPageIndex;

    private int pageLimit;

    private TxBizDate fdClndr;
    private String code;

    private static final String CHARSET = "UTF-8";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String fileSortUputhPath;

    private String fileUputhPath;

    private List<String> fileSortUputhContents;
    private Map<String, String> labelMap;
    private CltmrBus cltmr;
    private ClmcBus clmc;

    // WORKING-STORAGE  SECTION.
    // 01 WK-YYMMDD                          PIC 9(07).
    // 01 WK-ULPUTDT                         PIC 9(07).
    // 01 WK-ULLPUTDT                        PIC 9(07).
    // 01 WK-RTN                             PIC 9(01).
    // 01 WK-CLNDR-KEY                       PIC 9(03).
    // 01 WK-CLNDR-STUS                      PIC X(02).
    // 01 WK-CODE                            PIC X(06).
    private String processDate;
    private String tbsdyX;
    private int processDateInt = 0;
    private int wkUlputdt;
    private int wkUllputdt;
    private int pickType; // WK-RTN
    private int wkCode;

    private int tbsdy;

    private int nbsdy;

    private int fnbsdy;

    @Override
    public void onApplicationEvent(Uputh event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "onApplicationEvent()");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Uputh event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "run()");

        init(event);

        // 0000-MAIN-RTN
        queryAndSortAndWriteFile();

        batchResponse();
    }

    private void init(Uputh event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        //     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        processDateInt = parse.string2Integer(processDate);
        tbsdyX = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8);

        clmrPageIndex = 0;
        cldtlPageIndex = 0;
        pageLimit = 100000;

        fileSortUputhPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "SORT_UPUTH";
        fileUputhPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "UPUTH";

        textFile.deleteFile(fileSortUputhPath);
        textFile.deleteFile(fileUputhPath);

        tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        nbsdy = event.getAggregateBuffer().getTxCom().getNbsdy();
        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), processDate, processDate);
        if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
            fdClndr = txBizDates.get(0);
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "fdClndr = [{}]", fdClndr.toString());
            tbsdy = fdClndr.getTbsdy();
            nbsdy = fdClndr.getNbsdy();
            fnbsdy = fdClndr.getFnbsdy();
        }
        fileSortUputhContents = new ArrayList<>();
    }

    // 0000-MAIN-RTN
    private void queryAndSortAndWriteFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "queryAndSortAndWriteFile()");

        // SORT INPUT 段落：CS-SORTIN(針對需產生對帳媒體之代收類別，讀收付明細檔，並寫至SORTFL)
        // 資料照 S-PUTTYPE S-PUTNAME S-CODE S-DATE S-RCPTID 由小到大排序
        // SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，並寫至FD-UPUTH)
        //     SORT    SORTFL
        //             ASCENDING KEY  S-PUTTYPE S-PUTNAME S-CODE
        //                            S-DATE    S-RCPTID
        //     MEMORY SIZE 6 MODULES
        //     DISK SIZE 50 MODULES
        //             INPUT  PROCEDURE     CS-SORTIN
        csSortin();
        //             OUTPUT PROCEDURE     CS-SORTOUT
        csSortout();
        //     .
        // 0000-MAIN-EXIT.
    }

    // CS-SORTIN-RTN
    private void csSortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "csSortin()");

        // FIND NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，結束本節
        //     FIND NEXT DB-CLMR-DDS   ON EXCEPTION GO TO CS-SORTIN-EXIT.
        List<ClmrBus> clmrList = clmrService.findAll(clmrPageIndex, pageLimit);
        if (!Objects.isNull(clmrList) && !clmrList.isEmpty()) {
            Date startTime = new Date();
            for (ClmrBus clmr : clmrList) {
                code = clmr.getCode();
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "code = {}", code);
                cltmr = cltmrService.findById(code);
                if (Objects.isNull(cltmr)) {
                    ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "cltmr is null");
                    continue;
                }
                String putname = cltmr.getPutname();
                if (Objects.isNull(putname)) {
                    ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putname is null");
                    continue;
                }
                clmc = clmcService.findById(putname);
                if (Objects.isNull(clmc)) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "clmc is null, cltmr.getPutname() = {}",
                            cltmr.getPutname());
                    continue;
                }

                // 若DB-CLMR-CYCK2對帳媒體產生週期=0(無此需求)，跳掉這筆資料
                //     IF        DB-CLMR-CYCK2      =       0
                //       GO TO   CS-SORTIN-RTN.
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "clmc.getCyck2() = {}",
                        clmc.getCyck2());
                if (clmc.getCyck2() == 0) {
                    continue;
                }

                // 保留 代收類別 到變數WK-CODE
                //     MOVE      DB-CLMR-CODE       TO      WK-CODE.
                if (parse.isNumeric(code)) {
                    wkCode = parse.string2Integer(code);
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode = {}", wkCode);
                } else {
                    // 測試套有非數值型收付類別
                    ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "code is not numeric");
                    wkCode = 0;
                }
                // 保留 上次CYC2挑檔日 到變數WK-ULPUTDT
                // 若上次CYC2挑檔日=本營業日(重跑，不用再判斷???)
                //   A.保留 上上次CYC2挑檔日 到變數WK-ULPUTDT
                //   B.GO TO CS-INLOOP-BEGIN 讀DB-CLDTL-DDS收付明細檔
                // 否則，執行020-CHK-RTN，以利判斷此代收類別需挑檔否
                //   若WK-RTN=1，GO TO CS-INLOOP-BEGIN 讀DB-CLDTL-DDS收付明細檔
                //   否則，跳掉這筆資料
                //     MOVE      DB-CLMR-ULPUTDT    TO      WK-ULPUTDT.
                //     IF        DB-CLMR-ULPUTDT    =       WK-YYMMDD
                //       MOVE    DB-CLMR-ULLPUTDT   TO      WK-ULPUTDT
                //       GO TO   CS-INLOOP-BEGIN
                //     ELSE
                //       PERFORM 020-CHK-RTN       THRU    020-CHK-EXIT
                //       IF      WK-RTN            =       1
                //        GO TO  CS-INLOOP-BEGIN
                //       ELSE
                //        GO TO  CS-SORTIN-RTN.
                wkUlputdt = cltmr.getUlputdt();
                if (cltmr.getUlputdt() == processDateInt) {
                    wkUlputdt = cltmr.getUllputdt();
                    queryCldtl();
                } else {
                    chk();
                    if (pickType == 1) {
                        queryCldtl();
                    } else {
                        continue;
                    }
                }

                // Wei: 原本這段在CS-INLOOP-BEGIN內,逐筆查CLDTL無資料的時候,會更新主檔的上次挑檔日及上上次挑檔日
                // 新系統不是用GOTO,而是用了continue去做迴圈控制,當有挑檔且挑檔結束後都會走到這裡,所以移來這裡執行
                // 030-STRCLMR-RTN.
                strCltmr();

                // refreshTransaction
                startTime = batchUtil.refreshBatchTransaction(this.batchTransaction, startTime);
            }
            if (clmrList.size() == pageLimit) {
                // 若本頁筆數與每頁筆數上限相同,視為還有下一頁,再查一次
                clmrPageIndex++;
                csSortin();
            }
        } else {
            // 查無資料
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLMR is null");
        }

        // 若有資料,寫到排序檔
        if (!fileSortUputhContents.isEmpty()) {
            try {
                textFile.writeFileContent(fileSortUputhPath, fileSortUputhContents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        // CS-SORTIN-EXIT.
        //     EXIT.
    }

    // 020-CHK-RTN
    private void chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk()");

        // 此段落為判斷挑檔與否
        // 清挑檔記號為0(0-不挑檔、1-挑檔)
        //     MOVE      0                   TO     WK-RTN.
        pickType = 0;

        // 對帳媒體產生週期=0 & 對帳媒體產生週期日=00，無此需求，結束挑檔判斷
        //     IF        DB-CLMR-CYCK2       =      0
        //       AND     DB-CLMR-CYCNO2      =      0
        //       GO TO   020-CHK-EXIT.
        if (clmc.getCyck2() == 0 && clmc.getCycno2() == 0) {
            return;
        }

        // 對帳媒體產生週期=2(星期幾)，A.執行022-WEEK-RTN挑檔判斷，B.結束挑檔判斷
        // IF DB -CLMR-CYCK2 = 2
        // PERFORM 022-WEEK-RTN THRU 022-WEEK-EXIT
        // GO TO 020-CHK-EXIT.
        if (clmc.getCyck2() == 2) {
            week();
            return;
        }

        // 對帳媒體產生週期=3(每月幾日)，A.執行023-MON-RTN挑檔判斷，B.結束挑檔判斷

        // IF DB -CLMR-CYCK2 = 3
        // PERFORM 023-MON-RTN THRU 023-MON-EXIT
        // GO TO 020-CHK-EXIT.
        if (clmc.getCyck2() == 3) {
            mon();
            return;
        }

        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=10，A.執行024-CHK10-RTN挑檔判斷，B.結束挑檔判斷
        // IF DB-CLMR-CYCK2 = 4
        // AND DB-CLMR-CYCNO2 = 10
        // AND DB-CLMR-CYCNO2 = 10 < -多餘
        // PERFORM 024-CHK10-RTN THRU 024-CHK10-EXIT
        // GO TO 020-CHK-EXIT.
        if (clmc.getCyck2() == 4 && clmc.getCycno2() == 10) {
            chk10();
            return;
        }

        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=15，A.執行025-CHK15-RTN挑檔判斷，B.結束挑檔判斷
        // IF DB-CLMR-CYCK2 = 4
        // AND DB-CLMR-CYCNO2 = 15
        // PERFORM 025-CHK15-RTN THRU 025-CHK15-EXIT
        // GO TO 020-CHK-EXIT.
        if (clmc.getCyck2() == 4 && clmc.getCycno2() == 15) {
            chk15();
            return;
        }

        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=30，A.執行026-CHK30-RTN挑檔判斷，B.結束挑檔判斷
        // IF DB-CLMR-CYCK2 = 4
        // AND DB-CLMR-CYCNO2 = 30
        // PERFORM 026-CHK30-RTN THRU 026-CHK30-EXIT
        // GO TO 020-CHK-EXIT.
        if (clmc.getCyck2() == 4 && clmc.getCycno2() == 30) {
            chk30();
            return;
        }

        // 對帳媒體產生週期 & 對帳媒體產生週期日 非以上條件時，顯示錯誤訊息
        // DISPLAY "020-CHK-RTN ERROR(U)" DB-CLMR-CYCK2, DB-CLMR-CYCNO2.
        // MOVE 0 TO WK-RTN.
        // 020-CHK-EXIT.
        // EXIT.
        ApLogHelper.error(
                log,
                false,
                LogType.NORMAL.getCode(),
                "chk ERROR clmc.getCyck2() = {} , clmc.getCycno2() = {}",
                clmc.getCyck2(),
                clmc.getCycno2());
        pickType = 0;
    }

    // 022-WEEK-RTN
    private void week() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "week()");
        // 對帳媒體產生週期=2(星期幾)之判斷
        // 對帳媒體產生週期日=星期幾FD-BHDATE-WEEKDY，挑檔記號=1，結束判斷
        //     IF      DB-CLMR-CYCNO2      =      FD-BHDATE-WEEKDY
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT.
        int weekdy = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "weekdy = {}", weekdy);
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "clmc.getCycno2() = {}", clmc.getCycno2());
        if (clmc.getCycno2() == weekdy) {
            pickType = 1;
            return;
        }

        // 對帳媒體產生週期日<>星期幾FD-BHDATE-WEEKDY，
        //  符合以下條件(P025422-025439)，挑檔記號=1，結束判斷
        //  否則，往下一步驟
        // * 台電 203 206
        //     IF  ( DB-CLMR-CODE= "350003" AND (FD-BHDATE-WEEKDY=3 OR = 6))
        //      OR ( DB-CLMR-CODE= "510071" AND (FD-BHDATE-WEEKDY=3 OR = 6))
        //      OR ( DB-CLMR-CODE= "510011" AND (FD-BHDATE-WEEKDY=3 OR = 5))
        //      OR ((DB-CLMR-CODE="530021") AND (FD-BHDATE-WEEKDY=3 OR = 6))
        //      OR ( (DB-CLMR-CODE> "302006" AND DB-CLMR-CODE< "302238"   )
        //         AND (FD-BHDATE-WEEKDY= 3 OR = 5                        ))
        //      OR ( (DB-CLMR-CODE> "510999" AND DB-CLMR-CODE< "511023"   )
        //         AND (FD-BHDATE-WEEKDY= 3 OR = 5                        ))
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT.
        if ((code.equals("350003") && (weekdy == 3 || weekdy == 6))
                || (code.equals("510071") && (weekdy == 3 || weekdy == 6))
                || (code.equals("510011") && (weekdy == 3 || weekdy == 5))
                || (code.equals("530021") && (weekdy == 3 || weekdy == 6))
                || (wkCode > 302006 && wkCode < 302238 && (weekdy == 3 || weekdy == 5))
                || (wkCode > 510999 && wkCode < 511023 && (weekdy == 3 || weekdy == 5))) {
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
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nbsdy = {}", nbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "days = {}", days);
        if (days == 1) {
            pickType = 0;
            return;
        }

        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF  ( DB-CLMR-CYCNO2       =      FD-CLNDR-WEEKDY  )
        //       OR ((DB-CLMR-CODE="350003") AND (FD-CLNDR-WEEKDY=3 OR =6))
        //       OR ((DB-CLMR-CODE="510071") AND (FD-CLNDR-WEEKDY=3 OR =6))
        //       OR ((DB-CLMR-CODE="530021") AND (FD-CLNDR-WEEKDY=3 OR =6))
        //       OR ((DB-CLMR-CODE> "302006" AND  DB-CLMR-CODE < "302238")
        //           AND (FD-CLNDR-WEEKDY  =3 OR =5                      ))
        //       OR ((DB-CLMR-CODE> "510999" AND  DB-CLMR-CODE < "511023")
        //           AND (FD-CLNDR-WEEKDY  =3 OR =5                      ))
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 022-WEEK-EXIT
        //      ELSE
        //       GO TO 022-LOOP1.

        List<TxBizDate> nTxBizDate = getfdClndrRange("" + tbsdy, "" + nbsdy);
        for (int i = 0; i < nTxBizDate.size(); i++) {
            if (nTxBizDate.get(i).getHoliday() == 0) {
                pickType = 0;
                return;
            } else {
                int tempWeekdy = nTxBizDate.get(i).getWeekdy();
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "tempWeekdy = {}", tempWeekdy);
                if (clmc.getCycno2() == weekdy
                        || (code.equals("350003") && (tempWeekdy == 3 || tempWeekdy == 6))
                        || (code.equals("510071") && (tempWeekdy == 3 || tempWeekdy == 6))
                        || (code.equals("530021") && (tempWeekdy == 3 || tempWeekdy == 6))
                        || (wkCode > 302006
                                && wkCode < 302238
                                && (tempWeekdy == 3 || tempWeekdy == 5))
                        || (wkCode > 510999
                                && wkCode < 511023
                                && (tempWeekdy == 3 || tempWeekdy == 5))) {
                    pickType = 1;
                    return;
                }
            }
        }
        // 022-WEEK-EXIT.
        //     EXIT.
    }

    // 023-MON-RTN
    private void mon() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mon()");

        // 對帳媒體產生週期=3(每月幾日)之判斷
        // 對帳媒體產生週期日=FD-BHDATE-TBSDD，挑檔記號=1，結束判斷
        //     IF      DB-CLMR-CYCNO2      =      FD-BHDATE-TBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 023-MON-EXIT.
        int tbsdd = tbsdy % 100;
        if (clmc.getCycno2() == tbsdd) {
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
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nbsdy = {}", nbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "days = {}", days);
        if (days == 1) {
            pickType = 0;
            return;
        }

        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     DB-CLMR-CYCNO2      =      FD-CLNDR-TBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 023-MON-EXIT
        //      ELSE
        //       GO TO 023-LOOP1.

        List<TxBizDate> nTxBizDate = getfdClndrRange("" + tbsdy, "" + nbsdy);
        for (int i = 0; i < nTxBizDate.size(); i++) {
            if (nTxBizDate.get(i).getHoliday() == 0) {
                pickType = 0;
                return;
            } else {
                int tempDd = nTxBizDate.get(i).getTbsdy() % 100;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tempDd = {}", tempDd);
                if (clmc.getCycno2() == tempDd) {
                    pickType = 1;
                    return;
                }
            }
        }
        // 023-MON-EXIT.
        //     EXIT.
    }

    // 024-CHK10-RTN
    private void chk10() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk10");
        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=10 之判斷
        // 對帳媒體產生週期日=10 OR 20 OR 本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =  10 OR 20 OR FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 024-CHK10-EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdd = {}", tbsdd);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fnbsdd = {}", fnbsdd);
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
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nbsdy = {}", nbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "days = {}", days);
        if (days == 1) {
            pickType = 0;
            return;
        }

        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     FD-CLNDR-TBSDD      =      10  OR  20
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 024-CHK10-EXIT
        //      ELSE
        //       GO TO 024-LOOP1.
        // 024-CHK10-EXIT.
        //     EXIT.

        List<TxBizDate> nTxBizDate = getfdClndrRange("" + tbsdy, "" + nbsdy);
        for (int i = 0; i < nTxBizDate.size(); i++) {
            if (nTxBizDate.get(i).getHoliday() == 0) {
                pickType = 0;
                return;
            } else {
                int tempDd = nTxBizDate.get(i).getTbsdy() % 100;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tempDd = {}", tempDd);
                if (tempDd == 10 || tempDd == 20) {
                    pickType = 1;
                    return;
                }
            }
        }
    }

    // 025-CHK15-RTN
    private void chk15() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk15");
        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=15 之判斷
        // 對帳媒體產生週期日=15 OR 本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =  15 OR FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 025-CHK15-EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdd = {}", tbsdd);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fnbsdd = {}", fnbsdd);
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
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nbsdy = {}", nbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "days = {}", days);
        if (days == 1) {
            pickType = 0;
            return;
        }

        // 下一日期是非營業日，
        //  若符合條件，挑檔記號=1，結束判斷；否則再往下一日期找
        //      IF     FD-CLNDR-TBSDD      =      15
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 025-CHK15-EXIT
        //      ELSE
        //       GO TO 025-LOOP1.

        List<TxBizDate> nTxBizDate = getfdClndrRange("" + tbsdy, "" + nbsdy);
        for (int i = 0; i < nTxBizDate.size(); i++) {
            if (nTxBizDate.get(i).getHoliday() == 0) {
                pickType = 0;
                return;
            } else {
                int tempDd = nTxBizDate.get(i).getTbsdy() % 100;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tempDd = {}", tempDd);
                if (tempDd == 15) {
                    pickType = 1;
                    return;
                }
            }
        }
        // 025-CHK15-EXIT.
        //     EXIT.
    }

    // 026-CHK30-RTN
    private void chk30() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk30");
        // 對帳媒體產生週期=4(每旬) & 對帳媒體產生週期日=30 之判斷
        // 對帳媒體產生週期日=本月最終營業日，挑檔記號=1，結束判斷
        //     IF      FD-BHDATE-TBSDD     =      FD-BHDATE-FNBSDD
        //       MOVE  1                   TO     WK-RTN
        //       GO TO 026-CHK30-EXIT.
        // 026-CHK30-EXIT.
        //     EXIT.
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdd = {}", tbsdd);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fnbsdd = {}", fnbsdd);
        if (tbsdd == fnbsdd) {
            pickType = 1;
        }
    }

    // 030-STRCLMR-RTN
    private void strCltmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "strCltmr()");
        // 更新 事業單位基本資料檔
        // **   DISPLAY "LOCK=" WK-CODE.
        //     PERFORM 040-LOCKCLMR-RTN THRU 040-LOCKCLMR-EXIT.
        //     MOVE  WK-ULPUTDT   TO   DB-CLMR-ULLPUTDT.
        //     MOVE  WK-YYMMDD    TO   DB-CLMR-ULPUTDT.
        //     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
        //     STORE  DB-CLMR-DDS.
        //     END-TRANSACTION NO-AUDIT RESTART-DST.
        // 030-STRCLMR-EXIT.
        //     EXIT.
        CltmrBus holdCltmr = cltmrService.holdById(code);
        if (!Objects.isNull(holdCltmr)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkUlputdt = {}", wkUlputdt);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkYymmdd = {}", processDate);
            holdCltmr.setUllputdt(wkUlputdt);
            holdCltmr.setUlputdt(processDateInt);
            try {
                cltmrService.update(holdCltmr);
            } catch (UpDateNAException e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "update cltmr fail , error message = {}",
                        e.getMessage());
            }
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "holdCltmr is null");
        }
    }

    // CS-INLOOP-BEGIN
    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "queryCldtl()");
        //  CS-INLOOP-BEGIN.
        // 將DB-CLDTL-IDX3指標移至開始
        //     SET  DB-CLDTL-IDX3   TO   BEGINNING.
        //  CS-INLOOP.
        // 依 代收類別+代收日(大於上次CYC2挑檔日&小於等於本營業日) FIND NEXT 收付明細檔
        // 正常，則執行下一步驟，否則
        //  A.若不存在，表收付明細檔已無資料，則執行030-STRCLMR-RTN更新事業單位基本資料檔，GO TO CS-SORTIN-RTN找下一筆事業單位基本資料檔
        //  B.其餘，DISPLAY錯誤訊息，異常結束程式
        //     FIND NEXT   DB-CLDTL-IDX3  AT DB-CLDTL-CODE = DB-CLMR-CODE
        //                               AND DB-CLDTL-DATE > WK-ULPUTDT
        //                               AND DB-CLDTL-DATE NOT > WK-YYMMDD
        //     ON EXCEPTION  IF     DMSTATUS(NOTFOUND)
        // *                   MOVE  WK-ULPUTDT   TO   DB-CLMR-ULLPUTDT
        // *                   MOVE  WK-YYMMDD    TO   DB-CLMR-ULPUTDT
        //                    PERFORM  030-STRCLMR-RTN THRU 030-STRCLMR-EXIT
        //                    GO TO   CS-SORTIN-RTN
        //                   ELSE   DISPLAY "CS-INLOOP FAIL" DB-CLMR-CODE
        //                          CALL SYSTEM DMTERMINATE.
        List<CldtlByCodeEntdyRangeBus> cldtlList =
                cldtlService.findByCodeEntdyRange(
                        code, wkUlputdt, processDateInt, 0, cldtlPageIndex, pageLimit);
        if (!Objects.isNull(cldtlList) && !cldtlList.isEmpty()) {
            for (CldtlByCodeEntdyRangeBus cldtl : cldtlList) {
                // 搬相關欄位至SORT-REC，並寫至SORTFL
                //     MOVE       DB-CLMR-PUTTYPE   TO      S-PUTTYPE.	<-媒體種類
                //     MOVE       DB-CLMR-PUTNAME   TO      S-PUTNAME.	<-媒體檔名
                //     MOVE       DB-CLDTL-CODE     TO      S-CODE.			<-代收類別
                //     MOVE       DB-CLDTL-DATE     TO      S-DATE.			<-代收日
                //     MOVE       DB-CLDTL-TIME     TO      S-TIME.			<-代收時間
                //     MOVE       DB-CLDTL-CLLBR    TO      S-CLLBR.		<-代收行
                //     MOVE       DB-CLDTL-RCPTID   TO      S-RCPTID.		<-銷帳號碼
                //     MOVE       DB-CLDTL-AMT      TO      S-AMT.			<-繳費金額
                //     MOVE       DB-CLDTL-LMTDATE  TO      S-LMTDATE.	<-繳費期限
                //     MOVE       DB-CLDTL-USERDATA TO      S-USERDATA.	<-備註資料
                //     MOVE       DB-CLDTL-SITDATE  TO      S-SITDATE.	<-原代收日
                //     MOVE       DB-CLDTL-TXTYPE   TO      S-TXTYPE.		<-帳務別
                //     RELEASE    SORT-REC.
                fileSortUputh.setPuttype("" + clmc.getPuttype());
                fileSortUputh.setPutname(clmc.getPutsend() + clmc.getPutform() + clmc.getPutname());
                fileSortUputh.setCode(cldtl.getCode());
                fileSortUputh.setEntdy("" + cldtl.getEntdy());
                fileSortUputh.setTime("" + cldtl.getTime());
                fileSortUputh.setCllbr("" + cldtl.getCllbr());
                fileSortUputh.setRcptid(cldtl.getRcptid());
                fileSortUputh.setAmt("" + cldtl.getAmt());
                fileSortUputh.setLmtdate("" + cldtl.getLmtdate());
                fileSortUputh.setUserdata(cldtl.getUserdata());
                fileSortUputh.setSitdate("" + cldtl.getSitdate());
                fileSortUputh.setTxtype(cldtl.getTxtype());
                fileSortUputhContents.add(vo2TextFormatter.formatRS(fileSortUputh, false));
                // 若筆數已達10000筆,先寫入檔案再繼續查
                if (fileSortUputhContents.size() >= 10000) {
                    try {
                        textFile.writeFileContent(
                                fileSortUputhPath, fileSortUputhContents, CHARSET);
                    } catch (LogicException e) {
                        moveErrorResponse(e);
                    }
                    fileSortUputhContents = new ArrayList<>();
                }

                // GO TO CS-INLOOP讀下一筆收付明細檔
                //     GO TO      CS-INLOOP.
            }

            if (cldtlList.size() == pageLimit) {
                // 若本頁筆數與每頁筆數上限相同,視為還有下一頁,再查一次
                cldtlPageIndex++;
                queryCldtl();
            }
        } else {
            // 查無資料
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLDTL is null");
        }
    }

    private void csSortout() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "csSortout()");
        if (textFile.exists(fileSortUputhPath)) {
            List<KeyRange> keyRanges = new ArrayList<>();
            keyRanges.add(new KeyRange(1, 2, SortBy.ASC)); // S-PUTTYPE
            keyRanges.add(new KeyRange(3, 8, SortBy.ASC)); // S-PUTNAME
            keyRanges.add(new KeyRange(11, 6, SortBy.ASC)); // S-CODE
            keyRanges.add(new KeyRange(43, 8, SortBy.ASC)); // S-DATE
            keyRanges.add(new KeyRange(17, 26, SortBy.ASC)); // S-RCPTID

            File in = Paths.get(fileSortUputhPath).toFile();
            File out = Paths.get(fileUputhPath).toFile();
            externalSortUtil.sortingFile(in, out, keyRanges, CHARSET);
        } else {
            List<String> nodata = new ArrayList<>();
            try {
                textFile.writeFileContent(fileUputhPath, nodata, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        upload(fileUputhPath);
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath =
                File.separator + tbsdyX + File.separator + "2FSAP" + File.separator + "DATA";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
        ApLogHelper.error(
                log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }

    private List<TxBizDate> getfdClndrRange(String dateS, String dateE) {
        List<TxBizDate> txBizDates = fsapSync.sy202ForAp(event.getPeripheryRequest(), dateS, dateE);
        if (Objects.isNull(txBizDates) || txBizDates.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "TxBizDate notfound");
            throw new LogicException("GE999", "無日期檔 [" + dateS + "],[" + dateE + "]");
        }
        return txBizDates;
    }
}
