/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.svc.*;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperANALY extends MapperCase {}
