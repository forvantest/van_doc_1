/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRE_CLDMR;
import com.bot.ncl.dto.entities.CldmrBus;
import com.bot.ncl.jpa.svc.CldmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CRE_CLDMRLsnr")
@Scope("prototype")
public class CRE_CLDMRLsnr extends BatchListenerCase<CRE_CLDMR> {

    @Autowired private CldmrService cldmrService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "CRE_CLDMR";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private List<CldmrBus> cldmrBusList;
    private Map<String, String> textMap;
    private String filePath;
    private String code;
    private String rcptid;
    private String id;
    private String pname;
    private String curcd;
    private String bal;
    private String ldate;
    private String ltime;
    private String kdate;
    private String lflg;
    private String cdate;
    private String udate;
    private String utime;
    private String kinbr;
    private String tlrno;
    private String empno;
    private String lcaday;
    private String lasttime;
    private String batchDate;

    @Override
    public void onApplicationEvent(CRE_CLDMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in CRE_CLDMRLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRE_CLDMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLDMR run()");
        init(event);
        validateCldmr();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(CRE_CLDMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLDMR init");
        filePath = fileDir + FILE_NAME;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        if (Objects.isNull(textMap)) {
            textMap = new HashMap<>();
        }
        batchDate = formatUtil.pad9(labelMap.get("BBSDY"), 8, 0); // 分行別

        filePath = fileDir + FILE_NAME;
        textFile.deleteFile(filePath);

        fileContents = new ArrayList<>();
        //  測試用日期
        if (batchDate == null) {
            batchDate = "1120704";
        }

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLDMR" + batchDate);
    }

    private void validateCldmr() {
        cldmrBusList = cldmrService.findAll(0, Integer.MAX_VALUE);
        if (Objects.isNull(cldmrBusList) || cldmrBusList.isEmpty()) {
            reportNodata();
        } else {
            queryCldmr();
        }
    }

    private void reportNodata() {
        fileContents.add(" ");
    }

    private void queryCldmr() {
        for (CldmrBus cldmrBus : cldmrBusList) {
            code = cldmrBus.getCode();
            rcptid = cldmrBus.getRcptid();
            id = cldmrBus.getId();
            pname = cldmrBus.getPname();
            curcd = parse.decimal2String(cldmrBus.getCurcd(), 2, 0);
            bal = parse.decimal2String(cldmrBus.getBal(), 11, 2);
            ldate = parse.decimal2String(cldmrBus.getLdate(), 8, 0);
            ltime = parse.decimal2String(cldmrBus.getLtime(), 6, 0);
            kdate = parse.decimal2String(cldmrBus.getKdate(), 8, 0);
            lflg = parse.decimal2String(cldmrBus.getLflg(), 1, 0);
            cdate = parse.decimal2String(cldmrBus.getCdate(), 8, 0);
            udate = parse.decimal2String(cldmrBus.getUdate(), 8, 0);
            utime = parse.decimal2String(cldmrBus.getUtime(), 6, 0);
            kinbr = parse.decimal2String(cldmrBus.getKinbr(), 3, 0);
            tlrno = cldmrBus.getTlrno();
            reportFormat();
        }
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void reportFormat() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(formatUtil.padX(code, 6));
        stringBuilder.append(formatUtil.padX(rcptid, 16));
        stringBuilder.append(formatUtil.padX(id, 10));
        stringBuilder.append(formatUtil.padX(pname, 30));
        stringBuilder.append(formatUtil.pad9(curcd, 2));
        stringBuilder.append(formatUtil.pad9(bal, 14));
        stringBuilder.append(formatUtil.pad9(ldate, 8));
        stringBuilder.append(formatUtil.pad9(ltime, 6));
        stringBuilder.append(formatUtil.pad9(kdate, 8));
        stringBuilder.append(formatUtil.pad9(lflg, 1));
        stringBuilder.append(formatUtil.pad9(cdate, 8));
        stringBuilder.append(formatUtil.pad9(udate, 8));
        stringBuilder.append(formatUtil.pad9(utime, 6));
        stringBuilder.append(formatUtil.pad9(kinbr, 3));
        stringBuilder.append(formatUtil.padX(tlrno, 2));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 200));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "CRE_CLDMR_" + batchDate.substring(1) + ".TXT";
        textFileUtil.deleteFile(reportFilePath);
        textFileUtil.writeFileContent(reportFilePath, fileContents, CHARSET);

        upload(reportFilePath);
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "2FSAP";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse(CRE_CLDMR event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
