/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV121444_I;
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
@Component("CONV121444")
@Scope("prototype")
public class CONV121444 extends TradeEventCase<RequestSvcCase> {

    private CONV121444_I conv121444_I;

    public CONV121444(CONV121444_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv121444_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
