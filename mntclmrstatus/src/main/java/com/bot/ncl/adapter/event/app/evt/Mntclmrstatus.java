/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.MNTCLMRSTATUS_I;
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
@Component("MNTCLMRSTATUS")
@Scope("prototype")
public class Mntclmrstatus extends TradeEventCase<RequestSvcCase> {

    private MNTCLMRSTATUS_I mntclmrstatus_I;

    public Mntclmrstatus(MNTCLMRSTATUS_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.mntclmrstatus_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
