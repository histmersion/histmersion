package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IssuePlace(
    leftGeometry: Option[IssuePlace.LeftGeometry] = None,
    rightGeometry: Option[IssuePlace.RightGeometry] = None,
    leftSpan: Option[IssueSpan] = None,
    rightSpan: Option[IssueSpan] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object IssuePlace:
  type LeftGeometry = LeftGeometry.T
  object LeftGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RightGeometry = RightGeometry.T
  object RightGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
