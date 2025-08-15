/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV366AE9_I;
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
@Component("CONV366AE9")
@Scope("prototype")
public class CONV366AE9 extends TradeEventCase<RequestSvcCase> {

    private CONV366AE9_I conv366ae9_I;

    public CONV366AE9(CONV366AE9_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv366ae9_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
