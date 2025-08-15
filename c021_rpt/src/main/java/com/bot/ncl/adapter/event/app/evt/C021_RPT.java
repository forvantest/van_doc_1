/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C021_RPT_I;
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
@Component("C021_RPT")
@Scope("prototype")
public class C021_RPT extends TradeEventCase<RequestSvcCase> {

    private C021_RPT_I c021Rpt_I;

    public C021_RPT(C021_RPT_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c021Rpt_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
