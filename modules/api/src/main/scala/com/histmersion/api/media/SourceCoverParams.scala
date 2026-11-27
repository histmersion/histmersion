package com.histmersion
package api.media
import com.histmersion.api.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SourceCoverParams(
    citekey: Citekey,
    provider: MediaItem.Provider = MediaItem.Provider(MediaProvider.Calibre),
    key: SourceCoverParams.Key,
    mime: Option[Bundle.Asset.MimeText] = None
)

object SourceCoverParams:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
