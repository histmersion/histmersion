package com.histmersion
package upstreams.wikidata

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
final case class WikidataStatement(
    property: WikidataStatement.Property,
    file: WikidataStatement.File,
    preferred: WikidataStatement.Preferred,
    order: WikidataStatement.Order
)

object WikidataStatement:
  /** Pure: the statements of one property to use, best first — the preferred ones when there are any (Wikidata's own
    * rule for "the" value), else the normal ones — at most `per`.
    */
  type BestPer = BestPer.T
  object BestPer extends Newtype[Int, Pure]

  type Property = Property.T
  object Property extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]
  type Preferred = Preferred.T
  object Preferred extends Newtype[Boolean, Pure]
  type Order = Order.T
  object Order extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def best(statements: List[WikidataStatement], per: BestPer): List[WikidataStatement] =
    val preferred = statements.filter((refactorCallback => (refactorCallback.preferred).value))
    (if preferred.nonEmpty then preferred else statements).sortBy(_.order).take((per).value.max(0))
