package com.histmersion
package plugin

import scala.concurrent.duration.FiniteDuration
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Argument-vector execution supplied by the host. Implementations never interpret argv through a shell.
  * stdout and stderr remain separate; stdout may contain secrets and must not be included in errors.
  */
trait Processes:
  def run(argv: List[Processes.RunArgv], env: Map[Processes.RunEnvPart1, Processes.RunEnvPart2], stdin: Option[Processes.RunStdin], timeout: FiniteDuration): Either[Processes.RunResultPart1, Processes.Result]
  def start(argv: List[Processes.StartArgv], env: Map[Processes.StartEnvPart1, Processes.StartEnvPart2]): Either[Processes.StartResultPart1, Processes.Session]

  /** Capture a command without a time limit, closing stdin immediately. The caller explicitly chooses this mode. */
  def capture(argv: List[Processes.CaptureArgv]): Either[Processes.CaptureResultPart1, Processes.Result]

object Processes:
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends RefinedType[String, Pure]
  type RunArgv = RunArgv.T
  object RunArgv extends RefinedType[String, Pure]
  type RunEnvPart1 = RunEnvPart1.T
  object RunEnvPart1 extends RefinedType[String, Pure]
  type RunEnvPart2 = RunEnvPart2.T
  object RunEnvPart2 extends RefinedType[String, Pure]
  type RunStdin = RunStdin.T
  object RunStdin extends RefinedType[String, Pure]
  type StartResultPart1 = StartResultPart1.T
  object StartResultPart1 extends RefinedType[String, Pure]
  type StartArgv = StartArgv.T
  object StartArgv extends RefinedType[String, Pure]
  type StartEnvPart1 = StartEnvPart1.T
  object StartEnvPart1 extends RefinedType[String, Pure]
  type StartEnvPart2 = StartEnvPart2.T
  object StartEnvPart2 extends RefinedType[String, Pure]
  type CaptureResultPart1 = CaptureResultPart1.T
  object CaptureResultPart1 extends RefinedType[String, Pure]
  type CaptureArgv = CaptureArgv.T
  object CaptureArgv extends RefinedType[String, Pure]

  final case class Result(exit: Result.Exit, out: Result.Out, err: Result.Err)

  object Result:
    type Exit = Exit.T
    object Exit extends RefinedType[Int, Pure]
    type Out = Out.T
    object Out extends RefinedType[String, Pure]
    type Err = Err.T
    object Err extends RefinedType[String, Pure]

  /** Owned by the caller, which must close it. Output is kept in memory and never logged by the implementation. */
  trait Session:
    def out: Session.OutResult
    def err: Session.ErrResult
    def alive: Session.AliveResult
    def send(line: Session.SendLine): Either[Session.SendResultPart1, Unit]
    def awaitOut(timeout: FiniteDuration)(ready: Session.AwaitOutReadyArg1 => Session.AwaitOutReadyResult): Session.AwaitOutResult
    def awaitErr(length: Session.AwaitErrLength, timeout: FiniteDuration): Unit
    def close(timeout: FiniteDuration): Option[Session.CloseResult]
    def exit(timeout: FiniteDuration): Option[Session.ExitResult]

  object Session:
    type OutResult = OutResult.T
    object OutResult extends RefinedType[String, Pure]
    type ErrResult = ErrResult.T
    object ErrResult extends RefinedType[String, Pure]
    type AliveResult = AliveResult.T
    object AliveResult extends RefinedType[Boolean, Pure]
    type SendResultPart1 = SendResultPart1.T
    object SendResultPart1 extends RefinedType[String, Pure]
    type SendLine = SendLine.T
    object SendLine extends RefinedType[String, Pure]
    type AwaitOutResult = AwaitOutResult.T
    object AwaitOutResult extends RefinedType[Boolean, Pure]
    type AwaitOutReadyArg1 = AwaitOutReadyArg1.T
    object AwaitOutReadyArg1 extends RefinedType[String, Pure]
    type AwaitOutReadyResult = AwaitOutReadyResult.T
    object AwaitOutReadyResult extends RefinedType[Boolean, Pure]
    type AwaitErrLength = AwaitErrLength.T
    object AwaitErrLength extends RefinedType[Int, Pure]
    type CloseResult = CloseResult.T
    object CloseResult extends RefinedType[Int, Pure]
    type ExitResult = ExitResult.T
    object ExitResult extends RefinedType[Int, Pure]

  object Unavailable extends Processes:
    export Processes.{RunResultPart1, RunArgv, RunEnvPart1, RunEnvPart2, RunStdin, StartResultPart1, StartArgv, StartEnvPart1, StartEnvPart2, CaptureResultPart1, CaptureArgv}

    def run(argv: List[RunArgv], env: Map[RunEnvPart1, RunEnvPart2], stdin: Option[RunStdin], timeout: FiniteDuration): Either[RunResultPart1, Result] =
      Left(RunResultPart1("this host supplies no processes"))
    def start(argv: List[StartArgv], env: Map[StartEnvPart1, StartEnvPart2]): Either[StartResultPart1, Session] =
      Left(StartResultPart1("this host supplies no processes"))
    def capture(argv: List[CaptureArgv]): Either[CaptureResultPart1, Result] = Left(CaptureResultPart1("this host supplies no processes"))
