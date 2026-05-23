package cn.arorms.fms.server.mappers;

import cn.arorms.fms.server.dto.FermenterConnectionStatusDto;
import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FermenterConnectionStatusMapper {

    @Mapping(target = "online", expression = "java(entity.getEventType() == cn.arorms.fms.server.enums.ConnectionEventType.ONLINE)")
    @Mapping(target = "lastTime", expression = "java(entity.getEventTime() != null ? entity.getEventTime().toString() : null)")
    FermenterConnectionStatusDto toDto(FermenterConnectionEvent entity);
}