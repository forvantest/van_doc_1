/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CLDTLDWL_I;
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
@Component("CLDTLDWL")
@Scope("prototype")
public class Cldtldwl extends TradeEventCase<RequestSvcCase> {

    private CLDTLDWL_I cldtldwl_I;

    public Cldtldwl(CLDTLDWL_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.cldtldwl_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
