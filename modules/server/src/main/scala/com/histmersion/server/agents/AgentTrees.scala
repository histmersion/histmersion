package com.histmersion.server.agents

import com.histmersion.core.{AgentsConfig, AppConfig, UserPaths}
import com.histmersion.core.Agents.GenerateResult
import io.circe.Json
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Writes the generated per-runtime trees (docs/research-agent-runtimes.md §2.2) under the runtimes
  * directory: `<data dir>/agents/{claude-code,codex,opencode}/` and `manifest.json`.
  *
  * File by file, each one atomically (a temporary file renamed over it), and a file this generator
  * wrote last time but no longer produces is removed — never anything else: `codex/home` is also the
  * CODEX_HOME of the runs, holding its login and sessions, and `claude-code/login` an optional
  * CLAUDE_CONFIG_DIR. Login state is only ever created by the vendor's own login command there. */
final class AgentTrees(app: AppConfig, defs: AgentDefinitions):

  private val cfg: AgentsConfig = app.agents

  /** The runtimes directory: `agents.runtimes.dir`, else `<data dir>/agents`. */
  def root: Option[Path] = AgentTrees.root(app, sys.env.get)

  /** The CODEX_HOME of the runs: `agents.providers.codex.home`, else the generated one. */
  def codexHome: Option[Path] =
    Option(cfg.providers.codex.home).map(_.trim).filter(_.nonEmpty).map(h => Path.of(AgentTrees.expand(h)))
      .orElse(root.map(_.resolve("codex").resolve("home")))

  def claudePlugin: Option[Path] = root.map(_.resolve("claude-code").resolve("headless"))
  def claudeRole(name: String): Option[Path] = root.map(_.resolve("claude-code").resolve("roles").resolve(name).resolve("settings.json"))
  def opencodeXdg: Option[Path] = root.map(_.resolve("opencode").resolve("xdg"))
  def opencodeConfig: Option[Path] = root.map(_.resolve("opencode").resolve("config"))

  private def publicUrl = app.server.publicUrl.stripSuffix("/")

  def settings(version: String): GenSettings =
    val agentMcp = Option(cfg.providers.claudeCode.mcpUrl.trim).filter(_.nonEmpty).getOrElse(s"$publicUrl/mcp/agent")
    GenSettings(agentMcp, Some(s"$publicUrl/mcp"), s"$publicUrl/api/agents/quota/claude", cfg.runtimes.claudeMod,
      cfg.runtimes.interactiveMod, version, cfg.providers.codex.toolOutputTokens, cfg.providers.codex.leanBase)

  /** The definitions, loaded and checked; `known` are the MCP tool names placeholders may use. */
  def load(known: Set[String]): Either[String, AgentGenerator.Defs] =
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
      d   = AgentGenerator.Defs(as, fs, ss)
      _  <- AgentGenerator.check(d, known) match
              case Nil  => Right(())
              case errs => Left(errs.mkString("; "))
    yield d

  /** Generate the trees of `runtimes` (all three by default) into `out` (default [[root]]). */
  def generate(known: Set[String], runtimes: List[AgentRuntime] = AgentRuntime.Generated,
      out: Option[Path] = None): Either[String, GenerateResult] =
    for
      dir <- out.orElse(root).toRight("no runtimes directory: set agents.runtimes.dir (HISTMERSION_AGENTS_RUNTIMES_DIR)")
      d   <- load(known)
      sha  = AgentTrees.sha(d)
      tree = AgentGenerator.tree(d, settings(s"${AgentGenerator.PluginVersion}+${sha.take(12)}"), runtimes)
      res <- AgentTrees.write(dir, tree, runtimes.map(_.wire), sha)
    yield res

