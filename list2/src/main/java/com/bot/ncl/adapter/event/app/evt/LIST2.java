/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.LIST2__I;
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
@Component("LIST2")
@Scope("prototype")
public class LIST2 extends TradeEventCase<RequestSvcCase> {

    private LIST2__I list2__I;

    public LIST2(LIST2__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.list2__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
