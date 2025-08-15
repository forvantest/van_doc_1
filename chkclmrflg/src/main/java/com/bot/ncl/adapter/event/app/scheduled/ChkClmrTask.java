/* (C) 2024 */
package com.bot.ncl.adapter.event.app.scheduled;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.Format;
import com.bot.fsap.model.grpc.common.periphery.PeripheryResponse;
import com.bot.ncl.adapter.event.app.evt.Chkclmrflg;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.grpcclient.GrpcClientDto;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.scheduled.ScheduledFutureHolderPack;
import com.bot.txcontrol.util.transmit.GrpcClientService;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ChkClmrTask implements Runnable {

    private static final String WK_CODE = "160054";

    private Chkclmrflg event;

    @Autowired private ClmrService clmrService;

    @Autowired private GrpcClientService grpcClientService;

    @Autowired private ScheduledFutureHolderPack scheduledFutureHolderPack;

    @Override
    @Transactional
    public void run() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Find in database with code: " + WK_CODE);

        ClmrBus clmrBus = clmrService.findById(WK_CODE);

        if (!Objects.isNull(clmrBus) && clmrBus.getAfcbv() == 1) {
            // 資料封裝
            GrpcClientDto grpcClientDto = new GrpcClientDto();
            grpcClientDto.setMode("2");
            grpcClientDto.setBatchflag("S");

            grpcClientDto.setClientseq(
                    event.getPeripheryRequest().getApheader().getXBotClientSeq());
            grpcClientDto.getAttributes().put("TASKVALUE", "0");
            grpcClientDto.setPrid("");
            grpcClientDto.setTxcode("");
            grpcClientDto.setDscpt("");
            grpcClientDto.setFmtid("");
            grpcClientDto.setCharsets(Charsets.UTF8);
            grpcClientDto.setFormat(Format.TEXT);
            grpcClientDto.setBytes("".getBytes(StandardCharsets.UTF_8));
            PeripheryResponse peripheryResponse = grpcClientService.send(grpcClientDto);

            // Stop Task
            ScheduledFuture<?> scheduledFuture =
                    scheduledFutureHolderPack
                            .getScheduleMap()
                            .get(this.getClass().getName())
                            .getScheduledFuture();
            if (!Objects.isNull(scheduledFuture)) scheduledFuture.cancel(true);
        }
    }

    public Chkclmrflg getEvent() {
        return event;
    }

    public void setEvent(Chkclmrflg event) {
        this.event = event;
    }
}
