/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV19__I;
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
@Component("CONV19")
@Scope("prototype")
public class Conv19 extends TradeEventCase<RequestSvcCase> {

    private CONV19__I conv19__I;

    public Conv19(CONV19__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv19__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
