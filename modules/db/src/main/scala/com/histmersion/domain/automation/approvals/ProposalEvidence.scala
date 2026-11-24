package com.histmersion
package domain.automation.approvals
import com.histmersion.util.json.JsonCodec.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What every approval's list shows of a proposal about a record of a domain (a source's status or edge, the library's
  * proposals): the evidence it rests on and the passage it quotes, read from its stored payload.
  */
final case class ProposalEvidence(
    evidence: Option[ProposalEvidence.Evidence] = None,
    quote: Option[ProposalEvidence.Quote] = None
) derives JsonCodec

object ProposalEvidence:
  type Evidence = Evidence.T
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Evidence extends Newtype[String, Pure]:
    given JsonValueCodec[Evidence] = JsonCodec.mapped[Evidence, String](_.value, b => Right(Evidence(b)))
  type Quote = Quote.T
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Quote extends Newtype[String, Pure]:
    given JsonValueCodec[Quote] = JsonCodec.mapped[Quote, String](_.value, b => Right(Quote(b)))
