/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONVF__I;
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
@Component("CONVF")
@Scope("prototype")
public class CONVF extends TradeEventCase<RequestSvcCase> {

    private CONVF__I convf__I;

    public CONVF(CONVF__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.convf__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
