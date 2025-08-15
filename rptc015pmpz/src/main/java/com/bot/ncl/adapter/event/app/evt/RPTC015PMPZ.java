/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.RPTC015PMPZ_I;
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
@Component("RPTC015PMPZ")
@Scope("prototype")
public class RPTC015PMPZ extends TradeEventCase<RequestSvcCase> {

    private RPTC015PMPZ_I rptc015pmpz_I;

    public RPTC015PMPZ(RPTC015PMPZ_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.rptc015pmpz_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
