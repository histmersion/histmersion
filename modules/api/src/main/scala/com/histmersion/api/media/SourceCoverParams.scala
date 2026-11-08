package com.histmersion
package api.media
import com.histmersion.api.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure
final case class SourceCoverParams(
    citekey: Citekey,
    provider: MediaItem.Provider = MediaItem.Provider(MediaProvider.Calibre),
    key: SourceCoverParams.Key,
    mime: Option[Bundle.Asset.MimeText] = None
)

object SourceCoverParams:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
