/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF001_SVC_CLDTL_I;
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
@Component("TF001_SVC_CLDTL")
@Scope("prototype")
public class TF001_SVC_CLDTL extends TradeEventCase<RequestSvcCase> {

    private TF001_SVC_CLDTL_I tf001SvcCldtl_I;

    public TF001_SVC_CLDTL(TF001_SVC_CLDTL_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf001SvcCldtl_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
