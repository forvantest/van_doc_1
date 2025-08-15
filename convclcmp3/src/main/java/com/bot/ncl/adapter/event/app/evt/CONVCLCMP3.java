/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.CONVCLCMP3__I;
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
@Component("CONVCLCMP3")
@Scope("prototype")
public class CONVCLCMP3 extends TradeEventCase<RequestSvcCase> {

    private CONVCLCMP3__I convclcmp3__I;

    public CONVCLCMP3(CONVCLCMP3__I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.convclcmp3__I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
