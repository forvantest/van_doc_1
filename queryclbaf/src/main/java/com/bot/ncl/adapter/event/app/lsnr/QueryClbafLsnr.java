/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.QueryClbaf;
import com.bot.ncl.dto.entities.ClbafbyEntdyforCompareBus;
import com.bot.ncl.jpa.svc.ClbafService;
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
@Component("QueryClbafLsnr")
@Scope("prototype")
public class QueryClbafLsnr extends BatchListenerCase<QueryClbaf> {

    @Autowired private ClbafService clbafService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private QueryClbaf event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "queryClbaf";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;
    private Map<String, String> labelMap;
    private String filePath;
    private String batchDate;
    private String cllbr;
    private String entdy;
    private String code;
    private String pbrno;
    private String crdb;
    private String txtype;
    private String curcd;
    private String cnt;
    private String amt;
    private String cfee2;
    private String kfee;
    private int pageIndex = 0;
    private String reportFilePath;
    private Map<String, String> textMap;
    private List<ClbafbyEntdyforCompareBus> clbafbyEntdyforCompareBusList;

    @Override
    public void onApplicationEvent(QueryClbaf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in QUERYCLBAFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(QueryClbaf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QUERYCLBAF run()");
        init(event);
        validateClbaf();
        copyData();
        batchResponse(event);
    }

    private void init(QueryClbaf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QUERYCLBAF init");
        filePath = fileDir + FILE_NAME;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        if (Objects.isNull(textMap)) {
            textMap = new HashMap<>();
        }

        batchDate = labelMap.get("BBSDY"); // 分行別

        filePath = fileDir + FILE_NAME;
        reportFilePath = fileDir + "NCLCLBAF";
        textFile.deleteFile(filePath);
        textFile.deleteFile(reportFilePath);

        fileContents = new ArrayList<>();
        //  測試用日期
        if (batchDate == null) {
            batchDate = "1120704";
        }

        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "QUERYCLBAF" + batchDate);
    }

    private void validateClbaf() {
        int pageCnts = 100000;
        clbafbyEntdyforCompareBusList =
                clbafService.findbyEntdyforCompare(
                        parse.string2Integer(batchDate), pageIndex, pageCnts);
        if (Objects.isNull(clbafbyEntdyforCompareBusList)
                || clbafbyEntdyforCompareBusList.isEmpty()) {
            if (pageIndex == 0) {
                reportNoData();
            }
            return;
        } else {
            queryClbaf();
            // 當此頁筆數與每頁筆數相同時,視為可能還有資料,遞迴處理
            if (clbafbyEntdyforCompareBusList.size() == pageCnts) {
                pageIndex++;
                if (!fileContents.isEmpty()) {
                    writeFile();
                    fileContents = new ArrayList<>();
                }
                validateClbaf(); // next page
            } else {
                // 已無資料,將剩餘資料寫入檔案
                if (!fileContents.isEmpty()) {
                    writeFile();
                    fileContents = new ArrayList<>();
                }
            }
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryClbaf() {
        for (ClbafbyEntdyforCompareBus clbafbyEntdyforCompareBus : clbafbyEntdyforCompareBusList) {
            entdy = parse.decimal2String(clbafbyEntdyforCompareBus.getEntdy(), 8, 0);

            cllbr = parse.decimal2String(clbafbyEntdyforCompareBus.getCllbr(), 3, 0);
            code = clbafbyEntdyforCompareBus.getCode();
            pbrno = parse.decimal2String(clbafbyEntdyforCompareBus.getPbrno(), 3, 0);
            crdb = parse.decimal2String(clbafbyEntdyforCompareBus.getCrdb(), 1, 0);
            txtype = clbafbyEntdyforCompareBus.getTxtype();
            curcd = parse.decimal2String(clbafbyEntdyforCompareBus.getCurcd(), 2, 0);
            cnt = parse.decimal2String(clbafbyEntdyforCompareBus.getCnt(), 5, 0);
            amt = parse.decimal2String(clbafbyEntdyforCompareBus.getAmt(), 13, 2);
            cfee2 = parse.decimal2String(clbafbyEntdyforCompareBus.getCfee2(), 6, 2);
            kfee = parse.decimal2String(clbafbyEntdyforCompareBus.getKfee(), 8, 2);
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
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, e);
    }

    private void reportFormat() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(formatUtil.pad9(cllbr, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(entdy, 8));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(code, 6));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(pbrno, 3));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(crdb, 1));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.padX(txtype, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(curcd, 2));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cnt, 5));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(amt, 16));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(cfee2, 9));
        stringBuilder.append(formatUtil.padX("^", 1));
        stringBuilder.append(formatUtil.pad9(kfee, 11));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 100));
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "CHKDB";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void copyData() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "copyData()");
        List<String> clbafList = textFile.readFileContent(filePath, CHARSET);
        textFile.deleteFile(reportFilePath);
        textFile.writeFileContent(reportFilePath, clbafList, CHARSET);
        upload(reportFilePath);
    }

    private void batchResponse(QueryClbaf event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
