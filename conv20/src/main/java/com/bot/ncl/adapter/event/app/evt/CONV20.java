/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV20_I;
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
@Component("CONV20")
@Scope("prototype")
public class CONV20 extends TradeEventCase<RequestSvcCase> {

    private CONV20_I conv20_I;

    public CONV20(CONV20_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv20_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
