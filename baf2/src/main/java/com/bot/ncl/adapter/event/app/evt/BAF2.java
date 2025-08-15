/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.BAF2__I;
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
@Component("BAF2")
@Scope("prototype")
public class BAF2 extends TradeEventCase<RequestSvcCase> {

    private BAF2__I baf2_I;

    public BAF2(BAF2__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.baf2_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
