package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    List<ChannelMessage> findByChannelIdOrderByCreatedAtAscIdAsc(Long channelId);

    long countByChannelId(Long channelId);
}
