/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONVTAX__I;
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
@Component("CONVTAX")
@Scope("prototype")
public class Convtax extends TradeEventCase<RequestSvcCase> {

    private CONVTAX__I convtax__I;

    public Convtax(CONVTAX__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.convtax__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
