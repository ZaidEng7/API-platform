package com.company.document.api.mapper;

import com.company.document.api.dto.DocumentResponse;
import com.company.document.domain.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    DocumentResponse toResponse(Document document);
}
