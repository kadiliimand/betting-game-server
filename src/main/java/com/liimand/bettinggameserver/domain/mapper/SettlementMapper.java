package com.liimand.bettinggameserver.domain.mapper;

import com.liimand.bettinggameserver.domain.Settlement;
import com.liimand.bettinggameserver.domain.WinnerInfo;
import com.liimand.bettinggameserver.dto.SettlementDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SettlementMapper {

    SettlementDto toDto(Settlement s);

    List<SettlementDto.WinnerDto> toWinnerDtos(List<WinnerInfo> winners);
}
