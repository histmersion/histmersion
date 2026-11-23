package com.histmersion
package util.log

import java.nio.channels.ClosedByInterruptException
import scala.annotation.tailrec

enum RequestFailure derives CanEqual:
  case ClientGone, Fault

object RequestFailure:
  private val CausesFollowed = 16

  def of(error: Throwable, handler: Thread): RequestFailure =
    if handler.isInterrupted then ClientGone else causedBy(Option(error), CausesFollowed)

  @tailrec private def causedBy(error: Option[Throwable], causesLeft: Int): RequestFailure =
    error match
      case Some(_: InterruptedException | _: ClosedByInterruptException) => ClientGone
      case Some(e) if causesLeft > 0                                     => causedBy(Option(e.getCause), causesLeft - 1)
      case _                                                             => Fault

  def log(message: String, error: Throwable): Unit =
    of(error, Thread.currentThread()) match
      case ClientGone => scribe.debug(s"$message — the client went away before the answer: ${error.getClass.getName}")
      case Fault      => scribe.error(message, error)
