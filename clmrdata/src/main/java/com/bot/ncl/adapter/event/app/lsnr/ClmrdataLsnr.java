/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Clmrdata;
import com.bot.ncl.jpa.anq.entities.impl.QueryClmrdata;
import com.bot.ncl.jpa.anq.svc.QueryClmrdataService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileClmrdata;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ClmrdataLsnr")
@Scope("prototype")
public class ClmrdataLsnr extends BatchListenerCase<Clmrdata> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;
    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private QueryClmrdataService queryClmrdataService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    private Clmrdata event;

    private List<FileClmrdata> clmrdataList;

    private Map<String, String> textMap;
    private String wkPutBrno;
    private String wkPutFdate;
    private String wkPutFile;
    private String[] wkParamL;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Clmrdata event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ClmrdataLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Clmrdata event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ClmrdataLsnr run()");

        if (!init(event)) {
            batchResponse();
        }

        mainRoutine();

        output();

        batchResponse();
    }

    private Boolean init(Clmrdata event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        this.event = event;
        clmrdataList = new ArrayList<>();

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkPutFile = textMap.get("FILENAME");
        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkPutFdate = formatUtil.pad9(textMap.get("DATE"), 8).substring(1);
        wkPutBrno = paramMap.get("PBRNO");
        String wkPutDir =
                fileDir
                        + PATH_SEPARATOR
                        + wkPutBrno
                        + PATH_SEPARATOR
                        + wkPutFdate
                        + PATH_SEPARATOR
                        + wkPutFile;
        if (!textFileUtil.exists(wkPutDir)) {
            return false;
        }
        return true;
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        List<QueryClmrdata> list = queryClmrdataService.queryClmrdata();
        if (!Objects.isNull(list) && !list.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "list.size()={}", list.size());
            for (QueryClmrdata clmrdata : list) {
                moveClmrData(clmrdata);
            }
        } else {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmrBusList is empty.");
        }
    }

    private void moveClmrData(QueryClmrdata clmrData) {
        FileClmrdata fileClmrdata = new FileClmrdata();
        fileClmrdata.setCode(clmrData.getCode());
        fileClmrdata.setPbrno(clmrData.getPbrno());
        fileClmrdata.setAtmcode(clmrData.getAtmcode());
        fileClmrdata.setVrcode(clmrData.getVrcode());
        fileClmrdata.setRiddup(clmrData.getRiddup());
        fileClmrdata.setActno(clmrData.getActno());
        fileClmrdata.setMsg1(clmrData.getMsg1());
        fileClmrdata.setMsg2(clmrData.getMsg2());
        fileClmrdata.setCfee1(clmrData.getCfee1());
        fileClmrdata.setCfee2(clmrData.getCfee2());
        fileClmrdata.setPuttime(clmrData.getPuttime());
        fileClmrdata.setChktype(clmrData.getChktype());
        fileClmrdata.setChkamt(clmrData.getChkamt());
        fileClmrdata.setUnit(clmrData.getUnit());
        fileClmrdata.setAmt(clmrData.getAmt());
        fileClmrdata.setTotamt(clmrData.getTotamt());
        fileClmrdata.setTotcnt(clmrData.getTotcnt());
        fileClmrdata.setEntpno(clmrData.getEntpno());
        fileClmrdata.setHentpno(clmrData.getHentpno());
        fileClmrdata.setCname(clmrData.getCname());
        fileClmrdata.setScname(clmrData.getScname());
        fileClmrdata.setCdata(clmrData.getCdata());
        fileClmrdata.setFkd(clmrData.getFkd());
        fileClmrdata.setMfee(clmrData.getMfee());
        fileClmrdata.setStop(clmrData.getStop());
        fileClmrdata.setAfcbv(clmrData.getAfcbv());
        fileClmrdata.setCyck1(clmrData.getCyck1());
        fileClmrdata.setCycno1(clmrData.getCycno1());
        fileClmrdata.setCyck2(clmrData.getCyck2());
        fileClmrdata.setCycno2(clmrData.getCycno2());
        fileClmrdata.setPutname(clmrData.getPutname());
        fileClmrdata.setPuttype(clmrData.getPuttype());
        fileClmrdata.setPutaddr(clmrData.getPutaddr());
        fileClmrdata.setNetinfo(clmrData.getNetinfo());
        fileClmrdata.setAppdt(clmrData.getAppdt());
        fileClmrdata.setUpddt(clmrData.getUpddt());
        fileClmrdata.setPutdt(clmrData.getPutdt());
        fileClmrdata.setTputdt(clmrData.getTputdt());
        fileClmrdata.setLputdt(clmrData.getLputdt());
        fileClmrdata.setLlputdt(clmrData.getLlputdt());
        fileClmrdata.setUlputdt(clmrData.getUlputdt());
        fileClmrdata.setUllputdt(clmrData.getUllputdt());
        fileClmrdata.setClsacno(clmrData.getClsacno());
        fileClmrdata.setStopdate(clmrData.getStopdate());
        fileClmrdata.setStoptime(clmrData.getStoptime());
        fileClmrdata.setCrdb(clmrData.getCrdb());
        fileClmrdata.setCfee3(clmrData.getCfee3());
        fileClmrdata.setCfee4(clmrData.getCfee4());
        fileClmrdata.setCfeeeb(clmrData.getCfeeeb());
        fileClmrdata.setEbtype(clmrData.getEbtype());
        fileClmrdata.setLkcode(clmrData.getFlag());
        fileClmrdata.setPwtype(clmrData.getPwtype());
        clmrdataList.add(fileClmrdata);
    }

    private void output() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "output()");
        List<String> dataList = new ArrayList<>();
        if (clmrdataList != null && !clmrdataList.isEmpty()) {
            for (FileClmrdata clmrdata : clmrdataList) {
                dataList.add(vo2TextFormatter.formatRS(clmrdata, false, ","));
            }
        }
        // DATA/CL/BH/CLMRDATA
        String filePath = fileDir + "CLMRDATA";
        textFileUtil.deleteFile(filePath);
        textFileUtil.writeFileContent(filePath, dataList, CHARSET);
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
