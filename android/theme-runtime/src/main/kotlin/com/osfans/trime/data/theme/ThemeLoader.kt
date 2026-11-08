/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import timber.log.Timber
import java.io.File

/**
 * Loads a theme from its source YAML, expanding the supported librime DSL
 * subset directly. Themes using constructs outside that subset fall back to
 * the librime-deployed artifact. Failures are reported as [ThemeLoadError]
 * instead of a bare log line, so callers can fall back and surface
 * diagnostics (YAML syntax errors carry line/column info).
 */
object ThemeLoader {
    const val CONFIG_VERSION_KEY = "config_version"

    private const val INCLUDE = "__include"
    private const val PATCH = "__patch"

    /** Structured failure of a single theme load. */
    sealed class ThemeLoadError(
        val themeId: String,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        class FileNotFound(
            themeId: String,
            val path: String,
        ) : ThemeLoadError(themeId, "Deployed theme file not found: $path")

        class FileUnreadable(
            themeId: String,
            val path: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Cannot read theme file: $path", cause)

        /** Not valid YAML; [cause] message usually includes line/column info. */
        class YamlParseError(
            themeId: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Failed to parse theme YAML: ${cause.message}", cause)

        /** Root is not a mapping, or the theme structure is invalid. */
        class InvalidStructure(
            themeId: String,
            detail: String,
            cause: Throwable? = null,
        ) : ThemeLoadError(themeId, "Invalid theme structure: $detail", cause)

        /**
         * 主题文件根本不在：目录里没有 `$themeId.yaml`。
         *
         * 和 [DeployUnavailable] 分开报，因为这两件事的处理完全不同：这一件是
         * 「选错了主题，或者文件没拷过来」；那一件是「文件在，但用了编辑器读不懂的写法」。
         * 混成一句「没有 librime」，人会去查输入法，而问题在文件名。
         */
        class SourceNotFound(
            themeId: String,
        ) : ThemeLoadError(themeId, "Theme file not found: $themeId.yaml")

        /**
         * The theme declares no color scheme, so there is nothing the runtime
         * could render it with.
         */
        class NoColorScheme(
            themeId: String,
        ) : ThemeLoadError(themeId, "No color scheme is defined")

        /**
         * Source files could not be read faithfully and there is no librime to
         * deploy the theme with either — the editor's situation. Reported
         * instead of guessing a path, so the caller can say why the theme
         * stayed unloaded.
         *
         * @param reason the loader's own words for why the source was refused;
         *   without it the message only says the fallback is missing, which
         *   tells a user nothing about the file they are looking at.
         */
        class DeployUnavailable(
            themeId: String,
            val reason: String? = null,
        ) : ThemeLoadError(
            themeId,
            if (reason == null) {
                "No librime to deploy the theme with"
            } else {
                "No librime to deploy the theme with, and its source $reason"
            },
        )
    }

    sealed interface ThemeLoadResult {
        data class Success(
            val themeId: String,
            val theme: Theme,
            /**
             * Why the source file was not used, when the theme came from the
             * deployed artifact instead. Null when the file was read faithfully.
             */
            val fallbackReason: String? = null,
            /**
             * What static checks found in the theme, or null when the checks
             * could not run at all.
             */
            val findings: List<ThemeDiagnostics.Finding>? = null,
            /**
             * What the load read, so a caller caching the decoded theme can
             * tell later whether the cache is still valid.
             */
            val sources: ThemeSourceSnapshot? = null,
        ) : ThemeLoadResult

        data class Failure(
            val themeId: String,
            val error: ThemeLoadError,
        ) : ThemeLoadResult
    }

    /**
     * Loads [themeId], preferring its source files and falling back to the
     * librime-deployed artifact when the source cannot be read faithfully (see
     * [loadFromSource]). Never throws: returns [ThemeLoadResult.Success] or
     * [ThemeLoadResult.Failure] with a structured [ThemeLoadError].
     */
    fun loadTheme(themeId: String): ThemeLoadResult {
        val sources = SourceLoader()
        // The reason is carried through this call instead of a field on the
        // object: themes are loaded from several threads (warm-up, the theme
        // list, a switch), and a shared field let one load report another
        // one's failure.
        var reason: String? = null
        val source = loadFromSource(themeId, sources = sources) { reason = it }
        // A [ThemeLoadResult.Failure] here is a source that decoded but is
        // unusable (no colour scheme); it is reported as is rather than papered
        // over with whatever librime deployed earlier.
        if (source != null) return source
        // 读都没读到（而不是读了但不合用）：`reason` 只在「源文件被拒」时才会写，
        // 它还是空就说明压根没有这个文件。这时不该提 librime——那是另一件事。
        if (reason == null) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.SourceNotFound(themeId))
        }
        // The deployed artifact can go stale as well — the source file it was
        // compiled from may still change — so both are part of the snapshot.
        val tried = sources.snapshot()
        return when (val deployed = loadDeployedTheme(themeId, reason)) {
            is ThemeLoadResult.Success -> {
                val merged = deployed.sources?.merge(tried) ?: tried
                deployed.copy(fallbackReason = reason, sources = merged)
            }

            is ThemeLoadResult.Failure -> deployed
        }
    }

