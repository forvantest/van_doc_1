/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTCONVBAF_I;
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
@Component("OUTCONVBAF")
@Scope("prototype")
public class OUTCONVBAF extends TradeEventCase<RequestSvcCase> {

    private OUTCONVBAF_I outconvbaf_I;

    public OUTCONVBAF(OUTCONVBAF_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outconvbaf_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
