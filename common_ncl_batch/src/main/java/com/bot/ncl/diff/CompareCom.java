/* (C) 2024 */
package com.bot.ncl.diff;

import com.bot.ncl.diff.layout.model.Column;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import java.math.BigDecimal;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Getter
@Slf4j
@Component
@Scope("prototype")
public class CompareCom {

    private Map<String, String> detailDiffMap;

    private List<String> compareRecord;

    private boolean modifyFlag;

    private CompareCom() {
        // YOU SHOULD USE @Autowired ,NOT new CompareCom()
    }

    public boolean compareRecords(
            String tableName,
            String[] sourceRecord,
            String[] targetRecord,
            List<Column> columnConfigs,
            String keyName,
            String keyValue) {
        ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "compareRecords()");
        boolean isRowSame = true;
        detailDiffMap = new HashMap<>();
        compareRecord = new ArrayList<>();

        //  特殊處理
        modifyFlag = specialHandleModifyFlag(tableName, sourceRecord, targetRecord);

        // CLDTL特殊處理,比較時若有找到NCL的資料,以NCL的TXTNO為主
        String cldtlTxtno = "";

        int sourceRecordLen = sourceRecord.length;
        int targetRecordLen = targetRecord.length;

        for (int i = 0; i < columnConfigs.size(); i++) {
            Column column = columnConfigs.get(i);

            String diffFlag = column.getDiffFlag();
            String type = column.getType();

            if (i >= sourceRecordLen) {
                ApLogHelper.warn(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "來源檔欄位不足,={}",
                        Arrays.stream(sourceRecord).toArray());
                continue;
            }
            String sourceValue;
            if ("USERDATA".equals(column.getName())) {
                sourceValue = sourceRecord[i].replaceAll("[\\s\u3000]+$", "");
            } else {
                sourceValue = sourceRecord[i];
            }

            String targetValue = "";
            if (i >= targetRecordLen) {
                ApLogHelper.warn(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "目標檔欄位不足,={}",
                        Arrays.stream(targetRecord).toArray());
            } else {
                if ("USERDATA".equals(column.getName())) {
                    targetValue = targetRecord[i].replaceAll("[\\s\u3000]+$", "");
                } else {
                    targetValue = targetRecord[i];
                }
            }

            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "column={}", column);
            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "sourceValue={}", sourceValue);
            ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "targetValue={}", targetValue);

            // CLDTL特殊處理,比較時若有找到NCL的資料,以NCL的TXTNO為主
            if (tableName.equals("CLDTL")
                    && column.getName().equals("TXTNO")
                    && parseInteger(targetValue) != 0) {
                cldtlTxtno = targetValue;
                continue;
            }

            if (Objects.isNull(diffFlag) || diffFlag.isEmpty() || diffFlag.equals("N")) {
                //                detailDiffMap.put(column.getName(), sourceValue);
                continue;
            }

            if (type.equals("DECIMAL")) {
                BigDecimal sourceDecimal = parseBigDecimal(sourceValue);
                BigDecimal targetDecimal = parseBigDecimal(targetValue);
                if (sourceDecimal.compareTo(targetDecimal) != 0) {
                    isRowSame = diff(keyName, keyValue, column, sourceValue, targetValue);
                }
            } else if (!sourceValue.trim().equals(targetValue.trim())) {
                isRowSame = diff(keyName, keyValue, column, sourceValue, targetValue);
            }
        }
        // CLDTL特殊處理,比較時若有找到NCL的資料,以NCL的TXTNO為主
        if (!isRowSame) {
            detailDiffMap.put("TXTNO", cldtlTxtno);
        }
        return isRowSame;
    }

    private boolean specialHandleModifyFlag(
            String tableName, String[] sourceRecord, String[] targetRecord) {
        boolean modifyFlag;
        switch (tableName) {
            case "CLTOT":
                modifyFlag = specialHandleModifyFlagCLTOT(sourceRecord, targetRecord);
                break;
            default:
                modifyFlag = true;
                break;
        }
        return modifyFlag;
    }

    private boolean specialHandleModifyFlagCLTOT(String[] sourceRecord, String[] targetRecord) {
        // 特殊處理:若為CLTOT,需先比較NCL(LCALDY+LASTIME)<= FAS(LCALDY+LASTIME)決定是否可以異動,否則只出差異報表
        int nclLCaldy = parseInteger(targetRecord[5]);
        int nclLastTime = parseInteger(targetRecord[6]);
        int fasLCaldy = parseInteger(sourceRecord[5]);
        int fasLastTime = parseInteger(sourceRecord[6]);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nclLCaldy={}", nclLCaldy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nclLastTime={}", nclLastTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fasLCaldy={}", fasLCaldy);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fasLastTime={}", fasLastTime);
        if (nclLCaldy < fasLCaldy) {
            return true;
        } else if (nclLCaldy == fasLCaldy && nclLastTime <= fasLastTime) {
            return true;
        } else {
            return false;
        }
    }

    private boolean diff(
            String keyName,
            String keyValue,
            Column column,
            String sourceValue,
            String targetValue) {
        StringBuilder record = new StringBuilder();
        record.append("資料不同,主鍵欄位名稱=")
                .append(keyName)
                .append(",主鍵值=")
                .append(keyValue)
                .append(",差異欄位名稱=")
                .append(column.getName())
                .append(",FAS值=")
                .append(sourceValue)
                .append(",NCL值=")
                .append(targetValue)
                .append(",是否異動=")
                .append(modifyFlag ? "Y" : "N");
        ApLogHelper.warn(log, false, LogType.NORMAL.getCode(), record.toString());
        if (modifyFlag) {
            detailDiffMap.put(column.getName(), sourceValue);
        }
        compareRecord.add(record.toString());
        return false;
    }

    private BigDecimal parseBigDecimal(String s) {
        BigDecimal bigDecimal;
        try {
            bigDecimal = new BigDecimal(s);
        } catch (Exception e) {
            bigDecimal = BigDecimal.ZERO;
        }
        return bigDecimal;
    }

    private Integer parseInteger(String s) {
        int i;
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = 0;
        }
        return i;
    }
}
