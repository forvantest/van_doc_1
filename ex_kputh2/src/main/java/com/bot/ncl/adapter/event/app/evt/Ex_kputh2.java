/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.EX_KPUTH2_I;
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
@Component("EX_KPUTH2")
@Scope("prototype")
public class Ex_kputh2 extends TradeEventCase<RequestSvcCase> {

    private EX_KPUTH2_I exKputh2_I;

    public Ex_kputh2(EX_KPUTH2_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.exKputh2_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
