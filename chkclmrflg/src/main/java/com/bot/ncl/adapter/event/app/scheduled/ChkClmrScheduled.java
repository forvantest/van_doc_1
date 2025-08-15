/* (C) 2024 */
package com.bot.ncl.adapter.event.app.scheduled;

import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.scheduled.ScheduledFutureHolderPack;
import com.bot.txcontrol.util.dump.ExceptionDump;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChkClmrScheduled {

    @Autowired private ScheduledFutureHolderPack scheduledFutureHolderPack;

    // 每天七點半停止ChkClmrTask
    @Scheduled(cron = "0 30 19 * * ?")
    public void stopChkClmr() {
        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "stopChkClmr.");
        try {
            ScheduledFuture<?> scheduledFuture =
                    scheduledFutureHolderPack
                            .getScheduleMap()
                            .get(ChkClmrTask.class.getName())
                            .getScheduledFuture();
            if (!Objects.isNull(scheduledFuture)) scheduledFuture.cancel(true);
        } catch (Exception e) {
            ApLogHelper.warn(
                    log, false, LogType.APLOG.getCode(), ExceptionDump.exception2String(e));
        }
    }
}
