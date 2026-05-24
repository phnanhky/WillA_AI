package com.willa.ai.backend.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Chuẩn hóa response phân tích từ AI server (giữ code Python cũ).
 * Qwen thường gắn tọa độ trong {@code r} dạng {@code <box>(x1,y1),(x2,y2)</box>} mà không có {@code c}.
 * BE bổ sung {@code c} pixel + {@code isz/te/ss} để FE vẽ box overlay.
 */
public final class AiAnalysisEnricher {

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

        JsonNode sourceErrors = analysisData.get("e");
        if (sourceErrors == null || !sourceErrors.isArray() || sourceErrors.isEmpty()) {
            sourceErrors = analysisData.get("e_raw");
        }
        ArrayNode enriched = enrichErrors(sourceErrors, imgW, imgH);
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

    private static ArrayNode enrichErrors(JsonNode errorsNode, int imgW, int imgH) {
        ArrayNode out = MAPPER.createArrayNode();
        if (errorsNode == null || !errorsNode.isArray()) {
            return out;
        }

        Set<String> seen = new HashSet<>();
        List<ObjectNode> candidates = new ArrayList<>();

        for (JsonNode errNode : errorsNode) {
            if (!errNode.isObject()) {
                continue;
            }
            ObjectNode err = (ObjectNode) errNode;
            String reason = text(err, "r", "reason");
            String issue = text(err, "issue");
            String suggestion = text(err, "suggestion");
            if (reason.isEmpty() && (!issue.isEmpty() || !suggestion.isEmpty())) {
                reason = (issue + " " + suggestion).trim();
            }

            List<int[]> boxes = new ArrayList<>();
            JsonNode cNode = err.get("c");
            if (cNode == null) {
                cNode = err.get("box_2d");
            }
            if (cNode != null && cNode.isArray() && cNode.size() == 4) {
                boxes.add(toCoords(cNode));
            }
            for (String field : List.of(reason, issue, suggestion)) {
                boxes.addAll(parseBoxTags(field));
            }

            reason = stripBoxTags(reason);
            issue = stripBoxTags(issue);
            suggestion = stripBoxTags(suggestion);
            String display = !reason.isEmpty() ? reason : (!issue.isEmpty() ? issue : suggestion);
            if (display.isEmpty() || boxes.isEmpty()) {
                continue;
            }

            String severity = normalizeSeverity(err.path("s").asText("minor"));
            String category = normalizeCategory(err.path("g").asText("general"));

            for (int[] box : boxes) {
                int[] pixel = toPixelBox(box, imgW, imgH);
                if (pixel == null) {
                    continue;
                }
                String key = pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3];
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);

                ObjectNode item = MAPPER.createObjectNode();
                ArrayNode cArr = MAPPER.createArrayNode();
                for (int v : pixel) {
                    cArr.add(v);
                }
                item.set("c", cArr);
                item.put("r", cleanRuleMentions(display));
                if (!issue.isEmpty()) {
                    item.put("issue", issue);
                }
                if (!suggestion.isEmpty()) {
                    item.put("suggestion", suggestion);
                }
                item.put("s", severity);
                item.put("g", category);
                candidates.add(item);
            }
        }

        candidates.sort((a, b) -> severityRank(a.path("s").asText()) - severityRank(b.path("s").asText()));
        int limit = Math.min(5, candidates.size());
        for (int i = 0; i < limit; i++) {
            out.add(candidates.get(i));
        }
        return out;
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

    private static List<int[]> parseBoxTags(String text) {
        List<int[]> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        Matcher m = BOX_TAG.matcher(text);
        while (m.find()) {
            list.add(new int[] {
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4))
            });
        }
        return list;
    }

    private static String stripBoxTags(String text) {
        if (text == null) {
            return "";
        }
        return BOX_TAG.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private static int[] toPixelBox(int[] box, int imgW, int imgH) {
        int a0 = box[0], a1 = box[1], a2 = box[2], a3 = box[3];
        int x1, y1, x2, y2;
        boolean normalized = Math.max(Math.max(Math.abs(a0), Math.abs(a1)), Math.max(Math.abs(a2), Math.abs(a3))) <= 1000;
        if (normalized) {
            x1 = (int) ((long) Math.min(a0, a2) * imgW / 1000);
            y1 = (int) ((long) Math.min(a1, a3) * imgH / 1000);
            x2 = (int) ((long) Math.max(a0, a2) * imgW / 1000);
            y2 = (int) ((long) Math.max(a1, a3) * imgH / 1000);
        } else {
            x1 = Math.min(a0, a2);
            y1 = Math.min(a1, a3);
            x2 = Math.max(a0, a2);
            y2 = Math.max(a1, a3);
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
}
