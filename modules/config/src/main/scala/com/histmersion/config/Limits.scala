package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  def snippetWords: com.histmersion.config.Limits.SnippetWords = Limits.SnippetWords(math.max(1, snippetChars.value / 6))

  /** Clamp a caller's `limit` into `[1, maxPage]`; `<= 0` means "the caller gave none" and yields `dflt` (itself
    * capped). ONE definition, so no surface can forget the cap.
    */
  def page(limit: Option[com.histmersion.PageSize], dflt: com.histmersion.PageSize): com.histmersion.PageSize =
    val cap = math.max(1, maxPage.value)
    com.histmersion.PageSize.applyUnsafe(
      limit.map(_.value).filter(_ > 0).map(_.min(cap)).getOrElse(math.max(1, dflt.value).min(cap))
    )

object Limits:
  type SnippetWords = SnippetWords.T
  object SnippetWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxPage = MaxPage.T
  object MaxPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Search = Search.T
  object Search extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SnippetChars = SnippetChars.T
  object SnippetChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkChars = ChunkChars.T
  object ChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexChars = ReindexChars.T
  object ReindexChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadBytes = UploadBytes.T
  object UploadBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlineBytes = InlineBytes.T
  object InlineBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetBytes = AssetBytes.T
  object AssetBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullTextChars = FullTextChars.T
  object FullTextChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverageSources = CoverageSources.T
  object CoverageSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
