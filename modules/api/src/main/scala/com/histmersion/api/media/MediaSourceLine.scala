package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaSourceLine(citekey: com.histmersion.api.library.Citekey, role: MediaSourceLine.Role, provider: com.histmersion.api.media.MediaItem.Provider, detail: MediaSourceLine.Detail)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaSourceLine:
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
