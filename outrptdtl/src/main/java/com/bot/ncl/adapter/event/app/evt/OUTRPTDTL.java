/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTRPTDTL__I;
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
@Component("OUTRPTDTL")
@Scope("prototype")
public class OUTRPTDTL extends TradeEventCase<RequestSvcCase> {

    private OUTRPTDTL__I outrptdtl__I;

    public OUTRPTDTL(OUTRPTDTL__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outrptdtl__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
