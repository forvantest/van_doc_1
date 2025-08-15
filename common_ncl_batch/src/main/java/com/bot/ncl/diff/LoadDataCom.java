/* (C) 2024 */
package com.bot.ncl.diff;

import com.bot.ncl.diff.layout.model.Column;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.string.StringUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.text.astart.AstarUtils;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class LoadDataCom {

    @Autowired private AstarUtils astarUtils;

    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private StringUtil stringUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Getter private List<String> recordList;

    private LoadDataCom() {
        // YOU SHOULD USE @Autowired ,NOT new LoanDataCom()
    }

    public Map<String, String[]> loadData(
            String sourceOrtarget,
            String filePath,
            String tableName,
            List<Column> columns,
            List<String> keyFields,
            String delimiter,
            String charset) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "loadData()");
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "sourceOrtarget = {}", sourceOrtarget);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePath = {}", filePath);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "columns = {}", columns);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "keyFields = {}", keyFields);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "delimiter = {}", delimiter);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "charset = {}", charset);

        Map<String, String[]> dataMap = new HashMap<>();
        recordList = new ArrayList<>();
        Map<String, Integer> keyDupCountMap = new HashMap<>();

        Path path = Paths.get(filePath);
        File file = path.toFile();

        Charset fielCharset = null;
        if (charset.equalsIgnoreCase("BIG5")) {
            fielCharset = Charset.forName("big5");
        } else if (charset.equalsIgnoreCase("UTF8")) {
            fielCharset = StandardCharsets.UTF_8;
        } else if (charset.equalsIgnoreCase("BUR")) {
            // 先把檔案轉成Big5
            changeToBig5(file);
            fielCharset = Charset.forName("big5");
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), charset + "限為BUR、BIG5或UTF8");
            return dataMap;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, fielCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("PUTH".equals(tableName)) {
                    line = line.replaceAll("[\\x04\\x07]", "").replaceAll("[\\x00]", " ");
                    line = formatUtil.padX(line, 137);
                }
                String[] values = parseLine(line, columns, delimiter);
                Map<String, String> keyMap = createKeyMap(columns, values);
                String compositeKey = createCompositeKey(keyMap, keyFields);
                ApLogHelper.debug(
                        log, false, LogType.NORMAL.getCode(), "compositeKey = {}", compositeKey);
                if (dataMap.containsKey(compositeKey)) {
                    int keyDupCount = 1;
                    if (keyDupCountMap.containsKey(compositeKey)) {
                        keyDupCount = keyDupCountMap.get(compositeKey) + 1;
                    }
                    keyDupCountMap.put(compositeKey, keyDupCount);
                    String dupKeyRename = compositeKey + "_" + keyDupCount;
                    recordList.add(
                            sourceOrtarget + "資料重複,主鍵值:" + compositeKey + ",重新命名為" + dupKeyRename);
                    dataMap.put(dupKeyRename, values);
                } else {
                    dataMap.put(compositeKey, values);
                }
            }
        } catch (IOException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "readLine error = {}", e.getMessage());
        }
        return dataMap;
    }

    public String[] parseLine(String line, List<Column> columns, String delimiter) {
        ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "parseLine()");
        ApLogHelper.debug(log, false, LogType.NORMAL.getCode(), "line = {}", line);
        String[] values;
        if (delimiter != null && !delimiter.isEmpty()) {
            values = line.split(delimiter);
        } else {
            values = new String[columns.size()];
            int position = 0;
            for (int i = 0; i < columns.size(); i++) {
                int len = columns.get(i).getLen();
                if (position + len > line.getBytes(Charset.forName("Big5")).length) {
                    len = line.length() - position;
                }
                try {
                    values[i] =
                            stringUtil
                                    .CutBUR(line, position, position + len)
                                    .replaceAll("[\\s\u3000]+$", "");
                } catch (Exception e) {
                    values[i] = "";
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "異常處理空白 = [{}]  [{}]",
                            values[i],
                            Arrays.toString(values));
                }

                position += len;
            }
        }
        ApLogHelper.debug(
                log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));
        return values;
    }

    public Map<String, String> createKeyMap(List<Column> columns, String[] values) {
        Map<String, String> keyMap = new HashMap<>();
        for (int i = 0; i < columns.size() && i < values.length; i++) {
            keyMap.put(
                    columns.get(i).getName(),
                    values[i].replaceAll(
                            "[\\s\u3000]+$", "")); // 2024-12-04 Wei CLBAF TXTYPE 需要trim
        }
        return keyMap;
    }

    private String createCompositeKey(Map<String, String> keyMap, List<String> keyFields) {
        return keyFields.stream().map(keyMap::get).collect(Collectors.joining("_"));
    }

    private void changeToBig5(File file) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "changeToBig5()");
        List<String> lines = new ArrayList<>();
        List<byte[]> byteArrayList = new ArrayList<>();
        textFileUtil.deleteFile(file.getAbsolutePath());

        try (BufferedInputStream bis =
                new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
            byte[] buffer = new byte[64 * 1024]; // 一次讀取的緩衝區大小
            int bytesRead;
            ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(64 * 1024);
            while ((bytesRead = bis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') { // 遇到換行符
                        byteArrayList.add(lineBuffer.toByteArray());
                        lineBuffer.reset(); // 重置行緩衝區
                    } else {
                        lineBuffer.write(buffer[i]);
                    }
                }
            }
            if (lineBuffer.size() > 0) { // 處理最後一行（如果存在）
                byteArrayList.add(lineBuffer.toByteArray());
            }
        } catch (IOException e) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "讀檔失敗:" + e.getMessage());
        }

        int i = 0;
        for (byte[] btyeArray : byteArrayList) {
            i++;
            String line = "";
            try {
                line = astarUtils.burToUTF8(btyeArray);
            } catch (Exception e) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "轉碼失敗:" + e.getMessage());
                ApLogHelper.error(
                        log, false, LogType.NORMAL.getCode(), "原始碼:" + Arrays.toString(btyeArray));
                continue;
            }
            lines.add(line);
            // 100萬筆寫一次檔
            if (i >= 1000000) {
                textFileUtil.writeFileContent(file.getAbsolutePath(), lines, "BIG5");
                lines.clear();
                i = 0;
            }
        }
        // 寫剩下的
        if (!lines.isEmpty()) {
            textFileUtil.writeFileContent(file.getAbsolutePath(), lines, "BIG5");
        }
    }
}
