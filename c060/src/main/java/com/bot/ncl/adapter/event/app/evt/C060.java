/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C060__I;
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
@Component("C060")
@Scope("prototype")
public class C060 extends TradeEventCase<RequestSvcCase> {

    private C060__I c060__I;

    public C060(C060__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c060__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
