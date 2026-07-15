package io.canvasmc.canvas.configuration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.canvasmc.canvas.GlobalConfiguration;
import io.papermc.paper.ServerBuildInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.bukkit.craftbukkit.util.ApiVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Configuration")
class VanillaFixesConfigurationTest {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final URI MOJIRA_API = URI.create("https://bugs.mojang.com/api/jql-search-post");
    private static final boolean DEBUG = Boolean.parseBoolean(System.getenv("CANVAS_TEST_DEBUG"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void checkMojiraIssueStatuses() throws Exception {
        final Set<String> issues = collectIssues();

        if (issues.isEmpty()) {
            return;
        }

        final JsonArray found = queryMojira(issues);

        final Set<String> returned = new HashSet<>();
        final Set<String> fixed = new HashSet<>();

        for (final var element : found) {
            checkIssue(element.getAsJsonObject(), returned, fixed);
        }

        final Set<String> missing = issues.stream()
            .filter(issue -> !returned.contains(issue))
            .collect(Collectors.toCollection(HashSet::new));

        if (DEBUG && (!missing.isEmpty() || !fixed.isEmpty())) {
            final StringBuilder message = new StringBuilder();

            if (!missing.isEmpty()) {
                message.append("Issues missing from Mojira:\n - ")
                    .append(String.join("\n - ", missing));
            }

            if (!fixed.isEmpty()) {
                if (!message.isEmpty()) {
                    message.append("\n\n");
                }

                message.append("Issues fixed upstream:\n - ")
                    .append(String.join("\n - ", fixed));
            }

            throw new AssertionError(message);
        }
    }

    private static JsonArray queryMojira(final Set<String> issues) throws Exception {
        final JsonObject body = new JsonObject();
        body.addProperty("advanced", true);
        body.addProperty("search", "key in (" + String.join(", ", issues) + ")");
        body.addProperty("project", "MC");
        body.addProperty("filter", "all");
        body.addProperty("page", 0);
        body.addProperty("maxResults", issues.size());

        final HttpRequest request = HttpRequest.newBuilder()
            .uri(MOJIRA_API)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
            .build();

        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IllegalStateException(
                "Failed to query Mojira (HTTP " + response.statusCode() + ")\n" + response.body()
            );
        }

        return GSON.fromJson(response.body(), JsonObject.class)
            .getAsJsonArray("issues");
    }

    private static void checkIssue(final JsonObject issue, final Set<String> returned, final Set<String> fixed) {
        final String key = issue.get("key").getAsString();
        returned.add(key);

        final JsonObject fields = issue.getAsJsonObject("fields");

        if (isOpen(fields) || hasNoRelevantFixVersion(fields)) {
            return;
        }

        final JsonObject latest = getLatestFixVersion(fields);
        final String rawVersion = latest.get("name").getAsString();

        try {
            final ApiVersion current = ApiVersion.getOrCreateVersion(
                ServerBuildInfo.buildInfo().minecraftVersionId()
            );

            final ApiVersion fixVersion = ApiVersion.getOrCreateVersion(normalizeVersion(rawVersion));

            if (current.isNewerThanOrSameAs(fixVersion)) {
                final String message = key + " has been fixed in " + cleanVersion(rawVersion);
                if (DEBUG) {
                    fixed.add(message);
                } else {
                    System.err.println(message);
                }
            }
        } catch (final IllegalArgumentException e) {
            // not relevant for us cuz it means the version is pre 26.1, and we're on newer versions
            if (DEBUG) {
                System.err.println("Cannot parse fix version " + cleanVersion(rawVersion) + " for " + key);
                System.err.println(e.getMessage());
            }
        }
    }

    private static boolean isOpen(final JsonObject fields) {
        return "Open".equalsIgnoreCase(
            fields.getAsJsonObject("status").get("name").getAsString()
        );
    }

    private static boolean hasNoRelevantFixVersion(final JsonObject fields) {
        final JsonArray versions = fields.getAsJsonArray("fixVersions");

        if (versions == null || versions.isEmpty()) {
            return true;
        }

        return versions.asList().stream()
            .map(version -> version.getAsJsonObject().get("name").getAsString())
            .anyMatch(version -> "Future Update".equalsIgnoreCase(version) || "Future Hotfix".equalsIgnoreCase(version));
    }

    private static JsonObject getLatestFixVersion(final JsonObject fields) {
        final JsonArray versions = fields.getAsJsonArray("fixVersions");
        return versions.get(versions.size() - 1).getAsJsonObject();
    }

    private static Set<String> collectIssues() {
        final Set<String> issues = new HashSet<>();

        for (final Field field : GlobalConfiguration.UpstreamFixes.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())
                || Modifier.isStatic(field.getModifiers())
                || field.isSynthetic()) {
                continue;
            }

            final String name = field.getName();

            if (name.matches("mc\\d+")) {
                issues.add("MC-" + name.substring(2));
            }
        }

        return issues;
    }

    private static String normalizeVersion(final String version) {
        return cleanVersion(version)
            .replaceFirst("\\s+(Snapshot|Pre-Release|Release Candidate)\\s+\\d+$", "");
    }

    private static String cleanVersion(final String version) {
        return version.trim().replaceFirst("^Minecraft\\s+", "");
    }
}
