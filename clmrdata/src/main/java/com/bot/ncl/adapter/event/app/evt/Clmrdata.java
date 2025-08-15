/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CLMRDATA_00000_I;
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
@Component("CLMRDATA")
@Scope("prototype")
public class Clmrdata extends TradeEventCase<RequestSvcCase> {

    private CLMRDATA_00000_I clmrdata_00000_I;

    public Clmrdata(CLMRDATA_00000_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.clmrdata_00000_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
