package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One pair of the checklist: the critic, the kinds of edge from it to the other source, the other source, its state,
  * and the stored reason when it has one.
  */
final case class CritiquePair(
    src: CritiqueSide,
    kinds: List[String],
    dst: CritiqueSide,
    state: CritiqueState,
    reason: Option[String] = None,
    reasonBy: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ref: CritiqueRef = CritiqueRef(src.citekey, dst.citekey)
  def open: Boolean = state == CritiqueState.Open

  /** Whose claim the disagreement is about: the target of challenges/refutes/updates, but the SOURCE of superseded_by —
    * that edge is stored on the older work and points at the newer one.
    */
  def criticised: CritiqueSide = if Critiques.olderFirst(kinds) then src else dst

  /** The other side: the work that criticises, updates or supersedes. */
  def critic: CritiqueSide = if Critiques.olderFirst(kinds) then dst else src

  /** Open, and the article cites the work it criticises: a critique that can bear on a claim the article makes. An open
    * pair on a work the article does not cite is background — the article states none of its claims, so no ruling and
    * no MAJOR is owed for it.
    */
  def bearing: Boolean = open && criticised.cited

  /** `[[a]] refutes [[b]]`. */
  def line: String = s"[[${src.citekey}]] ${kinds.mkString("/")} [[${dst.citekey}]]"
object CritiquePair:
  given Schema[CritiquePair] = summon[KindlingsSchema[CritiquePair]].schema
