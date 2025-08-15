/* (C) 2024 */
package com.bot.ncl.diff;

import com.bot.ncl.diff.layout.model.Column;
import com.bot.ncl.diff.layout.model.Layout;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class DiffCom {

    @Autowired private LoadDataCom loadDataCom;

    @Autowired private CompareCom compareCom;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private BatchUtil batchUtil;
    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Getter private List<Map<String, String>> insertList;
    @Getter private List<Map<String, Map<String, String>>> updateList;
    @Getter private List<Map<String, String>> deleteList;

    @Getter private Date startTime;

    private int txtnoMaxNo = 90000000;

    private List<String> recordList;

    private List<String> compareRecord;

    private List<Map<String, Map<String, String>>> detailList;

    private Layout layout;

    private Map<String, String[]> sourceData;

    private Map<String, String[]> targetData;

    private boolean isSame;

    private List<String[]> targetLessList;

    private List<String> keyList;

    private DiffCom() {
        // YOU SHOULD USE @Autowired ,NOT new DiffCom()
    }

    public String diff(
            String sourceFile,
            String targetFile,
            Layout layout,
            TransactionCase batchTransaction,
            Date startTime) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "diff()");
        init();
        String result = "SAME";
        this.layout = layout;
        recordList = new ArrayList<>();
        detailList = new ArrayList<>();
        compareRecord = new ArrayList<>();

        keyList = layout.getKey();

        sourceData =
                loadDataCom.loadData(
                        "source",
                        sourceFile,
                        layout.getTableName(),
                        layout.getColumns(),
                        layout.getKey(),
                        layout.getDelimiter(),
                        "BUR"); // 來源檔固定是優利碼
        recordList.addAll(loadDataCom.getRecordList());

        targetData =
                loadDataCom.loadData(
                        "target",
                        targetFile,
                        layout.getTableName(),
                        layout.getColumns(),
                        layout.getKey(),
                        layout.getDelimiter(),
                        layout.getCharset());
        recordList.addAll(loadDataCom.getRecordList());

        int sourceCount = sourceData.size();
        int targetCount = targetData.size();
        recordList.add("檔案總筆數");
        recordList.add("來源檔:" + sourceCount);
        recordList.add("目標檔:" + targetCount);
        recordList.add("=====");

        isSame = true;
        int targetTxtno;
        if ("CLDTL".equals(layout.getTableName())) {
            for (String key : targetData.keySet()) {
                String[] values = targetData.get(key);
                targetTxtno = parse.string2Integer(values[6]);
                if (targetTxtno > txtnoMaxNo) {
                    txtnoMaxNo = targetTxtno;
                }
            }
        }
        targetLessList = new ArrayList<>();
        for (String key : sourceData.keySet()) {
            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "檢查KEY={}", key);
            if (!targetData.containsKey(key)) {
                ApLogHelper.warn(log, false, LogType.NORMAL.getCode(), "目標檔缺少KEY={}", key);
                String[] values = sourceData.get(key);
                if ("CLDTL".equals(layout.getTableName())) {
                    txtnoMaxNo++;
                    values[6] = "" + txtnoMaxNo;
                }

                targetLessList.add(values);
                isSame = false;
                continue;
            }
            if (!compareCom.compareRecords(
                    layout.getTableName(),
                    sourceData.get(key),
                    targetData.get(key),
                    layout.getColumns(),
                    layout.getKey().toString(),
                    key)) {
                isSame = false;
                compareRecord.addAll(compareCom.getCompareRecord());
                addToDetailList(key);
            } else if (!compareCom.getDetailDiffMap().isEmpty()) {
                addToDetailList(key);
            }
            this.startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        }

        checkTargetLessData();

        checkTargetMoreData();

        checkDetailList();

        if (isSame) {
            recordList.add("資料相同");
            result = "SAME";
        } else {
            recordList.add("資料有差異");
            result = "DIFF";
        }
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);
        String recordFilePath =
                fileDir + "DIFF_" + layout.getTableName() + "_RECORD_" + timestamp + ".txt";
        textFileUtil.writeFileContent(recordFilePath, recordList, "UTF-8");
        Path path = Paths.get(recordFilePath);
        File file = path.toFile();
        String uploadPath = "/TF001/";
        fsapSyncSftpService.uploadFile(file, uploadPath);
        return result;
    }

    private void addToDetailList(String key) {
        Map<String, Map<String, String>> detailMap = new HashMap<>();
        Map<String, String> updateKey = new HashMap<>();
        String[] keys = key.contains("_") ? key.split("_") : new String[] {key};
        if (keyList.size() != keys.length) {
            ApLogHelper.warn(log, false, LogType.NORMAL.getCode(), "無法解析主鍵={}", key);
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            String kName = keyList.get(i);
            String kValue = keys[i];
            updateKey.put(kName, kValue);
        }
        if ("CLDTL".equals(layout.getTableName())) {
            String[] v = targetData.get(key);
            int i = 0;
            for (Column column : layout.getColumns()) {
                if (i >= v.length) {
                    break;
                }
                if ("TXTNO".equals(column.getName())) {
                    updateKey.put(column.getName(), v[i]);
                    break;
                }
                i++;
            }
        }
        detailMap.put("UPDATE_KEY", updateKey);
        detailMap.put("UPDATE_VALUE", compareCom.getDetailDiffMap());
        detailList.add(detailMap);
    }

    private void checkTargetLessData() {
        if (!targetLessList.isEmpty()) {
            recordList.add("FAS有,NCL缺少,共" + targetLessList.size() + "筆");
            for (String[] values : targetLessList) {
                Map<String, String> insertMap = new HashMap<>();
                List<Column> columns = layout.getColumns();
                int i = 0;
                for (Column column : columns) {
                    if (i >= values.length) {
                        break;
                    }
                    insertMap.put(column.getName(), values[i]);
                    i++;
                }
                recordList.add("FAS有,NCL缺少,insertMap=" + insertMap);
                insertList.add(insertMap);
            }
            recordList.add("=====");
        }
    }

    private void checkTargetMoreData() {
        List<String[]> targetMoreList = new ArrayList<>();
        for (String key : targetData.keySet()) {
            if (!sourceData.containsKey(key)) {
                ApLogHelper.warn(log, false, LogType.NORMAL.getCode(), "來源檔缺少KEY={}", key);
                String[] values = targetData.get(key);
                targetMoreList.add(values);
                isSame = false;
                continue;
            }
        }
        if (!targetMoreList.isEmpty()) {
            recordList.add("FAS沒有,NCL多的,共" + targetMoreList.size() + "筆");
            List<String> keys = layout.getKey();
            List<Column> columns = layout.getColumns();
            Map<String, String> keyMap = new HashMap<>();
            for (String key : keys) {
                keyMap.put(key, key);
            }
            for (String[] values : targetMoreList) {
                Map<String, String> deleteMap = new HashMap<>();
                int i = 0;
                for (Column column : columns) {
                    if (keyMap.containsKey(column.getName())) {
                        if (values == null || i >= values.length) {
                            break;
                        }
                        deleteMap.put(column.getName(), values[i]);
                    }
                    if ("CLDTL".equals(layout.getTableName())) {
                        if ("TXTNO".equals(column.getName())) {
                            deleteMap.put(column.getName(), values[i]);
                        }
                    }
                    i++;
                }
                recordList.add("FAS沒有,NCL多的,deleteMap=" + deleteMap);
                deleteList.add(deleteMap);
            }
            recordList.add("=====");
        }
    }

    private void checkDetailList() {
        if (!detailList.isEmpty()) {
            recordList.add("欄位內容有差異,共" + detailList.size() + "筆");
            updateList.addAll(detailList);
            recordList.addAll(compareRecord);
            recordList.add("=====");
        }
    }

    private void init() {
        insertList = new ArrayList<>();
        updateList = new ArrayList<>();
        deleteList = new ArrayList<>();
    }
}
