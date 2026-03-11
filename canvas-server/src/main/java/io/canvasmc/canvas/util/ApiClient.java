package io.canvasmc.canvas.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Client for interacting with the Canvas V2 REST API.
 *
 * <p>URL: <a href="https://canvasmc.io/api/v2">https://canvasmc.io/api/v2</a></p>
 *
 * <p>This client provides methods to fetch builds, retrieve the latest stable or experimental versions,
 * and inspect commits associated with each build.</p>
 *
 * <p>All returned collections are non-null but may be empty unless otherwise noted.</p>
 */
public final class ApiClient {
    /**
     * The base URL for API access
     */
    private static final String BASE_URL = "https://canvasmc.io/api/v2";
    /**
     * The HTTP client
     */
    private final HttpClient CLIENT = HttpClient.newHttpClient();

    /**
     * Fetches all builds for a specific Minecraft version.
     *
     * <p>If {@code experimental} is true, experimental builds are included.
     * Otherwise, only stable builds are returned.</p>
     *
     * @param minecraftVersion
     *     the target Minecraft version (non-null, but may be blank to fetch all builds)
     * @param experimental
     *     whether to include experimental builds in the result
     *
     * @return a non-null list of matching builds (may be empty)
     *
     * @throws IOException
     *     if the API request fails
     * @throws InterruptedException
     *     if the HTTP request is interrupted
     */
    public @NonNull List<Build> getAllBuilds(String minecraftVersion, boolean experimental) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(BASE_URL + "/builds");

