package com.histmersion.plugins.calibre

import scala.jdk.CollectionConverters.*


/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[String]): Either[String, String]
  def isRunning(processMatch: String): Boolean

object Shell:
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */
  object System extends Shell:
    def run(cmd: List[String]): Either[String, String] =
      scala.util
        .Try {
          val p = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
          val out = new String(p.getInputStream.readAllBytes, "UTF-8")
          val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(s"${cmd.head} exited $rc: ${err.trim.take(400)}")
        }
        .fold(e => Left(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def isRunning(processMatch: String): Boolean =
      run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)