    /**
     * Reads [themeId] and its dependencies from source files, expands the
     * supported DSL subset and decodes the result. Returns null whenever the
     * source cannot be read faithfully — missing, unreadable, using DSL outside
     * the subset, or failing to decode — so the caller falls back to the
     * deployed artifact and librime decides what the file means.
     *
     * @param file source file of [themeId] when it is already known.
     * @param sources resource lookup; the data dirs by default, a fixture loader
     *   in tests.
     * @param onFallback receives the loader's own words for why the source was
     *   refused, so the caller can report it without a shared field.
     */
    fun loadFromSource(
        themeId: String,
        file: File? = null,
        sources: SourceLoader = SourceLoader(),
        onFallback: (String) -> Unit = {},
    ): ThemeLoadResult? {
        val node = sources.load(themeId, file) ?: return null
        return try {
            val decoded = decodeAndReport(themeId, expandSource(themeId, node) { id -> sources.load(id, null) })
            if (decoded is ThemeLoadResult.Success) decoded.copy(sources = sources.snapshot()) else decoded
        } catch (e: ThemeDslExpander.UnsupportedDsl) {
            fallBack(themeId, e, "uses DSL outside the supported subset (%s)", onFallback)
        } catch (e: ThemeDslExpander.UnresolvedReference) {
            fallBack(themeId, e, "has unresolved references (%s)", onFallback)
        } catch (e: Exception) {
            fallBack(themeId, e, "cannot be decoded from its source (%s)", onFallback)
        }
    }

    /** Reports why the source was not used and asks for the deployed artifact. */
    private fun fallBack(
        themeId: String,
        cause: Exception,
        reason: String,
        onFallback: (String) -> Unit,
    ): ThemeLoadResult? {
        val message = reason.format(cause.message)
        Timber.w(cause, "Theme '%s' $reason, falling back to the deployed artifact", themeId, cause.message)
        onFallback(message)
        return null
    }

