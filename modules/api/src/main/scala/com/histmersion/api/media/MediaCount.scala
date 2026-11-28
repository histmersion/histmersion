package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

final case class MediaCount(
    key: MediaCount.Key,
    n: MediaCount.N,
    allowed: MediaCount.Allowed = MediaCount.Allowed.Allowed
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object MediaCount:

  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]
  enum Allowed derives CanEqual:
    case Allowed, Blocked
  object Allowed extends TwoStates[Allowed]:
    extension (allowed: Allowed) def allowed: Boolean = allowed == Allowed

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(keys: List[Key], allowed: Key => Boolean = _ => (true)): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, N(v.size), (if allowed(k) then Allowed.Allowed else Allowed.Blocked)))
      .sortBy(c => (-(c.n).value, c.key))
