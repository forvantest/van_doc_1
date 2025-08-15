/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Deldtl;
import com.bot.ncl.dto.entities.CldtlBus;
import com.bot.ncl.dto.entities.CldtlEntdyBeforeBus;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.entities.impl.CldtlId;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.exception.dbs.DelNAException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("DeldtlLsnr")
@Scope("prototype")
public class DeldtlLsnr extends BatchListenerCase<Deldtl> {

    @Autowired private DateUtil dateUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;

    @Autowired private TextFileUtil textFileUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private CldtlService cldtlService;

    @Autowired private ClmcService clmcService;

    @Autowired private CltmrService cltmrService;

    // 004600    02 WK-YYMMDD                       PIC 9(07).
    private Deldtl event;
    private String processDate;
    private String tbsdy;
    private int processDateInt = 0;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    // 003000 01 WK-YYMMDD2                         PIC 9(08).                 99/12/29
    // 003100 01 WK-YYMMDD2-R      REDEFINES WK-YYMMDD2.
    // 003200    02 WK-YY2                          PIC 9(04).                 99/12/29
    // 003300    02 WK-MM2                          PIC 9(02).
    // 003400    02 WK-DD2                          PIC 9(02).
    // 003410 01 WK-YYMMDD3                         PIC 9(08).                 99/12/29
    // 003420 01 WK-YYMMDD3-R      REDEFINES WK-YYMMDD3.                       95/11/27
    // 003430    02 WK-YY3                          PIC 9(04).                 99/12/29
    // 003440    02 WK-MM3                          PIC 9(02).                 95/11/27
    // 003450    02 WK-DD3                          PIC 9(02).                 95/11/27
    // 003455 01 WK-YYMMDD6                         PIC 9(08).                 07/08/10
    // 003460 01 WK-YYMMDD6-R      REDEFINES WK-YYMMDD6.                       07/08/10
    // 003465    02 WK-YY6                          PIC 9(04).                 07/08/10
    // 003470    02 WK-MM6                          PIC 9(02).                 07/08/10
    // 003475    02 WK-DD6                          PIC 9(02).                 07/08/10
    private int wkYymmdd2;
    private int wkYymmdd3;
    private int wkYymmdd6;

    private int nbsdy;

    private List<String> errorFileContents;

    private String wkText;

    private String errorReportPath;

    private static final String BIG5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;

    @Override
    public void onApplicationEvent(Deldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DeldtlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Deldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DeldtlLsnr run()");
        init(event);

        // 確定今日是否為本週最後營業日(WK-RTN:0.否 1.是)
        // 009900     PERFORM 022-WEEK-RTN    THRU    022-WEEK-EXIT.
        int wkRtn = week022();

        if (wkRtn == 0) {
            // 非本周最後營業日 結束程式
        } else if (wkRtn == 1) {
            // 本周最後營業日,進行CLDTL清檔
            dataClean();
        }

        batchResponse();
    }

    private void init(Deldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 009800     MOVE    FD-BHDATE-TBSDY TO      WK-YYMMDD.
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        processDateInt = parse.string2Integer(processDate);
        tbsdy = labelMap.get("PROCESS_DATE");
        nbsdy = parse.string2Integer(labelMap.get("NBSDY"));

        errorFileContents = new ArrayList<>();

        wkText = "";

        // BD/CL/BH/004
        errorReportPath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-004";
    }

