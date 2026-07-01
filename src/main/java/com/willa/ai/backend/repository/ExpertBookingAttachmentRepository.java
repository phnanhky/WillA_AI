package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBookingAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpertBookingAttachmentRepository extends JpaRepository<ExpertBookingAttachment, Long> {

    List<ExpertBookingAttachment> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
