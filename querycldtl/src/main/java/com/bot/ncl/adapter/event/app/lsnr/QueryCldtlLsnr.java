/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryCldtl;
import com.bot.ncl.dto.entities.CldtlbyEntdyforCompareBus;
import com.bot.ncl.jpa.svc.CldtlService;
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
@Component("QueryCldtlLsnr")
@Scope("prototype")
public class QueryCldtlLsnr extends BatchListenerCase<QueryCldtl> {

    @Autowired private CldtlService cldtlService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryCldtl";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private String filePath;
    private String batchDate;
    private String code;
    private String amt;
    private String rcptid;
    private String txtype;
    private String cllbr;
    private String trmno;
    private String txtno;
    private String tlrno;
    private String entdy;
    private String time;
    private String lmtdate;
    private String userdata;
    private String sitdate;
    private String caldy;
    private String actno;
    private String serino;
    private String crdb;
    private String curcd;
    private String hcode;
    private String sourcetp;
    private String cactno;
    private String htxseq;
    private String empno;
    private String putfg;
    private String entfg;
    private String sourceip;
    private String uplfile;
    private String pbrno;
    private String cfee2;
    private String fkd;
    private String fee;
    private String feecost;
    private int pageIndex = 0;
    private Map<String, String> textMap;
    private List<CldtlbyEntdyforCompareBus> cldtlbyEntdyforCompareBusList;

    private String reportFilePath;

    @Override
    public void onApplicationEvent(QueryCldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QueryCldtlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryCldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldtl run()");
        init(event);
        validateCldtl();

        copyData();
        batchResponse(event);
    }

    private void init(QueryCldtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldtl init");
        filePath = fileDir + FILE_NAME;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        if (Objects.isNull(textMap)) {
            textMap = new HashMap<>();
        }
        batchDate = labelMap.get("BBSDY"); // 分行別
        reportFilePath = fileDir + "NCLCLDTL";
        filePath = fileDir + FILE_NAME;
        textFile.deleteFile(filePath);

        fileContents = new ArrayList<>();
        //  測試用日期
        if (batchDate == null) {
            batchDate = "1120704";
        }

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QueryCldtl" + batchDate);
    }

    private void validateCldtl() {

        int pageCnts = 100000;
        cldtlbyEntdyforCompareBusList =
                cldtlService.findbyEntdyforCompare(
                        parse.string2Integer(batchDate), 0, pageIndex, pageCnts);
        if (Objects.isNull(cldtlbyEntdyforCompareBusList)
                || cldtlbyEntdyforCompareBusList.isEmpty()) {
            if (pageIndex == 0) {
                reportNodata();
            }
            return;
        } else {
            queryCldtl();
            // 當此頁筆數與每頁筆數相同時,視為可能還有資料,遞迴處理
            if (cldtlbyEntdyforCompareBusList.size() == pageCnts) {
                pageIndex++;
                validateCldtl(); // next page
            } else {
                // 已無資料,將剩餘資料寫入檔案
                if (!fileContents.isEmpty()) {
                    writeFile();
                    fileContents = new ArrayList<>();
                }
            }
        }
    }

    private void reportNodata() {
        fileContents.add(" ");
    }

    private void queryCldtl() {
        for (CldtlbyEntdyforCompareBus cldtlbyEntdyforCompareBus : cldtlbyEntdyforCompareBusList) {
            entdy = parse.decimal2String(cldtlbyEntdyforCompareBus.getEntdy(), 8, 0);
            // TODO: 2025/2/20 暫時改等於
            code = cldtlbyEntdyforCompareBus.getCode();
            amt = parse.decimal2String(cldtlbyEntdyforCompareBus.getAmt(), 11, 2);
            rcptid = cldtlbyEntdyforCompareBus.getRcptid();
            txtype = cldtlbyEntdyforCompareBus.getTxtype();
            cllbr = parse.decimal2String(cldtlbyEntdyforCompareBus.getCllbr(), 3, 0);
            trmno = parse.decimal2String(cldtlbyEntdyforCompareBus.getTrmno(), 7, 0);
            txtno = parse.decimal2String(cldtlbyEntdyforCompareBus.getTxtno(), 8, 0);
            tlrno = cldtlbyEntdyforCompareBus.getTlrno();
            time = parse.decimal2String(cldtlbyEntdyforCompareBus.getTime(), 6, 0);
            lmtdate = parse.decimal2String(cldtlbyEntdyforCompareBus.getLmtdate(), 8, 0);
            userdata = cldtlbyEntdyforCompareBus.getUserdata();
            sitdate = parse.decimal2String(cldtlbyEntdyforCompareBus.getSitdate(), 8, 0);
            caldy = parse.decimal2String(cldtlbyEntdyforCompareBus.getCaldy(), 8, 0);
            actno = cldtlbyEntdyforCompareBus.getActno();
            serino = parse.decimal2String(cldtlbyEntdyforCompareBus.getSerino(), 6, 0);
            crdb = parse.decimal2String(cldtlbyEntdyforCompareBus.getCrdb(), 1, 0);
            curcd = parse.decimal2String(cldtlbyEntdyforCompareBus.getCurcd(), 2, 0);
            hcode = parse.decimal2String(cldtlbyEntdyforCompareBus.getHcode(), 1, 0);
            sourcetp = cldtlbyEntdyforCompareBus.getSourcetp();
            cactno = cldtlbyEntdyforCompareBus.getCactno();
            htxseq = parse.decimal2String(cldtlbyEntdyforCompareBus.getHtxseq(), 15, 0);
            empno = parse.decimal2String(cldtlbyEntdyforCompareBus.getEmpno(), 6, 0);
            putfg = parse.decimal2String(cldtlbyEntdyforCompareBus.getPutfg(), 1, 0);
            entfg = parse.decimal2String(cldtlbyEntdyforCompareBus.getEntfg(), 1, 0);
            sourceip = cldtlbyEntdyforCompareBus.getSourceip();
            uplfile = cldtlbyEntdyforCompareBus.getUplfile();
            pbrno = parse.decimal2String(cldtlbyEntdyforCompareBus.getPbrno(), 3, 0);
            cfee2 = parse.decimal2String(cldtlbyEntdyforCompareBus.getCfee2(), 3, 2);
            fkd = parse.decimal2String(cldtlbyEntdyforCompareBus.getFkd(), 1, 0);
            fee = parse.decimal2String(cldtlbyEntdyforCompareBus.getFee(), 3, 2);
            feecost = parse.decimal2String(cldtlbyEntdyforCompareBus.getFeecost(), 3, 2);
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
        stringBuilder.append(formatUtil.pad9(amt, 14));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(rcptid, 26));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(txtype, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cllbr, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(trmno, 7));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(txtno, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(tlrno, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(entdy, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(time, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(lmtdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(userdata, 100));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(sitdate, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(caldy, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(actno, 20));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(serino, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(crdb, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(curcd, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(hcode, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(sourcetp, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(cactno, 16));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(htxseq, 15));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(empno, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(putfg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(entfg, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(sourceip, 40));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(uplfile, 40));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(pbrno, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee2, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(fkd, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(fee, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(feecost, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 500));
    }

    private void copyData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "copyData()");
        List<String> cldtlList = textFile.readFileContent(filePath, CHARSET);
        textFile.deleteFile(reportFilePath);
        textFile.writeFileContent(reportFilePath, cldtlList, CHARSET);
        upload(reportFilePath);
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "CHKDB";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse(QueryCldtl event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
