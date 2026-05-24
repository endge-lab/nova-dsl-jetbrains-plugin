package dev.engine2d.nova

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class NovaComponentsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = NovaComponentsPanel(project)
    val content = ContentFactory.getInstance().createContent(panel, "Components", false)
    toolWindow.contentManager.addContent(content)
  }
}

private class NovaComponentsPanel(
  private val project: Project,
) : JPanel(BorderLayout()) {
  private val allComponents = NovaComponentRegistry.components(project)
  private val rootNode = DefaultMutableTreeNode("Nova")
  private val treeModel = DefaultTreeModel(rootNode)
  private val tree = JTree(treeModel)
  private val search = JBTextField()
  private val details = JEditorPane("text/html", "")
  private val insertButton = JButton("Insert snippet")
  private val sourceButton = JButton("Open source")

  init {
    search.emptyText.text = "Поиск компонента или prop"
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.cellRenderer = NovaComponentTreeRenderer()
    tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    tree.dragEnabled = true
    tree.transferHandler = object : TransferHandler() {
      override fun getSourceActions(component: javax.swing.JComponent): Int = COPY

      override fun createTransferable(component: javax.swing.JComponent): Transferable {
        return StringSelection(buildSnippetFromSelection() ?: "")
      }
    }

    details.isEditable = false
    details.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

    val left = JPanel(BorderLayout())
    left.add(search, BorderLayout.NORTH)
    left.add(JBScrollPane(tree), BorderLayout.CENTER)

    val right = JPanel(BorderLayout())
    right.add(JBScrollPane(details), BorderLayout.CENTER)
    val actions = JPanel()
    actions.add(insertButton)
    actions.add(sourceButton)
    right.add(actions, BorderLayout.SOUTH)

    val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, left, right)
    split.resizeWeight = 0.62
    split.preferredSize = Dimension(360, 640)
    add(split, BorderLayout.CENTER)

    search.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) = refill()
      override fun removeUpdate(e: DocumentEvent) = refill()
      override fun changedUpdate(e: DocumentEvent) = refill()
    })
    tree.addTreeSelectionListener { renderDetails(tree.selectionPaths?.firstOrNull()) }
    insertButton.addActionListener { insertSelectedSnippet() }
    sourceButton.addActionListener { openSelectedSource() }

    refill()
    selectFirstComponent()
  }

  private fun refill() {
    val query = search.text.trim().lowercase()
    rootNode.removeAllChildren()

    val grouped = allComponents
      .filter { componentMatches(it, query) }
      .groupBy { it.groupTitle }
      .toSortedMap()

    for ((groupTitle, components) in grouped) {
      val groupNode = DefaultMutableTreeNode(NovaComponentGroupNode(groupTitle))
      for (component in components.sortedBy { it.name }) {
        val componentNode = DefaultMutableTreeNode(component)
        for (prop in component.props.sortedWith(compareByDescending<NovaComponentPropDoc> { it.required }.thenBy { it.name })) {
          componentNode.add(DefaultMutableTreeNode(NovaComponentPropNode(component, prop)))
        }
        groupNode.add(componentNode)
      }
      rootNode.add(groupNode)
    }

    treeModel.reload()
    for (row in 0 until minOf(tree.rowCount, 8)) tree.expandRow(row)
  }

  private fun componentMatches(component: NovaComponentDoc, query: String): Boolean {
    if (query.isBlank()) return true
    if (component.name.lowercase().contains(query)) return true
    if (component.description.lowercase().contains(query)) return true
    return component.props.any {
      it.name.lowercase().contains(query) || it.description.lowercase().contains(query)
    }
  }

  private fun selectFirstComponent() {
    val group = rootNode.firstChild as? DefaultMutableTreeNode ?: return
    val component = group.firstChild as? DefaultMutableTreeNode ?: return
    tree.selectionPath = TreePath(component.path)
  }

  private fun renderDetails(path: TreePath?) {
    val node = path?.lastPathComponent as? DefaultMutableTreeNode
    when (val value = node?.userObject) {
      is NovaComponentDoc -> renderComponentDetails(value)
      is NovaComponentPropNode -> renderPropDetails(value)
      is NovaComponentGroupNode -> {
        setDetailsHtml("<h3>${escapeHtml(value.title)}</h3>")
      }
      else -> {
        setDetailsHtml("")
      }
    }
  }

  private fun renderComponentDetails(component: NovaComponentDoc) {
    val props = component.props.joinToString("") { prop ->
      val required = if (prop.required) " <strong class=\"required\">required</strong>" else ""
      "<li><code>${escapeHtml(prop.name)}</code>: ${escapeHtml(shortDescription(prop.description))}$required</li>"
    }
    setDetailsHtml("""
      <h3>${escapeHtml(component.name)}</h3>
      <div class="meta">${escapeHtml(component.groupTitle)}</div>
      <p>${escapeHtml(component.description)}</p>
      <h4>Props</h4>
      ${if (props.isBlank()) "<p class=\"muted\">Нет props.</p>" else "<ul>$props</ul>"}
      <p class="hint">Перетащите компонент или выбранные props в редактор.</p>
    """.trimIndent())
  }

  private fun renderPropDetails(node: NovaComponentPropNode) {
    val required = if (node.prop.required) "Да" else "Нет"
    setDetailsHtml("""
      <h3>${escapeHtml(node.component.name)}.<code>${escapeHtml(node.prop.name)}</code></h3>
      <p>${escapeHtml(node.prop.description)}</p>
      <dl>
        <dt>Тип</dt><dd><code>${escapeHtml(node.prop.type)}</code></dd>
        <dt>Обязательный</dt><dd>$required</dd>
      </dl>
      <p class="hint">Перетащите prop, чтобы вставить ${escapeHtml(node.component.name)} с выбранными props.</p>
    """.trimIndent())
  }

  private fun insertSelectedSnippet() {
    val snippet = buildSnippetFromSelection() ?: return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    WriteCommandAction.runWriteCommandAction(project, "Insert Nova component", null, Runnable {
      editor.document.insertString(editor.caretModel.offset, snippet)
    })
  }

  private fun openSelectedSource() {
    val component = selectedComponent() ?: return
    val source = component.source ?: return
    val basePath = project.basePath ?: return
    val base = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
    val file = VfsUtil.findRelativeFile(source, base) ?: return
    FileEditorManager.getInstance(project).openFile(file, true)
  }

  private fun selectedComponent(): NovaComponentDoc? {
    val path = tree.selectionPaths?.firstOrNull() ?: return null
    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
    return when (val value = node.userObject) {
      is NovaComponentDoc -> value
      is NovaComponentPropNode -> value.component
      else -> null
    }
  }

  private fun buildSnippetFromSelection(): String? {
    val paths = tree.selectionPaths?.toList().orEmpty()
    if (paths.isEmpty()) return null

    val selectedComponents = linkedSetOf<NovaComponentDoc>()
    val selectedProps = linkedMapOf<NovaComponentDoc, MutableSet<NovaComponentPropDoc>>()

    for (path in paths) {
      val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
      when (val value = node.userObject) {
        is NovaComponentDoc -> selectedComponents.add(value)
        is NovaComponentPropNode -> selectedProps.getOrPut(value.component) { linkedSetOf() }.add(value.prop)
      }
    }

    for (component in selectedComponents) {
      selectedProps.putIfAbsent(component, linkedSetOf())
    }

    if (selectedProps.isEmpty()) return null

    return selectedProps.entries.joinToString("\n") { (component, props) ->
      buildComponentSnippet(component, props)
    }
  }

  private fun buildComponentSnippet(
    component: NovaComponentDoc,
    selectedProps: Set<NovaComponentPropDoc>,
  ): String {
    val props = linkedSetOf<NovaComponentPropDoc>()
    props += component.props.filter { it.required }
    props += selectedProps

    val attrs = props
      .sortedWith(compareByDescending<NovaComponentPropDoc> { it.required }.thenBy { it.name })
      .joinToString(" ") { prop -> renderPropAttribute(prop) }

    val attrText = attrs.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    return if (component.name in LEAF_COMPONENTS) {
      "<${component.name}$attrText />"
    } else {
      "<${component.name}$attrText>\n  \n</${component.name}>"
    }
  }

  private fun renderPropAttribute(prop: NovaComponentPropDoc): String {
    val name = prop.name
    val type = prop.type.lowercase()
    if (type == "boolean") return name

    val example = exampleValue(name, type)
    return if (example.dynamic) {
      ":$name=\"${example.value}\""
    } else {
      "$name=\"${example.value}\""
    }
  }

  private fun exampleValue(name: String, type: String): ExampleValue {
    return when {
      name == "text" || name == "value" || name == "title" || name == "label" -> ExampleValue("Example")
      name == "id" -> ExampleValue("example-id")
      name == "src" || name == "source" || name == "icon" -> ExampleValue("../assets/icon.svg")
      name == "background" || name == "color" || name.endsWith("color") -> ExampleValue("#2563eb")
      type.contains("number") || name in NUMERIC_PROPS -> ExampleValue("120", dynamic = true)
      type.contains("array") -> ExampleValue("[]", dynamic = true)
      type.contains("function") || type.contains("=>") -> ExampleValue("() => {}", dynamic = true)
      type.contains("object") || type.contains("record") || type.contains("unknown") -> ExampleValue("{}", dynamic = true)
      type.contains("|") -> ExampleValue(type.split("|").first().trim().trim('\'', '"'))
      else -> ExampleValue("example")
    }
  }

  private fun shortDescription(description: String): String {
    return description.substringBefore(".").substringBefore("\n").trim().ifBlank { description }
  }

  private fun setDetailsHtml(body: String) {
    details.text = """
      <html>
        <head>
          <style>
            body {
              font-family: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif;
              font-size: 11px;
              line-height: 1.35;
              margin: 8px;
            }
            h3 {
              font-size: 13px;
              margin: 0 0 2px 0;
            }
            h4 {
              font-size: 11px;
              margin: 10px 0 4px 0;
              text-transform: uppercase;
              letter-spacing: .04em;
            }
            p {
              margin: 6px 0;
            }
            ul {
              margin: 4px 0 0 14px;
              padding: 0;
            }
            li {
              margin: 2px 0;
            }
            code {
              font-family: JetBrains Mono, Menlo, monospace;
              font-size: 10px;
            }
            dl {
              margin: 6px 0;
            }
            dt {
              font-weight: 700;
              margin-top: 4px;
            }
            dd {
              margin-left: 0;
            }
            .meta, .muted, .hint {
              color: #6b7280;
            }
            .required {
              color: #b45309;
            }
          </style>
        </head>
        <body>$body</body>
      </html>
    """.trimIndent()
    details.caretPosition = 0
  }

  private fun escapeHtml(value: String): String {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}

