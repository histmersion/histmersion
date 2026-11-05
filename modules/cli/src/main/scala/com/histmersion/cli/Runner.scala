package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

/** Running the kit's scripts and curl. Behind a trait so the commands can be tested without them. */
trait Runner:
  /** stdout on success; on failure the exit code and stderr. */
  def capture(cmd: List[com.histmersion.cli.Runner.CaptureCmd], env: Map[com.histmersion.cli.Runner.CaptureEnvPart1, com.histmersion.cli.Runner.CaptureEnvPart2] = Map.empty): Either[(com.histmersion.cli.Runner.CaptureResultPart1Part1, com.histmersion.cli.Runner.CaptureResultPart1Part2), com.histmersion.cli.Runner.CaptureResultPart2]

  /** The process's output goes to this terminal; its exit code. */
  def interactive(cmd: List[com.histmersion.cli.Runner.InteractiveCmd], env: Map[com.histmersion.cli.Runner.InteractiveEnvPart1, com.histmersion.cli.Runner.InteractiveEnvPart2] = Map.empty): com.histmersion.cli.Runner.InteractiveResult

object Runner:
  type CaptureResultPart1Part1 = CaptureResultPart1Part1.T
  object CaptureResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureResultPart1Part2 = CaptureResultPart1Part2.T
  object CaptureResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureResultPart2 = CaptureResultPart2.T
  object CaptureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureCmd = CaptureCmd.T
  object CaptureCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureEnvPart1 = CaptureEnvPart1.T
  object CaptureEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureEnvPart2 = CaptureEnvPart2.T
  object CaptureEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveResult = InteractiveResult.T
  object InteractiveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveCmd = InteractiveCmd.T
  object InteractiveCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveEnvPart1 = InteractiveEnvPart1.T
  object InteractiveEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveEnvPart2 = InteractiveEnvPart2.T
  object InteractiveEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object System extends Runner:
    type CaptureCmd = CaptureCmd.T
    object CaptureCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CaptureEnvPart1 = CaptureEnvPart1.T
    object CaptureEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CaptureEnvPart2 = CaptureEnvPart2.T
    object CaptureEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InteractiveCmd = InteractiveCmd.T
    object InteractiveCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InteractiveEnvPart1 = InteractiveEnvPart1.T
    object InteractiveEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InteractiveEnvPart2 = InteractiveEnvPart2.T
    object InteractiveEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def capture(cmd: List[com.histmersion.cli.Runner.System.CaptureCmd], env: Map[com.histmersion.cli.Runner.System.CaptureEnvPart1, com.histmersion.cli.Runner.System.CaptureEnvPart2]) =
      scala.util
        .Try {
          val pb = new ProcessBuilder(cmd.asJava)
          env.foreach((k, v) => pb.environment().put(k, v))
          val p = pb.start()
          // drain stderr beside stdout: a full stderr pipe would block the child
          val err =
            java.util.concurrent.CompletableFuture.supplyAsync(() => new String(p.getErrorStream.readAllBytes, UTF_8))
          val out = new String(p.getInputStream.readAllBytes, UTF_8)
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(rc -> err.get().trim.take(2000))
        }
        .fold(e => Left(127 -> s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def interactive(cmd: List[com.histmersion.cli.Runner.System.InteractiveCmd], env: Map[com.histmersion.cli.Runner.System.InteractiveEnvPart1, com.histmersion.cli.Runner.System.InteractiveEnvPart2]) =
      scala.util
        .Try {
          val pb = new ProcessBuilder(cmd.asJava).inheritIO()
          env.foreach((k, v) => pb.environment().put(k, v))
          pb.start().waitFor()
        }
        .getOrElse(127)
