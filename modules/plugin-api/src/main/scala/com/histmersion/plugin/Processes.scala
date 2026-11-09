package com.histmersion
package plugin

import scala.concurrent.duration.FiniteDuration
import io.github.iltotore.iron.constraint.any.Pure
import Processes.Failure

/** Argument-vector execution supplied by the host. Implementations never interpret argv through a shell. stdout and
  * stderr remain separate; stdout may contain secrets and must not be included in errors.
  */
trait Processes:
  def run(
      argv: List[Processes.RunArgv],
      env: Map[EnvVar.Name, EnvVar.Value],
      stdin: Option[Processes.RunStdin],
      timeout: FiniteDuration
  ): Either[Failure, Processes.Result]
  def start(
      argv: List[Processes.StartArgv],
      env: Map[EnvVar.Name, EnvVar.Value]
  ): Either[Problem, Processes.Session]

  /** Capture a command without a time limit, closing stdin immediately. The caller explicitly chooses this mode. */
  def capture(argv: List[Processes.CaptureArgv]): Either[Problem, Processes.Result]

object Processes:
  type RunArgv = RunArgv.T
  object RunArgv extends Newtype[String, Pure]
  type RunStdin = RunStdin.T
  object RunStdin extends Newtype[String, Pure]
  type StartArgv = StartArgv.T
  object StartArgv extends Newtype[String, Pure]
  type CaptureArgv = CaptureArgv.T
  object CaptureArgv extends Newtype[String, Pure]

  /** Why [[Processes.run]] gave no [[Result]]: the tool did not finish within the time limit (it was killed), or it
    * could not be started. A tool that ran and exited non-zero is a [[Result]], not a failure.
    */
  enum Failure derives CanEqual:
    case TimedOut(tool: Tool, after: FiniteDuration)
    case NotStarted(why: Problem)

    /** The sentence for a person. */
    def problem: Problem = this match
      case TimedOut(tool, after) => Problem(s"$tool did not answer within ${after.toSeconds} s")
      case NotStarted(why)       => why

  /** The file name of a tool. */
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]

  final case class Result(exit: Result.Exit, out: Result.Out, err: Result.Err)

  object Result:
    type Exit = Exit.T
    object Exit extends Newtype[Int, Pure]
    type Out = Out.T
    object Out extends Newtype[String, Pure]
    type Err = Err.T
    object Err extends Newtype[String, Pure]

  /** Owned by the caller, which must close it. Output is kept in memory and never logged by the implementation. */
  trait Session:
    def out: Result.Out
    def err: Result.Err
    def alive: Boolean
    def send(line: Session.SendLine): Either[Problem, Unit]
    def awaitOut(timeout: FiniteDuration)(
        ready: Result.Out => Boolean
    ): Boolean
    def awaitErr(length: Session.AwaitErrLength, timeout: FiniteDuration): Unit
    def close(timeout: FiniteDuration): Option[Result.Exit]
    def exit(timeout: FiniteDuration): Option[Result.Exit]

  object Session:
    type SendLine = SendLine.T
    object SendLine extends Newtype[String, Pure]
    type AwaitErrLength = AwaitErrLength.T
    object AwaitErrLength extends Newtype[Int, Pure]

  object Unavailable extends Processes:
    export Processes.{CaptureArgv, RunArgv, RunStdin, StartArgv}

    def run(
        argv: List[RunArgv],
        env: Map[EnvVar.Name, EnvVar.Value],
        stdin: Option[RunStdin],
        timeout: FiniteDuration
    ): Either[Failure, Result] =
      Left(Failure.NotStarted(Problem("this host supplies no processes")))
    def start(argv: List[StartArgv], env: Map[EnvVar.Name, EnvVar.Value]): Either[Problem, Session] =
      Left(Problem("this host supplies no processes"))
    def capture(argv: List[CaptureArgv]): Either[Problem, Result] = Left(
      Problem("this host supplies no processes")
    )
