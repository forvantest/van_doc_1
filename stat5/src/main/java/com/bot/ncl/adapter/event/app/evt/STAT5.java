/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT5_I;
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
@Component("STAT5")
@Scope("prototype")
public class STAT5 extends TradeEventCase<RequestSvcCase> {

    private STAT5_I stat5__I;

    public STAT5(STAT5_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat5__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
