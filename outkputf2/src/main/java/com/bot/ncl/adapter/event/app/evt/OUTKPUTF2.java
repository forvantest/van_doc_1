/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTKPUTF2__I;
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
@Component("OUTKPUTF2")
@Scope("prototype")
public class OUTKPUTF2 extends TradeEventCase<RequestSvcCase> {

    private OUTKPUTF2__I outkputf2__I;

    public OUTKPUTF2(OUTKPUTF2__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outkputf2__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
