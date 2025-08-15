/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT7_I;
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
@Component("STAT7")
@Scope("prototype")
public class STAT7 extends TradeEventCase<RequestSvcCase> {

    private STAT7_I stat7__I;

    public STAT7(STAT7_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat7__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
