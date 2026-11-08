package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class DebounceConfig(suggestMs: DebounceConfig.SuggestMs, searchMs: DebounceConfig.SearchMs, reloadMs: DebounceConfig.ReloadMs, previewMs: DebounceConfig.PreviewMs)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object DebounceConfig:
  type SuggestMs = SuggestMs.T
  object SuggestMs extends RefinedType[Int, Pure]
  type SearchMs = SearchMs.T
  object SearchMs extends RefinedType[Int, Pure]
  type ReloadMs = ReloadMs.T
  object ReloadMs extends RefinedType[Int, Pure]
  type PreviewMs = PreviewMs.T
  object PreviewMs extends RefinedType[Int, Pure]
