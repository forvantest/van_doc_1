/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C0761__I;
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
@Component("C0761")
@Scope("prototype")
public class C0761 extends TradeEventCase<RequestSvcCase> {

    private C0761__I c0761_I;

    public C0761(C0761__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c0761_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