private data class NovaComponentGroupNode(val title: String)

private data class NovaComponentPropNode(
  val component: NovaComponentDoc,
  val prop: NovaComponentPropDoc,
)

private data class ExampleValue(
  val value: String,
  val dynamic: Boolean = false,
)

private class NovaComponentTreeRenderer : DefaultTreeCellRenderer() {
  override fun getTreeCellRendererComponent(
    tree: JTree?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ): java.awt.Component {
    val label = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus) as JLabel
    val node = value as? DefaultMutableTreeNode
    when (val userObject = node?.userObject) {
      is NovaComponentGroupNode -> label.text = userObject.title
      is NovaComponentDoc -> label.text = "${userObject.name}  ·  ${userObject.groupTitle}"
      is NovaComponentPropNode -> {
        val required = if (userObject.prop.required) " *" else ""
        label.text = "${userObject.prop.name}: ${userObject.prop.description.substringBefore(".")}$required"
      }
    }
    return label
  }
}

private val LEAF_COMPONENTS = setOf(
  "Icon",
  "Line",
  "Rect",
  "Circle",
  "Text",
  "TextBlock",
  "ProgressRing",
  "StripePattern",
)

private val NUMERIC_PROPS = setOf(
  "x",
  "y",
  "x1",
  "x2",
  "y1",
  "y2",
  "width",
  "height",
  "size",
  "radius",
  "opacity",
  "stroke-width",
  "strokeWidth",
)
