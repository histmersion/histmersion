package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.values.PlaceKind

// ── a person's kind and period for a place (V080) ─────────────────────────────────────────────────

/** `POST /api/places/{id}/override` — what a person decides a place IS, over what its source says: `kind` and/or the
  * period `periodStart`..`periodEnd` (signed years; one side alone leaves the other open) and/or the POINT
  * `pointLat`/`pointLon` (V081: a place known by a point whose stored point is wrong; `reason` is then required — it
  * names the gazetteer value the point comes from). Given again, only what is passed changes — a kind, one side of the
  * period, the point. `reset: true` drops the override and puts back what the source says. Every re-import keeps it
  * (the database applies it, V080/V081).
  */
final case class PlaceOverrideSet(
    kind: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    reason: Option[String] = None,
    reset: Boolean = false,
    pointLat: Option[Double] = None,
    pointLon: Option[Double] = None
)
object PlaceOverrideSet:
  given Schema[PlaceOverrideSet] = KindlingsSchema
    .derived[PlaceOverrideSet]
    .schema
    .modify(_.kind)(_.description(s"one of ${PlaceKind.all.mkString(", ")}"))
    .modify(_.reason)(
      _.description("why: the evidence the decision rests on (required with a point: where the point comes from)")
    )
    .modify(_.pointLat)(
      _.description("latitude of the place's corrected point (with pointLon); only for a place known by a point")
    )
    .modify(_.pointLon)(_.description("longitude of the place's corrected point (with pointLat)"))
  given Codec.AsObject[PlaceOverrideSet] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceOverrideSet])
