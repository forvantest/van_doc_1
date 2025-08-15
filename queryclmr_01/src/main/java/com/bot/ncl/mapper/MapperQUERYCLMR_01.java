/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.QUERYCLMR_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperQUERYCLMR_01 extends MapperCase {

    QUERYCLMR_I mapperQUERYCLMR(CL_BATCH_I cL_BATCH_I);
}
