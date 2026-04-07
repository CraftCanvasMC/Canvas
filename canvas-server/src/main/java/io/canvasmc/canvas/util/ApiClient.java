package io.canvasmc.canvas.util;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
    private static final String BASE_URL = "https://canvasmc.io/api/v2/";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String project;

    @ApiStatus.Internal
    private static @NonNull String constructUrlRequest(@NonNull Type type, @Nullable String project, String @NonNull [] args) {
        String base = BASE_URL + switch (type) {
            case JD -> "jd";
            case ALL_BUILDS -> "builds/all";
            case LATEST -> "builds/latest";
            case PROJECTS -> "projects";
        };
        return appendParams(base, Lists.asList(project, args));
    }

    @ApiStatus.Internal
    private static @NonNull String appendParams(String base, @NonNull List<String> arguments) {
        StringBuilder builder = new StringBuilder(base);
        boolean first = true;
        for (String str : arguments) {
            if (str == null || str.isEmpty()) continue;

            builder.append(first ? "?" : "&");
            builder.append(str);
            first = false;
        }
        return builder.toString();
    }

    @ApiStatus.Internal
    private static JsonObject sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch from Canvas API: " + response.statusCode());
        }

        return GSON.fromJson(response.body(), JsonObject.class);
    }

    @ApiStatus.Internal
    private static @Nullable Build parseBuild(@NonNull JsonObject jsonObject) {
        try {
            if (!jsonObject.get("result").getAsString().equalsIgnoreCase("SUCCESS")) {
                // failed builds don't have some of these args
                return null;
            }
            int buildNumber = jsonObject.get("buildNumber").getAsInt();
            String url = jsonObject.get("url").getAsString();
            String downloadUrl = jsonObject.get("downloadUrl").getAsString();
            String channel = jsonObject.get("channelVersion").getAsString();
            long timestamp = jsonObject.get("timestamp").getAsLong();
            boolean isExperimental = jsonObject.get("isExperimental").getAsBoolean();
            BuildStatus buildStatus = (buildNumber == -1) ? BuildStatus.LOCAL : (isExperimental ? BuildStatus.EXPERIMENTAL : BuildStatus.STABLE);

            List<Commit> commits = new ArrayList<>();
            for (final JsonElement commitElement : jsonObject.getAsJsonArray("commits")) {
                JsonObject commitObj = commitElement.getAsJsonObject();
                commits.add(new Commit(
                    commitObj.get("message").getAsString(),
                    commitObj.get("hash").getAsString(),
                    commitObj.get("author").getAsString()
                ));
            }

            return new Build(buildNumber, url, downloadUrl, channel, timestamp, buildStatus, commits.toArray(new Commit[0]));
        } catch (Throwable thrown) {
            throw new IllegalArgumentException("Unknown object:\n" + GSON.toJson(jsonObject) + "\nStacktrace:", thrown);
        }
    }

    /**
     * Constructs a new API client for a provided project
     *
     * @param project
     *     the project id
     */
    @ApiStatus.Internal
    private ApiClient(final @NonNull String project) {
        this.project = project.toLowerCase();
    }

    /**
     * Constructs a new API client for project-specific actions
     *
     * @param project
     *     the project for the client
     *
     * @return a new API client instance for the project specified
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NonNull ApiClient getClientFor(String project) {
        return new ApiClient(project);
    }

    /**
     * Gets all projects provided by CanvasMC CI
     *
     * @return an array of project slugs
     */
    public static String @NonNull [] getProjects() {
        List<String> projects = new ArrayList<>();
        try {
            JsonObject json = sendRequest(constructUrlRequest(Type.PROJECTS, null, new String[0]));
            for (final JsonElement element : json.getAsJsonArray("projects")) {
                JsonObject project = element.getAsJsonObject();
                projects.add(project.get("slug").getAsString());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Unable to fetch CanvasMC REST API", e);
        }
        return projects.toArray(new String[0]);
    }

    /**
     * Gets the project slug for the project associated with this client
     *
     * @return the slug
     */
    public String getProject() {
        return project;
    }

    /**
     * Swaps the current project slug with a new project slug
     *
     * @param slug
     *     the slug to switch to
     */
    @Contract(mutates = "this")
    public void switchProject(@NonNull String slug) {
        this.project = slug.toLowerCase();
    }

    /**
     * Gets all stable builds provided by the currently set project slug
     *
     * @return all stable builds
     */
    public Build @NonNull [] getStableBuilds() {
        return getBuilds("", false);
    }

    /**
     * Gets all the builds provided by the currently set project slug
     *
     * @param includeExperimental
     *     If the query should include experimental builds
     *
     * @return all builds
     */
    public Build @NonNull [] getBuilds(boolean includeExperimental) {
        return getBuilds("", includeExperimental);
    }

    /**
     * Gets all the builds provided by the currently set project slug
     *
     * @param includeExperimental
     *     If the query should include experimental builds
     * @param channelId
     *     The channel id. If provided an empty string, it will include builds in all channels
     *
     * @return all builds in the channel
     */
    public Build @NonNull [] getBuilds(@NonNull String channelId, boolean includeExperimental) {
        List<Build> builds = new ArrayList<>();
        try {
            JsonObject json = sendRequest(
                constructUrlRequest(Type.ALL_BUILDS, "project=" + getProject(),
                    new String[]{
                        channelId.isEmpty() ? "" : "channel=" + channelId, "experimental=" + includeExperimental})
            );
            for (final JsonElement element : json.getAsJsonArray("builds")) {
                JsonObject build = element.getAsJsonObject();
                Build parsed = parseBuild(build);
                if (parsed == null) continue;
                builds.add(parsed);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Unable to fetch CanvasMC REST API", e);
        }
        return builds.toArray(new Build[0]);
    }

    /**
     * Gets all the builds provided by the currently set project slug
     *
     * @param filter
     *     The filter to apply to the returned array
     *
     * @return all builds that passed the filter
     *
     * @apiNote Experimental builds will also pass through the filter
     */
    public Build @NonNull [] getBuilds(Predicate<Build> filter) {
        return Arrays.stream(getBuilds(true))
            .filter(filter)
            .toArray(Build[]::new);
    }

    /**
     * Gets the build associated with the provided build number, regardless of channel or build status
     *
     * @param buildNumber
     *     the number to search for
     *
     * @return the associated build
     */
    public @NonNull Build getBuild(int buildNumber) {
        return Arrays.stream(getBuilds(true))
            .filter(b -> b.buildNumber() == buildNumber)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown build " + buildNumber + " for project " + project));
    }

    /**
     * Gets all the builds newer than the specified build number for the provided project slug
     *
     * @return all builds
     *
     * @apiNote This will not include experimental builds, only stable builds
     */
    public Build @NonNull [] getStableBuildsNewerThan(int buildNumber) {
        return getBuilds((build) -> build.buildStatus == BuildStatus.STABLE && build.buildNumber > buildNumber);
    }

    /**
     * Gets the latest stable build for the project slug
     *
     * @return the latest build
     */
    public @NonNull Build getLatestStableBuild() {
        return getLatestBuild("", false);
    }

    /**
     * Gets the latest build for the project slug
     *
     * @param allowExperimentalBuilds
     *     If the query is allowed to return an experimental build
     *
     * @return the latest build
     */
    public @NonNull Build getLatestBuild(boolean allowExperimentalBuilds) {
        return getLatestBuild("", allowExperimentalBuilds);
    }

    /**
     * Gets the latest build for the project slug
     *
     * @param allowExperimentalBuilds
     *     If the query is allowed to return an experimental build
     * @param channelId
     *     The channel id. If provided an empty string, it will include builds in all channels
     *
     * @return the latest build
     */
    public @NonNull Build getLatestBuild(@NonNull String channelId, boolean allowExperimentalBuilds) {
        try {
            JsonObject json = sendRequest(
                constructUrlRequest(Type.LATEST, "project=" + getProject(),
                    new String[]{
                        channelId.isEmpty() ? "" : "channel=" + channelId, "experimental=" + allowExperimentalBuilds})
            );
            Build parsed = parseBuild(json);
            if (parsed == null) throw new RuntimeException("Couldn't find latest build");
            return parsed;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Unable to fetch CanvasMC REST API", e);
        }
    }

    /**
     * Gets the Javadoc redirect URL for a given class
     *
     * @param clazz
     *     The class to search for
     * @param channelVersion
     *     The channel version, or empty for latest
     *
     * @return the redirect URL to the Javadocs page
     */
    public @NonNull String getJavadocUrl(@NonNull Class<?> clazz, @NonNull String channelVersion) {
        String redirectPath = clazz.getName().replace('.', '/').replace('$', '.') + ".html";

        return constructUrlRequest(
            Type.JD,
            "project=" + getProject(),
            new String[]{
                channelVersion.isEmpty() ? "" : "channel=" + channelVersion,
                "redirect=" + redirectPath,
                "experimental=true"
            }
        );
    }

    /**
     * The release channel of this build
     */
    public enum BuildStatus {
        STABLE(0x55ff55),
        EXPERIMENTAL(0xffff55),
        LOCAL(0xff5555),
        UNKNOWN(0xffaa00);

        private final int colorValue;

        BuildStatus(int value) {
            this.colorValue = value;
        }

        public int color() {
            return colorValue;
        }
    }

    @ApiStatus.Internal
    private enum Type {
        PROJECTS,
        ALL_BUILDS,
        LATEST,
        JD
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
     * @param channelVersion
     *     the channel version for this build
     * @param timestamp
     *     the timestamp of the associated build
     * @param buildStatus
     *     the channel of this build
     * @param commits
     *     an array of commits in this build, can be empty
     */
    public record Build(
        int buildNumber,
        String url,
        String downloadUrl,
        String channelVersion,
        long timestamp,
        BuildStatus buildStatus,
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
    public record Commit(String message, String hash, String author) {}
}
