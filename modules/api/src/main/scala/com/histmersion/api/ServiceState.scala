package com.histmersion
package api

import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The converter reports free text (`ok`, `busy: 2 jobs`, `degraded: …`), so a wording never seen before reads as
  * [[ServiceState.Unknown]] at worst.
  */
@movesTo(FeaturePlace.Kernel)
enum ServiceState:
  case Ok, Busy, Down, Unknown

  def label: ServiceState.Wire = this match
    case Ok      => ServiceState.Wire("ok")
    case Busy    => ServiceState.Wire("busy")
    case Down    => ServiceState.Wire("down")
    case Unknown => ServiceState.Wire("unknown")

object ServiceState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(text: Option[Wire]): ServiceState =
    text.map((_).value.trim.toLowerCase).filter(_.nonEmpty) match
      case None                                                               => Unknown
      case Some(t) if t == "ok" || t.startsWith("ok ") || t.startsWith("ok:") => Ok
      case Some(t) if t.startsWith("busy") || t.contains(" busy")             => Busy
      case Some(t) if t == "unknown"                                          => Unknown
      case Some(t) if DownWords.exists(t.contains)                            => Down
      case Some(_)                                                            => Unknown
