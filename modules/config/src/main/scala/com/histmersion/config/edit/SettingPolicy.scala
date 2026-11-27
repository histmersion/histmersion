package com.histmersion
package config.edit

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Whether a change may be written at all, against the schema: the key is a setting, the value is of its kind, a
  * credential is a reference (`value:` only with `literalConsent`). The warnings say what works but should be known (a
  * `value:` credential). Pure.
  */
@movesTo(FeaturePlace.UtilSettings)
object SettingPolicy:
  type LiteralConsent = LiteralConsent.T
  object LiteralConsent extends Newtype[Boolean, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]

  /** The settings that decide what agents may do: a person changes them, an agent's tool call never does (the owner's
    * rule that agents fetch legal copies alone). A key under one of them counts too.
    */
  val PersonHeld: List[SettingKey] = List(
    SettingKey("histmersion.acquire.allow-unauthorised"),
    SettingKey("histmersion.acquire.agents-may-use-unauthorised")
  )

  /** [[check]] of every edit, the first refusal ending it; the warnings of all of them. */

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CheckLiteralConsent = LiteralConsent
  val CheckLiteralConsent: LiteralConsent.type = LiteralConsent

  def all(
      edits: List[SettingEdit],
      schema: ConfigSchema,
      literalConsent: LiteralConsent
  ): Either[SettingError, List[Note]] =
    @scala.annotation.tailrec
    def loop(rest: List[SettingEdit], notes: List[Note]): Either[SettingError, List[Note]] = rest match
      case Nil       => Right(notes)
      case e :: tail =>
        check(e, schema, literalConsent) match
          case Left(err) => Left(err)
          case Right(ns) => loop(tail, notes ++ ns)
    loop(edits, Nil)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def check(
      edit: SettingEdit,
      schema: ConfigSchema,
      literalConsent: CheckLiteralConsent
  ): Either[SettingError, List[Note]] =
    val key = edit.key
    for
      field <- schema
        .field(key)
        .toRight(if schema.isBlock(key) then SettingError.NotAValue(key)
        else SettingError.UnknownKey(key, schema.closest(key)))
      _ <- Either.cond(field.kind != SettingKind.Block, (), SettingError.NotAValue(key))
      notes <- edit match
        case SettingEdit.Unset(_)  => Right(Nil)
        case SettingEdit.Set(_, v) => value(field, v, literalConsent)
    yield notes

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def value(
      field: SettingField,
      v: SettingValue,
      literalConsent: LiteralConsent
  ): Either[SettingError, List[Note]] =
    val key = field.key
    val kindOk = field.kind == SettingKind.Any || field.kind == v.kind
    if !kindOk then Left(SettingError.WrongKind(key, field.kind, v.kind))
    else
      field.secret match
        case SecretRule.NotSecret       => Right(Nil)
        case SecretRule.FromEnvironment => Left(SettingError.CredentialInFile(key, field.envVar))
        case SecretRule.Reference       =>
          v match
            case SettingValue.Text(t) =>
              SecretForm
                .reference(t.value)
                .left
                .map(SettingError.NotAReference(key, _))
                .flatMap {
                  case SecretForm.Literal if !literalConsent.value => Left(SettingError.LiteralNeedsConsent(key))
                  case SecretForm.Literal                          =>
                    Right(
                      List(
                        Note(
                          s"${key.relative} holds the key itself (value:): anyone who can read the settings file can read it."
                        )
                      )
                    )
                  case _ => Right(Nil)
                }
            case other => Left(SettingError.WrongKind(key, SettingKind.Text, other.kind))
