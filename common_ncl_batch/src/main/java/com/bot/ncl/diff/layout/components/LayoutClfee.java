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
public class LayoutClfee extends Layout {

    private static final String TABLE_NAME = "CLFEE";

    public LayoutClfee() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("KEY_CODE_FEE");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("KEY_CODE_FEE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("TXTYPE", "VARCHAR2", 2, 0, "N"));
        columns.add(new Column("STAMT", "DECIMAL", 6, 0, "N"));
        columns.add(new Column("CFEE1", "DECIMAL", 6, 2, "Y"));
        columns.add(new Column("CFEE2", "DECIMAL", 5, 2, "Y"));
        columns.add(new Column("CFEE3", "DECIMAL", 6, 2, "Y"));
        columns.add(new Column("CFEE4", "DECIMAL", 12, 2, "Y"));
        columns.add(new Column("FKD", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("MFEE", "DECIMAL", 5, 2, "Y"));
        columns.add(new Column("CFEEEB", "DECIMAL", 5, 2, "Y"));
        columns.add(new Column("FEE", "DECIMAL", 5, 2, "N"));
        columns.add(new Column("FEECOST", "DECIMAL", 5, 2, "N"));
        columns.add(new Column("CFEE_003", "DECIMAL", 5, 2, "N"));
        columns.add(new Column("FEE_CAL_TYPE", "DECIMAL", 1, 0, "N"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
