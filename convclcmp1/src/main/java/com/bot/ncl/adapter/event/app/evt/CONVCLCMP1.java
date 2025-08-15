/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONVCLCMP1__I;
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
@Component("CONVCLCMP1")
@Scope("prototype")
public class CONVCLCMP1 extends TradeEventCase<RequestSvcCase> {

    private CONVCLCMP1__I convclcmp1__I;

    public CONVCLCMP1(CONVCLCMP1__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.convclcmp1__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
