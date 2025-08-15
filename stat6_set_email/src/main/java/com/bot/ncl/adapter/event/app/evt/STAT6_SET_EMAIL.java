/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.STAT6_SET_EMAIL_I;
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
@Component("STAT6_SET_EMAIL")
@Scope("prototype")
public class STAT6_SET_EMAIL extends TradeEventCase<RequestSvcCase> {

    private STAT6_SET_EMAIL_I stat6SetEmail_I;

    public STAT6_SET_EMAIL(STAT6_SET_EMAIL_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.stat6SetEmail_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
