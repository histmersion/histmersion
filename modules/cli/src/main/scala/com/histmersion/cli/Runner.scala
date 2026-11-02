package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

/** Running the kit's scripts and curl. Behind a trait so the commands can be tested without them. */
trait Runner:
  /** stdout on success; on failure the exit code and stderr. */
  def capture(cmd: List[String], env: Map[String, String] = Map.empty): Either[(Int, String), String]

  /** The process's output goes to this terminal; its exit code. */
  def interactive(cmd: List[String], env: Map[String, String] = Map.empty): Int

object Runner:
  object System extends Runner:
    def capture(cmd: List[String], env: Map[String, String]) =
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

    def interactive(cmd: List[String], env: Map[String, String]) =
      scala.util
        .Try {
          val pb = new ProcessBuilder(cmd.asJava).inheritIO()
          env.foreach((k, v) => pb.environment().put(k, v))
          pb.start().waitFor()
        }
        .getOrElse(127)
