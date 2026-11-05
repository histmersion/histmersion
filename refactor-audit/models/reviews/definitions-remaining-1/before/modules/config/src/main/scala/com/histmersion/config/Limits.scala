package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class Limits(
    /** HARD cap on any caller-supplied `limit`, everywhere. */
    maxPage: Int,
    search: Int,
    findings: Int,
    snippetChars: Int,
    chunkChars: Int,
    reindexChars: Long,
    /** Largest accepted `POST /api/ingest` upload, in bytes. */
    uploadBytes: Long,
    /** Largest accepted base64 payload of the MCP `ingest` tool, in bytes of DECODED data. */
    inlineBytes: Long,
    /** Largest single stored asset, in bytes — a runaway figure must not fill the row. */
    assetBytes: Long,
    /** Cap on `fullText=true` / `section=body` without `offset` in `get_source`. */
    fullTextChars: Int,
    /** The most sources of an article's filter its coverage (#958) reads; the counts stay exact. */
    coverageSources: Int,
    /** `passages` (#954): search inside ONE source's body. */
    passages: PassageLimits
) derives ConfigReader:
  /** `ts_headline` counts words, not characters; ~6 chars per word is the usual English average. */
  def snippetWords: Int = math.max(1, snippetChars / 6)

  /** Clamp a caller's `limit` into `[1, maxPage]`; `<= 0` means "the caller gave none" and yields `dflt` (itself
    * capped). ONE definition, so no surface can forget the cap.
    */
  def page(limit: Option[Int], dflt: Int): Int =
    val cap = math.max(1, maxPage)
    limit.filter(_ > 0).map(_.min(cap)).getOrElse(math.max(1, dflt).min(cap))
