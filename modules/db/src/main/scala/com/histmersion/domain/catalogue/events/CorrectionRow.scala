package com.histmersion
package domain.catalogue.events
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.api.catalogue.Correction
import com.histmersion.util.database.Jsonb
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{tolerated, LintRule}

/** A `corrections` row: a person's value for one field, with what upstream said then and at the last refresh. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def valueJson: Json = Jsonb.read[Json](Jsonb.JsonText(value.value)).getOrElse(Json.Null)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def upstreamValueJson: Json = Jsonb.read[Json](Jsonb.JsonText(upstreamValue.value)).getOrElse(Json.Null)
