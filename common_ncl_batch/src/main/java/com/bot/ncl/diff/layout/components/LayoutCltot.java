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
public class LayoutCltot extends Layout {

    private static final String TABLE_NAME = "CLTOT";

    public LayoutCltot() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("CODE");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("CURCD", "DECIMAL", 2, 0, "N"));
        columns.add(new Column("RCVAMT", "DECIMAL", 17, 2, "Y"));
        columns.add(new Column("PAYAMT", "DECIMAL", 17, 2, "Y"));
        columns.add(new Column("TOTCNT", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("LCADAY", "DECIMAL", 8, 0, "N"));
        columns.add(new Column("LASTIME", "DECIMAL", 6, 0, "N"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
