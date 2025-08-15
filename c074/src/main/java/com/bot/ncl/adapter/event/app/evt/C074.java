/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C074__I;
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
@Component("C074")
@Scope("prototype")
public class C074 extends TradeEventCase<RequestSvcCase> {

    private C074__I c074_I;

    public C074(C074__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c074_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
