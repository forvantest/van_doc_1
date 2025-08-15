/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.TXTEST_SVC_I;
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
@Component("TXTEST_SVC")
@Scope("prototype")
public class Txtest_svc extends TradeEventCase<RequestSvcCase> {

    private TXTEST_SVC_I txtestSvc_I;

    public Txtest_svc(TXTEST_SVC_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.txtestSvc_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
