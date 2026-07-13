package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkflowUsage;
import com.willa.ai.backend.entity.enums.WorkflowType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface WorkflowUsageRepository extends JpaRepository<WorkflowUsage, Long> {

    @Query("""
            SELECT w.workflow, COUNT(w), COALESCE(SUM(w.durationMs), 0L), COALESCE(AVG(w.durationMs), 0.0)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.user.id IN :userIds
            GROUP BY w.workflow
            ORDER BY SUM(w.durationMs) DESC
            """)
    List<Object[]> aggregateByWorkflow(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT w.user.id, u.email, COUNT(w), COALESCE(SUM(w.durationMs), 0L)
            FROM WorkflowUsage w
            JOIN w.user u
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.user.id IN :userIds
            GROUP BY w.user.id, u.email
            ORDER BY SUM(w.durationMs) DESC
            """)
    List<Object[]> aggregateByUser(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT w.user.id, w.workflow, COUNT(w), COALESCE(SUM(w.durationMs), 0L)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.user.id IN :userIds
            GROUP BY w.user.id, w.workflow
            """)
    List<Object[]> aggregateByUserAndWorkflow(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(w.durationMs), 0L), COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.user.id IN :userIds
            """)
    Object[] totalDurationAndCount(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    Page<WorkflowUsage> findByUser_IdInAndStartedAtBetweenOrderByStartedAtDesc(
            Collection<Long> userIds,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    List<WorkflowUsage> findByUser_IdAndStartedAtBetweenOrderByStartedAtDesc(
            Long userId,
            LocalDateTime from,
            LocalDateTime to);

    @Query("""
            SELECT w.workflow, COUNT(w), COALESCE(SUM(w.durationMs), 0L), COALESCE(AVG(w.durationMs), 0.0)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            GROUP BY w.workflow
            ORDER BY SUM(w.durationMs) DESC
            """)
    List<Object[]> aggregateByWorkflowInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(w.durationMs), 0L), COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            """)
    Object[] totalDurationAndCountInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    org.springframework.data.domain.Page<WorkflowUsage> findByStartedAtBetweenOrderByStartedAtDesc(
            LocalDateTime from,
            LocalDateTime to,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT w.user.id)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            """)
    Long countDistinctUsersInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT DATE(w.started_at) AS day, COUNT(*) AS runs, COALESCE(SUM(w.duration_ms), 0) AS total_ms
            FROM workflow_usages w
            WHERE w.started_at >= :from AND w.started_at <= :to
            GROUP BY DATE(w.started_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> getDailyWorkflowStats(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT w.user.id, u.email, COUNT(w), COALESCE(SUM(w.durationMs), 0L)
            FROM WorkflowUsage w
            JOIN w.user u
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            GROUP BY w.user.id, u.email
            ORDER BY SUM(w.durationMs) DESC
            """)
    List<Object[]> topUsersByWorkflowTime(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** Tất cả user có gọi AI workflow trong kỳ. */
    @Query("""
            SELECT w.user.id, u.email, COUNT(w), COALESCE(SUM(w.durationMs), 0L)
            FROM WorkflowUsage w
            JOIN w.user u
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            GROUP BY w.user.id, u.email
            ORDER BY SUM(w.durationMs) DESC
            """)
    List<Object[]> usersByWorkflowTimeInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT w.workflow, COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.status = com.willa.ai.backend.entity.enums.WorkflowUsageStatus.FAILED
            GROUP BY w.workflow
            """)
    List<Object[]> failedCountByWorkflowInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(w.durationMs), 0L), COUNT(w), COALESCE(AVG(w.durationMs), 0.0)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.workflow = :workflow
            """)
    Object[] statsForWorkflowInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("workflow") WorkflowType workflow);

    @Query("""
            SELECT COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.workflow = :workflow
            AND w.status = com.willa.ai.backend.entity.enums.WorkflowUsageStatus.FAILED
            """)
    Long failedCountForWorkflowInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("workflow") WorkflowType workflow);

    @Query("""
            SELECT w.workflow, COUNT(w)
            FROM WorkflowUsage w
            WHERE w.user.id = :userId
              AND w.startedAt >= :from
              AND w.startedAt <= :to
            GROUP BY w.workflow
            """)
    List<Object[]> countWorkflowsByUserSince(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.user.id IN :userIds
            AND w.status = com.willa.ai.backend.entity.enums.WorkflowUsageStatus.FAILED
            """)
    Long countFailedInRange(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(w)
            FROM WorkflowUsage w
            WHERE w.startedAt >= :from AND w.startedAt <= :to
            AND w.status = com.willa.ai.backend.entity.enums.WorkflowUsageStatus.FAILED
            """)
    Long countFailedInRangeAll(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
