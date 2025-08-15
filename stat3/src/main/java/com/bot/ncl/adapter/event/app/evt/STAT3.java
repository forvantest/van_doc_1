/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT3_I;
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
@Component("STAT3")
@Scope("prototype")
public class STAT3 extends TradeEventCase<RequestSvcCase> {

    private STAT3_I stat3__I;

    public STAT3(STAT3_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat3__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
