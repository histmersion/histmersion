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
  def resolve(ref: com.histmersion.plugins.vaults.BitwardenVault.ResolveRef): Either[com.histmersion.plugins.vaults.BitwardenVault.ResolveResultPart1, com.histmersion.plugins.vaults.BitwardenVault.ResolveResultPart2] =
    val (item, f) = Vaults.entryAndField(ref, field)
    for
      i <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Subprocess.PlainWhat("the item"), item)
      g <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Subprocess.PlainWhat("the field"), f)
      argv = List(exe, "get", g, i, "--nointeraction")
      env = session.map(s => Map("BW_SESSION" -> s)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(i))
    yield v

  private def classify(item: com.histmersion.plugins.vaults.BitwardenVault.ClassifyItem)(err: com.histmersion.plugins.vaults.BitwardenVault.ClassifyErr): Option[com.histmersion.plugins.vaults.BitwardenVault.ClassifyResult] =
    if (err).value.contains("vault is locked") || err.contains("not logged in") then
      Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.ClassifyResult(s"$name: the vault is locked — run `bw unlock` and set session = \"env:BW_SESSION\" for this vault"))
    else if (err).value.contains("more than one result") then Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.ClassifyResult(s"$name: '$item' matches more than one item — use its id"))
    else if (err).value.contains("not found") then Some(_root_.com.histmersion.plugins.vaults.BitwardenVault.ClassifyResult(s"$name: no item '$item'"))
    else None

object BitwardenVault:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exe = Exe.T
  object Exe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Session = Session.T
  object Session extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveRef = ResolveRef.T
  object ResolveRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResult = ClassifyResult.T
  object ClassifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyItem = ClassifyItem.T
  object ClassifyItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyErr = ClassifyErr.T
  object ClassifyErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
