/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV12__I;
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
@Component("CONV12")
@Scope("prototype")
public class CONV12 extends TradeEventCase<RequestSvcCase> {

    private CONV12__I conv12_I;

    public CONV12(CONV12__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv12_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
