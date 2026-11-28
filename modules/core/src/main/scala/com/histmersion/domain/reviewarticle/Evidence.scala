package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.Proposal

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Evidence(
    citekey: library.Citekey,
    offset: Option[Evidence.Offset],
    quote: Evidence.Quote,
    ratio: Evidence.Ratio
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object Evidence:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  type Offset = com.histmersion.Offset
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Offset: com.histmersion.Offset.type = com.histmersion.Offset
  export Proposal.Quote
  export AgentVerify.Ratio

  extension (e: Evidence)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def evidenceLine: String =
      val at = e.offset.fold("")(o => s" @$o")
      s"[[${e.citekey}]]$at: \"${e.quote.value.replace("\n", " ").take(400)}\""
