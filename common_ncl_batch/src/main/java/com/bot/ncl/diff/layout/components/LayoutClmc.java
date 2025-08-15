/* (C) 2024 */
package com.bot.ncl.diff.layout.components;

import com.bot.ncl.diff.layout.model.Column;
import com.bot.ncl.diff.layout.model.Layout;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class LayoutClmc extends Layout {

    private static final String TABLE_NAME = "CLMC";

    public LayoutClmc() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("PUTNAME");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("PUTNAME", "CHAR", 6, 0, "Y"));
        columns.add(new Column("PUTSEND", "CHAR", 1, 0, "Y"));
        columns.add(new Column("PUTFORM", "CHAR", 1, 0, "Y"));
        columns.add(new Column("PUTTYPE", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("PUTADDR", "VARCHAR2", 100, 0, "Y"));
        columns.add(new Column("PUT_ENCODE", "CHAR", 1, 0, "N"));
        columns.add(new Column("PUT_COMPRESS", "CHAR", 1, 0, "N"));
        columns.add(new Column("OPUTTIME", "CHAR", 1, 0, "N"));
        columns.add(new Column("CYCK1", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("CYCNO1", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("CYCK2", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("CYCNO2", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("PUTDTFG", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("PUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("MSG2", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("TPUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("USECNT", "DECIMAL", 4, 0, "N"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
