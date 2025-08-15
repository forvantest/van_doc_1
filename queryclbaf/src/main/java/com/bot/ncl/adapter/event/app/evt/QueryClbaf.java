/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.QUERYCLBAF_I;
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
@Component("QUERYCLBAF")
@Scope("prototype")
public class QueryClbaf extends TradeEventCase<RequestSvcCase> {

    private QUERYCLBAF_I queryclbaf_I;

    public QueryClbaf(QUERYCLBAF_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.queryclbaf_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
