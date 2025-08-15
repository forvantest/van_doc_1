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
public class LayoutClcmp extends Layout {

    private static final String TABLE_NAME = "CLCMP";

    public LayoutClcmp() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("CODE");
        key.add("RCPTID");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("RCPTID", "VARCHAR2", 16, 0, "Y"));
        columns.add(new Column("ID", "VARCHAR2", 10, 0, "N"));
        columns.add(new Column("PNAME", "NVARCHAR2", 30, 0, "Y"));
        columns.add(new Column("CURCD", "DECIMAL", 2, 0, "N"));
        columns.add(new Column("AMT", "DECIMAL", 13, 2, "Y"));
        columns.add(new Column("SDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("STIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("LDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("LTIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("KDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("LFLG", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("CDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("UDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("UTIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("KINBR", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("TLRNO", "VARCHAR2", 2, 0, "Y"));
        columns.add(new Column("EMPNO", "VARCHAR2", 6, 0, "N"));
        columns.add(new Column("OTFLAG", "VARCHAR2", 2, 0, "N"));
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
