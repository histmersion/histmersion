package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssetInfo(
    name: AssetInfo.Name,
    mime: AssetInfo.Mime,
    bytes: com.histmersion.api.ByteCount,
    sha256: com.histmersion.api.Sha256,
    width: Option[AssetInfo.Width] = None,
    height: Option[AssetInfo.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetInfo:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
