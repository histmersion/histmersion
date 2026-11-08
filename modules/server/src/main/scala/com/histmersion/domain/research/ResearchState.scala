package com.histmersion
package domain.research

import com.histmersion.api.research.{Article, CritiqueReason}
import com.histmersion.domain.ReviewLedger.Ledger

import java.time.Instant
import java.util.UUID
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object ArticlesKey extends RefinedType[String, Pure]
  type AliasesKey = AliasesKey.T
  object AliasesKey extends RefinedType[String, Pure]
  type AliasesValue = AliasesValue.T
  object AliasesValue extends RefinedType[String, Pure]
  type NextVersion = NextVersion.T
  object NextVersion extends RefinedType[Long, Pure]
  type CritiquesKey = CritiquesKey.T
  object CritiquesKey extends RefinedType[String, Pure]
  type LedgersKey = LedgersKey.T
  object LedgersKey extends RefinedType[String, Pure]

  val empty: ResearchState = ResearchState(Map.empty, Map.empty, Vector.empty, NextVersion(1L), Map.empty)

  /** One stored article: the record without its update time (the state's `updatedAt`), its uid and the article targets
    * its body links, in the order they were written.
    */
  final case class Stored(article: Article, uid: UUID, updatedAt: Instant, links: List[Stored.Links])

  object Stored:
    type Links = Links.T
    object Links extends RefinedType[String, Pure]

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
    object Id extends RefinedType[Long, Pure]
    type Slug = Slug.T
    object Slug extends RefinedType[String, Pure]
    type Body = Body.T
    object Body extends RefinedType[String, Pure]
    type Summary = Summary.T
    object Summary extends RefinedType[String, Pure]
