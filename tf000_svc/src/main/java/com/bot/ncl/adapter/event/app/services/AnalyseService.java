/* (C) 2024 */
package com.bot.ncl.adapter.event.app.services;

import com.bot.ncl.adapter.event.app.struct.Column;
import com.bot.ncl.adapter.event.app.util.ParseUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class AnalyseService {

    @Getter private String analyseErrorMsg;

    private Map<Integer, Column> tableLayoutMap;

    public AnalyseService() {
        tableLayoutMap = null;
    }

    public Map<String, String> analyse(String[] values, int rowNumber, String fileName) {
        Map<String, String> result = new HashMap<>();
        analyseErrorMsg = "";
        boolean dataTypeError = false;
        if (tableLayoutMap == null) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "tableLayoutMap is null");
            return null;
        }

        if (tableLayoutMap.size() > values.length) {
            analyseErrorMsg =
                    "檔案:"
                            + fileName
                            + ",第"
                            + rowNumber
                            + "筆不轉入(欄位數不足"
                            + values.length
                            + "/"
                            + tableLayoutMap.size()
                            + ")"
                            + "此筆內容為:"
                            + Arrays.toString(values);
            return null;
        }
        List<Integer> errorColumns = new ArrayList<>();
        Map<Integer, String> errorValues = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            Column column = tableLayoutMap.get(i);
            String columnName = column.getColumnName();
            String dataType = column.getDataType();
            double dataLength = column.getDataLength();

            // 檢核 欄位型態 , 不符合時提示錯誤
            if (dataType.equals("X")) {
                if (Objects.isNull(value)) {
                    value = "";
                }
                value = value.replaceAll("\u0004", "");
                value = value.replaceAll("\u0007", "");
                value = value.replaceAll("\u0000", "");
                value = value.replace("'", "''");
                value = value.stripTrailing();
            }

            if (dataType.equals("9")) {
                BigDecimal bigDecimal;
                try {
                    bigDecimal = new BigDecimal(value);
                } catch (Exception e) {
                    errorColumns.add((i + 1));
                    errorValues.put(i, value);
                    // 錯誤轉0
                    bigDecimal = BigDecimal.ZERO;
                    // 紀錄為錯誤
                    dataTypeError = true;
                }
                value = bigDecimal.toPlainString();
            }
            result.put(columnName, value);
        }

        if (dataTypeError) {
            StringBuilder errorColumnNames = new StringBuilder();
            for (Integer i : errorColumns) {
                errorColumnNames
                        .append("第")
                        .append(i)
                        .append("欄位(")
                        .append(tableLayoutMap.get(i - 1).getColumnName())
                        .append(":")
                        .append(errorValues.get(i))
                        .append(");");
            }
            String errorMsg = "轉碼成功檔內第" + rowNumber + "筆不轉入,轉型失敗," + errorColumnNames;
            analyseErrorMsg = errorMsg;
            StringBuilder content = new StringBuilder();
            content.append("{");
            for (int i = 0; i < tableLayoutMap.size(); i++) {
                content.append("\"");
                content.append(tableLayoutMap.get(i).getColumnName());
                content.append("\"");
                content.append(":");
                content.append("\"");
                if (i < values.length) {
                    content.append(values[i]);
                }
                content.append("\"");
                if (i < tableLayoutMap.size() - 1) {
                    content.append(",");
                }
            }
            content.append("}");
            analyseErrorMsg += content;
            return null;
        }
        return result;
    }

    public void loadTableLayout(String fileName) throws LogicException {
        tableLayoutMap = new HashMap<>();
        try (InputStream inputStream =
                AnalyseService.class.getResourceAsStream("/" + fileName + ".tableLayout")) {
            if (inputStream == null) {
                ApLogHelper.error(
                        log, false, LogType.NORMAL.getCode(), fileName + " file not found! ");
                throw new LogicException("GE999", fileName + " file not found! ");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                int rowNumber = 0;
                while ((line = reader.readLine()) != null) {
                    String[] s = line.split(",");
                    if (s.length != 0) {
                        try {
                            int index = rowNumber;
                            String columnName = s[0];
                            String dataType = s[1];
                            double dataLength = ParseUtil.stringToDouble(s[2]);
                            Column column = new Column(index, columnName, dataType, dataLength);
                            tableLayoutMap.put(index, column);
                            rowNumber++;
                        } catch (Exception e) {
                            ApLogHelper.error(
                                    log,
                                    false,
                                    LogType.NORMAL.getCode(),
                                    "read column Exception = " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "read " + fileName + " Exception = " + e.getMessage());
            throw new LogicException("GE999", fileName + " file cannot read! ");
        }
    }

    public int getTableLayoutMapSize() {
        return tableLayoutMap.size();
    }
}
