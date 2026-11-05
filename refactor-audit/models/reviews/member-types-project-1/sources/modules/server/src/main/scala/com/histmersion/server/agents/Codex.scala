package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.CodexConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*
import com.histmersion.api.Plural
import com.histmersion.domain.CodexRetention
import com.histmersion.domain.CodexRetention.FileInfo
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.jdk.CollectionConverters.*

object Codex:

  /** The typed answer's JSON Schema as derived (opencode reads it in the message). */
  type SchemaJsonResult = SchemaJsonResult.T
  object SchemaJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutputSchemaJsonResult = OutputSchemaJsonResult.T
  object OutputSchemaJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvResult = ArgvResult.T
  object ArgvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvModel = ArgvModel.T
  object ArgvModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvAgent = ArgvAgent.T
  object ArgvAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvSchemaFile = ArgvSchemaFile.T
  object ArgvSchemaFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvCwd = ArgvCwd.T
  object ArgvCwd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvLastFile = ArgvLastFile.T
  object ArgvLastFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentHome = EnvironmentHome.T
  object EnvironmentHome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentToken = EnvironmentToken.T
  object EnvironmentToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentEnvPart1 = EnvironmentEnvPart1.T
  object EnvironmentEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvironmentEnvPart2 = EnvironmentEnvPart2.T
  object EnvironmentEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseLines = ParseLines.T
  object ParseLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClientResultPart1 = ClientResultPart1.T
  object ClientResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def schemaJson(s: ResponseSchema[?]): com.histmersion.server.agents.Codex.SchemaJsonResult = schema(s).noSpaces

  /** The `--output-schema` file: the derived schema in OpenAI's strict form, the only one Codex's structured output
    * accepts ([[StrictJsonSchema]] says why and how).
    */
  def outputSchemaJson(s: ResponseSchema[?]): com.histmersion.server.agents.Codex.OutputSchemaJsonResult = StrictJsonSchema(schema(s)).noSpaces

  private def schema(s: ResponseSchema[?]): Json =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema"))

  /** `codex exec --json -p <role> …` with the prompt on stdin (`-`). Not `--ephemeral`: the rollout keeps the run's
    * rate-limit snapshot.
    */
  def argv(
      cfg: CodexConfig,
      model: com.histmersion.server.agents.Codex.ArgvModel,
      agent: com.histmersion.server.agents.Codex.ArgvAgent,
      schemaFile: com.histmersion.server.agents.Codex.ArgvSchemaFile,
      cwd: com.histmersion.server.agents.Codex.ArgvCwd,
      lastFile: com.histmersion.server.agents.Codex.ArgvLastFile
  ): List[com.histmersion.server.agents.Codex.ArgvResult] =
    List(
      cfg.command,
      "exec",
      "--json",
      "-p",
      agent,
      "-m",
      model,
      "--output-schema",
      schemaFile,
      "--skip-git-repo-check",
      "-C",
      cwd,
      "-o",
      lastFile,
      "-"
    )

  /** The CLI's environment: its own CODEX_HOME (its login, never a copied `auth.json`), the run token the MCP server
    * config names, and no API key unless asked (a key bills the API).
    */
  def environment(cfg: CodexConfig, home: com.histmersion.server.agents.Codex.EnvironmentHome, token: Option[com.histmersion.server.agents.Codex.EnvironmentToken], env: java.util.Map[com.histmersion.server.agents.Codex.EnvironmentEnvPart1, com.histmersion.server.agents.Codex.EnvironmentEnvPart2]): Unit =
    if !cfg.useApiKey then
      List("OPENAI_API_KEY", "CODEX_API_KEY").foreach { k =>
        val _ = env.remove(k)
      }
    val _ = env.put("CODEX_HOME", home)
    // the base config makes Histmersion's MCP server required: a step with no tools still starts it
    val _ = env.put(AgentGenerator.TokenEnv, token.getOrElse("none"))

  /** `codex exec --json` events → the outcome: `item.completed` agent messages (the last is the answer), MCP tool calls
    * with their results, reasoning; `turn.completed.usage`; `turn.failed` and `error` messages.
    */
  def parse(lines: List[com.histmersion.server.agents.Codex.ParseLines]): CliOutcome =
    val events = lines.map(_.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[String] = None
    var usage = TokenUsage.Zero
    var error: Option[String] = None
    var turns = 0
    events.foreach { e =>
      val c = e.hcursor
      c.downField("type").as[String].getOrElse("") match
        case "item.completed" =>
          val it = c.downField("item")
          it.downField("type").as[String].getOrElse("") match
            case "agent_message" =>
              val t = it.downField("text").as[String].getOrElse("")
              turns += 1
              finalText = Some(t)
              entries += ConversationEntry.AssistantResponse(t, Nil)
            case "mcp_tool_call" =>
              val id = it.downField("id").as[String].getOrElse("")
              val tool = it.downField("tool").as[String].getOrElse("")
              val args = it.downField("arguments").focus.fold("{}")(_.noSpaces)
              val res = it
                .downField("result")
                .downField("content")
                .as[List[Json]]
                .getOrElse(Nil)
                .flatMap(_.hcursor.downField("text").as[String].toOption)
                .mkString("\n")
              val out = it.downField("error").downField("message").as[String].toOption.fold(res)(m => s"error: $m")
              entries += ConversationEntry.AssistantResponse("", List(ToolCall(id, tool, args)))
              entries += ConversationEntry.ToolResult(id, tool, out)
              calls += ToolCallRecord(id, tool, args, out, math.max(1, turns))
            case _ => ()
        case "turn.completed" =>
          val u = c.downField("usage")
          val l = (k: String) => u.downField(k).as[Long].getOrElse(0L)
          usage = usage + TokenUsage(
            Tokens(l("input_tokens")),
            Tokens(l("output_tokens")),
            Tokens(l("cached_input_tokens")),
            Tokens.Zero,
            Tokens(l("cache_write_input_tokens"))
          )
        case "turn.failed" =>
          error = c.downField("error").downField("message").as[String].toOption.orElse(Some("turn failed"))
        case "error" => error = error.orElse(c.downField("message").as[String].toOption)
        case _       => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, turns)

  def client(cfg: CodexConfig, rt: CliRuntime, ref: ModelRef): Either[com.histmersion.server.agents.Codex.ClientResultPart1, LlmClient] =
    if ref.provider != ProviderKind.Codex then Left(s"${ref.wire} is not a codex model")
    else if !rt.loopback then
      Left("codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan")
    else if cfg.command.trim.isEmpty then Left("agents.providers.codex.command is empty")
    else Right(CodexExecClient(ref, cfg, rt))

  /** Who uses the runs' Codex home, and its retention (#2582, [[CodexRetention]]).
    *
    * Every process Histmersion starts on the home — a step's `codex exec`, a plan read's `codex app-server` — holds the
    * home's SHARED lock while it runs ([[using]]); a retention pass takes the EXCLUSIVE lock without waiting, and when
    * a run holds the home it skips ("in use") rather than delete or vacuum under it. While the pass runs, a new run
    * waits for it. SQLite's own locking guards the vacuum against anything else that has the files open (it then fails,
    * said in the report).
    */
  object Home:

    type RetainNowMs = RetainNowMs.T
    object RetainNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainRunArg1 = RetainRunArg1.T
    object RetainRunArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainRunResultPart1 = RetainRunResultPart1.T
    object RetainRunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainRunResultPart2 = RetainRunResultPart2.T
    object RetainRunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunCommandResultPart1 = RunCommandResultPart1.T
    object RunCommandResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunCommandResultPart2 = RunCommandResultPart2.T
    object RunCommandResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunCommandArgv = RunCommandArgv.T
    object RunCommandArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaybeRetainNowMs = MaybeRetainNowMs.T
    object MaybeRetainNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val locks = ConcurrentHashMap[Path, ReentrantReadWriteLock]()
    private val lastPass = ConcurrentHashMap[Path, AtomicLong]()

    private def key(home: Path): Path = home.toAbsolutePath.normalize
    private def lock(home: Path): ReentrantReadWriteLock =
      locks.computeIfAbsent(key(home), _ => ReentrantReadWriteLock())

    /** Run `f` (a Codex process on `home`) holding the home's shared lock. */
    def using[A](home: Path)(f: => A): A =
      val l = lock(home).readLock()
      l.lock()
      try f
      finally l.unlock()

    /** What a pass did: `skipped` says why it did nothing. */
    final case class Report(
        skipped: Option[Report.Skipped] = None,
        deleted: Report.Deleted = 0,
        freedBytes: Report.FreedBytes = 0,
        vacuumed: List[(Report.VacuumedPart1, Report.VacuumedPart2, Report.VacuumedPart3)] = Nil,
        problems: List[Report.Problems] = Nil
    ):
      def summary: com.histmersion.server.agents.Codex.Home.Report.SummaryResult = skipped.getOrElse {
        val v = vacuumed.map((n, b, a) =>
          s"$n ${com.histmersion.api.backup.Backup.humanBytes(b)} → ${com.histmersion.api.backup.Backup.humanBytes(a)}"
        )
        (List(
          s"${Plural.count(deleted, "transcript")} deleted (${com.histmersion.api.backup.Backup.humanBytes(freedBytes)})"
        ) ++
          Option.when(v.nonEmpty)(s"vacuumed ${v.mkString(", ")}") ++ problems).mkString("; ")
      }

    object Report:
      type SummaryResult = SummaryResult.T
      object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Skipped = Skipped.T
      object Skipped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Deleted = Deleted.T
      object Deleted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FreedBytes = FreedBytes.T
      object FreedBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type VacuumedPart1 = VacuumedPart1.T
      object VacuumedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type VacuumedPart2 = VacuumedPart2.T
      object VacuumedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type VacuumedPart3 = VacuumedPart3.T
      object VacuumedPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Problems = Problems.T
      object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** Every regular file under `dir` (none when it is missing), relative to `home`. */
    private def filesUnder(home: Path, dir: Path): List[FileInfo] =
      if !Files.isDirectory(dir) then Nil
      else
        val s = Files.walk(dir)
        try
          s.iterator()
            .asScala
            .filter(p => Files.isRegularFile(p) && !Files.isSymbolicLink(p))
            .map { p =>
              FileInfo(
                home.relativize(p).toString.replace('\\', '/'),
                Files.size(p),
                Files.getLastModifiedTime(p).toMillis
              )
            }
            .toList
        finally s.close()

    /** One pass now: transcripts older than `keep-sessions-days` deleted, the SQLite files vacuumed — or nothing, when
      * a run holds the home.
      */
    def retain(
        home: Path,
        cfg: CodexConfig,
        nowMs: com.histmersion.server.agents.Codex.Home.RetainNowMs = System.currentTimeMillis(),
        run: List[com.histmersion.server.agents.Codex.Home.RetainRunArg1] => Either[com.histmersion.server.agents.Codex.Home.RetainRunResultPart1, com.histmersion.server.agents.Codex.Home.RetainRunResultPart2] = runCommand
    ): Report =
      val w = lock(home).writeLock()
      if !Files.isDirectory(home) then Report(skipped = Some(s"no Codex home at $home"))
      else if !w.tryLock() then Report(skipped = Some("a Codex run is using the home — not now"))
      else
        try
          val problems = List.newBuilder[String]
          val files = CodexRetention.SessionDirs.flatMap(d => filesUnder(home, home.resolve(d)))
          val gone = CodexRetention.stale(files, nowMs, cfg.keepSessionsDays).filter { f =>
            scala.util
              .Try(Files.deleteIfExists(home.resolve(f.rel)))
              .fold(e => { problems += s"${f.rel}: ${e.getMessage}"; false }, identity)
          }
          CodexRetention.SessionDirs.foreach(d => pruneEmpty(home.resolve(d)))
          val vacuumed =
            if !cfg.vacuum then Nil
            else
              val names = Option(home.toFile.list()).toList.flatten
              CodexRetention.sqliteFiles(names).flatMap { n =>
                val f = home.resolve(n)
                val before = Files.size(f)
                run(List(cfg.sqlite, f.toString, "VACUUM;")) match
                  case Right(0) => Some((n, before, Files.size(f)))
                  case Right(c) => problems += s"$n: ${cfg.sqlite} exit $c"; None
                  case Left(e)  => problems += s"$n: $e"; None
              }
          Report(None, gone.size, gone.map(_.bytes).sum, vacuumed, problems.result())
        finally w.unlock()

    /** Empty directories under `dir` (not `dir` itself), deepest first. */
    private def pruneEmpty(dir: Path): Unit =
      if Files.isDirectory(dir) then
        val s = Files.walk(dir)
        try
          s.iterator()
            .asScala
            .toList
            .filter(p => p != dir && Files.isDirectory(p))
            .sortBy(-_.getNameCount)
            .foreach(p => scala.util.Try(Files.delete(p))) // a non-empty one stays
        finally s.close()

    /** A command's exit code; Left when it cannot start or does not end within 10 minutes. */
    def runCommand(argv: List[com.histmersion.server.agents.Codex.Home.RunCommandArgv]): Either[com.histmersion.server.agents.Codex.Home.RunCommandResultPart1, com.histmersion.server.agents.Codex.Home.RunCommandResultPart2] =
      scala.util
        .Try {
          val p =
            ProcessBuilder(argv.asJava)
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start()
          if p.waitFor(10, TimeUnit.MINUTES) then Right(p.exitValue())
          else { p.destroyForcibly(); Left(s"${argv.head} did not finish in 10 minutes") }
        }
        .toEither
        .left
        .map(e => s"${argv.headOption.getOrElse("")}: ${e.getMessage}")
        .flatten

    /** After a step: a pass on a virtual thread when one is due ([[CodexRetention.due]]). */
    def maybeRetain(home: Path, cfg: CodexConfig, nowMs: com.histmersion.server.agents.Codex.Home.MaybeRetainNowMs = System.currentTimeMillis()): Unit =
      val last = lastPass.computeIfAbsent(key(home), _ => AtomicLong(0L))
      val prev = last.get()
      if CodexRetention.due(Option.when(prev > 0)(prev), nowMs, cfg.retentionEveryHours) && last.compareAndSet(
          prev,
          nowMs
        )
      then
        val _ = Thread.ofVirtual().name("codex-retention").start { () =>
          val r = retain(home, cfg)
          if r.skipped.isDefined then last.set(prev) // try again after the next step
          scribe.info(s"codex home retention ($home): ${r.summary}")
        }
