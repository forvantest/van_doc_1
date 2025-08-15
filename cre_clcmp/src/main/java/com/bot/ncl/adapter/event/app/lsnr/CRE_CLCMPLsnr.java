/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRE_CLCMP;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.jpa.svc.ClcmpService;
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
@Component("CRE_CLCMPLsnr")
@Scope("prototype")
public class CRE_CLCMPLsnr extends BatchListenerCase<CRE_CLCMP> {

    @Autowired private ClcmpService clcmpService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "CRE_CLCMP";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private List<ClcmpBus> clcmpBusList;
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
    public void onApplicationEvent(CRE_CLCMP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLCMPLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRE_CLCMP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLCMP run()");
        init(event);
        validateClcmp();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(CRE_CLCMP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLCMP init");
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

        lcaday = "0";
        lasttime = "0";

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), FILE_NAME + batchDate);
    }

    private void validateClcmp() {

        clcmpBusList = clcmpService.findAll(0, Integer.MAX_VALUE);
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
        for (ClcmpBus clcmpBus : clcmpBusList) {
            code = clcmpBus.getCode();
            rcptid = clcmpBus.getRcptid();
            id = clcmpBus.getId();
            pname = clcmpBus.getPname();
            curcd = parse.decimal2String(clcmpBus.getCurcd(), 2, 0);
            amt = parse.decimal2String(clcmpBus.getAmt(), 11, 2);
            sdate = parse.decimal2String(clcmpBus.getSdate(), 8, 0);
            stime = parse.decimal2String(clcmpBus.getStime(), 6, 0);
            ldate = parse.decimal2String(clcmpBus.getLdate(), 8, 0);
            ltime = parse.decimal2String(clcmpBus.getLtime(), 6, 0);
            kdate = parse.decimal2String(clcmpBus.getKdate(), 8, 0);
            lflg = parse.decimal2String(clcmpBus.getLflg(), 1, 0);
            cdate = parse.decimal2String(clcmpBus.getCdate(), 8, 0);
            udate = parse.decimal2String(clcmpBus.getUdate(), 8, 0);
            utime = parse.decimal2String(clcmpBus.getUtime(), 6, 0);
            kinbr = parse.decimal2String(clcmpBus.getKinbr(), 3, 0);
            tlrno = clcmpBus.getTlrno();
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
        stringBuilder.append(formatUtil.pad9(amt, 14));
        stringBuilder.append(formatUtil.pad9(sdate, 8));
        stringBuilder.append(formatUtil.pad9(stime, 6));
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

        String reportFilePath = fileDir + "CRE_CLCMP_" + batchDate.substring(1) + ".TXT";
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

    private void batchResponse(CRE_CLCMP event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
