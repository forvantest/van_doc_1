/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryClmr;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
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
@Component("QueryClmrLsnr")
@Scope("prototype")
public class QueryClmrLsnr extends BatchListenerCase<QueryClmr> {

    @Autowired private ClmrService clmrService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryClmr";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private String batchDate;
    private String filePath;
    private String code;
    private String pbrno;
    private String vrcode;
    private String riddup;
    private String dupcyc;
    private String actno;
    private String msg1;
    private String puttime;
    private String subfg;
    private String chktype;
    private String chkamt;
    private String unit;
    private String amtcyc;
    private String amtfg;
    private String amt;
    private String cname;
    private String stop;
    private String holdcnt;
    private String holdcnt2;
    private String afcbv;
    private String netinfo;
    private String print;
    private String stdate;
    private String sttime;
    private String stopdate;
    private String stoptime;
    private String crdb;
    private String hcode;
    private String lkcode;
    private String flag;
    private String otherfield;
    private Map<String, String> textMap;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private List<ClmrBus> clmrBusList;

    @Override
    public void onApplicationEvent(QueryClmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryClmrLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryClmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmr run()");
        init(event);
        validateClmr();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(QueryClmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmr init");
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

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryClmr" + batchDate);
    }

    private void validateClmr() {
        clmrBusList = clmrService.findAll(0, Integer.MAX_VALUE);
        if (Objects.isNull(clmrBusList) || clmrBusList.isEmpty()) {
            reportNoData();
        } else {
            queryClmr();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryClmr() {
        for (ClmrBus clmrBus : clmrBusList) {
            code = clmrBus.getCode();
            pbrno = parse.decimal2String(clmrBus.getPbrno(), 3, 0);
            vrcode = parse.decimal2String(clmrBus.getVrcode(), 4, 0);
            riddup = parse.decimal2String(clmrBus.getRiddup(), 1, 0);
            dupcyc = parse.decimal2String(clmrBus.getDupcyc(), 3, 0);
            actno = parse.decimal2String(clmrBus.getActno(), 12, 0);
            msg1 = parse.decimal2String(clmrBus.getMsg1(), 1, 0);
            puttime = parse.decimal2String(clmrBus.getPuttime(), 1, 0);
            subfg = parse.decimal2String(clmrBus.getSubfg(), 1, 0);
            chktype = clmrBus.getChktype();
            chkamt = parse.decimal2String(clmrBus.getChkamt(), 1, 0);
            unit = parse.decimal2String(clmrBus.getUnit(), 8, 2);
            amtcyc = parse.decimal2String(clmrBus.getAmtcyc(), 1, 0);
            amtfg = parse.decimal2String(clmrBus.getAmtfg(), 1, 0);
            amt = parse.decimal2String(clmrBus.getAmt(), 15, 2);
            cname = clmrBus.getCname();
            stop = parse.decimal2String(clmrBus.getStop(), 2, 0);
            holdcnt = parse.decimal2String(clmrBus.getHoldcnt(), 3, 0);
            holdcnt2 = parse.decimal2String(clmrBus.getHoldcnt2(), 3, 0);
            afcbv = parse.decimal2String(clmrBus.getAfcbv(), 1, 0);
            netinfo = clmrBus.getNetinfo();
            print = parse.decimal2String(clmrBus.getPrint(), 2, 0);
            stdate = parse.decimal2String(clmrBus.getStdate(), 8, 0);
            sttime = parse.decimal2String(clmrBus.getSttime(), 6, 0);
            stopdate = parse.decimal2String(clmrBus.getStopdate(), 8, 0);
            stoptime = parse.decimal2String(clmrBus.getStoptime(), 6, 0);
            crdb = parse.decimal2String(clmrBus.getCrdb(), 1, 0);
            hcode = parse.decimal2String(clmrBus.getHcode(), 1, 0);
            lkcode = clmrBus.getLkcode();
            flag = clmrBus.getFlag();
            otherfield = clmrBus.getOtherField();
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
        stringBuilder.append(formatUtil.pad9(pbrno, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(vrcode, 4));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(riddup, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(dupcyc, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(actno, 12));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(msg1, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(puttime, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(subfg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(chktype, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(chkamt, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(unit, 11));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(amtcyc, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(amtfg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(amt, 18));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(cname, 40));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stop, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(holdcnt, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(holdcnt2, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(afcbv, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(netinfo, 20));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(print, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(sttime, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stopdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(stoptime, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(crdb, 1));
        stringBuilder.append(formatUtil.pad9("^", 1));
        stringBuilder.append(formatUtil.pad9(hcode, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(lkcode, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(flag, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(otherfield, 500));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 700));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "NCLCLMR";
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

    private void batchResponse(QueryClmr event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
