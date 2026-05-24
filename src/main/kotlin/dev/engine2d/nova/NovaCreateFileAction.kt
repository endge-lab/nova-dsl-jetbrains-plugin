package dev.engine2d.nova

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.nio.charset.StandardCharsets

abstract class NovaCreateFileAction(
  private val extension: String,
  private val defaultName: String,
  private val title: String,
  private val template: String,
) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null && resolveTargetDirectory(event) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val targetDirectory = resolveTargetDirectory(event) ?: return
    val rawName = Messages.showInputDialog(
      project,
      "File name:",
      title,
      null,
      defaultName,
      null,
    ) ?: return
    val fileName = normalizeFileName(rawName.trim())
    if (fileName.isBlank()) return

    WriteCommandAction.runWriteCommandAction(project, title, null, Runnable {
      runWriteAction {
        val file = targetDirectory.createChildData(this, fileName)
        file.setBinaryContent(template.toByteArray(StandardCharsets.UTF_8))
        PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
          event.getData(LangDataKeys.IDE_VIEW)?.selectElement(psiFile)
        }
      }
    })
  }

  private fun normalizeFileName(value: String): String {
    if (value.endsWith(".$extension")) return value
    return "$value.$extension"
  }

  private fun resolveTargetDirectory(event: AnActionEvent): VirtualFile? {
    val ideView = event.getData(LangDataKeys.IDE_VIEW)
    val directory = ideView?.orChooseDirectory?.virtualFile
    if (directory != null) return directory

    val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
    return if (file.isDirectory) file else file.parent
  }
}

class CreateNovaDslFileAction : NovaCreateFileAction(
  extension = "nova",
  defaultName = "NovaApp.nova",
  title = "New Nova DSL File",
  template = """
    <script setup lang="ts">
    const props = defineProps()
    </script>

    <template>
      <Root :width="props.width" :height="props.height">
        <TextBlock text="Nova DSL" />
      </Root>
    </template>
  """.trimIndent() + "\n",
)

class CreateNovaCssFileAction : NovaCreateFileAction(
  extension = "novacss",
  defaultName = "styles.novacss",
  title = "New NovaCSS File",
  template = """
    @theme light {
      --nova-scene-bg: #ffffff;
      --nova-scene-text: #0f172a;
    }
  """.trimIndent() + "\n",
)
