package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final case class MediaSample(
    targetType: MediaTarget.TargetType,
    targetId: MediaTarget.TargetId,
    targetLabel: MediaSample.TargetLabel,
    key: MediaSample.Key,
    title: MediaSample.Title,
    license: MediaItem.License,
    basis: MediaSample.Basis,
    url: MediaSample.Url
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MediaSample:
  type TargetLabel = TargetLabel.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetLabel extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Basis = Basis.T
  object Basis extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
