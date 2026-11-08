package com.histmersion.api.library

import com.histmersion.api.catalogue.Period

type Citekey = Citekey.T
object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

final case class Source(
    // S3 REWRITE: same package
    citekey: com.histmersion.api.library.Citekey,
    // S4 REWRITE: other package, through an added import
    year: Option[com.histmersion.api.catalogue.Year],
    // S5 REWRITE: the companion from the class: Source.Venue (Venue alone is not in scope here)
    venue: Option[com.histmersion.api.library.Source.Venue],
    // S6 UNCHANGED: the companion's member is not in scope in the class
    topics: List[Source.Topics],
    period: Option[Period]
)

object Source:
  object Neighbor:
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Calibre = Calibre.T
  object Calibre extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  // S7 REWRITE: self, full path
  def venueOf(s: Source): Option[com.histmersion.api.library.Source.Venue] = s.venue
  // S8 REWRITE: self, name-qualified
  def topic(raw: String): Source.Topics = Source.Topics(raw)
  // S9 REWRITE: self, nested object keeps the inner qualifier
  def line(l: com.histmersion.api.library.Source.Neighbor.Line): Source.Neighbor.Line = l
  // S10 REWRITE to Source.Venue: a parameter named Venue shadows the member
  def shadowed(Venue: Int): com.histmersion.api.library.Source.Venue.type = com.histmersion.api.library.Source.Venue
