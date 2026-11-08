package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `compile-article`: the filter is read up to `maxSources` sources (one extraction each), each extraction sees
  * `recordChars` of the record (summary, findings, notes, then the body) and keeps at most `maxClaims` claims; the
  * writer may write `draftMaxTokens` output tokens; `review` = chain a review-article run with fix after the article is
  * written.
  */
final case class AgentCompileConfig(
    maxSources: AgentCompileConfig.MaxSources,
    recordChars: AgentCompileConfig.RecordChars,
    maxClaims: AgentCompileConfig.MaxClaims,
    draftMaxTokens: AgentCompileConfig.DraftMaxTokens,
    review: AgentCompileConfig.Review
) derives ConfigReader

object AgentCompileConfig:
  type MaxSources = MaxSources.T
  object MaxSources extends RefinedType[Int, Pure]
  type RecordChars = RecordChars.T
  object RecordChars extends RefinedType[Int, Pure]
  type MaxClaims = MaxClaims.T
  object MaxClaims extends RefinedType[Int, Pure]
  type DraftMaxTokens = DraftMaxTokens.T
  object DraftMaxTokens extends RefinedType[Int, Pure]
  type Review = Review.T
  object Review extends RefinedType[Boolean, Pure]
