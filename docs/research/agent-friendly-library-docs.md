# Making TaskPulse usable by coding agents without decompiling

Research date: 2026-10-19. Question: which proven ways exist to get a Java library's documentation to an AI coding agent (Claude Code, Copilot, Cursor, Codex, Junie…), inside the jar or some other way, so the agent does not have to decompile classes? For each option: what it is, maturity, an example, effort for TaskPulse, recommendation.

**How this was sourced.** Claims link to the owner's page: spec, docs, repository or first-party announcement. From the research sandbox, many hosts (vaadin.com, quarkus.io, nextjs.org, tanstack.com, context7.com, llmstxt.org, agents.md, agentskills.io, central.sonatype.org, netflixtechblog.com, jetbrains.com) could not be fetched directly. For those, the facts come from the same document's source on GitHub where one exists (marked *via GitHub source*). Otherwise they come from the search engine's index of that official URL (marked *via search index*), so the link is primary but the page was not read in full.

## Where TaskPulse stands today

- The `release` profile in the root `pom.xml` already attaches `-sources.jar` and `-javadoc.jar` (maven-source-plugin `jar-no-fork`, maven-javadoc-plugin `jar`), signs them and publishes through `central-publishing-maven-plugin`. The `directory` profile in `addon/pom.xml` puts both in the Vaadin Directory zip.
- The add-on is 12 source files with about 96 Javadoc blocks. It has no `package-info.java` and no `module-info.java`, and its manifest has no `Automatic-Module-Name`.
- The jar holds only classes and `META-INF/resources/taskpulse/taskpulse.css`. It ships no Markdown for agents. The 283-line `README.md` exists only on GitHub.

---

## 1. Sources and Javadoc jars on Maven Central

