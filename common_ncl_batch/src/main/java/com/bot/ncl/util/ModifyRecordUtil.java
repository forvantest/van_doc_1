/* (C) 2024 */
package com.bot.ncl.util;

import com.bot.ncl.util.files.TextFileUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ModifyRecordUtil {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private TextFileUtil textFileUtil;

    private List<String> records;

    private String outputFilePath;

    private static final String UTF8 = "UTF-8";

    private ModifyRecordUtil() {
        // YOU SHOULD USE @Autowired ,NOT new DiffParseUtil()
    }

    public void initialize(String tableName) {
        this.records = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);
        outputFilePath = fileDir + "Modify_" + tableName + "_" + timestamp + ".txt";
    }

    public void addRecord(String msg) {
        records.add(msg);
    }

    public void output() {
        textFileUtil.writeFileContent(outputFilePath, records, UTF8);
    }
}
