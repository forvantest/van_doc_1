/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C087_I;
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
@Component("C087")
@Scope("prototype")
public class C087 extends TradeEventCase<RequestSvcCase> {

    private C087_I c087__I;

    public C087(C087_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c087__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
