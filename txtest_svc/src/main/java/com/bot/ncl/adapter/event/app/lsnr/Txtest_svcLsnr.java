/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.Format;
import com.bot.ncl.adapter.event.app.evt.Txtest_svc;
import com.bot.ncl.dto.entities.CldtlBus;
import com.bot.ncl.jpa.entities.impl.CldtlId;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.grpcclient.GrpcClientDto;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.transmit.GrpcClientService;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Txtest_svcLsnr")
@Scope("prototype")
public class Txtest_svcLsnr extends BatchListenerCase<Txtest_svc> {

    @Autowired private GrpcClientService grpcClientService;

    @Autowired private CldtlService cldtlService;

    private int queryIndex;
    private static final int PAGE_LIMIT = 100000;

    private int uncommitCount;
    private int commitLimit;

    private Date startTime;

    private int dataCounts;

    private List<Map<String, String>> recordList;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Txtest_svc event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Txtest_svcLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Txtest_svc event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Txtest_svcLsnr run()");
        recordList = new ArrayList<>();

        Map<String, String> map =
                event.getPeripheryRequest().getPayload().getPyheader().getAttributesMap();
        String testType = map.get("TEST_TYPE");
        if (testType.equals("BT001_BT200")) {
            callBT001_BT200(event);
        } else {
            String commit_limit = map.get("COMMIT_LIMIT");
            if (Objects.isNull(commit_limit)) {
                commit_limit = "10";
            }
            commitLimit = Integer.parseInt(commit_limit);
            testingRoutine();

            // 列印最終結果
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "列印最終結果");
            for (Map<String, String> record : recordList) {
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "dataCounts={}",
                        record.get("dataCounts"));
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "commitLimit={}",
                        record.get("commitLimit"));
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "usedTime={}",
                        record.get("usedTime"));
            }
        }
    }

    private void testingRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "commitLimit={}", commitLimit);
        initParam();
        mainRoutine();
        record();
    }

    private void initParam() {
        dataCounts = 0;
        queryIndex = 0;
        uncommitCount = 0;
        startTime = new Date();
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "queryIndex={}", queryIndex);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PAGE_LIMIT={}", PAGE_LIMIT);
        // query
        List<CldtlBus> cldtlBusList = cldtlService.findAll(queryIndex, PAGE_LIMIT);
        if (Objects.isNull(cldtlBusList) || cldtlBusList.isEmpty()) {
            return;
        }
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "cldtlBusList.size()={}",
                cldtlBusList.size());

        // foreach
        for (CldtlBus cldtl : cldtlBusList) {
            CldtlId cldtlId = settingCldtlId(cldtl);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "holdById={}", cldtlId);
            CldtlBus holdCldtl = cldtlService.holdById(cldtlId);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "holdCldtl={}", holdCldtl);
            holdCldtl.setCreateEmp("123456");
            cldtlService.update(holdCldtl);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "update.");
            dataCounts++;
            uncommitCount++;
            if (uncommitCount == commitLimit) {
                doCommit();
            }
        }
    }

    private CldtlId settingCldtlId(CldtlBus cldtl) {
        CldtlId cldtlId = new CldtlId();
        cldtlId.setCode(cldtl.getCode());
        cldtlId.setEntdy(cldtl.getEntdy());
        cldtlId.setRcptid(cldtl.getRcptid());
        cldtlId.setTrmno(cldtl.getTrmno());
        cldtlId.setTxtno(cldtl.getTxtno());
        return cldtlId;
    }

    private void doCommit() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doCommit()");
        this.batchTransaction.commit();
        uncommitCount = 0;
    }

    private void record() {
        Date endTime = new Date();
        long usedTime = endTime.getTime() - startTime.getTime();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dataCounts={}", dataCounts);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "commitLimit={}", commitLimit);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "usedTime={}", usedTime);

        Map<String, String> record = new HashMap<>();
        record.put("dataCounts", "" + dataCounts);
        record.put("commitLimit", "" + commitLimit);
        record.put("usedTime", "" + usedTime);
        recordList.add(record);
    }

    private void callBT001_BT200(Txtest_svc event) {
        GrpcClientDto grpcClientDto = new GrpcClientDto();
        grpcClientDto.setSendSelf(false);
        grpcClientDto.setMode("1");
        grpcClientDto.setClientid("NCL");
        grpcClientDto.setServerid("FSAP-BATCH");
        grpcClientDto.setClientseq("137007171504001");
        grpcClientDto.goNextSeq();
        grpcClientDto.setRequestid("TEST");
        grpcClientDto.setBranchsyncno("");
        grpcClientDto.setSyssyncno("");
        grpcClientDto.setBatchflag("B");
        Map<String, String> rqAttrMap =
                event.getPeripheryRequest().getPayload().getPyheader().getAttributesMap();
        grpcClientDto.setAttributes(rqAttrMap);
        grpcClientDto.setPrid("");
        grpcClientDto.setTxcode("BT001");
        grpcClientDto.setDscpt("BT200");
        grpcClientDto.setFmtid("BT001.BT200.I");
        grpcClientDto.setCharsets(Charsets.UTF8);
        grpcClientDto.setFormat(Format.TEXT);
        grpcClientDto.setHcode("N");
        grpcClientDto.setRstinq("");
        grpcClientDto.setRequestCase(event.getApiRequestCase());
        // 送出
        try {
            grpcClientService.send(grpcClientDto);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "grpcClientService.send() error={}",
                    e.getMessage());
        }
    }
}
