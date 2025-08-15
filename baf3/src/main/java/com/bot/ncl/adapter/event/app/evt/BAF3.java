/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.BAF3__I;
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
@Component("BAF3")
@Scope("prototype")
public class BAF3 extends TradeEventCase<RequestSvcCase> {

    private BAF3__I baf3__I;

    public BAF3(BAF3__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.baf3__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
