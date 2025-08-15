/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.UPUTH_I;
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
@Component("UPUTH")
@Scope("prototype")
public class Uputh extends TradeEventCase<RequestSvcCase> {

    private UPUTH_I uputh_I;

    public Uputh(UPUTH_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.uputh_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
