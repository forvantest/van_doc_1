/* (C) 2025 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.PRTMSG_I;
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
@Component("PRTMSG")
@Scope("prototype")
public class PRTMSG extends TradeEventCase<RequestSvcCase> {

    private PRTMSG_I prtmsg_I;

    public PRTMSG(PRTMSG_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.prtmsg_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
