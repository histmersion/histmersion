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
  type Answer = Answer.T
  object Answer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Operation = Operation.T
  object Operation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The version these plugins report (they ship with the server, and change with it). */
  type OptionalResultPart2 = OptionalResultPart2.T
  object OptionalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerVault = AnswerVault.T
  object AnswerVault extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "0.1.0"

  /** The vault's own settings, shared by all three. */
  private[vaults] def timeout(seconds: com.histmersion.plugins.vaults.OnePasswordConfig.TimeoutSeconds): FiniteDuration = (seconds).value.max(1).seconds

  /** Pure: `<entry>#<field>`, the field defaulting to `dflt` (split at the LAST `#`). */
  def entryAndField(ref: com.histmersion.plugin.Secret.Vault.ResolveRef, dflt: com.histmersion.plugins.vaults.Vaults.Field): (com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.Vaults.Field) =
    (ref).value.lastIndexOf('#') match
      case -1 => (_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((ref).value.trim), _root_.com.histmersion.plugins.vaults.Vaults.Field((dflt).value))
      case i  => (_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((ref).value.take(i).trim), Option((ref).value.drop(i + 1).trim).filter(_.nonEmpty).getOrElse(dflt))

  /** A Secret given in a vault's own settings (its unlock password, its session), resolved through the host; `None`
    * when the setting is `null`.
    */
  private[vaults] def optional(s: Option[Secret], ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.Vaults.Problem, Option[com.histmersion.plugins.vaults.Vaults.OptionalResultPart2]] =
    s.fold(Right(None))(ctx.secret(_).map(Some(_)))

  /** Pure: `~/x` against HOME. */
  private[vaults] def expand(p: com.histmersion.plugins.vaults.Vaults.Path, home: Option[com.histmersion.plugins.vaults.Vaults.Path]): com.histmersion.plugins.vaults.Vaults.Path =
    home.filter(_ => p == _root_.com.histmersion.plugins.vaults.Vaults.Path("~") || (p).value.startsWith("~/")).fold(p)(h => (h).value + (p).value.drop(1))

  /** Run the tool and turn its answer into the value or an error naming the reference — never the value. */
  private[vaults] def answer(
      vault: com.histmersion.plugins.vaults.Vaults.AnswerVault,
      ref: com.histmersion.plugin.Secret.Vault.ResolveRef,
      r: Either[com.histmersion.plugin.Secret.Vault.ResolveRef, Subprocess.Result],
      classify: com.histmersion.plugins.vaults.Vaults.Problem => Option[com.histmersion.plugins.vaults.Vaults.Problem]
  ): Either[com.histmersion.plugins.vaults.Vaults.Problem, com.histmersion.plugins.vaults.Vaults.Answer] =
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
