package com.histmersion.web.settings

import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*

/** The changes a Settings form holds before they are saved: each key's text to write, or `None` to take the setting out
  * of the file (back to its default).
  */
final class Draft:
  private val held = Var(Map.empty[String, Option[String]])

  def signal: Signal[Map[com.histmersion.web.settings.Draft.SignalResultPart1, Option[com.histmersion.web.settings.Draft.SignalResultPart2]]] = held.signal
  def set(key: com.histmersion.web.settings.Draft.SetKey, value: Option[com.histmersion.web.settings.Draft.SetValue]): Unit = held.update(_ + (key -> value))
  def forget(key: com.histmersion.web.settings.Draft.ForgetKey): Unit = held.update(_ - key)
  def clear(): Unit = held.set(Map.empty)
  def isEmpty: Signal[com.histmersion.web.settings.Draft.IsEmptyResult] = held.signal.map(_.isEmpty)
  def count: Signal[com.histmersion.web.settings.Draft.CountResult] = held.signal.map(_.size)
  def changes: List[ConfigEdit.Change] = held.now().toList.sortBy(_._1).map((k, v) => ConfigEdit.Change(k, v))

  /** What the form shows for `f`: the change held, else what the file says, else what applies. */
  def shown(f: ConfigEdit.Field): Signal[com.histmersion.web.settings.Draft.ShownResult] =
    held.signal.map(_.get(f.key) match
      case Some(Some(v)) => v
      case Some(None)    => Draft.plain(f.default)
      case None          => Draft.plain(f.value.orElse(f.effective).orElse(f.default)))

object Draft:
  /** A value as HOCON writes it, as a person types it: a string without its quotes. Pure. */
  type SignalResultPart1 = SignalResultPart1.T
  object SignalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignalResultPart2 = SignalResultPart2.T
  object SignalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetKey = SetKey.T
  object SetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetValue = SetValue.T
  object SetValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForgetKey = ForgetKey.T
  object ForgetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShownResult = ShownResult.T
  object ShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainResult = PlainResult.T
  object PlainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainV = PlainV.T
  object PlainV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOnResult = IsOnResult.T
  object IsOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOnText = IsOnText.T
  object IsOnText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def plain(v: Option[com.histmersion.web.settings.Draft.PlainV]): com.histmersion.web.settings.Draft.PlainResult =
    v.fold("") { s =>
      val t = s.trim
      if t.length >= 2 && t.startsWith("\"") && t.endsWith("\"") then
        t.drop(1).dropRight(1).replace("\\\"", "\"").replace("\\\\", "\\")
      else t
    }

  /** Is a flag's value on? Pure. */
  def isOn(text: com.histmersion.web.settings.Draft.IsOnText): com.histmersion.web.settings.Draft.IsOnResult = Set("true", "yes", "on")(text.trim.toLowerCase)
