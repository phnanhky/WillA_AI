package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

// Bảng lưu nhiều trang trong 1 Workspace (như Canva Design)
@Entity
@Table(name = "workspace_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspacePage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "page_number")
    private Integer pageNumber; // Fallback to page_number mapping

    @Column(name = "image_url")
    private String imageUrl; // For previous implementation compat

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes; // For previous implementation compat

    // Lưu toàn bộ toạ độ, font, element, url dưới dạng JSON
    @Column(name = "design_data", columnDefinition = "TEXT")
    private String designData;
}
