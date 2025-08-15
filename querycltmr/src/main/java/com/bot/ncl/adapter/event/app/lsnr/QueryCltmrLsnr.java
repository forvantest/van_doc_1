/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryCltmr;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CltmrService;
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
@Component("QueryCltmrLsnr")
@Scope("prototype")
public class QueryCltmrLsnr extends BatchListenerCase<QueryCltmr> {
    @Autowired private CltmrService cltmrService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryCltmr";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private String batchDate;
    private String filePath;
    private String code;
    private String atmcode;
    private String entpno;
    private String hentpno;
    private String scname;
    private String cdata;
    private String appdt;
    private String upddt;
    private String lputdt;
    private String llputdt;
    private String ulputdt;
    private String ullputdt;
    private String prtype;
    private String clsacno;
    private String clssbno;
    private String clsdtlno;
    private String ebtype;
    private String pwtype;
    private String feename;
    private String putname;
    private Map<String, String> textMap;
    private List<CltmrBus> cltmrBusList;

    @Override
    public void onApplicationEvent(QueryCltmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltmrLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryCltmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltmrLsnr run()");
        init(event);
        validateCltmr();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryCltmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltmr init");
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
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCltmr" + batchDate);
    }

    private void validateCltmr() {
        cltmrBusList = cltmrService.findAll(0, Integer.MAX_VALUE);
        if (Objects.isNull(cltmrBusList) || cltmrBusList.isEmpty()) {
            reportNoData();
        } else {
            queryCltmr();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryCltmr() {
        for (CltmrBus cltmrBus : cltmrBusList) {
            code = cltmrBus.getCode();
            atmcode = parse.decimal2String(cltmrBus.getAtmcode(), 3, 0);
            entpno = cltmrBus.getEntpno();
            hentpno = parse.decimal2String(cltmrBus.getHentpno(), 8, 0);
            scname = parse.decimal2String(cltmrBus.getScname(), 10, 0);
            cdata = parse.decimal2String(cltmrBus.getCdata(), 1, 0);
            appdt = parse.decimal2String(cltmrBus.getAppdt(), 8, 0);
            upddt = parse.decimal2String(cltmrBus.getUpddt(), 8, 0);
            lputdt = parse.decimal2String(cltmrBus.getLputdt(), 8, 0);
            llputdt = parse.decimal2String(cltmrBus.getLlputdt(), 8, 0);
            ulputdt = parse.decimal2String(cltmrBus.getUlputdt(), 8, 0);
            ullputdt = parse.decimal2String(cltmrBus.getUllputdt(), 8, 0);
            prtype = cltmrBus.getPrtype();
            clsacno = cltmrBus.getClsacno();
            clssbno = cltmrBus.getClssbno();
            clsdtlno = cltmrBus.getClsdtlno();
            ebtype = cltmrBus.getEbtype();
            pwtype = cltmrBus.getPwtype();
            feename = cltmrBus.getFeename();
            putname = cltmrBus.getPutname();
            reportFormat();
        }
    }

    private void reportFormat() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(formatUtil.padX(code, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(atmcode, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(entpno, 10));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(hentpno, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(scname, 10));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cdata, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(appdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(upddt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lputdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(llputdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(ulputdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(ullputdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(prtype, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(clsacno, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(clssbno, 4));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(clsdtlno, 4));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(ebtype, 10));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(pwtype, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(feename, 42));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putname, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 200));
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

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLTMR";
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

    private void batchResponse(QueryCltmr event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
