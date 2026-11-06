package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

/** The first-party secret vaults (docs/research-plugins.md §3.7): each a plugin, built into the server and DISABLED
  * until `histmersion.plugins.<id>.enabled = true`. Each one runs the vault's own CLI as a subprocess ([[Subprocess]])
  * and registers a [[Secret.Vault]] under its id, so `<id>:<ref>` references in any plugin's config resolve through it.
  * No master password is ever stored: a tool that needs one to unlock gets it from a [[Secret]] (`env:…`), or the vault
  * answers that it is locked.
  */
object Vaults:
  /** The version these plugins report (they ship with the server, and change with it). */
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryAndFieldResultPart1 = EntryAndFieldResultPart1.T
  object EntryAndFieldResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryAndFieldResultPart2 = EntryAndFieldResultPart2.T
  object EntryAndFieldResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryAndFieldRef = EntryAndFieldRef.T
  object EntryAndFieldRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryAndFieldDflt = EntryAndFieldDflt.T
  object EntryAndFieldDflt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionalResultPart1 = OptionalResultPart1.T
  object OptionalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionalResultPart2 = OptionalResultPart2.T
  object OptionalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandResult = ExpandResult.T
  object ExpandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandP = ExpandP.T
  object ExpandP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandHome = ExpandHome.T
  object ExpandHome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerResultPart1 = AnswerResultPart1.T
  object AnswerResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerResultPart2 = AnswerResultPart2.T
  object AnswerResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerVault = AnswerVault.T
  object AnswerVault extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerRef = AnswerRef.T
  object AnswerRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerRPart1 = AnswerRPart1.T
  object AnswerRPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerClassifyArg1 = AnswerClassifyArg1.T
  object AnswerClassifyArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerClassifyResult = AnswerClassifyResult.T
  object AnswerClassifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "0.1.0"

  /** The vault's own settings, shared by all three. */
  private[vaults] def timeout(seconds: com.histmersion.plugins.vaults.Vaults.TimeoutSeconds): FiniteDuration = (seconds).value.max(1).seconds

  /** Pure: `<entry>#<field>`, the field defaulting to `dflt` (split at the LAST `#`). */
  def entryAndField(ref: com.histmersion.plugins.vaults.Vaults.EntryAndFieldRef, dflt: com.histmersion.plugins.vaults.Vaults.EntryAndFieldDflt): (com.histmersion.plugins.vaults.Vaults.EntryAndFieldResultPart1, com.histmersion.plugins.vaults.Vaults.EntryAndFieldResultPart2) =
    (ref).value.lastIndexOf('#') match
      case -1 => (_root_.com.histmersion.plugins.vaults.Vaults.EntryAndFieldResultPart1((ref).value.trim), _root_.com.histmersion.plugins.vaults.Vaults.EntryAndFieldResultPart2((dflt).value))
      case i  => (_root_.com.histmersion.plugins.vaults.Vaults.EntryAndFieldResultPart1((ref).value.take(i).trim), Option((ref).value.drop(i + 1).trim).filter(_.nonEmpty).getOrElse(dflt))

  /** A Secret given in a vault's own settings (its unlock password, its session), resolved through the host; `None`
    * when the setting is `null`.
    */
  private[vaults] def optional(s: Option[Secret], ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.Vaults.OptionalResultPart1, Option[com.histmersion.plugins.vaults.Vaults.OptionalResultPart2]] =
    s.fold(Right(None))(ctx.secret(_).map(Some(_)))

  /** Pure: `~/x` against HOME. */
  private[vaults] def expand(p: com.histmersion.plugins.vaults.Vaults.ExpandP, home: Option[com.histmersion.plugins.vaults.Vaults.ExpandHome]): com.histmersion.plugins.vaults.Vaults.ExpandResult =
    home.filter(_ => p == _root_.com.histmersion.plugins.vaults.Vaults.ExpandP("~") || (p).value.startsWith("~/")).fold(p)(h => (h).value + (p).value.drop(1))

  /** Run the tool and turn its answer into the value or an error naming the reference — never the value. */
  private[vaults] def answer(
      vault: com.histmersion.plugins.vaults.Vaults.AnswerVault,
      ref: com.histmersion.plugins.vaults.Vaults.AnswerRef,
      r: Either[com.histmersion.plugins.vaults.Vaults.AnswerRPart1, Subprocess.Result],
      classify: com.histmersion.plugins.vaults.Vaults.AnswerClassifyArg1 => Option[com.histmersion.plugins.vaults.Vaults.AnswerClassifyResult]
  ): Either[com.histmersion.plugins.vaults.Vaults.AnswerResultPart1, com.histmersion.plugins.vaults.Vaults.AnswerResultPart2] =
    r.flatMap { res =>
      if res.exit == _root_.com.histmersion.plugin.Processes.Result.Exit(0) then
        val v = Subprocess.value(res.out)
        if v.isEmpty then Left(s"$vault: '$ref' has an empty value") else Right(v)
      else
        val low = res.err.toLowerCase(java.util.Locale.ROOT)
        Left(
          classify(low).getOrElse(s"$vault: could not read '$ref' (exit ${res.exit}: ${Subprocess.problem(res.err)})")
        )
    }
