/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.C0153_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperSTAT6_5A extends MapperCase {

    C0153_I mapperC0153(CL_BATCH_I cL_BATCH_I);
}
