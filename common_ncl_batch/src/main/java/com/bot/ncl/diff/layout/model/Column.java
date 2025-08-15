/* (C) 2024 */
package com.bot.ncl.diff.layout.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Column {
    private String name; // 欄位名稱
    private String type; // "X" 表示字串, "9" 表示數字
    private int len; // 總長度
    private int dec; // 小數位數
    private String diffFlag; // 是否要比較
}
