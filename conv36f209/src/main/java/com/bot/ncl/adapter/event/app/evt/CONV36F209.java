/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV36F209_I;
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
@Component("CONV36F209")
@Scope("prototype")
public class CONV36F209 extends TradeEventCase<RequestSvcCase> {

    private CONV36F209_I conv36f209_I;

    public CONV36F209(CONV36F209_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv36f209_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
