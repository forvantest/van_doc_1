/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C088_I;
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
@Component("C088")
@Scope("prototype")
public class C088 extends TradeEventCase<RequestSvcCase> {

    private C088_I c088__I;

    public C088(C088_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c088__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
