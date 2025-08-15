/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.Pretrust;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("PretrustLsnr")
@Scope("prototype")
public class PretrustLsnr extends BatchListenerCase<Pretrust> {

    @Autowired private Parse parse;
    @Autowired private DateUtil dateUtil;

    private String wkTaskNbsdate;
    private int wkYymmdd;
    private String wkTaskWek;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Pretrust event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PretrustLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Pretrust event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PretrustLsnr run()");
        initialize(event);

        startRtn(event);

        // 004800 0000-END-RTN.
        // 004900     DISPLAY "SYM/CL/BH/PRETRUST GENERATE NBSDATE , WEK OK".
        // 005000     CLOSE   FD-BHDATE  WITH SAVE.
        // 005100     CLOSE   FD-CLNDR   WITH SAVE.
        // 005200     STOP RUN.
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/PRETRUST GENERATE NBSDATE , WEK OK");
    }

    private void initialize(Pretrust event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initialize()");
        // 003700 PROCEDURE        DIVISION  USING WK-TASK-NBSDATE,                96/07/10
        // 003750                                  WK-YYMMDD      ,WK-TASK-WEK.    96/07/10

        // ref:
        // 002700 01 WK-TASK-NBSDATE                    PIC X(08).
        // 002500 01 WK-YYMMDD                          PIC 9(06).
        // 002800 01 WK-TASK-WEK                        PIC X(01).
        Map<String, String> attrMap =
                event.getPeripheryRequest().getPayload().getPyheader().getAttributesMap();
        wkTaskNbsdate =
                Objects.isNull(attrMap.get("wkTaskNbsdate")) ? "" : attrMap.get("wkTaskNbsdate");
        wkYymmdd =
                Objects.isNull(attrMap.get("wkYymmdd"))
                        ? 0
                        : parse.string2Integer(attrMap.get("wkYymmdd"));
        wkTaskWek = Objects.isNull(attrMap.get("wkTaskWek")) ? "" : attrMap.get("wkTaskWek");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTaskNbsdate={}", wkTaskNbsdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkYymmdd={}", wkYymmdd);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTaskWek={}", wkTaskWek);
    }

    private void startRtn(Pretrust event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "startRtn()");
        // 003900 0000-START.
        // 004000     OPEN INPUT   FD-BHDATE.
        // 004100     OPEN INPUT   FD-CLNDR.
        // 004200     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 004300     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 004400          STOP RUN.
        // 004500     MOVE    1               TO     WK-CLNDR-KEY.
        // 004600     MOVE    FD-BHDATE-NBSDY TO     WK-YYMMDD , WK-YYMMDD8.       99/12/29
        wkYymmdd = event.getAggregateBuffer().getTxCom().getNbsdy();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkYymmdd={}", wkYymmdd);
        // 004700     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.
        mainRtn();
    }

    private void mainRtn() {
        // 005400 0000-MAIN-RTN.
        // 005500     READ FD-CLNDR INVALID KEY
        // 005600          MOVE  "000000"         TO     WK-TASK-NBSDATE
        // 005700          GO TO 0000-MAIN-EXIT.
        // 005800     IF      FD-CLNDR-TBSDY      NOT =  WK-YYMMDD8                99/12/29
        // 005900      ADD    1                   TO     WK-CLNDR-KEY
        // 006000      GO TO  0000-MAIN-RTN
        // 006100     ELSE
        // 006200      MOVE   FD-CLNDR-WEEKDY     TO     WK-TASK-WEK1.
        // 006300      MOVE   WK-TASK-WEK1        TO     WK-TASK-WEK.
        wkTaskWek = String.valueOf(getWeekdy(wkYymmdd));
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTaskWek={}", wkTaskWek);

        // 006400     COMPUTE WK-YY =  FD-CLNDR-TBSYY  +   11.
        // 006500     MOVE    WK-YY               TO     WK-CLNDR-TBSYY.
        // 006600     MOVE    FD-CLNDR-TBSMM     TO      WK-CLNDR-TBSMM.
        // 006700     MOVE    FD-CLNDR-TBSDD     TO      WK-CLNDR-TBSDD.

        // ref:
        // 003000 01 WK-STDATE.
        // 003100    03  WK-CLNDR-TBSMM                 PIC X(02).
        // 003200    03  FILLER                         PIC X(01) VALUE "/".
        // 003300    03  WK-CLNDR-TBSDD                 PIC X(02).
        // 003400    03  FILLER                         PIC X(01) VALUE "/".
        // 003500    03  WK-CLNDR-TBSYY                 PIC X(02).

        // ref:
        // 002600 01 WK-YY                              PIC 9(02).

        int wkYy = wkYymmdd / 10000 % 100;
        int wkMm = wkYymmdd / 100 % 100;
        int wkDd = wkYymmdd % 100;
        String wkClndrTbsyy = parse.decimal2String(wkYy + 11, 2, 0);
        String wkClndrTbsmm = parse.decimal2String(wkMm, 2, 0);
        String wkClndrTbsdd = parse.decimal2String(wkDd, 2, 0);

        // 006800     MOVE    WK-STDATE          TO      WK-TASK-NBSDATE.
        wkTaskNbsdate = wkClndrTbsmm + "/" + wkClndrTbsdd + "/" + wkClndrTbsyy;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTaskNbsdate={}", wkTaskNbsdate);

        // 006900 0000-MAIN-EXIT.
        // 007000     EXIT.
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }
}
