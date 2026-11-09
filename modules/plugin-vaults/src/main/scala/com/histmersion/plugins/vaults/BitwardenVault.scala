package com.histmersion
package plugins.vaults
import com.histmersion.util.problem.Problem

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Processes
import com.histmersion.plugin.Secret

final class BitwardenVault(
    val name: Secret.Vault.Name,
    exe: Vaults.Path,
    session: Option[Secret.Resolved],
    field: Vaults.Field,
    timeout: FiniteDuration
)(using processes: Processes)
    extends Secret.Vault:
  def resolve(ref: Secret.Vault.ResolveRef): Either[Problem, Secret.Resolved] =
    val (item, f) = Vaults.entryAndField(ref, field)
    for
      i <- Subprocess
        .plain(Vaults.Operation("the item"), Subprocess.Text(item.value))
      g <- Subprocess
        .plain(Vaults.Operation("the field"), Subprocess.Text(f.value))
      argv = List(exe.value, "get", g.value, i.value, "--nointeraction").map(a => Processes.RunArgv(a))
      env: Map[EnvVar.Name, EnvVar.Value] = session
        .map(s => Map(EnvVar.Name("BW_SESSION") -> EnvVar.Value(s.value)))
        .getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(i.value))
    yield v

  private def classify(item: String)(err: String): Option[String] =
    if err.contains("vault is locked") || err.contains("not logged in") then
      Some(s"$name: the vault is locked — run `bw unlock` and set session = \"env:BW_SESSION\" for this vault")
    else if err.contains("more than one result") then Some(s"$name: '$item' matches more than one item — use its id")
    else if err.contains("not found") then Some(s"$name: no item '$item'")
    else None
