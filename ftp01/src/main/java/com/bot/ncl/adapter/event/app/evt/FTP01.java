/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.FTP01_I;
import com.bot.txcontrol.adapter.RequestSvcCase;
import com.bot.txcontrol.adapter.event.TradeEventCase;
import com.bot.txcontrol.buffer.AggregateBuffer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Slf4j
@Component("FTP01")
@Scope("prototype")
public class FTP01 extends TradeEventCase<RequestSvcCase> {

    private FTP01_I ftp01_I;

    public FTP01(FTP01_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.ftp01_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
