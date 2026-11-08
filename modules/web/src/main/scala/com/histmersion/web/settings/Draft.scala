package com.histmersion
package web.settings

import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure

/** The changes a Settings form holds before they are saved: each key's text to write, or `None` to take the setting out
  * of the file (back to its default).
  */
final class Draft:
  private val held = Var(Map.empty[ConfigEdit.Field.Key, Option[Draft.Text]])

  def signal: Signal[Map[ConfigEdit.Field.Key, Option[Draft.Text]]] = held.signal
  def set(key: ConfigEdit.Field.Key, value: Option[Draft.Text]): Unit = held.update(_ + (key -> value))
  def forget(key: ConfigEdit.Field.Key): Unit = held.update(_ - key)
  def clear(): Unit = held.set(Map.empty)
  def isEmpty: Signal[Boolean] = held.signal.map(_.isEmpty)
  def count: Signal[Draft.Count] = held.signal.map(m => Draft.Count(m.size))
  def changes: List[ConfigEdit.Change] = held.now().toList.sortBy(_._1.value).map((k, v) => ConfigEdit.Change(ConfigEdit.Change.Key(k.value), v.map(t => ConfigEdit.Change.Value(t.value))))

  /** What the form shows for `f`: the change held, else what the file says, else what applies. */
  def shown(f: ConfigEdit.Field): Signal[Draft.Text] =
    held.signal.map(_.get(f.key) match
      case Some(Some(v)) => v
      case Some(None)    => Draft.plain(f.default.map(d => Draft.Text(d.value)))
      case None          =>
        Draft.plain(
          f.value.map(x => Draft.Text(x.value)).orElse(f.effective.map(x => Draft.Text(x.value))).orElse(f.default.map(x => Draft.Text(x.value)))
        ))

object Draft:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  /** A value as HOCON writes it, as a person types it: a string without its quotes. Pure. */

  def plain(v: Option[Text]): Text =
    v.fold(Text("")) { s =>
      val t = s.value.trim
      Text(
        if t.length >= 2 && t.startsWith("\"") && t.endsWith("\"") then
          t.drop(1).dropRight(1).replace("\\\"", "\"").replace("\\\\", "\\")
        else t
      )
    }

  /** Is a flag's value on? Pure. */
  def isOn(text: Text): Boolean = Set("true", "yes", "on")(text.value.trim.toLowerCase)
