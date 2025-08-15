/* (C) 2024 */
package com.bot.ncl.adapter.event.app.util;

import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.text.astart.AstarUtils;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class FileUtil {

    @Getter private List<String> astarErrorMsg;

    @Autowired private AstarUtils astarUtils;

    private FileUtil() {}

    public List<String> readFileToByteArrayByLine(String filePath) throws IOException {
        File file = new File(filePath);
        List<String> lines = new ArrayList<>();
        List<byte[]> byteArrayList = new ArrayList<>();
        astarErrorMsg = new ArrayList<>();

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
        }

        int i = 0;
        for (byte[] btyeArray : byteArrayList) {
            String line = "";
            try {
                line = astarUtils.burToUTF8(btyeArray);
            } catch (Exception e) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "轉碼失敗:" + e.getMessage());
                ApLogHelper.error(
                        log, false, LogType.NORMAL.getCode(), "原始碼:" + Arrays.toString(btyeArray));
                astarErrorMsg.add(
                        "原始檔內第"
                                + i
                                + "筆轉碼失敗:"
                                + e.getMessage()
                                + "原始碼:"
                                + Arrays.toString(btyeArray));
                i++;
                continue;
            }
            line = line.replaceAll("\u0000", "");
            lines.add(line);
            i++;
        }
        return lines;
    }
}
