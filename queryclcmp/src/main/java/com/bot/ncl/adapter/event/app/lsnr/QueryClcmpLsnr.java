/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryClcmp;
import com.bot.ncl.jpa.anq.svc.cm.QueryClcmpService;
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
@Component("QueryClcmpLsnr")
@Scope("prototype")
public class QueryClcmpLsnr extends BatchListenerCase<QueryClcmp> {

    @Autowired private QueryClcmpService queryClcmpService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryClcmp";

    private static final String CHARSET = "Big5";

    private List<com.bot.ncl.jpa.anq.entities.impl.cm.QueryClcmp> clcmpBusList;
    private List<String> fileContents;
    private Map<String, String> textMap;
    private String filePath;
    private String code;
    private String rcptid;
    private String id;
    private String pname;
    private String curcd;
    private String amt;
    private String sdate;
    private String stime;
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
    private String otflag;
    private String lcaday;
    private String lasttime;
    private String batchDate;

    @Override
    public void onApplicationEvent(QueryClcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryClcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryClcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClcmp run()");
        init(event);
        validateClcmp();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryClcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClcmp init");
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

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), FILE_NAME + batchDate);
    }

    private void validateClcmp() {

        clcmpBusList = queryClcmpService.queryCldtlCompare();
        if (Objects.isNull(clcmpBusList) || clcmpBusList.isEmpty()) {
            reportNodata();
        } else {
            queryClcmp();
        }
    }

    private void reportNodata() {
        fileContents.add("");
    }

    private void queryClcmp() {
        for (com.bot.ncl.jpa.anq.entities.impl.cm.QueryClcmp clcmpBus : clcmpBusList) {
            code = clcmpBus.getQueryClcmpId().getCode();
            rcptid = clcmpBus.getQueryClcmpId().getRcptid();
            id = clcmpBus.getId();
            pname = clcmpBus.getPname();
            curcd = clcmpBus.getCurcd();
            BigDecimal wkAmt = parse.string2BigDecimal(clcmpBus.getAmt());
            amt = parse.decimal2String(wkAmt, 11, 2);
            sdate = clcmpBus.getSdate();
            stime = clcmpBus.getStime();
            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "SDATE = " + sdate);
            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "STIME = " + stime);
            ldate = clcmpBus.getLdate();
            ltime = clcmpBus.getLtime();
            kdate = clcmpBus.getKdate();
            lflg = clcmpBus.getLflg();
            cdate = clcmpBus.getCdate();
            udate = clcmpBus.getUdate();
            utime = clcmpBus.getUtime();
            kinbr = clcmpBus.getKinbr();
            tlrno = clcmpBus.getTlrno();
            empno = clcmpBus.getEmpno();
            otflag = clcmpBus.getOtflag();
            if (Objects.isNull(clcmpBus.getCaldy()) || clcmpBus.getCaldy().isEmpty()) {
                lcaday = "0";
            } else {
                lcaday = clcmpBus.getCaldy();
            }
            if (Objects.isNull(clcmpBus.getTime()) || clcmpBus.getTime().isEmpty()) {
                lasttime = "0";
            } else {
                lasttime = clcmpBus.getTime();
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
        stringBuilder.append(formatUtil.pad9(amt, 14));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(sdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stime, 6));
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
        stringBuilder.append(formatUtil.padX(otflag, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lcaday, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lasttime, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 200));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLCMP";
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

    private void batchResponse(QueryClcmp event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
