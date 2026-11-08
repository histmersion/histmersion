package com.histmersion
package server.agents

import com.histmersion.api.Problem
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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.backup.Backup

object Codex:
  private given CanEqual[Path, Path] = CanEqual.derived

  type SchemaJson = SchemaJson.T
  object SchemaJson extends Newtype[String, Pure]

  /** The typed answer's JSON Schema as derived (opencode reads it in the message). */

  def schemaJson(s: ResponseSchema[?]): SchemaJson = SchemaJson(schema(s).noSpaces)

  /** The `--output-schema` file: the derived schema in OpenAI's strict form, the only one Codex's structured output
    * accepts ([[StrictJsonSchema]] says why and how).
    */
  def outputSchemaJson(s: ResponseSchema[?]): SchemaJson = SchemaJson(StrictJsonSchema(schema(s)).noSpaces)

  private def schema(s: ResponseSchema[?]): Json =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema"))

  /** `codex exec --json -p <role> …` with the prompt on stdin (`-`). Not `--ephemeral`: the rollout keeps the run's
    * rate-limit snapshot.
    */
  def argv(
      cfg: CodexConfig,
      model: ModelRef.Model,
      agent: AgentDef.Name,
      schemaFile: Path,
      cwd: Path,
      lastFile: Path
  ): List[ClaudeCode.Argument] =
    List(
      cfg.command.value,
      "exec",
      "--json",
      "-p",
      agent.value,
      "-m",
      model.value,
      "--output-schema",
      schemaFile.toString,
      "--skip-git-repo-check",
      "-C",
      cwd.toString,
      "-o",
      lastFile.toString,
      "-"
    ).map(ClaudeCode.Argument(_))

  /** The CLI's environment: its own CODEX_HOME (its login, never a copied `auth.json`), the run token the MCP server
    * config names, and no API key unless asked (a key bills the API).
    */
  def environment(
      cfg: CodexConfig,
      home: Path,
      token: Option[RunGrant.Token],
      env: java.util.Map[String, String]
  ): Unit =
    if !cfg.useApiKey.value then
      List("OPENAI_API_KEY", "CODEX_API_KEY").foreach { k =>
        val _ = env.remove(k)
      }
    val _ = env.put("CODEX_HOME", home.toString)
    // the base config makes Histmersion's MCP server required: a step with no tools still starts it
    val _ = env.put(AgentGenerator.TokenEnv, token.fold("none")(_.value))

  /** `codex exec --json` events → the outcome: `item.completed` agent messages (the last is the answer), MCP tool calls
    * with their results, reasoning; `turn.completed.usage`; `turn.failed` and `error` messages.
    */
  def parse(lines: List[CliProcess.Run.Lines]): CliOutcome =
    val events = lines.map(_.value.trim).filter(_.startsWith("{")).flatMap(l => parser.parse(l).toOption)
    val entries = Vector.newBuilder[ConversationEntry]
    val calls = Vector.newBuilder[ToolCallRecord]
    // accumulators of ONE parse, over the events in order
    var finalText: Option[CliOutcome.FinalText] = None
    var usage = TokenUsage.Zero
    var error: Option[Problem] = None
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
              finalText = Some(CliOutcome.FinalText(t))
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
          error = c
            .downField("error")
            .downField("message")
            .as[String]
            .toOption
            .orElse(Some("turn failed"))
            .map(Problem(_))
        case "error" => error = error.orElse(c.downField("message").as[String].toOption.map(Problem(_)))
        case _       => ()
    }
    CliOutcome(entries.result(), calls.result(), finalText, usage, error, CliOutcome.Turns(turns))

  def client(cfg: CodexConfig, rt: CliRuntime, ref: ModelRef): Either[Problem, LlmClient] =
    if ref.provider != ProviderKind.Codex then Left(Problem(s"${ref.wire} is not a codex model"))
    else if !rt.loopback then
      Left(
        Problem(
          "codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan"
        )
      )
    else if cfg.command.value.trim.isEmpty then Left(Problem("agents.providers.codex.command is empty"))
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
    type Argument = Argument.T
    object Argument extends Newtype[String, Pure]
    type ExitCode = ExitCode.T
    object ExitCode extends Newtype[Int, Pure]

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
        deleted: Report.Deleted = Report.Deleted(0),
        freedBytes: ByteCount = ByteCount(0L),
        vacuumed: List[(Report.VacuumedFile, ByteCount, ByteCount)] = Nil,
        problems: List[Problem] = Nil
    ):
      def summary: Report.Summary = Report.Summary(skipped.fold {
        val v = vacuumed.map((n, b, a) => s"$n ${Backup.humanBytes(b)} → ${Backup.humanBytes(a)}")
        (List(
          s"${Plural.count(Plural.Count(deleted.value.toLong), Plural.Noun.Singular("transcript"))} deleted (${Backup.humanBytes(freedBytes)})"
        ) ++
          Option.when(v.nonEmpty)(s"vacuumed ${v.mkString(", ")}") ++ problems.map(_.value)).mkString("; ")
      }(_.value))

    object Report:
      type Summary = Summary.T
      object Summary extends Newtype[String, Pure]
      type VacuumedFile = VacuumedFile.T
      object VacuumedFile extends Newtype[String, Pure]

      type Skipped = Problem
      val Skipped: Problem.type = Problem
      type Deleted = Deleted.T
      object Deleted extends Newtype[Int, Pure]

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
                FileInfo.Rel(home.relativize(p).toString.replace('\\', '/')),
                FileInfo.Bytes(Files.size(p)),
                FileInfo.ModifiedMs(Files.getLastModifiedTime(p).toMillis)
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
        nowMs: CodexRetention.NowMs = CodexRetention.NowMs(System.currentTimeMillis()),
        run: List[Argument] => Either[Problem, ExitCode] = runCommand
    ): Report =
      val w = lock(home).writeLock()
      if !Files.isDirectory(home) then Report(skipped = Some(Problem(s"no Codex home at $home")))
      else if !w.tryLock() then Report(skipped = Some(Problem("a Codex run is using the home — not now")))
      else
        try
          val problems = List.newBuilder[Problem]
          val files = CodexRetention.SessionDirs.flatMap(d => filesUnder(home, home.resolve(d.value)))
          val gone =
            CodexRetention.stale(files, nowMs, CodexRetention.KeepDays(cfg.keepSessionsDays.value)).filter { f =>
              scala.util
                .Try(Files.deleteIfExists(home.resolve(f.rel.value)))
                .fold(e => { problems += Problem(s"${f.rel}: ${e.getMessage}"); false }, identity)
            }
          CodexRetention.SessionDirs.foreach(d => pruneEmpty(home.resolve(d.value)))
          val vacuumed =
            if !cfg.vacuum.value then Nil
            else
              val names = Option(home.toFile.list()).toList.flatten.map(CodexRetention.FileName(_))
              CodexRetention.sqliteFiles(names).flatMap { n =>
                val f = home.resolve(n.value)
                val before = Files.size(f)
                run(List(Argument(cfg.sqlite.value), Argument(f.toString), Argument("VACUUM;"))) match
                  case Right(c) if c.value == 0 =>
                    Some((Report.VacuumedFile(n.value), ByteCount(before), ByteCount(Files.size(f))))
                  case Right(c) => problems += Problem(s"$n: ${cfg.sqlite} exit $c"); None
                  case Left(e)  => problems += Problem(s"$n: $e"); None
              }
          Report(
            None,
            Report.Deleted(gone.size),
            ByteCount(gone.map(_.bytes.value).sum),
            vacuumed,
            problems.result()
          )
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
    def runCommand(argv: List[Argument]): Either[Problem, ExitCode] =
      scala.util
        .Try {
          val p =
            ProcessBuilder(argv.map(_.value).asJava)
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start()
          if p.waitFor(10, TimeUnit.MINUTES) then Right(ExitCode(p.exitValue()))
          else { p.destroyForcibly(); Left(Problem(s"${argv.head} did not finish in 10 minutes")) }
        }
        .toEither
        .left
        .map(e => Problem(s"${argv.headOption.fold("")(_.value)}: ${e.getMessage}"))
        .flatten

    /** After a step: a pass on a virtual thread when one is due ([[CodexRetention.due]]). */
    def maybeRetain(
        home: Path,
        cfg: CodexConfig,
        nowMs: CodexRetention.NowMs = CodexRetention.NowMs(System.currentTimeMillis())
    ): Unit =
      val last = lastPass.computeIfAbsent(key(home), _ => AtomicLong(0L))
      val prev = last.get()
      if CodexRetention.due(
          Option.when(prev > 0)(CodexRetention.LastMs(prev)),
          nowMs,
          CodexRetention.EveryHours(cfg.retentionEveryHours.value)
        ) && last.compareAndSet(
          prev,
          nowMs.value
        )
      then
        val _ = Thread.ofVirtual().name("codex-retention").start { () =>
          val r = retain(home, cfg)
          if r.skipped.isDefined then last.set(prev) // try again after the next step
          scribe.info(s"codex home retention ($home): ${r.summary}")
        }
