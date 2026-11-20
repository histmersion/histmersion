package com.histmersion
package cli

import com.histmersion.config.*
import com.histmersion.util.problem.Problem.Io

import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

/** Running the kit's scripts and curl. Behind a trait so the commands can be tested without them. The exit code here is
  * the child process's ([[Io.ExitCode]]), not the CLI's own ([[com.histmersion.cli.ExitCode]]).
  */
trait Runner:
  /** stdout on success; on failure the exit code and stderr. */
  def capture(
      cmd: List[String],
      env: Map[LocalInstall.EnvVariable, LocalInstall.EnvSetting] = Map.empty
  ): Either[(Io.ExitCode, String), String]

  /** The process's output goes to this terminal; its exit code. */
  def interactive(
      cmd: List[String],
      env: Map[LocalInstall.EnvVariable, LocalInstall.EnvSetting] = Map.empty
  ): Io.ExitCode

object Runner:

  object System extends Runner:

    def capture(cmd: List[String], env: Map[LocalInstall.EnvVariable, LocalInstall.EnvSetting]) =
      scala.util
        .Try {
          val pb = new ProcessBuilder(cmd.asJava)
          env.foreach((k, v) => pb.environment().put(k.value, v.value))
          val p = pb.start()
          // drain stderr beside stdout: a full stderr pipe would block the child
          val err =
            java.util.concurrent.CompletableFuture.supplyAsync(() => new String(p.getErrorStream.readAllBytes, UTF_8))
          val out = new String(p.getInputStream.readAllBytes, UTF_8)
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(Io.ExitCode(rc) -> err.get().trim.take(2000))
        }
        .fold[Either[(Io.ExitCode, String), String]](
          e => Left(Io.ExitCode(127) -> s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"),
          identity
        )

    def interactive(cmd: List[String], env: Map[LocalInstall.EnvVariable, LocalInstall.EnvSetting]) =
      scala.util
        .Try {
          val pb = new ProcessBuilder(cmd.asJava).inheritIO()
          env.foreach((k, v) => pb.environment().put(k.value, v.value))
          Io.ExitCode(pb.start().waitFor())
        }
        .getOrElse(Io.ExitCode(127))
