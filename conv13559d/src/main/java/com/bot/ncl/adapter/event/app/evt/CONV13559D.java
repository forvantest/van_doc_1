/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV13559D_I;
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
@Component("CONV13559D")
@Scope("prototype")
public class CONV13559D extends TradeEventCase<RequestSvcCase> {

    private CONV13559D_I conv13559d_I;

    public CONV13559D(CONV13559D_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv13559d_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
