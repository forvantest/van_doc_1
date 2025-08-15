/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT4_I;
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
@Component("STAT4")
@Scope("prototype")
public class STAT4 extends TradeEventCase<RequestSvcCase> {

    private STAT4_I stat4__I;

    public STAT4(STAT4_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat4__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
