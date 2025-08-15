/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryClfee;
import com.bot.ncl.dto.entities.ClfeebyTxtypeBus;
import com.bot.ncl.jpa.svc.ClfeeService;
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
@Component("QueryClfeeLsnr")
@Scope("prototype")
public class QueryClfeeLsnr extends BatchListenerCase<QueryClfee> {

    @Autowired private ClfeeService clfeeService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryClfee";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private String batchDate;
    private String filePath;
    private String keyCodeFee;
    private String txtype;
    private String stamt;
    private String cfee1;
    private String cfee2;
    private String cfee3;
    private String cfee4;
    private String fkd;
    private String mfee;
    private String cfeeeb;
    private String fee;
    private String feecost;
    private String cfee003;
    private String feeCalType;
    private Map<String, String> textMap;
    private List<ClfeebyTxtypeBus> clfeebyTxtypeList;

    @Override
    public void onApplicationEvent(QueryClfee event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryClfeeLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryClfee event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClfee run()");
        init(event);
        validateClfee();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryClfee event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClfee init");
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClfee" + batchDate);
    }

    private void validateClfee() {
        clfeebyTxtypeList = clfeeService.findbyTxtype("00", 0, Integer.MAX_VALUE);
        if (Objects.isNull(clfeebyTxtypeList) || clfeebyTxtypeList.isEmpty()) {
            reportNoData();
        } else {
            queryClfee();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryClfee() {
        for (ClfeebyTxtypeBus clfeebyTxtypeBus : clfeebyTxtypeList) {
            keyCodeFee = clfeebyTxtypeBus.getKeyCodeFee();
            txtype = clfeebyTxtypeBus.getTxtype();
            stamt = parse.decimal2String(clfeebyTxtypeBus.getStamt(), 6, 0);
            cfee1 = parse.decimal2String(clfeebyTxtypeBus.getCfee1(), 4, 2);
            cfee2 = parse.decimal2String(clfeebyTxtypeBus.getCfee2(), 3, 2);
            cfee3 = parse.decimal2String(clfeebyTxtypeBus.getCfee3(), 4, 2);
            cfee4 = parse.decimal2String(clfeebyTxtypeBus.getCfee4(), 10, 2);
            fkd = parse.decimal2String(clfeebyTxtypeBus.getFkd(), 1, 0);
            mfee = parse.decimal2String(clfeebyTxtypeBus.getMfee(), 3, 2);
            cfeeeb = parse.decimal2String(clfeebyTxtypeBus.getCfeeeb(), 3, 2);
            fee = parse.decimal2String(clfeebyTxtypeBus.getFee(), 3, 2);
            feecost = parse.decimal2String(clfeebyTxtypeBus.getFeecost(), 3, 2);
            cfee003 = parse.decimal2String(clfeebyTxtypeBus.getCfee003(), 3, 2);
            feeCalType = parse.decimal2String(clfeebyTxtypeBus.getFeeCalType(), 1, 0);
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
        stringBuilder.append(formatUtil.padX(keyCodeFee, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(txtype, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stamt, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee1, 7));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee2, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee3, 7));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee4, 13));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(fkd, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(mfee, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfeeeb, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(fee, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(feecost, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee003, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(feeCalType, 1));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 700));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLFEE";
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

    private void batchResponse(QueryClfee event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
