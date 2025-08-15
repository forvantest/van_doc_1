/* (C) 2024 */
package com.bot.ncl.adapter.event.app.struct;

import lombok.Data;

@Data
public class Column {
    int index;
    String columnName;
    String dataType;
    double dataLength;

    public Column(int index, String columnName, String dataType, double dataLength) {
        this.index = index;
        this.columnName = columnName;
        this.dataType = dataType;
        this.dataLength = dataLength;
    }
}
