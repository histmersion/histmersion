package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssuePlace(
    leftGeometry: Option[IssuePlace.LeftGeometry] = None,
    rightGeometry: Option[IssuePlace.RightGeometry] = None,
    leftSpan: Option[IssueSpan] = None,
    rightSpan: Option[IssueSpan] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object IssuePlace:
  type LeftGeometry = LeftGeometry.T
  object LeftGeometry extends Newtype[String, Pure]
  type RightGeometry = RightGeometry.T
  object RightGeometry extends Newtype[String, Pure]
