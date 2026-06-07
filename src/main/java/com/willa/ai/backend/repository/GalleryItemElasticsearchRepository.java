package com.willa.ai.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.willa.ai.backend.document.GalleryItemDocument;

public interface GalleryItemElasticsearchRepository extends ElasticsearchRepository<GalleryItemDocument, String> {

    Page<GalleryItemDocument> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            {
              "bool": {
                "must": [
                  { "term": { "userId": ?0 } },
                  { "multi_match": { "query": "?1", "fields": ["sessionTitle^3", "description"] } }
                ]
              }
            }
            """)
    Page<GalleryItemDocument> searchByUserIdAndQuery(Long userId, String query, Pageable pageable);
}
