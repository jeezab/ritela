import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Usage

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val ktlint = configurations.create("ktlint") {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

dependencies {
    add(ktlint.name, libs.ktlint)
}

fun registerKotlinStyleTask(name: String, format: Boolean) = tasks.register<JavaExec>(name) {
    group = "verification"
    description =
        if (format) "Format Kotlin sources and build scripts" else "Check Kotlin formatting"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        listOfNotNull(if (format) "--format" else null) +
            listOf("*.gradle.kts", "app/src/**/*.kt", "app/build.gradle.kts")
    )
}

registerKotlinStyleTask("checkKotlin", format = false)
registerKotlinStyleTask("formatKotlin", format = true)
