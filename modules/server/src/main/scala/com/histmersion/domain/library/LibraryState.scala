package com.histmersion.domain.library

import com.histmersion.api.library.{Edge, Source}
import com.histmersion.util.ocr.Chunk

import java.time.Instant
import java.util.UUID

/** The library's records the in-memory services share: the sources (each with its findings, chunks and identity), the
  * old citekeys, the edges between stored sources and the edges parked until their target arrives.
  */
final case class LibraryState(
    sources: Map[LibraryState.SourcesKey, LibraryState.Stored],
    aliases: Map[LibraryState.AliasesKey, LibraryState.AliasesValue],
    aliasTombstones: Set[LibraryState.AliasTombstones],
    edges: Set[Edge],
    parked: Set[Edge]
)

object LibraryState:
  type SourcesKey = SourcesKey.T
  object SourcesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesKey = AliasesKey.T
  object AliasesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesValue = AliasesValue.T
  object AliasesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasTombstones = AliasTombstones.T
  object AliasTombstones extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      sha256: Option[com.histmersion.Sha256] = None,
      licence: Option[Stored.Licence] = None
  )

  object Stored:
    type FindingUidsKey = FindingUidsKey.T
    object FindingUidsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Licence = Licence.T
    object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
