/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C065__I;
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
@Component("C065")
@Scope("prototype")
public class C065 extends TradeEventCase<RequestSvcCase> {

    private C065__I c065__I;

    public C065(C065__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c065__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
