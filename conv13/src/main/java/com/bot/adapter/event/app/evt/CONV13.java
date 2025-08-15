/* (C) 2024 */
package com.bot.adapter.event.app.evt;

import com.bot.adapter.in.svc.CONV13__I;
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
@Component("CONV13")
@Scope("prototype")
public class CONV13 extends TradeEventCase<RequestSvcCase> {

    private CONV13__I conv13__I;

    public CONV13(CONV13__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv13__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
