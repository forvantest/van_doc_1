/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.UPUTFN_I;
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
@Component("UPUTFN")
@Scope("prototype")
public class Uputfn extends TradeEventCase<RequestSvcCase> {

    private UPUTFN_I uputfn_I;

    public Uputfn(UPUTFN_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.uputfn_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
