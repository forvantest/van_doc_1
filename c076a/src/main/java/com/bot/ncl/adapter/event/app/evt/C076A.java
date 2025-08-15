/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C076A__I;
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
@Component("C076A")
@Scope("prototype")
public class C076A extends TradeEventCase<RequestSvcCase> {

    private C076A__I c076A_I;

    public C076A(C076A__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c076A_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
