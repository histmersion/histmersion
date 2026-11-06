package com.histmersion.domain.catalogue.places

import java.util.Locale
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** Wikidata item ids, as the gazetteer and the catalogues carry them. Pure. */
object Wikidata:
  type Qid = Qid.T
  object Qid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

<<<<<<< ours
  type QidOfPlaceIdId = QidOfPlaceIdId.T
  object QidOfPlaceIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

=======
>>>>>>> theirs
  val CommonsCategory = "P373"

  def isQid(s: String): Boolean = ((s).matches("Q[1-9][0-9]*"))

  /** Pure: the item a gazetteer place id names (`wikidata_q132564` → `Q132564`). */
<<<<<<< ours
  def qidOfPlaceId(id: com.histmersion.domain.catalogue.places.Wikidata.QidOfPlaceIdId): Option[com.histmersion.domain.catalogue.places.Wikidata.Qid] =
=======
  def qidOfPlaceId(id: com.histmersion.domain.catalogue.Place.Id): Option[Wikidata.Id] =
>>>>>>> theirs
    val s = (id).value.trim.toLowerCase(Locale.ROOT)
    Option.when(s.startsWith("wikidata_q"))("Q" + s.drop("wikidata_q".length)).flatMap(Wikidata.Id.option)

  /** A Wikidata item: `Q` and its number (`Q12554`). */
  type Id = Id.T

  object Id extends RefinedType[String, Match["Q[1-9][0-9]*"]]
