/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryClmc;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.jpa.svc.ClmcService;
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
@Component("QueryClmcLsnr")
@Scope("prototype")
public class QueryClmcLsnr extends BatchListenerCase<QueryClmc> {

    @Autowired private ClmcService clmcService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryClmc";
    private static final String CHARSET = "Big5";
    private List<String> fileContents;
    private String batchDate;
    private String filePath;
    private String putname;
    private String putsend;
    private String putform;
    private String puttype;
    private String putaddr;
    private String putEncode;
    private String putCompress;
    private String oputtime;
    private String cyck1;
    private String cycno1;
    private String cyck2;
    private String cycno2;
    private String putdtfg;
    private String putdt;
    private String msg2;
    private String tputdt;
    private String usecnt;
    private Map<String, String> textMap;
    private List<ClmcBus> clmcBusList;

    @Override
    public void onApplicationEvent(QueryClmc event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryClmcLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryClmc event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmc run()");
        init(event);
        validateClmc();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryClmc event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmc init");
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

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmc" + batchDate);
    }

    private void validateClmc() {
        clmcBusList = clmcService.findAll(0, Integer.MAX_VALUE);
        if (Objects.isNull(clmcBusList) || clmcBusList.isEmpty()) {
            reportNoData();
        } else {
            queryClmc();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryClmc() {
        for (ClmcBus clmcBus : clmcBusList) {
            putname = clmcBus.getPutname();
            putsend = clmcBus.getPutsend();
            putform = clmcBus.getPutform();
            puttype = parse.decimal2String(clmcBus.getPuttype(), 2, 0);
            putaddr = clmcBus.getPutaddr();
            putEncode = clmcBus.getPutEncode();
            putCompress = clmcBus.getPutCompress();
            oputtime = clmcBus.getOputtime();
            cyck1 = parse.decimal2String(clmcBus.getCyck1(), 1, 0);
            cycno1 = parse.decimal2String(clmcBus.getCycno1(), 2, 0);
            cyck2 = parse.decimal2String(clmcBus.getCyck2(), 1, 0);
            cycno2 = parse.decimal2String(clmcBus.getCycno2(), 2, 0);
            putdtfg = parse.decimal2String(clmcBus.getPutdtfg(), 1, 0);
            putdt = parse.decimal2String(clmcBus.getPutdt(), 8, 0);
            msg2 = parse.decimal2String(clmcBus.getMsg2(), 1, 0);
            tputdt = parse.decimal2String(clmcBus.getTputdt(), 8, 0);
            usecnt = parse.decimal2String(clmcBus.getUsecnt(), 4, 0);
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
        stringBuilder.append(formatUtil.padX(putname, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putsend, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putform, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(puttype, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putaddr, 100));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putEncode, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(putCompress, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(oputtime, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cyck1, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cycno1, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cyck2, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cycno2, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(putdtfg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(putdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(msg2, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(tputdt, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(usecnt, 4));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 200));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLMC";
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

    private void batchResponse(QueryClmc event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
