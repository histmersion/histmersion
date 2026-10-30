package com.histmersion.api

import scala.annotation.tailrec

/** The converter reports free text (`ok`, `busy: 2 jobs`, `degraded: …`), so a wording never seen before reads as
  * [[ServiceState.Unknown]] at worst.
  */
enum ServiceState:
  case Ok, Busy, Down, Unknown

  def label: String = this match
    case Ok      => "ok"
    case Busy    => "busy"
    case Down    => "down"
    case Unknown => "unknown"

object ServiceState:
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

  def of(text: Option[String]): ServiceState =
    text.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                                               => Unknown
      case Some(t) if t == "ok" || t.startsWith("ok ") || t.startsWith("ok:") => Ok
      case Some(t) if t.startsWith("busy") || t.contains(" busy")             => Busy
      case Some(t) if t == "unknown"                                          => Unknown
      case Some(t) if DownWords.exists(t.contains)                            => Down
      case Some(_)                                                            => Unknown
