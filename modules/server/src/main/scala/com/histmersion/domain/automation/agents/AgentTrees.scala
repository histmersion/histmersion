package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.ClaudeCode

import com.histmersion.config.{AgentsConfig, AppConfig, UserPaths}
import com.histmersion.api.automation.Agents.GenerateResult
import io.circe.Json
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Writes the generated per-runtime trees (docs/research-agent-runtimes.md §2.2) under the runtimes directory:
  * `<data dir>/agents/{claude-code,codex,opencode}/` and `manifest.json`.
  *
  * File by file, each one atomically (a temporary file renamed over it), and a file this generator wrote last time but
  * no longer produces is removed — never anything else: `codex/home` is also the CODEX_HOME of the runs, holding its
  * login and sessions, and `claude-code/login` an optional CLAUDE_CONFIG_DIR. Login state is only ever created by the
  * vendor's own login command there.
  */
final class AgentTrees(app: AppConfig, defs: AgentDefinitions, paths: UserPaths = AgentTrees.NoEnvironment)(using stores: com.histmersion.util.storage.Stores, clock: java.time.Clock):

  private val cfg: AgentsConfig = app.agents

  /** The runtimes directory: `agents.runtimes.dir`, else `<data dir>/agents`. */
  def root: Option[Path] = AgentTrees.root(app, paths)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else the generated one. */
  def codexHome: Option[Path] = AgentTrees.codexHome(app, paths)

  def claudePlugin: Option[Path] = root.map(_.resolve("claude-code").resolve("headless"))
  def claudeRole(name: com.histmersion.domain.automation.agents.AgentTrees.ClaudeRoleName): Option[Path] =
    root.map(_.resolve("claude-code").resolve("roles").resolve(name).resolve("settings.json"))
  def opencodeXdg: Option[Path] = root.map(_.resolve("opencode").resolve("xdg"))
  def opencodeConfig: Option[Path] = root.map(_.resolve("opencode").resolve("config"))

  private def publicUrl = app.server.publicUrl.stripSuffix("/")

  def settings(version: com.histmersion.domain.automation.agents.AgentTrees.SettingsVersion): GenSettings =
    val agentMcp = Option(cfg.providers.claudeCode.mcpUrl.trim).filter(_.nonEmpty).getOrElse(s"$publicUrl/mcp/agent")
    GenSettings(
      agentMcp,
      Some(s"$publicUrl/mcp"),
      s"$publicUrl/api/agents/quota/claude",
      cfg.runtimes.claudeMod,
      cfg.runtimes.interactiveMod,
      version,
      cfg.providers.codex.toolOutputTokens,
      cfg.providers.codex.leanBase
    )

  /** The definitions, loaded and checked; `known` are the MCP tool names placeholders may use. */
  def load(known: Set[com.histmersion.domain.automation.agents.AgentTrees.LoadKnown]): Either[com.histmersion.domain.automation.agents.AgentTrees.LoadResultPart1, AgentGenerator.Defs] =
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
      d = AgentGenerator.Defs(as, fs, ss)
      _ <- AgentGenerator.check(d, known) match
        case Nil  => Right(())
        case errs => Left(errs.mkString("; "))
    yield d

  /** Generate the trees of `runtimes` (all three by default) into `out` (default [[root]]). */
  def generate(
      known: Set[com.histmersion.domain.automation.agents.AgentTrees.GenerateKnown],
      runtimes: List[AgentRuntime] = AgentRuntime.Generated,
      out: Option[Path] = None
  ): Either[com.histmersion.domain.automation.agents.AgentTrees.GenerateResultPart1, GenerateResult] =
    for
      dir <- out
        .orElse(root)
        .toRight("no runtimes directory: set agents.runtimes.dir (HISTMERSION_AGENTS_RUNTIMES_DIR)")
      d <- load(known)
      sha = AgentTrees.sha(d)
      tree = AgentGenerator.tree(d, settings(s"${AgentGenerator.PluginVersion}+${sha.take(12)}"), runtimes)
      res <- AgentTrees.write(stores.at(com.histmersion.util.storage.Location.AgentHomes, dir), tree, runtimes.map(_.wire), sha)
    yield res

