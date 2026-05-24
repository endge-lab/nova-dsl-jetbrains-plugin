package dev.engine2d.nova

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class NovaFileTypeAssociationStartupActivity : StartupActivity, DumbAware {
  override fun runActivity(project: Project) {
    val manager = FileTypeManagerEx.getInstanceEx()
    manager.makeFileTypesChange("Register Nova file type associations") {
      ensureExtensionOwner(manager, "nova", NovaFileType.INSTANCE)
      ensureExtensionOwner(manager, "novacss", NovaCssFileType.INSTANCE)
    }
  }

  private fun ensureExtensionOwner(
    manager: FileTypeManagerEx,
    extension: String,
    owner: FileType,
  ) {
    for (fileType in manager.registeredFileTypes) {
      val ownsExtension = manager.getAssociations(fileType).any { matcher ->
        matcher is ExtensionFileNameMatcher && matcher.extension == extension
      }
      if (ownsExtension) {
        manager.removeAssociatedExtension(fileType, extension)
      }
    }
    manager.associateExtension(owner, extension)
  }
}
