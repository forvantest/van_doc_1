/* (C) 2024 */
package com.bot.ncl.util;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.Format;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.fsap.model.grpc.common.periphery.PeripheryResponse;
import com.bot.fsap.model.grpc.common.periphery.RsHeader;
import com.bot.fsap.model.grpc.common.periphery.RsPayload;
import com.bot.ncl.enums.BatchLabel;
import com.bot.txcontrol.adapter.RequestSvcCase;
import com.bot.txcontrol.adapter.event.TradeEventCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.grpcclient.GrpcClientDto;
import com.bot.txcontrol.eum.Constant;
import com.bot.txcontrol.eum.Env;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.transmit.GrpcClientService;
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
public class FsapBatchUtil {

    @Value("${spring.profiles.active}")
    private String env;

    @Autowired private GrpcClientService grpcClientService;

    @Autowired private DateUtil dateUtil;

    private FsapBatchUtil() {
        // YOU SHOULD USE @Autowired ,NOT new FsapBatchUtil()
    }

    /**
     * processFile 檔案處理,通知FSAP-BATCH處理檔案
     *
     * @param batchNo 檔案批號(6碼長) ex.000001
     * @param apKind 業務大項,系統別(5碼長) ex.NCL
     * @param apItem 業務細項(10碼長) ex.PUTF
     * @param srcFileName 來源檔案名稱(20碼長)
     * @param tarFileName 目的檔案名稱(20碼長)
     * @param syncFlag 同步/非同步記號(1碼長) 1：同步 2：非同步
     * @param prFileType 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
     * @return Map<String, String> KEY "RESULT":("S":成功,"F":失敗), KEY "MSG": 失敗時提供錯誤訊息
     */
    public Map<String, String> processFile(
            String batchNo,
            String apKind,
            String apItem,
            String srcFileName,
            String tarFileName,
            String syncFlag,
            String prFileType) {
        return this.processFile(
                batchNo,
                apKind,
                apItem,
                srcFileName,
                tarFileName,
                syncFlag,
                prFileType,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
    }

    /**
     * processFile 檔案處理,通知FSAP-BATCH處理檔案
     *
     * @param batchNo 檔案批號(6碼長) ex.000001
     * @param apKind 業務大項,系統別(5碼長) ex.NCL
     * @param apItem 業務細項(10碼長) ex.PUTF
     * @param srcFileName 來源檔案名稱(20碼長)
     * @param tarFileName 目的檔案名稱(20碼長)
     * @param syncFlag 同步/非同步記號(1碼長) 1：同步 2：非同步
     * @param prFileType 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
     * @param ftpAct 對方FTP連線帳號
     * @param ftpid 對方FTP連線密碼
     * @param bizid 檔案設定代號
     * @param prdId PRDID (產品代碼,系統別)，ex.”NCL”
     * @param ntfClsId NTFCLSID (分類代碼,notify版型), ex.”01”
     * @param dlvChnl DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
     * @param cntAddr CNTADDR 收件者，多筆以半形分號(;)區隔
     * @param notifyTitle NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
     * @param content CONTENT (通知本文)email內文、簡訊內容
     * @return Map<String, String> KEY "RESULT":("S":成功,"F":失敗), KEY "MSG": 失敗時提供錯誤訊息
     */
    public Map<String, String> processFile(
            String batchNo,
            String apKind,
            String apItem,
            String srcFileName,
            String tarFileName,
            String syncFlag,
            String prFileType,
            String ftpAct,
            String ftpid,
            String bizid,
            String prdId,
            String ntfClsId,
            String dlvChnl,
            String cntAddr,
            String notifyTitle,
            String content,
            String wfl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "processFile()");
        // call BT001.BT200(NCL通知處理檔案)
        // 參考文件:
        // 雲端硬碟\台銀新核心-全行代理收付系統\文件\25.電文\05.FSAP與NCL之間批次服務規格
        // NCL-fsapBatch_apiSpec.docx
        Map<String, String> textMap = new HashMap<>();
        textMap.put(BatchLabel.BATCH_NO.getKey(), batchNo);
        textMap.put(BatchLabel.AP_KIND.getKey(), apKind);
        textMap.put(BatchLabel.AP_ITEM.getKey(), apItem);
        textMap.put(BatchLabel.SRC_FILE_NAME.getKey(), srcFileName);
        textMap.put(BatchLabel.TAR_FILE_NAME.getKey(), tarFileName);
        textMap.put(BatchLabel.SYNC_FG.getKey(), syncFlag);
        textMap.put(BatchLabel.PR_FILE_TYPE.getKey(), prFileType);
        textMap.put(BatchLabel.FTPACT.getKey(), ftpAct);
        textMap.put(BatchLabel.FTPID.getKey(), ftpid);
        textMap.put(BatchLabel.BIZID.getKey(), bizid);
        textMap.put(BatchLabel.PRDID.getKey(), prdId);
        textMap.put(BatchLabel.NTFCLSID.getKey(), ntfClsId);
        textMap.put(BatchLabel.DLVCHNL.getKey(), dlvChnl);
        textMap.put(BatchLabel.CNTADDR.getKey(), cntAddr);
        textMap.put(BatchLabel.NOTIFYTITLE.getKey(), notifyTitle);
        textMap.put(BatchLabel.CONTENT.getKey(), content);
        textMap.put(BatchLabel.WFL.getKey(), wfl);

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "batchNo={}", batchNo);

