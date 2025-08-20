package com.liimand.bettinggameserver.domain.mapper;

import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.dto.RoundDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoundInfoMapper {

    @Mapping(target = "state", expression = "java(r.state().name())")
    @Mapping(target = "openedAtMs", expression = "java(r.openedAt().toEpochMilli())")
    @Mapping(target = "bettingClosesAtMs", expression = "java(r.bettingClosesAt().toEpochMilli())")
    RoundDto toDto(RoundInfo r);

}
