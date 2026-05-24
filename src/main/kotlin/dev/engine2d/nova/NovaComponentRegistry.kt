package dev.engine2d.nova

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

data class NovaComponentPropDoc(
  val name: String,
  val type: String,
  val required: Boolean,
  val description: String,
)

data class NovaComponentDoc(
  val name: String,
  val title: String,
  val description: String,
  val source: String?,
  val snippet: String,
  val props: List<NovaComponentPropDoc>,
  val groupTitle: String,
)

object NovaComponentRegistry {
  private val bundled by lazy { loadBundledManifests() }
  private val projectCache = ConcurrentHashMap<String, List<NovaComponentDoc>>()

  fun components(project: Project?): List<NovaComponentDoc> {
    val projectComponents = project?.let { currentProjectManifests(it) } ?: emptyList()
    return mergeComponents(bundled + projectComponents)
  }

  fun find(project: Project?, name: String): NovaComponentDoc? {
    return components(project).firstOrNull { it.name == name }
  }

  private fun loadBundledManifests(): List<NovaComponentDoc> {
    val loader = NovaComponentRegistry::class.java.classLoader
    val resources = listOf(
      "nova-components/nova-ui-kit.json",
      "nova-components/nova-vue.json",
      "nova-components/timeline-chart.json",
    )
    return resources.flatMap { resource ->
      loader.getResourceAsStream(resource)?.use { stream ->
        parseManifest(InputStreamReader(stream, Charsets.UTF_8).readText())
      } ?: emptyList()
    }
  }

  private fun currentProjectManifests(project: Project): List<NovaComponentDoc> {
    val base = project.baseDir ?: return emptyList()
    return projectCache.getOrPut(base.path) { loadProjectManifests(base) }
  }

  private fun loadProjectManifests(base: VirtualFile): List<NovaComponentDoc> {
    val result = mutableListOf<NovaComponentDoc>()
    val packageJsonFiles = collectKnownPackageJsonFiles(base)

    for (packageJson in packageJsonFiles) {
      val json = runCatching { JsonParser.parseString(String(packageJson.contentsToByteArray())).asJsonObject }.getOrNull() ?: continue
      val componentPath = json.getAsJsonObject("nova")?.get("components")?.asString ?: continue
      val manifestFile = VfsUtil.findRelativeFile(componentPath, packageJson.parent) ?: continue
      val manifestText = runCatching { String(manifestFile.contentsToByteArray()) }.getOrNull() ?: continue
      result += parseManifest(manifestText)
    }

    return result
  }

  private fun collectKnownPackageJsonFiles(base: VirtualFile): List<VirtualFile> {
    val result = linkedSetOf<VirtualFile>()
    base.findChild("package.json")?.let { result.add(it) }

    collectPackageJsonChildren(base.findChild("packages"), result)
    collectPackageJsonChildren(base.findChild("examples"), result)
    collectScopedPackageJsonChildren(base.findChild("node_modules")?.findChild("@endge"), result)
    collectScopedPackageJsonChildren(base.findChild("node_modules")?.findChild("@engine2d"), result)

    return result.toList()
  }

  private fun collectPackageJsonChildren(root: VirtualFile?, result: MutableSet<VirtualFile>, depth: Int = 0) {
    if (root == null || !root.isDirectory) return
    if (depth > 2 || root.name in SKIPPED_PACKAGE_DIRS) return

    for (child in root.children.take(80)) {
      if (!child.isDirectory) continue
      child.findChild("package.json")?.let { result.add(it) }
      collectPackageJsonChildren(child, result, depth + 1)
    }
  }

  private fun collectScopedPackageJsonChildren(scope: VirtualFile?, result: MutableSet<VirtualFile>) {
    if (scope == null || !scope.isDirectory) return

    for (child in scope.children.take(80)) {
      if (!child.isDirectory) continue
      child.findChild("package.json")?.let { result.add(it) }
    }
  }

  private fun parseManifest(source: String): List<NovaComponentDoc> {
    val root = runCatching { JsonParser.parseString(source).asJsonObject }.getOrNull() ?: return emptyList()
    val groups = root.getAsJsonArray("groups") ?: return emptyList()
    val result = mutableListOf<NovaComponentDoc>()
    for (groupElement in groups) {
      val group = groupElement.asJsonObject
      val groupTitle = group.string("title") ?: group.string("id") ?: "Nova"
      val components = group.getAsJsonArray("components") ?: continue
      for (componentElement in components) {
        val component = componentElement.asJsonObject
        val name = component.string("name") ?: continue
        val props = component.getAsJsonArray("props")?.mapNotNull { propElement ->
          val prop = propElement.asJsonObject
          val propName = prop.string("name") ?: return@mapNotNull null
          NovaComponentPropDoc(
            name = propName,
            type = prop.string("type") ?: "unknown",
            required = prop.get("required")?.asBoolean == true,
            description = prop.localized("description") ?: "Prop $propName.",
          )
        } ?: emptyList()
        result += NovaComponentDoc(
          name = name,
          title = component.string("title") ?: name,
          description = component.localized("description") ?: "Компонент Nova $name.",
          source = component.string("source"),
          snippet = component.string("snippet") ?: "<$name />",
          props = props,
          groupTitle = groupTitle,
        )
      }
    }
    return result
  }

  private fun mergeComponents(components: List<NovaComponentDoc>): List<NovaComponentDoc> {
    val byName = linkedMapOf<String, NovaComponentDoc>()
    for (component in components) byName[component.name] = component
    return byName.values.sortedWith(compareBy<NovaComponentDoc> { it.groupTitle }.thenBy { it.name })
  }

  private fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    return if (value.isJsonPrimitive) value.asString else null
  }

  private fun JsonObject.localized(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonPrimitive) return value.asString
    val objectValue = value.asJsonObject
    return objectValue.string("ru") ?: objectValue.string("en")
  }

  private val SKIPPED_PACKAGE_DIRS = setOf(
    ".git",
    ".gradle",
    ".idea",
    "build",
    "coverage",
    "dist",
    "node_modules",
  )
}
