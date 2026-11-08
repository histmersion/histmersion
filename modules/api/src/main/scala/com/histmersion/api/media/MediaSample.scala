package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaSample(
    targetType: MediaTarget.TargetType,
    targetId: MediaTarget.TargetId,
    targetLabel: MediaSample.TargetLabel,
    key: MediaSample.Key,
    title: MediaSample.Title,
    license: MediaItem.License,
    basis: MediaSample.Basis,
    url: MediaSample.Url
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MediaSample:
  type TargetLabel = TargetLabel.T
  object TargetLabel extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Basis = Basis.T
  object Basis extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
