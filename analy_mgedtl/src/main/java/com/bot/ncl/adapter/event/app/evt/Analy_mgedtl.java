/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.ANALY_MGEDTL_I;
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
@Component("ANALY_MGEDTL")
@Scope("prototype")
public class Analy_mgedtl extends TradeEventCase<RequestSvcCase> {

    private ANALY_MGEDTL_I analyMgedtl_I;

    public Analy_mgedtl(ANALY_MGEDTL_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.analyMgedtl_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
