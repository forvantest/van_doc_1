/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV11__I;
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
@Component("CONV11")
@Scope("prototype")
public class CONV11 extends TradeEventCase<RequestSvcCase> {

    private CONV11__I conv11__I;

    public CONV11(CONV11__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv11__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
