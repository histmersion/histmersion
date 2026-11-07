package com.histmersion.util.settings

import java.util.concurrent.atomic.AtomicInteger

/** Restarting the server for the settings only a restart applies: the process ends, and the service it runs under (the
  * LaunchAgent, the systemd unit) starts it again.
  */
trait RestartService:
  /** A restart is possible: this server runs under such a service. */
  def available: Boolean

  /** Ask for the restart: it happens a moment after the answer is sent. `Left` says why it cannot. */
  def restart(): Either[com.histmersion.util.settings.RestartService.Problem, com.histmersion.util.settings.RestartService.Summary]

object RestartService:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestartCount = RestartCount.T
  object RestartCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val Started = "The server is restarting. It is back in a few seconds; this page reconnects by itself."
  val NotHere =
    "This server was not started by a service that starts it again, so it cannot restart itself. " +
      "Stop it and start it again yourself."

  /** `stop` ends the process (`None`: nothing runs this server again, so no restart is offered). */
  def apply(stop: Option[() => Unit]): RestartService = Implementation(stop)

  final private class Implementation(stop: Option[() => Unit]) extends RestartService:
    def available: Boolean = stop.isDefined
    def restart(): Either[com.histmersion.util.settings.RestartService.Problem, com.histmersion.util.settings.RestartService.Summary] = stop.toRight(NotHere).map { s => s(); Started }

  object Implementation:

  /** Counts the restarts asked for instead of ending anything. */
  final class InMemory(val available: InMemory.Available) extends RestartService:
    private val asked = AtomicInteger(0)
    def restarts: com.histmersion.util.settings.RestartService.RestartCount = asked.get
    def restart(): Either[com.histmersion.util.settings.RestartService.Problem, com.histmersion.util.settings.RestartService.Summary] =
      if !available then Left(NotHere) else { asked.incrementAndGet(); Right(Started) }

  object InMemory:
    type Available = Available.T
    object Available extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

