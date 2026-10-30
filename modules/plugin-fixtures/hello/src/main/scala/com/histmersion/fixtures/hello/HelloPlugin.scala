package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: the smallest third-party plugin, compiled against plugin-api alone and packaged as
  * its own jar, which the relink tests and `scripts/bundle/smoke.sh` drop into a plugins directory
  * (docs/research-plugins.md §7). It began as spike A's `hello` (§10.1).
  *
  * Its greeter is chosen BY NAME from the config, so native-image cannot see the class statically: the reflective
  * lookup works in a relinked binary only because this jar carries its own
  * `META-INF/native-image/com.histmersion/plugin-hello/reachability-metadata.json`.
  */
final case class HelloConfig(greeting: String, greeter: String, mirrors: List[String]) derives ConfigReader

trait Greeter:
  def greet(who: String): String

final class LoudGreeter extends Greeter:
  def greet(who: String) = s"${who.toUpperCase(java.util.Locale.ROOT)}!"

final class QuietGreeter extends Greeter:
  def greet(who: String) = s"(${who.toLowerCase(java.util.Locale.ROOT)})"

/** The provider (META-INF/services/com.histmersion.plugin.Plugin): one open-access source fetcher that has no copy of
  * anything.
  */
final class HelloPlugin extends Plugin:
  type Config = HelloConfig
  val info = PluginInfo("hello", "0.1.0", Spi.Version)

  def register(config: HelloConfig, ctx: PluginContext): Either[String, Unit] =
    scala.util
      .Try(
        Class
          .forName(s"com.histmersion.fixtures.hello.${config.greeter}")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[Greeter]
      )
      .toEither
      .left
      .map(e => s"greeter '${config.greeter}': ${e.getClass.getSimpleName}: ${e.getMessage}")
      .flatMap { greeter =>
        ctx.log.info(s"hello: ${greeter.greet(config.greeting)} (${config.mirrors.size} mirror(s))")
        ctx.sourceFetchers.add("hello", HelloFetcher(config.mirrors.size))
      }

final class HelloFetcher(mirrors: Int) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    Fetch.Miss(s"hello has no copy (asked $mirrors mirror(s))")

/** Built for a FOREIGN SPI generation. Not listed in this jar's services file: the tests write a jar whose services
  * file names it, to see it refused before it is installed or linked.
  */
final class ForeignSpiPlugin extends Plugin:
  type Config = HelloConfig
  val info = PluginInfo("foreign-spi", "0.1.0", Spi.Version + 98)
  def register(config: HelloConfig, ctx: PluginContext): Either[String, Unit] = Right(())
