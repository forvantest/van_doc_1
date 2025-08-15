/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C063__I;
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
@Component("C063")
@Scope("prototype")
public class C063 extends TradeEventCase<RequestSvcCase> {

    private C063__I c063__I;

    public C063(C063__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c063__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
