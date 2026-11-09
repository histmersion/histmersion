package com.histmersion
package config.edit

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import org.ekrich.config.ConfigUtil

import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure

/** The full path of one setting in the config file, `histmersion.<block>.<key>`: segments of letters, digits, `_` and
  * `-` (the plugin ids and HOCON's kebab-case keys).
  */
type SettingKeyRule = Match["histmersion(\\.[A-Za-z0-9_][A-Za-z0-9_-]*)+"]

type SettingKey = SettingKey.T

object SettingKey extends Newtype[String, SettingKeyRule]:
  type HoconPath = HoconPath.T
  object HoconPath extends Newtype[String, Pure]
  type Relative = Relative.T
  object Relative extends Newtype[String, Pure]
  type Segment = Segment.T
  object Segment extends Newtype[String, Pure]

  type ParseRaw = ParseRaw.T
  object ParseRaw extends Newtype[String, Pure]

  val Root = "histmersion"

  /** A key as a person or a client typed it: `backups.keep` and `histmersion.backups.keep` are the same key. */
  def parse(raw: ParseRaw): Either[Problem, SettingKey] =
    val t = Option(raw).map(_.value).getOrElse("").trim
    val full = if t == Root || t.startsWith(s"$Root.") then t else s"$Root.$t"
    either(full).left.map(_ =>
      Problem(
        s"'$t' is not a setting's name: dot-separated words of letters, digits, '_' and '-', such as backups.keep"
      )
    )

  extension (k: SettingKey)
    /** The path's words, `histmersion` first. */
    def segments: List[Segment] = k.value.split('.').toList.map(Segment(_))

    /** The key without `histmersion.`, as the documentation names it. */
    def relative: Relative = Relative(k.value.stripPrefix(s"$Root."))

    /** The last word: `keep` of `histmersion.backups.keep`. */
    def leaf: Segment = segments.last

    /** The path in HOCON's own syntax, each word quoted where HOCON needs it (`"1password"`). */
    def hoconPath: HoconPath = HoconPath(ConfigUtil.joinPath(segments.map(_.value).asJava))

    /** Is this key `other` or under it? */
    def within(other: SettingKey): Boolean = k == other || k.value.startsWith(other.value + ".")
    def parent: Option[SettingKey] =
      Option.when(segments.size > 2)(SettingKey.applyUnsafe(segments.init.mkString(".")))

  /** Words of a HOCON path (quoted segments unquoted), when `path` is one. */
  def fromHocon(path: HoconPath): Option[SettingKey] =
    Try(ConfigUtil.splitPath(path.value).asScala.toList).toOption.flatMap(ws => either(ws.mkString(".")).toOption)
