package com.histmersion
package domain.automation.agents

import com.histmersion.api.Problem
import com.histmersion.server.agents.ClaudeCode

import com.histmersion.config.{AgentsConfig, AppConfig, UserPaths}
import com.histmersion.api.automation.Agents.GenerateResult
import io.circe.Json
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Stores
import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.util.storage.Location
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.config.Environment
import com.histmersion.util.system.JvmHome
import com.histmersion.util.storage.Store

/** Writes the generated per-runtime trees (docs/research-agent-runtimes.md §2.2) under the runtimes directory:
  * `<data dir>/agents/{claude-code,codex,opencode}/` and `manifest.json`.
  *
  * File by file, each one atomically (a temporary file renamed over it), and a file this generator wrote last time but
  * no longer produces is removed — never anything else: `codex/home` is also the CODEX_HOME of the runs, holding its
  * login and sessions, and `claude-code/login` an optional CLAUDE_CONFIG_DIR. Login state is only ever created by the
  * vendor's own login command there.
  */
final class AgentTrees(app: AppConfig, defs: AgentDefinitions, paths: UserPaths = AgentTrees.NoEnvironment)(using
    stores: Stores,
    clock: java.time.Clock
):

  private val cfg: AgentsConfig = app.agents

  /** The runtimes directory: `agents.runtimes.dir`, else `<data dir>/agents`. */
  def root: Option[Path] = AgentTrees.root(app, paths)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else the generated one. */
  def codexHome: Option[Path] = AgentTrees.codexHome(app, paths)

  def claudePlugin: Option[Path] = root.map(_.resolve("claude-code").resolve("headless"))
  def claudeRole(name: String): Option[Path] =
    root.map(_.resolve("claude-code").resolve("roles").resolve(name).resolve("settings.json"))
  def opencodeXdg: Option[Path] = root.map(_.resolve("opencode").resolve("xdg"))
  def opencodeConfig: Option[Path] = root.map(_.resolve("opencode").resolve("config"))

  private def publicUrl = app.server.publicUrl.value.stripSuffix("/")

  def settings(version: AgentTrees.SettingsVersion): GenSettings =
    val agentMcp =
      Option(cfg.providers.claudeCode.mcpUrl.value.trim).filter(_.nonEmpty).getOrElse(s"$publicUrl/mcp/agent")
    GenSettings(
      GenSettings.AgentMcpUrl(agentMcp),
      Some(GenSettings.McpUrl(s"$publicUrl/mcp")),
      GenSettings.QuotaUrl(s"$publicUrl/api/agents/quota/claude"),
      GenSettings.ClaudeMod(cfg.runtimes.claudeMod.value),
      GenSettings.InteractiveMod(cfg.runtimes.interactiveMod.value),
      version,
      GenSettings.CodexToolOutputTokens(cfg.providers.codex.toolOutputTokens.value),
      GenSettings.CodexLeanBase(cfg.providers.codex.leanBase)
    )

  /** The definitions, loaded and checked; `known` are the MCP tool names placeholders may use. */
  def load(known: Set[ToolName]): Either[Problem, AgentGenerator.Defs] =
    for
      as <- defs.agents.left.map(Problem(_))
      fs <- defs.fragments.left.map(Problem(_))
      ss <- defs.skills.left.map(Problem(_))
      d = AgentGenerator.Defs(as, fs, ss)
      _ <- AgentGenerator.check(d, known.map(t => t.value: String)) match
        case Nil  => Right(())
        case errs => Left(Problem(errs.mkString("; ")))
    yield d

  /** Generate the trees of `runtimes` (all three by default) into `out` (default [[root]]). */
  def generate(
      known: Set[ToolName],
      runtimes: List[AgentRuntime] = AgentRuntime.Generated,
      out: Option[Path] = None
  ): Either[Problem, GenerateResult] =
    for
      dir <- out
        .orElse(root)
        .toRight(Problem("no runtimes directory: set agents.runtimes.dir (HISTMERSION_AGENTS_RUNTIMES_DIR)"))
      d <- load(known)
      sha = AgentTrees.sha(d)
      tree = AgentGenerator.tree(
        d,
        settings(AgentTrees.SettingsVersion(s"${AgentGenerator.PluginVersion}+${sha.value.take(12)}")),
        runtimes
      )
      res <- AgentTrees.write(stores.at(Location.AgentHomes, dir), tree, runtimes.map(_.wire.value), sha)
    yield res

