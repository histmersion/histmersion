package com.histmersion
package api.settings
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ConfigFileInfo(
    path: Option[ConfigFileInfo.Path] = None,
    loaded: ConfigFileInfo.Loaded = ConfigFileInfo.Loaded(false),
    unknownKeys: List[ConfigFileInfo.UnknownKeys] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object ConfigFileInfo:

  type Path = Path.T
  object Path extends Newtype[String, Pure]
  type Loaded = Loaded.T
  object Loaded extends Newtype[Boolean, Pure]
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends Newtype[String, Pure]

  def describe(c: ConfigFileInfo): String =
    c.path match
      case None    => ("none (no location: HOME is unset)")
      case Some(p) =>
        val state = if (c.loaded).value then "read" else "not found: defaults and environment only"
        val keys = if c.unknownKeys.isEmpty then ""
        else
          s"; ignored unknown ${Plural.noun(Plural.Count((c.unknownKeys.size).toLong), Plural.Noun.Singular("key"))}: ${c.unknownKeys.mkString(", ")}"
        (s"$p ($state$keys)")
