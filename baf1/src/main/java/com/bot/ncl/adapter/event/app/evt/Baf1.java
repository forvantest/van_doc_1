/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.BAF1__I;
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
@Component("BAF1")
@Scope("prototype")
public class Baf1 extends TradeEventCase<RequestSvcCase> {

    private BAF1__I baf1__I;

    public Baf1(BAF1__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.baf1__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
