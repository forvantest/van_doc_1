/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV302007_I;
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
@Component("CONV302007")
@Scope("prototype")
public class CONV302007 extends TradeEventCase<RequestSvcCase> {

    private CONV302007_I conv302007_I;

    public CONV302007(CONV302007_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv302007_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
