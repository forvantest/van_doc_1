/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV7__I;
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
@Component("CONV7")
@Scope("prototype")
public class Conv7 extends TradeEventCase<RequestSvcCase> {

    private CONV7__I conv7__I;

    public Conv7(CONV7__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv7__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
