package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Templates must stay CSP-clean and XSS-safe: no unescaped output, inline script or inline style. */
class TemplateSafetyTest {

    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)(?![^>]*th:src=)[^>]*>");
    private static final List<String> BANNED = List.of("th:utext", "[(", "style=", "onclick=", "onerror=", "javascript:");

    @Test
    void templatesContainNoUnsafeConstructs() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            List<Path> templates = files.filter(p -> p.toString().endsWith(".html")).toList();
            assertThat(templates).isNotEmpty();
            for (Path template : templates) {
                String html = Files.readString(template);
                for (String banned : BANNED) {
                    assertThat(html).as("%s must not contain %s", template, banned).doesNotContain(banned);
                }
                assertThat(INLINE_SCRIPT.matcher(html).find()).as("%s has an inline script", template).isFalse();
            }
        }
    }
}
