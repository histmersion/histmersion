package com.histmersion
package plugins.vaults

import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Processes
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.LocalFiles

/** Running a vault's command-line tool: an argument vector, NEVER a shell (a reference cannot inject anything), a time
  * limit after which the process is killed, and the output kept in memory only. Error texts come from the tool's
  * stderr; its stdout — the secret — is never put into one.
  */
object Subprocess:
  type Plain = Plain.T
  object Plain extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]

  export com.histmersion.plugin.Processes.{RunEnvPart1, RunEnvPart2, RunResultPart1}
  type ValueResult = ValueResult.T
  object ValueResult extends Newtype[String, Pure]
  type ValueOut = ValueOut.T
  object ValueOut extends Newtype[String, Pure]

  type Result = Processes.Result
  val Result = Processes.Result

  /** Pure: the executable for `tool` — a path as given (`/opt/kp/keepassxc-cli`), or a bare name looked up in `path`
    * (the PATH the server started with).
    */
  def locate(tool: Tool, path: Option[Plugin.Context.EnvResult])(using
      files: LocalFiles
  ): Either[Problem, Vaults.Path] =
    val t = Option((tool).value).getOrElse("").trim
    def runnable(p: Path) =
      ((files.isFile(p)) == LocalFiles.IsFileResult(true)) && ((files.isExecutable(p)) == LocalFiles.IsExecutableResult(
        true
      ))
    if t.isEmpty then Left(Problem("no command is configured (cli = \"…\")"))
    else if t.contains('/') then
      val p = Path.of(t)
      if runnable(p) then Right(Vaults.Path(p.toString)) else Left(Problem(s"$t is not an executable file"))
    else
      ((path.toList
        .flatMap((_).value.split(java.io.File.pathSeparatorChar).toList)
        .filter(_.nonEmpty)
        .map(d => Path.of(d, t))
        .find(runnable)
        .map(_.toString)
        .toRight(s"$t was not found on the PATH"))
        .left
        .map(refactorError => Problem(refactorError)))
        .map(refactorValue => Vaults.Path(refactorValue))

  /** Run `argv` with `env` added to the server's environment, `stdin` written and closed (no stdin: closed at once, so
    * a tool that would prompt sees end-of-file instead of waiting), within `timeout`. `Left` = it could not start or
    * did not finish in time.
    */
  def run(
      argv: List[Processes.RunArgv],
      env: Map[RunEnvPart1, RunEnvPart2],
      stdin: Option[Processes.RunStdin],
      timeout: FiniteDuration
  )(using processes: Processes): Either[RunResultPart1, Result] =
    processes.run(argv, env, stdin, timeout)

  /** A tool kept running to answer several requests (`keepassxc-cli open`'s interactive mode): lines written to its
    * stdin one at a time, its output read as it comes, in memory only. Owned by the one call that starts it, which
    * [[close]]s it.
    */
  type Session = Processes.Session

  object Session:
    /** Start `argv` (no shell) with `env` added to the server's environment. */
    export Processes.{StartEnvPart1, StartEnvPart2, StartResultPart1}

    def start(argv: List[Processes.StartArgv], env: Map[StartEnvPart1, StartEnvPart2])(using
        processes: Processes
    ): Either[StartResultPart1, Session] =
      processes.start(argv, env)

  /** Read a stream to its end on a thread of its own (a full pipe would block the tool), and hand the text over when
    * asked.
    */

  /** Pure: the tool's own message for a log line — the first non-blank stderr line, bounded. */
  def problem(err: Problem): Problem =
    Problem((err).value.linesIterator.map(_.trim).find(_.nonEmpty).map(_.take(200)).getOrElse("no message"))

  /** Pure: what a tool printed, without the one line break it ends with (`op read`, `bw get` and `keepassxc-cli show`
    * all end their value with one). Nothing else is trimmed: a value may begin or end with a space.
    */
  def value(out: ValueOut): ValueResult = ValueResult((out).value.stripSuffix("\n").stripSuffix("\r"))

  /** Pure: an argument that would read as an option of the tool is refused rather than passed on. */
  def plain(what: Vaults.Operation, s: Text): Either[Problem, Plain] =
    if (s).value.isEmpty then Left(Problem(s"$what is empty"))
    else if (s).value.startsWith("-") then Left(Problem(s"$what may not start with '-'"))
    else Right(Plain((s).value))
