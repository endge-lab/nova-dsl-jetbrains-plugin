# Nova DSL JetBrains Plugin

MVP-плагин для JetBrains IDE, который регистрирует `.nova` и `.novacss` как отдельные file types. Это убирает Vue false-positive warnings для Nova-only props и добавляет базовую подсветку:

- SFC-теги `<script>`, `<template>`, `<style>`;
- Nova/UI Kit/Timeline DSL tags;
- attributes, bound attributes, events и slots;
- strings, braces, comments;
- ключевые слова TypeScript/NovaCSS, включая `@theme`.
- брендированную plugin logo и file icon на основе `public/nova-logo.png`.
- пункты `New -> Nova DSL File` и `New -> NovaCSS File`.
- автоматический override расширений `*.nova` и `*.novacss` на file types плагина после установки.
- `Ctrl+B` / `Go To Declaration` для локальных `.nova` импортов, `<template src>`, `<Component src>`, asset paths и компонентов из Nova manifests.
- панель `Nova Components` справа: список компонентов, краткое описание, props и вставка snippet.
- completion для DSL tags и props из `nova-components.json`.
- folding для SFC-блоков, вложенных DSL-тегов и комментариев.

## Установка из исходников

1. Откройте терминал в корне репозитория плагина.
2. Соберите plugin zip:

```bash
gradle buildPlugin
```

Если Gradle не установлен глобально, откройте папку плагина как Gradle project в IntelliJ IDEA/WebStorm и запустите task `Tasks > intellij platform > buildPlugin`.

Проект настроен на Java 21 через локальный Homebrew path:

```properties
org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Это важно для macOS, где Homebrew может держать глобальным Java более новую версию, несовместимую с Kotlin compiler.

3. В IDE откройте:

```txt
Settings / Preferences
→ Plugins
→ ⚙
→ Install Plugin from Disk...
```

4. Выберите zip из:

```txt
build/distributions/
```

5. Перезапустите IDE.

## Проверка

После установки файл `*.nova` должен открываться как `Nova DSL`, а не как Vue SFC или plain text:

```txt
Settings / Preferences
→ Editor
→ File Types
→ Nova DSL
```

Если раньше `*.nova` был вручную привязан к Vue:

```txt
Settings / Preferences
→ Editor
→ File Types
→ Vue.js Single File Component
→ Remove: *.nova
```

Затем проверьте, что `*.nova` есть в `Nova DSL`.

Плагин также делает это автоматически при старте проекта: если `*.nova` или `*.novacss` были привязаны к Vue, HTML, CSS или plain text, association будет перенесен на `Nova DSL` / `NovaCSS`.

Для NovaCSS проверьте:

```txt
Settings / Preferences
→ Editor
→ File Types
→ NovaCSS
```

Там должен быть `*.novacss`.

Создание файлов доступно из project tree:

```txt
Right click directory
→ New
→ Nova DSL File
```

и:

```txt
Right click directory
→ New
→ NovaCSS File
```

## Навигация

Плагин добавляет легкий `Go To Declaration` без запуска Nova compiler language service.

Работают переходы:

```vue
<script setup lang="ts">
import AirportTimeline from './ui/AirportTimeline.nova'
</script>

<template>
  <AirportTimeline />
  <template src="./groups/GroupPanel.nova" />
  <Component src="./panels/Inspector.nova" />
</template>
```

`Ctrl+B` на `AirportTimeline`, строке `src="./..."` или импортной строке открывает соответствующий `.nova` файл.
Для assets также работают строки `src`, `source`, `icon`, `background`, `fill-pattern`:

```vue
<Icon src="../assets/icons/crane.svg" />
<Rect background="../assets/patterns/weekend.png" />
```

Для встроенных компонентов переход открывает исходники в workspace:

```vue
<Root>
  <Flex>
    <TextBlock text="Nova" />
  </Flex>

  <TimelineChart.Root>
    <TimelineChart.GroupPanel />
    <TimelineChart.GroupColumn id="readiness" />
  </TimelineChart.Root>
</Root>
```

Компоненты берутся из bundled/project manifests:

```json
{
  "nova": {
    "components": "./nova-components.json"
  }
}
```

Панель `Nova Components` открывается справа. В ней можно искать компонент, смотреть русское описание и props, вставлять базовый snippet и открывать source, если он указан в manifest.

Сейчас это файловая навигация по manifest/source paths. Точный переход на symbol-level declaration внутри TypeScript будет частью следующего language-service слоя.

## Marketplace

Первую публикацию нового плагина JetBrains требует делать вручную через Marketplace UI:

```txt
JetBrains Marketplace
→ Profile
→ Add new plugin
→ upload build/distributions/jetbrains-nova-plugin-0.5.2.zip
```

После первой публикации можно использовать Gradle:

```bash
export JETBRAINS_MARKETPLACE_TOKEN="perm:..."
export JETBRAINS_MARKETPLACE_CHANNEL="default"

gradle publishPlugin
```

Для signing используются environment variables. Секреты нельзя коммитить в репозиторий:

```bash
export JETBRAINS_CERTIFICATE_CHAIN="$(cat certificate/chain.crt)"
export JETBRAINS_PRIVATE_KEY="$(cat certificate/private.pem)"
export JETBRAINS_PRIVATE_KEY_PASSWORD="..."

gradle signPlugin
```

Если signing variables не заданы, локальная сборка `gradle buildPlugin` продолжает работать и выпускает unsigned zip для ручной установки.

## Текущие ограничения

Это первый слой IDE support. Он не запускает `@endge/nova-compiler` language service и не делает полноценный TypeScript/Vue PSI внутри `<script setup>`. Следующий шаг: добавить Node worker поверх `@endge/nova-compiler`, чтобы IDE показывала настоящие Nova diagnostics, completion и symbol-level go-to-definition.
