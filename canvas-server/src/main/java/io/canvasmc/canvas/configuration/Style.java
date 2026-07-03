package io.canvasmc.canvas.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Contract;

public class Style {

    private final List<Comment> instructions = new ArrayList<>();

    private Style() {
    }

    @Contract(" -> new")
    public static Style create() {
        return new Style();
    }

    public static Style wrap(final String... wrap) {
        return create().wordWrap(wrap).endLine();
    }

    public Style literal(final String text) {
        instructions.add(new Literal(text));
        return this;
    }

    public Style wordWrap(final String... parts) {
        instructions.add(new WordWrap(String.join(" ", parts)));
        return this;
    }

    public Style blank() {
        instructions.add(new BlankLine());
        return this;
    }

    public <E extends Enum<E>> Style defineEnum(final Class<E> enumClass, final Function<E, String> descriptor) {
        instructions.add(new EnumDoc<>(enumClass, descriptor));
        return this;
    }

    public Style endLine() {
        instructions.add(new EndLine());
        return this;
    }

    public String[] compile(final int characterLimit) {
        final List<String> lines = new ArrayList<>();

        // each "current line" is built by accumulating literals/wordwraps until
        // an EndLine flushes it. we track whether we're mid-line.
        final StringBuilder currentLine = new StringBuilder();

        for (final Comment instruction : instructions) {
            switch (instruction) {
                case Literal(String text) -> currentLine.append(text);

                case WordWrap(String text) -> {
                    // append word-wrapped text to current line, respecting characterLimit
                    final String[] words = text.split("\\s+");

                    for (final String word : words) {
                        if (word.isEmpty()) {
                            continue;
                        }

                        final boolean hasContent = !currentLine.isEmpty();
                        final int projected = hasContent
                            ? currentLine.length() + 1 + word.length()
                            : word.length();

                        if (hasContent && projected > characterLimit) {
                            lines.add(currentLine.toString());
                            currentLine.setLength(0);
                            currentLine.append(word);
                        }
                        else {
                            if (hasContent) currentLine.append(' ');
                            currentLine.append(word);
                        }
                    }
                }

                case BlankLine() -> {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine.setLength(0);
                    }
                    lines.add("");  // empty string, toCommentLine renders it as bare #
                }

                case EnumDoc<?> enDoc -> {
                    // flush pending line before the enum block
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine.setLength(0);
                    }
                    compileEnum(enDoc, lines);
                }

                case EndLine() -> {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine.setLength(0);
                    }
                }
            }
        }

        // flush any trailing content that wasn't terminated with endLine()
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    private <E extends Enum<E>> void compileEnum(final EnumDoc<?> raw, final List<String> lines) {
        //noinspection unchecked
        final EnumDoc<E> doc = (EnumDoc<E>) raw;
        final E[] constants = doc.enumClass().getEnumConstants();
        lines.add("Possible values(can be lowercase):");
        for (final E constant : constants) {
            final String description = doc.descriptor().apply(constant);
            lines.add(" - " + constant.name() + " - " + description);
        }
    }

    public sealed interface Comment permits Style.Literal, Style.WordWrap, Style.BlankLine, Style.EnumDoc, Style.EndLine {}

    public record Literal(String text) implements Comment {}

    public record WordWrap(String text) implements Comment {}

    public record BlankLine() implements Comment {}

    public record EnumDoc<E extends Enum<E>>(Class<E> enumClass, Function<E, String> descriptor) implements Comment {}

    public record EndLine() implements Comment {}
}
