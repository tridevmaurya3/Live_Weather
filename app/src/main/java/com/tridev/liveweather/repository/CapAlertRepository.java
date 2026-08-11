package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.alert.AlertLocation;
import com.tridev.liveweather.domain.alert.WeatherAlert;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

public final class CapAlertRepository {

    public static final String IMD_CAP_RSS_URL =
            "https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml";

    @NonNull
    public Result loadImdAlertsBlocking(
            @NonNull AlertLocation location,
            @Nullable String previousEtag
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(IMD_CAP_RSS_URL).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
        connection.setRequestProperty("User-Agent", "LiveWeather-Android/1.0");
        if (previousEtag != null && !previousEtag.trim().isEmpty()) {
            connection.setRequestProperty("If-None-Match", previousEtag);
        }

        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            connection.disconnect();
            return new Result(true, previousEtag, new ArrayList<>());
        }
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new java.io.IOException("IMD CAP feed HTTP " + code);
        }

        String etag = connection.getHeaderField("ETag");
        try (InputStream input = connection.getInputStream()) {
            List<WeatherAlert> alerts = parseFeed(input, location);
            return new Result(false, etag, alerts);
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private List<WeatherAlert> parseFeed(
            @NonNull InputStream input,
            @NonNull AlertLocation location
    ) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(input);
        NodeList items = document.getElementsByTagName("item");
        List<WeatherAlert> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) continue;
            Element item = (Element) node;

            String title = first(item, "event", "title");
            String description = first(item, "description", "headline", "summary");
            String area = first(item, "areaDesc", "area", "location");
            String severityText = first(item, "severity");
            String urgency = first(item, "urgency");
            String identifier = first(item, "identifier", "guid");
            String effective = first(item, "effective", "onset", "pubDate");
            String expires = first(item, "expires");

            if (!matchesLocation(location, area, title, description)) continue;

            WeatherAlert.Severity severity = capSeverity(severityText, urgency);
            long issuedAt = parseTime(effective, now);
            long expiresAt = parseTime(expires, 0L);
            if (expiresAt > 0L && expiresAt < now) continue;

            String resolvedTitle = empty(title) ? "Official weather warning" : title.trim();
            String resolvedMessage = empty(description)
                    ? "An official IMD CAP warning is active for the selected area."
                    : stripHtml(description);
            String validLabel = expiresAt > 0L
                    ? "Valid until " + DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(expiresAt))
                    : "Official CAP alert";
            String id = empty(identifier)
                    ? "imd-cap-" + Math.abs((resolvedTitle + area + effective).hashCode())
                    : identifier.trim();

            result.add(new WeatherAlert(
                    id,
                    resolvedTitle,
                    resolvedMessage,
                    empty(area) ? location.displayLabel() : area.trim(),
                    validLabel,
                    severity,
                    WeatherAlert.Source.IMD_DISTRICT_WARNING,
                    issuedAt,
                    expiresAt
            ));
        }

        result.sort(Comparator
                .comparingInt((WeatherAlert alert) -> severityRank(alert.getSeverity())).reversed()
                .thenComparingLong(WeatherAlert::getIssuedAt).reversed());
        return result;
    }

    private boolean matchesLocation(
            AlertLocation location,
            @Nullable String area,
            @Nullable String title,
            @Nullable String description
    ) {
        String haystack = normalize(join(area, title, description));
        if (haystack.isEmpty()) return false;
        String district = normalize(location.getDistrict());
        String state = normalize(location.getState());
        if (!district.isEmpty() && fuzzyContains(haystack, district)) return true;
        return !state.isEmpty() && fuzzyContains(haystack, state);
    }

    private boolean fuzzyContains(String haystack, String needle) {
        if (haystack.contains(needle)) return true;
        String simplifiedNeedle = needle
                .replace(" district", "")
                .replace(" distt", "")
                .replace(" city", "")
                .trim();
        return simplifiedNeedle.length() >= 4 && haystack.contains(simplifiedNeedle);
    }

    private WeatherAlert.Severity capSeverity(@Nullable String severity, @Nullable String urgency) {
        String s = severity == null ? "" : severity.trim().toLowerCase(Locale.ROOT);
        if (s.equals("extreme") || s.equals("severe")) return WeatherAlert.Severity.RED;
        if (s.equals("moderate")) return WeatherAlert.Severity.ORANGE;
        if (s.equals("minor")) return WeatherAlert.Severity.YELLOW;
        String u = urgency == null ? "" : urgency.trim().toLowerCase(Locale.ROOT);
        if (u.equals("immediate") || u.equals("expected")) return WeatherAlert.Severity.ORANGE;
        return WeatherAlert.Severity.INFO;
    }

    private int severityRank(WeatherAlert.Severity severity) {
        switch (severity) {
            case RED: return 4;
            case ORANGE: return 3;
            case YELLOW: return 2;
            default: return 1;
        }
    }

    @Nullable
    private String first(Element element, String... names) {
        for (String name : names) {
            NodeList namespaced = element.getElementsByTagNameNS("*", name);
            if (namespaced.getLength() > 0) {
                String value = namespaced.item(0).getTextContent();
                if (!empty(value)) return value;
            }
            NodeList plain = element.getElementsByTagName(name);
            if (plain.getLength() > 0) {
                String value = plain.item(0).getTextContent();
                if (!empty(value)) return value;
            }
        }
        return null;
    }

    private long parseTime(@Nullable String text, long fallback) {
        if (empty(text)) return fallback;
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String stripHtml(String value) {
        return value.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String join(@Nullable String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!empty(value)) builder.append(' ').append(value);
        }
        return builder.toString();
    }

    private String normalize(@Nullable String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('&', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean empty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Result {
        private final boolean notModified;
        private final String etag;
        private final List<WeatherAlert> alerts;

        Result(boolean notModified, @Nullable String etag, @NonNull List<WeatherAlert> alerts) {
            this.notModified = notModified;
            this.etag = etag;
            this.alerts = alerts;
        }

        public boolean isNotModified() { return notModified; }
        @Nullable public String getEtag() { return etag; }
        @NonNull public List<WeatherAlert> getAlerts() { return alerts; }
    }
}
