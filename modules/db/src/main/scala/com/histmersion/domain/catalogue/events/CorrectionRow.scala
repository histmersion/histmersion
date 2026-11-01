package com.histmersion.domain.catalogue.events

import com.histmersion.api.Correction
import com.histmersion.db.Jsonb
import com.augustnagro.magnum.DbCodec
import io.circe.Json

/** A `corrections` row: a person's value for one field, with what upstream said then and at the last refresh. */
final private[events] case class CorrectionRow(
    id: Long,
    targetKind: String,
    targetId: String,
    field: String,
    value: String,
    upstreamValue: String,
    status: String,
    upstream: Option[String],
    upstreamNow: Option[String],
    createdAt: String,
    checkedAt: Option[String]
) derives DbCodec:
  def toDto(label: Option[String] = None): Correction =
    Correction(
      id,
      targetKind,
      targetId,
      field,
      value,
      upstreamValue,
      status,
      upstream,
      upstreamNow,
      label,
      createdAt,
      checkedAt
    )
  def json(s: String): Json = Jsonb.read[Json](s).getOrElse(Json.Null)
