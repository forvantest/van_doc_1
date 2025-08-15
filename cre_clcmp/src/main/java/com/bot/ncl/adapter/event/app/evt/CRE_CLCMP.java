/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CRE_CLCMP_I;
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
@Component("CRE_CLCMP")
@Scope("prototype")
public class CRE_CLCMP extends TradeEventCase<RequestSvcCase> {

    private CRE_CLCMP_I creClcmp_I;

    public CRE_CLCMP(CRE_CLCMP_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.creClcmp_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
