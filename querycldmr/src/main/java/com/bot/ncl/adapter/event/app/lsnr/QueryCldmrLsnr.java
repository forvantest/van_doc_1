/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryCldmr;
import com.bot.ncl.jpa.anq.svc.cm.QueryCldmrService;
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
@Component("QueryCldmrLsnr")
@Scope("prototype")
public class QueryCldmrLsnr extends BatchListenerCase<QueryCldmr> {

    @Autowired private QueryCldmrService queryCldmrService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryCldmr";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
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
    private Map<String, String> textMap;
    private List<com.bot.ncl.jpa.anq.entities.impl.cm.QueryCldmr> cldmrBusList;

    @Override
    public void onApplicationEvent(QueryCldmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryCldmrLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryCldmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldmr run()");
        init(event);
        validateCldmr();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryCldmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldmr init");
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

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldmr" + batchDate);
    }

    private void validateCldmr() {
        cldmrBusList = queryCldmrService.queryCldtlCompare();
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
        for (com.bot.ncl.jpa.anq.entities.impl.cm.QueryCldmr cldmrBus : cldmrBusList) {
            code = cldmrBus.getQueryCldmrId().getCode();
            rcptid = cldmrBus.getQueryCldmrId().getRcptid();
            id = cldmrBus.getId();
            pname = cldmrBus.getPname();
            curcd = cldmrBus.getQueryCldmrId().getCurcd();
            BigDecimal wkBal = parse.string2BigDecimal(cldmrBus.getBal());
            bal = parse.decimal2String(wkBal, 11, 2);
            ldate = cldmrBus.getLdate();
            ltime = cldmrBus.getLtime();
            kdate = cldmrBus.getKdate();
            lflg = cldmrBus.getLflg();
            cdate = cldmrBus.getCdate();
            udate = cldmrBus.getUdate();
            utime = cldmrBus.getUtime();
            kinbr = cldmrBus.getKinbr();
            tlrno = cldmrBus.getTlrno();
            empno = cldmrBus.getEmpno();
            if (Objects.isNull(cldmrBus.getCaldy()) || cldmrBus.getCaldy().isEmpty()) {
                lcaday = "0";
            } else {
                lcaday = parse.decimal2String(cldmrBus.getCaldy(), 8, 0);
            }
            if (Objects.isNull(cldmrBus.getTime()) || cldmrBus.getTime().isEmpty()) {
                lasttime = "0";
            } else {
                lasttime = parse.decimal2String(cldmrBus.getTime(), 6, 0);
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
        stringBuilder.append(formatUtil.padX(rcptid, 16));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(id, 10));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(pname, 30));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(curcd, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(bal, 14));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(ldate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(ltime, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(kdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lflg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(udate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(utime, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(kinbr, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(tlrno, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(empno, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lcaday, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lasttime, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 200));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLDMR";
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

    private void batchResponse(QueryCldmr event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
