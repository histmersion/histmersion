package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** The WRITE side of the contract (plan §1.9a): DTOs for `put_source` / `patch_source` plus the pure merge that turns a
  * patch into a new [[Source]]. No I/O here — the db module persists the result, so every merge rule is unit-testable
  * without Postgres.
  *
  * Two operations, deliberately asymmetric:
  *   - `put_source` REPLACES everything for a citekey (create / deliberate re-import; what `seed` does),
  *   - `patch_source` touches ONLY the parts present in the request (enrichment; what agents do).
  *
  * Every field is optional with a `None`/`Nil` default so an omitted JSON key decodes to "absent" and therefore means
  * "leave alone".
  */

/** A finding as supplied by a writer: no id — the store allocates a stable `<citekey>#<n>`. */
final case class FindingInput(
    claim: String,
    region: Option[String] = None,
    lon: Option[Double] = None,
    lat: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    periodName: Option[String] = None,
    tags: List[String] = Nil,
    confidence: Option[String] = None,
    significance: Option[String] = None
)
object FindingInput:
  given Schema[FindingInput] = KindlingsSchema
    .derived[FindingInput]
    .schema
    .modify(_.confidence)(_.description("high | medium | low | contested — another word is refused"))

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[FindingInput] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingInput])

  /** Round-trip helper: an existing finding back into writer form (drops the id). */
  def of(f: Finding): FindingInput = FindingInput(
    claim = f.claim,
    region = f.region,
    lon = f.geo.map(_.lon),
    lat = f.geo.map(_.lat),
    periodStart = f.period.map(_.start),
    periodEnd = f.period.map(_.end),
    periodName = f.periodName,
    tags = f.tags,
    confidence = f.confidence.map(_.toString.toLowerCase),
    significance = f.significance
  )
