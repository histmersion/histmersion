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
  def system(using processes: com.histmersion.plugin.Processes): Shell = new Shell:
    def run(cmd: List[String]): Either[String, String] =
      processes.capture(cmd).flatMap(result =>
        if result.exit == 0 then Right(result.out)
        else Left(s"${cmd.head} exited ${result.exit}: ${result.err.trim.take(400)}")
      )
    def isRunning(processMatch: String): Boolean = run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)
