package dev.engine2d.nova

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
  val packageName: String?,
  val importSource: String?,
  val importName: String?,
  val isGlobal: Boolean,
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
    val basePath = project.basePath ?: return emptyList()
    val base = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
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
    val packageName = root.string("packageName")
    val groups = root.getAsJsonArray("groups") ?: return emptyList()
    val result = mutableListOf<NovaComponentDoc>()
    for (groupElement in groups) {
      val group = groupElement.asJsonObject
      val groupTitle = group.string("title") ?: group.string("id") ?: "Nova"
      val components = group.getAsJsonArray("components") ?: continue
      for (componentElement in components) {
        val component = componentElement.asJsonObject
        val name = component.string("name") ?: continue
        val rawComponentDescription = component.localized("description") ?: "Компонент Nova $name."
        val props = component.getAsJsonArray("props")?.mapNotNull { propElement ->
          val prop = propElement.asJsonObject
          val propName = prop.string("name") ?: return@mapNotNull null
          val rawPropDescription = prop.localized("description") ?: "Prop $propName."
          NovaComponentPropDoc(
            name = propName,
            type = prop.string("type") ?: "unknown",
            required = prop.get("required")?.asBoolean == true,
            description = normalizePropDescription(propName, rawPropDescription),
          )
        } ?: emptyList()
        result += NovaComponentDoc(
          name = name,
          title = component.string("title") ?: name,
          description = normalizeComponentDescription(name, rawComponentDescription),
          source = component.string("source"),
          packageName = packageName,
          importSource = component.string("importSource"),
          importName = component.string("importName"),
          isGlobal = component.get("global")?.asBoolean ?: true,
          snippet = component.string("snippet") ?: "<$name />",
          props = props,
          groupTitle = groupTitle,
        )
      }
    }
    return result
  }

  private fun normalizeComponentDescription(name: String, description: String): String {
    if (!looksEnglish(description)) return description

    COMPONENT_DESCRIPTIONS[name]?.let { return it }

    if (description.contains("advanced Nova UI Kit DSL node", ignoreCase = true)) {
      return "Расширенный компонент Nova UI Kit. Поддерживает состояние, значение, overlay и анимацию."
    }
    if (description.contains("Vue host component", ignoreCase = true)) {
      return "Vue-компонент, создающий NovaApp и монтирующий Nova DSL в canvas."
    }
    if (description.contains("Internal marker", ignoreCase = true) || description.contains("Vue marker", ignoreCase = true)) {
      return "Служебный marker, который связывает скомпилированный Nova SFC с NovaCanvas."
    }
    if (description.contains("text primitive", ignoreCase = true)) {
      return "Текстовый primitive для schema-шаблонов TimelineChart."
    }

    return "Компонент Nova $name."
  }

  private fun normalizePropDescription(name: String, description: String): String {
    if (!looksEnglish(description)) return description
    PROP_DESCRIPTIONS[name]?.let { return it }
    return "Prop `${name}`."
  }

  private fun looksEnglish(value: String): Boolean {
    val latinWords = Regex("""[A-Za-z]{4,}""").findAll(value).count()
    val cyrillicWords = Regex("""[А-Яа-яЁё]{4,}""").findAll(value).count()
    return latinWords > cyrillicWords
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

  private val COMPONENT_DESCRIPTIONS = mapOf(
    "nova-template" to "Служебный marker для монтирования скомпилированного Nova SFC в NovaCanvas.",
    "Root" to "Корневой контейнер Nova UI.",
    "Flex" to "Flex layout-контейнер для Nova UI.",
    "Grid" to "Grid layout-контейнер для Nova UI.",
    "TextBlock" to "Текстовый блок canvas UI.",
    "Text" to "Текстовый primitive для schema-шаблонов.",
    "Surface" to "Базовая поверхность с фоном, рамкой и состояниями.",
    "Button" to "Кнопка Nova UI Kit.",
    "Tag" to "Компактная метка или badge.",
    "Input" to "Поле ввода Nova UI Kit.",
    "SplitPane" to "Контейнер с разделяемыми панелями.",
    "ScrollArea" to "Прокручиваемая область Nova UI Kit.",
    "Scrollbar" to "Полоса прокрутки Nova UI Kit.",
    "Slider" to "Слайдер для выбора числового значения.",
    "Checkbox" to "Checkbox с бинарным состоянием.",
    "Toggle" to "Переключатель бинарного режима.",
    "Tooltip" to "Всплывающая подсказка.",
    "SegmentedControl" to "Сегментированный переключатель вариантов.",
    "Panel" to "Панель-контейнер Nova UI Kit.",
    "ProgressRing" to "Кольцевой индикатор прогресса.",
    "Knob" to "Регулятор значения в виде ручки.",
    "Icon" to "Иконка в Nova schema или UI.",
    "Rect" to "Прямоугольник schema-шаблона.",
    "Line" to "Линия schema-шаблона.",
    "Circle" to "Окружность schema-шаблона.",
    "NovaCanvas" to "Vue host-компонент, создающий NovaApp и canvas.",
    "TimelineChart.Root" to "Корневой runtime-компонент TimelineChart.",
    "TimelineChart.GroupPanel" to "Декларация панели групп TimelineChart.",
    "TimelineChart.GroupColumn" to "Декларация шаблона колонки групп.",
    "TimelineTaskProfile" to "Шаблон отрисовки задачи TimelineChart.",
  )

  private val PROP_DESCRIPTIONS = mapOf(
    "id" to "Стабильный идентификатор компонента.",
    "key" to "Ключ для reconcile списка.",
    "ref" to "Имя template ref.",
    "ref-key" to "Ключ элемента в refMap.",
    "refKey" to "Ключ элемента в refMap.",
    "context" to "Nova context для поддерева.",
    "layout" to "Layout-настройки внутри родителя.",
    "if" to "Условие отрисовки Nova DSL.",
    "else-if" to "Дополнительная ветка условия.",
    "else" to "Fallback-ветка условия.",
    "for" to "Повтор элементов, например `item in items`.",
    "class" to "NovaCSS class компонента.",
    "className" to "Runtime class name компонента.",
    "attrs" to "Raw attributes для style engine.",
    "src" to "Путь к внешнему Nova asset.",
    "source" to "Debug source path или путь к source-файлу.",
    "debug-id" to "Debug-идентификатор.",
    "component" to "Скомпилированный Nova component constructor.",
    "data" to "Данные, передаваемые в template.",
    "options" to "Опции, передаваемые в template.",
    "width" to "Ширина объекта.",
    "height" to "Высота объекта.",
    "x" to "Позиция по оси X.",
    "y" to "Позиция по оси Y.",
    "x1" to "Начальная X-координата линии.",
    "y1" to "Начальная Y-координата линии.",
    "x2" to "Конечная X-координата линии.",
    "y2" to "Конечная Y-координата линии.",
    "size" to "Размер объекта.",
    "value" to "Текущее значение.",
    "text" to "Текст для отображения.",
    "title" to "Заголовок.",
    "label" to "Подпись.",
    "background" to "Фон объекта.",
    "color" to "Основной цвет.",
    "font" to "Настройки шрифта.",
    "lineHeight" to "Высота строки.",
    "padding" to "Внутренний отступ.",
    "border" to "Рамка объекта.",
    "radius" to "Радиус скругления.",
    "align" to "Выравнивание содержимого.",
    "ellipsis" to "Обрезка текста с многоточием.",
    "opacity" to "Прозрачность объекта.",
    "clip" to "Обрезать содержимое по bounds.",
    "active" to "Активность компонента.",
    "meta" to "Произвольные metadata.",
    "mount" to "Статический путь к `.nova` entrypoint.",
    "props" to "Props, передаваемые в mounted `.nova` entrypoint.",
    "maxDpr" to "Максимальный device pixel ratio canvas.",
    "surfaceName" to "Имя root surface.",
    "plugins" to "Nova plugins, регистрируемые перед mount.",
    "appOptions" to "Дополнительные опции NovaApp.",
    "rootId" to "ID неявного Root wrapper.",
    "rootClassName" to "Class name неявного Root wrapper.",
    "styleSheet" to "NovaCSS stylesheet или asset.",
    "devtools" to "Настройки регистрации в Nova DevTools.",
    "contract" to "Контракт render planner для batching.",
  )
}
