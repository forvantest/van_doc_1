/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONV36C179_I;
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
@Component("CONV36C179")
@Scope("prototype")
public class CONV36C179 extends TradeEventCase<RequestSvcCase> {

    private CONV36C179_I conv36c179_I;

    public CONV36C179(CONV36C179_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.conv36c179_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
