/* (C) 2025 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CVRPT01_I;
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
@Component("CVRPT01")
@Scope("prototype")
public class Cvrpt01 extends TradeEventCase<RequestSvcCase> {

    private CVRPT01_I cvrpt01_I;

    public Cvrpt01(CVRPT01_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.cvrpt01_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
