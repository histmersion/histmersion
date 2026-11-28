package com.histmersion
package api.aliases

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
final case class AliasKind(noun: RecordNoun, idWord: KeyWord)

object AliasKind:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  type Note = Note.T
  object Note extends Newtype[String, Pure]

  type RuleText = RuleText.T
  object RuleText extends Newtype[String, Pure]

  type Rule = RuleValue

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RuleValue = RuleText

  val RuleValue: RuleText.type = RuleText

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Rule: Rule =
    RuleText(
      "The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
        "reference) goes to the record under its new one, and the answer names the new one."
    )

  extension (k: AliasKind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def rule: RuleText =
      RuleText(
        (Rule).value
          .replaceFirst("The old one", s"The old ${k.idWord.value}")
          .replace("new one", s"new ${k.idWord.value}")
      )

  extension (k: AliasKind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def followed(asked: Id, now: Id): Option[Note] =
      val a = (asked).value.trim
      Option.when(a.nonEmpty && a != (now).value)(
        Note(s"_`$a` is an old ${k.idWord.value} of this ${k.noun.value} — it went to `$now`._")
      )

  extension (k: AliasKind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def note(asked: Id, now: Id)(md: Note): Note =
      k.followed(asked, now).fold(md)(n => Note(s"$n\n\n$md"))
