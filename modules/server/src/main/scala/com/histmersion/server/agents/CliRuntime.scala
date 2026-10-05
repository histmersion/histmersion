package com.histmersion.server.agents

import com.histmersion.core.AppConfig

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import scala.jdk.CollectionConverters.*

/** What the CLI runtimes (`claude-code`, `codex`, `opencode`) share: the definitions, the generated
  * trees they launch, the run tokens their steps reach the agent MCP endpoint with, and the plan pools
  * their streams report to. One per server. */
final class CliRuntime(val app: AppConfig, val definitions: AgentDefinitions):
  val tokens: RunTokens = RunTokens()
  val trees: AgentTrees = AgentTrees(app, definitions)
  /** Wired by [[AgentsHandlers]] (it needs the DataSource); `var` only for that late attachment. */
  @volatile var pools: Option[PoolSink] = None

  /** `127.x`, `::1`, `localhost`: nobody else can reach the server, so nobody else can spend a plan. */
  def loopback: Boolean = ClaudeCodeEnv.isLoopback(app.server.host)

  /** The agent MCP endpoint as a CLI reaches it (one URL; the run token decides the tools). */
  def agentMcpUrl: String = trees.settings("").agentMcpUrl
  def quotaUrl: String = trees.settings("").quotaUrl

  /** A token for one step attempt, alive a little longer than the CLI may run. */
  def grant(call: LlmCall[?], agent: Option[AgentDef], timeoutSeconds: Int): Option[RunGrant] =
    agent.map(a => tokens.issue(a, call.scope, (math.max(1, timeoutSeconds).toLong + 60L) * 1000L))

object CliRuntime:
  def apply(app: AppConfig): CliRuntime =
    new CliRuntime(app, AgentDefinitions(Prompts(app.agents.promptsDir)))

/** One CLI process per step attempt: in an empty temporary working directory (no project
  * `AGENTS.md`, `.codex/`, `.claude/` or `opencode.json` is read), the user message on stdin, stdout
  * read LINE BY LINE as it arrives (a quota guard can stop the process mid-step), stderr kept for the
  * error, a timeout, and the whole process tree killed on cancel. */
object CliProcess:

  /** What a run printed; `stopped` = `onLine` asked for it to end (the quota guard), with why. */
  final case class Run(lines: List[String], exit: Int, stderr: String, stopped: Option[String])

  /** Raised when the executable cannot be started at all. */
  def cannotStart(command: String, e: java.io.IOException, hint: String): java.io.IOException =
    java.io.IOException(s"cannot run '$command' (${e.getMessage}) — $hint")

  def run(argv: List[String], dir: Path, env: java.util.Map[String, String] => Unit, stdin: String, timeoutSeconds: Int,
      hint: String, onLine: String => Option[String] = _ => None): Run =
    val pb = ProcessBuilder(argv.asJava).directory(dir.toFile)
    env(pb.environment())
    val p = try pb.start() catch case e: java.io.IOException => throw cannotStart(argv.head, e, hint)
    val stdout = ConcurrentLinkedQueue[String]()
    val stderr = StringBuilder()
    val stopped = java.util.concurrent.atomic.AtomicReference[Option[String]](None) // set once by the reader thread
    val tOut = Thread.ofVirtual().start { () =>
      p.inputReader(StandardCharsets.UTF_8).lines().forEach { l =>
        val _ = stdout.add(l)
        if stopped.get().isEmpty then
          onLine(l).foreach { why =>
            stopped.set(Some(why))
            kill(p)
          }
      }
    }
    val tErr = Thread.ofVirtual().start { () =>
      val s = String(p.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)
      stderr.synchronized { val _ = stderr.append(s.takeRight(4000)) }
    }
    try
      val w = p.outputWriter(StandardCharsets.UTF_8)
      try w.write(stdin)
      catch case _: java.io.IOException => () // the CLI may close its stdin early (a stopped run)
      finally
        try w.close() catch case _: java.io.IOException => ()
      if !p.waitFor(math.max(1, timeoutSeconds).toLong, TimeUnit.SECONDS) then
        kill(p)
        throw java.util.concurrent.TimeoutException(s"${Path.of(argv.head).getFileName} did not finish within $timeoutSeconds s")
      tOut.join(); tErr.join()
      Run(stdout.asScala.toList, p.exitValue(), stderr.synchronized(stderr.toString).trim, stopped.get())
    catch
      case e: InterruptedException =>
        kill(p) // a cancelled run: the CLI and whatever it started go with it
        throw e
    finally if p.isAlive then kill(p)

  def kill(p: Process): Unit =
    p.descendants().forEach(d => { val _ = d.destroy() })
    p.destroy()
    if !p.waitFor(5, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }

  /** A scratch working directory, removed with everything in it afterwards. */
  def withDir[A](prefix: String)(f: Path => A): A =
    val dir = Files.createTempDirectory(prefix)
    try f(dir)
    finally
      val files = Files.walk(dir)
      try files.sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })
      finally files.close()

  /** An owner-only file (it may carry a run token). */
  def privateFile(f: Path, text: String): Path =
    Files.writeString(f, text, StandardCharsets.UTF_8)
    try { val _ = Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
    catch case _: UnsupportedOperationException => ()
    f
