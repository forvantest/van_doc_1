/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.Updtax;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("UpdtaxLsnr")
@Scope("prototype")
public class UpdtaxLsnr extends BatchListenerCase<Updtax> {

    @Autowired private DateUtil dateUtil;

    @Autowired private FormatUtil formatUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private Parse parse;

    @Autowired private ClmrService clmrService;

    private int tbsdy;
    private int nbsdy;
    private int lbsdy;
    private int llbsdy;
    private int l3bsdy;
    private int l4bsdy;
    private int l5bsdy;
    private int lmndy;
    private int fnbsdy;

    // 003300 01 WK-CYCNO                           PIC 9(01).
    private int wkCycno;

    // WK-NETINFO
    // 003600 01 WK-NETINFO.
    private String wkNetinfo;
    private String wkNetinfo1;
    // 003700   03  WK-WEEKSTART.
    // 003800     05 WK-STARTYEAR                   PIC 9(02).
    private String wkStartyear;
    private String wkStartyear1;
    // 003900     05 WK-STARTMONTH                  PIC 9(02).
    private String wkStartmonth;
    private String wkStartmonth1;
    // 004000     05 WK-STARTDATE                   PIC 9(02).
    private String wkStartdate;
    private String wkStartdate1;
    // 004100   03  WK-LMNDY                        PIC X(06).
    private String wkLmndy = "      ";
    private String wkLmndy1 = "      ";
    // 004200   03  WK-LMNDY-R  REDEFINES  WK-LMNDY.
    // 004300     05 WK-LMNDY-LMNYY                 PIC 9(02).
    // 004400     05 WK-LMNDY-LMNMM                 PIC 9(02).
    // 004500     05 WK-LMNDY-LMNDD                 PIC 9(02).
    // 004600   03  WK-WEEKEND.
    // 004700     05 WK-ENDYEAR                     PIC 9(02).
    private String wkEndyear;
    // 004800     05 WK-ENDMONTH                    PIC 9(02).
    private String wkEndmonth;
    // 004900     05 WK-ENDDATE                     PIC 9(02).
    private String wkEnddate;
    // 005000   03  FILLER                          PIC X(02).

    private boolean taxweekFlag;
    private boolean taxmonFlag;
    private boolean taxyearFlag;
    private boolean taxyear1Flag;

    private int clmrPageIndex;

    private int clmrPageLimit = 10000;

    @Override
    public void onApplicationEvent(Updtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdtaxLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Updtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdtaxLsnr run()");

        init(event);

        // 0000-TAXWEEK-RTN
        taxweek();

        // 0000-TAXMON-RTN
        taxmon();

        // 0000-TAXYEAR-RTN
        taxyear();

        // 1000-TAXYEAR-RTN
        taxyear1();

        updateClmrNetInfo();
        batchResponse(event);
    }

