/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.OUTLIQRPT__I;
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
@Component("OUTLIQRPT")
@Scope("prototype")
public class OUTLIQRPT extends TradeEventCase<RequestSvcCase> {

    private OUTLIQRPT__I outliqrpt__I;

    public OUTLIQRPT(OUTLIQRPT__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.outliqrpt__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
