/* (C) 2024 */
package com.bot.ncl.util;

import com.bot.ncl.diff.DiffCom;
import com.bot.ncl.diff.layout.components.*;
import com.bot.ncl.diff.layout.model.Layout;
import com.bot.ncl.modify.*;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class DataDiffUtil {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private List<String> lModifyType = Arrays.asList("Insert", "Update", "Delete");

    @Autowired private DiffCom diffCom;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private ModifyClmr modifyClmr;
    @Autowired private ModifyCltmr modifyCltmr;
    @Autowired private ModifyClmc modifyClmc;
    @Autowired private ModifyClfee modifyClfee;
    @Autowired private ModifyCltot modifyCltot;
    @Autowired private ModifyClbaf modifyClbaf;
    @Autowired private ModifyClcmp modifyClcmp;
    @Autowired private ModifyCldmr modifyCldmr;
    @Autowired private ModifyCldtl modifyCldtl;

    @Getter private Date startTime;

    private List<Map<String, String>> insertList;
    private List<Map<String, Map<String, String>>> updateList;
    private List<Map<String, String>> deleteList;

    private DataDiffUtil() {
        // YOU SHOULD USE @Autowired ,NOT new DataDiffUtil()
    }

    public Map<String, String> diffFile(
            String sourceFile,
            String targetFile,
            String tableName,
            TransactionCase batchTransaction,
            Date startTime,
            String updFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "diffFile");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sourceFile={}", sourceFile);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "targetFile={}", targetFile);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tableName={}", tableName);
        Map<String, String> result = new HashMap<>();
        result.put("STATUS", "start");

        // Step 1: 檢查檔案是否存在
        String sourceFileExist = checkFile(sourceFile);
        result.put("sourceFileExist", sourceFileExist);

        String targetFileExist = checkFile(targetFile);
        result.put("targetFileExist", targetFileExist);

        if (sourceFileExist.equals("N") || targetFileExist.equals("N")) {
            result.put("STATUS", "failed");
            result.put("ERROR_STEP", "fileExist");
            return result;
        }

        // Step 2: 檢查是否為已設定好的tableName
        String checkTableName = checkTableName(tableName);
        result.put("checkTableName", checkTableName);

        if (checkTableName.equals("N")) {
            result.put("STATUS", "failed");
            result.put("ERROR_STEP", "checkTableName");
            return result;
        }

        // Step 3: 比較差異
        String diffFileByTable =
                diffFileByTable(sourceFile, targetFile, tableName, batchTransaction, startTime);
        result.put("diffFileByTable", diffFileByTable);

        if (diffFileByTable.equals("N")) {
            result.put("STATUS", "failed");
            result.put("ERROR_STEP", "diffFileByTable");
            return result;
        }

        if (diffFileByTable.equals("DIFF") && "Y".equals(updFg)) {
            // Step 4: 更新資料庫(新增/修改/刪除)
            String modifyDbByTable = modifyDbByTable(tableName, batchTransaction, startTime);
        }

        // Step 5: 回傳結果

        result.put("STATUS", "finish");

        return result;
    }

    private String checkFile(String filePath) {
        String result = "Y";
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            result = "N";
        }
        return result;
    }

    private String checkTableName(String tableName) {
        String result = "Y";
        switch (tableName) {
            case "CLMR":
            case "CLTMR":
            case "CLTOT":
            case "CLFEE":
            case "CLMC":
            case "CLBAF":
            case "CLCMP":
            case "CLDMR":
            case "CLDTL":
            case "PUTH":
                break;
            default:
                result = "N";
                break;
        }
        return result;
    }

    private String diffFileByTable(
            String sourceFile,
            String targetFile,
            String tableName,
            TransactionCase batchTransaction,
            Date startTime) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "diffFileByTable() tableName = {}",
                tableName);
        String result = "Y";
        Layout layout = null;
        switch (tableName) {
            case "CLMR":
                layout = new LayoutClmr();
                break;
            case "CLTMR":
                layout = new LayoutCltmr();
                break;
            case "CLTOT":
                layout = new LayoutCltot();
                break;
            case "CLFEE":
                layout = new LayoutClfee();
                break;
            case "CLMC":
                layout = new LayoutClmc();
                break;
            case "CLBAF":
                layout = new LayoutClbaf();
                break;
            case "CLCMP":
                layout = new LayoutClcmp();
                break;
            case "CLDMR":
                layout = new LayoutCldmr();
                break;
            case "CLDTL":
                layout = new LayoutCldtl();
                break;
            case "PUTH":
                layout = new LayoutPuth();
                break;
            default:
                result = "N";
                break;
        }
        if (!Objects.isNull(layout)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sourceFile = {}", sourceFile);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "targetFile = {}", targetFile);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "layout = {}", layout);
            result = diffCom.diff(sourceFile, targetFile, layout, batchTransaction, startTime);
            this.startTime = diffCom.getStartTime();
            insertList = diffCom.getInsertList();
            updateList = diffCom.getUpdateList();
            deleteList = diffCom.getDeleteList();
        }
        return result;
    }

    private String modifyDbByTable(
            String tableName, TransactionCase batchTransaction, Date startTime) {
        StringBuilder result = new StringBuilder("Y");
        Map<String, String> resultMap = new HashMap<>();
        switch (tableName) {
            case "CLMR":
                deleteFile(tableName);
                resultMap =
                        modifyClmr.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyClmr.getStartTime();
                break;
            case "CLTMR":
                deleteFile(tableName);
                resultMap =
                        modifyCltmr.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyCltmr.getStartTime();
                break;
            case "CLTOT":
                deleteFile(tableName);
                resultMap =
                        modifyCltot.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyCltot.getStartTime();
                break;
            case "CLFEE":
                deleteFile(tableName);
                resultMap =
                        modifyClfee.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyClfee.getStartTime();
                break;
            case "CLMC":
                deleteFile(tableName);
                resultMap =
                        modifyClmc.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyClmc.getStartTime();
                break;
            case "CLBAF":
                deleteFile(tableName);
                resultMap =
                        modifyClbaf.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyClbaf.getStartTime();
                break;
            case "CLCMP":
                deleteFile(tableName);
                resultMap =
                        modifyClcmp.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyClcmp.getStartTime();
                break;
            case "CLDMR":
                deleteFile(tableName);
                resultMap =
                        modifyCldmr.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyCldmr.getStartTime();
                break;
            case "CLDTL":
                deleteFile(tableName);
                resultMap =
                        modifyCldtl.modify(
                                insertList, updateList, deleteList, batchTransaction, startTime);
                this.startTime = modifyCldtl.getStartTime();
                break;
            default:
                result = new StringBuilder("N");
                break;
        }
        if (!Objects.isNull(resultMap) && !resultMap.isEmpty()) {
            for (Map.Entry<String, String> entry : resultMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                result.append(",").append("[").append(key).append(",").append(value).append("]");
            }
        }
        return result.toString();
    }

    private void deleteFile(String table) {
        textFileUtil.deleteFile(fileDir + "TempError" + table);
        for (String type : lModifyType) {
            textFileUtil.deleteFile(fileDir + type + "Error" + table);
        }
    }
}
