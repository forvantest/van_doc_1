/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C064__I;
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
@Component("C064")
@Scope("prototype")
public class C064 extends TradeEventCase<RequestSvcCase> {

    private C064__I c064__I;

    public C064(C064__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c064__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
