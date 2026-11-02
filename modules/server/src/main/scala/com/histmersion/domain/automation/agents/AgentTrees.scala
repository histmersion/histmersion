package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.ClaudeCode

import com.histmersion.config.{AgentsConfig, AppConfig, UserPaths}
import com.histmersion.api.Agents.GenerateResult
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
final class AgentTrees(app: AppConfig, defs: AgentDefinitions, paths: UserPaths = AgentTrees.NoEnvironment):

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

  private def publicUrl = app.server.publicUrl.stripSuffix("/")

  def settings(version: String): GenSettings =
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
  def load(known: Set[String]): Either[String, AgentGenerator.Defs] =
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
      known: Set[String],
      runtimes: List[AgentRuntime] = AgentRuntime.Generated,
      out: Option[Path] = None
  ): Either[String, GenerateResult] =
    for
      dir <- out
        .orElse(root)
        .toRight("no runtimes directory: set agents.runtimes.dir (HISTMERSION_AGENTS_RUNTIMES_DIR)")
      d <- load(known)
      sha = AgentTrees.sha(d)
      tree = AgentGenerator.tree(d, settings(s"${AgentGenerator.PluginVersion}+${sha.take(12)}"), runtimes)
      res <- AgentTrees.write(dir, tree, runtimes.map(_.wire), sha)
    yield res

object AgentTrees:

  /** No environment: the trees only where the settings put them (a test's). */
  val NoEnvironment: UserPaths = UserPaths(com.histmersion.config.Environment.empty, "")

  /** The directory a request names for the trees (`~` expanded), absolute; none for a blank one. */
  def out(raw: Option[String], paths: UserPaths): Option[Path] =
    raw.map(_.trim).filter(_.nonEmpty).map(o => Path.of(expandIn(o, paths)).toAbsolutePath.normalize)

  /** `~` as the JVM's home directory: what the command-line runtimes still read (their slice gives them the injected
    * environment, §25).
    */
  def expand(p: String): String = p.replaceFirst("^~", System.getProperty("user.home"))

  /** `agents.runtimes.dir`, else the data directory the bundle uses (`runtime.data-dir`, else
    * `~/Library/Application Support/Histmersion` on macOS, `$XDG_DATA_HOME/histmersion` elsewhere) + `/agents`.
    */
  def root(app: AppConfig, paths: UserPaths): Option[Path] = root(app, paths.environment.lookup, paths.osName)

  /** [[root]] in the environment `env` of the operating system `osName`. */
  def root(app: AppConfig, env: String => Option[String], osName: String): Option[Path] =
    Option(app.agents.runtimes.dir)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(d => Path.of(expandIn(d, env)))
      .orElse(UserPaths.dataDir(app.runtime.dataDir, env, osName).map(_.resolve("agents")))
      .map(_.toAbsolutePath.normalize)

  /** `~` at the start of `p` as the environment's `HOME` (left as it is when there is none). */
  def expandIn(p: String, env: String => Option[String]): String =
    env("HOME")
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(p)(h => p.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(h)))

  /** [[expandIn]] the injected environment. */
  def expandIn(p: String, paths: UserPaths): String = expandIn(p, paths.environment.lookup)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else `<runtimes dir>/codex/home`. */
  def codexHome(app: AppConfig, paths: UserPaths): Option[Path] =
    codexHome(app, paths.environment.lookup, paths.osName)

  /** [[codexHome]] in the environment `env` of the operating system `osName`. */
  def codexHome(app: AppConfig, env: String => Option[String], osName: String): Option[Path] =
    Option(app.agents.providers.codex.home)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(h => Path.of(expandIn(h, env)))
      .orElse(root(app, env, osName).map(_.resolve("codex").resolve("home")))

  /** The definitions' fingerprint: what the trees were generated from. */
  def sha(d: AgentGenerator.Defs): String =
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
  def write(dir: Path, tree: Map[String, String], runtimes: List[String], sha: String): Either[String, GenerateResult] =
    try
      Files.createDirectories(dir)
      val previous: List[String] =
        val m = dir.resolve(Manifest)
        if !Files.isRegularFile(m) then Nil
        else
          io.circe.parser
            .parse(Files.readString(m, UTF_8))
            .toOption
            .flatMap(_.hcursor.downField("files").as[Map[String, String]].toOption)
            .map(_.keys.toList)
            .getOrElse(Nil)
      var written = 0 // counted while writing; local to this call
      tree.toList.sortBy(_._1).foreach { (rel, text) =>
        val target = dir.resolve(rel).normalize
        require(target.startsWith(dir), s"generated path $rel leaves $dir")
        val same = Files.isRegularFile(target) && Files.readString(target, UTF_8) == text
        if !same then
          Files.createDirectories(target.getParent)
          val tmp = Files.createTempFile(target.getParent, ".gen-", ".tmp")
          Files.writeString(tmp, text, UTF_8)
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
          written += 1
      }
      val gone = previous.filter(p => runtimes.exists(r => p.startsWith(s"$r/")) && !tree.contains(p))
      gone.foreach { p =>
        val _ = Files.deleteIfExists(dir.resolve(p).normalize)
      }
      // the empty login directories the vendors' own login commands fill (never this generator)
      if runtimes.contains(AgentRuntime.ClaudeCode.wire) then {
        val _ = Files.createDirectories(dir.resolve("claude-code/login"))
      }
      val keep = previous.filterNot(p => runtimes.exists(r => p.startsWith(s"$r/")))
      val files = (keep.flatMap(p =>
        Option.when(Files.isRegularFile(dir.resolve(p)))(p -> Prompt.sha256(Files.readString(dir.resolve(p), UTF_8)))
      ) ++
        tree.toList.map((k, v) => k -> Prompt.sha256(v))).sortBy(_._1)
      val manifest = Json.obj(
        "generator" -> AgentGenerator.PluginVersion.asJson,
        "definitions" -> sha.asJson,
        "generatedAt" -> java.time.Instant.now().toString.asJson,
        "runtimes" -> runtimes.asJson,
        "files" -> Json.obj(files.map((k, v) => k -> v.asJson)*)
      )
      Files.writeString(dir.resolve(Manifest), AgentGenerator.pretty(manifest), UTF_8)
      Right(GenerateResult(dir.toString, runtimes, tree.size, written, gone.size, sha))
    catch case e: Exception => Left(s"generating into $dir failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
