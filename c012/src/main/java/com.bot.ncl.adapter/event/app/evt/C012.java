/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C012__I;
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
@Component("C012")
@Scope("prototype")
public class C012 extends TradeEventCase<RequestSvcCase> {

    private C012__I C012__I;

    public C012(C012__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.C012__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