        boolean hasQuery = false;
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            url.append("?minecraft_version=").append(minecraftVersion);
            hasQuery = true;
        }
        if (experimental) {
            url.append(hasQuery ? "&" : "?").append("experimental=true");
        }

        String json = sendRequest(url.toString());
        List<Build> builds = parseBuildsArray(json);
        builds.sort(Comparator.comparingInt(Build::buildNumber));
        return builds;
    }

    /**
     * Returns the latest build for the specified Minecraft version.
     *
     * <p>If {@code includeExperimental} is true, experimental builds are considered.
     * Otherwise, only stable builds are used when determining the latest version.</p>
     *
     * @param minecraftVersion
     *     the target Minecraft version
     * @param includeExperimental
     *     whether to include experimental builds in the search
     *
     * @return the latest build matching the filters, or {@code null} if none exist
     *
     * @throws IOException
     *     if the API request fails
     * @throws InterruptedException
     *     if the HTTP request is interrupted
     */
    public @Nullable Build getLatestBuildForVersion(String minecraftVersion, boolean includeExperimental)
        throws IOException, InterruptedException {

        List<Build> builds = getAllBuilds(minecraftVersion, includeExperimental);
        if (builds.isEmpty()) {
            return null;
        }

        return builds.stream()
            .max(Comparator.comparingInt(Build::buildNumber))
            .orElse(null);
    }

    /**
     * Returns the latest <b>stable</b> build for the specified Minecraft version.
     *
     * <p>This is equivalent to calling
     * {@link #getLatestBuildForVersion(String, boolean)} with {@code includeExperimental = false}.</p>
     *
     * @param minecraftVersion
     *     the target Minecraft version
     *
     * @return the latest stable build, or {@code null} if none exist
     *
     * @throws IOException
     *     if the API request fails
     * @throws InterruptedException
     *     if the HTTP request is interrupted
     */
    public @Nullable Build getLatestBuildForVersion(String minecraftVersion)
        throws IOException, InterruptedException {
        return getLatestBuildForVersion(minecraftVersion, false);
    }

    /**
     * Returns the latest build across <b>all Minecraft versions</b>.
     *
     * @param experimental
     *     whether to allow experimental builds to be returned
     *
     * @return the latest available build
     *
     * @throws IOException
     *     if the API request fails
     * @throws InterruptedException
     *     if the HTTP request is interrupted
     */
    public @NonNull Build getLatestBuild(boolean experimental) throws IOException, InterruptedException {
        String url = BASE_URL + "/builds/latest" + (experimental ? "?experimental=true" : "");
        String json = sendRequest(url);
        return parseSingleBuild(json);
    }

    /**
     * Returns the build across <b>all Minecraft versions</b> related to the provided build number.
     *
     * @param buildNum
     *     the build number
     *
     * @return the build associated with the provided build number
     *
     * @throws IOException
     *     if the API request fails
     * @throws InterruptedException
     *     if the HTTP request is interrupted
     */
    public @Nullable Build getBuild(int buildNum) throws IOException, InterruptedException {
        String json = sendRequest(BASE_URL + "/builds?experimental=true");
        List<Build> builds = parseBuildsArray(json);
        builds.sort(Comparator.comparingInt(Build::buildNumber));
        return builds.stream()
            .filter((build) -> build.buildNumber == buildNum)
            .findFirst().orElse(null);
    }

    private String sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch from Canvas API: " + response.statusCode());
        }
        return response.body();
    }

    private @NonNull List<Build> parseBuildsArray(@NonNull String json) {
        List<Build> builds = new ArrayList<>();
        int start = json.indexOf("[");
        int end = json.lastIndexOf("]");
        if (start < 0 || end < 0) return builds;

        String arrayContent = json.substring(start + 1, end);
        String[] objects = splitObjects(arrayContent);

        for (String obj : objects) {
            obj = obj.strip();
            if (!obj.isEmpty()) {
                builds.add(parseSingleBuild(obj));
            }
        }
        return builds;
    }

    private @NonNull Build parseSingleBuild(String json) {
        int buildNumber = extractIntElse(json, "buildNumber", -1);
        String url = extractString(json, "url");
        String downloadUrl = extractString(json, "downloadUrl");
        String mcVersion = extractString(json, "minecraftVersion");
        long timestamp = extractLong(json, "timestamp");
        boolean experimental = extractBoolean(json, "isExperimental");

        List<Commit> commits = parseCommits(json);
        return new Build(buildNumber, url, downloadUrl, mcVersion, timestamp, buildNumber == -1 ? Channel.LOCAL : experimental ? Channel.BETA : Channel.STABLE, commits.toArray(new Commit[0]));
    }

    private @NonNull List<Commit> parseCommits(@NonNull String json) {
        List<Commit> commits = new LinkedList<>();
        String key = "\"commits\":";
        int start = json.indexOf(key);
        if (start < 0) return commits;

        start = json.indexOf("[", start);
        int end = json.indexOf("]", start);
        if (start < 0 || end < 0) return commits;

        String arrayContent = json.substring(start + 1, end);
        String[] objects = splitObjects(arrayContent);

        for (String obj : objects) {
            String message = extractString(obj, "message");
            String hash = extractString(obj, "hash");
            if (message != null && hash != null) {
                commits.add(new Commit(message, hash));
            }
        }
        return commits;
    }

    private @NonNull String @NonNull [] splitObjects(@NonNull String json) {
        List<String> objects = new LinkedList<>();
        int braceCount = 0;
        int lastSplit = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0 && c == '}') {
                objects.add(json.substring(lastSplit, i + 1));
                lastSplit = i + 2; // skip comma + space
            }
        }
        return objects.toArray(new String[0]);
    }

    private @Nullable String extractString(@NonNull String json, String key) {
        String k = "\"" + key + "\":";
        int idx = json.indexOf(k);
        if (idx < 0) return null;

        idx = json.indexOf('"', idx + k.length());
        if (idx < 0) return null;

        int end = json.indexOf('"', idx + 1);
        if (end < 0) return null;

        return json.substring(idx + 1, end);
    }

    private int extractInt(String json, String key) {
        String value = extractNumber(json, key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    private int extractIntElse(String json, String key, int fallback) {
        String value = extractNumber(json, key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private long extractLong(String json, String key) {
        String value = extractNumber(json, key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private boolean extractBoolean(@NonNull String json, String key) {
        String k = "\"" + key + "\":";
        int idx = json.indexOf(k);
        if (idx < 0) return false;

        int start = idx + k.length();
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        if (end < 0) return false;

        return Boolean.parseBoolean(json.substring(start, end).trim());
    }

    private @Nullable String extractNumber(@NonNull String json, String key) {
        String k = "\"" + key + "\":";
        int idx = json.indexOf(k);
        if (idx < 0) return null;

        int start = idx + k.length();
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        if (end < 0) return null;

        return json.substring(start, end).trim();
    }

    /**
     * The release channel of this build
     */
    public enum Channel {
        STABLE(NamedTextColor.GREEN),
        BETA(NamedTextColor.YELLOW),
        LOCAL(NamedTextColor.RED),
        UNKNOWN(NamedTextColor.GOLD);

        private final TextColor color;

        Channel(TextColor color) {
            this.color = color;
        }

        public @Nullable TextColor color() {
            return color;
        }
    }

    /**
     * Represents a Jenkins build
     *
     * @param buildNumber
     *     the build number
     * @param url
     *     the URL for this associated build
     * @param downloadUrl
     *     the download URL
     * @param minecraftVersion
     *     the Minecraft version for this build
     * @param timestamp
     *     the timestamp of the associated build
     * @param channel
     *     the channel of this build
     * @param commits
     *     an array of commits in this build, can be empty
     */
    public record Build(
        int buildNumber,
        String url,
        String downloadUrl,
        String minecraftVersion,
        long timestamp,
        Channel channel,
        Commit[] commits
    ) {
        public boolean hasChanges() {
            return this.commits.length > 0;
        }
    }

    /**
     * Represents a GitHub Commit
     *
     * @param message
     *     the commit message
     * @param hash
     *     the commit hash
     */
    public record Commit(String message, String hash) {
    }
}
