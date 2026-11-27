package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class MediaSourceLine(
    citekey: Citekey,
    role: MediaSourceLine.Role,
    provider: MediaItem.Provider,
    detail: MediaSourceLine.Detail
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MediaSourceLine:
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
