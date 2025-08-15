/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping.interfaces;

import java.util.Map;

public interface IMapper {
    String COMMA = ",";
    String EMPTY = "";

    Map<String, String> mapping(Map<String, String> map);
}
