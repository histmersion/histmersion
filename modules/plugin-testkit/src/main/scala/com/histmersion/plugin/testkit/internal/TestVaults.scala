package com.histmersion
package plugin.testkit.internal

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{tolerated, LintRule}

/** The stand-in vaults of a test's `secrets`: `vault → (ref → value)`. */
final private[testkit] class TestVaults(
    id: Plugin.Info.Id,
    values: Map[Secret.Ref.Vault.Value, Map[Secret.Ref.Vault.Ref, Secret.Resolved]]
) extends Plugin:
  type Config = Unit
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val info = Plugin.Info(id, Plugin.Info.Version("0"), Plugin.Info.SpiVersion(Spi.Version))
  override given configReader: ConfigReader[Unit] = new ConfigReader[Unit]:
    def from(v: org.ekrich.config.ConfigValue) = Right(())
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def register(config: Unit, ctx: Plugin.Context): Either[Problems, Unit] =
    values.toList
      .foldLeft[Either[Problem, Unit]](Right(())) { case (acc, (vault, refs)) =>
        acc.flatMap(_ =>
          ctx.secretVaults.add(
            Registry.AddId(vault.value),
            new Secret.Vault:
              @tolerated(
                LintRule.G44,
                "pending G44 (raw value only in the newtype's companion or at a lifted boundary)"
              )
              val name = Secret.Vault.Name(vault.value)
              @tolerated(
                LintRule.G44,
                "pending G44 (raw value only in the newtype's companion or at a lifted boundary)"
              )
              def resolve(ref: Secret.Vault.ResolveRef) =
                refs
                  .get(Secret.Ref.Vault.Ref(ref.value))
                  .toRight(
                    Problem.Plugin
                      .Vault(Problem.Plugin.Id(s"$vault"), Problem.Plugin.Retrieval.Absent(Problem.Raw(s"$ref")))
                  )
          )
        )
      }
