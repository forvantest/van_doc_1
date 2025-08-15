/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CLM01_I;
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
@Component("CLM01")
@Scope("prototype")
public class Clm01 extends TradeEventCase<RequestSvcCase> {

    private CLM01_I clm01_I;

    public Clm01(CLM01_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.clm01_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
