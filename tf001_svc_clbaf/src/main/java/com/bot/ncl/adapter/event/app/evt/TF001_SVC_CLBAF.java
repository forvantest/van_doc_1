/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF001_SVC_CLBAF_I;
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
@Component("TF001_SVC_CLBAF")
@Scope("prototype")
public class TF001_SVC_CLBAF extends TradeEventCase<RequestSvcCase> {

    private TF001_SVC_CLBAF_I tf001SvcClbaf_I;

    public TF001_SVC_CLBAF(TF001_SVC_CLBAF_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf001SvcClbaf_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
