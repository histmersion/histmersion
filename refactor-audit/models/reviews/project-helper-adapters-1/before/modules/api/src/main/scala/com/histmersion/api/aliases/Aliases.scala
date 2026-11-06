package com.histmersion.api.aliases
import com.histmersion.api.given

object Aliases:

  type Rule = Rule.T
  object Rule extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RuleResult = RuleResult.T
  object RuleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowedResult = FollowedResult.T
  object FollowedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowedAsked = FollowedAsked.T
  object FollowedAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowedNow = FollowedNow.T
  object FollowedNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteAsked = NoteAsked.T
  object NoteAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteNow = NoteNow.T
  object NoteNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteMd = NoteMd.T
  object NoteMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Rule: com.histmersion.api.aliases.Aliases.Rule =
    _root_.com.histmersion.api.aliases.Aliases.Rule("The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
      "reference) goes to the record under its new one, and the answer names the new one.")

  def rule(k: AliasKind): com.histmersion.api.aliases.Aliases.RuleResult =
    Rule.replaceFirst("The old one", s"The old ${k.idWord.value}").replace("new one", s"new ${k.idWord.value}")

  def followed(k: AliasKind, asked: com.histmersion.api.aliases.Aliases.FollowedAsked, now: com.histmersion.api.aliases.Aliases.FollowedNow): Option[com.histmersion.api.aliases.Aliases.FollowedResult] =
    val a = (asked).value.trim
    Option.when(a.nonEmpty && a != (now).value)(
      _root_.com.histmersion.api.aliases.Aliases.FollowedResult(s"_`$a` is an old ${k.idWord.value} of this ${k.noun.value} — it went to `$now`._")
    )

  def note(k: AliasKind, asked: com.histmersion.api.aliases.Aliases.NoteAsked, now: com.histmersion.api.aliases.Aliases.NoteNow)(md: com.histmersion.api.aliases.Aliases.NoteMd): com.histmersion.api.aliases.Aliases.NoteResult =
    followed(k, asked, now).fold(md)(n => s"$n\n\n$md")
