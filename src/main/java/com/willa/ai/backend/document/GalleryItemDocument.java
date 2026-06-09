package com.willa.ai.backend.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "willa_gallery")
public class GalleryItemDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long sessionId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String sessionTitle;

    @Field(type = FieldType.Long)
    private Long messageId;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Text, analyzer = "standard", index = false)
    private String description;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;
}
