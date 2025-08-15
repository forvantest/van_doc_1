/* (C) 2025 */
package com.bot.ncl.mapper;

import com.bot.adapter.in.svc.CONV13__I;
import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperC1530_S26 extends MapperCase {

    CONV13__I mapperCONV13(CL_BATCH_I cL_BATCH_I);
}
