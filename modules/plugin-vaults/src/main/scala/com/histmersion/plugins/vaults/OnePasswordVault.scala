package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Processes
import com.histmersion.plugin.Secret

final class OnePasswordVault(
    val name: Secret.Vault.NameResult,
    exe: Vaults.Path,
    account: Option[OnePasswordConfig.Account],
    token: Option[Plugin.Context.SecretResultPart2],
    timeout: FiniteDuration
)(using processes: Processes) extends Secret.Vault:
  def resolve(ref: Secret.Vault.ResolveRef): Either[Secret.Vault.ResolveResultPart1, Secret.Vault.ResolveResultPart2] =
    val r = ref.value.trim
    for
      _ <- Subprocess.plain(Vaults.Operation("the reference"), Subprocess.Text(r)).left.map(e => Secret.Vault.ResolveResultPart1(e.value))
      uri = if r.startsWith("op://") then r else s"op://$r"
      argv = (List(exe.value, "read", uri) ++ account.toList.flatMap(a => List("--account", a.value))).map(a => Processes.RunArgv(a))
      env: Map[Subprocess.RunEnvPart1, Subprocess.RunEnvPart2] = token.map(t => Map(Subprocess.RunEnvPart1("OP_SERVICE_ACCOUNT_TOKEN") -> Subprocess.RunEnvPart2(t.value))).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(uri))
    yield v

  private def classify(uri: String)(err: String): Option[String] =
    if err.contains("not currently signed in") || err.contains("sign in") || err.contains("authorization") ||
      err.contains("session expired") || err.contains("locked")
    then Some(s"$name: not signed in — unlock the 1Password app, or set service-account-token = \"env:<NAME>\"")
    else if err.contains("isn't an item") || err.contains("could not find") || err.contains("not found") then
      Some(s"$name: no item at $uri")
    else None
