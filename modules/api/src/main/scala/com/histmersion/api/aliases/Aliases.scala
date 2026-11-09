package com.histmersion
package api.aliases
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

object Aliases:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type RuleText = RuleText.T
  object RuleText extends Newtype[String, Pure]

  type Rule = RuleValue
  type RuleValue = RuleText
  val RuleValue: RuleText.type = RuleText

  val Rule: Rule =
    RuleText(
      "The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
        "reference) goes to the record under its new one, and the answer names the new one."
    )

  def rule(k: AliasKind): RuleText =
    RuleText(
      (Rule).value
        .replaceFirst("The old one", s"The old ${k.idWord.value}")
        .replace("new one", s"new ${k.idWord.value}")
    )

  def followed(k: AliasKind, asked: Id, now: Id): Option[Note] =
    val a = (asked).value.trim
    Option.when(a.nonEmpty && a != (now).value)(
      Note(s"_`$a` is an old ${k.idWord.value} of this ${k.noun.value} — it went to `$now`._")
    )

  def note(k: AliasKind, asked: Id, now: Id)(md: Note): Note =
    followed(k, asked, now).fold(md)(n => Note(s"$n\n\n$md"))
