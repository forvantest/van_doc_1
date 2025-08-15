/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.Chkclmrflg;
import com.bot.ncl.adapter.event.app.scheduled.ChkClmrTask;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.scheduled.ScheduledFutureHolder;
import com.bot.txcontrol.scheduled.ScheduledFutureHolderPack;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ChkclmrflgLsnr")
@Scope("prototype")
public class ChkclmrflgLsnr extends BatchListenerCase<Chkclmrflg> {

    @Autowired private ClmrService clmrService;

    @Autowired private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Autowired private ChkClmrTask chkClmrTask;

    @Autowired private ScheduledFutureHolderPack scheduledFutureHolderPack;

    private ClmrBus clmrBus;

    // @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Chkclmrflg event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChkclmrflgLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Chkclmrflg event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChkclmrflgLsnr run()");
        chkClmrTask.setEvent(event);
        // 每900秒 執行一次
        ScheduledFuture<?> schedule =
                threadPoolTaskScheduler.schedule(
                        chkClmrTask, new PeriodicTrigger(900, TimeUnit.SECONDS));
        ScheduledFutureHolder scheduledFutureHolder = new ScheduledFutureHolder();
        scheduledFutureHolder.setScheduledFuture(schedule);
        scheduledFutureHolder.setRunnableClass(chkClmrTask.getClass());
        scheduledFutureHolderPack
                .getScheduleMap()
                .put(scheduledFutureHolder.getRunnableClass().getName(), scheduledFutureHolder);

        // TODO: 待確認BATCH參數名稱

        //        mainRoutine(event);
    }

    private void mainRoutine(Chkclmrflg event) {
        String wkCode = "160054";
        int taskValue = findInDatabase(wkCode);

        if (taskValue == 2) {
            return;
        }

        int wkTime4 = getCurrentTime();

        if (wkTime4 > 1929) {
            taskValue = 2;
        } else if (!isAfCbvEqual1()) {
            taskValue = 1;
        } else {
            taskValue = 0;
        }

        if (taskValue == 1) {
            try {
                Thread.sleep(900);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            mainRoutine(event);
        } else {
            event.getPeripheryRequest().getHeadersMap().put("TASKVALUE", String.valueOf(taskValue));
        }
    }

    private int findInDatabase(String code) {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Find in database with code: " + code);
        clmrBus = null;
        try {
            clmrBus = clmrService.findById(code);
        } catch (Exception e) {
            return 2;
        }
        return !Objects.isNull(clmrBus) ? 0 : 2;
    }

    private int getCurrentTime() {
        LocalTime now = LocalTime.now();
        return now.getHour() * 100 + now.getMinute();
    }

    private boolean isAfCbvEqual1() {
        return clmrBus != null && clmrBus.getAfcbv() == 1;
    }
}
