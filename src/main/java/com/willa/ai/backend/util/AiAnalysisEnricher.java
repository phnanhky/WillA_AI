package com.willa.ai.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Chuẩn hóa response phân tích từ AI server → hợp đồng {@code source_pixel} cho FE.
 * <p>Qwen: {@code c} = [xmin,ymin,xmax,ymax] grid 0–1000; tag {@code <box>(ymin,xmin),(ymax,xmax)}.
 * AI {@code isz} = analysis frame (≤1536). Sau {@link #remapAnalysisToSourceSize}: {@code isz} = upload gốc.
 * Xem docs/BOUNDING_BOX_COORDINATES.md.
 */
public final class AiAnalysisEnricher {

    public static final String COORD_SPACE_FRAME_PIXEL = "frame_pixel";
    public static final String COORD_SPACE_SOURCE_PIXEL = "source_pixel";
    public static final int QWEN_GRID_MAX = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BOX_TAG = Pattern.compile(
            "<box\\s*>?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)\\s*,\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)\\s*</box\\s*>?",
            Pattern.CASE_INSENSITIVE);

    private AiAnalysisEnricher() {
    }

    public static JsonNode enrich(JsonNode root) {
        if (root == null || !root.isObject()) {
            return root;
        }
        ObjectNode obj = (ObjectNode) root;
        String type = obj.path("type").asText("");
        if (!"analysis".equals(type)) {
            return root;
        }
        JsonNode ad = obj.get("analysis_data");
        if (ad == null || !ad.isObject()) {
            return root;
        }
        ObjectNode analysisData = (ObjectNode) ad;
        int imgW = analysisData.path("isz").path("w").asInt(0);
        int imgH = analysisData.path("isz").path("h").asInt(0);
        if (imgW <= 0 || imgH <= 0) {
            JsonNode analyzedSize = analysisData.get("analyzed_size");
            if (analyzedSize != null && analyzedSize.isArray() && analyzedSize.size() >= 2) {
                imgW = analyzedSize.get(0).asInt(1);
                imgH = analyzedSize.get(1).asInt(1);
            } else {
                imgW = 1;
                imgH = 1;
            }
        }

        JsonNode displayErrors = analysisData.get("e");
        JsonNode coordErrors = analysisData.get("e_raw");
        if (displayErrors == null || !displayErrors.isArray() || displayErrors.isEmpty()) {
            displayErrors = coordErrors;
        }
        if (coordErrors == null || !coordErrors.isArray() || coordErrors.isEmpty()) {
            coordErrors = displayErrors;
        }
        String coordSpace = analysisData.path("coord_space").asText("");
        ArrayNode enriched = enrichErrors(displayErrors, coordErrors, imgW, imgH, coordSpace);
        analysisData.set("e", enriched);
        analysisData.set("isz", MAPPER.createObjectNode().put("w", imgW).put("h", imgH));
        analysisData.put("te", enriched.size());

        ObjectNode ss = MAPPER.createObjectNode();
        int minor = 0, major = 0, critical = 0;
        for (JsonNode item : enriched) {
            switch (item.path("s").asText("minor")) {
                case "critical" -> critical++;
                case "major" -> major++;
                default -> minor++;
            }
        }
        ss.put("minor", minor).put("major", major).put("critical", critical);
        analysisData.set("ss", ss);
        return root;
    }

    private static ArrayNode enrichErrors(
            JsonNode displayErrors,
            JsonNode coordErrors,
            int imgW,
            int imgH,
            String coordSpace) {
        ArrayNode out = MAPPER.createArrayNode();
        if (displayErrors == null || !displayErrors.isArray()) {
            return out;
        }

        List<ObjectNode> items = new ArrayList<>();

        for (int i = 0; i < displayErrors.size(); i++) {
            JsonNode errNode = displayErrors.get(i);
            if (!errNode.isObject()) {
                continue;
            }
            ObjectNode err = (ObjectNode) errNode;
            ObjectNode errCoords = err;
            if (coordErrors != null && coordErrors.isArray() && i < coordErrors.size()
                    && coordErrors.get(i).isObject()) {
                errCoords = (ObjectNode) coordErrors.get(i);
            }

            String reason = text(err, "r", "reason");
            String issue = text(err, "issue");
            String suggestion = text(err, "suggestion");
            if (reason.isEmpty() && (!issue.isEmpty() || !suggestion.isEmpty())) {
                reason = (issue + " " + suggestion).trim();
            }

            reason = stripBoxTags(reason);
            issue = stripBoxTags(issue);
            suggestion = stripBoxTags(suggestion);
            String display = !reason.isEmpty() ? reason : (!issue.isEmpty() ? issue : suggestion);
            if (display.isEmpty()) {
                continue;
            }

            String severity = normalizeSeverity(err.path("s").asText("minor"));
            String category = normalizeCategory(err.path("g").asText("general"));

            int[] pixel = resolvePixelForError(
                    err,
                    errCoords,
                    coordErrors,
                    reason,
                    issue,
                    suggestion,
                    imgW,
                    imgH,
                    coordSpace);

            ObjectNode item = MAPPER.createObjectNode();
            if (pixel != null) {
                ArrayNode cArr = MAPPER.createArrayNode();
                for (int v : pixel) {
                    cArr.add(v);
                }
                item.set("c", cArr);
                JsonNode cGrid = err.get("c_grid");
                if (cGrid == null && errCoords != null) {
                    cGrid = errCoords.get("c_grid");
                }
                if (cGrid == null && errCoords != null && errCoords.get("c") != null
                        && isQwenGrid1000(toCoords(errCoords.get("c")), coordSpace, imgW, imgH)) {
                    item.set("c_grid", errCoords.get("c"));
                } else if (cGrid != null) {
                    item.set("c_grid", cGrid);
                }
            }
            item.put("r", cleanRuleMentions(display));
            if (!issue.isEmpty()) {
                item.put("issue", issue);
            }
            if (!suggestion.isEmpty()) {
                item.put("suggestion", suggestion);
            }
            item.put("s", severity);
            item.put("g", category);
            
            JsonNode reference = err.get("reference");
            if (reference != null) {
                item.set("reference", reference);
            }
            
            items.add(item);
        }

        items.sort((a, b) -> severityRank(a.path("s").asText()) - severityRank(b.path("s").asText()));
        int limit = Math.min(5, items.size());
        for (int i = 0; i < limit; i++) {
            out.add(items.get(i));
        }
        return out;
    }

    /** Một lỗi → một pixel box tốt nhất (không dedupe giữa các lỗi — cho phép overlap). */
    private static int[] resolvePixelForError(
            ObjectNode err,
            ObjectNode errCoords,
            JsonNode coordErrors,
            String reason,
            String issue,
            String suggestion,
            int imgW,
            int imgH,
            String coordSpace) {
        List<int[]> boxes = new ArrayList<>();
        List<Boolean> boxesArePixels = new ArrayList<>();
        collectBoxesForError(
                err,
                errCoords,
                coordErrors,
                reason,
                issue,
                suggestion,
                imgW,
                imgH,
                coordSpace,
                boxes,
                boxesArePixels);

        int[] best = null;
        double bestScore = -1;
        for (int bi = 0; bi < boxes.size(); bi++) {
            int[] pixel = boxesArePixels.get(bi)
                    ? clampPixelBox(boxes.get(bi), imgW, imgH)
                    : toPixelBoxFromGrid1000(boxes.get(bi), imgW, imgH);
            if (pixel == null) {
                continue;
            }
            double score = scorePixelBox(pixel, imgW, imgH);
            if (score > bestScore) {
                bestScore = score;
                best = pixel;
            }
        }
        return best;
    }

    private static String text(ObjectNode err, String... keys) {
        for (String key : keys) {
            if (err.has(key) && err.get(key).isTextual()) {
                return err.get(key).asText("").trim();
            }
        }
        return "";
    }

    private static int[] toCoords(JsonNode cNode) {
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) {
            c[i] = cNode.get(i).asInt(0);
        }
        return c;
    }

    private static void collectBoxesForError(
            ObjectNode err,
            ObjectNode errCoords,
            JsonNode coordErrors,
            String reason,
            String issue,
            String suggestion,
            int imgW,
            int imgH,
            String coordSpace,
            List<int[]> boxes,
            List<Boolean> boxesArePixels) {
        appendBoxFromNode(err, imgW, imgH, coordSpace, boxes, boxesArePixels);

        if (boxes.isEmpty()) {
            int[] resolved = resolveBestBox(err, reason, issue, suggestion, imgW, imgH, coordSpace);
            appendResolvedBox(resolved, coordSpace, imgW, imgH, boxes, boxesArePixels);
        }
        if (boxes.isEmpty() && errCoords != null && errCoords != err) {
            int[] resolved = resolveBestBox(
                    errCoords,
                    text(errCoords, "r", "reason"),
                    text(errCoords, "issue"),
                    text(errCoords, "suggestion"),
                    imgW,
                    imgH,
                    coordSpace);
            appendResolvedBox(resolved, coordSpace, imgW, imgH, boxes, boxesArePixels);
        }
        if (boxes.isEmpty()) {
            int[] fromPool = resolveBestBoxFromRawPool(
                    coordErrors, issue, reason, suggestion, imgW, imgH, coordSpace);
            appendResolvedBox(fromPool, coordSpace, imgW, imgH, boxes, boxesArePixels);
        }
    }

    private static void appendBoxFromNode(
            ObjectNode err,
            int imgW,
            int imgH,
            String coordSpace,
            List<int[]> boxes,
            List<Boolean> boxesArePixels) {
        JsonNode cGrid = err.get("c_grid");
        if (cGrid != null && cGrid.isArray() && cGrid.size() == 4) {
            int[] grid = toCoords(cGrid);
            if (isQwenGrid1000(grid, coordSpace, imgW, imgH)) {
                boxes.add(grid);
                boxesArePixels.add(false);
                return;
            }
        }
        JsonNode cNode = err.get("c");
        if (cNode == null) {
            cNode = err.get("box_2d");
        }
        if (cNode == null || !cNode.isArray() || cNode.size() != 4) {
            return;
        }
        int[] raw = toCoords(cNode);
        if (isQwenGrid1000(raw, coordSpace, imgW, imgH) || looksLikeMislabeledGrid(raw, imgW, imgH)) {
            boxes.add(raw);
            boxesArePixels.add(false);
            return;
        }
        int[] framePixel = clampPixelBox(raw, imgW, imgH, false);
        if (framePixel != null) {
            boxes.add(framePixel);
            boxesArePixels.add(true);
        }
    }

    private static void appendResolvedBox(
            int[] resolved,
            String coordSpace,
            int imgW,
            int imgH,
            List<int[]> boxes,
            List<Boolean> boxesArePixels) {
        if (resolved == null) {
            return;
        }
        if (isQwenGrid1000FromModel(resolved)
                || isQwenGrid1000(resolved, coordSpace, imgW, imgH)
                || looksLikeMislabeledGrid(resolved, imgW, imgH)) {
            boxes.add(resolved);
            boxesArePixels.add(false);
        } else {
            boxes.add(resolved);
            boxesArePixels.add(true);
        }
    }

    private static int[] resolveBestBoxFromRawPool(
            JsonNode coordErrors,
            String issue,
            String reason,
            String suggestion,
            int imgW,
            int imgH,
            String coordSpace) {
        if (coordErrors == null || !coordErrors.isArray()) {
            return null;
        }
        int[] best = null;
        double bestScore = -1;
        for (JsonNode node : coordErrors) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode raw = (ObjectNode) node;
            String rawIssue = text(raw, "issue");
            String rawReason = text(raw, "r", "reason");
            String rawSuggestion = text(raw, "suggestion");
            int[] resolved = resolveBestBox(raw, rawReason, rawIssue, rawSuggestion, imgW, imgH, coordSpace);
            if (resolved == null) {
                continue;
            }
            int[] pixel = isQwenGrid1000(resolved, coordSpace, imgW, imgH)
                    || looksLikeMislabeledGrid(resolved, imgW, imgH)
                    ? toPixelBoxFromGrid1000(resolved, imgW, imgH)
                    : clampPixelBox(resolved, imgW, imgH, false);
            if (pixel == null) {
                continue;
            }
            double score = scorePixelBox(pixel, imgW, imgH);
            if (!issue.isEmpty() && issue.equals(rawIssue)) {
                score += 5;
            } else if (!reason.isEmpty() && reason.equals(rawReason)) {
                score += 3;
            } else if (!issue.isEmpty() && !rawIssue.isEmpty()
                    && (issue.startsWith(rawIssue) || rawIssue.startsWith(issue))) {
                score += 4;
            }
            if (score > bestScore) {
                bestScore = score;
                best = resolved;
            }
        }
        return best;
    }

    private static List<int[]> parseBoxTags(String text) {
        List<int[]> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        Matcher m = BOX_TAG.matcher(text);
        while (m.find()) {
            // Prompt format: <box>(ymin,xmin),(ymax,xmax)</box>
            int ymin = Integer.parseInt(m.group(1));
            int xmin = Integer.parseInt(m.group(2));
            int ymax = Integer.parseInt(m.group(3));
            int xmax = Integer.parseInt(m.group(4));
            list.add(new int[] { xmin, ymin, xmax, ymax });
        }
        return list;
    }

    private static String stripBoxTags(String text) {
        if (text == null) {
            return "";
        }
        return BOX_TAG.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /** Grid 0–1000 từ model (tag / JSON c) — luôn chia /1000, không dùng heuristic pixel. */
    private static boolean isQwenGrid1000FromModel(int[] c) {
        if (c == null || c.length < 4) {
            return false;
        }
        for (int v : c) {
            if (v < 0 || v > QWEN_GRID_MAX) {
                return false;
            }
        }
        return true;
    }

    /** Qwen grid 0–1000; không nhầm pixel khi coord_space đã là frame/source. */
    private static boolean isQwenGrid1000(int[] c, String coordSpace, int imgW, int imgH) {
        if (COORD_SPACE_FRAME_PIXEL.equals(coordSpace) || COORD_SPACE_SOURCE_PIXEL.equals(coordSpace)) {
            return false;
        }
        if (c == null || c.length < 4) {
            return false;
        }
        for (int v : c) {
            if (v < 0 || v > QWEN_GRID_MAX) {
                return false;
            }
        }
        if (imgW > 0 && imgH > 0) {
            int x1 = Math.min(c[0], c[2]);
            int x2 = Math.max(c[0], c[2]);
            int y1 = Math.min(c[1], c[3]);
            int y2 = Math.max(c[1], c[3]);
            int w = x2 - x1;
            int h = y2 - y1;
            if (w >= 5 && h >= 5 && x2 <= imgW && y2 <= imgH) {
                if (imgW > QWEN_GRID_MAX || imgH > QWEN_GRID_MAX) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isQwenGrid1000(int[] c) {
        return isQwenGrid1000(c, "", 0, 0);
    }

    /**
     * Chọn box tốt nhất: JSON {@code c} = [xmin,ymin,xmax,ymax] (ưu tiên), tag {@code <box>}.
     * Model thường ghi tag (xmin,ymin),(xmax,ymax) dù prompt ghi (ymin,xmin).
     */
    private static int[] resolveBestBox(
            ObjectNode err,
            String reason,
            String issue,
            String suggestion,
            int imgW,
            int imgH,
            String coordSpace) {
        List<int[]> candidates = new ArrayList<>();
        java.util.Map<String, Double> tagBonuses = new java.util.HashMap<>();
        JsonNode cNode = err.get("c");
        if (cNode == null) {
            cNode = err.get("box_2d");
        }
        if (cNode == null) {
            cNode = err.get("c_grid");
        }
        int[] cRaw = null;
        if (cNode != null && cNode.isArray() && cNode.size() == 4) {
            cRaw = toCoords(cNode);
            candidates.add(cRaw);
            candidates.add(new int[] { cRaw[1], cRaw[0], cRaw[3], cRaw[2] });
        }
        for (String field : List.of(reason, issue, suggestion)) {
            Matcher m = BOX_TAG.matcher(field != null ? field : "");
            while (m.find()) {
                int v1 = Integer.parseInt(m.group(1));
                int v2 = Integer.parseInt(m.group(2));
                int v3 = Integer.parseInt(m.group(3));
                int v4 = Integer.parseInt(m.group(4));
                addBoxTagCandidates(candidates, tagBonuses, v1, v2, v3, v4);
            }
        }

        int[] best = null;
        double bestScore = -1;
        for (int[] cand : candidates) {
            if (cand == null || cand.length < 4) {
                continue;
            }
            int[] pixel;
            if (tagBonuses.containsKey(coordsKey(cand)) && isQwenGrid1000FromModel(cand)) {
                pixel = toPixelBoxFromGrid1000(cand, imgW, imgH);
            } else if (isQwenGrid1000(cand, coordSpace, imgW, imgH)
                    || looksLikeMislabeledGrid(cand, imgW, imgH)) {
                pixel = toPixelBoxFromGrid1000(cand, imgW, imgH);
            } else {
                pixel = clampPixelBox(cand, imgW, imgH, false);
            }
            if (pixel == null) {
                continue;
            }
            double score = scorePixelBox(pixel, imgW, imgH);
            if (cRaw != null && java.util.Arrays.equals(cand, cRaw)) {
                score += 5;
            }
            score += tagBonuses.getOrDefault(coordsKey(cand), 0.0);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    private static String coordsKey(int[] c) {
        return c[0] + "," + c[1] + "," + c[2] + "," + c[3];
    }

    private static void addBoxTagCandidates(
            List<int[]> candidates,
            java.util.Map<String, Double> tagBonuses,
            int v1,
            int v2,
            int v3,
            int v4) {
        int[] direct = new int[] {
                Math.min(v1, v3), Math.min(v2, v4), Math.max(v1, v3), Math.max(v2, v4)
        };
        int[] yminXmin = new int[] {
                Math.min(v2, v4), Math.min(v1, v3), Math.max(v2, v4), Math.max(v1, v3)
        };
        addTagCandidate(candidates, tagBonuses, direct, 3.0);
        addTagCandidate(candidates, tagBonuses, yminXmin, 1.0);
        addTagCandidate(candidates, tagBonuses, new int[] { v2, v1, v4, v3 }, 0.0);
    }

    private static void addTagCandidate(
            List<int[]> candidates,
            java.util.Map<String, Double> tagBonuses,
            int[] coords,
            double bonus) {
        String key = coordsKey(coords);
        if (!tagBonuses.containsKey(key)) {
            candidates.add(coords);
        }
        tagBonuses.put(key, Math.max(tagBonuses.getOrDefault(key, 0.0), bonus));
    }

    private static double scorePixelBox(int[] box, int imgW, int imgH) {
        if (box == null || box.length < 4 || imgW <= 0 || imgH <= 0) {
            return -1;
        }
        int w = box[2] - box[0];
        int h = box[3] - box[1];
        if (w < 5 || h < 5) {
            return -1;
        }
        double imgArea = (double) imgW * imgH;
        double area = (double) w * h;
        double ratio = area / imgArea;
        if (ratio > 0.92) {
            return -1;
        }
        if (ratio < 0.00005) {
            return -1;
        }
        double aspect = (double) w / Math.max(h, 1);
        double aspectScore = aspect >= 0.15 && aspect <= 8 ? 2 : 0;
        return Math.min(ratio * 500, 8) + aspectScore;
    }

    /** Pixel box — clamp; optional 1px pad sau grid→pixel. */
    private static int[] clampPixelBox(int[] box, int imgW, int imgH) {
        return clampPixelBox(box, imgW, imgH, true);
    }

    private static int[] clampPixelBox(int[] box, int imgW, int imgH, boolean padOnePx) {
        if (box == null || box.length < 4 || imgW <= 0 || imgH <= 0) {
            return null;
        }
        int x1 = Math.min(box[0], box[2]);
        int y1 = Math.min(box[1], box[3]);
        int x2 = Math.max(box[0], box[2]);
        int y2 = Math.max(box[1], box[3]);
        if (padOnePx && imgW > 2 && imgH > 2) {
            x1 = Math.max(0, x1 - 1);
            y1 = Math.max(0, y1 - 1);
            x2 = Math.min(imgW, x2 + 1);
            y2 = Math.min(imgH, y2 + 1);
        }
        x1 = clamp(x1, 0, imgW);
        y1 = clamp(y1, 0, imgH);
        x2 = clamp(x2, 0, imgW);
        y2 = clamp(y2, 0, imgH);
        if (x2 - x1 < 5 || y2 - y1 < 5) {
            return null;
        }
        return new int[] { x1, y1, x2, y2 };
    }

    /** Convert Qwen grid 0–1000 or inline tag coords to pixel box. */
    private static int[] toPixelBoxFromGrid1000(int[] box, int imgW, int imgH) {
        if (box == null || box.length < 4 || imgW <= 0 || imgH <= 0) {
            return null;
        }
        int a0 = box[0], a1 = box[1], a2 = box[2], a3 = box[3];
        int x1 = (int) ((long) Math.min(a0, a2) * imgW / 1000);
        int y1 = (int) ((long) Math.min(a1, a3) * imgH / 1000);
        int x2 = (int) ((long) Math.max(a0, a2) * imgW / 1000);
        int y2 = (int) ((long) Math.max(a1, a3) * imgH / 1000);
        return clampPixelBox(new int[] { x1, y1, x2, y2 }, imgW, imgH);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static String cleanRuleMentions(String text) {
        return text
                .replaceAll("(?i)\\bRules?\\s+\\d+([-&,]\\d+)?\\s*[:\\-—]+\\s*", "")
                .replaceAll("(?i)\\bRules?\\s+\\d+([-&,]\\d+)?\\b", "")
                .replaceAll("\\(\\s*\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeSeverity(String s) {
        String v = s == null ? "" : s.toLowerCase().trim();
        if ("critical".equals(v) || "major".equals(v) || "minor".equals(v)) {
            return v;
        }
        return "minor";
    }

    private static String normalizeCategory(String g) {
        String v = g == null ? "" : g.toLowerCase().trim();
        return switch (v) {
            case "color_theory", "typography", "layout_rules", "logo_design", "poster_design",
                 "icon_design", "pattern_design", "general" -> v;
            default -> "general";
        };
    }

    private static int severityRank(String s) {
        return switch (s) {
            case "critical" -> 0;
            case "major" -> 1;
            default -> 2;
        };
    }

    /**
     * AI phân tích trên ảnh đã thumbnail ≤1536px ({@code isz}); FE/R2 dùng ảnh upload gốc.
     * Đưa {@code c} về pixel trên ảnh gốc và cập nhật {@code isz} = source.
     */
    public static void remapAnalysisToSourceSize(ObjectNode analysisData, int sourceW, int sourceH) {
        if (analysisData == null || sourceW <= 0 || sourceH <= 0) {
            return;
        }
        int frameW = analysisData.path("isz").path("w").asInt(0);
        int frameH = analysisData.path("isz").path("h").asInt(0);
        if (frameW <= 0 || frameH <= 0) {
            int[] frame = AiImageFrameUtil.analysisFrameSize(sourceW, sourceH);
            frameW = frame[0];
            frameH = frame[1];
        }
        if (frameW == sourceW && frameH == sourceH) {
            analysisData.put("coord_space", COORD_SPACE_SOURCE_PIXEL);
            return;
        }

        JsonNode errors = analysisData.get("e");
        if (errors != null && errors.isArray()) {
            for (JsonNode errNode : errors) {
                if (!errNode.isObject()) {
                    continue;
                }
                ObjectNode err = (ObjectNode) errNode;
                JsonNode cNode = err.get("c");
                if (cNode == null || !cNode.isArray() || cNode.size() != 4) {
                    continue;
                }
                int[] scaled = AiImageFrameUtil.scalePixelBoxToSource(
                        toCoords(cNode), frameW, frameH, sourceW, sourceH);
                scaled = clampPixelBox(scaled, sourceW, sourceH, false);
                if (scaled == null) {
                    continue;
                }
                ArrayNode cArr = MAPPER.createArrayNode();
                for (int v : scaled) {
                    cArr.add(v);
                }
                err.set("c", cArr);
            }
        }

        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("w", frameW).put("h", frameH);
        analysisData.set("analysis_frame", frame);
        analysisData.set("isz", MAPPER.createObjectNode().put("w", sourceW).put("h", sourceH));
        analysisData.put("coord_space", COORD_SPACE_SOURCE_PIXEL);
    }

    /**
     * Khóa hợp đồng lưu DB / trả FE / seed AI: {@code isz} = ảnh hiển thị (R2),
     * {@code e[].c} pixel trên {@code isz}, {@code analysis_frame} = khung AI ≤1536.
     */
    public static void finalizeForDisplayStorage(ObjectNode analysisData, int displayW, int displayH) {
        if (analysisData == null || displayW <= 0 || displayH <= 0) {
            return;
        }
        int frameW = analysisData.path("isz").path("w").asInt(0);
        int frameH = analysisData.path("isz").path("h").asInt(0);
        if (frameW > 0 && frameH > 0) {
            rescueMislabeledGridCoords(analysisData, frameW, frameH);
        }
        remapAnalysisToSourceSize(analysisData, displayW, displayH);
        rescueMislabeledGridCoords(analysisData, displayW, displayH);
        analysisData.put("coord_space", COORD_SPACE_SOURCE_PIXEL);
    }

    /**
     * Grid 0–1000 đôi khi còn trong {@code c} khi {@code coord_space} đã là pixel
     * (ảnh lớn → bbox dồn góc trên-trái trên FE).
     */
    private static void rescueMislabeledGridCoords(ObjectNode analysisData, int displayW, int displayH) {
        if (displayW <= QWEN_GRID_MAX && displayH <= QWEN_GRID_MAX) {
            return;
        }
        int frameW = analysisData.path("analysis_frame").path("w").asInt(0);
        int frameH = analysisData.path("analysis_frame").path("h").asInt(0);
        if (frameW <= 0 || frameH <= 0) {
            frameW = analysisData.path("isz").path("w").asInt(displayW);
            frameH = analysisData.path("isz").path("h").asInt(displayH);
        }
        JsonNode errors = analysisData.get("e");
        if (errors == null || !errors.isArray()) {
            return;
        }
        for (JsonNode errNode : errors) {
            if (!errNode.isObject()) {
                continue;
            }
            ObjectNode err = (ObjectNode) errNode;
            JsonNode cNode = err.get("c");
            if (cNode == null || !cNode.isArray() || cNode.size() != 4) {
                continue;
            }
            int[] c = toCoords(cNode);
            if (!looksLikeMislabeledGrid(c, displayW, displayH)) {
                continue;
            }
            int[] pixelOnFrame = toPixelBoxFromGrid1000(c, frameW, frameH);
            if (pixelOnFrame == null) {
                continue;
            }
            int[] pixelOnDisplay = AiImageFrameUtil.scalePixelBoxToSource(
                    pixelOnFrame, frameW, frameH, displayW, displayH);
            pixelOnDisplay = clampPixelBox(pixelOnDisplay, displayW, displayH, false);
            if (pixelOnDisplay == null) {
                continue;
            }
            ArrayNode cArr = MAPPER.createArrayNode();
            for (int v : pixelOnDisplay) {
                cArr.add(v);
            }
            err.set("c", cArr);
        }
    }

    private static boolean looksLikeMislabeledGrid(int[] c, int imgW, int imgH) {
        if (imgW <= QWEN_GRID_MAX && imgH <= QWEN_GRID_MAX) {
            return false;
        }
        if (c == null || c.length < 4) {
            return false;
        }
        for (int v : c) {
            if (v < 0 || v > QWEN_GRID_MAX) {
                return false;
            }
        }
        int x1 = Math.min(c[0], c[2]);
        int x2 = Math.max(c[0], c[2]);
        int y1 = Math.min(c[1], c[3]);
        int y2 = Math.max(c[1], c[3]);
        int w = x2 - x1;
        int h = y2 - y1;
        if ((double) w / Math.max(imgW, 1) > 0.45 || (double) h / Math.max(imgH, 1) > 0.45) {
            return false;
        }
        double pixelAreaRatio = (double) w * h / Math.max((double) imgW * imgH, 1.0);
        double gridAreaRatio = (double) w / QWEN_GRID_MAX * (double) h / QWEN_GRID_MAX;
        return gridAreaRatio > pixelAreaRatio * 1.5;
    }
}
