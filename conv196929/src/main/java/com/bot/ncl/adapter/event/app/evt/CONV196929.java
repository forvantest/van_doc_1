/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV196929_I;
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
@Component("CONV196929")
@Scope("prototype")
public class CONV196929 extends TradeEventCase<RequestSvcCase> {

    private CONV196929_I conv196929_I;

    public CONV196929(CONV196929_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv196929_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
