/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV530004_I;
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
@Component("CONV530004")
@Scope("prototype")
public class CONV530004 extends TradeEventCase<RequestSvcCase> {

    private CONV530004_I conv530004_I;

    public CONV530004(CONV530004_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv530004_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
