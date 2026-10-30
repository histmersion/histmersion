package com.histmersion.plugin.testkit.internal

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.LogLine
import com.histmersion.pluginhost.HostLog
import hearth.kindlings.sconfigderivation.ConfigReader

/** Every line logged while a harness ran: the plugin's own (through `PluginContext.log`), and the host's (the
  * acquisition chain, the polite clients). Guarded by `this`: a fetcher may log from several threads.
  */
final private[testkit] class Lines:
  private val buffer = List.newBuilder[LogLine]
  def all: List[LogLine] = synchronized(buffer.result())
  private def add(l: LogLine): Unit = synchronized(buffer += l)
  val plugin: (String, Boolean, String) => Unit = (id, warn, message) =>
    add(LogLine(if id.isEmpty then "plugins" else id, warn, message))
  val host: HostLog = new HostLog:
    def info(line: String) = add(LogLine("host", false, line))
    def warn(line: String) = add(LogLine("host", true, line))

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
