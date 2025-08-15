/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV360019_I;
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
@Component("CONV360019")
@Scope("prototype")
public class CONV360019 extends TradeEventCase<RequestSvcCase> {

    private CONV360019_I conv360019_I;

    public CONV360019(CONV360019_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv360019_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
