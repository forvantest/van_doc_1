/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.CONV302007_I;
import com.bot.ncl.adapter.in.svc.CONV302007_PBRNO_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperC1530_S14 extends MapperCase {

    CONV302007_PBRNO_I mapperCONV302007_PBRNO(CL_BATCH_I cL_BATCH_I);

    CONV302007_I mapperCONV302007(CL_BATCH_I cL_BATCH_I);
}
