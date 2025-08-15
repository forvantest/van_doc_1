/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CREC012__I;
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
@Component("CREC012")
@Scope("prototype")
public class Crec012 extends TradeEventCase<RequestSvcCase> {

    private CREC012__I crec012_I;

    public Crec012(CREC012__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.crec012_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
