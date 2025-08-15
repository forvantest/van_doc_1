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
public class LayoutClbaf extends Layout {

    private static final String TABLE_NAME = "CLBAF";

    public LayoutClbaf() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("CLLBR");
        key.add("ENTDY");
        key.add("CODE");
        key.add("PBRNO");
        key.add("CRDB");
        key.add("TXTYPE");
        key.add("CURCD");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CLLBR", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("ENTDY", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("PBRNO", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("CRDB", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("TXTYPE", "VARCHAR2", 2, 0, "Y"));
        columns.add(new Column("CURCD", "DECIMAL", 2, 0, "N"));
        columns.add(new Column("CNT", "DECIMAL", 5, 0, "Y"));
        columns.add(new Column("AMT", "DECIMAL", 15, 2, "Y"));
        columns.add(new Column("CFEE2", "DECIMAL", 8, 2, "Y"));
        columns.add(new Column("KFEE", "DECIMAL", 10, 2, "Y"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
