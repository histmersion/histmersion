package com.histmersion.web.settings

import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*

/** The changes a Settings form holds before they are saved: each key's text to write, or `None` to take the setting out
  * of the file (back to its default).
  */
final class Draft:
  private val held = Var(Map.empty[String, Option[String]])

  def signal: Signal[Map[com.histmersion.web.settings.Draft.Key, Option[com.histmersion.web.settings.Draft.Text]]] = held.signal
  def set(key: com.histmersion.web.settings.Draft.Key, value: Option[com.histmersion.web.settings.Draft.Text]): Unit = held.update(_ + (key -> value))
  def forget(key: com.histmersion.web.settings.Draft.Key): Unit = held.update(_ - key)
  def clear(): Unit = held.set(Map.empty)
  def isEmpty: Signal[Boolean] = held.signal.map(_.isEmpty)
  def count: Signal[com.histmersion.web.settings.Draft.Count] = held.signal.map(_.size)
  def changes: List[ConfigEdit.Change] = held.now().toList.sortBy(_._1).map((k, v) => ConfigEdit.Change(k, v))

  /** What the form shows for `f`: the change held, else what the file says, else what applies. */
  def shown(f: ConfigEdit.Field): Signal[com.histmersion.web.settings.Draft.Text] =
    held.signal.map(_.get(f.key) match
      case Some(Some(v)) => v
      case Some(None)    => Draft.plain(f.default)
      case None          => Draft.plain(f.value.orElse(f.effective).orElse(f.default)))

object Draft:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A value as HOCON writes it, as a person types it: a string without its quotes. Pure. */

  def plain(v: Option[com.histmersion.web.settings.Draft.Text]): com.histmersion.web.settings.Draft.Text =
    v.fold("") { s =>
      val t = s.trim
      if t.length >= 2 && t.startsWith("\"") && t.endsWith("\"") then
        t.drop(1).dropRight(1).replace("\\\"", "\"").replace("\\\\", "\\")
      else t
    }

  /** Is a flag's value on? Pure. */
  def isOn(text: com.histmersion.web.settings.Draft.Text): Boolean = Set("true", "yes", "on")(text.trim.toLowerCase)
