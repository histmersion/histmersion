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
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

final case class ConfigFileInfo(
    path: Option[ConfigFileInfo.Path] = None,
    loaded: ConfigFileInfo.Loaded = ConfigFileInfo.Loaded.Absent,
    unknownKeys: List[ConfigFileInfo.UnknownKeys] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object ConfigFileInfo:

  type Path = Path.T
  object Path extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  enum Loaded derives CanEqual:
    case Loaded, Absent
  object Loaded extends TwoStates[Loaded]:
    extension (loaded: Loaded) def loaded: Boolean = loaded == Loaded
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(c: ConfigFileInfo): String =
    c.path match
      case None    => ("none (no location: HOME is unset)")
      case Some(p) =>
        val state = if (c.loaded).loaded then "read" else "not found: defaults and environment only"
        val keys = if c.unknownKeys.isEmpty then ""
        else
          s"; ignored unknown ${Plural.noun(Plural.Count((c.unknownKeys.size).toLong), Plural.Noun.Singular("key"))}: ${c.unknownKeys.mkString(", ")}"
        (s"$p ($state$keys)")
