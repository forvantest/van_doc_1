/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C076__I;
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
@Component("C076")
@Scope("prototype")
public class C076 extends TradeEventCase<RequestSvcCase> {

    private C076__I c076_I;

    public C076(C076__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c076_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
