package com.histmersion.plugin

import scala.concurrent.duration.FiniteDuration

/** Argument-vector execution supplied by the host. Implementations never interpret argv through a shell.
  * stdout and stderr remain separate; stdout may contain secrets and must not be included in errors.
  */
trait Processes:
  def run(argv: List[com.histmersion.plugin.Processes.RunArgv], env: Map[com.histmersion.plugin.Processes.RunEnvPart1, com.histmersion.plugin.Processes.RunEnvPart2], stdin: Option[com.histmersion.plugin.Processes.RunStdin], timeout: FiniteDuration): Either[com.histmersion.plugin.Processes.RunResultPart1, Processes.Result]
  def start(argv: List[com.histmersion.plugin.Processes.StartArgv], env: Map[com.histmersion.plugin.Processes.StartEnvPart1, com.histmersion.plugin.Processes.StartEnvPart2]): Either[com.histmersion.plugin.Processes.StartResultPart1, Processes.Session]

  /** Capture a command without a time limit, closing stdin immediately. The caller explicitly chooses this mode. */
  def capture(argv: List[com.histmersion.plugin.Processes.CaptureArgv]): Either[com.histmersion.plugin.Processes.CaptureResultPart1, Processes.Result]

object Processes:
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
  type StartResultPart1 = StartResultPart1.T
  object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartArgv = StartArgv.T
  object StartArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartEnvPart1 = StartEnvPart1.T
  object StartEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartEnvPart2 = StartEnvPart2.T
  object StartEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureResultPart1 = CaptureResultPart1.T
  object CaptureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureArgv = CaptureArgv.T
  object CaptureArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Result(exit: Result.Exit, out: Result.Out, err: Result.Err)

  object Result:
    type Exit = Exit.T
    object Exit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Out = Out.T
    object Out extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Err = Err.T
    object Err extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Owned by the caller, which must close it. Output is kept in memory and never logged by the implementation. */
  trait Session:
    def out: com.histmersion.plugin.Processes.Session.OutResult
    def err: com.histmersion.plugin.Processes.Session.ErrResult
    def alive: com.histmersion.plugin.Processes.Session.AliveResult
    def send(line: com.histmersion.plugin.Processes.Session.SendLine): Either[com.histmersion.plugin.Processes.Session.SendResultPart1, Unit]
    def awaitOut(timeout: FiniteDuration)(ready: com.histmersion.plugin.Processes.Session.AwaitOutReadyArg1 => com.histmersion.plugin.Processes.Session.AwaitOutReadyResult): com.histmersion.plugin.Processes.Session.AwaitOutResult
    def awaitErr(length: com.histmersion.plugin.Processes.Session.AwaitErrLength, timeout: FiniteDuration): Unit
    def close(timeout: FiniteDuration): Option[com.histmersion.plugin.Processes.Session.CloseResult]
    def exit(timeout: FiniteDuration): Option[com.histmersion.plugin.Processes.Session.ExitResult]

  object Session:
    type OutResult = OutResult.T
    object OutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ErrResult = ErrResult.T
    object ErrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliveResult = AliveResult.T
    object AliveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SendResultPart1 = SendResultPart1.T
    object SendResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SendLine = SendLine.T
    object SendLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutResult = AwaitOutResult.T
    object AwaitOutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutReadyArg1 = AwaitOutReadyArg1.T
    object AwaitOutReadyArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutReadyResult = AwaitOutReadyResult.T
    object AwaitOutReadyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitErrLength = AwaitErrLength.T
    object AwaitErrLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseResult = CloseResult.T
    object CloseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExitResult = ExitResult.T
    object ExitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Unavailable extends Processes:
    type RunResultPart1 = _root_.com.histmersion.plugin.Processes.RunResultPart1
    val RunResultPart1: _root_.com.histmersion.plugin.Processes.RunResultPart1.type = _root_.com.histmersion.plugin.Processes.RunResultPart1
    type RunArgv = _root_.com.histmersion.plugin.Processes.RunArgv
    val RunArgv: _root_.com.histmersion.plugin.Processes.RunArgv.type = _root_.com.histmersion.plugin.Processes.RunArgv
    type RunEnvPart1 = _root_.com.histmersion.plugin.Processes.RunEnvPart1
    val RunEnvPart1: _root_.com.histmersion.plugin.Processes.RunEnvPart1.type = _root_.com.histmersion.plugin.Processes.RunEnvPart1
    type RunEnvPart2 = _root_.com.histmersion.plugin.Processes.RunEnvPart2
    val RunEnvPart2: _root_.com.histmersion.plugin.Processes.RunEnvPart2.type = _root_.com.histmersion.plugin.Processes.RunEnvPart2
    type RunStdin = _root_.com.histmersion.plugin.Processes.RunStdin
    val RunStdin: _root_.com.histmersion.plugin.Processes.RunStdin.type = _root_.com.histmersion.plugin.Processes.RunStdin
    type StartResultPart1 = _root_.com.histmersion.plugin.Processes.StartResultPart1
    val StartResultPart1: _root_.com.histmersion.plugin.Processes.StartResultPart1.type = _root_.com.histmersion.plugin.Processes.StartResultPart1
    type StartArgv = _root_.com.histmersion.plugin.Processes.StartArgv
    val StartArgv: _root_.com.histmersion.plugin.Processes.StartArgv.type = _root_.com.histmersion.plugin.Processes.StartArgv
    type StartEnvPart1 = _root_.com.histmersion.plugin.Processes.StartEnvPart1
    val StartEnvPart1: _root_.com.histmersion.plugin.Processes.StartEnvPart1.type = _root_.com.histmersion.plugin.Processes.StartEnvPart1
    type StartEnvPart2 = _root_.com.histmersion.plugin.Processes.StartEnvPart2
    val StartEnvPart2: _root_.com.histmersion.plugin.Processes.StartEnvPart2.type = _root_.com.histmersion.plugin.Processes.StartEnvPart2
    type CaptureResultPart1 = _root_.com.histmersion.plugin.Processes.CaptureResultPart1
    val CaptureResultPart1: _root_.com.histmersion.plugin.Processes.CaptureResultPart1.type = _root_.com.histmersion.plugin.Processes.CaptureResultPart1
    type CaptureArgv = _root_.com.histmersion.plugin.Processes.CaptureArgv
    val CaptureArgv: _root_.com.histmersion.plugin.Processes.CaptureArgv.type = _root_.com.histmersion.plugin.Processes.CaptureArgv

    def run(argv: List[com.histmersion.plugin.Processes.Unavailable.RunArgv], env: Map[com.histmersion.plugin.Processes.Unavailable.RunEnvPart1, com.histmersion.plugin.Processes.Unavailable.RunEnvPart2], stdin: Option[com.histmersion.plugin.Processes.Unavailable.RunStdin], timeout: FiniteDuration): Either[com.histmersion.plugin.Processes.Unavailable.RunResultPart1, Result] =
      Left(_root_.com.histmersion.plugin.Processes.Unavailable.RunResultPart1("this host supplies no processes"))
    def start(argv: List[com.histmersion.plugin.Processes.Unavailable.StartArgv], env: Map[com.histmersion.plugin.Processes.Unavailable.StartEnvPart1, com.histmersion.plugin.Processes.Unavailable.StartEnvPart2]): Either[com.histmersion.plugin.Processes.Unavailable.StartResultPart1, Session] =
      Left(_root_.com.histmersion.plugin.Processes.Unavailable.StartResultPart1("this host supplies no processes"))
    def capture(argv: List[com.histmersion.plugin.Processes.Unavailable.CaptureArgv]): Either[com.histmersion.plugin.Processes.Unavailable.CaptureResultPart1, Result] = Left(_root_.com.histmersion.plugin.Processes.Unavailable.CaptureResultPart1("this host supplies no processes"))
