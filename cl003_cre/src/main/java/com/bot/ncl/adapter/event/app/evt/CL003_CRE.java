/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CL003_CRE_I;
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
@Component("CL003_CRE")
@Scope("prototype")
public class CL003_CRE extends TradeEventCase<RequestSvcCase> {

    private CL003_CRE_I cl003Cre_I;

    public CL003_CRE(CL003_CRE_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.cl003Cre_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
