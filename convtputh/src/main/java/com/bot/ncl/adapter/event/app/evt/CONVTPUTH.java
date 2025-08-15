/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONVTPUTH_I;
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
@Component("CONVTPUTH")
@Scope("prototype")
public class CONVTPUTH extends TradeEventCase<RequestSvcCase> {

    private CONVTPUTH_I convtputh_I;

    public CONVTPUTH(CONVTPUTH_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.convtputh_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
