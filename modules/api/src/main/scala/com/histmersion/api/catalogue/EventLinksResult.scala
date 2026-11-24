package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventLinksResult(
    links: EventLinkList,
    attested: List[Attestation] = Nil,
    refused: List[EventLinksResult.Refused] = Nil,
    rejected: List[EventLinksResult.Rejected] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventLinksResult:
  type Refused = Refused.T
  object Refused extends Newtype[String, Pure]
  type Rejected = Rejected.T
  object Rejected extends Newtype[String, Pure]
