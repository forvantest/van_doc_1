/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.STAT6_SET_EMAIL;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("STAT6_SET_EMAILLsnr")
@Scope("prototype")
public class STAT6_SET_EMAILLsnr extends BatchListenerCase<STAT6_SET_EMAIL> {

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT6_SET_EMAIL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT6_SET_EMAILLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT6_SET_EMAIL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT6_SET_EMAILLsnr run()");
    }
}
