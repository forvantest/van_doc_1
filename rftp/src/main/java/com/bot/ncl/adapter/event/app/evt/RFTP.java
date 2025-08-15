/* (C) 2025 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.RFTP_I;
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
@Component("RFTP")
@Scope("prototype")
public class RFTP extends TradeEventCase<RequestSvcCase> {

    private RFTP_I rftp_I;

    public RFTP(RFTP_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.rftp_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
