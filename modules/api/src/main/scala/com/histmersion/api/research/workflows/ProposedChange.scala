package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("ProposalAnswer")))
final case class ProposedChange(
      @description("status (change citekey's status) | edge (a disagreement edge from citekey to target)") kind: ProposedChange.Kind,
      @description("The source the change is ON (an edge starts here)") citekey: ProposedChange.Citekey,
      @description("For kind=status: foundational | current | contested | superseded | debunked | retracted") status: Option[ProposedChange.Status] = None,
      @description("For kind=edge: challenges | refutes | updates | superseded_by (from the older source to the newer)") edgeKind: Option[ProposedChange.EdgeKind] = None,
      @description("For kind=edge: the citekey the edge points to") target: Option[ProposedChange.Target] = None,
      @description("Why, in one or two sentences") evidence: ProposedChange.Evidence,
      @description("The sentence of citekey's (or target's) record that shows it, copied EXACTLY") quote: ProposedChange.Quote
  ) derives JsonCodec, KindlingsSchema, Schema

object ProposedChange:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  export Source.Status.Wire as Status
  export Edge.Kind.Wire as EdgeKind
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Evidence = Evidence.T
  object Evidence extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
