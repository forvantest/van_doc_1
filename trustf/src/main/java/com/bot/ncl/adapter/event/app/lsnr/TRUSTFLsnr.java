/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.TRUSTF;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyBetweenBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@Component("TRUSTFLsnr")
@Scope("prototype")
public class TRUSTFLsnr extends BatchListenerCase<TRUSTF> {

    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private TextFileUtil textFileUtil;

    @Autowired private ClmrService clmrService;

    @Autowired private CldtlService cldtlService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private static final String CHARSET = "UTF-8";

    private String wkPutDir;
    private String tbsdy;
    private int wkTotCnt = 0;
    private BigDecimal wkTotAmt = BigDecimal.ZERO;

    private int clmrIndex = 0;
    private int cldtlIndex = 0;
    private static final int LIMIT = 2000;

    private int wkDate;

    @Override
    public void onApplicationEvent(TRUSTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TRUSTFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(TRUSTF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TRUSTFLsnr run()");
        mainRoutine(event);
    }

    private void mainRoutine(TRUSTF event) {
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定本營業日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkPutDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "TRUSTF";
        wkDate = event.getAggregateBuffer().getTxCom().getTbsdy();

        queryClmr();

        if (wkTotCnt == 0) {
            return;
        }

        writeSummaryRecord();

        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/TRUSTF GENERATE DATA/CL/BH/TRUSTF OK");
    }

    private void queryClmr() {
        List<ClmrBus> clmrList = clmrService.findAll(clmrIndex, LIMIT);
        if (Objects.isNull(clmrList) || clmrList.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "***EMPTY TODAY-FILE***");
            return;
        }
        for (ClmrBus clmr : clmrList) {
            processClmr(clmr);
        }
        if (clmrList.size() == LIMIT) {
            clmrIndex++;
            queryClmr();
        }
    }

    private void processClmr(ClmrBus clmr) {
        String code = clmr.getCode();
        if ((code.startsWith("33") && !"330022".equals(code))
                || (code.compareTo("710291") > 0
                        && code.compareTo("710296") < 0
                        && !"710293".equals(code))
                || "730521".equals(code)) {
            cldtlIndex = 0;
            queryCldtl(code);
        }
    }

    private void queryCldtl(String code) {
        List<CldtlbyCodeEntdyBetweenBus> cldtlList =
                cldtlService.findbyCodeEntdyBetween(code, wkDate, wkDate, 0, cldtlIndex, LIMIT);
        if (Objects.isNull(cldtlList) || cldtlList.isEmpty()) {
            return;
        }
        for (CldtlbyCodeEntdyBetweenBus cldtl : cldtlList) {
            processCldtl(cldtl);
        }
        if (cldtlList.size() == LIMIT) {
            cldtlIndex++;
            queryCldtl(code);
        }
    }

    private void processCldtl(CldtlbyCodeEntdyBetweenBus cldtl) {
        FilePUTF filePutf = new FilePUTF();
        filePutf.setCtl("11");
        filePutf.setCode(cldtl.getCode());
        filePutf.setRcptid(cldtl.getRcptid());
        filePutf.setEntdy(String.format("%08d", cldtl.getEntdy()));
        filePutf.setTime(String.format("%06d", cldtl.getTime()));
        filePutf.setCllbr(String.format("%03d", cldtl.getCllbr()));
        filePutf.setLmtdate(String.format("%08d", cldtl.getLmtdate()));
        filePutf.setUserdata(cldtl.getUserdata());
        filePutf.setOldamt(cldtl.getAmt().setScale(2, RoundingMode.HALF_UP).toPlainString());
        filePutf.setAmt(cldtl.getAmt().setScale(2, RoundingMode.HALF_UP).toPlainString());
        filePutf.setSitdate(String.format("%08d", cldtl.getSitdate()));
        filePutf.setTxtype(cldtl.getTxtype());
        filePutf.setFiller(""); // 假設這個欄位是空的

        // 寫入 PUTF-REC
        wkTotCnt++;
        wkTotAmt = wkTotAmt.add(cldtl.getAmt());

        List<String> list = new ArrayList<>();
        list.add(vo2TextFormatter.formatRS(filePutf, false));
        textFileUtil.writeFileContent(wkPutDir, list, CHARSET);
        upload(wkPutDir, "DATA", "");
    }

    private void writeSummaryRecord() {
        FileSumPUTF fileSumPutf = new FileSumPUTF();
        fileSumPutf.setCtl("12"); // 12表示彙總
        fileSumPutf.setCode("330011");
        fileSumPutf.setBdate(String.format("%06d", wkDate));
        fileSumPutf.setEdate(String.format("%06d", wkDate));
        fileSumPutf.setTotcnt(String.format("%06d", wkTotCnt));
        fileSumPutf.setTotamt(wkTotAmt.setScale(0, RoundingMode.HALF_UP).toPlainString());
        fileSumPutf.setFiller(""); // 假設這個欄位是空的

        List<String> list = new ArrayList<>();
        list.add(vo2TextFormatter.formatRS(fileSumPutf, false));
        textFileUtil.writeFileContent(wkPutDir, list, CHARSET);
        upload(wkPutDir, "DATA", "");
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }
}
