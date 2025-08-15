/* (C) 2024 */
package com.bot.ncl.diff.layout.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Layout {
    private String tableName; // 資料表名稱
    protected List<String> key; // 複合主鍵
    private String delimiter; // 分隔符號
    private String charset; // 檔案編碼
    protected List<Column> columns; // 欄位設定
}
