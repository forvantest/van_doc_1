/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C004_RPT_I;
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
@Component("C004_RPT")
@Scope("prototype")
public class C004_RPT extends TradeEventCase<RequestSvcCase> {

    private C004_RPT_I c004Rpt_I;

    public C004_RPT(C004_RPT_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c004Rpt_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
