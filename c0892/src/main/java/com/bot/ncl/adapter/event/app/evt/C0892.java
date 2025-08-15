/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C0892_I;
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
@Component("C0892")
@Scope("prototype")
public class C0892 extends TradeEventCase<RequestSvcCase> {

    private C0892_I c0892__I;

    public C0892(C0892_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c0892__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
