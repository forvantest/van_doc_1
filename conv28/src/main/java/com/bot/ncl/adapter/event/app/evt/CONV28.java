/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV28_I;
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
@Component("CONV28")
@Scope("prototype")
public class CONV28 extends TradeEventCase<RequestSvcCase> {

    private CONV28_I conv28_I;

    public CONV28(CONV28_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv28_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
