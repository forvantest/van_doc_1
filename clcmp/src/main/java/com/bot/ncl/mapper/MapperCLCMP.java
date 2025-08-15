/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.CONVCLCMP1__I;
import com.bot.ncl.adapter.in.svc.CONVCLCMP2__I;
import com.bot.ncl.adapter.in.svc.CONVCLCMP3__I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperCLCMP extends MapperCase {

    CONVCLCMP1__I mapperCONVCLCMP1(CL_BATCH_I cl_batch_i);

    CONVCLCMP2__I mapperCONVCLCMP2(CL_BATCH_I cl_batch_i);

    CONVCLCMP3__I mapperCONVCLCMP3(CL_BATCH_I cl_batch_i);
}
