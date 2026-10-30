package com.histmersion.plugin.testkit.internal

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader

/** The stand-in vaults of a test's `secrets`: `vault → (ref → value)`. */
final private[testkit] class TestVaults(id: String, values: Map[String, Map[String, String]]) extends Plugin:
  type Config = Unit
  val info = PluginInfo(id, "0", Spi.Version)
  override given configReader: ConfigReader[Unit] = new ConfigReader[Unit]:
    def from(v: org.ekrich.config.ConfigValue) = Right(())
  def register(config: Unit, ctx: PluginContext): Either[String, Unit] =
    values.toList.foldLeft[Either[String, Unit]](Right(())) { case (acc, (vault, refs)) =>
      acc.flatMap(_ =>
        ctx.secretVaults.add(
          vault,
          new SecretVault:
            val name = vault
            def resolve(ref: String) = refs.get(ref).toRight(s"the test gives no secret $vault:$ref")
        )
      )
    }
