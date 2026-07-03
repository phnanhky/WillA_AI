package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    @Query("""
            SELECT m FROM ChannelMessage m
            LEFT JOIN FETCH m.user
            WHERE m.channel.id = :channelId AND m.parentMessage IS NULL
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChannelMessage> findTopLevelByChannelId(@Param("channelId") Long channelId);

    @Query("""
            SELECT m FROM ChannelMessage m
            LEFT JOIN FETCH m.user
            LEFT JOIN FETCH m.parentMessage
            WHERE m.channel.id = :channelId AND m.parentMessage IS NOT NULL
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChannelMessage> findThreadRepliesByChannelId(@Param("channelId") Long channelId);

    long countByChannelId(Long channelId);
}