object AgentTrees:

  def expand(p: String): String = p.replaceFirst("^~", System.getProperty("user.home"))

  /** `agents.runtimes.dir`, else the data directory the bundle uses (`runtime.data-dir`, else
    * `~/Library/Application Support/Histmersion` on macOS, `$XDG_DATA_HOME/histmersion` elsewhere) + `/agents`. */
  def root(app: AppConfig, env: String => Option[String]): Option[Path] =
    Option(app.agents.runtimes.dir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(expand(d)))
      .orElse {
        UserPaths.dataDir(app.runtime.dataDir, env, System.getProperty("os.name", "")).map(_.resolve("agents"))
      }.map(_.toAbsolutePath.normalize)

  /** The definitions' fingerprint: what the trees were generated from. */
  def sha(d: AgentGenerator.Defs): String =
    Prompt.sha256((d.agents.sortBy(_.name).map(a => s"${a.name}\u0000${a.body}\u0000${a.toString}") ++
      d.fragments.toList.sorted.map((k, v) => s"$k\u0000$v") ++
      d.skills.toList.sortBy(_._1).map((k, s) => s"$k\u0000${s.header.mkString("\n")}\u0000${s.body}")).mkString("\u0001"))

  private val Manifest = "manifest.json"

  /** Write `tree` under `dir`: each file atomically, only when its text changed; a file the previous
    * manifest listed (for one of these runtimes) and this tree does not have is removed. */
  def write(dir: Path, tree: Map[String, String], runtimes: List[String], sha: String): Either[String, GenerateResult] =
    try
      Files.createDirectories(dir)
      val previous: List[String] =
        val m = dir.resolve(Manifest)
        if !Files.isRegularFile(m) then Nil
        else io.circe.parser.parse(Files.readString(m, UTF_8)).toOption
          .flatMap(_.hcursor.downField("files").as[Map[String, String]].toOption).map(_.keys.toList).getOrElse(Nil)
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
      gone.foreach(p => { val _ = Files.deleteIfExists(dir.resolve(p).normalize) })
      // the empty login directories the vendors' own login commands fill (never this generator)
      if runtimes.contains(AgentRuntime.ClaudeCode.wire) then { val _ = Files.createDirectories(dir.resolve("claude-code/login")) }
      val keep = previous.filterNot(p => runtimes.exists(r => p.startsWith(s"$r/")))
      val files = (keep.flatMap(p => Option.when(Files.isRegularFile(dir.resolve(p)))(p -> Prompt.sha256(Files.readString(dir.resolve(p), UTF_8)))) ++
        tree.toList.map((k, v) => k -> Prompt.sha256(v))).sortBy(_._1)
      val manifest = Json.obj(
        "generator" -> AgentGenerator.PluginVersion.asJson,
        "definitions" -> sha.asJson,
        "generatedAt" -> java.time.Instant.now().toString.asJson,
        "runtimes" -> runtimes.asJson,
        "files" -> Json.obj(files.map((k, v) => k -> v.asJson)*))
      Files.writeString(dir.resolve(Manifest), AgentGenerator.pretty(manifest), UTF_8)
      Right(GenerateResult(dir.toString, runtimes, tree.size, written, gone.size, sha))
    catch
      case e: Exception => Left(s"generating into $dir failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

/** The repository's checked-in copy of the interactive Claude Code plugin — `plugin/` and the root
  * `.claude-plugin/marketplace.json` — for people without a running server. A generator output like
  * the rest; `PluginTreeSuite` fails when it is out of date. Regenerate with `just plugin`
  * (`sbt "server/Test/runMain com.histmersion.server.agents.GeneratePlugin <repo root>"` — the test
  * classpath runs it in process, outside the native-image tracing agent). */
object GeneratePlugin:

  /** The tree from the bundled definitions (no server, no override directory). */
  def tree(): Either[String, Map[String, String]] =
    val defs = AgentDefinitions(Prompts(""))
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
    yield AgentGenerator.repoPlugin(AgentGenerator.Defs(as, fs, ss))

  /** The generated directories the tree owns: anything else in them is stale. */
  val Owned: List[String] = List("plugin/agents", "plugin/skills")

  def main(args: Array[String]): Unit =
    val root = Path.of(args.headOption.getOrElse(".")).toAbsolutePath.normalize
    tree() match
      case Left(m) => System.err.println(s"cannot generate the plugin: $m"); sys.exit(1)
      case Right(t) =>
        Owned.map(root.resolve).filter(Files.exists(_)).foreach { d =>
          val all = Files.walk(d)
          try all.sorted(java.util.Comparator.reverseOrder()).forEach { p =>
            val rel = root.relativize(p).toString
            if Files.isSymbolicLink(p) || (Files.isRegularFile(p) && !t.contains(rel)) || (Files.isDirectory(p) && p != d && !t.keys.exists(_.startsWith(rel + "/"))) then
              val _ = Files.deleteIfExists(p)
          } finally all.close()
        }
        t.foreach { (rel, text) =>
          val f = root.resolve(rel)
          if Files.isSymbolicLink(f.getParent) then Files.delete(f.getParent)
          Files.createDirectories(f.getParent)
          Files.writeString(f, text, UTF_8)
        }
        System.err.println(s"wrote ${t.size} file(s) under $root")
