/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF001_SVC_I;
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
@Component("TF001_SVC")
@Scope("prototype")
public class TF001_SVC extends TradeEventCase<RequestSvcCase> {

    private TF001_SVC_I tf001Svc_I;

    public TF001_SVC(TF001_SVC_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf001Svc_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
