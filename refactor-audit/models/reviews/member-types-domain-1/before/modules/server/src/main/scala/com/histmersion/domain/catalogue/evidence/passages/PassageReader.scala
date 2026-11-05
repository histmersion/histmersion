package com.histmersion.domain.catalogue.evidence.passages

/** One source's text, searched for passages: a phrase or a regular expression, each hit with its context; `limit`
  * passages at most.
  */
trait PassageReader:
  def find(
      query: String,
      regex: Boolean,
      limit: Int,
      context: Int,
      maxMatches: Int,
      timeoutMs: Long
  ): Either[String, List[Passage]]
