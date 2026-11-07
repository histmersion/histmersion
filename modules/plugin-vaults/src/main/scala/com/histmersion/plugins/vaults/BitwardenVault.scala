package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class BitwardenVault(
    val name: BitwardenVault.Name,
    exe: BitwardenVault.Exe,
    session: Option[BitwardenVault.Session],
    field: BitwardenVault.Field,
    timeout: FiniteDuration
)(using processes: com.histmersion.plugin.Processes) extends Secret.Vault:
  def resolve(ref: com.histmersion.plugin.Secret.Vault.ResolveRef): Either[com.histmersion.plugins.vaults.BitwardenVault.ResolveResultPart1, com.histmersion.plugins.vaults.BitwardenVault.ResolveResultPart2] =
    val (item, f) = Vaults.entryAndField(ref, field)
    for
      i <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Vaults.Operation("the item"), item)
      g <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Vaults.Operation("the field"), f)
      argv = List(exe, "get", g, i, "--nointeraction")
      env = session.map(s => Map("BW_SESSION" -> s)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(i))
    yield v

  private def classify(item: com.histmersion.plugins.vaults.BitwardenVault.Item)(err: com.histmersion.plugins.vaults.BitwardenVault.Problem): Option[com.histmersion.plugins.vaults.BitwardenVault.Problem] =
    if (err).value.contains("vault is locked") || (err).value.contains("not logged in") then
      Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.Problem(s"$name: the vault is locked — run `bw unlock` and set session = \"env:BW_SESSION\" for this vault"))
    else if (err).value.contains("more than one result") then Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.Problem(s"$name: '$item' matches more than one item — use its id"))
    else if (err).value.contains("not found") then Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.Problem(s"$name: no item '$item'"))
    else None

object BitwardenVault:
  type Item = Item.T
  object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exe = com.histmersion.plugins.vaults.BitwardenConfig.Cli
  val Exe: _root_.com.histmersion.plugins.vaults.BitwardenConfig.Cli.type = _root_.com.histmersion.plugins.vaults.BitwardenConfig.Cli
  type Session = Session.T
  object Session extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = com.histmersion.plugins.vaults.BitwardenConfig.Field
  val Field: _root_.com.histmersion.plugins.vaults.BitwardenConfig.Field.type = _root_.com.histmersion.plugins.vaults.BitwardenConfig.Field

  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
