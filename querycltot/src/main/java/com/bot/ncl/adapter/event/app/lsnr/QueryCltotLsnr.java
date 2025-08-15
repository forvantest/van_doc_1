/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryCltot;
import com.bot.ncl.jpa.anq.svc.cm.QueryCltotService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
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
@Component("QueryCltotLsnr")
@Scope("prototype")
public class QueryCltotLsnr extends BatchListenerCase<QueryCltot> {
    @Autowired private QueryCltotService queryCltotService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryCltot";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private String batchDate;
    private String filePath;
    private String code;
    private String curcd;
    private String rcvamt;
    private String payamt;
    private String totcnt;
    private String pamt;
    private String npamt;
    private String lcaday;
    private String lasttime;
    private Map<String, String> textMap;
    private List<com.bot.ncl.jpa.anq.entities.impl.cm.QueryCltot> cltotBusList;

    @Override
    public void onApplicationEvent(QueryCltot event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryCltotLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryCltot event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltot run()");
        init(event);
        validateCltot();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryCltot event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltot init");
        filePath = fileDir + FILE_NAME;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        if (Objects.isNull(textMap)) {
            textMap = new HashMap<>();
        }
        batchDate = labelMap.get("BBSDY"); // 分行別

        filePath = fileDir + FILE_NAME;
        textFile.deleteFile(filePath);

        fileContents = new ArrayList<>();
        //  測試用日期
        if (batchDate == null) {
            batchDate = "1120704";
        }

        lcaday = "0";
        lasttime = "0";

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltot" + batchDate);
    }

    private void validateCltot() {
        cltotBusList = queryCltotService.queryCldtlCompare();
        if (Objects.isNull(cltotBusList) || cltotBusList.isEmpty()) {
            reportNoData();
        } else {
            queryCltot();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryCltot() {
        for (com.bot.ncl.jpa.anq.entities.impl.cm.QueryCltot cltotBus : cltotBusList) {
            code = cltotBus.getQueryCltotId().getCode();
            curcd = cltotBus.getQueryCltotId().getCurcd();
            BigDecimal wkRcvamt = parse.string2BigDecimal(cltotBus.getRcvamt());
            rcvamt = parse.decimal2String(wkRcvamt, 15, 2);
            BigDecimal wkPayamt = parse.string2BigDecimal(cltotBus.getPayamt());
            payamt = parse.decimal2String(wkPayamt, 15, 2);
            totcnt = cltotBus.getTotcnt();
            BigDecimal wkPamt = parse.string2BigDecimal(cltotBus.getPamt());
            pamt = parse.decimal2String(wkPamt, 15, 2);
            BigDecimal wkNpamt = parse.string2BigDecimal(cltotBus.getNpamt());
            npamt = parse.decimal2String(wkNpamt, 15, 2);
            if (Objects.isNull(cltotBus.getCaldy()) || cltotBus.getCaldy().isEmpty()) {
                lcaday = "0";
            } else {
                lcaday = parse.decimal2String(cltotBus.getCaldy(), 8, 0);
            }
            if (Objects.isNull(cltotBus.getTime()) || cltotBus.getTime().isEmpty()) {
                lasttime = "0";
            } else {
                lasttime = parse.decimal2String(cltotBus.getTime(), 6, 0);
            }
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
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(curcd, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(rcvamt, 18));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(payamt, 18));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(totcnt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(pamt, 18));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(npamt, 18));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lcaday, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lasttime, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 100));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLTOT";
        textFileUtil.deleteFile(reportFilePath);
        textFileUtil.writeFileContent(reportFilePath, fileContents, CHARSET);

        upload(reportFilePath);
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "CHKDB";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse(QueryCltot event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
