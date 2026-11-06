package com.histmersion.api.media
import com.histmersion.api.given
final case class SourceCoverParams(
    citekey: com.histmersion.api.library.Citekey,
    provider: com.histmersion.api.media.MediaItem.Provider = _root_.com.histmersion.api.media.MediaItem.Provider(MediaProvider.Calibre),
    key: SourceCoverParams.Key,
    mime: Option[com.histmersion.api.exchange.Bundle.Asset.MimeText] = None
)

object SourceCoverParams:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
