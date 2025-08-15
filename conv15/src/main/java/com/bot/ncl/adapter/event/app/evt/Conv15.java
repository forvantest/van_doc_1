/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV15__I;
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
@Component("CONV15")
@Scope("prototype")
public class Conv15 extends TradeEventCase<RequestSvcCase> {

    private CONV15__I conv15__I;

    public Conv15(CONV15__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv15__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
