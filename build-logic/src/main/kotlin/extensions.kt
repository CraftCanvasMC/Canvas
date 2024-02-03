import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

fun JavaPluginExtension.javaTarget(version: Int) {
  toolchain.languageVersion.set(JavaLanguageVersion.of(version))
}
