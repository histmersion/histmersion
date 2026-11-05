package com.histmersion.domain.research

import com.histmersion.api.research.{Article, CritiqueReason}
import com.histmersion.domain.ReviewLedger.Ledger

import java.time.Instant
import java.util.UUID

/** Research's records the in-memory services share: the articles (each with its uid, its last write and the targets its
  * body links), the old slugs, the recorded versions, the critique reasons beside each article and each article's
  * review ledger.
  */
final case class ResearchState(
    articles: Map[ResearchState.ArticlesKey, ResearchState.Stored],
    aliases: Map[ResearchState.AliasesKey, ResearchState.AliasesValue],
    versions: Vector[ResearchState.Replaced],
    nextVersion: ResearchState.NextVersion,
    critiques: Map[ResearchState.CritiquesKey, List[CritiqueReason]],
    ledgers: Map[ResearchState.LedgersKey, Ledger] = Map.empty
)

object ResearchState:
  type ArticlesKey = ArticlesKey.T
  object ArticlesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesKey = AliasesKey.T
  object AliasesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesValue = AliasesValue.T
  object AliasesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextVersion = NextVersion.T
  object NextVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiquesKey = CritiquesKey.T
  object CritiquesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgersKey = LedgersKey.T
  object LedgersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: ResearchState = ResearchState(Map.empty, Map.empty, Vector.empty, 1L, Map.empty)

  /** One stored article: the record without its update time (the state's `updatedAt`), its uid and the article targets
    * its body links, in the order they were written.
    */
  final case class Stored(article: Article, uid: UUID, updatedAt: Instant, links: List[Stored.Links])

  object Stored:
    type Links = Links.T
    object Links extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A body and summary an article had before a write replaced them. */
  final case class Replaced(
      id: Replaced.Id,
      slug: Replaced.Slug,
      body: Replaced.Body,
      summary: Option[Replaced.Summary],
      updatedAt: Instant,
      createdAt: Instant
  )

  object Replaced:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