    /**
     * Decodes [mapping] and reports what the runtime ignores or cannot resolve
     * in it, so a theme is checked when it is read instead of on first use.
     * Diagnostics never affect the result of a load.
     *
     * A theme that declares no color scheme is refused: it decodes, but there is
     * nothing for the runtime to pick, so loading it would only fail later.
     */
    fun decodeAndReport(
        themeId: String,
        mapping: YamlMap,
    ): ThemeLoadResult {
        val theme = ThemeYaml.parser.decodeFromYamlNode<Theme>(mapping)
        if (theme.presetColorSchemes.isEmpty()) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.NoColorScheme(themeId))
        }
        val findings =
            runCatching { ThemeDiagnostics.lint(theme, mapping) }
                .onFailure { Timber.w(it, "Theme '%s': diagnostics failed", themeId) }
                .getOrNull()
        findings?.let { ThemeDiagnostics.log(themeId, it) }
        return ThemeLoadResult.Success(themeId, theme, findings = findings)
    }

    /**
     * Expands [node] with [loadResource] and decodes the result. Kept separate
     * from the file lookup so tests can feed fixture resources.
     */
    fun decodeSource(
        themeId: String,
        node: YamlNode,
        loadResource: (String) -> YamlNode?,
    ): Theme = ThemeYaml.parser.decodeFromYamlNode(expandSource(themeId, node, loadResource))

    /** Applies the supported DSL subset to [node] and returns its root mapping. */
    private fun expandSource(
        themeId: String,
        node: YamlNode,
        loadResource: (String) -> YamlNode?,
    ): YamlMap {
        val expanded = ThemeDslExpander.expand(themeId, node, loadResource)
        return expanded.mapping
            ?: throw ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping")
    }

    /**
     * Reads [themeId] from [file], or looks its source up in the user and
     * shared data dirs when [file] is null, and expands the supported DSL
     * subset, so a name that an `__include` provides is resolved as well.
     * Returns null when the source is missing, unreadable, or uses DSL outside
     * the supported subset.
     *
     * @param sources resource lookup; the data dirs by default, a fixture loader
     *   in tests.
     */
    fun loadSourceNode(
        themeId: String,
        file: File? = null,
        sources: SourceLoader = SourceLoader(),
    ): YamlNode? {
        val node = sources.load(themeId, file) ?: return null
        return runCatching {
            ThemeDslExpander.expand(themeId, node) { id -> sources.load(id, null) }
        }.getOrNull()
    }

    /**
     * Applies the librime auto-patch convention: unless the root already has an
     * explicit `__patch`, the `patch` node of `<id>.custom.yaml` is applied on
     * top of the resource. Reads `trime.yaml` source files (user data first,
     * then shared data) the same way librime resolves resources.
     *
     * @param findSource source file of a resource id; the data dirs by default.
     */
    class SourceLoader(
        private val findSource: (String) -> File? = ::findSourceFile,
    ) {
        private val cache = HashMap<String, YamlNode?>()

        /** By path, so a file reached twice is stamped once. */
        private val stamps = LinkedHashMap<String, ThemeFileStamp>()

        private val missing = LinkedHashSet<String>()

        /**
         * @param file source file of [resourceId] when it is already known.
         *   Included resources are always looked up by id. An explicit file
         *   wins over the id lookup cache.
         */
        fun load(resourceId: String, file: File?): YamlNode? {
            if (file != null) return readAndStamp(resourceId, null, file)
            if (cache.containsKey(resourceId)) return cache[resourceId]
            val found = findSource(resourceId)
            if (found == null) missing += resourceId
            val result = found?.let { readAndStamp(resourceId, resourceId, it) }
            cache[resourceId] = result
            return result
        }

        /** What this loader read, for a caller that wants to cache the result. */
        fun snapshot(): ThemeSourceSnapshot = ThemeSourceSnapshot(stamps.values.toList(), missing.toList())

        /**
         * Stamps [file] before reading it: a change racing the read then fails
         * the next staleness check instead of being cached as current.
         */
        private fun readAndStamp(
            key: String,
            resourceId: String?,
            file: File,
        ): YamlNode? {
            stamps.getOrPut(file.absolutePath) { ThemeFileStamp.of(file, resourceId) }
            return readAndPatch(key, file)
        }

        private fun readAndPatch(resourceId: String, file: File): YamlNode? {
            val node = runCatching { ThemeYaml.parser.parseToYamlNode(file.readText()) }.getOrNull()
            return node?.let { applyCustomPatch(resourceId, it) }
        }
    }

    /** Source file of [resourceId]: the user data dir first, then the shared one. */
    fun findSourceFile(resourceId: String): File? = findSourceFile(
        resourceId,
        ThemeDirectory.roots(),
    )

    /**
     * Source file of [resourceId] under [roots], in order. A resource id is free
     * text in an include directive, so a file that resolves outside the root it
     * was found in is refused; librime still resolves such an id on its own, so
     * the caller falls back to the deployed artifact.
     */
    fun findSourceFile(
        resourceId: String,
        roots: List<File>,
    ): File? {
        val relative = "$resourceId.yaml"
        return roots.firstNotNullOfOrNull { root ->
            root.resolve(relative).takeIf { it.isFile && it.isInside(root) }
        }
    }

    /** Whether this file really lives in [root]: `..` and absolute ids are escapes. */
    private fun File.isInside(root: File): Boolean = runCatching {
        // java.nio.file is unavailable before API 26, so compare canonical
        // paths with an explicit separator to keep the boundary check.
        val rootPath = root.canonicalFile.path.trimEnd(File.separatorChar)
        val filePath = canonicalFile.path
        filePath == rootPath || filePath.startsWith(rootPath + File.separator)
    }.getOrDefault(false)

    /**
     * Injects the patch of `<id>.custom.yaml` as librime's auto-patch plugin
     * does: the optional reference `__patch: <id>.custom:/patch?`, resolved in
     * that resource. Reading it as a resource keeps the patch's own directives
     * (an `__include`, for instance) relative to the file they are written in.
     * An explicit root `__patch` wins; `.custom` files are never patched.
     */
    fun applyCustomPatch(
        resourceId: String,
        node: YamlNode,
    ): YamlNode {
        if (resourceId.endsWith(".custom")) return node
        val root = node as? YamlMap ?: return node
        if (root.pairs[PATCH] != null) return node
        val patchId = resourceId.removeSuffix(".schema") + ".custom"
        return yamlMapOf(root.pairs + (PATCH to yamlScalarOf("$patchId:/patch?")))
    }

    /**
     * Loads the theme from its librime-deployed artifact.
     *
     * @param sourceFailure why the source path was given up on, carried into
     *   [ThemeLoadError.DeployUnavailable] so the reason survives the very case
     *   where the fallback is missing.
     */
    private fun loadDeployedTheme(
        themeId: String,
        sourceFailure: String?,
    ): ThemeLoadResult {
        // The editor has no librime at all, so there is neither a deploy step
        // nor an artifact to fall back to; say so instead of reading a path
        // that this process cannot even name.
        val deployer = ThemeDirectory.deployer
            ?: return ThemeLoadResult.Failure(
                themeId,
                ThemeLoadError.DeployUnavailable(themeId, sourceFailure),
            )
        // Returns false when the artifact is already up to date (mtime cache), which is fine.
        if (!deployer(themeId, CONFIG_VERSION_KEY)) {
            Timber.w("Failed to deploy theme config file '$themeId.yaml'")
        }

        val file =
            ThemeDirectory.deployedFile(themeId)
                ?: return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.DeployUnavailable(themeId, sourceFailure),
                )
        val path = file.absolutePath
        if (!file.exists()) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.FileNotFound(themeId, path))
        }
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.FileUnreadable(themeId, path, e),
                )
            }

        val node =
            try {
                ThemeYaml.parser.parseToYamlNode(content)
            } catch (e: Exception) {
                return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.YamlParseError(themeId, e),
                )
            }
        val mapping = node.mapping ?: return ThemeLoadResult.Failure(
            themeId,
            ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping"),
        )

        return try {
            // Stamped after the deploy above, so the recorded state is the one
            // the decode actually read.
            val deployed = ThemeSourceSnapshot(listOf(ThemeFileStamp.of(file)))
            when (val result = decodeAndReport(themeId, mapping)) {
                is ThemeLoadResult.Success -> result.copy(sources = deployed)
                is ThemeLoadResult.Failure -> result
            }
        } catch (e: Exception) {
            ThemeLoadResult.Failure(
                themeId,
                ThemeLoadError.InvalidStructure(themeId, "Decode failed", e),
            )
        }
    }
}
