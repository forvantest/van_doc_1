/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV366ZA9_I;
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
@Component("CONV366ZA9")
@Scope("prototype")
public class CONV366ZA9 extends TradeEventCase<RequestSvcCase> {

    private CONV366ZA9_I conv366za9_I;

    public CONV366ZA9(CONV366ZA9_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv366za9_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
