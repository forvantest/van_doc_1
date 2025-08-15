/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTCONVAC__I;
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
@Component("OUTCONVAC")
@Scope("prototype")
public class OUTCONVAC extends TradeEventCase<RequestSvcCase> {

    private OUTCONVAC__I outconvac__I;

    public OUTCONVAC(OUTCONVAC__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outconvac__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
