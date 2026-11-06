package com.histmersion.api

import scala.annotation.tailrec

/** The converter reports free text (`ok`, `busy: 2 jobs`, `degraded: …`), so a wording never seen before reads as
  * [[ServiceState.Unknown]] at worst.
  */
enum ServiceState:
  case Ok, Busy, Down, Unknown

  def label: com.histmersion.api.ServiceState.Wire = this match
    case Ok      => _root_.com.histmersion.api.ServiceState.Wire("ok")
    case Busy    => _root_.com.histmersion.api.ServiceState.Wire("busy")
    case Down    => _root_.com.histmersion.api.ServiceState.Wire("down")
    case Unknown => _root_.com.histmersion.api.ServiceState.Wire("unknown")

object ServiceState:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val DownWords = List(
    "degraded",
    "error",
    "unreachable",
    "down",
    "failed",
    "refused",
    "timeout",
    "did not",
    "no answer",
    "returned",
    "answered"
  )

  def of(text: Option[com.histmersion.api.ServiceState.Wire]): ServiceState =
    text.map((_).value.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                                               => Unknown
      case Some(t) if t == "ok" || t.startsWith("ok ") || t.startsWith("ok:") => Ok
      case Some(t) if t.startsWith("busy") || t.contains(" busy")             => Busy
      case Some(t) if t == "unknown"                                          => Unknown
      case Some(t) if DownWords.exists(t.contains)                            => Down
      case Some(_)                                                            => Unknown
