/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV21_I;
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
@Component("CONV21")
@Scope("prototype")
public class CONV21 extends TradeEventCase<RequestSvcCase> {

    private CONV21_I conv21_I;

    public CONV21(CONV21_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv21_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
