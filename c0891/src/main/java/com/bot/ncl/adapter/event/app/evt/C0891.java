/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C0891_I;
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
@Component("C0891")
@Scope("prototype")
public class C0891 extends TradeEventCase<RequestSvcCase> {

    private C0891_I c0891__I;

    public C0891(C0891_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c0891__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
