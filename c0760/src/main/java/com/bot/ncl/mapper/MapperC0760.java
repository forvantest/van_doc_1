/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.C0761__I;
import com.bot.ncl.adapter.in.svc.C076A__I;
import com.bot.ncl.adapter.in.svc.C076__I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperC0760 extends MapperCase {

    C076__I mapperC076(CL_BATCH_I cl_batch_i);

    C0761__I mapperC0761(CL_BATCH_I cl_batch_i);

    C076A__I mapperC076A(CL_BATCH_I cl_batch_i);
}
