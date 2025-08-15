/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C060M_I;
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
@Component("C060M")
@Scope("prototype")
public class C060M extends TradeEventCase<RequestSvcCase> {

    private C060M_I c060m__I;

    public C060M(C060M_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c060m__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
