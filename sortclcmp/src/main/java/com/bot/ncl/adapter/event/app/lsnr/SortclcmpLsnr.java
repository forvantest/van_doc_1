/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Sortclcmp;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("SortclcmpLsnr")
@Scope("prototype")
public class SortclcmpLsnr extends BatchListenerCase<Sortclcmp> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private Sortclcmp event;
    private static final String UTF_8 = "UTF-8";
    private static final String PATH_SEPARATOR = File.separator;
    String filePath = "";
    private String[] wkParamL;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    @Override
    public void onApplicationEvent(Sortclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SortclcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Sortclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SortclcmpLsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }
        sort();
        batchResponse();
    }

    private Boolean init(Sortclcmp event) {

        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }

        String wkPutFdate = textMap.get("DATE"); // TODO: 待確認BATCH參數名稱
        String wkPutBrno = paramMap.get("PBRNO"); // TODO: 待確認BATCH參數名稱
        String wkPutFile = textMap.get("FILENAME"); // TODO: 待確認BATCH參數名稱

        // CENTERFILE :="DATA/GN/UPL/CL002/086/"&YYYMMDD&"/"& FILENAME;
        filePath = fileDir + wkPutBrno + PATH_SEPARATOR + wkPutFdate + PATH_SEPARATOR + wkPutFile;
        if (!textFileUtil.exists(filePath)) {
            return false;
        }

        return true;
    }

    private void sort() {

        List<KeyRange> keyRangeList = new ArrayList<>();
        keyRangeList.add(new KeyRange(1, 1, SortBy.ASC));
        keyRangeList.add(new KeyRange(2, 1, SortBy.DESC));
        keyRangeList.add(new KeyRange(3, 16, SortBy.ASC));

        externalSortUtil.sortingFile(filePath, filePath, keyRangeList, UTF_8);
    }

    private Map<String, String> getG2006Param(String lParam) {
        String[] paramL;
        if (lParam.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lParam is null");
            return null;
        }
        paramL = lParam.split(";");
        if (paramL == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "paramL is null");
            return null;
        }
        // G2006:
        //  PBRNO(;),
        //  HCODE(;),
        //  LEN(;),
        //  PARAM1(;),
        //  PARAM2(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("PBRNO", paramL[0]); // 對應 PBRNO
        if (paramL.length > 1) map.put("HCODE", paramL[1]); // 對應 HCODE
        if (paramL.length > 2) map.put("LEN", paramL[2]); // 對應 LEN
        if (paramL.length > 3) map.put("PARAM1", paramL[3]); // 對應 PARAM1
        if (paramL.length > 4) map.put("PARAM2", paramL[4]); // 對應 PARAM2
        if (map.size() == 0) {
            return null;
        }

        for (String key : map.keySet()) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "map KEY = {} ,VALUE = {}",
                    key,
                    map.get(key));
        }
        return map;
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
