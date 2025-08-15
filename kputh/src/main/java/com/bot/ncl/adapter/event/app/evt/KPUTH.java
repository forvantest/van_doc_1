/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.KPUTH_I;
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
@Component("KPUTH")
@Scope("prototype")
public class KPUTH extends TradeEventCase<RequestSvcCase> {

    private KPUTH_I kputh_I;

    public KPUTH(KPUTH_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.kputh_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