    private void init(Updtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        clmrPageIndex = 0;
        tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        nbsdy = event.getAggregateBuffer().getTxCom().getNbsdy();
        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        llbsdy = event.getAggregateBuffer().getTxCom().getLlbsdy();
        l3bsdy = event.getAggregateBuffer().getTxCom().getL3bsdy();
        l4bsdy = event.getAggregateBuffer().getTxCom().getL4bsdy();
        l5bsdy = event.getAggregateBuffer().getTxCom().getL5bsdy();
        lmndy = event.getAggregateBuffer().getTxCom().getLmndy();
        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);
        taxweekFlag = false;
        taxmonFlag = false;
        taxyearFlag = false;
        taxyear1Flag = false;
    }

    // 0000-TAXWEEK-RTN
    private void taxweek() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxweek()");
        // 008799* 非直轄市地方稅 / 燃料費 : 本週第一個營業日至週末
        // 008800 0000-TAXWEEK-RTN.
        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 008900     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 確定今日是否為本週最後營業日
        // 009000     PERFORM CHK-WEEKLAST-RTN   THRU    CHK-WEEKLAST-EXIT.
        // WK-RTN本週最後營業日註記:0.否、1.是
        // 今日非本週最後營業日，GO TO 0000-TAXWEEK-EXIT，結束本段落
        // 今日為本週最後營業日，WK-NETINFO清空白，往下執行
        // 009100     IF      WK-RTN              =      0
        // 009200       GO TO 0000-TAXWEEK-EXIT
        // 009300     ELSE
        // 009400       MOVE  SPACES  TO   WK-NETINFO.

        // 智偉:若本營業日與下營業日中間有一個禮拜日,則今日為本周最後營業日
        boolean isWeekLast = chkWeeklast();
        if (isWeekLast) {
            // 今日為本週最後營業日

        } else {
            // 今日非本週最後營業日
            return;
        }

        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 搬FD-BHDATE-...本營業日給WK-WEEKEND
        // 009500     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 009600     MOVE    FD-BHDATE-TBSYY    TO      WK-ENDYEAR.
        // 009700     MOVE    FD-BHDATE-TBSMM    TO      WK-ENDMONTH.
        // 009800     MOVE    FD-BHDATE-TBSDD    TO      WK-ENDDATE.
        // 009900     MOVE    FD-BHDATE-WEEKDY   TO      WK-CYCNO
        wkEndyear = parse.decimal2String(tbsdy / 10000 % 100, 2, 0);
        wkEndmonth = parse.decimal2String(tbsdy / 100 % 100, 2, 0);
        wkEnddate = parse.decimal2String(tbsdy % 100, 2, 0);
        wkCycno = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndyear = {}", wkEndyear);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndmonth = {}", wkEndmonth);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndDate = {}", wkEnddate);

        // 尋找本週週一及跨月時上月月底日
        // 010000     PERFORM 0000-FINDDATE-RTN  THRU    0000-FINDDATE-EXIT.
        findStartDate();

        // 將DB-CLMR-DDS指標移至開始
        // 010100     SET     DB-CLMR-DDS         TO     BEGINNING.
        // 010200 0000-TAXWEEK-LOOP.
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至0000-TAXWEEK-EXIT，結束本段落
        // 010300     LOCK NEXT DB-CLMR-DDS ON EXCEPTION GO TO 0000-TAXWEEK-EXIT.
        // 若符合條件，更新DB-CLMR-NETINFO
        // 010400     MOVE    DB-CLMR-CODE    TO     WK-CODE.
        // 010500     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 010520          AND (WK-CODE1-2      = "1" OR = "2" OR = "3"
        // 010540                            OR = "6" OR = "7" OR = "8"        ) )
        // 010600       OR ( WK-CODE1           = "11331" OR = "11521"           )
        // 010700       MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 010800       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 010900       STORE  DB-CLMR-DDS
        // 011000       END-TRANSACTION NO-AUDIT RESTART-DST.
        // 011050
        // LOOP讀下一筆CLMR
        // 011100     GO   TO  0000-TAXWEEK-LOOP.

        // 智偉:改成最後對CLMR forloop一次 這裡先上flag即可
        taxweekFlag = true;
        wkNetinfo =
                wkStartyear
                        + wkStartmonth
                        + wkStartdate
                        + wkLmndy
                        + wkEndyear
                        + wkEndmonth
                        + wkEnddate
                        + "  ";

        // 011200 0000-TAXWEEK-EXIT.
        // 011300     EXIT.
    }

    // CHK-WEEKLAST-RTN
    private boolean chkWeeklast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkWeeklast()");
        // 智偉:若本營業日與下營業日中間有一個禮拜日,則今日為本周最後營業日
        DateDto dateDto = new DateDto();
        dateDto.setDateS(tbsdy);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        if (days >= 2) {
            for (int i = 1; i < days; i++) {
                dateDto.init();
                dateDto.setDateS(tbsdy);
                dateDto.setDays(i);
                dateUtil.getCalenderDay(dateDto);
                int tempDate = dateDto.getDateE2Integer(false);
                int tempWeekdy = getWeekdy(tempDate);
                if (tempWeekdy == 7) {
                    return true;
                }
            }
        }
        return false;
    }

    // 0000-FINDDATE-RTN
    private void findStartDate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findStartDate()");
        // 尋找本週週一及跨月時上月月底日
        // 029300 0000-FINDDATE-RTN.
        // 029400     MOVE     SPACES           TO     WK-LMNDY.
        // 029500 0000-FINDDATE-LOOP.

        // 若為週一(WK-CYCNO星期幾)，搬本日給WK-WEEKSTART，結束本段落
        // 029600     IF       WK-CYCNO         =      1
        // 029620        MOVE  FD-CLNDR-TBSYY   TO     WK-STARTYEAR
        // 029640        MOVE  FD-CLNDR-TBSMM   TO     WK-STARTMONTH
        // 029660        MOVE  FD-CLNDR-TBSDD   TO     WK-STARTDATE
        // 029700        GO TO 0000-FINDDATE-EXIT.
        if (wkCycno == 1) {
            wkStartyear = wkEndyear;
            wkStartmonth = wkEndmonth;
            wkStartdate = wkEnddate;
            return;
        }

        // 從本營業日往前找CLNDR
        // 029800     SUBTRACT 1                FROM   WK-CYCNO.
        // 029900     SUBTRACT 1                FROM   WK-CLNDR-KEY.
        // 030000     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 030100     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        // 030200          GO TO 0000-END-RTN.
        int tempWeekdy = getWeekdy(lbsdy);
        if (tempWeekdy == 1) {
            setWkStart(lbsdy);
            return;
        }
        tempWeekdy = getWeekdy(llbsdy);
        if (tempWeekdy == 1) {
            setWkStart(llbsdy);
            return;
        }
        tempWeekdy = getWeekdy(l3bsdy);
        if (tempWeekdy == 1) {
            setWkStart(l3bsdy);
            return;
        }
        tempWeekdy = getWeekdy(l4bsdy);
        if (tempWeekdy == 1) {
            setWkStart(l4bsdy);
            return;
        }
        tempWeekdy = getWeekdy(l5bsdy);
        if (tempWeekdy == 1) {
            setWkStart(l5bsdy);
            return;
        }
        // LOOP往前找FD-CLNDR
        // 031500     GO TO 0000-FINDDATE-LOOP.
        // 031600 0000-FINDDATE-EXIT.
        // 031700     EXIT.
    }

    private void setWkStart(int date) {
        // 若營業，搬FD-CLNDR-...本日給WK-WEEKSTART(多餘，最後還是會被029600-029700取代掉???)
        //  若跨月，搬FD-BHDATE-...上月月底日給WK-LMNDY???
        // 030300     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 030400        MOVE  FD-CLNDR-TBSYY   TO     WK-STARTYEAR
        // 030500        MOVE  FD-CLNDR-TBSMM   TO     WK-STARTMONTH
        // 030600        MOVE  FD-CLNDR-TBSDD   TO     WK-STARTDATE
        wkStartyear = parse.decimal2String(date / 10000 % 100, 2, 0);
        wkStartmonth = parse.decimal2String(date / 100 % 100, 2, 0);
        wkStartdate = parse.decimal2String(date % 100, 2, 0);

        // 030700        IF    FD-CLNDR-TBSMM  NOT =   FD-BHDATE-TBSMM
        // 030800           MOVE  FD-BHDATE-LMNYY  TO  WK-LMNDY-LMNYY
        // 030900           MOVE  FD-BHDATE-LMNMM  TO  WK-LMNDY-LMNMM
        // 031000           MOVE  FD-BHDATE-LMNDD  TO  WK-LMNDY-LMNDD.
        if (!wkEndmonth.equals(wkStartmonth)) {
            wkLmndy = parse.decimal2String(lmndy / 10000, 6, 0);
        }
    }

    // 0000-TAXMON-RTN
    private void taxmon() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxmon()");
        // 016950* 非直轄市地方稅 : 本週第一個營業日至月底  990701 結束運作
        // 017000 0000-TAXMON-RTN.
        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 017100     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 確定今日是否為本月最後營業日
        // 017200     PERFORM CHK-MONLAST-RTN    THRU    CHK-MONLAST-EXIT.
        boolean isMonlast = chkMonlast();

        // WK-RTN本月最後營業日註記:0.否、1.是
        // 今日非本月最後營業日，GO TO 0000-TAXMON-EXIT，結束本段落
        // 今日為本月最後營業日，WK-NETINFO清空白，往下執行
        // 017300     IF      WK-RTN              =      0
        // 017400       GO TO 0000-TAXMON-EXIT
        // 017500     ELSE
        // 017600       MOVE  SPACES  TO   WK-NETINFO.
        if (isMonlast) {
            // 今日為本月最後營業日
        } else {
            // 今日非本月最後營業日
            return;
        }
        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 搬FD-BHDATE-...本營業日給WK-WEEKEND
        // 017700     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 017800     MOVE    FD-BHDATE-TBSYY    TO      WK-ENDYEAR.
        // 017900     MOVE    FD-BHDATE-TBSMM    TO      WK-ENDMONTH.
        // 018000     MOVE    FD-BHDATE-TBSDD    TO      WK-ENDDATE.
        // 018100     MOVE    FD-BHDATE-WEEKDY   TO      WK-CYCNO.
        wkEndyear = parse.decimal2String(tbsdy / 10000 % 100, 2, 0);
        wkEndmonth = parse.decimal2String(tbsdy / 100 % 100, 2, 0);
        wkEnddate = parse.decimal2String(tbsdy % 100, 2, 0);
        wkCycno = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndyear = {}", wkEndyear);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndmonth = {}", wkEndmonth);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndDate = {}", wkEnddate);

        // 尋找本週週一
        // 018200     PERFORM 0000-FINDDATE-RTN  THRU    0000-FINDDATE-EXIT.
        findStartDate();

        // 將DB-CLMR-DDS指標移至開始
        // 018300     SET     DB-CLMR-DDS         TO     BEGINNING.
        // 018400 0000-TAXMON-LOOP.
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至0000-TAXMON-EXIT，結束本段落
        // 18500     LOCK NEXT DB-CLMR-DDS   ON EXCEPTION GO TO 0000-TAXMON-EXIT.
        // 若符合條件，更新DB-CLMR-NETINFO
        // 018600     MOVE    DB-CLMR-CODE    TO     WK-CODE.
        // 018700     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 018800          AND (WK-CODE1-2      = "2" OR = "3" OR
        // 019100                               = "7" OR = "8"        ) )
        // 019300       MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 019400       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 019500       STORE  DB-CLMR-DDS
        // 019600       END-TRANSACTION NO-AUDIT RESTART-DST.

        // 智偉:改成最後對CLMR forloop一次 這裡先上flag即可
        taxmonFlag = true;
        wkNetinfo =
                wkStartyear
                        + wkStartmonth
                        + wkStartdate
                        + wkLmndy
                        + wkEndyear
                        + wkEndmonth
                        + wkEnddate
                        + "  ";

        // LOOP讀下一筆CLMR
        // 019700     GO TO  0000-TAXMON-LOOP.
        // 019800 0000-TAXMON-EXIT.
        // 019900     EXIT.
    }

    // CHK-MONLAST-RTN
    private boolean chkMonlast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkMonlast()");
        // 037200*-------- 確定今日是否為本月最後營業日 --------------------------
        // 037300 CHK-MONLAST-RTN.
        // 037400     MOVE       0                TO     WK-RTN.
        // 若本營業日等於本月最終營業日，本月最後營業日註記設為1
        // 037500     IF  FD-BHDATE-FNBSDY        =      FD-BHDATE-TBSDY
        // 037600       MOVE     1                TO     WK-RTN.
        // 037700 CHK-MONLAST-EXIT.
        // 037800     EXIT.
        if (tbsdy == fnbsdy) {
            return true;
        }
        return false;
    }

    // 0000-TAXYEAR-RTN
    private void taxyear() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxyear()");
        // 020050* 非直轄市地方稅 : 本週第一個營業日至年底
        // 020100 0000-TAXYEAR-RTN.
        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 020200     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 確定今日是否為本年最後營業日
        // 020300     PERFORM CHK-YEARLAST-RTN   THRU    CHK-YEARLAST-EXIT.
        boolean isYearlast = chkYearlast();

        // WK-RTN本年最後營業日註記:0.否、1.是
        // 今日非本年最後營業日，GO TO 0000-TAXYEAR-EXIT，結束本段落
        // 今日為本年最後營業日，WK-NETINFO清空白，往下執行
        // 020400     IF      WK-RTN              =      0
        // 020500       GO TO 0000-TAXYEAR-EXIT
        // 020600     ELSE
        // 020700       MOVE  SPACES  TO   WK-NETINFO.
        if (isYearlast) {
            // 今日為本年最後營業日

        } else {
            // 今日非本年最後營業日
            return;
        }

        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 搬FD-BHDATE-...本營業日給WK-WEEKEND
        // 020800     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 020900     MOVE    FD-BHDATE-TBSYY    TO      WK-ENDYEAR.
        // 021000     MOVE    FD-BHDATE-TBSMM    TO      WK-ENDMONTH.
        // 021100     MOVE    FD-BHDATE-TBSDD    TO      WK-ENDDATE.
        // 021200     MOVE    FD-BHDATE-WEEKDY   TO      WK-CYCNO.
        wkEndyear = parse.decimal2String(tbsdy / 10000 % 100, 2, 0);
        wkEndmonth = parse.decimal2String(tbsdy / 100 % 100, 2, 0);
        wkEnddate = parse.decimal2String(tbsdy % 100, 2, 0);
        wkCycno = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndyear = {}", wkEndyear);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndmonth = {}", wkEndmonth);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndDate = {}", wkEnddate);

        // 尋找本週週一
        // 021300     PERFORM 0000-FINDDATE-RTN  THRU    0000-FINDDATE-EXIT.
        findStartDate();

        // 將DB-CLMR-DDS指標移至開始
        // 021400     SET     DB-CLMR-DDS         TO     BEGINNING.
        // 021500 0000-TAXYEAR-LOOP.
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至0000-TAXYEAR-EXIT，結束本段落
        // 021600     LOCK NEXT DB-CLMR-DDS   ON EXCEPTION GO TO 0000-TAXYEAR-EXIT.

        // 若符合條件，更新DB-CLMR-NETINFO
        // 021700     MOVE    DB-CLMR-CODE    TO     WK-CODE.
        // 021800     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 021900          AND (WK-CODE1-2    = "2" OR = "3" OR = "7" OR = "8" ) )
        // 022250         MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 022300         BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 022400         STORE  DB-CLMR-DDS
        // 022500         END-TRANSACTION NO-AUDIT RESTART-DST.

        // 智偉:改成最後對CLMR forloop一次 這裡先上flag即可
        taxyearFlag = true;
        wkNetinfo =
                wkStartyear
                        + wkStartmonth
                        + wkStartdate
                        + wkLmndy
                        + wkEndyear
                        + wkEndmonth
                        + wkEnddate
                        + "  ";

        // LOOP讀下一筆CLMR
        // 022600     GO TO 0000-TAXYEAR-LOOP.
        // 022700 0000-TAXYEAR-EXIT.
        // 022800   EXIT.
    }

    // PERFORM CHK-YEARLAST-RTN
    private boolean chkYearlast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkYearlast()");
        // 037900*-------- 確定今日是否為本年最後營業日 --------------------------
        // 038000 CHK-YEARLAST-RTN.
        // 038100     MOVE       0                TO     WK-RTN.
        // 若本營業日等於本月最終營業日且月份為12，本年最後營業日註記設為1
        // 038200     IF     FD-BHDATE-TBSMM      =      12
        // 038300        AND FD-BHDATE-TBSDD      =      FD-BHDATE-FNBSDD
        // 038400       MOVE     1                TO     WK-RTN.
        int tbsmm = tbsdy / 100 % 100;
        int tbsdd = tbsdy % 100;
        int fnbsdd = fnbsdy % 100;
        if (tbsmm == 12 && tbsdd == fnbsdd) {
            return true;
        }
        return false;
        // 038500 CHK-YEARLAST-EXIT.
        // 038600     EXIT.
    }

    // 1000-TAXYEAR-RTN
    private void taxyear1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "taxyear1()");
        // 022950* 非直轄市國稅 / 直轄市地方稅 : 本週第一個週一或週四至年底
        // 022970*        代收起迄時間為本年底日向前推至第一個週四或週一為止
        // 023000 1000-TAXYEAR-RTN.
        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 023300     PERFORM POINTTBSDY-RTN     THRU    POINTTBSDY-EXIT.
        // 確定今日是否為本年最後營業日
        // 023400     PERFORM CHK-YEARLAST-RTN   THRU    CHK-YEARLAST-EXIT.
        boolean isYearLast = chkYearlast();

        // WK-RTN本年最後營業日註記:0.否、1.是
        // 今日非本年最後營業日，GO TO 1000-TAXYEAR-EXIT，結束本段落
        // 今日為本年最後營業日，WK-NETINFO清空白，往下執行
        // 023500     IF      WK-RTN              =      0
        // 023600       GO TO 1000-TAXYEAR-EXIT
        // 023700     ELSE
        // 023800       MOVE  SPACES  TO   WK-NETINFO.
        if (isYearLast) {
            // 今日為本年最後營業日

        } else {
            // 今日非本年最後營業日
            return;
        }

        // 執行POINTTBSDY-RTN，找到本營業日的日曆資料
        // 搬FD-BHDATE-...本營業日給WK-WEEKEND
        // 023900 PERFORM POINTTBSDY -RTN THRU POINTTBSDY -EXIT.
        // 024000 MOVE FD-BHDATE-TBSYY TO WK-ENDYEAR.
        // 024100 MOVE FD-BHDATE-TBSMM TO WK-ENDMONTH.
        // 024200 MOVE FD-BHDATE-TBSDD TO WK-ENDDATE.
        // 024300 MOVE FD-BHDATE-WEEKDY TO WK-CYCNO.
        wkEndyear = parse.decimal2String(tbsdy / 10000 % 100, 2, 0);
        wkEndmonth = parse.decimal2String(tbsdy / 100 % 100, 2, 0);
        wkEnddate = parse.decimal2String(tbsdy % 100, 2, 0);
        wkCycno = getWeekdy(tbsdy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndyear = {}", wkEndyear);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndmonth = {}", wkEndmonth);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEndDate = {}", wkEnddate);

        // 回頭尋找週一或週四
        // 024400 PERFORM 2000-FINDDATE-RTN THRU 2000-FINDDATE-EXIT.
        findStartDate2();

        // 將DB-CLMR-DDS指標移至開始
        // 024500 SET DB -CLMR-DDS TO BEGINNING.
        // 024600 1000-TAXYEAR-LOOP.
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至1000-TAXYEAR-EXIT，結束本段落
        // 024700 LOCK NEXT DB-CLMR-DDS ON EXCEPTION GO TO 1000-TAXYEAR-EXIT.
        // 若符合條件，更新DB-CLMR-NETINFO
        // 024800 MOVE DB -CLMR-CODE TO WK -CODE.
        // 024900 IF((WK-CODE1 NOT < "11011"AND WK-CODE1 NOT > "11115")
        // 025000 AND(WK-CODE1-2 = "4"OR = "5"OR = "9"OR = "0") )
        // 025050 OR(WK-CODE1 = "11523")
        // 025250 MOVE WK -NETINFO TO DB -CLMR-NETINFO
        // 025300 BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 025400 STORE DB -CLMR-DDS
        // 025500 END-TRANSACTION NO-AUDIT RESTART-DST.

        // 智偉:改成最後對CLMR forloop一次 這裡先上flag即可
        taxyear1Flag = true;
        wkNetinfo1 =
                wkStartyear1
                        + wkStartmonth1
                        + wkStartdate1
                        + wkLmndy1
                        + wkEndyear
                        + wkEndmonth
                        + wkEnddate
                        + "  ";

        // LOOP讀下一筆CLMR
        // 025600 GO TO 1000-TAXYEAR-LOOP.
        // 025700 1000-TAXYEAR-EXIT.
    }

    // 2000-FINDDATE-RTN
    private void findStartDate2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findStartDate2()");
        // 034600*---------- 回頭詢找第一個週二或週四 ---------------------------
        // 034700 2000-FINDDATE-RTN.
        // 034800     MOVE     SPACES           TO     WK-LMNDY.
        // 034900 2000-FINDDATE-LOOP.

        // 若為週一或週四(WK-CYCNO星期幾)，搬本日給WK-WEEKSTART，結束本段落
        // 035000     IF       WK-CYCNO         =      1  OR = 4
        // 035020        MOVE  FD-CLNDR-TBSYY   TO     WK-STARTYEAR
        // 035040        MOVE  FD-CLNDR-TBSMM   TO     WK-STARTMONTH
        // 035060        MOVE  FD-CLNDR-TBSDD   TO     WK-STARTDATE
        // 035100        GO TO 2000-FINDDATE-EXIT.
        if (wkCycno == 1 || wkCycno == 4) {
            wkStartyear1 = wkEndyear;
            wkStartmonth1 = wkEndmonth;
            wkStartdate1 = wkEnddate;
            return;
        }

        // 從本營業日往前找CLNDR
        // 035200     SUBTRACT 1                FROM   WK-CYCNO.
        // 035300     SUBTRACT 1                FROM   WK-CLNDR-KEY.
        // 035400     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 035500     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO -1
        // 035600          GO TO 0000-END-RTN.
        // 若營業，搬FD-CLNDR-...本日給WK-WEEKSTART(多餘，最後還是會被035000-035100取代掉???)
        //  若跨月，搬FD-BHDATE-...上月月底日給WK-LMNDY???
        // 035700     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 035800        MOVE  FD-CLNDR-TBSYY   TO     WK-STARTYEAR
        // 035900        MOVE  FD-CLNDR-TBSMM   TO     WK-STARTMONTH
        // 036000        MOVE  FD-CLNDR-TBSDD   TO     WK-STARTDATE
        // 036100        IF    FD-CLNDR-TBSMM  NOT =   FD-BHDATE-TBSMM
        // 036200           MOVE  FD-BHDATE-LMNYY  TO  WK-LMNDY-LMNYY
        // 036300           MOVE  FD-BHDATE-LMNMM  TO  WK-LMNDY-LMNMM
        // 036400           MOVE  FD-BHDATE-LMNDD  TO  WK-LMNDY-LMNDD.
        int tempWeekdy = getWeekdy(lbsdy);
        if (tempWeekdy == 1 || tempWeekdy == 4) {
            setWkStart1(lbsdy);
            return;
        }
        tempWeekdy = getWeekdy(llbsdy);
        if (tempWeekdy == 1 || tempWeekdy == 4) {
            setWkStart1(llbsdy);
            return;
        }
        tempWeekdy = getWeekdy(l3bsdy);
        if (tempWeekdy == 1 || tempWeekdy == 4) {
            setWkStart1(l3bsdy);
            return;
        }
        tempWeekdy = getWeekdy(l4bsdy);
        if (tempWeekdy == 1 || tempWeekdy == 4) {
            setWkStart1(l4bsdy);
            return;
        }
        tempWeekdy = getWeekdy(l5bsdy);
        if (tempWeekdy == 1 || tempWeekdy == 4) {
            setWkStart1(l5bsdy);
            return;
        }

        // LOOP往前找FD-CLNDR
        // 036900     GO TO 2000-FINDDATE-LOOP.
        // 037000 2000-FINDDATE-EXIT.
        // 037100     EXIT.
    }

    private void setWkStart1(int date) {
        // 若營業，搬FD-CLNDR-...本日給WK-WEEKSTART(多餘，最後還是會被029600-029700取代掉???)
        //  若跨月，搬FD-BHDATE-...上月月底日給WK-LMNDY???
        // 030300     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 030400        MOVE  FD-CLNDR-TBSYY   TO     WK-STARTYEAR
        // 030500        MOVE  FD-CLNDR-TBSMM   TO     WK-STARTMONTH
        // 030600        MOVE  FD-CLNDR-TBSDD   TO     WK-STARTDATE
        wkStartyear1 = parse.decimal2String(date / 10000 % 100, 2, 0);
        wkStartmonth1 = parse.decimal2String(date / 100 % 100, 2, 0);
        wkStartdate1 = parse.decimal2String(date % 100, 2, 0);

        // 030700        IF    FD-CLNDR-TBSMM  NOT =   FD-BHDATE-TBSMM
        // 030800           MOVE  FD-BHDATE-LMNYY  TO  WK-LMNDY-LMNYY
        // 030900           MOVE  FD-BHDATE-LMNMM  TO  WK-LMNDY-LMNMM
        // 031000           MOVE  FD-BHDATE-LMNDD  TO  WK-LMNDY-LMNDD.
        if (!wkEndmonth.equals(wkStartmonth)) {
            wkLmndy1 = parse.decimal2String(lmndy / 10000, 6, 0);
        }
    }

    private void updateClmrNetInfo() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "updateClmrNetInfo()");
        List<ClmrBus> clmrList = clmrService.findAll(clmrPageIndex, clmrPageLimit);
        if (!Objects.isNull(clmrList) && !clmrList.isEmpty()) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "clmrList.size() = {}", clmrList.size());
            for (ClmrBus clmr : clmrList) {
                String code = formatUtil.padX(clmr.getCode(), 6);
                if (taxweekFlag) {
                    taxweekFilterAndUpdate(code);
                }
                if (taxmonFlag) {
                    taxmonFilterAndUpdate(code);
                }
                if (taxyearFlag) {
                    taxyearFilterAndUpdate(code);
                }
                if (taxyear1Flag) {
                    taxyear1FilterAndUpdate(code);
                }
            }
            if (clmrList.size() == clmrPageLimit) {
                clmrPageIndex++;
                updateClmrNetInfo();
            }
        } else {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmr is null");
        }
    }

    private void taxweekFilterAndUpdate(String code) {
        // 010500     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 010520          AND (WK-CODE1-2      = "1" OR = "2" OR = "3"
        // 010540                            OR = "6" OR = "7" OR = "8"        ) )
        // 010600       OR ( WK-CODE1           = "11331" OR = "11521"           )
        // 010700       MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 010800       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 010900       STORE  DB-CLMR-DDS
        // 011000       END-TRANSACTION NO-AUDIT RESTART-DST.
        String wkCode1 = code.substring(0, 5);
        String wkCode12 = code.substring(5);
        int intWkCode1 = parse.isNumeric(wkCode1) ? parse.string2Integer(wkCode1) : 0;
        if (((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                        && (wkCode12.equals("1")
                                || wkCode12.equals("2")
                                || wkCode12.equals("3")
                                || wkCode12.equals("6")
                                || wkCode12.equals("7")
                                || wkCode12.equals("8")))
                || (intWkCode1 == 11331 || intWkCode1 == 11521)) {
            holdAndUpdateClmrNetInfo(code);
        }
    }

    private void taxmonFilterAndUpdate(String code) {
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至0000-TAXMON-EXIT，結束本段落
        // 18500     LOCK NEXT DB-CLMR-DDS   ON EXCEPTION GO TO 0000-TAXMON-EXIT.
        // 若符合條件，更新DB-CLMR-NETINFO
        // 018600     MOVE    DB-CLMR-CODE    TO     WK-CODE.
        // 018700     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 018800          AND (WK-CODE1-2      = "2" OR = "3" OR
        // 019100                               = "7" OR = "8"        ) )
        // 019300       MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 019400       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 019500       STORE  DB-CLMR-DDS
        // 019600       END-TRANSACTION NO-AUDIT RESTART-DST.
        String wkCode1 = code.substring(0, 5);
        String wkCode12 = code.substring(5);
        int intWkCode1 = parse.isNumeric(wkCode1) ? parse.string2Integer(wkCode1) : 0;
        if ((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                && (wkCode12.equals("2")
                        || wkCode12.equals("3")
                        || wkCode12.equals("7")
                        || wkCode12.equals("8"))) {
            holdAndUpdateClmrNetInfo(code);
        }
    }

    private void taxyearFilterAndUpdate(String code) {
        // 若符合條件，更新DB-CLMR-NETINFO
        // 021700     MOVE    DB-CLMR-CODE    TO     WK-CODE.
        // 021800     IF   ( (WK-CODE1 NOT < "11011" AND WK-CODE1 NOT > "11115")
        // 021900          AND (WK-CODE1-2    = "2" OR = "3" OR = "7" OR = "8" ) )
        // 022250         MOVE  WK-NETINFO      TO     DB-CLMR-NETINFO
        // 022300         BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 022400         STORE  DB-CLMR-DDS
        // 022500         END-TRANSACTION NO-AUDIT RESTART-DST.
        String wkCode1 = code.substring(0, 5);
        String wkCode12 = code.substring(5);
        int intWkCode1 = parse.isNumeric(wkCode1) ? parse.string2Integer(wkCode1) : 0;
        if ((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                && (wkCode12.equals("2")
                        || wkCode12.equals("3")
                        || wkCode12.equals("7")
                        || wkCode12.equals("8"))) {
            holdAndUpdateClmrNetInfo(code);
        }
    }

    private void taxyear1FilterAndUpdate(String code) {
        // 將DB-CLMR-DDS指標移至開始
        // 024500 SET DB -CLMR-DDS TO BEGINNING.
        // 024600 1000-TAXYEAR-LOOP.
        // LOCK NEXT DB-CLMR-DDS事業單位基本資料檔，若有誤，跳至1000-TAXYEAR-EXIT，結束本段落
        // 024700 LOCK NEXT DB-CLMR-DDS ON EXCEPTION GO TO 1000-TAXYEAR-EXIT.
        // 若符合條件，更新DB-CLMR-NETINFO
        // 024800 MOVE DB -CLMR-CODE TO WK -CODE.
        // 024900 IF((WK-CODE1 NOT < "11011"AND WK-CODE1 NOT > "11115")
        // 025000 AND(WK-CODE1-2 = "4"OR = "5"OR = "9"OR = "0") )
        // 025050 OR(WK-CODE1 = "11523")
        // 025250 MOVE WK -NETINFO TO DB -CLMR-NETINFO
        // 025300 BEGIN-TRANSACTION NO-AUDIT RESTART-DST
        // 025400 STORE DB -CLMR-DDS
        // 025500 END-TRANSACTION NO-AUDIT RESTART-DST.
        String wkCode1 = code.substring(0, 5);
        String wkCode12 = code.substring(5);
        int intWkCode1 = parse.isNumeric(wkCode1) ? parse.string2Integer(wkCode1) : 0;
        if (((intWkCode1 >= 11011 && intWkCode1 <= 11115)
                        && (wkCode12.equals("4")
                                || wkCode12.equals("5")
                                || wkCode12.equals("9")
                                || wkCode12.equals("0")))
                || intWkCode1 == 11523) {
            holdAndUpdateClmrNetInfo1(code);
        }
    }

    private void holdAndUpdateClmrNetInfo(String code) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "holdAndUpdateClmrNetInfo(), code = {} , wkNetinfo = {}",
                code,
                wkNetinfo);
        ClmrBus holdClmr = clmrService.holdById(code);
        holdClmr.setNetinfo(wkNetinfo);
        clmrService.update(holdClmr);
    }

    private void holdAndUpdateClmrNetInfo1(String code) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "holdAndUpdateClmrNetInfo1(), code = {} , wkNetinfo1 = {}",
                code,
                wkNetinfo1);
        ClmrBus holdClmr = clmrService.holdById(code);
        holdClmr.setNetinfo(wkNetinfo1);
        clmrService.update(holdClmr);
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    private void batchResponse(Updtax event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
