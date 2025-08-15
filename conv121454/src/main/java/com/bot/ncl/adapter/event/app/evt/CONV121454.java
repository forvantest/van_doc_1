/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV121454_I;
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
@Component("CONV121454")
@Scope("prototype")
public class CONV121454 extends TradeEventCase<RequestSvcCase> {

    private CONV121454_I conv121454_I;

    public CONV121454(CONV121454_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv121454_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
