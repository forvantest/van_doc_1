/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.HTAXEXDTL_I;
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
@Component("HTAXEXDTL")
@Scope("prototype")
public class Htaxexdtl extends TradeEventCase<RequestSvcCase> {

    private HTAXEXDTL_I htaxexdtl_I;

    public Htaxexdtl(HTAXEXDTL_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.htaxexdtl_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
