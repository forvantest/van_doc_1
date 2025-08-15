/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV361689__I;
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
@Component("CONV361689")
@Scope("prototype")
public class Conv361689 extends TradeEventCase<RequestSvcCase> {

    private CONV361689__I conv361689__I;

    public Conv361689(CONV361689__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv361689__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
