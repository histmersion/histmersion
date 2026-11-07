package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class OnePasswordVault(
    val name: OnePasswordVault.Name,
    exe: OnePasswordVault.Exe,
    account: Option[OnePasswordVault.Account],
    token: Option[OnePasswordVault.Token],
    timeout: FiniteDuration
)(using processes: com.histmersion.plugin.Processes) extends Secret.Vault:
  def resolve(ref: com.histmersion.plugin.Secret.Vault.ResolveRef): Either[com.histmersion.plugins.vaults.OnePasswordVault.ResolveResultPart1, com.histmersion.plugins.vaults.OnePasswordVault.ResolveResultPart2] =
    val r = (ref).value.trim
    for
      _ <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Vaults.Operation("the reference"), _root_.com.histmersion.plugins.vaults.Subprocess.Text(r))
      uri = if r.startsWith("op://") then r else s"op://$r"
      argv = List(exe, "read", uri) ++ account.toList.flatMap(a => List("--account", a))
      env = token.map(t => Map("OP_SERVICE_ACCOUNT_TOKEN" -> t)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(_root_.com.histmersion.plugin.Secret.Vault.ResolveRef(uri)))
    yield v

  private def classify(uri: com.histmersion.plugin.Secret.Vault.ResolveRef)(err: com.histmersion.plugins.vaults.OnePasswordVault.Problem): Option[com.histmersion.plugins.vaults.OnePasswordVault.Problem] =
    if (err).value.contains("not currently signed in") || (err).value.contains("sign in") || (err).value.contains("authorization") ||
      (err).value.contains("session expired") || (err).value.contains("locked")
    then Some(_root_.com.histmersion.plugins.vaults.OnePasswordVault.Problem(s"$name: not signed in — unlock the 1Password app, or set service-account-token = \"env:<NAME>\""))
    else if (err).value.contains("isn't an item") || (err).value.contains("could not find") || (err).value.contains("not found") then
      Some(_root_.com.histmersion.plugins.vaults.OnePasswordVault.Problem(s"$name: no item at $uri"))
    else None

object OnePasswordVault:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exe = com.histmersion.plugins.vaults.OnePasswordConfig.Cli
  val Exe: _root_.com.histmersion.plugins.vaults.OnePasswordConfig.Cli.type = _root_.com.histmersion.plugins.vaults.OnePasswordConfig.Cli
  type Account = com.histmersion.plugins.vaults.OnePasswordConfig.Account
  val Account: _root_.com.histmersion.plugins.vaults.OnePasswordConfig.Account.type = _root_.com.histmersion.plugins.vaults.OnePasswordConfig.Account
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
