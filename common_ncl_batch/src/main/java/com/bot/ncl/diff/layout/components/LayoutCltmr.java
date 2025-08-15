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
public class LayoutCltmr extends Layout {

    private static final String TABLE_NAME = "CLTMR";

    public LayoutCltmr() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("CODE");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("ATMCODE", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("ENTPNO", "VARCHAR2", 10, 0, "Y"));
        columns.add(new Column("HENTPNO", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("SCNAME", "VARCHAR2", 10, 0, "Y"));
        columns.add(new Column("CDATA", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("APPDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("UPDDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("LPUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("LLPUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("ULPUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("ULLPUTDT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("PRTYPE", "CHAR", 1, 0, "Y"));
        columns.add(new Column("CLSACNO", "VARCHAR2", 6, 0, "Y"));
        columns.add(new Column("CLSSBNO", "VARCHAR2", 4, 0, "Y"));
        columns.add(new Column("CLSDTLNO", "VARCHAR2", 4, 0, "Y"));
        columns.add(new Column("EBTYPE", "VARCHAR2", 10, 0, "Y"));
        columns.add(new Column("PWTYPE", "VARCHAR2", 3, 0, "Y"));
        columns.add(new Column("FEENAME", "VARCHAR2", 42, 0, "Y"));
        columns.add(new Column("PUTNAME", "VARCHAR2", 6, 0, "Y"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
