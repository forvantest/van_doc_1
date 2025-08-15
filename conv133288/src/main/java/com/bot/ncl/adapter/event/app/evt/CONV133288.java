/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV133288_I;
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
@Component("CONV133288")
@Scope("prototype")
public class CONV133288 extends TradeEventCase<RequestSvcCase> {

    private CONV133288_I conv133288_I;

    public CONV133288(CONV133288_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv133288_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
