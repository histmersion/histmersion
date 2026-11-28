package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.corequota.Window
import com.histmersion.Newtype

final case class Backoff(until: Instant, why: Backoff.Rationale)

object Backoff:
  enum Rationale derives CanEqual:
    case Throttled

    case Exhausted(limit: Option[Window.Limit])

    case Unstated

    case Stored(text: Rationale.Text)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case Throttled        => "HTTP 429"
      case Exhausted(limit) => s"X-RateLimit-Remaining 0 of ${limit.fold("?")(l => s"$l")}"
      case Unstated         => "asked to wait"
      case Stored(text)     => s"$text"

  object Rationale:
    type Text = Text.T
    object Text extends Newtype[String, Pure]

    private val ExhaustedOf = "X-RateLimit-Remaining 0 of (\\d+|\\?)".r

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def read(written: String): Rationale = written match
      case "HTTP 429"         => Throttled
      case "asked to wait"    => Unstated
      case ExhaustedOf("?")   => Exhausted(None)
      case ExhaustedOf(limit) =>
        limit.toIntOption
          .map(Window.Limit(_))
          .filter(l => s"$l" == limit)
          .fold(Stored(Text(written)))(l => Exhausted(Some(l)))
      case other => Stored(Text(other))
