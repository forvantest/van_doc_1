/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CREC0121_I;
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
@Component("CREC0121")
@Scope("prototype")
public class Crec0121 extends TradeEventCase<RequestSvcCase> {

    private CREC0121_I crec0121_I;

    public Crec0121(CREC0121_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.crec0121_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
