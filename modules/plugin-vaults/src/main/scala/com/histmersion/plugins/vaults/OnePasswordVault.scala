package com.histmersion
package plugins.vaults
import Vaults.{failed, said}
import com.histmersion.util.problem.{Problem, Problems}

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Processes
import com.histmersion.plugin.Secret
import com.histmersion.lint.{LintRule, tolerated}

final class OnePasswordVault(
    val name: Secret.Vault.Name,
    exe: Vaults.Path,
    account: Option[OnePasswordConfig.Account],
    token: Option[Secret.Resolved],
    timeout: FiniteDuration
)(using processes: Processes)
    extends Secret.Vault:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolve(ref: Secret.Vault.ResolveRef): Either[Problems, Secret.Resolved] =
    val r = ref.value.trim
    for
      _ <- Subprocess
        .plain(Vaults.Operation("the reference"), Subprocess.Text(r))
      uri = if r.startsWith("op://") then r else s"op://$r"
      argv = (List(exe.value, "read", uri) ++ account.toList.flatMap(a => List("--account", a.value))).map(a =>
        Processes.RunArgv(a)
      )
      env: Map[EnvVar.Name, EnvVar.Value] = token
        .map(t => Map(EnvVar.Name("OP_SERVICE_ACCOUNT_TOKEN") -> EnvVar.Value(t.value)))
        .getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(uri))
    yield v

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def classify(uri: String)(err: String): Option[Problem] =
    if err.contains("not currently signed in") || err.contains("sign in") || err.contains("authorization") ||
      err.contains("session expired") || err.contains("locked")
    then
      Some(
        name.said(
          Problem.Plugin.Claim.Misconfigured,
          "not signed in — unlock the 1Password app, or set service-account-token = \"env:<NAME>\""
        )
      )
    else if err.contains("isn't an item") || err.contains("could not find") || err.contains("not found") then
      Some(name.failed(Problem.Plugin.Retrieval.Absent(Problem.Raw(s"$uri"))))
    else None
