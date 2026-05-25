package dev.engine2d.nova

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

data class NovaLocalComponent(
  val name: String,
  val file: VirtualFile,
)

object NovaLocalComponentIndex {
  private val cache = ConcurrentHashMap<String, List<NovaLocalComponent>>()

  fun components(project: Project, currentFile: VirtualFile?): List<NovaLocalComponent> {
    val basePath = project.basePath ?: return emptyList()
    val base = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
    return cache.getOrPut(base.path) { collectComponents(base) }
      .filter { currentFile == null || it.file.path != currentFile.path }
  }

  private fun collectComponents(base: VirtualFile): List<NovaLocalComponent> {
    val result = mutableListOf<NovaLocalComponent>()
    visit(base, result, 0)
    return result.sortedBy { it.name }
  }

  private fun visit(file: VirtualFile, result: MutableList<NovaLocalComponent>, depth: Int) {
    if (result.size >= MAX_COMPONENTS || depth > MAX_DEPTH) return
    if (file.isDirectory) {
      if (file.name in SKIPPED_DIRS) return
      for (child in file.children.take(MAX_CHILDREN_PER_DIR)) {
        visit(child, result, depth + 1)
        if (result.size >= MAX_COMPONENTS) return
      }
      return
    }

    if (file.extension != "nova") return
    val name = file.nameWithoutExtension.toComponentName()
    if (name.isNotBlank()) result += NovaLocalComponent(name = name, file = file)
  }

  private fun String.toComponentName(): String {
    return split('-', '_', '.', ' ')
      .filter { it.isNotBlank() }
      .joinToString("") { part -> part.replaceFirstChar { char -> char.uppercaseChar() } }
  }

  private val SKIPPED_DIRS = setOf(
    ".git",
    ".gradle",
    ".idea",
    "build",
    "coverage",
    "dist",
    "node_modules",
    "out",
  )

  private const val MAX_DEPTH = 8
  private const val MAX_CHILDREN_PER_DIR = 160
  private const val MAX_COMPONENTS = 600
}
