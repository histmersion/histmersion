package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class Limits(
    /** HARD cap on any caller-supplied `limit`, everywhere. */
    maxPage: Limits.MaxPage,
    search: Limits.Search,
    findings: Limits.Findings,
    snippetChars: Limits.SnippetChars,
    chunkChars: Limits.ChunkChars,
    reindexChars: Limits.ReindexChars,
    /** Largest accepted `POST /api/ingest` upload, in bytes. */
    uploadBytes: Limits.UploadBytes,
    /** Largest accepted base64 payload of the MCP `ingest` tool, in bytes of DECODED data. */
    inlineBytes: Limits.InlineBytes,
    /** Largest single stored asset, in bytes — a runaway figure must not fill the row. */
    assetBytes: Limits.AssetBytes,
    /** Cap on `fullText=true` / `section=body` without `offset` in `get_source`. */
    fullTextChars: Limits.FullTextChars,
    /** The most sources of an article's filter its coverage (#958) reads; the counts stay exact. */
    coverageSources: Limits.CoverageSources,
    /** `passages` (#954): search inside ONE source's body. */
    passages: PassageLimits
) derives ConfigReader:
  /** `ts_headline` counts words, not characters; ~6 chars per word is the usual English average. */
  def snippetWords: Limits.SnippetWords = Limits.SnippetWords(math.max(1, snippetChars.value / 6))

  /** Clamp a caller's `limit` into `[1, maxPage]`; `<= 0` means "the caller gave none" and yields `dflt` (itself
    * capped). ONE definition, so no surface can forget the cap.
    */
  def page(limit: Option[PageSize], dflt: PageSize): PageSize =
    val cap = math.max(1, maxPage.value)
    PageSize.applyUnsafe(
      limit.map(_.value).filter(_ > 0).map(_.min(cap)).getOrElse(math.max(1, dflt.value).min(cap))
    )

object Limits:
  type SnippetWords = SnippetWords.T
  object SnippetWords extends Newtype[Int, Pure]

  type MaxPage = MaxPage.T
  object MaxPage extends Newtype[Int, Pure]
  type Search = Search.T
  object Search extends Newtype[Int, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[Int, Pure]
  type SnippetChars = SnippetChars.T
  object SnippetChars extends Newtype[Int, Pure]
  type ChunkChars = ChunkChars.T
  object ChunkChars extends Newtype[Int, Pure]
  type ReindexChars = ReindexChars.T
  object ReindexChars extends Newtype[Long, Pure]
  type UploadBytes = UploadBytes.T
  object UploadBytes extends Newtype[Long, Pure]
  type InlineBytes = InlineBytes.T
  object InlineBytes extends Newtype[Long, Pure]
  type AssetBytes = AssetBytes.T
  object AssetBytes extends Newtype[Long, Pure]
  type FullTextChars = FullTextChars.T
  object FullTextChars extends Newtype[Int, Pure]
  type CoverageSources = CoverageSources.T
  object CoverageSources extends Newtype[Int, Pure]
