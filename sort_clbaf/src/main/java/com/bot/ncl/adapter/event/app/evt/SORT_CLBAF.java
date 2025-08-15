/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.SORT_CLBAF_I;
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
@Component("SORT_CLBAF")
@Scope("prototype")
public class SORT_CLBAF extends TradeEventCase<RequestSvcCase> {

    private SORT_CLBAF_I sortClbaf_I;

    public SORT_CLBAF(SORT_CLBAF_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.sortClbaf_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
