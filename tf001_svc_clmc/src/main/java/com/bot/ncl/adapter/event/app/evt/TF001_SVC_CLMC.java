/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF001_SVC_CLMC_I;
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
@Component("TF001_SVC_CLMC")
@Scope("prototype")
public class TF001_SVC_CLMC extends TradeEventCase<RequestSvcCase> {

    private TF001_SVC_CLMC_I tf001SvcClmc_I;

    public TF001_SVC_CLMC(TF001_SVC_CLMC_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf001SvcClmc_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
