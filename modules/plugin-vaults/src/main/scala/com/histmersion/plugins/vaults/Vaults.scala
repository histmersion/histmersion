package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Secret
import com.histmersion.plugin.Plugin

/** The first-party secret vaults (docs/research-plugins.md §3.7): each a plugin, built into the server and DISABLED
  * until `histmersion.plugins.<id>.enabled = true`. Each one runs the vault's own CLI as a subprocess ([[Subprocess]])
  * and registers a [[Secret.Vault]] under its id, so `<id>:<ref>` references in any plugin's config resolve through it.
  * No master password is ever stored: a tool that needs one to unlock gets it from a [[Secret]] (`env:…`), or the vault
  * answers that it is locked.
  */
object Vaults:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type Operation = Operation.T
  object Operation extends Newtype[String, Pure]
  type Path = Path.T
  object Path extends Newtype[String, Pure]

  /** The version these plugins report (they ship with the server, and change with it). */
  val Version = "0.1.0"

  /** The vault's own settings, shared by all three. */
  private[vaults] def timeout(seconds: Int): FiniteDuration = seconds.max(1).seconds

  /** Pure: `<entry>#<field>`, the field defaulting to `dflt` (split at the LAST `#`). */
  def entryAndField(ref: Secret.Vault.ResolveRef, dflt: Field): (KeePassXcSession.Entry, Field) =
    ref.value.lastIndexOf('#') match
      case -1 => (KeePassXcSession.Entry(ref.value.trim), dflt)
      case i  =>
        (
          KeePassXcSession.Entry(ref.value.take(i).trim),
          Option(ref.value.drop(i + 1).trim).filter(_.nonEmpty).fold(dflt)(f => Field(f))
        )

  /** A Secret given in a vault's own settings (its unlock password, its session), resolved through the host; `None`
    * when the setting is `null`.
    */
  private[vaults] def optional(
      s: Option[Secret],
      ctx: Plugin.Context
  ): Either[Plugin.Context.SecretResultPart1, Option[Plugin.Context.SecretResultPart2]] =
    s.fold[Either[Plugin.Context.SecretResultPart1, Option[Plugin.Context.SecretResultPart2]]](Right(None))(secret =>
      ctx.secret(secret).map(Some(_))
    )

  /** Pure: `~/x` against HOME. */
  private[vaults] def expand(p: Path, home: Option[Plugin.Context.EnvResult]): Path =
    home.filter(_ => p == Path("~") || p.value.startsWith("~/")).fold(p)(h => Path(h.value + p.value.drop(1)))

  /** Run the tool and turn its answer into the value or an error naming the reference — never the value. */
  private[vaults] def answer[E](
      vault: Secret.Vault.NameResult,
      ref: Secret.Vault.ResolveRef,
      r: Either[E, Subprocess.Result],
      classify: String => Option[String]
  ): Either[Secret.Vault.ResolveResultPart1, Secret.Vault.ResolveResultPart2] =
    r.left.map(e => Secret.Vault.ResolveResultPart1(e.toString)).flatMap { res =>
      if res.exit.value == 0 then
        val v = Subprocess.value(Subprocess.ValueOut(res.out.value)).value
        if v.isEmpty then Left(Secret.Vault.ResolveResultPart1(s"$vault: '$ref' has an empty value"))
        else Right(Secret.Vault.ResolveResultPart2(v))
      else
        val low = res.err.value.toLowerCase(java.util.Locale.ROOT)
        Left(
          Secret.Vault.ResolveResultPart1(
            classify(low).getOrElse(
              s"$vault: could not read '$ref' (exit ${res.exit}: ${Subprocess.problem(Subprocess.Problem(res.err.value))})"
            )
          )
        )
    }
