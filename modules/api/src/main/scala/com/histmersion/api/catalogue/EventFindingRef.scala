package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventFindingRef(
    id: EventFindingRef.Id,
    citekey: Citekey,
    claim: EventFindingRef.Claim,
    km: EventFindingRef.Km,
    yearGap: EventFindingRef.YearGap,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventFindingRef:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Km = Km.T
  object Km extends Newtype[Double, Pure]
  type YearGap = YearGap.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object YearGap extends Newtype[Long, Pure]
