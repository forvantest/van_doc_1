/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TF000_SVC_I;
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
@Component("TF000_SVC")
@Scope("prototype")
public class TF000_SVC extends TradeEventCase<RequestSvcCase> {

    private TF000_SVC_I tf000Svc_I;

    public TF000_SVC(TF000_SVC_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.tf000Svc_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
