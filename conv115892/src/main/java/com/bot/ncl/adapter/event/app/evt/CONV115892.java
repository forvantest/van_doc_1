/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV115892_I;
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
@Component("CONV115892")
@Scope("prototype")
public class CONV115892 extends TradeEventCase<RequestSvcCase> {

    private CONV115892_I conv115892_I;

    public CONV115892(CONV115892_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv115892_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
