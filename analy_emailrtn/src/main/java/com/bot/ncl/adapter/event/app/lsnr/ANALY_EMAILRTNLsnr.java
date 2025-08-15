/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.ANALY_EMAILRTN;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ANALY_EMAILRTNLsnr")
@Scope("prototype")
public class ANALY_EMAILRTNLsnr extends BatchListenerCase<ANALY_EMAILRTN> {

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(ANALY_EMAILRTN event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ANALY_EMAILRTNLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(ANALY_EMAILRTN event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ANALY_EMAILRTNLsnr run()");
    }
}
