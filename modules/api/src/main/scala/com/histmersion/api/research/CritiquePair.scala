package com.histmersion
package api.research

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CritiquePair(
    src: CritiqueSide,
    kinds: List[CritiquePair.Kinds],
    dst: CritiqueSide,
    state: CritiqueState,
    reason: Option[Problem] = None,
    reasonBy: Option[CritiquePair.ReasonBy] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def ref: CritiqueRef = CritiqueRef(src.citekey, dst.citekey)
  def open: Boolean = (state == CritiqueState.Open)

  def criticised: CritiqueSide = if Critiques.olderFirst(kinds) then src else dst

  def critic: CritiqueSide = if Critiques.olderFirst(kinds) then dst else src

  def bearing: Boolean = (((open) == (true)) && ((criticised.cited) == CritiqueSide.Cited(true)))

  def line: String = (s"[[${src.citekey}]] ${kinds.mkString("/")} [[${dst.citekey}]]")

object CritiquePair:

  type Kinds = Kinds.T
  object Kinds extends Newtype[String, Pure]
  export com.histmersion.api.automation.Activity.Actor as ReasonBy
