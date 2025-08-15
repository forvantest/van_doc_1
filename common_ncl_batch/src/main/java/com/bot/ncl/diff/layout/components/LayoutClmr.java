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
public class LayoutClmr extends Layout {

    private static final String TABLE_NAME = "CLMR";

    public LayoutClmr() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        key.add("CODE");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("PBRNO", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("VRCODE", "DECIMAL", 4, 0, "Y"));
        columns.add(new Column("RIDDUP", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("DUPCYC", "DECIMAL", 3, 0, "N"));
        columns.add(new Column("ACTNO", "DECIMAL", 12, 0, "Y"));
        columns.add(new Column("MSG1", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("PUTTIME", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("SUBFG", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("CHKTYPE", "VARCHAR2", 2, 0, "Y"));
        columns.add(new Column("CHKAMT", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("UNIT", "DECIMAL", 10, 2, "Y"));
        columns.add(new Column("AMTCYC", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("AMTFG", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("AMT", "DECIMAL", 17, 2, "Y"));
        columns.add(new Column("CNAME", "NVARCHAR2", 40, 0, "Y"));
        columns.add(new Column("STOP", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("HOLDCNT", "DECIMAL", 3, 0, "N"));
        columns.add(new Column("HOLDCNT2", "DECIMAL", 3, 0, "N"));
        columns.add(new Column("AFCBV", "DECIMAL", 1, 0, "Y"));
        columns.add(new Column("NETINFO", "VARCHAR2", 20, 0, "Y"));
        columns.add(new Column("PRINT", "DECIMAL", 2, 0, "Y"));
        columns.add(new Column("STDATE", "DECIMAL", 8, 0, "N"));
        columns.add(new Column("STTIME", "DECIMAL", 6, 0, "N"));
        columns.add(new Column("STOPDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("STOPTIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("CRDB", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("HCODE", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("LKCODE", "VARCHAR2", 6, 0, "Y"));
        columns.add(new Column("FLAG", "CHAR", 1, 0, "N"));
        columns.add(new Column("OTHER_FIELD", "VARCHAR2", 500, 0, "N"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
