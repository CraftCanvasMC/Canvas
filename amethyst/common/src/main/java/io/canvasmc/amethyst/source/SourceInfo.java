package io.canvasmc.amethyst.source;

import io.canvasmc.amethyst.PlatformImplementation;
import io.canvasmc.amethyst.proto.Source;
import java.util.List;

public record SourceInfo(
    String name,
    String version,
    String description,
    List<String> authors,
    Source.Kind kind
) {
    public static final SourceInfo INTERNALS = new SourceInfo(
        "Server",
        PlatformImplementation.get().getPlatformVer(),
        "",
        PlatformImplementation.get().getPlatformAuthors(),
        Source.Kind.KIND_INTERNALS
    );

    public Source toProto() {
        return Source.newBuilder()
            .setName(name)
            .setVersion(version)
            .setDescription(description)
            .addAllAuthors(authors)
            .setKind(kind)
            .build();
    }
}
