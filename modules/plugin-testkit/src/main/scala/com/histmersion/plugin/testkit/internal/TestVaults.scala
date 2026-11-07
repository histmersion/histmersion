package com.histmersion.plugin.testkit.internal

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader

/** The stand-in vaults of a test's `secrets`: `vault → (ref → value)`. */
final private[testkit] class TestVaults(id: Plugin.Info.Id, values: Map[Secret.Ref.Vault.Value, Map[Secret.Ref.Vault.Ref, Secret.Vault.ResolveResultPart2]]) extends Plugin:
  type Config = Unit
  val info = Plugin.Info(id, Plugin.Info.Version("0"), Plugin.Info.SpiVersion(Spi.Version))
  override given configReader: ConfigReader[Unit] = new ConfigReader[Unit]:
    def from(v: org.ekrich.config.ConfigValue) = Right(())
  def register(config: Unit, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    values.toList.foldLeft[Either[Registry.AddResultPart1, Unit]](Right(())) { case (acc, (vault, refs)) =>
      acc.flatMap(_ =>
        ctx.secretVaults.add(
          Registry.AddId(vault.value),
          new Secret.Vault:
            val name = Secret.Vault.NameResult(vault.value)
            def resolve(ref: Secret.Vault.ResolveRef) =
              refs
                .get(Secret.Ref.Vault.Ref(ref.value))
                .toRight(Secret.Vault.ResolveResultPart1(s"the test gives no secret ${vault.value}:${ref.value}"))
        )
      )
    }.left.map(e => Plugin.RegisterResultPart1(e.value))