**What it is.** Maven Central requires every non-`pom` artifact to come with `<artifactId>-<version>-sources.jar` and `-javadoc.jar`, "so consumers can browse and navigate" them. Placeholder jars are accepted ([Central requirements](https://central.sonatype.org/publish/requirements/), via search index; [closed-source FAQ](https://central.sonatype.org/faq/closed-source/), via search index). The `central-publishing-maven-plugin` does not build them, so the project has to ([Publishing with the Maven plugin](https://central.sonatype.org/publish/publish-portal-maven/), via search index).

**How agents and tools actually consume them.**
- **IDEs.** IntelliJ and VS Code attach sources for navigation. IntelliJ's built-in MCP server (since 2025.2) exposes IDE tools to external agents such as Claude Code, including `get_symbol_info`, which returns "the same info as Quick Documentation" plus the declaration snippet ([IntelliJ IDEA help: MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html), via search index; [What's new in 2025.2](https://www.jetbrains.com/idea/whatsnew/2025-2/), via search index). The documentation doesn't say whether it resolves library symbols that have attached sources, so this is not confirmed.
- **Claude Code, Copilot, Cursor.** I found no first-party documentation saying any of them reads `-sources.jar` by itself. In practice the agent sees `~/.m2` through its shell. This is a negative finding: nothing documents it.
- **MCP servers that resolve Maven sources and Javadoc** (all third-party, small, young):
  - [javadocs.dev](https://github.com/jamesward/javadoccentral) renders the `-javadoc.jar` of any Maven Central artifact and serves an MCP endpoint at `https://www.javadocs.dev/mcp` (Streamable HTTP).
  - [jarscope](https://github.com/t-lorenz/jarscope) searched sources jars first and fell back to javadoc jars, from the Gradle cache, then `~/.m2`, then Central. It is now **archived and unmaintained**, which shows how short-lived this category is.
  - Others found by search but not vetted: [maven-indexer-mcp](https://github.com/tangcent/maven-indexer-mcp), [maven-mcp](https://github.com/tyutyutyu/maven-mcp) (returns source only if the sources artifact is in the local repo), and [maven-decoder-mcp](https://github.com/salitaba/maven-decoder-mcp) (falls back to `javap` when there is no sources jar).
- **Netflix `ja` and `jist`** read attached sources from the compilation context. See section 6.

**Maturity.** The publishing requirement is universal and long-established. *Consumption* by agents is indirect: an IDE MCP bridge, a third-party MCP server, or a CLI such as Netflix's `jist`.

**Effort for TaskPulse.** Already done. The remaining work is to make the Javadoc worth reading:
- add `package-info.java` with an overview and a minimal setup example (`{@snippet}`);
- document every public `Pulse*` type, especially the entry points `PulseTaskList.getCurrent()`, `start(action, work)` and `PulseTab`.

**Recommendation.** Keep it. **Proven practice**, and the base every other tool reads from. Improve Javadoc content rather than add new channels first.

---

## 2. llms.txt

**What it is.** A Markdown file at `/llms.txt` (site root or any subpath) gives agents a curated overview. Its format: an H1 name, a blockquote summary, then H2 sections of `[name](url): notes` links, with an `Optional` section an agent may skip. Pages may offer clean Markdown at `page.md` / `page.html.md`. Version 2 (front matter modified 2026-08-10) adds discovery through `rel="describedby"` / `rel="alternate" type="text/markdown"`. Proposed by Jeremy Howard, 2024-09-03 ([AnswerDotAI/llms-txt](https://github.com/AnswerDotAI/llms-txt), the source of [llmstxt.org](https://llmstxt.org/)).

**Adoption.**
- Broad among docs platforms: Mintlify, GitBook and Docusaurus plugins generate it ([llms-txt repo](https://github.com/AnswerDotAI/llms-txt)).
- JVM example: Kotlin publishes [kotlinlang.org/llms.txt](https://kotlinlang.org/llms.txt), which links to per-page `.txt` versions, including its library-author guidelines.
- Consumers: GitMCP reads `llms.txt` first, then AI-optimized docs, then the README ([idosal/git-mcp](https://github.com/idosal/git-mcp)). Context7's API accepts llms.txt / llms-full.txt URLs ([Context7 docs: add an llms.txt file](https://context7.com/docs/api-reference/add-library/add-an-llmstxt-file.md), via search index).
- I could not confirm that Vaadin or Spring publish one.

**Effort for TaskPulse.** Low. TaskPulse has no docs site, but an `llms.txt` at the repository root still works for GitMCP and is a small index into the README, the glossary and the Javadoc on javadocs.dev or javadoc.io.

**Recommendation.** **Emerging, cheap.** Worth adding once the README and Javadoc are in shape. It is an index, not a substitute for content.

---

## 3. Shipping agent instructions with the artifact

### 3a. Agent Skills (SKILL.md)

**What it is.** A folder with a `SKILL.md`, whose frontmatter needs at least `name` and `description`, plus optional `scripts/`, `references/` and `assets/`. Agents load only the description at startup and the body on demand. Anthropic developed it and released it as an open standard maintained at [agentskills/agentskills](https://github.com/agentskills/agentskills) (spec at [agentskills.io/specification](https://agentskills.io/specification)).
- **Claude Code** loads skills from `.claude/skills/<name>/SKILL.md` or from a plugin's `skills/` ([Claude Code: skills](https://code.claude.com/docs/en/skills), via search index).
- **The `skills` CLI** (`npx skills add owner/repo`) installs from any git repo's root `SKILL.md` or `skills/` folder into about 79 agents ([vercel-labs/skills](https://github.com/vercel-labs/skills)).

**Evidence on effectiveness.** Vercel's Next.js evals found that skills were never invoked in 56% of cases. A compressed 8 KB docs index in AGENTS.md reached 100% pass, against 79% for skills with explicit instructions ([Vercel blog](https://vercel.com/blog/agents-md-outperforms-skills-in-our-agent-evals), via search index; the same figures are repeated in the [Next.js AI agents guide source](https://github.com/vercel/next.js/blob/canary/docs/01-app/02-guides/ai-agents.mdx)). The lesson: a skill needs a sharp `description`, or a pointer from AGENTS.md / CLAUDE.md.

### 3b. Skills *inside jars*: the Java precedents

- **Quarkus (first-party, production).** An extension puts `META-INF/quarkus-skill.md` in its deployment module. An `aggregate-skills` goal composes it with the extension metadata into `META-INF/skills/{extension-name}/SKILL.md`, following the Agent Skills spec, and publishes an aggregated `io.quarkus:quarkus-extension-skills` jar to Maven Central with each release. The Quarkus Agent MCP server discovers per-extension skills from deployment jars in the local Maven repo. This "supports skills from Quarkus core, Quarkiverse, and custom extensions." Extensions can also ship RAG doc embeddings in the jar or as a separate artifact ([Quarkus agent-mcp guide source](https://raw.githubusercontent.com/quarkusio/quarkus/main/docs/src/main/asciidoc/agent-mcp.adoc); [quarkusio/quarkus-agent-mcp](https://github.com/quarkusio/quarkus-agent-mcp); [launch post](https://quarkus.io/blog/introducing-agent-mcp/), via search index). This is the strongest JVM precedent, but it only works inside Quarkus tooling.
- **SkillsJars (community convention).** Skills are packaged at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md` and published under groupId `com.skillsjars` ([SPEC.md](https://raw.githubusercontent.com/skillsjars/skillsjars/main/SPEC.md)). The `com.skillsjars:maven-plugin:0.0.7` has a `package` goal (any project can put its skills at that path) and an `extract` goal (`mvn skillsjars:extract -Ddir=.claude/skills`) ([skillsjars-maven-plugin](https://github.com/skillsjars/skillsjars-maven-plugin)). Gradle and sbt plugins exist ([github.com/skillsjars](https://github.com/skillsjars)). A JetBrains plugin scans dependency jars for `META-INF/skills/**/SKILL.md` and `META-INF/resources/skills/**/SKILL.md` and exports them to `.claude/skills`, `.cursor/skills`, `.junie/skills` and others ([dong4j/skillsjars-helper](https://github.com/dong4j/skillsjars-helper)). Adoption is small: tens of GitHub stars.
- **Spring AI** implements Agent Skills for agents *built with* Spring AI ([spring.io blog](https://spring.io/blog/2026/01/28/apring-ai-anthropic-agentic-skills/), via search index). This is for building agents, not for coding agents reading a library.

**Key fact.** No coding agent today reads `META-INF/skills` from the classpath by itself. Something has to extract it: the Quarkus MCP, the SkillsJars plugin, or the IntelliJ helper.

### 3c. AGENTS.md / CLAUDE.md

**What it is.** A Markdown "README for agents" at the project root ([agentsmd/agents.md](https://github.com/agentsmd/agents.md)). OpenAI contributed it to the Linux Foundation's Agentic AI Foundation on 2025-12-09 and claims adoption by more than 60,000 projects ([OpenAI announcement](https://openai.com/blog/agentic-ai-foundation), via search index). Claude Code, Codex, Cursor and Copilot read it at session start ([Next.js guide source](https://github.com/vercel/next.js/blob/canary/docs/01-app/02-guides/ai-agents.mdx)).

### 3d. npm precedents

- **Next.js** (≥16.2) bundles version-matched docs in `node_modules/next/dist/docs/`. From 16.3 it maintains a marked block in the app's `AGENTS.md` telling agents to read those docs first, because "APIs … may all differ from your training data" ([Next.js AI agents guide source](https://github.com/vercel/next.js/blob/canary/docs/01-app/02-guides/ai-agents.mdx)).
- **TanStack Intent** is "a CLI for library maintainers to generate, validate, and ship Agent Skills alongside their npm packages" ([TanStack/intent](https://github.com/TanStack/intent)). Skills sit at `node_modules/<pkg>/skills/<name>/SKILL.md` ([TanStack AI: agent skills](https://tanstack.com/ai/latest/docs/getting-started/agent-skills.md), via search index). `intent install` writes skill-loading guidance into AGENTS.md, and `intent stale` flags version drift ([Intent overview source](https://raw.githubusercontent.com/TanStack/intent/main/docs/overview.md)). It is labelled alpha ([tanstack.com/intent](https://tanstack.com/intent/latest), via search index).
- **The Java gap.** npm puts package files on disk under the project, where agents grep. Maven puts jars in `~/.m2` and never unpacks them, hence the extract step in Quarkus and SkillsJars.

**Effort for TaskPulse.**
1. Write `skills/taskpulse/SKILL.md` in the repo: setup, `PulseTaskList.start(action, work)`, Blocking and Retry, the `Pulse` prefix, and common mistakes from ADR 0002 and 0003. Low effort, and immediately installable with `npx skills add jcgueriaud1/vaadin-task-manager`.
2. Also copy it into the jar at `META-INF/skills/jcgueriaud1/vaadin-task-manager/taskpulse/SKILL.md`, the SkillsJars layout, which the IntelliJ helper scans. This takes one `maven-resources-plugin` copy, or the SkillsJars `package` goal. It adds a few KB and costs nothing at runtime.
3. Add a README section "Using TaskPulse with a coding agent" with a 5-line AGENTS.md / CLAUDE.md snippet for consumers. That is the passive context Vercel's evals favour.
4. Optionally, a Claude Code plugin marketplace (`.claude-plugin/marketplace.json`) in the repo ([Claude Code: plugin marketplaces](https://code.claude.com/docs/en/plugin-marketplaces), via search index), as Vaadin does (section 5).

**Recommendation.**
- In-repo `SKILL.md` and README snippet: **adopt, low cost, proven pattern** (Vaadin, Quarkus, TanStack all do it).
- `SKILL.md` inside the jar: **emerging/speculative**. Cheap to add, but only niche tools read it today.

---

## 4. MCP documentation servers

| Server | How a library gets in | Notes / evidence |
|---|---|---|
| **Context7** (Upstash) | Anyone can add a public GitHub repo through the web UI. It indexes `.md/.mdx/.rst/.txt/.ipynb`, and generates examples from source when docs are thin. Refreshes follow popularity, or a GitHub Action on push ([adding-libraries](https://raw.githubusercontent.com/upstash/context7/master/docs/adding-libraries.mdx)). | `context7.json` at the repo root: `projectTitle`, `description`, `branch`, `folders`, `excludeFolders`, `excludeFiles`, `rules` (shown to agents), `previousVersions`. Owners can claim the library for an admin panel ([library-owners](https://raw.githubusercontent.com/upstash/context7/master/docs/library-owners.mdx)). Tools: `resolve-library-id`, `query-docs` ([upstash/context7](https://github.com/upstash/context7)). The crawler and backend are closed source. |
| **GitMCP** | Zero setup: `gitmcp.io/{owner}/{repo}`. Reads `llms.txt`, then AI docs, then the README ([idosal/git-mcp](https://github.com/idosal/git-mcp)). | Free, Apache-2.0, about 8.5k stars. |
| **DeepWiki** (Cognition) | AI-generated wiki for public GitHub repos. MCP at `https://mcp.deepwiki.com/mcp` with `read_wiki_structure`, `read_wiki_contents`, `ask_question` ([Devin docs](https://docs.devin.ai/work-with-devin/deepwiki-mcp.md), via search index). | Answers lag the last index. |
| **Vaadin MCP** (first-party) | Vaadin's own docs only. `https://mcp.vaadin.com/docs`, streamable HTTP: semantic search, full documents, component Java/React/Web Component APIs, styling, version info, a development primer. Can be self-hosted ([Vaadin docs: MCP](https://vaadin.com/docs/latest/building-apps/mcp), via search index; [announcement, 2025-10-31](https://blog.vaadin.com/official-vaadin-mcp), via search index). | No evidence found that it covers Directory add-ons. |
| **Quarkus Agent MCP** (first-party) | Quarkus docs plus per-extension skills and embeddings found in jars (section 3b). `quarkus_searchDocs` uses BGE embeddings and pgvector ([quarkus-agent-mcp](https://github.com/quarkusio/quarkus-agent-mcp)). | The only first-party MCP that pulls *third-party library* docs out of jars. |
| **Spring Tools 5** | An embedded, experimental MCP server inside the IDE ([spring.io blog](https://spring.io/blog/2025/12/04/towards-spring-tools-5-part3/), via search index). | IDE-bound. |
| **IntelliJ MCP server** | Built into the IDE since 2025.2. Exposes IDE tools, including `get_symbol_info` (section 1). | Requires the IDE to be running. |

**Effort for TaskPulse.**
- Context7: submit the repo, plus an optional `context7.json` that limits indexing to `README.md`, `GLOSSARY.md`, `docs/adr` and `skills/`, with `rules` for the two or three must-know rules. Under an hour.
- GitMCP and DeepWiki: nothing to do, they work on public repos already. Better README and `llms.txt` content improves both.

**Recommendation.**
- Register with Context7: **widely used, low effort**. Its quality depends on the Markdown you give it.
- Running your own MCP server for a 12-class add-on: **not worth it**.

---

## 5. Official guidance from JVM projects

- **Vaadin.**
  - Hosted docs MCP (above).
  - Skills in [vaadin/agent-skills](https://github.com/vaadin/agent-skills) (`aura-theme`, `vaadin-frontend-design`, `vaadin-form-layout`; installable with `npx skills add`).
  - Plugin marketplace [vaadin/agent-marketplace](https://github.com/vaadin/agent-marketplace): `/plugin marketplace add vaadin/agent-marketplace`, then `/plugin install vaadin-skills@vaadin-marketplace`, which also configures the MCP server.
  - The skills moved out of `vaadin/claude-plugin` so that agents other than Claude can use them ([forum announcement](https://vaadin.com/forum/t/attention-users-of-the-vaadin-claude-plugin/179553), via search index).
  - I found no Vaadin guidance aimed at *add-on authors*. TaskPulse's consumers likely already run the Vaadin MCP, so a TaskPulse skill complements it rather than competes.
- **Quarkus.** The most concrete library-author story: skill files in extension jars, plus doc embeddings (section 3b).
- **Spring.** Spring AI implements Agent Skills and MCP for building agents. Spring Tools 5 adds an IDE MCP server. I found no guidance for Spring library authors on agent docs.
- **Kotlin** publishes `llms.txt`, and its library-author guidelines include "informative documentation" ([kotlinlang.org/llms.txt](https://kotlinlang.org/llms.txt)).
- **Micronaut.** Nothing found.

---

## 6. Netflix: "Leave the Class Path in the Rearview Mirror" (Sept 2026)

**What it proposes.** A post by Danny Thomas (Netflix JVM Ecosystem team), dated 2026-09-18, previewing **ja**: composable command-line tools built on the Java Module System. The premise is that IDE capabilities (finding dependencies, docs and sources) are stuck in a GUI, so coding agents fall back on manual searches of dependency caches and source archives, and the tools should be exposed in the terminal with machine-readable output ([article](https://netflixtechblog.com/leave-the-class-path-in-the-rearview-mirror-67a85b15b6be); the article itself could not be fetched from the sandbox, so the summary comes from its search-index entry and the repositories below). The `ja` README states the motivation directly: "Coding agents encounter these gaps constantly" ([Netflix/ja](https://github.com/Netflix/ja)).

**The tools (primary repos, all "preview", JDK 25+, published on Maven Central):**
- **jist:** "source aware symbol search for Java classes", grep-style and exact-name, showing signatures, documentation and full source, and finding semantic usages. It gives agents symbols and sources "without indexing, LSPs or MCPs" ([Netflix/jist](https://github.com/Netflix/jist); [Netflix/ja](https://github.com/Netflix/ja)). Its Gradle integration puts "dependency source archives that Gradle can resolve" on the source path, and "missing source variants are tolerated" ([jist wiki: Project Integration](https://github.com/Netflix/jist/wiki/Project-Integration)).
- **jdocserver:** a local Javadoc server for a modular compilation context, using the standard HTML doclet ([Netflix/jdocserver](https://github.com/Netflix/jdocserver)).
- **ja doc / ja source:** look up APIs of dependencies from the terminal. Without attached source, `ja source` "warns and shows the compiled declaration instead" ([ja wiki: Development](https://github.com/Netflix/ja/wiki/Development)).
- **jig:** resolves Maven artifacts to module names. "A name declared by `module-info.class` or `Automatic-Module-Name` is used unchanged"; otherwise it falls back to OSGi or coordinate-derived names. It asks library authors to make their module name locatable ([jig wiki: Maven Modules](https://github.com/Netflix/jig/wiki/Maven-Modules)).

**Implications for TaskPulse.**
- Netflix's approach is consumer-side tooling, not a new artifact format. What it reads is exactly what section 1 covers: **the sources jar and the Javadoc in it**. Publishing sources is what keeps these tools from falling back to compiled declarations.
- A cheap, standard step makes TaskPulse a better citizen for module-aware tools: declare a stable module name (`Automatic-Module-Name: org.vaadin.addons.taskpulse` in the jar manifest, or a real `module-info.java`).
- Maturity: **preview** from a credible JVM team, about a month old. Adoption outside Netflix is unknown.

---

## Ranked recommendation for TaskPulse

**Proven practice (do now)**
1. **Keep publishing sources and Javadoc jars** (already in the `release` profile), and **invest in the Javadoc**: a `package-info.java` overview with a setup `{@snippet}`, and Javadoc on every public `Pulse*` type. Every tool above (IDE MCP, javadocs.dev, jist, ja doc, Context7's source fallback) reads this. *(§1, §6)*
2. **Write the agent-facing Markdown in the repo**: a tight README "Quick start for agents" section, plus `skills/taskpulse/SKILL.md` with a sharp `description` covering setup, starting Tasks, Blocking and Retry, the `Pulse` prefix, and pitfalls. Installable today with `npx skills add jcgueriaud1/vaadin-task-manager`. *(§3a; Vaadin, Quarkus and TanStack all ship skills)*
3. **Give consumers a 3–5 line AGENTS.md / CLAUDE.md snippet** in the README that points at the skill or docs. Passive context beat on-demand skills in Vercel's evals. *(§3c, §3d)*
4. **Register the repo with Context7**, with a `context7.json` (`folders`, `rules`). GitMCP and DeepWiki then work with no setup. *(§4)*

**Cheap and emerging (do when convenient)**
5. Add `Automatic-Module-Name: org.vaadin.addons.taskpulse` to the jar manifest, for module-aware agent tooling such as Netflix ja/jig. *(§6)*
6. Add a root `llms.txt` indexing the README, glossary, ADRs, skill and hosted Javadoc. *(§2)*
7. Copy the same `SKILL.md` into the jar at `META-INF/skills/jcgueriaud1/vaadin-task-manager/taskpulse/SKILL.md`, the SkillsJars layout. Today only extraction tools read it (SkillsJars plugin, IntelliJ helper). Quarkus shows the idea works when an ecosystem tool reads it. *(§3b)*

**Speculative or not worth it now**
8. A TaskPulse Claude Code plugin marketplace or a bespoke MCP server. Too much machinery for a 12-class add-on; the Vaadin MCP plus a skill covers it.
9. Relying on third-party Maven-sources MCP servers: useful to consumers, but young and churning (jarscope is already archived).
