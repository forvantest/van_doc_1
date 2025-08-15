/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CRE_CLDMR_I;
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
@Component("CRE_CLDMR")
@Scope("prototype")
public class CRE_CLDMR extends TradeEventCase<RequestSvcCase> {

    private CRE_CLDMR_I creCldmr_I;

    public CRE_CLDMR(CRE_CLDMR_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.creCldmr_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
