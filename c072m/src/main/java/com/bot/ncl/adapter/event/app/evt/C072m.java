/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.C072m_I;
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
@Component("C072m")
@Scope("prototype")
public class C072m extends TradeEventCase<RequestSvcCase> {

    private C072m_I c072m__I;

    public C072m(C072m_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.c072m__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
