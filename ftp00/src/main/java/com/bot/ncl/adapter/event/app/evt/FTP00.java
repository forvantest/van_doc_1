/* (C) 2024 */
package com.bot.ncl.adapter.event.app.evt;

import com.bot.ncl.adapter.in.svc.FTP00_I;
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
@Component("FTP00")
@Scope("prototype")
public class FTP00 extends TradeEventCase<RequestSvcCase> {

    private FTP00_I ftp00_I;

    public FTP00(FTP00_I source, AggregateBuffer aggregateBuffer) {
        super(source, aggregateBuffer);
        this.ftp00_I = source;
        this.setAggregateBuffer(aggregateBuffer);
    }
}
