package com.histmersion.upstreams.wikidata
final case class WikidataStatement(property: WikidataStatement.Property, file: WikidataStatement.File, preferred: WikidataStatement.Preferred, order: WikidataStatement.Order)

object WikidataStatement:
  /** Pure: the statements of one property to use, best first — the preferred ones when there are any (Wikidata's own
    * rule for "the" value), else the normal ones — at most `per`.
    */
  type BestPer = BestPer.T
  object BestPer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Property = Property.T
  object Property extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preferred = Preferred.T
  object Preferred extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Order = Order.T
  object Order extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def best(statements: List[WikidataStatement], per: com.histmersion.upstreams.wikidata.WikidataStatement.BestPer): List[WikidataStatement] =
    val preferred = statements.filter((_.preferred).value)
    (if preferred.nonEmpty then preferred else statements).sortBy(_.order).take(per.max(0))
