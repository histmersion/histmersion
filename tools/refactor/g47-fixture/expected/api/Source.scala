package com.histmersion
package api.library

import com.histmersion.api.catalogue.Period
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year

type Citekey = Citekey.T
object Citekey extends RefinedType[String, Pure]

final case class Source(
    // S3 REWRITE: same package
    citekey: Citekey,
    // S4 REWRITE: other package, through an added import
    year: Option[Year],
    // S5 REWRITE: the companion from the class: Source.Venue (Venue alone is not in scope here)
    venue: Option[Source.Venue],
    // S6 UNCHANGED: the companion's member is not in scope in the class
    topics: List[Source.Topics],
    period: Option[Period]
)

object Source:
  object Neighbor:
    type Line = Line.T
    object Line extends RefinedType[String, Pure]
  type Venue = Venue.T
  object Venue extends RefinedType[String, Pure]
  type Topics = Topics.T
  object Topics extends RefinedType[String, Pure]
  type Calibre = Calibre.T
  object Calibre extends RefinedType[Long, Pure]
  // S7 REWRITE: self, full path
  def venueOf(s: Source): Option[Venue] = s.venue
  // S8 REWRITE: self, name-qualified
  def topic(raw: String): Topics = Topics(raw)
  // S9 REWRITE: self, nested object keeps the inner qualifier
  def line(l: Neighbor.Line): Neighbor.Line = l
  // S10 REWRITE to Source.Venue: a parameter named Venue shadows the member
  def shadowed(Venue: Int): Source.Venue.type = Source.Venue
