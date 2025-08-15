/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C077_I;
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
@Component("C077")
@Scope("prototype")
public class C077 extends TradeEventCase<RequestSvcCase> {

    private C077_I c077_I;

    public C077(C077_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c077_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
