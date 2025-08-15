/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.PRETRUST_I;
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
@Component("PRETRUST")
@Scope("prototype")
public class Pretrust extends TradeEventCase<RequestSvcCase> {

    private PRETRUST_I pretrust_I;

    public Pretrust(PRETRUST_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.pretrust_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