object AgentTrees:

  /** No environment: the trees only where the settings put them (a test's). */
  type ClaudeRoleName = ClaudeRoleName.T
  object ClaudeRoleName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsVersion = _root_.com.histmersion.domain.automation.agents.GenSettings.Version
  val SettingsVersion: _root_.com.histmersion.domain.automation.agents.GenSettings.Version.type = _root_.com.histmersion.domain.automation.agents.GenSettings.Version
  type LoadResultPart1 = LoadResultPart1.T
  object LoadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadKnown = LoadKnown.T
  object LoadKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GenerateResultPart1 = GenerateResultPart1.T
  object GenerateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GenerateKnown = GenerateKnown.T
  object GenerateKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutRaw = OutRaw.T
  object OutRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandResult = ExpandResult.T
  object ExpandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandP = ExpandP.T
  object ExpandP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootEnvArg1 = RootEnvArg1.T
  object RootEnvArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootEnvResult = RootEnvResult.T
  object RootEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootOsName = RootOsName.T
  object RootOsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandInResult = ExpandInResult.T
  object ExpandInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandInP = ExpandInP.T
  object ExpandInP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandInEnvArg1 = ExpandInEnvArg1.T
  object ExpandInEnvArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandInEnvResult = ExpandInEnvResult.T
  object ExpandInEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodexHomeEnvArg1 = CodexHomeEnvArg1.T
  object CodexHomeEnvArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodexHomeEnvResult = CodexHomeEnvResult.T
  object CodexHomeEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodexHomeOsName = CodexHomeOsName.T
  object CodexHomeOsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShaResult = ShaResult.T
  object ShaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResultPart1 = WriteResultPart1.T
  object WriteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreePart1 = WriteTreePart1.T
  object WriteTreePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreePart2 = WriteTreePart2.T
  object WriteTreePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRuntimes = WriteRuntimes.T
  object WriteRuntimes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteSha = WriteSha.T
  object WriteSha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val NoEnvironment: UserPaths = UserPaths(com.histmersion.config.Environment.empty, "")

  /** The directory a request names for the trees (`~` expanded), absolute; none for a blank one. */
  def out(raw: Option[com.histmersion.domain.automation.agents.AgentTrees.OutRaw], paths: UserPaths): Option[Path] =
    raw.map(_.trim).filter(_.nonEmpty).map(o => Path.of(expandIn(o, paths)).toAbsolutePath.normalize)

  /** `~` as the JVM's home directory: what the command-line runtimes still read (their slice gives them the injected
    * environment, §25).
    */
  def expand(p: com.histmersion.domain.automation.agents.AgentTrees.ExpandP)(using home: com.histmersion.util.system.JvmHome): com.histmersion.domain.automation.agents.AgentTrees.ExpandResult = home.expand(p)

  /** `agents.runtimes.dir`, else the data directory the bundle uses (`runtime.data-dir`, else
    * `~/Library/Application Support/Histmersion` on macOS, `$XDG_DATA_HOME/histmersion` elsewhere) + `/agents`.
    */
  def root(app: AppConfig, paths: UserPaths): Option[Path] = root(app, paths.environment.lookup, paths.osName)

  /** [[root]] in the environment `env` of the operating system `osName`. */
  def root(app: AppConfig, env: com.histmersion.domain.automation.agents.AgentTrees.RootEnvArg1 => Option[com.histmersion.domain.automation.agents.AgentTrees.RootEnvResult], osName: com.histmersion.domain.automation.agents.AgentTrees.RootOsName): Option[Path] =
    Option(app.agents.runtimes.dir)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(d => Path.of(expandIn(d, env)))
      .orElse(UserPaths.dataDir(app.runtime.dataDir, env, osName).map(_.resolve("agents")))
      .map(_.toAbsolutePath.normalize)

  /** `~` at the start of `p` as the environment's `HOME` (left as it is when there is none). */
  def expandIn(p: com.histmersion.domain.automation.agents.AgentTrees.ExpandInP, env: com.histmersion.domain.automation.agents.AgentTrees.ExpandInEnvArg1 => Option[com.histmersion.domain.automation.agents.AgentTrees.ExpandInEnvResult]): com.histmersion.domain.automation.agents.AgentTrees.ExpandInResult =
    env("HOME")
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(p)(h => p.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(h)))

  /** [[expandIn]] the injected environment. */
  def expandIn(p: com.histmersion.domain.automation.agents.AgentTrees.ExpandInP, paths: UserPaths): com.histmersion.domain.automation.agents.AgentTrees.ExpandInResult = expandIn(p, paths.environment.lookup)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else `<runtimes dir>/codex/home`. */
  def codexHome(app: AppConfig, paths: UserPaths): Option[Path] =
    codexHome(app, paths.environment.lookup, paths.osName)

  /** [[codexHome]] in the environment `env` of the operating system `osName`. */
  def codexHome(app: AppConfig, env: com.histmersion.domain.automation.agents.AgentTrees.CodexHomeEnvArg1 => Option[com.histmersion.domain.automation.agents.AgentTrees.CodexHomeEnvResult], osName: com.histmersion.domain.automation.agents.AgentTrees.CodexHomeOsName): Option[Path] =
    Option(app.agents.providers.codex.home)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(h => Path.of(expandIn(h, env)))
      .orElse(root(app, env, osName).map(_.resolve("codex").resolve("home")))

  /** The definitions' fingerprint: what the trees were generated from. */
  def sha(d: AgentGenerator.Defs): com.histmersion.domain.automation.agents.AgentTrees.ShaResult =
    Prompt.sha256(
      (d.agents.sortBy(_.name).map(a => s"${a.name}\u0000${a.body}\u0000${a.fingerprint}") ++
        d.fragments.toList.sorted.map((k, v) => s"$k\u0000$v") ++
        d.skills.toList.sortBy(_._1).map((k, s) => s"$k\u0000${s.header.mkString("\n")}\u0000${s.body}"))
        .mkString("\u0001")
    )

  private val Manifest = "manifest.json"

  /** Write `tree` under `dir`: each file atomically, only when its text changed; a file the previous manifest listed
    * (for one of these runtimes) and this tree does not have is removed.
    */
  def write(store: com.histmersion.util.storage.Store, tree: Map[com.histmersion.domain.automation.agents.AgentTrees.WriteTreePart1, com.histmersion.domain.automation.agents.AgentTrees.WriteTreePart2], runtimes: List[com.histmersion.domain.automation.agents.AgentTrees.WriteRuntimes], sha: com.histmersion.domain.automation.agents.AgentTrees.WriteSha)(using java.time.Clock): Either[com.histmersion.domain.automation.agents.AgentTrees.WriteResultPart1, GenerateResult] =
    AgentTreeWriter.write(store, tree, runtimes, sha)
