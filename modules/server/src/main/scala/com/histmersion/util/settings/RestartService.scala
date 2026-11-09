package com.histmersion
package util.settings
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.settings.ConfigEdit
import java.util.concurrent.atomic.AtomicInteger
import io.github.iltotore.iron.constraint.any.Pure

/** Restarting the server for the settings only a restart applies: the process ends, and the service it runs under (the
  * LaunchAgent, the systemd unit) starts it again.
  */
trait RestartService:
  /** A restart is possible: this server runs under such a service. */
  def available: Boolean

  /** Ask for the restart: it happens a moment after the answer is sent. `Left` says why it cannot. */
  def restart(): Either[Problem, ConfigEdit.Notice]

object RestartService:
  type RestartCount = RestartCount.T
  object RestartCount extends Newtype[Int, Pure]

  val Started: ConfigEdit.Notice = ConfigEdit.Notice(
    "The server is restarting. It is back in a few seconds; this page reconnects by itself."
  )
  val NotHere: Problem =
    Problem.Unclassified(
      Legacy.Text(
        "This server was not started by a service that starts it again, so it cannot restart itself. " +
          "Stop it and start it again yourself."
      )
    )

  /** `stop` ends the process (`None`: nothing runs this server again, so no restart is offered). */
  def apply(stop: Option[() => Unit]): RestartService = Implementation(stop)

  final private class Implementation(stop: Option[() => Unit]) extends RestartService:
    def available: Boolean = stop.isDefined
    def restart(): Either[Problem, ConfigEdit.Notice] = stop.toRight(NotHere).map { s => s(); Started }

  /** Counts the restarts asked for instead of ending anything. */
  final class InMemory(val available: Boolean) extends RestartService:
    private val asked = AtomicInteger(0)
    def restarts: RestartCount = RestartCount(asked.get)
    def restart(): Either[Problem, ConfigEdit.Notice] =
      if !available then Left(NotHere) else { asked.incrementAndGet(); Right(Started) }
