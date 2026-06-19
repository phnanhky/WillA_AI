package com.willa.ai.backend.service;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Xóa dữ liệu task theo thứ tự FK (checklist → task) trước khi xóa workspace.
 */
@Component
@Slf4j
public class WorkspaceDataPurger {

    @PersistenceContext
    private EntityManager entityManager;

    public void purgeWorkspaceTaskData(Long workspaceId) {
        run(
                """
                DELETE FROM task_checklist_items AS i
                USING task_checklists AS c, tasks AS t
                WHERE i.checklist_id = c.id AND c.task_id = t.id AND t.workspace_id = :wid
                """,
                workspaceId,
                "task_checklist_items");

        run(
                """
                DELETE FROM task_checklists AS c
                USING tasks AS t
                WHERE c.task_id = t.id AND t.workspace_id = :wid
                """,
                workspaceId,
                "task_checklists");

        run(
                """
                DELETE FROM task_comments AS cm
                USING tasks AS t
                WHERE cm.task_id = t.id AND t.workspace_id = :wid
                """,
                workspaceId,
                "task_comments");

        run(
                """
                DELETE FROM task_attachments AS a
                USING tasks AS t
                WHERE a.task_id = t.id AND t.workspace_id = :wid
                """,
                workspaceId,
                "task_attachments");

        run(
                """
                DELETE FROM task_assignees AS a
                USING tasks AS t
                WHERE a.task_id = t.id AND t.workspace_id = :wid
                """,
                workspaceId,
                "task_assignees");

        run("DELETE FROM tasks WHERE workspace_id = :wid", workspaceId, "tasks");
        purgeTableIfExists("workspace_projects", workspaceId);
        purgeTableIfExists("workspace_library_images", workspaceId);
    }

    /** Xóa dữ liệu con của một task (FK) trước khi xóa bản ghi tasks. */
    public void purgeTaskData(Long taskId) {
        runForTask(
                """
                DELETE FROM task_checklist_items AS i
                USING task_checklists AS c
                WHERE i.checklist_id = c.id AND c.task_id = :tid
                """,
                taskId,
                "task_checklist_items");

        runForTask("DELETE FROM task_checklists WHERE task_id = :tid", taskId, "task_checklists");
        runForTask("DELETE FROM task_comments WHERE task_id = :tid", taskId, "task_comments");
        runForTask("DELETE FROM task_attachments WHERE task_id = :tid", taskId, "task_attachments");
        runForTask("DELETE FROM task_assignees WHERE task_id = :tid", taskId, "task_assignees");
    }

    private boolean tableExists(String tableName) {
        Object result = entityManager.createNativeQuery(
                """
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = :table
                )
                """)
                .setParameter("table", tableName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    /** Chỉ xóa bảng phụ nếu tồn tại — tránh abort transaction Postgres. */
    private void purgeTableIfExists(String tableName, Long workspaceId) {
        if (!tableExists(tableName)) {
            log.debug("skip purge {} (table missing) workspaceId={}", tableName, workspaceId);
            return;
        }
        run(
                "DELETE FROM " + tableName + " WHERE workspace_id = :wid",
                workspaceId,
                tableName);
    }

    private void run(String sql, Long workspaceId, String label) {
        int rows = entityManager.createNativeQuery(sql)
                .setParameter("wid", workspaceId)
                .executeUpdate();
        log.debug("purge {} workspaceId={} rows={}", label, workspaceId, rows);
    }

    private void runForTask(String sql, Long taskId, String label) {
        int rows = entityManager.createNativeQuery(sql)
                .setParameter("tid", taskId)
                .executeUpdate();
        log.debug("purge {} taskId={} rows={}", label, taskId, rows);
    }
}
