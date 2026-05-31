package com.willa.ai.backend.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.willa.ai.backend.config.PersonaProperties;
import com.willa.ai.backend.dto.request.PersonaSettingsRequest;
import com.willa.ai.backend.dto.response.PersonaResponse;
import com.willa.ai.backend.entity.ChatMessage;
import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.UserPersona;
import com.willa.ai.backend.entity.enums.MessageRole;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import com.willa.ai.backend.entity.enums.WorkflowType;
import com.willa.ai.backend.exception.PersonaRateLimitException;
import com.willa.ai.backend.repository.ChatMessageRepository;
import com.willa.ai.backend.repository.SubscriptionRepository;
import com.willa.ai.backend.repository.UserPersonaRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkflowUsageRepository;
import com.willa.ai.backend.service.PersonaService;
import com.willa.ai.backend.util.PersonaAnalysisParser;
import com.willa.ai.backend.util.PersonaAnalysisParser.DesignSignals;
import com.willa.ai.backend.util.PersonaTextSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonaServiceImpl implements PersonaService {

    private final UserRepository userRepository;
    private final UserPersonaRepository userPersonaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkflowUsageRepository workflowUsageRepository;
    private final PersonaProperties personaProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PersonaResponse getMyPersona(String authenticatedEmail) {
        User user = requireActiveUser(authenticatedEmail);
        try {
            UserPersona stored = userPersonaRepository.findByUserId(user.getId()).orElse(null);
            if (stored == null) {
                return rebuildInternal(user, false);
            }
            if (Boolean.TRUE.equals(stored.getEnabled()) && stored.getAiContextJson() == null) {
                return rebuildInternal(user, false);
            }
            return mapStoredToResponse(user, stored);
        } catch (Exception e) {
            log.error("getMyPersona failed [userId={}]: {}", user.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public PersonaResponse updateMySettings(String authenticatedEmail, PersonaSettingsRequest request) {
        User user = requireActiveUser(authenticatedEmail);
        UserPersona persona = getOrCreatePersona(user);
        persona.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        persistPersona(persona, user.getId(), true);
        log.info("Persona settings updated [userId={}, enabled={}]", user.getId(), persona.getEnabled());
        if (Boolean.TRUE.equals(persona.getEnabled())) {
            return rebuildInternal(user, false);
        }
        return mapStoredToResponse(user, persona);
    }

    @Override
    @Transactional
    public PersonaResponse refreshMyPersona(String authenticatedEmail, boolean enforceCooldown) {
        User user = requireActiveUser(authenticatedEmail);
        UserPersona persona = getOrCreatePersona(user);
        if (enforceCooldown) {
            assertRefreshAllowed(persona);
        }
        return rebuildInternal(user, enforceCooldown);
    }

    @Override
    @Transactional(readOnly = true)
    public String getAiContextJsonForUser(Long userId) {
        if (!personaProperties.isEnabled() || userId == null) {
            return null;
        }
        try {
            UserPersona persona = userPersonaRepository.findByUserId(userId).orElse(null);
            if (persona == null || !Boolean.TRUE.equals(persona.getEnabled())) {
                return null;
            }
            String json = persona.getAiContextJson();
            if (json == null || json.isBlank()) {
                return null;
            }
            int max = personaProperties.getMaxAiContextChars();
            return json.length() > max ? json.substring(0, max) : json;
        } catch (Exception e) {
            log.warn("Failed to load persona AI context [userId={}]: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void refreshAfterAnalysis(Long userId) {
        if (!personaProperties.isEnabled() || userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            try {
                UserPersona persona = userPersonaRepository.findByUserId(userId).orElse(null);
                if (persona != null && !Boolean.TRUE.equals(persona.getEnabled())) {
                    return;
                }
                rebuildInternal(user, false);
                log.debug("Persona refreshed after analysis [userId={}]", userId);
            } catch (Exception e) {
                log.warn("Persona refresh after analysis failed [userId={}]: {}", userId, e.getMessage());
            }
        });
    }

    private PersonaResponse rebuildInternal(User user, boolean userInitiated) {
        UserPersona persona = getOrCreatePersona(user);
        boolean active = personaProperties.isEnabled() && Boolean.TRUE.equals(persona.getEnabled());

        BuiltPersona built = active ? buildFromData(user) : BuiltPersona.empty();

        if (active) {
            persona.setAiContextJson(built.aiContextJson());
            persona.setDisplaySummary(built.displaySummary());
            persona.setAnalysisCountUsed(built.analysisCount());
        } else {
            persona.setAiContextJson(null);
            persona.setDisplaySummary(null);
            persona.setAnalysisCountUsed(0);
        }
        persona.setLastRefreshedAt(LocalDateTime.now());
        persistPersona(persona, user.getId(), userInitiated);

        if (userInitiated) {
            log.info("Persona rebuilt [userId={}, analyses={}]", user.getId(), built.analysisCount());
        }

        return mapBuiltToResponse(user, persona, built, active);
    }

    private BuiltPersona buildFromData(User user) {
        try {
            return buildFromDataInternal(user);
        } catch (Exception e) {
            log.warn("Persona buildFromData failed [userId={}]: {}", user.getId(), e.getMessage());
            return BuiltPersona.empty();
        }
    }

    private BuiltPersona buildFromDataInternal(User user) {
        int maxAnalyses = clamp(personaProperties.getMaxRecentAnalyses(), 1, 10);
        List<String> contents = chatMessageRepository
                .findRecentAnalysisMessagesByUserId(user.getId(), MessageRole.AI, PageRequest.of(0, maxAnalyses))
                .stream()
                .map(ChatMessage::getContent)
                .toList();

        DesignSignals signals = PersonaAnalysisParser.aggregate(contents, 2);
        String planTier = resolvePlanTier(user.getId());
        String occupation = PersonaTextSanitizer.sanitizeLabel(
                user.getOccupation(), personaProperties.getMaxTextFieldLength());
        Map<String, Long> workflowCounts = loadWorkflowCounts(user.getId());
        List<String> topCategories = PersonaAnalysisParser.topCategories(
                signals.getCategoryCounts(), personaProperties.getMaxTopCategories());

        PersonaResponse view = PersonaResponse.builder()
                .profile(PersonaResponse.PersonaProfileView.builder()
                        .occupationLabel(occupation)
                        .planTier(planTier)
                        .isStudent(Boolean.TRUE.equals(user.getIsStudent()))
                        .build())
                .behavior(PersonaResponse.PersonaBehaviorView.builder()
                        .primaryWorkflow(resolvePrimaryWorkflow(workflowCounts))
                        .workflowCounts30d(workflowCounts)
                        .build())
                .designPatterns(PersonaResponse.PersonaDesignPatternsView.builder()
                        .recentAnalysisCount(signals.getParsedAnalyses())
                        .topIssueCategories(topCategories)
                        .severityMix(signals.getSeverityCounts())
                        .focusHints(signals.getFocusHints().stream().limit(6).toList())
                        .build())
                .build();

        return new BuiltPersona(
                buildAiContextJson(view),
                buildDisplaySummary(view),
                signals.getParsedAnalyses(),
                view);
    }

    private PersonaResponse mapStoredToResponse(User user, UserPersona persona) {
        boolean active = personaProperties.isEnabled() && Boolean.TRUE.equals(persona.getEnabled());
        if (!active) {
            return PersonaResponse.builder()
                    .enabled(false)
                    .updatedAt(persona.getUpdatedAt())
                    .nextRefreshAllowedAt(buildNextRefreshAt(persona))
                    .build();
        }
        BuiltPersona built = buildFromData(user);
        return mapBuiltToResponse(user, persona, built, true);
    }

    private PersonaResponse mapBuiltToResponse(
            User user, UserPersona persona, BuiltPersona built, boolean active) {
        return PersonaResponse.builder()
                .enabled(active)
                .summary(active ? built.displaySummary() : null)
                .profile(active ? built.view().getProfile() : null)
                .behavior(active ? built.view().getBehavior() : null)
                .designPatterns(active ? built.view().getDesignPatterns() : null)
                .updatedAt(persona.getUpdatedAt())
                .nextRefreshAllowedAt(buildNextRefreshAt(persona))
                .build();
    }

    private String buildAiContextJson(PersonaResponse view) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", 1);

            ObjectNode profile = root.putObject("profile");
            if (view.getProfile() != null) {
                putIfPresent(profile, "occupationLabel", view.getProfile().getOccupationLabel());
                putIfPresent(profile, "planTier", view.getProfile().getPlanTier());
                profile.put("isStudent", Boolean.TRUE.equals(view.getProfile().getIsStudent()));
            }

            ObjectNode behavior = root.putObject("behavior");
            if (view.getBehavior() != null) {
                putIfPresent(behavior, "primaryWorkflow", view.getBehavior().getPrimaryWorkflow());
                if (view.getBehavior().getWorkflowCounts30d() != null
                        && !view.getBehavior().getWorkflowCounts30d().isEmpty()) {
                    ObjectNode counts = behavior.putObject("workflowCounts30d");
                    view.getBehavior().getWorkflowCounts30d().forEach((k, v) -> counts.put(k, v));
                }
            }

            ObjectNode design = root.putObject("designPatterns");
            if (view.getDesignPatterns() != null) {
                design.put("recentAnalysisCount", view.getDesignPatterns().getRecentAnalysisCount());
                if (view.getDesignPatterns().getTopIssueCategories() != null) {
                    design.putPOJO("topIssueCategories", view.getDesignPatterns().getTopIssueCategories());
                }
                if (view.getDesignPatterns().getSeverityMix() != null) {
                    design.putPOJO("severityMix", view.getDesignPatterns().getSeverityMix());
                }
                if (view.getDesignPatterns().getFocusHints() != null) {
                    design.putPOJO("focusHints", view.getDesignPatterns().getFocusHints());
                }
            }

            String json = objectMapper.writeValueAsString(root);
            int max = personaProperties.getMaxAiContextChars();
            return json.length() > max ? json.substring(0, max) : json;
        } catch (Exception e) {
            log.warn("Failed to serialize persona AI context: {}", e.getMessage());
            return null;
        }
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String buildDisplaySummary(PersonaResponse view) {
        if (view.getDesignPatterns() == null || view.getDesignPatterns().getRecentAnalysisCount() == 0) {
            return "Chưa đủ lịch sử phân tích — Willa sẽ học thêm khi bạn feedback design.";
        }
        StringBuilder sb = new StringBuilder();
        if (view.getProfile() != null) {
            if (view.getProfile().getOccupationLabel() != null) {
                sb.append("Bạn là ").append(view.getProfile().getOccupationLabel()).append(". ");
            }
            if (view.getProfile().getPlanTier() != null) {
                sb.append("Gói ").append(view.getProfile().getPlanTier()).append(". ");
            }
        }
        List<String> cats = view.getDesignPatterns().getTopIssueCategories();
        if (cats != null && !cats.isEmpty()) {
            sb.append("Hay gặp lỗi: ");
            sb.append(String.join(", ", cats.stream().map(c -> c.replace('_', ' ')).toList()));
            sb.append(".");
        }
        String result = PersonaTextSanitizer.sanitizeHint(sb.toString(), 480);
        return result != null ? result : "Persona đã sẵn sàng.";
    }

    private Map<String, Long> loadWorkflowCounts(Long userId) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(Math.max(1, personaProperties.getBehaviorWindowDays()));
        List<Object[]> rows = workflowUsageRepository.countWorkflowsByUserSince(userId, from, to);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] instanceof WorkflowType wt && row[1] instanceof Number n) {
                map.put(wt.name(), n.longValue());
            }
        }
        return map;
    }

    private String resolvePrimaryWorkflow(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String resolvePlanTier(Long userId) {
        List<Subscription> subs = subscriptionRepository.findActiveRecurringByUserId(
                userId, SubscriptionStatus.ACTIVE);
        if (subs.isEmpty()) {
            return "free";
        }
        String name = subs.get(0).getPlan().getName();
        if (name == null) {
            return "pro";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("free")) {
            return "free";
        }
        if (lower.contains("student")) {
            return "student";
        }
        return "pro";
    }

    private User requireActiveUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(user.getIsActive()) || !Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new IllegalArgumentException("Account is not active");
        }
        return user;
    }

    /**
     * Bản ghi đã có trong DB (managed) hoặc entity mới — chỉ set {@code user}, không set {@code userId}
     * (tránh Hibernate UPDATE 0 rows / unsaved-value với @MapsId).
     */
    private UserPersona getOrCreatePersona(User user) {
        return userPersonaRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserPersona created = new UserPersona();
                    created.setUser(user);
                    created.setEnabled(true);
                    return created;
                });
    }

    /**
     * Luôn load managed entity hoặc tạo mới chỉ qua {@code user} (@MapsId) — tránh stale state.
     */
    private void persistPersona(UserPersona persona, Long userId, boolean failOnError) {
        try {
            UserPersona target = userPersonaRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        UserPersona created = new UserPersona();
                        created.setUser(userRepository.getReferenceById(userId));
                        return created;
                    });
            target.setEnabled(persona.getEnabled());
            target.setAiContextJson(persona.getAiContextJson());
            target.setDisplaySummary(persona.getDisplaySummary());
            target.setAnalysisCountUsed(persona.getAnalysisCountUsed());
            target.setLastRefreshedAt(persona.getLastRefreshedAt());
            userPersonaRepository.save(target);
        } catch (DataAccessException e) {
            log.error("Failed to persist user_personas [userId={}]: {}", userId, e.getMessage());
            if (failOnError) {
                throw e;
            }
        }
    }

    private void assertRefreshAllowed(UserPersona persona) {
        if (persona.getLastRefreshedAt() == null) {
            return;
        }
        LocalDateTime allowed = persona.getLastRefreshedAt()
                .plusSeconds(Math.max(60, personaProperties.getRefreshCooldownSeconds()));
        if (LocalDateTime.now().isBefore(allowed)) {
            throw new PersonaRateLimitException(
                    "Vui lòng đợi trước khi làm mới persona.");
        }
    }

    private LocalDateTime buildNextRefreshAt(UserPersona persona) {
        if (persona.getLastRefreshedAt() == null) {
            return LocalDateTime.now();
        }
        return persona.getLastRefreshedAt()
                .plusSeconds(Math.max(60, personaProperties.getRefreshCooldownSeconds()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BuiltPersona(
            String aiContextJson,
            String displaySummary,
            int analysisCount,
            PersonaResponse view) {

        static BuiltPersona empty() {
            return new BuiltPersona(null, null, 0, PersonaResponse.builder().build());
        }
    }
}
