/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.BT002_CLBH_I;
import com.bot.ncl.adapter.in.svc.CLBH_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperBT002 extends MapperCase {

    CLBH_I mapperCLBH(BT002_CLBH_I bT002_CLBH_I);
}
