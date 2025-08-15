/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C069_I;
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
@Component("C069")
@Scope("prototype")
public class C069 extends TradeEventCase<RequestSvcCase> {

    private C069_I c069_I;

    public C069(C069_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c069_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
