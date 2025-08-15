/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV145952_I;
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
@Component("CONV145952")
@Scope("prototype")
public class CONV145952 extends TradeEventCase<RequestSvcCase> {

    private CONV145952_I conv145952_I;

    public CONV145952(CONV145952_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv145952_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
