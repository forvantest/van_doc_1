/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV25_I;
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
@Component("CONV25")
@Scope("prototype")
public class CONV25 extends TradeEventCase<RequestSvcCase> {

    private CONV25_I conv25_I;

    public CONV25(CONV25_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv25_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
