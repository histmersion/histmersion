package com.histmersion.domain.settings

import java.util.concurrent.atomic.AtomicInteger

/** Restarting the server for the settings only a restart applies: the process ends, and the service it runs under (the
  * LaunchAgent, the systemd unit) starts it again.
  */
trait RestartService:
  /** A restart is possible: this server runs under such a service. */
  def available: Boolean

  /** Ask for the restart: it happens a moment after the answer is sent. `Left` says why it cannot. */
  def restart(): Either[String, String]

object RestartService:

  val Started = "The server is restarting. It is back in a few seconds; this page reconnects by itself."
  val NotHere =
    "This server was not started by a service that starts it again, so it cannot restart itself. " +
      "Stop it and start it again yourself."

  /** `stop` ends the process (`None`: nothing runs this server again, so no restart is offered). */
  def apply(stop: Option[() => Unit]): RestartService = Implementation(stop)

  final private class Implementation(stop: Option[() => Unit]) extends RestartService:
    def available: Boolean = stop.isDefined
    def restart(): Either[String, String] = stop.toRight(NotHere).map { s => s(); Started }

  /** Counts the restarts asked for instead of ending anything. */
  final class InMemory(val available: Boolean) extends RestartService:
    private val asked = AtomicInteger(0)
    def restarts: Int = asked.get
    def restart(): Either[String, String] =
      if !available then Left(NotHere) else { asked.incrementAndGet(); Right(Started) }
