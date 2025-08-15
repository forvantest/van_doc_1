/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C079__I;
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
@Component("C079")
@Scope("prototype")
public class C079 extends TradeEventCase<RequestSvcCase> {

    private C079__I c079__I;

    public C079(C079__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c079__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
