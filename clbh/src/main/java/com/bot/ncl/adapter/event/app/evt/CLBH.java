/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CLBH_I;
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
@Component("CLBH")
@Scope("prototype")
public class CLBH extends TradeEventCase<RequestSvcCase> {

    private CLBH_I clbh_I;

    public CLBH(CLBH_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.clbh_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
