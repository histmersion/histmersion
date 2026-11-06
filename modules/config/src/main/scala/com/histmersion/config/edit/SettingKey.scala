package com.histmersion.config.edit

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import org.ekrich.config.ConfigUtil

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The full path of one setting in the config file, `histmersion.<block>.<key>`: segments of letters, digits, `_` and
  * `-` (the plugin ids and HOCON's kebab-case keys).
  */
type SettingKeyRule = Match["histmersion(\\.[A-Za-z0-9_][A-Za-z0-9_-]*)+"]

type SettingKey = SettingKey.T

object SettingKey extends RefinedType[String, SettingKeyRule]:
  type HoconPath = HoconPath.T
  object HoconPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Relative = Relative.T
  object Relative extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Segment = Segment.T
  object Segment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Root = "histmersion"

  /** A key as a person or a client typed it: `backups.keep` and `histmersion.backups.keep` are the same key. */
  def parse(raw: com.histmersion.config.edit.SettingKey.ParseRaw): Either[com.histmersion.config.edit.SettingKey.Problem, SettingKey] =
    val t = Option(raw).getOrElse("").trim
    val full = if t == Root || t.startsWith(s"$Root.") then t else s"$Root.$t"
    either(full).left.map(_ =>
      s"'$t' is not a setting's name: dot-separated words of letters, digits, '_' and '-', such as backups.keep"
    )

  extension (k: SettingKey)
    /** The path's words, `histmersion` first. */
    def segments: List[com.histmersion.config.edit.SettingKey.Segment] = k.value.split('.').toList

    /** The key without `histmersion.`, as the documentation names it. */
    def relative: com.histmersion.config.edit.SettingKey.Relative = k.value.stripPrefix(s"$Root.")

    /** The last word: `keep` of `histmersion.backups.keep`. */
    def leaf: com.histmersion.config.edit.SettingKey.Segment = segments.last

    /** The path in HOCON's own syntax, each word quoted where HOCON needs it (`"1password"`). */
    def hoconPath: com.histmersion.config.edit.SettingKey.HoconPath = ConfigUtil.joinPath(segments.asJava)

    /** Is this key `other` or under it? */
    def within(other: SettingKey): Boolean = k.value == other.value || k.value.startsWith(other.value + ".")
    def parent: Option[SettingKey] =
      Option.when(segments.size > 2)(SettingKey.applyUnsafe(segments.init.mkString(".")))

  /** Words of a HOCON path (quoted segments unquoted), when `path` is one. */
  def fromHocon(path: com.histmersion.config.edit.SettingKey.HoconPath): Option[SettingKey] =
    Try(ConfigUtil.splitPath(path).asScala.toList).toOption.flatMap(ws => either(ws.mkString(".")).toOption)
