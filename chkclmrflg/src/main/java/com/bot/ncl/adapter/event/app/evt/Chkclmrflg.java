/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CHKCLMRFLG_I;
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
@Component("CHKCLMRFLG")
@Scope("prototype")
public class Chkclmrflg extends TradeEventCase<RequestSvcCase> {

    private CHKCLMRFLG_I chkclmrflg_I;

    public Chkclmrflg(CHKCLMRFLG_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.chkclmrflg_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
