package com.histmersion
package domain.library

import com.histmersion.api.library.{Edge, Source}
import com.histmersion.util.ocr.Chunk

import java.time.Instant
import java.util.UUID
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The library's records the in-memory services share: the sources (each with its findings, chunks and identity), the
  * old citekeys, the edges between stored sources and the edges parked until their target arrives.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class LibraryState(
    sources: Map[LibraryState.SourcesKey, LibraryState.Stored],
    aliases: Map[LibraryState.AliasesKey, LibraryState.AliasesValue],
    aliasTombstones: Set[LibraryState.AliasTombstones],
    edges: Set[Edge],
    parked: Set[Edge]
)

object LibraryState:
  type SourcesKey = SourcesKey.T
  object SourcesKey extends Newtype[String, Pure]
  type AliasesKey = AliasesKey.T
  object AliasesKey extends Newtype[String, Pure]
  type AliasesValue = AliasesValue.T
  object AliasesValue extends Newtype[String, Pure]
  type AliasTombstones = AliasTombstones.T
  object AliasTombstones extends Newtype[String, Pure]

  val empty: LibraryState = LibraryState(Map.empty, Map.empty, Set.empty, Set.empty, Set.empty)

  /** One stored source: the record without its edges (they are the state's), its uid, its findings' uids, its chunks
    * and its last write; the digest of its stored original and the licence it is published under, as stored.
    */
  final case class Stored(
      source: Source,
      uid: UUID,
      findingUids: Map[Stored.FindingUidsKey, UUID],
      chunks: List[Chunk],
      updatedAt: Instant,
      sha256: Option[Sha256] = None,
      licence: Option[Stored.Licence] = None
  )

  object Stored:
    type FindingUidsKey = FindingUidsKey.T
    object FindingUidsKey extends Newtype[String, Pure]
    type Licence = Licence.T
    object Licence extends Newtype[String, Pure]
