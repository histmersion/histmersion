package com.histmersion.api.settings
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

final case class ConfigFileInfo(
    path: Option[ConfigFileInfo.Path] = None,
    loaded: ConfigFileInfo.Loaded = false,
    unknownKeys: List[ConfigFileInfo.UnknownKeys] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object ConfigFileInfo:


  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def describe(c: ConfigFileInfo): com.histmersion.api.settings.ConfigFileInfo.DescribeResult =
    c.path match
      case None    => "none (no location: HOME is unset)"
      case Some(p) =>
        val state = if c.loaded then "read" else "not found: defaults and environment only"
        val keys = if c.unknownKeys.isEmpty then ""
        else s"; ignored unknown ${Plural.noun(c.unknownKeys.size, "key")}: ${c.unknownKeys.mkString(", ")}"
        s"$p ($state$keys)"
