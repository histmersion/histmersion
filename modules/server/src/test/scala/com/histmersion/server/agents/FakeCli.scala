package com.histmersion
package server.agents
import com.histmersion.util.problem.Problem

import com.histmersion.domain.automation.agents.PoolSink

import com.histmersion.config.AppConfig
import com.histmersion.api.automation.PlanQuota.{PoolId, Reading}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.automation.agents.PlanQuotaRepo
import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.api.automation.PlanQuota

/** A fake CLI (`claude`, `codex`, `opencode`): a shell script that records what it was given — its arguments one per
  * line, its stdin, the `--output-schema` file, the environment values a test asserts on, whether its working directory
  * was empty — then replays a recorded event stream and exits. With `sleepAfter` it then hangs, so a test can see the
  * process stopped (the quota guard) or killed (a timeout). No network, no login, nothing spent.
  */
object FakeCli:

  final case class Fake(script: Path, dir: Path):
    def args: List[String] = Files.readAllLines(dir.resolve("args")).asScala.toList
    def stdin: String = Files.readString(dir.resolve("stdin"))
    def env: Map[String, String] =
      Files
        .readAllLines(dir.resolve("env"))
        .asScala
        .toList
        .flatMap(l =>
          l.split("=", 2) match
            case Array(k, v) => Some(k -> v)
            case _           => None
        )
        .toMap
    def cwdWasEmpty: Boolean = Files.readString(dir.resolve("cwd")).trim.isEmpty
    def after(flag: String): String = args(args.indexOf(flag) + 1)

    /** The file `--output-schema` named, as the CLI read it (codex). */
    def outputSchema: Option[String] =
      Option.when(Files.isRegularFile(dir.resolve("output-schema")))(Files.readString(dir.resolve("output-schema")))

  /** The environment values recorded (an unset one reads `<unset>`). */
  val Watched = List(
    "ANTHROPIC_API_KEY",
    "ANTHROPIC_AUTH_TOKEN",
    "OPENAI_API_KEY",
    "CODEX_API_KEY",
    "CODEX_HOME",
    "HISTMERSION_RUN_TOKEN",
    "HISTMERSION_QUOTA_URL",
    "ENABLE_CLAUDEAI_MCP_SERVERS",
    "CLAUDE_CODE_MAX_OUTPUT_TOKENS",
    "XDG_CONFIG_HOME",
    "OPENCODE_CONFIG_DIR",
    "OPENCODE_DISABLE_CLAUDE_CODE",
    "CLAUDE_CONFIG_DIR",
    "CLAUDE_CODE_DISABLE_ARTIFACT"
  )

  def apply(name: String, out: List[String], code: Int = 0, sleepAfter: Int = 0): Fake =
    val dir = Files.createTempDirectory(s"hm-fake-$name-")
    val outFile = Files.write(dir.resolve("out.jsonl"), out.asJava)
    val script = dir.resolve(name)
    val envLines = Watched.map(k => s"""echo "$k=$${$k:-<unset>}"""").mkString("\n")
    Files.writeString(
      script,
      s"""#!/bin/bash
         |for a in "$$@"; do printf '%s\\n' "$$a"; done > '$dir/args'
         |prev=""; for a in "$$@"; do [ "$$prev" = "--output-schema" ] && cp "$$a" '$dir/output-schema'; prev="$$a"; done
         |ls -A > '$dir/cwd'
         |{
         |$envLines
         |} > '$dir/env'
         |cat > '$dir/stdin'
         |cat '$outFile'
         |${if sleepAfter > 0 then s"sleep $sleepAfter" else ""}
         |exit $code
         |""".stripMargin
    )
    script.toFile.setExecutable(true)
    Fake(script, dir)

  /** A fake that replays `outs(i)` on its i-th invocation (the last one again after that) — a model that answers
    * differently when asked again. Each invocation's stdin is kept ([[stdinOf]]); the arguments, environment and
    * working directory recorded are the last invocation's.
    */
  def sequence(name: String, outs: List[List[String]]): Fake =
    val dir = Files.createTempDirectory(s"hm-fake-$name-")
    outs.zipWithIndex.foreach((o, i) => Files.write(dir.resolve(s"out-${i + 1}.jsonl"), o.asJava))
    val script = dir.resolve(name)
    val envLines = Watched.map(k => s"""echo "$k=$${$k:-<unset>}"""").mkString("\n")
    Files.writeString(
      script,
      s"""#!/bin/bash
         |n=$$(( $$(cat '$dir/count' 2>/dev/null || echo 0) + 1 )); echo "$$n" > '$dir/count'
         |out='$dir/out-'"$$n"'.jsonl'; [ -f "$$out" ] || out='$dir/out-${outs.size}.jsonl'
         |for a in "$$@"; do printf '%s\\n' "$$a"; done > '$dir/args'
         |ls -A > '$dir/cwd'
         |{
         |$envLines
         |} > '$dir/env'
         |cat > '$dir/stdin-'"$$n"; cp '$dir/stdin-'"$$n" '$dir/stdin'
         |cat "$$out"
         |""".stripMargin
    )
    script.toFile.setExecutable(true)
    Fake(script, dir)

  /** How often a [[sequence]] fake was invoked, and what its n-th invocation (from 1) read on stdin. */
  def invocations(f: Fake): Int =
    Option
      .when(Files.isRegularFile(f.dir.resolve("count")))(Files.readString(f.dir.resolve("count")).trim.toInt)
      .getOrElse(0)
  def stdinOf(f: Fake, n: Int): String = Files.readString(f.dir.resolve(s"stdin-$n"))

  /** A server configuration with the runtimes under `dir` (and the given overrides). */
  def app(dir: Path, extra: (String, String)*): AppConfig =
    AppConfig.load(
      org.ekrich.config.ConfigFactory.parseMap(
        (Map(
          "HISTMERSION_AGENTS_RUNTIMES_DIR" -> dir.toString,
          "HISTMERSION_BIND" -> "127.0.0.1",
          "HISTMERSION_PUBLIC_URL" -> "http://127.0.0.1:18000"
        ) ++ extra).asJava
      )
    )

  /** A pool sink in memory: records readings and blocks while `block` says so. */
  final class Sink(var block: Option[(PlanQuota.Rationale, Option[PlanQuotaRepo.EpochMillis])] = None) extends PoolSink:
    val recorded = java.util.concurrent.ConcurrentLinkedQueue[(PoolId, Reading)]()
    @volatile var refreshed = 0 // how often a client asked for a fresh read
    def record(p: PoolId, rs: List[Reading]): Unit = rs.foreach(r => recorded.add(p -> r))
    def blocked(
        p: PoolId,
        model: Option[AgentsControllers.ModelName]
    ): Option[(PlanQuota.Rationale, Option[PlanQuotaRepo.EpochMillis])] = block
    def refresh(now: PlanQuotaRepo.EpochMillis): Unit = refreshed += 1
