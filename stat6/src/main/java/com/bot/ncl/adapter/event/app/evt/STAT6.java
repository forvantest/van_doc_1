/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT6_I;
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
@Component("STAT6")
@Scope("prototype")
public class STAT6 extends TradeEventCase<RequestSvcCase> {

    private STAT6_I stat6_I;

    public STAT6(STAT6_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat6_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
