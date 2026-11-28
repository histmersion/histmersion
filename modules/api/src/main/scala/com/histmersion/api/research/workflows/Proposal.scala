package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.Approval
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure

final case class Proposal(
    kind: Approval.Kind,
    citekey: Citekey,
    status: Option[Source.Status] = None,
    edgeKind: Option[Edge.Kind] = None,
    target: Option[Citekey] = None,
    evidence: Proposal.Evidence = Proposal.Evidence(""),
    quote: Option[Proposal.Quote] = None
) derives JsonCodec, KindlingsSchema, Schema

object Proposal:
  type Evidence = Evidence.T
  object Evidence extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
