/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CRE_CLMR_I;
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
@Component("CRE_CLMR")
@Scope("prototype")
public class CRE_CLMR extends TradeEventCase<RequestSvcCase> {

    private CRE_CLMR_I creClmr_I;

    public CRE_CLMR(CRE_CLMR_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.creClmr_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
