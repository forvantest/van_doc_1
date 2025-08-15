/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV153894_I;
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
@Component("CONV153894")
@Scope("prototype")
public class CONV153894 extends TradeEventCase<RequestSvcCase> {

    private CONV153894_I conv153894_I;

    public CONV153894(CONV153894_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv153894_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
