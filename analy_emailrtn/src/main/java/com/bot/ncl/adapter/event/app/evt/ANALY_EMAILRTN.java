/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.ANALY_EMAILRTN_I;
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
@Component("ANALY_EMAILRTN")
@Scope("prototype")
public class ANALY_EMAILRTN extends TradeEventCase<RequestSvcCase> {

    private ANALY_EMAILRTN_I analyEmailrtn_I;

    public ANALY_EMAILRTN(ANALY_EMAILRTN_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.analyEmailrtn_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
