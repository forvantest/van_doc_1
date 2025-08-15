/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.TXTEST_I;
import com.bot.ncl.adapter.in.svc.TXTEST_SVC_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperTXTEST extends MapperCase {

    TXTEST_SVC_I mapperTXTEST_SVC(TXTEST_I tXTEST_I);
}
