package com.histmersion
package domain.catalogue.places

import java.util.Locale
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.domain.catalogue.Place

object Wikidata:

  val CommonsCategory = "P373"

  def isQid(s: String): Boolean = ((s).matches("Q[1-9][0-9]*"))

  def qidOfPlaceId(id: Place.Id): Option[Id] =
    val s = (id).value.trim.toLowerCase(Locale.ROOT)
    Option.when(s.startsWith("wikidata_q"))("Q" + s.drop("wikidata_q".length)).flatMap(Id.option)

  type Id = Id.T

  object Id extends Newtype[String, Match["Q[1-9][0-9]*"]]
