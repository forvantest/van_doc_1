/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTUPLRPT_I;
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
@Component("OUTUPLRPT")
@Scope("prototype")
public class OUTUPLRPT extends TradeEventCase<RequestSvcCase> {

    private OUTUPLRPT_I outuplrpt_I;

    public OUTUPLRPT(OUTUPLRPT_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outuplrpt_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
