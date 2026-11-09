package com.histmersion
package plugin.testkit.internal

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader

/** The stand-in vaults of a test's `secrets`: `vault → (ref → value)`. */
final private[testkit] class TestVaults(
    id: Plugin.Info.Id,
    values: Map[Secret.Ref.Vault.Value, Map[Secret.Ref.Vault.Ref, Secret.Resolved]]
) extends Plugin:
  type Config = Unit
  val info = Plugin.Info(id, Plugin.Info.Version("0"), Plugin.Info.SpiVersion(Spi.Version))
  override given configReader: ConfigReader[Unit] = new ConfigReader[Unit]:
    def from(v: org.ekrich.config.ConfigValue) = Right(())
  def register(config: Unit, ctx: Plugin.Context): Either[Problem, Unit] =
    values.toList
      .foldLeft[Either[Problem, Unit]](Right(())) { case (acc, (vault, refs)) =>
        acc.flatMap(_ =>
          ctx.secretVaults.add(
            Registry.AddId(vault.value),
            new Secret.Vault:
              val name = Secret.Vault.Name(vault.value)
              def resolve(ref: Secret.Vault.ResolveRef) =
                refs
                  .get(Secret.Ref.Vault.Ref(ref.value))
                  .toRight(Problem.Unclassified(Legacy.Text(s"the test gives no secret ${vault.value}:${ref.value}")))
          )
        )
      }
