package com.histmersion.domain.library

import com.histmersion.api.library.{Edge, Source}
import com.histmersion.util.ocr.Chunk

import java.time.Instant
import java.util.UUID

/** The library's records the in-memory services share: the sources (each with its findings, chunks and identity), the
  * old citekeys, the edges between stored sources and the edges parked until their target arrives.
  */
final case class LibraryState(
    sources: Map[String, LibraryState.Stored],
    aliases: Map[String, String],
    aliasTombstones: Set[String],
    edges: Set[Edge],
    parked: Set[Edge]
)

object LibraryState:
  val empty: LibraryState = LibraryState(Map.empty, Map.empty, Set.empty, Set.empty, Set.empty)

  /** One stored source: the record without its edges (they are the state's), its uid, its findings' uids, its chunks
    * and its last write; the digest of its stored original and the licence it is published under, as stored.
    */
  final case class Stored(
      source: Source,
      uid: UUID,
      findingUids: Map[String, UUID],
      chunks: List[Chunk],
      updatedAt: Instant,
      sha256: Option[String] = None,
      licence: Option[String] = None
  )
