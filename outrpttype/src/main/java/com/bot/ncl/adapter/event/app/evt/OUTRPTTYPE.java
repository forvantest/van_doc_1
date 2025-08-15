/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTRPTTYPE_I;
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
@Component("OUTRPTTYPE")
@Scope("prototype")
public class OUTRPTTYPE extends TradeEventCase<RequestSvcCase> {

    private OUTRPTTYPE_I outrpttype_I;

    public OUTRPTTYPE(OUTRPTTYPE_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outrpttype_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
