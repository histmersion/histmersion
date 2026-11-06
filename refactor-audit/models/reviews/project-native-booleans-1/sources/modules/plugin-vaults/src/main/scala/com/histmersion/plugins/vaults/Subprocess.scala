package com.histmersion.plugins.vaults

import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Running a vault's command-line tool: an argument vector, NEVER a shell (a reference cannot inject anything), a time
  * limit after which the process is killed, and the output kept in memory only. Error texts come from the tool's
  * stderr; its stdout — the secret — is never put into one.
  */
object Subprocess:

  type LocateResultPart1 = LocateResultPart1.T
  object LocateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateResultPart2 = LocateResultPart2.T
  object LocateResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateTool = LocateTool.T
  object LocateTool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocatePath = LocatePath.T
  object LocatePath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunArgv = RunArgv.T
  object RunArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunEnvPart1 = RunEnvPart1.T
  object RunEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunEnvPart2 = RunEnvPart2.T
  object RunEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStdin = RunStdin.T
  object RunStdin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemResult = ProblemResult.T
  object ProblemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemErr = ProblemErr.T
  object ProblemErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueResult = ValueResult.T
  object ValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueOut = ValueOut.T
  object ValueOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainResultPart1 = PlainResultPart1.T
  object PlainResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainResultPart2 = PlainResultPart2.T
  object PlainResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainWhat = PlainWhat.T
  object PlainWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainS = PlainS.T
  object PlainS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Result = com.histmersion.plugin.Processes.Result
  val Result = com.histmersion.plugin.Processes.Result

  /** Pure: the executable for `tool` — a path as given (`/opt/kp/keepassxc-cli`), or a bare name looked up in `path`
    * (the PATH the server started with).
    */
  def locate(tool: com.histmersion.plugins.vaults.Subprocess.LocateTool, path: Option[com.histmersion.plugins.vaults.Subprocess.LocatePath])(using files: com.histmersion.plugin.LocalFiles): Either[com.histmersion.plugins.vaults.Subprocess.LocateResultPart1, com.histmersion.plugins.vaults.Subprocess.LocateResultPart2] =
    val t = Option((tool).value).getOrElse("").trim
    def runnable(p: Path) = ((files.isFile(p)) == _root_.com.histmersion.plugin.LocalFiles.IsFileResult(true)) && files.isExecutable(p)
    if t.isEmpty then Left(_root_.com.histmersion.plugins.vaults.Subprocess.LocateResultPart1("no command is configured (cli = \"…\")"))
    else if t.contains('/') then
      val p = Path.of(t)
      if runnable(p) then Right(_root_.com.histmersion.plugins.vaults.Subprocess.LocateResultPart2(p.toString)) else Left(_root_.com.histmersion.plugins.vaults.Subprocess.LocateResultPart1(s"$t is not an executable file"))
    else
      ((path.toList
        .flatMap((_).value.split(java.io.File.pathSeparatorChar).toList)
        .filter(_.nonEmpty)
        .map(d => Path.of(d, t))
        .find(runnable)
        .map(_.toString)
        .toRight(s"$t was not found on the PATH")).left.map(refactorError => _root_.com.histmersion.plugins.vaults.Subprocess.LocateResultPart1(refactorError))).map(refactorValue => _root_.com.histmersion.plugins.vaults.Subprocess.LocateResultPart2(refactorValue))

  /** Run `argv` with `env` added to the server's environment, `stdin` written and closed (no stdin: closed at once, so
    * a tool that would prompt sees end-of-file instead of waiting), within `timeout`. `Left` = it could not start or
    * did not finish in time.
    */
  def run(
      argv: List[com.histmersion.plugins.vaults.Subprocess.RunArgv],
      env: Map[com.histmersion.plugins.vaults.Subprocess.RunEnvPart1, com.histmersion.plugins.vaults.Subprocess.RunEnvPart2],
      stdin: Option[com.histmersion.plugins.vaults.Subprocess.RunStdin],
      timeout: FiniteDuration
  )(using processes: com.histmersion.plugin.Processes): Either[com.histmersion.plugins.vaults.Subprocess.RunResultPart1, Result] =
    processes.run(argv, env, stdin, timeout)

  /** A tool kept running to answer several requests (`keepassxc-cli open`'s interactive mode): lines written to its
    * stdin one at a time, its output read as it comes, in memory only. Owned by the one call that starts it, which
    * [[close]]s it.
    */
  type Session = com.histmersion.plugin.Processes.Session

  object Session:
    /** Start `argv` (no shell) with `env` added to the server's environment. */
    type StartResultPart1 = StartResultPart1.T
    object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartArgv = StartArgv.T
    object StartArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartEnvPart1 = StartEnvPart1.T
    object StartEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartEnvPart2 = StartEnvPart2.T
    object StartEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def start(argv: List[com.histmersion.plugins.vaults.Subprocess.Session.StartArgv], env: Map[com.histmersion.plugins.vaults.Subprocess.Session.StartEnvPart1, com.histmersion.plugins.vaults.Subprocess.Session.StartEnvPart2])(using processes: com.histmersion.plugin.Processes): Either[com.histmersion.plugins.vaults.Subprocess.Session.StartResultPart1, Session] =
      processes.start(argv, env)

  /** Read a stream to its end on a thread of its own (a full pipe would block the tool), and hand the text over when
    * asked.
    */


  /** Pure: the tool's own message for a log line — the first non-blank stderr line, bounded. */
  def problem(err: com.histmersion.plugins.vaults.Subprocess.ProblemErr): com.histmersion.plugins.vaults.Subprocess.ProblemResult =
    _root_.com.histmersion.plugins.vaults.Subprocess.ProblemResult((err).value.linesIterator.map(_.trim).find(_.nonEmpty).map(_.take(200)).getOrElse("no message"))

  /** Pure: what a tool printed, without the one line break it ends with (`op read`, `bw get` and `keepassxc-cli show`
    * all end their value with one). Nothing else is trimmed: a value may begin or end with a space.
    */
  def value(out: com.histmersion.plugins.vaults.Subprocess.ValueOut): com.histmersion.plugins.vaults.Subprocess.ValueResult = _root_.com.histmersion.plugins.vaults.Subprocess.ValueResult((out).value.stripSuffix("\n").stripSuffix("\r"))

  /** Pure: an argument that would read as an option of the tool is refused rather than passed on. */
  def plain(what: com.histmersion.plugins.vaults.Subprocess.PlainWhat, s: com.histmersion.plugins.vaults.Subprocess.PlainS): Either[com.histmersion.plugins.vaults.Subprocess.PlainResultPart1, com.histmersion.plugins.vaults.Subprocess.PlainResultPart2] =
    if (s).value.isEmpty then Left(_root_.com.histmersion.plugins.vaults.Subprocess.PlainResultPart1(s"$what is empty"))
    else if (s).value.startsWith("-") then Left(_root_.com.histmersion.plugins.vaults.Subprocess.PlainResultPart1(s"$what may not start with '-'"))
    else Right(s)
