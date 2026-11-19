package com.histmersion
package api.instance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class DebounceConfig(
    suggestMs: DebounceConfig.SuggestMs,
    searchMs: DebounceConfig.SearchMs,
    reloadMs: DebounceConfig.ReloadMs,
    previewMs: DebounceConfig.PreviewMs
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DebounceConfig:
  type SuggestMs = SuggestMs.T
  object SuggestMs extends Newtype[Int, Pure]
  type SearchMs = SearchMs.T
  object SearchMs extends Newtype[Int, Pure]
  type ReloadMs = ReloadMs.T
  object ReloadMs extends Newtype[Int, Pure]
  type PreviewMs = PreviewMs.T
  object PreviewMs extends Newtype[Int, Pure]
