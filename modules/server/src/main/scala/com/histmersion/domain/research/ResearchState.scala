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
    articles: Map[String, ResearchState.Stored],
    aliases: Map[String, String],
    versions: Vector[ResearchState.Replaced],
    nextVersion: Long,
    critiques: Map[String, List[CritiqueReason]],
    ledgers: Map[String, Ledger] = Map.empty
)

object ResearchState:
  val empty: ResearchState = ResearchState(Map.empty, Map.empty, Vector.empty, 1L, Map.empty)

  /** One stored article: the record without its update time (the state's `updatedAt`), its uid and the article targets
    * its body links, in the order they were written.
    */
  final case class Stored(article: Article, uid: UUID, updatedAt: Instant, links: List[String])

  /** A body and summary an article had before a write replaced them. */
  final case class Replaced(
      id: Long,
      slug: String,
      body: String,
      summary: Option[String],
      updatedAt: Instant,
      createdAt: Instant
  )
