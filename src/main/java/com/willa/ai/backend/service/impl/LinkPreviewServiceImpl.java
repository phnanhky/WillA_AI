package com.willa.ai.backend.service.impl;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.willa.ai.backend.dto.response.LinkPreviewResponse;
import com.willa.ai.backend.service.LinkPreviewService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LinkPreviewServiceImpl implements LinkPreviewService {

    private static final int MAX_HTML_BYTES = 512_000;
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>([^<]+)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_TITLE = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_TITLE_ALT = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:title[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_TITLE = Pattern.compile(
            "<meta[^>]+name=[\"']twitter:title[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE_ALT = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_IMAGE = Pattern.compile(
            "<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    @Override
    public LinkPreviewResponse resolveTitle(String rawUrl) {
        URI uri = validatePublicHttpUrl(rawUrl.trim());
        String source = detectLinkSource(uri);
        String html = fetchHtml(uri);
        String title = extractTitle(html);
        if (title == null || title.isBlank()) {
            title = fallbackTitleFromUrl(uri);
        } else {
            title = cleanTitle(title);
        }
        String imageUrl = extractImageUrl(html);

        return LinkPreviewResponse.builder()
                .url(uri.toString())
                .title(title)
                .source(source)
                .imageUrl(imageUrl)
                .build();
    }

    private String extractImageUrl(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String og = firstMatch(OG_IMAGE, html);
        if (og == null) {
            og = firstMatch(OG_IMAGE_ALT, html);
        }
        if (og != null && !og.isBlank()) {
            return decodeHtmlEntities(og.trim());
        }
        String twitter = firstMatch(TWITTER_IMAGE, html);
        if (twitter != null && !twitter.isBlank()) {
            return decodeHtmlEntities(twitter.trim());
        }
        return null;
    }

    /** Nhận diện Google Docs/Sheets/Drive từ URL (không cần fetch HTML). */
    private static String detectLinkSource(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!host.contains("google.com")) {
            return null;
        }
        if (path.contains("/spreadsheets/")) {
            return "Google Sheets";
        }
        if (path.contains("/document/")) {
            return "Google Docs";
        }
        if (path.contains("/presentation/")) {
            return "Google Slides";
        }
        if (path.contains("/forms/")) {
            return "Google Forms";
        }
        if (path.contains("/drawings/")) {
            return "Google Drawings";
        }
        if (host.contains("drive.google.com")) {
            return "Google Drive";
        }
        return "Google";
    }

    private URI validatePublicHttpUrl(String raw) {
        if (raw.isBlank()) {
            throw new RuntimeException("URL không hợp lệ");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new RuntimeException("URL không hợp lệ");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new RuntimeException("Chỉ hỗ trợ liên kết http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RuntimeException("URL không hợp lệ");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.equals("localhost")
                || lowerHost.endsWith(".local")
                || lowerHost.equals("127.0.0.1")
                || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")) {
            throw new RuntimeException("URL không được phép");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()) {
                    throw new RuntimeException("URL không được phép");
                }
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Không phân giải được tên miền");
        }
        return uri;
    }

    private String fetchHtml(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (compatible; WillaBot/1.0; +https://willaai.tech)");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }
        int len = Math.min(body.length, MAX_HTML_BYTES);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String og = firstMatch(OG_TITLE, html);
        if (og == null) {
            og = firstMatch(OG_TITLE_ALT, html);
        }
        if (og != null && !og.isBlank()) {
            return decodeHtmlEntities(og.trim());
        }
        String twitter = firstMatch(TWITTER_TITLE, html);
        if (twitter != null && !twitter.isBlank()) {
            return decodeHtmlEntities(twitter.trim());
        }
        Matcher m = TITLE_TAG.matcher(html);
        if (m.find()) {
            return decodeHtmlEntities(m.group(1).trim());
        }
        return null;
    }

    private static String firstMatch(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String cleanTitle(String title) {
        String t = title.trim();
        String[] suffixes = {
                " - Google Docs",
                " - Google Drive",
                " - Google Sheets",
                " - Google Slides",
                " - Google Forms",
                " - Google Drawings",
                " - Google Tài liệu",
                " - Google Trang tính",
                " - Google Trang trình bày",
        };
        for (String suffix : suffixes) {
            if (t.endsWith(suffix)) {
                t = t.substring(0, t.length() - suffix.length()).trim();
                break;
            }
        }
        return t.isBlank() ? title.trim() : t;
    }

    private static String fallbackTitleFromUrl(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i].trim();
            if (!seg.isEmpty() && !seg.equals("view") && !seg.equals("edit") && !seg.equals("preview")) {
                return decodeHtmlEntities(seg.replace('+', ' '));
            }
        }
        return uri.getHost() != null ? uri.getHost() : uri.toString();
    }

    private static String decodeHtmlEntities(String input) {
        return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
}
