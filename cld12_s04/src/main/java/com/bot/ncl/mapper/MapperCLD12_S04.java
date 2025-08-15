/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.OUTKPUTF1__I;
import com.bot.ncl.adapter.in.svc.OUTKPUTF2__I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperCLD12_S04 extends MapperCase {

    OUTKPUTF1__I mapperOUTKPUTF1(CL_BATCH_I cl_batch_i);

    OUTKPUTF2__I mapperOUTKPUTF2(CL_BATCH_I cl_batch_i);
}
