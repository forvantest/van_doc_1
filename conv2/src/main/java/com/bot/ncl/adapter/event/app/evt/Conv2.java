/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV2__I;
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
@Component("CONV2")
@Scope("prototype")
public class Conv2 extends TradeEventCase<RequestSvcCase> {

    private CONV2__I conv2__I;

    public Conv2(CONV2__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv2__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
