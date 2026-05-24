# Nova DSL

JetBrains IDE support for Engine2D/Nova `.nova` and `.novacss` files.

## Features

- Dedicated file types for `.nova` and `.novacss`.
- Nova heart file icons and New File actions.
- Syntax highlighting for Nova SFC blocks, DSL tags, attributes, strings, comments and NovaCSS theme declarations.
- Code folding for SFC blocks, comments and nested Nova DSL tags.
- Code completion for Nova component tags and props.
- `Go to Declaration` for local `.nova` files, `<template src>`, `<Component src>`, asset paths and manifest component sources.
- `Nova Components` tool window with searchable components, compact prop documentation, snippets and drag-and-drop insertion.
- Automatic file association for `.nova` and `.novacss` after plugin installation.

## Installation

Install the plugin from JetBrains Marketplace after publication, or install a local plugin ZIP:

```text
Settings / Preferences
-> Plugins
-> Gear icon
-> Install Plugin from Disk...
```

Select:

```text
build/distributions/jetbrains-nova-plugin-0.5.3.zip
```

Restart the IDE after installation.

## Usage

Create files from the project tree:

```text
New -> Nova DSL File
New -> NovaCSS File
```

Open the `Nova Components` tool window to browse available components and props. You can select a component or selected props and either:

- click `Insert snippet`;
- drag the selection into an editor.

Example supported navigation:

```vue
<script setup lang="ts">
import AirportTimeline from './ui/AirportTimeline.nova'
</script>

<template>
  <AirportTimeline />
  <template src="./groups/GroupPanel.nova" />
  <Component src="./panels/Inspector.nova" />
  <Icon src="../assets/icons/crane.svg" />
</template>
```

## Component Manifests

The plugin reads bundled Nova manifests and project manifests declared in `package.json`:

```json
{
  "nova": {
    "components": "./nova-components.json"
  }
}
```

Manifest data powers component completion, prop completion, the `Nova Components` tool window and source navigation.

## Build From Source

Requirements:

- Java 21
- Gradle

Build:

```bash
gradle buildPlugin
```

Verify plugin configuration:

```bash
gradle verifyPluginConfiguration
```

The plugin ZIP is generated in:

```text
build/distributions/
```

## License

Apache License 2.0.