    private int week022() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "week022()");
        // 020000 022-WEEK-RTN.
        // WK-RTN預設為0
        // 022000     MOVE    0                   TO     WK-RTN.
        int wkRtn = 0;

        // 若本日為周六，WK-RTN設為1，結束本段落
        // 024000     IF      FD-BHDATE-WEEKDY    =      6
        // 026000       MOVE  1                   TO     WK-RTN
        // 028000       GO TO 022-WEEK-EXIT.
        int weekdy = getWeekdy(processDateInt);
        if (weekdy == 6) {
            wkRtn = 1;
            return wkRtn;
        }

        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        // 030000     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 032000 022-LOOP.

        // 往下找
        // 034000     ADD     1                   TO     WK-CLNDR-KEY.
        // 036000     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 038000          MOVE  0                TO     WK-RTN
        // 040000          CLOSE FD-CLNDR
        // 042000          GO TO 022-WEEK-EXIT.

        // 非假日(今日非本周最後營業日)，WK-RTN設為0，結束本段落
        // 假日
        //   若周六，WK-RTN設為1，結束本段落
        //   其他，GO TO 022-LOOP
        // 044000     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 046000       MOVE  0                   TO     WK-RTN
        // 048000       GO TO 022-WEEK-EXIT
        // 050000     ELSE
        // 052000      IF     FD-CLNDR-WEEKDY     =      6
        // 054000       MOVE  1                   TO     WK-RTN
        // 056000       GO TO 022-WEEK-EXIT
        // 058000      ELSE
        // 060000       GO TO 022-LOOP.
        DateDto dateDto = new DateDto();
        dateDto.setDateS(processDate);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        if (days >= 2) {
            for (int i = 1; i < days; i++) {
                dateDto.init();
                dateDto.setDateS(processDate);
                dateDto.setDays(i);
                dateUtil.getCalenderDay(dateDto);
                int tempDate = dateDto.getDateE2Integer(false);
                int tempWeekdy = getWeekdy(tempDate);
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tempDate = {}", tempDate);
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "tempWeekdy = {}", tempWeekdy);
                if (tempWeekdy == 6) {
                    wkRtn = 1;
                    return wkRtn;
                }
            }
        }
        // 關FD-CLNDR
        // 062000     CLOSE FD-CLNDR.
        // 064000 022-WEEK-EXIT.
        // 066000     EXIT.
        return wkRtn;
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    private void dataClean() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dataClean()");
        // 010900 0000-MAIN-RTN.
        // 010910*  計算代收資料保存一年之刪檔基準日
        // 010920     COMPUTE   WK-YY3  = FD-BHDATE-TBSYY - 1.
        // 010930     COMPUTE   WK-MM3  = FD-BHDATE-TBSMM.
        // 010940     COMPUTE   WK-DD3  = FD-BHDATE-TBSDD.
        // 010950*  計算代收資料保存兩個月之刪檔基準日
        // 011000     IF        FD-BHDATE-TBSMM  >    2
        // 011100       COMPUTE WK-MM2  = FD-BHDATE-TBSMM - 2
        // 011200       COMPUTE WK-YY2  = FD-BHDATE-TBSYY
        // 011300     ELSE
        // 011400       COMPUTE WK-MM2  = FD-BHDATE-TBSMM + 12 - 2
        // 011500       COMPUTE WK-YY2  = FD-BHDATE-TBSYY - 1.
        // 011510*  計算代收資料保存六個月之刪檔基準日
        // 011520     IF        FD-BHDATE-TBSMM  >    6
        // 011530       COMPUTE WK-MM6  = FD-BHDATE-TBSMM - 6
        // 011540       COMPUTE WK-YY6  = FD-BHDATE-TBSYY
        // 011550     ELSE
        // 011560       COMPUTE WK-MM6  = FD-BHDATE-TBSMM + 12 - 6
        // 011570       COMPUTE WK-YY6  = FD-BHDATE-TBSYY - 1.
        // 011580
        // 011600     MOVE      FD-BHDATE-TBSDD  TO   WK-DD2.
        wkYymmdd3 = processDateInt - 10000;
        wkYymmdd2 =
                (processDateInt % 10000 / 100) > 2
                        ? processDateInt - 200
                        : processDateInt + 1200 - 200 - 10000;
        wkYymmdd6 =
                (processDateInt % 10000 / 100) > 6
                        ? processDateInt - 600
                        : processDateInt + 1200 - 600 - 10000;

        // 將DB-CLDTL-IDX2指標移至開始
        // 011620     SET       DB-CLDTL-IDX2    TO   BEGINNING.
        // 011640     MOVE             0         TO   WK-DELCNT.
        // 011700 0000-DEL-LOOP.

        // DB-CLDTL-IDX2：KEY IS ( DB-CLDTL-DATE,DB-CLDTL-CLLBR,DB-CLDTL-CODE ) DUPLICATES LAST;
        // 011800     LOCK NEXT DB-CLDTL-IDX2  ON EXCEPTION
        // 011810          IF DMSTATUS(NOTFOUND)
        // 011820             IF WK-DELCNT  >  0
        // 011830                GO TO 0000-ETR-RTN
        // 011840             ELSE
        // 011850                GO TO 0000-MAIN-EXIT
        // 011860          ELSE
        // 011870                GO TO 0000-MAIN-EXIT.
        // 011872*  注意 : 因為使用 CLDTL 的 INDEX2  故日期是以升冪的方式排列
        // 011874*         當代收明細的日期已經大於兩個月刪檔基準日時  及表示
        // 011876*         已經沒有符合刪除交易之明細
        // 011878     IF        DB-CLDTL-DATE       >    WK-YYMMDD2
        // 011880       GO TO   0000-MAIN-EXIT.
        List<CldtlEntdyBeforeBus> cldtlList =
                cldtlService.findEntdyBefore(wkYymmdd2, 0, Integer.MAX_VALUE);

        if (Objects.isNull(cldtlList) || cldtlList.isEmpty()) {
            // 無資料
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cldtl is empty");
            return;
        }

        for (CldtlEntdyBeforeBus cldtl : cldtlList) {
            // 確認此筆資料是否超過期限(WK-DEL-FLAG：0.否 1.是)
            //  CODE="145952"...，刪除一年前資料
            //  CODE="115892"...，刪除半年前資料
            //  其他，刪除2個月前資料
            String cldtlCode = cldtl.getCode();
            int wkDelFlag = 0;
            switch (cldtlCode) {
                    // 011882     IF        DB-CLDTL-CODE       =    "145952"  OR = "146002"
                    // 011884                               OR  =    "111801"  OR = "121801"
                    // 011886                               OR  =    "111981"  OR = "121981"
                    // 011888                               OR  =    "366009"  OR = "115265"
                    // 011890       PERFORM CHK-YEAR-RTN        THRU CHK-YEAR-EXIT
                case "145952", "146002", "111801", "121801", "111981", "121981", "366009", "115265":
                    wkDelFlag = chkYear(cldtl);
                    break;
                    // 011892     ELSE IF  DB-CLDTL-CODE       =    "115892"  OR = "115902"
                    // 011893                                    OR "115988"
                    // 011894       PERFORM CHK-HALFYEAR-RTN    THRU CHK-HALFYEAR-EXIT
                case "115892", "115902", "115988":
                    wkDelFlag = chkHalfYear(cldtl);
                    break;
                    // 011895     ELSE
                    // 011897       PERFORM CHK-2MONTH-RTN      THRU CHK-2MONTH-EXIT.
                    // 011899
                default:
                    wkDelFlag = chkTwoMonth(cldtl);
                    break;
            }

            // 期限內不需刪除(WK-DEL-FLAG：0.否 1.是)，GO TO 0000-DEL-LOOP，LOOP讀下一筆CLDTL
            // 011900     IF        WK-DEL-FLAG         =    0
            // 011965        GO TO  0000-DEL-LOOP.
            // 011980*
            if (wkDelFlag == 0) {
                continue;
            }

            // 執行080-CHK-RTN，確認資料是否要刪除
            // 012000     PERFORM   080-CHK-RTN     THRU     080-CHK-EXIT.
            int wkRtn = chk(cldtl);

            // WK-RTN=0，此筆資料不刪除，GO TO 0000-DEL-LOOP，LOOP讀下一筆CLDTL
            // WK-RTN=1，往0000-BTR-RTN執行刪檔
            // 012100     IF        WK-RTN          =        0
            // 012200       GO  TO  0000-DEL-LOOP.
            if (wkRtn == 0) {
                continue;
            }

            // 012300*
            // 012400 0000-BTR-RTN.
            // 逐筆刪除，所以WK-DELCNT都會是0
            // BEGIN-TRANSACTION開始交易，若有誤，結束0000-MAIN-RTN
            // 012450      IF      WK-DELCNT     =     0
            // 012500         BEGIN-TRANSACTION NO-AUDIT RESTART-DST ON EXCEPTION
            // 012550              GO TO 0000-MAIN-EXIT.
            // DELETE DB-CLDTL-DDS收付明細檔，若有誤，結束0000-MAIN-RTN
            // 012555      DELETE  DB-CLDTL-DDS ON EXCEPTION GO TO 0000-MAIN-EXIT.
            CldtlId cldtlId = new CldtlId();
            CldtlBus holdCldtl = cldtlService.holdById(cldtlId);
            if (Objects.isNull(holdCldtl)) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "hold cldtl ({}) is null.",
                        holdCldtl);
                return;
            }
            try {
                cldtlService.delete(holdCldtl);
            } catch (DelNAException e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "delete cldtl ({}) , error = {}",
                        holdCldtl,
                        e.getMessage());
                return;
            }

            // 刪檔筆數累加1
            // 012560      ADD     1         TO     WK-DELCNT.
            // 逐筆刪除後，WK-DELCNT刪檔筆數清為0，所以WK-DELCNT都會是1
            // 012565      IF    WK-DELCNT    <     1      GO  TO  0000-DEL-LOOP.
            // 012570 0000-ETR-RTN.
            // END-TRANSACTION確認交易，若有誤，結束0000-MAIN-RTN
            // WK-DELCNT刪檔筆數清為0
            // 012575      END-TRANSACTION   NO-AUDIT RESTART-DST ON EXCEPTION
            // 012580            GO TO 0000-MAIN-EXIT.
            // 012585      MOVE    0   TO    WK-DELCNT.

            // LOOP讀下一筆CLDTL
            // 012590      GO      TO  0000-DEL-LOOP.
        }
        // 012600 0000-MAIN-EXIT.
        // 012700     EXIT.
    }

    // CHK-YEAR-RTN
    private int chkYear(CldtlEntdyBeforeBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkYear()");
        // 100000 CHK-YEAR-RTN.
        // 代收日期大於WK-YYMMDD3一年之刪檔基準日，不須刪除(WK-DEL-FLAG=0)
        // 代收日期小於等於WK-YYMMDD3一年之刪檔基準日，須刪除(WK-DEL-FLAG=1)
        // 102000     IF      DB-CLDTL-DATE       >    WK-YYMMDD3
        // 104000          MOVE  0                TO   WK-DEL-FLAG
        // 106000     ELSE
        // 108000          MOVE  1                TO   WK-DEL-FLAG.
        // 110000 CHK-YEAR-EXIT.
        // 112000     EXIT.
        if (cldtl.getEntdy() > wkYymmdd3) {
            return 0;
        } else {
            return 1;
        }
    }

    // CHK-HALFYEAR-RTN
    private int chkHalfYear(CldtlEntdyBeforeBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkHalfYear()");
        // 128300 CHK-HALFYEAR-RTN.
        // 代收日期大於WK-YYMMDD6六個月之刪檔基準日，不須刪除(WK-DEL-FLAG=0)
        // 代收日期小於等於WK-YYMMDD6六個月之刪檔基準日，須刪除(WK-DEL-FLAG=1)
        // 128400     IF      DB-CLDTL-DATE       >    WK-YYMMDD6
        // 128500          MOVE  0                TO   WK-DEL-FLAG
        // 128600     ELSE
        // 128700          MOVE  1                TO   WK-DEL-FLAG.
        // 128800 CHK-HALFYEAR-EXIT.
        // 128900     EXIT.
        if (cldtl.getEntdy() > wkYymmdd6) {
            return 0;
        } else {
            return 1;
        }
    }

    // CHK-2MONTH-RTN
    private int chkTwoMonth(CldtlEntdyBeforeBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkTwoMonth()");
        // 116000 CHK-2MONTH-RTN.
        // 代收日期大於WK-YYMMDD2兩個月之刪檔基準日，不須刪除(WK-DEL-FLAG=0)
        // 代收日期小於等於WK-YYMMDD2兩個月之刪檔基準日，須刪除(WK-DEL-FLAG=1)
        // 118000     IF      DB-CLDTL-DATE       >    WK-YYMMDD2
        // 120000          MOVE  0                TO   WK-DEL-FLAG
        // 122000     ELSE
        // 124000          MOVE  1                TO   WK-DEL-FLAG.
        // 126000 CHK-2MONTH-EXIT.
        // 128000     EXIT.
        // 128100
        if (cldtl.getEntdy() > wkYymmdd2) {
            return 0;
        } else {
            return 1;
        }
    }

    // 080-CHK-RTN
    private int chk(CldtlEntdyBeforeBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk()");
        // 012800 080-CHK-RTN.
        // WK-RTN預設為0(0.不刪除 1.刪除)
        // 012900     MOVE      0               TO       WK-RTN.
        int wkRtn = 0;

        // 依代收類別找事業單位基本資料檔，若有誤
        //  A.WK-RTN設為0，不刪除，出表
        //  B.寫REPORTFL報表表頭
        //  C.搬不同的錯誤訊息給WK-TEXT
        //  D.寫REPORTFL報表明細(WK-ERROR-LINE)
        //  E.結束本段

        // 013000     FIND      DB-CLMR-IDX1    AT DB-CLMR-CODE = DB-CLDTL-CODE
        // 013100          ON EXCEPTION
        // 013200             MOVE    0    TO  WK-RTN
        // 013300             PERFORM 098-WRITET-RTN THRU 098-WRITET-EXIT
        // 013400             IF  DMSTATUS(NOTFOUND)
        // 013500               MOVE  "USE CLDTL-CODE CAN NOT FOUND CLMR-DDS"
        // 013600                     TO   WK-TEXT
        // 013700               PERFORM 095-WRITEERROR-RTN THRU 095-WRITEERROR-EXIT
        // 013800               GO TO  080-CHK-EXIT
        // 013900             ELSE
        // 014000               MOVE  "SOME DB PROBLEMS MAY OCCUR"  TO    WK-TEXT
        // 014100               PERFORM 095-WRITEERROR-RTN THRU 095-WRITEERROR-EXIT
        // 014200               GO TO  080-CHK-EXIT.
        CltmrBus cltmr = cltmrService.findById(cldtl.getCode());
        if (Objects.isNull(cltmr)) {
            wkText = "USE CLDTL-CODE CAN NOT FOUND CLTMR";
            writet();
            writeerror(cldtl);
            return wkRtn;
        }
        String putname = cltmr.getPutname();
        ClmcBus clmc = clmcService.findById(putname);
        if (Objects.isNull(clmc)) {
            wkText = "USE PUTNAME(" + putname + ") CAN NOT FOUND CLMC";
            writet();
            writeerror(cldtl);
            return wkRtn;
        }

        // DB-CLMR-CYCK1  銷帳媒體產生週期   9(01)
        // DB-CLMR-CYCNO1 銷帳媒體產生週期日 9(02)
        // 000:以指定日期要媒體;
        // 1xx: xx週期日數、不可大於10個營業日;
        // 20x: x星期幾
        // 3xx: xx每月幾日、但不大於28
        // 410:每旬;
        // 415:每半月;
        // 430:每月底;均指營業日;
        // (若不需要產生媒體檔時設定為501)
        //
        // DB-CLMR-LPUTDT 上次CYC1挑檔日 9(08)
        // DB-CLMR-CYCK1 NOT= 0，WK-RTN設為1 刪除
        // DB-CLMR-CYCK1 = 0，
        //  上次CYC1挑檔日>=WK-YYMMDD2，WK-RTN設為1 刪除
        //  上次CYC1挑檔日< WK-YYMMDD2
        //    上次CYC1挑檔日 <  DB-CLDTL-DATE，WK-RTN設為0 不刪除
        //    上次CYC1挑檔日 >= DB-CLDTL-DATE，WK-RTN設為1 刪除
        // 014300     IF        DB-CLMR-CYCK1  NOT =   0
        // 014400       MOVE    1              TO      WK-RTN
        // 014500     ELSE
        // 014600*-( 上次挑檔日 >= YYMMDD2 )&(CLDTL-DATE <= 上次挑檔日 <YYMMDD2)
        // 014700*-- =======>DELETE
        // 014800*-( 上次挑檔日 < CLDTL-DATE) =========> NOT DELETE
        // 014900       IF      DB-CLMR-LPUTDT NOT <   WK-YYMMDD2
        // 015000        MOVE   1              TO      WK-RTN
        // 015100       ELSE
        // 015200        IF     DB-CLMR-LPUTDT <       DB-CLDTL-DATE
        // 015300         MOVE  0              TO      WK-RTN
        // 015400        ELSE
        // 015500         MOVE  1              TO      WK-RTN.
        if (clmc.getCyck1() != 0) {
            wkRtn = 1;
        } else if (cltmr.getLputdt() >= wkYymmdd2) {
            wkRtn = 1;
        } else if (cltmr.getLputdt() < cldtl.getEntdy()) {
            wkRtn = 0;
        } else {
            wkRtn = 1;
        }
        // 015600 080-CHK-EXIT.
        // 015700     EXIT.
        return wkRtn;
    }

    // 098-WRITET-RTN
    private void writet() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writet()");
        // 018000 098-WRITET-RTN.
        // 寫REPORTFL報表表頭(WK-TITLE-LINE1~WK-TITLE-LINE3)
        // 018100     MOVE      SPACES          TO     REPORT-LINE.
        // 018200     WRITE     REPORT-LINE     AFTER 2 LINES.
        errorFileContents.add("");
        errorFileContents.add("");
        // 018300     WRITE     REPORT-LINE     FROM   WK-TITLE-LINE1.
        errorFileContents.add(wkTitleLine1());
        // 018400     MOVE      SPACES          TO     REPORT-LINE.
        // 018500     WRITE     REPORT-LINE     AFTER 2 LINES.
        errorFileContents.add("");
        errorFileContents.add("");
        // 018600     WRITE     REPORT-LINE     FROM   WK-TITLE-LINE2.
        errorFileContents.add(wkTitleLine2());
        // 018700     MOVE      SPACES          TO     REPORT-LINE.
        // 018800     WRITE     REPORT-LINE     AFTER 2 LINES.
        errorFileContents.add("");
        errorFileContents.add("");
        // 018900     WRITE     REPORT-LINE     FROM   WK-TITLE-LINE3.
        errorFileContents.add(wkTitleLine3());
        // 019000 098-WRITET-EXIT.
        // 019100     EXIT.
    }

    // 095-WRITEERROR-RTN
    private void writeerror(CldtlEntdyBeforeBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeerror()");
        // 016400 095-WRITEERROR-RTN.
        // 寫REPORTFL報表明細(WK-ERROR-LINE)
        // 016500     MOVE      DB-CLDTL-CODE   TO     WK-CODE.
        // 016600     MOVE      DB-CLDTL-DATE   TO     WK-DATE.
        // 016700     MOVE      DB-CLDTL-TIME   TO     WK-TIME.
        // 016800     MOVE      DB-CLDTL-CLLBR  TO     WK-CLLBR.
        // 016900     MOVE      DB-CLDTL-RCPTID TO     WK-RCPTID.
        // 017000     MOVE      DB-CLDTL-AMT    TO     WK-AMT.
        // 017100     MOVE      DB-CLDTL-TXTYPE TO     WK-TXTYPE.
        // 017200     MOVE      DB-CLDTL-LMTDATE TO    WK-LMTDATE.
        // 017300     MOVE      DB-CLDTL-TRMNO  TO     WK-TRMNO.
        // 017400     MOVE      DB-CLDTL-TLRNO  TO     WK-TLRNO.
        // 017500     MOVE      DB-CLDTL-USERDATA TO   WK-USERDATA.
        // 017600     MOVE      SPACES          TO     REPORT-LINE.
        // 017700     WRITE     REPORT-LINE     FROM   WK-ERROR-LINE.
        errorFileContents.add(wkErrorLine(cldtl));
        textFileUtil.writeFileContent(errorReportPath, errorFileContents, BIG5);
        upload(errorReportPath, "RPT", "");
        errorFileContents = new ArrayList<>();
        // 017800 095-WRITEERROR-EXIT.
        // 017900     EXIT.
    }

    private String wkTitleLine1() {
        // 003500 01 WK-TITLE-LINE1.
        // 003600    02 FILLER                          PIC X(20) VALUE SPACE.
        // 003700    02 FILLER                          PIC X(52) VALUE
        // 003800         " 連  線  代  收  業  務  錯  誤  名  細  記  錄  表 ".
        // 003900    02 FILLER                          PIC X(32) VALUE SPACE.
        // 004000    02 FILLER                          PIC X(11) VALUE
        // 004100          "FORM : C004".
        String wkTitleLine1;
        wkTitleLine1 = formatUtil.padX("", 20);
        wkTitleLine1 += formatUtil.padX(" 連  線  代  收  業  務  錯  誤  明  細  記  錄  表 ", 52);
        wkTitleLine1 += formatUtil.padX("", 32);
        wkTitleLine1 += formatUtil.padX("FORM : C004", 11);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine1 = {}", wkTitleLine1);
        return wkTitleLine1;
    }

    private String wkTitleLine2() {
        // 004200 01 WK-TITLE-LINE2.
        // 004300    02 FILLER                          PIC X(09) VALUE SPACE.
        // 004400    02 FILLER                          PIC X(12) VALUE
        // 004500         " 資料日期 : ".
        // 004600    02 WK-YYMMDD                       PIC 9(07).
        // 004700    02 FILLER                          PIC X(77) VALUE SPACE.
        // 004800    02 FILLER                          PIC X(10) VALUE
        // 004900          " 頁數 : 01".
        String wkTitleLine2;
        wkTitleLine2 = formatUtil.padX("", 9);
        wkTitleLine2 += formatUtil.padX(" 資料日期 : ", 12);
        wkTitleLine2 += parse.decimal2String(processDate, 7, 0);
        wkTitleLine2 += formatUtil.padX("", 77);
        wkTitleLine2 += formatUtil.padX(" 頁數 : 01", 10);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine2 = {}", wkTitleLine2);
        return wkTitleLine2;
    }

    private String wkTitleLine3() {
        // 005000 01 WK-TITLE-LINE3.
        // 005100    02 FILLER              PIC X(02) VALUE SPACE.
        // 005200    02 FILLER              PIC X(08) VALUE " CLLBR  ".
        // 005300    02 FILLER              PIC X(09) VALUE "  CODE   ".
        // 005400    02 FILLER              PIC X(11) VALUE "   DATE    ".
        // 005500    02 FILLER              PIC X(11) VALUE "   TIME    ".
        // 005600    02 FILLER              PIC X(16) VALUE "    AMOUNT      ".
        // 005700    02 FILLER              PIC X(19) VALUE "      RCPTID       ".
        // 005800    02 FILLER              PIC X(05) VALUE "KIND ".
        // 005900    02 FILLER              PIC X(11) VALUE " LMTDATE   ".
        // 006000    02 FILLER              PIC X(08) VALUE "TRMNO   ".
        // 006100    02 FILLER              PIC X(08) VALUE "TLRNO   ".
        // 006200    02 FILLER              PIC X(16) VALUE "      USERDATA  ".
        // 006250    02 FILLER              PIC X(16) VALUE "      REASON    ".
        String wkTitleLine3;
        wkTitleLine3 = formatUtil.padX("", 2);
        wkTitleLine3 += formatUtil.padX(" CLLBR  ", 8);
        wkTitleLine3 += formatUtil.padX("  CODE   ", 9);
        wkTitleLine3 += formatUtil.padX("   DATE    ", 11);
        wkTitleLine3 += formatUtil.padX("   TIME    ", 11);
        wkTitleLine3 += formatUtil.padX("    AMOUNT      ", 16);
        wkTitleLine3 += formatUtil.padX("      RCPTID       ", 19);
        wkTitleLine3 += formatUtil.padX("KIND ", 5);
        wkTitleLine3 += formatUtil.padX(" LMTDATE   ", 11);
        wkTitleLine3 += formatUtil.padX("TRMNO   ", 8);
        wkTitleLine3 += formatUtil.padX("TLRNO   ", 8);
        wkTitleLine3 += formatUtil.padX("      USERDATA  ", 16);
        wkTitleLine3 += formatUtil.padX("      REASON    ", 16);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine3 = {}", wkTitleLine3);
        return wkTitleLine3;
    }

    private String wkErrorLine(CldtlEntdyBeforeBus cldtl) {
        // 006300 01 WK-ERROR-LINE.
        String wkErrorLine = "";
        // 006400    02 FILLER                          PIC X(05) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 5);
        // 006500    02 WK-CLLBR                        PIC 9(03).
        wkErrorLine += parse.decimal2String(cldtl.getCllbr(), 3, 0);
        // 006600    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 006700    02 WK-CODE                         PIC X(06).
        wkErrorLine += formatUtil.padX(cldtl.getCode(), 6);
        // 006800    02 FILLER                          PIC X(02) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 2);
        // 006900    02 WK-DATE                         PIC Z99/99/99.
        wkErrorLine +=
                reportUtil.customFormat(parse.decimal2String(cldtl.getEntdy(), 7, 0), "Z99/99/99");
        // 007000    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 007100    02 WK-TIME                         PIC 99:99:99.
        wkErrorLine +=
                reportUtil.customFormat(parse.decimal2String(cldtl.getTime(), 6, 0), "99:99:99");
        // 007200    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 007300    02 WK-AMT                          PIC Z,ZZZ,ZZZ,ZZ9.
        wkErrorLine +=
                reportUtil.customFormat(
                        parse.decimal2String(cldtl.getAmt(), 10, 0), "Z,ZZZ,ZZZ,ZZ9");
        // 007400    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 007500    02 WK-RCPTID                       PIC X(16).
        wkErrorLine += formatUtil.padX(cldtl.getRcptid(), 16);
        // 007600    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 007700    02 WK-TXTYPE                       PIC X(01).
        wkErrorLine += formatUtil.padX(cldtl.getTxtype(), 1);
        // 007800    02 FILLER                          PIC X(02) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 2);
        // 007900    02 WK-LMTDATE                      PIC Z99/99/99.
        wkErrorLine +=
                reportUtil.customFormat(
                        parse.decimal2String(cldtl.getLmtdate(), 7, 0), "Z99/99/99");
        // 008000    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 008050*FOR 24hr-2 94/12/21 CSChen
        // 008100*   02 WK-TRMNO                        PIC 9(05).
        // 008200*   02 FILLER                          PIC X(05) VALUE SPACE.
        // 008220    02 WK-TRMNO                        PIC 9(07).
        wkErrorLine += parse.decimal2String(cldtl.getTrmno(), 7, 0);
        // 008240    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 008260*END 24hr-2
        // 008300    02 WK-TLRNO                        PIC X(02).
        wkErrorLine += parse.decimal2String(cldtl.getTlrno(), 2, 0);
        // 008400    02 FILLER                          PIC X(03) VALUE SPACE.
        wkErrorLine += formatUtil.padX("", 3);
        // 008500    02 WK-USERDATA                     PIC X(16).
        wkErrorLine += formatUtil.padX(cldtl.getUserdata(), 3);
        // 008600    02 FILLER                          PIC X(03) VALUE SPACES.
        wkErrorLine += formatUtil.padX("", 3);
        // 008700    02 WK-TEXT                         PIC X(40).
        wkErrorLine += formatUtil.padX(wkText, 40);
        return wkErrorLine;
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

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", "CL-BH-004");

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
