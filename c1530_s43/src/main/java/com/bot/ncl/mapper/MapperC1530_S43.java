/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.EX_KPUTH2_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperC1530_S43 extends MapperCase {

    EX_KPUTH2_I mapperEX_KPUTH2(CL_BATCH_I cl_batch_i);
}
