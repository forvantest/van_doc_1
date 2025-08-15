/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.EX_KPUTH1_I;
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
@Component("EX_KPUTH1")
@Scope("prototype")
public class ExKputh1 extends TradeEventCase<RequestSvcCase> {

    private EX_KPUTH1_I exKputh1_I;

    public ExKputh1(EX_KPUTH1_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.exKputh1_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
