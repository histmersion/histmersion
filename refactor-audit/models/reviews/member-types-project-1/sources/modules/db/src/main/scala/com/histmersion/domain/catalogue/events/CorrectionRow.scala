package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.Correction
import com.histmersion.util.database.Jsonb
import com.augustnagro.magnum.DbCodec
import io.circe.Json

/** A `corrections` row: a person's value for one field, with what upstream said then and at the last refresh. */
final private[events] case class CorrectionRow(
    id: CorrectionRow.Id,
    targetKind: CorrectionRow.TargetKind,
    targetId: CorrectionRow.TargetId,
    field: CorrectionRow.Field,
    value: CorrectionRow.Value,
    upstreamValue: CorrectionRow.UpstreamValue,
    status: CorrectionRow.Status,
    upstream: Option[CorrectionRow.Upstream],
    upstreamNow: Option[CorrectionRow.UpstreamNow],
    createdAt: CorrectionRow.CreatedAt,
    checkedAt: Option[CorrectionRow.CheckedAt]
) derives DbCodec:
  def toDto(label: Option[com.histmersion.domain.catalogue.events.CorrectionRow.ToDtoLabel] = None): Correction =
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
  def json(s: com.histmersion.domain.catalogue.events.CorrectionRow.JsonS): Json = Jsonb.read[Json](s).getOrElse(Json.Null)

object CorrectionRow:
  type ToDtoLabel = ToDtoLabel.T
  object ToDtoLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonS = JsonS.T
  object JsonS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetKind = TargetKind.T
  object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpstreamValue = UpstreamValue.T
  object UpstreamValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Upstream = Upstream.T
  object Upstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpstreamNow = UpstreamNow.T
  object UpstreamNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
