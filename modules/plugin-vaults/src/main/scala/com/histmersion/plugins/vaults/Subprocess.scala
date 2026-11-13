package com.histmersion
package plugins.vaults
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.EnvVar

import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.github.iltotore.iron.constraint.any.{Not, Pure}
import io.github.iltotore.iron.constraint.collection.MinLength
import io.github.iltotore.iron.constraint.string.StartWith
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
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]

  type Value = Value.T
  object Value extends Newtype[String, Pure]

  type Result = Processes.Result
  val Result = Processes.Result

  /** Pure: the executable for `tool` — a path as given (`/opt/kp/keepassxc-cli`), or a bare name looked up in `path`
    * (the PATH the server started with).
    */
  def locate(tool: Tool, path: Option[EnvVar.Value])(using
      files: LocalFiles
  ): Either[Problem, Vaults.Path] =
    val t = Option((tool).value).getOrElse("").trim
    def runnable(p: Path) =
      files.isFile(p) && files.isExecutable(p)
    if t.isEmpty then Left(Problem.Invalid.Missing(Problem.Field("cli")))
    else if t.contains('/') then
      val p = Path.of(t)
      if runnable(p) then Right(Vaults.Path(p.toString))
      else Left(Problem.Io.Wrong(Problem.Io.Path(s"$t"), Problem.Io.Kind.Executable))
    else
      path.toList
        .flatMap((_).value.split(java.io.File.pathSeparatorChar).toList)
        .filter(_.nonEmpty)
        .map(d => Path.of(d, t))
        .find(runnable)
        .map(p => Vaults.Path(p.toString))
        .toRight(Problem.Io.Wrong(Problem.Io.Path(s"$t"), Problem.Io.Kind.OnPath))

  /** Run `argv` with `env` added to the server's environment, `stdin` written and closed (no stdin: closed at once, so
    * a tool that would prompt sees end-of-file instead of waiting), within `timeout`. `Left` = it could not start or
    * did not finish in time.
    */
  def run(
      argv: List[Processes.RunArgv],
      env: Map[EnvVar.Name, EnvVar.Value],
      stdin: Option[Processes.RunStdin],
      timeout: FiniteDuration
  )(using processes: Processes): Either[Processes.Failure, Result] =
    processes.run(argv, env, stdin, timeout)

  /** A tool kept running to answer several requests (`keepassxc-cli open`'s interactive mode): lines written to its
    * stdin one at a time, its output read as it comes, in memory only. Owned by the one call that starts it, which
    * [[close]]s it.
    */
  type Session = Processes.Session

  object Session:
    /** Start `argv` (no shell) with `env` added to the server's environment. */
    def start(argv: List[Processes.StartArgv], env: Map[EnvVar.Name, EnvVar.Value])(using
        processes: Processes
    ): Either[Problem, Session] =
      processes.start(argv, env)

  /** Read a stream to its end on a thread of its own (a full pipe would block the tool), and hand the text over when
    * asked.
    */

  extension (exit: Result.Exit)
    /** The tool's exit status, as a problem names it. */
    def code: Problem.Io.ExitCode = Problem.Io.ExitCode(exit.value)

  extension (err: Result.Err)
    /** Pure: the tool's own message — the first non-blank line it wrote to its standard error, bounded. */
    def firstLine: External.Stderr =
      External.Stderr.tail(s"$err".linesIterator.map(_.trim).find(_.nonEmpty).map(_.take(200)).getOrElse("no message"))

  /** Pure: what a tool printed, without the one line break it ends with (`op read`, `bw get` and `keepassxc-cli show`
    * all end their value with one). Nothing else is trimmed: a value may begin or end with a space.
    */
  def value(out: Result.Out): Value = Value(out.value.stripSuffix("\n").stripSuffix("\r"))

  /** Pure: an argument that would read as an option of the tool is refused rather than passed on. */
  def plain(what: Vaults.Operation, s: Text): Either[Problem, Plain] =
    Argument
      .checked((s).value)
      .map(a => Plain(s"$a"))
      .left
      .map(refused => Problem.Invalid.malformed(refused).within(Problem.Field(s"$what")))

  /** An argument handed to a vault's tool: something, and nothing the tool would read as an option. */
  type Argument = Argument.T
  object Argument extends Newtype[String, MinLength[1] & Not[StartWith["-"]]]
