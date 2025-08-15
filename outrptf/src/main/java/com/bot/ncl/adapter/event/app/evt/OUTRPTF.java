/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTRPTF_I;
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
@Component("OUTRPTF")
@Scope("prototype")
public class OUTRPTF extends TradeEventCase<RequestSvcCase> {

    private OUTRPTF_I outrptf_I;

    public OUTRPTF(OUTRPTF_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outrptf_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
