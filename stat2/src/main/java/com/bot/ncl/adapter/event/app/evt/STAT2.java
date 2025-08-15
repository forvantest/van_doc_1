/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT2_I;
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
@Component("STAT2")
@Scope("prototype")
public class STAT2 extends TradeEventCase<RequestSvcCase> {

    private STAT2_I stat2__I;

    public STAT2(STAT2_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat2__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
