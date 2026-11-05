package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class BitwardenVault(
    val name: String,
    exe: String,
    session: Option[String],
    field: String,
    timeout: FiniteDuration
)(using processes: com.histmersion.plugin.Processes) extends Secret.Vault:
  def resolve(ref: String): Either[String, String] =
    val (item, f) = Vaults.entryAndField(ref, field)
    for
      i <- Subprocess.plain("the item", item)
      g <- Subprocess.plain("the field", f)
      argv = List(exe, "get", g, i, "--nointeraction")
      env = session.map(s => Map("BW_SESSION" -> s)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(i))
    yield v

  private def classify(item: String)(err: String): Option[String] =
    if err.contains("vault is locked") || err.contains("not logged in") then
      Some(s"$name: the vault is locked — run `bw unlock` and set session = \"env:BW_SESSION\" for this vault")
    else if err.contains("more than one result") then Some(s"$name: '$item' matches more than one item — use its id")
    else if err.contains("not found") then Some(s"$name: no item '$item'")
    else None
