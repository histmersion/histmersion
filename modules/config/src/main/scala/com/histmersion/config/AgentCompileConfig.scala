package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `compile-article`: the filter is read up to `maxSources` sources (one extraction each), each extraction sees
  * `recordChars` of the record (summary, findings, notes, then the body) and keeps at most `maxClaims` claims; the
  * writer may write `draftMaxTokens` output tokens; `review` = chain a review-article run with fix after the article is
  * written.
  */
final case class AgentCompileConfig(
    maxSources: Int,
    recordChars: Int,
    maxClaims: Int,
    draftMaxTokens: Int,
    review: Boolean
) derives ConfigReader