        // 資料封裝
        GrpcClientDto grpcClientDto = new GrpcClientDto();
        grpcClientDto.setMode("-1");
        grpcClientDto.setSendSelf(false);
        grpcClientDto.setBatchflag("B");
        grpcClientDto.setServerid(Constant.FASP_BATCH.getCode());
        grpcClientDto.setClientid(Constant.NCL_BATCH.getCode());

        // 其餘相關參數與原grpc格式一致
        grpcClientDto.setClientseq(batchNo);

        grpcClientDto.put2arrayMap("textMap", textMap);

        grpcClientDto.setTxcode("BT001");
        grpcClientDto.setDscpt("BT200");
        grpcClientDto.setFmtid("BT001.BT200.I");
        grpcClientDto.setCharsets(Charsets.UTF8);
        grpcClientDto.setFormat(Format.TEXT);
        grpcClientDto.setData(null);
        // 送出
        PeripheryResponse response = grpcClientService.send(grpcClientDto);

        String xBotStatus = response.getApheader().getXBotStatus();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "xBotStatus={}", xBotStatus);

        Map<String, String> result = new HashMap<>();
        if (xBotStatus.equals("0000")) {
            result.put("RESULT", "S");
            result.put("MSG", "");
        } else {
            result.put("RESULT", "F");

            RsPayload payload = response.getPayloadOrThrow(0L);
            Map<String, String> responseTextMap =
                    payload.getPyheader().getArrayAttrMap().get("textMap").getMapAttrMap();
            String msg = responseTextMap.get("MSG");
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "msg={}", msg);

            result.put("MSG", msg);
        }
        return result;
    }

    /**
     * 回傳結果給FSAP
     *
     * @param event EVENT
     * @param status "0000":成功;"Ennn":失敗(nnn為錯誤代碼)
     * @param msg 錯誤訊息
     * @param responseTextMap 回傳參數(若無可為null)
     */
    public void response(
            TradeEventCase<RequestSvcCase> event,
            String status,
            String msg,
            Map<String, String> responseTextMap) {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "response(),status={},msg={}", status, msg);
        if (Env.LOCAL.getCode().equals(this.env)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "此為local測試不發送");
            return;
        }
        // call BT001.CLBH(NCL通知JOB處理結果)
        // 參考文件:
        // 雲端硬碟\台銀新核心-全行代理收付系統\文件\25.電文\05.FSAP與NCL之間批次服務規格
        // NCL-fsapBatch_apiSpec.docx
        GrpcClientDto grpcClientDto = new GrpcClientDto();
        grpcClientDto.setRequestid(event.getPeripheryRequest().getApheader().getXBotRequestId());
        grpcClientDto.setClientseq(event.getPeripheryRequest().getApheader().getXBotClientSeq());
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> responseLabelMap = settingLabelMap(arrayMap, status, msg);
        responseTextMap = settingTextMap(arrayMap, responseTextMap, status, msg);
        settingDto(grpcClientDto, responseLabelMap, responseTextMap);
        // 送出
        PeripheryResponse fsapBatchResponse = grpcClientService.send(grpcClientDto);
        showFsapBatchResponseMsg(fsapBatchResponse);
    }

    private Map<String, String> settingTextMap(
            Map<String, ArrayMap> arrayMap,
            Map<String, String> responseTextMap,
            String status,
            String msg) {
        Map<String, String> textMap;
        if (arrayMap.containsKey("textMap")) {
            textMap = arrayMap.get("textMap").getMapAttrMap();
        } else {
            textMap = new HashMap<>();
        }
        if (Objects.isNull(responseTextMap)) {
            responseTextMap = new HashMap<>(textMap);
        } else {
            for (String key : textMap.keySet()) {
                if (!responseTextMap.containsKey(key)) {
                    responseTextMap.put(key, textMap.get(key));
                }
            }
        }
        responseTextMap.put("STATUS", status);
        responseTextMap.put("MSG", msg);
        return responseTextMap;
    }

    private Map<String, String> settingLabelMap(
            Map<String, ArrayMap> arrayMap, String status, String msg) {
        Map<String, String> labelMap;
        if (arrayMap.containsKey("labelMap")) {
            labelMap = arrayMap.get("labelMap").getMapAttrMap();
        } else {
            labelMap = new HashMap<>();
        }
        Map<String, String> responseLabelMap = new HashMap<>(labelMap);
        responseLabelMap.put("STATUS", status);
        responseLabelMap.put("MSG", msg);
        responseLabelMap.put("END_DATE", dateUtil.getNowStringBc());
        responseLabelMap.put("END_TIME", dateUtil.getNowStringTime(false));
        return responseLabelMap;
    }

    private void settingDto(
            GrpcClientDto grpcClientDto,
            Map<String, String> labelMap,
            Map<String, String> textMap) {
        grpcClientDto.setMode("-1");
        grpcClientDto.setSendSelf(false);
        grpcClientDto.setBatchflag("S");
        grpcClientDto.setAsync(false);
        grpcClientDto.setServerid(Constant.FASP_BATCH.getCode());
        grpcClientDto.setClientid(Constant.NCL_BATCH.getCode());

        grpcClientDto.put2arrayMap("labelMap", labelMap);
        grpcClientDto.put2arrayMap("textMap", textMap);

        grpcClientDto.setTxcode("BT001");
        grpcClientDto.setDscpt("CLBH");
        grpcClientDto.setFmtid("BT001.CLBH.I");
        grpcClientDto.setCharsets(Charsets.UTF8);
        grpcClientDto.setFormat(Format.TEXT);
        grpcClientDto.setData(null);
    }

    private void showFsapBatchResponseMsg(PeripheryResponse fsapBatchResponse) {
        RsPayload fsapBatchPayload = fsapBatchResponse.getPayloadOrThrow(0L);
        RsHeader fsapBatchPyHeader = fsapBatchPayload.getPyheader();
        String fsapMsgid = fsapBatchPyHeader.getMsgid();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fsapMsgid={}", fsapMsgid);
        Map<String, String> fsapResponseTextMap =
                fsapBatchPyHeader.getArrayAttrMap().get("textMap").getMapAttrMap();
        if (fsapResponseTextMap.containsKey("MSG")) {
            String fsapBatchResponseMsg = fsapResponseTextMap.get("MSG");
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "fsapBatchResponseMsg={}",
                    fsapBatchResponseMsg);
        }
    }

    /**
     * 回傳錯誤給FSAP
     *
     * @param event EVENT
     * @param e 錯誤
     */
    public void response(TradeEventCase<RequestSvcCase> event, LogicException e) {
        this.response(event, e.getMsgId(), e.getErrMsg(), null);
    }
}
