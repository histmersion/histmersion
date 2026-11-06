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
  def resolve(ref: com.histmersion.plugins.vaults.OnePasswordVault.ResolveRef): Either[com.histmersion.plugins.vaults.OnePasswordVault.ResolveResultPart1, com.histmersion.plugins.vaults.OnePasswordVault.ResolveResultPart2] =
    val r = (ref).value.trim
    for
      _ <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Subprocess.PlainWhat("the reference"), r)
      uri = if r.startsWith("op://") then r else s"op://$r"
      argv = List(exe, "read", uri) ++ account.toList.flatMap(a => List("--account", a))
      env = token.map(t => Map("OP_SERVICE_ACCOUNT_TOKEN" -> t)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(_root_.com.histmersion.plugins.vaults.OnePasswordVault.ClassifyUri(uri)))
    yield v

  private def classify(uri: com.histmersion.plugins.vaults.OnePasswordVault.ClassifyUri)(err: com.histmersion.plugins.vaults.OnePasswordVault.ClassifyErr): Option[com.histmersion.plugins.vaults.OnePasswordVault.ClassifyResult] =
    if (err).value.contains("not currently signed in") || err.contains("sign in") || err.contains("authorization") ||
      err.contains("session expired") || err.contains("locked")
    then Some(_root_.com.histmersion.plugins.vaults.OnePasswordVault.ClassifyResult(s"$name: not signed in — unlock the 1Password app, or set service-account-token = \"env:<NAME>\""))
    else if (err).value.contains("isn't an item") || err.contains("could not find") || err.contains("not found") then
      Some(_root_.com.histmersion.plugins.vaults.OnePasswordVault.ClassifyResult(s"$name: no item at $uri"))
    else None

object OnePasswordVault:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exe = Exe.T
  object Exe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Account = Account.T
  object Account extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveRef = ResolveRef.T
  object ResolveRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResult = ClassifyResult.T
  object ClassifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyUri = ClassifyUri.T
  object ClassifyUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyErr = ClassifyErr.T
  object ClassifyErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
