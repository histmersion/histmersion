package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventCount(key: EventCount.Key, n: EventCount.N) derives JsonCodec, KindlingsSchema, api.json.Schema
object EventCount:

  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(keys: List[Key]): List[EventCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => EventCount(k, N(v.size)))
      .sortBy(c => (-(c.n).value, c.key))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def add(a: List[EventCount], b: List[EventCount]): List[EventCount] =
    (a ++ b)
      .groupBy(_.key)
      .toList
      .map((k, cs) => EventCount(k, N(cs.map(c => (c.n.value: Int)).sum)))
      .sortBy(c => (-(c.n).value, c.key))
