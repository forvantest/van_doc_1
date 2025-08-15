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
public class LayoutCldtl extends Layout {

    private static final String TABLE_NAME = "CLDTL";

    public LayoutCldtl() {
        List<String> key = new ArrayList<>(); // 複合主鍵
        // CLDTL有特殊比較方式,舊表沒有TXTNO,新系統有,比較時多比金額,UPDATE時的KEY用新系統的KEY看ModifyCldtl程式
        key.add("CODE");
        key.add("RCPTID");
        key.add("ENTDY");
        key.add("TRMNO");
        key.add("TIME");
        key.add("AMT");
        String delimiter = "\\^"; // 分隔符號
        String charset = "BIG5"; // 檔案編碼
        List<Column> columns = new ArrayList<>(); // 欄位設定
        columns.add(new Column("CODE", "CHAR", 6, 0, "Y"));
        columns.add(new Column("AMT", "DECIMAL", 13, 2, "Y"));
        columns.add(new Column("RCPTID", "VARCHAR2", 26, 0, "Y"));
        columns.add(new Column("TXTYPE", "VARCHAR2", 2, 0, "Y"));
        columns.add(new Column("CLLBR", "DECIMAL", 3, 0, "Y"));
        columns.add(new Column("TRMNO", "DECIMAL", 7, 0, "Y"));
        columns.add(new Column("TXTNO", "DECIMAL", 8, 0, "N"));
        columns.add(new Column("TLRNO", "CHAR", 2, 0, "Y"));
        columns.add(new Column("ENTDY", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("TIME", "DECIMAL", 6, 0, "Y"));
        columns.add(new Column("LMTDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("USERDATA", "NVARCHAR2", 100, 0, "Y"));
        columns.add(new Column("SITDATE", "DECIMAL", 8, 0, "Y"));
        columns.add(new Column("CALDY", "DECIMAL", 8, 0, "N"));
        columns.add(new Column("ACTNO", "VARCHAR2", 20, 0, "Y"));
        columns.add(new Column("SERINO", "DECIMAL", 6, 0, "N"));
        columns.add(new Column("CRDB", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("CURCD", "DECIMAL", 2, 0, "N"));
        columns.add(new Column("HCODE", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("SOURCETP", "VARCHAR2", 3, 0, "N"));
        columns.add(new Column("CACTNO", "VARCHAR2", 16, 0, "N"));
        columns.add(new Column("HTXSEQ", "DECIMAL", 15, 0, "N"));
        columns.add(new Column("EMPNO", "VARCHAR2", 6, 0, "N"));
        columns.add(new Column("PUTFG", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("ENTFG", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("SOURCEIP", "VARCHAR2", 40, 0, "N"));
        columns.add(new Column("UPLFILE", "VARCHAR2", 40, 0, "N"));
        columns.add(new Column("PBRNO", "DECIMAL", 3, 0, "N"));
        columns.add(new Column("CFEE2", "DECIMAL", 5, 2, "N"));
        columns.add(new Column("FKD", "DECIMAL", 1, 0, "N"));
        columns.add(new Column("FEE", "DECIMAL", 5, 2, "N"));
        columns.add(new Column("FEECOST", "DECIMAL", 5, 2, "N"));
        this.setTableName(TABLE_NAME);
        this.key = new ArrayList<>();
        this.key.addAll(key);
        this.setDelimiter(delimiter);
        this.setCharset(charset);
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
    }
}
