package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.corequota.Window
import com.histmersion.Newtype

final case class Block(until: Instant, why: Block.Rationale)

object Block:
  /** Why CORE is not asked: what it answered, the account of the block, not a failure. Kept in
    * `upstream_quota.blocked_why` as its [[said]] text, and read back by [[Rationale.read]].
    */
  enum Rationale derives CanEqual:
    /** It answered HTTP 429. */
    case Throttled

    /** Its `X-RateLimit-Remaining` header said 0 of `limit` (when it said the limit). */
    case Exhausted(limit: Option[Window.Limit])

    /** A stored block that kept no reason. */
    case Unstated

    /** A reason an earlier version stored in other words, kept as it was. */
    case Stored(text: Rationale.Text)

    /** The reason as the surfaces show it and the column keeps it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case Throttled        => "HTTP 429"
      case Exhausted(limit) => s"X-RateLimit-Remaining 0 of ${limit.fold("?")(l => s"$l")}"
      case Unstated         => "asked to wait"
      case Stored(text)     => s"$text"

  object Rationale:
    /** The words of a reason as an earlier version stored them. */
    type Text = Text.T
    object Text extends Newtype[String, Pure]

    private val ExhaustedOf = "X-RateLimit-Remaining 0 of (\\d+|\\?)".r

    /** The reason a column's text says: the case that writes it back, else the text kept as it was. */
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
