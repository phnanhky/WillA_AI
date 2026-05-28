package com.willa.ai.backend.entity.enums;

/**
 * Product workflows tracked for usage time (wall-clock per request).
 */
public enum WorkflowType {
    /** Text-only or multi-turn chat without new image analysis */
    CHAT,
    /** Design feedback / image analysis (redesign tool) */
    ANALYZE,
    /** Text-to-image or image-to-image generate */
    GENERATE,
    /** Seed session before image-to-image generate */
    GENERATE_SEED,
    /** Fix design / regen image */
    REGEN,
    /** Prepare mask before regen */
    PREPARE_REGEN,
    SUGGEST_STYLE,
    EXTRACT_LAYERS,
    WORKSPACE
}
