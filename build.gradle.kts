plugins {
  kotlin("jvm") version "2.0.21"
  id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "dev.engine2d"
version = "0.5.13"

kotlin {
  jvmToolchain(21)
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity("2024.3")
    bundledPlugin("com.intellij.java")
  }
}

intellijPlatform {
  pluginConfiguration {
    name = "Nova DSL"
    version = project.version.toString()

    description = """
      <p>
        JetBrains IDE support for Engine2D/Nova <code>.nova</code> DSL files.
      </p>
      <p>
        Registers a dedicated Nova file type with branded icons and syntax highlighting for Nova SFC sections,
        DSL tags, attributes, strings, comments and NovaCSS theme declarations.
      </p>
      <p>
        Includes New File actions, component and prop completion from Nova manifests, a Nova Components tool window,
        folding, and go-to-declaration for local Nova files, asset paths and manifest component sources.
      </p>
    """.trimIndent()

    changeNotes = """
      <ul>
        <li>Added Nova DSL and NovaCSS file types for <code>.nova</code> and <code>.novacss</code>.</li>
        <li>Added New menu actions for Nova DSL File and NovaCSS File.</li>
        <li>Added automatic override for existing <code>.nova</code> and <code>.novacss</code> file associations.</li>
        <li>Updated Nova DSL and NovaCSS file icons to use the Nova heart logo.</li>
        <li>Added Ctrl+B / Go To Declaration for <code>.nova</code> imports, <code>src</code> includes and built-in Nova DSL components.</li>
        <li>Added Nova component registry panel, manifest-backed completions and Nova folding support.</li>
        <li>Cached component manifests and replaced recursive project scanning with bounded package lookup to avoid UI freezes.</li>
        <li>Changed Nova Components panel to an expandable component/prop tree with snippet insertion and drag-and-drop into editors.</li>
        <li>Rendered component details as compact formatted HTML and normalized manifest fallback descriptions to Russian.</li>
        <li>Fixed startup file association threading and removed unsupported plugin descriptor URL element.</li>
        <li>Removed deprecated project root API usages reported by JetBrains Plugin Verifier.</li>
        <li>Improved Nova syntax highlighting for script blocks and bound attribute expressions.</li>
        <li>Added Reformat Code support for wrapping long Nova DSL tags across multiple lines.</li>
        <li>Added CSS-like NovaCSS highlighting and fixed Nova tag folding through a parser definition.</li>
        <li>Improved Nova folding placeholders and added Nova/NovaCSS comment actions.</li>
        <li>Added NovaCSS block folding with selector placeholders and self-managed line comments.</li>
        <li>Enabled Reformat Code for Nova DSL and NovaCSS files, including CSS-like NovaCSS block formatting.</li>
        <li>Highlighted short Nova directive values such as <code>for</code>, <code>if</code>, <code>show</code> and <code>model</code> as TypeScript-like expressions.</li>
        <li>Added Nova expression references for local declarations and imported symbols, so Find Usages and navigation can see DSL usages.</li>
      </ul>
    """.trimIndent()

    vendor {
      name = "Engine2D"
      email = "team@engine2d.dev"
      url = "https://engine2d.dev"
    }

    ideaVersion {
      sinceBuild = "243"
    }
  }

  signing {
    certificateChain = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
    password = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    channels = providers.environmentVariable("JETBRAINS_MARKETPLACE_CHANNEL").map { listOf(it) }
  }
}