object AgentTrees:

  /** No environment: the trees only where the settings put them (a test's). */
  type SettingsVersion = GenSettings.Version
  val SettingsVersion: GenSettings.Version.type = GenSettings.Version

  val NoEnvironment: UserPaths = UserPaths(Environment.empty, UserPaths.OsName(""))

  /** The directory a request names for the trees (`~` expanded), absolute; none for a blank one. */
  def out(raw: Option[String], paths: UserPaths): Option[Path] =
    raw.map(_.trim).filter(_.nonEmpty).map(o => Path.of(expandIn(o, paths)).toAbsolutePath.normalize)

  /** `~` as the JVM's home directory: what the command-line runtimes still read (their slice gives them the injected
    * environment, §25).
    */
  def expand(p: String)(using home: JvmHome): String = home.expand(JvmHome.Path(p)).value

  /** `agents.runtimes.dir`, else the data directory the bundle uses (`runtime.data-dir`, else
    * `~/Library/Application Support/Histmersion` on macOS, `$XDG_DATA_HOME/histmersion` elsewhere) + `/agents`.
    */
  def root(app: AppConfig, paths: UserPaths): Option[Path] = root(app, paths.environment.lookup, paths.osName)

  /** [[root]] in the environment `env` of the operating system `osName`. */
  def root(app: AppConfig, env: UserPaths.Lookup, osName: UserPaths.OsName): Option[Path] =
    Option(app.agents.runtimes.dir)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(d => Path.of(expandIn(d, env)))
      .orElse(UserPaths.dataDir(app.runtime.dataDir, env, osName).map(_.resolve("agents")))
      .map(_.toAbsolutePath.normalize)

  /** `~` at the start of `p` as the environment's `HOME` (left as it is when there is none). */
  def expandIn(p: String, env: UserPaths.Lookup): String =
    env(Environment.VariablesKey("HOME"))
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .fold(p)(h => p.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(h)))

  /** [[expandIn]] the injected environment. */
  def expandIn(p: String, paths: UserPaths): String = expandIn(p, paths.environment.lookup)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else `<runtimes dir>/codex/home`. */
  def codexHome(app: AppConfig, paths: UserPaths): Option[Path] =
    codexHome(app, paths.environment.lookup, paths.osName)

  /** [[codexHome]] in the environment `env` of the operating system `osName`. */
  def codexHome(app: AppConfig, env: UserPaths.Lookup, osName: UserPaths.OsName): Option[Path] =
    Option(app.agents.providers.codex.home)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(h => Path.of(expandIn(h, env)))
      .orElse(root(app, env, osName).map(_.resolve("codex").resolve("home")))

  /** The definitions' fingerprint: what the trees were generated from. */
  def sha(d: AgentGenerator.Defs): Sha256 =
    Prompt.sha256(
      (d.agents.sortBy[String](_.name.value).map(a => s"${a.name}\u0000${a.body}\u0000${a.fingerprint}") ++
        d.fragments.toList.sortBy[String](_._1.value).map((k, v) => s"$k\u0000$v") ++
        d.skills.toList.sortBy[String](_._1.value).map((k, s) => s"$k\u0000${s.header.mkString("\n")}\u0000${s.body}"))
        .mkString("\u0001")
    )

  private val Manifest = "manifest.json"

  /** Write `tree` under `dir`: each file atomically, only when its text changed; a file the previous manifest listed
    * (for one of these runtimes) and this tree does not have is removed.
    */
  def write(store: Store, tree: Map[String, String], runtimes: List[String], sha: Sha256)(using
      java.time.Clock
  ): Either[Problem, GenerateResult] =
    AgentTreeWriter.write(store, tree, runtimes, sha).left.map(Problem(_))
