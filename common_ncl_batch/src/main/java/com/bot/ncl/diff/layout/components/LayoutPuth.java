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
public class LayoutPuth extends Layout {

    private static final String TABLE_NAME = "PUTH";

    public LayoutPuth() {
        List<String> key = new ArrayList<>(); // 複合主鍵

        key.add("PUTTYPE");
        key.add("PUTNAME");
        key.add("CODE");
        key.add("RCPTID");
        key.add("DATE");
        key.add("TIME");
        key.add("AMT");
        String delimiter = ""; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("PUTTYPE", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("PUTNAME", "CHAR", 8, 0, "Y"));
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("RCPTID", "CHAR", 26, 0, "Y"));
        columns.add(new Column("DATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("TIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("CLLBR", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("LMTDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("AMT", "DECIMAL", 12, 0, "Y"));
        columns.add(new Column("USERDATA", "NVARCHAR2", 40, 0, "Y"));
        columns.add(new Column("SITDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("TXTYPE", "CHAR", 1, 0, "Y"));
        columns.add(new Column("SERINO", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("PBRNO", "CHAR", 3, 0, "Y"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
