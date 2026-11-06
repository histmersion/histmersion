package com.histmersion.api.aliases
import com.histmersion.api.given

object Aliases:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RuleText = RuleText.T
  object RuleText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Rule = RuleValue
  type RuleValue = com.histmersion.api.aliases.Aliases.RuleText
  val RuleValue: _root_.com.histmersion.api.aliases.Aliases.RuleText.type = _root_.com.histmersion.api.aliases.Aliases.RuleText
  type RuleResult = com.histmersion.api.aliases.Aliases.RuleText
  val RuleResult: _root_.com.histmersion.api.aliases.Aliases.RuleText.type = _root_.com.histmersion.api.aliases.Aliases.RuleText

  val Rule: com.histmersion.api.aliases.Aliases.Rule =
    _root_.com.histmersion.api.aliases.Aliases.RuleText("The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
      "reference) goes to the record under its new one, and the answer names the new one.")

  def rule(k: AliasKind): com.histmersion.api.aliases.Aliases.RuleText =
    _root_.com.histmersion.api.aliases.Aliases.RuleText((Rule).value.replaceFirst("The old one", s"The old ${k.idWord.value}").replace("new one", s"new ${k.idWord.value}"))

  def followed(k: AliasKind, asked: com.histmersion.api.aliases.Aliases.Id, now: com.histmersion.api.aliases.Aliases.Id): Option[com.histmersion.api.aliases.Aliases.Id] =
    val a = (asked).value.trim
    Option.when(a.nonEmpty && a != (now).value)(
      _root_.com.histmersion.api.aliases.Aliases.Id(s"_`$a` is an old ${k.idWord.value} of this ${k.noun.value} — it went to `$now`._")
    )

  def note(k: AliasKind, asked: com.histmersion.api.aliases.Aliases.Id, now: com.histmersion.api.aliases.Aliases.Id)(md: com.histmersion.api.aliases.Aliases.Note): com.histmersion.api.aliases.Aliases.Note =
    followed(k, _root_.com.histmersion.api.aliases.Aliases.Id((asked).value), _root_.com.histmersion.api.aliases.Aliases.Id((now).value)).fold(md)(n => s"$n\n\n$md")
