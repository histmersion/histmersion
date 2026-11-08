package com.histmersion
package domain.catalogue.events

import com.histmersion.api.catalogue.Correction
import com.histmersion.util.database.Jsonb
import com.augustnagro.magnum.DbCodec
import io.circe.Json
import com.histmersion.util.database.NewtypeColumns.given

/** A `corrections` row: a person's value for one field, with what upstream said then and at the last refresh. */
final private[events] case class CorrectionRow(
    id: Correction.Id,
    targetKind: Correction.TargetKind,
    targetId: Correction.TargetId,
    field: Correction.Field,
    value: Correction.Value,
    upstreamValue: Correction.UpstreamValue,
    status: Correction.StatusValue,
    upstream: Option[Correction.Upstream],
    upstreamNow: Option[Correction.UpstreamNow],
    createdAt: Correction.CreatedAt,
    checkedAt: Option[Correction.CheckedAt]
) derives DbCodec:
  def toDto(label: Option[Correction.Label] = None): Correction =
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
  def valueJson: Json = Jsonb.read[Json](Jsonb.JsonText(value.value)).getOrElse(Json.Null)
  def upstreamValueJson: Json = Jsonb.read[Json](Jsonb.JsonText(upstreamValue.value)).getOrElse(Json.Null)
