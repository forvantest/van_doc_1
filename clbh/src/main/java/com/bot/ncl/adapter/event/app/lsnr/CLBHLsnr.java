/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.Format;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.fsap.model.grpc.common.periphery.RqHeader;
import com.bot.ncl.adapter.event.app.evt.CLBH;
import com.bot.txcontrol.adapter.event.TradeListenerCase;
import com.bot.txcontrol.config.flow.ApiFolw;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.grpcclient.GrpcClientDto;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.transmit.GrpcClientService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class CLBHLsnr extends TradeListenerCase<CLBH> {

    @Autowired private GrpcClientService grpcClientService;

    @Autowired private DateUtil dateUtil;

    @Autowired private ApiFolw apiFolw;

    @Value("${server.hostname}")
    private String hostname;

    @Value("${spring.application.name}")
    private String springApplicationName;

    private static final String FMTID = "CL_BATCH_I";
    private static final String BATCH_LABEL_KEY_BTNNO = "BTNNO";
    private static final String BATCH_LABEL_KEY_BBSDY = "BBSDY";
    private static final String BATCH_LABEL_KEY_JOB = "JOB";

    @Override
    public void onApplicationEvent(CLBH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in CLBHLsnr");
        // 參考文件:
        // 雲端硬碟\台銀新核心-全行代理收付系統\文件\25.電文\05.FSAP與NCL之間批次服務規格
        // NCL-fsapBatch_apiSpec.docx
        RqHeader rqHeader = event.getPeripheryRequest().getPayload().getPyheader();
        Map<String, String> rqAttrMap = rqHeader.getAttributesMap();
        Map<String, ArrayMap> arrayMap = rqHeader.getArrayAttrMap();
        String btnno = "";
        String bbsdy = "";
        String job = "";
        Map<String, String> labelMap = new HashMap<>();
        Map<String, String> textMap = new HashMap<>();

        if (!rqAttrMap.isEmpty()) {
            btnno = rqAttrMap.get(BATCH_LABEL_KEY_BTNNO); // 批次執行序號
            bbsdy = rqAttrMap.get(BATCH_LABEL_KEY_BBSDY); // 批次營業日
            job = rqAttrMap.get(BATCH_LABEL_KEY_JOB); // job程式代號
        }
        if (!arrayMap.isEmpty()) {
            labelMap = new HashMap<>(arrayMap.get("labelMap").getMapAttrMap());
            btnno = labelMap.get(BATCH_LABEL_KEY_BTNNO); // 批次執行序號
            bbsdy = labelMap.get(BATCH_LABEL_KEY_BBSDY); // 批次營業日
            job = labelMap.get(BATCH_LABEL_KEY_JOB); // job程式代號
            if (arrayMap.containsKey("textMap")) {
                textMap = arrayMap.get("textMap").getMapAttrMap();
            } else {
                textMap = new HashMap<>();
            }
        }
        if (Objects.isNull(btnno) || btnno.isEmpty() || Objects.isNull(job) || job.isEmpty()) {
            labelMap = new HashMap<>();
            labelMap.put("STATUS", "E0001");
            labelMap.put("MSG", "缺少批次共用參數");
        }

        labelMap.put("JOBKIND", getJobkind(job));
        labelMap.put("START_DATE", dateUtil.getNowStringBc());
        labelMap.put("START_TIME", dateUtil.getNowStringTime(false));

        String tempHostName = hostname;
        if ("default-hostname".equals(tempHostName)) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "hostname為default-hostname,改用InetAddress.getLocalHost()");
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                tempHostName = localHost.getHostName();
            } catch (UnknownHostException e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "Failed to resolve hostname: " + e.getMessage());
            }
        }

        labelMap.put("HOSTNAME", tempHostName);

        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "發動批次,BTNNO={},BBSDY={},JOB={}",
                btnno,
                bbsdy,
                job);

        // 資料封裝
        GrpcClientDto grpcClientDto = new GrpcClientDto();
        grpcClientDto.setMode("-1");
        grpcClientDto.setSendSelf(true);
        grpcClientDto.setBatchflag("B");
        // 其餘相關參數與原grpc格式一致
        grpcClientDto.setClientid(event.getPeripheryRequest().getApheader().getXBotClientId());
        grpcClientDto.setServerid(event.getPeripheryRequest().getApheader().getXBotServerId());
        grpcClientDto.setClientdt(event.getPeripheryRequest().getApheader().getXBotClientDt());
        grpcClientDto.setRequestid(event.getPeripheryRequest().getApheader().getXBotRequestId());
        grpcClientDto.setClientseq(event.getPeripheryRequest().getApheader().getXBotClientSeq());
        grpcClientDto.setSyssyncno(event.getPeripheryRequest().getApheader().getXBotSysSyncno());
        grpcClientDto.setBranchsyncno(
                event.getPeripheryRequest().getApheader().getXBotBranchSyncno());
        grpcClientDto.setAttributes(rqAttrMap);
        grpcClientDto.put2arrayMap("labelMap", labelMap);
        grpcClientDto.put2arrayMap("textMap", textMap);
        grpcClientDto.setPrid(job);
        grpcClientDto.setTxcode(job);
        grpcClientDto.setDscpt("");
        grpcClientDto.setFmtid(FMTID);
        grpcClientDto.setCharsets(Charsets.BUR);
        grpcClientDto.setFormat(Format.TEXT);
        grpcClientDto.setRequestCase(event.getApiRequestCase());
        grpcClientDto.setData(null);
        // 送出
        grpcClientService.send(grpcClientDto);
    }

    private String getJobkind(String job) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "getJobkind,job={}", job);
        String jobKind = "U";
        try {
            jobKind = apiFolw.getApi(job).getTxType();
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "jobKind={}", jobKind);
        return jobKind;
    }
}
