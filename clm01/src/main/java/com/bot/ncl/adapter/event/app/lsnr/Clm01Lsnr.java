/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Clm01;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyBetweenBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Clm01Lsnr")
@Scope("prototype")
public class Clm01Lsnr extends BatchListenerCase<Clm01> {

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Parse parse;

    @Autowired private CldtlService cldtlService;

    private int cldtlIndex = 0;

    private static final int CLDTL_LIMIT = 2000;

    private int wkTotcnt = 0;

    private BigDecimal wkTotamt = BigDecimal.ZERO;

    private String wkFlag;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String putfDir;

    private String wkCode;

    private int wkBdate;

    private int wkEdate;

    // @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Clm01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Clm01Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Clm01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Clm01Lsnr run()");
        mainRoutine(event);
    }

    private void mainRoutine(Clm01 event) {
        wkFlag = "N";

        int tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();

        Map<String, String> headersMap = event.getPeripheryRequest().getHeadersMap();

        settingWkBdate(tbsdy, headersMap);
        settingWkEdate(tbsdy, headersMap);

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

        putfDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "OIL";

        wkCode = "160054";

        queryAndWriteReport();

        writeSummaryRecord();

        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/MEDIA/001 GENERATE DATA/CL/BH/OIL OK");
    }

    private void settingWkBdate(int tbsdy, Map<String, String> headersMap) {
        if (headersMap.containsKey("BDATE")
                && !Objects.isNull(headersMap.get("BDATE"))
                && parse.isNumeric(headersMap.get("BDATE"))) {
            wkBdate = parse.string2Integer(headersMap.get("BDATE"));
        } else {
            wkBdate = tbsdy;
        }
    }

    private void settingWkEdate(int tbsdy, Map<String, String> headersMap) {
        if (headersMap.containsKey("EDATE")
                && !Objects.isNull(headersMap.get("EDATE"))
                && parse.isNumeric(headersMap.get("EDATE"))) {
            wkEdate = parse.string2Integer(headersMap.get("EDATE"));
        } else {
            wkEdate = tbsdy;
        }
    }

    private void queryAndWriteReport() {
        List<CldtlbyCodeEntdyBetweenBus> cldtlList =
                cldtlService.findbyCodeEntdyBetween(
                        wkCode, wkBdate, wkEdate, 0, cldtlIndex, CLDTL_LIMIT);
        if (cldtlList == null || cldtlList.isEmpty()) {
            return;
        }
        for (CldtlbyCodeEntdyBetweenBus cldtl : cldtlList) {
            wkTotcnt++;
            wkTotamt = wkTotamt.add(cldtl.getAmt());
            wkFlag = "Y";
            writePutfRec(cldtl);
        }

        if (cldtlList.size() == CLDTL_LIMIT) {
            cldtlIndex++;
            queryAndWriteReport();
        }
    }

    private void writePutfRec(CldtlbyCodeEntdyBetweenBus cldtlBus) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "Write PUTF-REC for cldtlBus: " + cldtlBus + " with ctl: " + 11);
        FilePUTF filePutf = new FilePUTF();
        filePutf.setCtl("11");
        filePutf.setCode(cldtlBus.getCode());
        filePutf.setRcptid(cldtlBus.getRcptid());
        filePutf.setEntdy(String.format("%06d", cldtlBus.getEntdy()));
        filePutf.setTime(String.format("%06d", cldtlBus.getTime()));
        filePutf.setCllbr(String.format("%03d", cldtlBus.getCllbr()));
        filePutf.setLmtdate(String.format("%06d", cldtlBus.getLmtdate()));
        filePutf.setOldamt(cldtlBus.getAmt().setScale(2, RoundingMode.HALF_UP).toPlainString());
        filePutf.setUserdata(cldtlBus.getUserdata());
        filePutf.setSitdate(String.format("%06d", cldtlBus.getSitdate()));
        filePutf.setTxtype(cldtlBus.getTxtype());
        filePutf.setAmt(cldtlBus.getAmt().setScale(2, RoundingMode.HALF_UP).toPlainString());
        filePutf.setFiller(""); // 假設這個欄位是空的
        List<String> list = new ArrayList<>();
        list.add(vo2TextFormatter.formatRS(filePutf, false));
        textFileUtil.writeFileContent(putfDir, list, CHARSET);
    }

    private void writeSummaryRecord() {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "Write summary record for code: "
                        + wkCode
                        + " from "
                        + wkBdate
                        + " to "
                        + wkEdate
                        + " with total count: "
                        + wkTotcnt
                        + " and total amount: "
                        + wkTotamt);
        if ("N".equals(wkFlag)) {
            return;
        }

        FileSumPUTF fileSumPutf = new FileSumPUTF();
        fileSumPutf.setCtl("12"); // 12表示彙總
        fileSumPutf.setCode(wkCode);
        fileSumPutf.setBdate(String.format("%06d", wkBdate));
        fileSumPutf.setEdate(String.format("%06d", wkEdate));
        fileSumPutf.setTotcnt(String.format("%06d", wkTotcnt));
        fileSumPutf.setTotamt(wkTotamt.setScale(0, RoundingMode.HALF_UP).toPlainString());
        fileSumPutf.setFiller(""); // 假設這個欄位是空的

        List<String> list = new ArrayList<>();
        list.add(vo2TextFormatter.formatRS(fileSumPutf, false));
        textFileUtil.writeFileContent(putfDir, list, CHARSET);
    }
}
