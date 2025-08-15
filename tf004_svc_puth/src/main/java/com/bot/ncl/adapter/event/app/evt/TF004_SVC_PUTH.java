/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF004_SVC_PUTH_I;
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
@Component("TF004_SVC_PUTH")
@Scope("prototype")
public class TF004_SVC_PUTH extends TradeEventCase<RequestSvcCase> {

    private TF004_SVC_PUTH_I tf004SvcPuth_I;

    public TF004_SVC_PUTH(TF004_SVC_PUTH_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf004SvcPuth_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
