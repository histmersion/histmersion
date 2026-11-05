package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaSample(
    targetType: com.histmersion.api.media.MediaTarget.TargetType,
    targetId: com.histmersion.api.media.MediaTarget.TargetId,
    targetLabel: MediaSample.TargetLabel,
    key: MediaSample.Key,
    title: MediaSample.Title,
    license: com.histmersion.api.media.MediaItem.License,
    basis: MediaSample.Basis,
    url: MediaSample.Url
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaSample:
  type TargetLabel = TargetLabel.T
  object TargetLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
