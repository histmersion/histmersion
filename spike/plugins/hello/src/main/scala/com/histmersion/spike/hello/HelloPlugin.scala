package com.histmersion.spike.hello

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given

/** The plugin's own config: a case class, its reader DERIVED. Keys are kebab-case in HOCON
  * (`max-items`), as everywhere in Histmersion. */
final case class HelloConfig(greeting: String, maxItems: Int, mirrors: List[String], greeter: String)
    derives ConfigReader

/** Chosen BY NAME from the config (`greeter`), so native-image cannot see the class statically: the
  * reflective lookup only works because the plugin jar carries its own reachability metadata. */
trait Greeter:
  def greet(who: String): String

final class LoudGreeter extends Greeter:
  def greet(who: String) = s"${who.toUpperCase}!"

final class QuietGreeter extends Greeter:
  def greet(who: String) = s"(${who.toLowerCase})"

/** The provider: a public class with a no-argument constructor, named in
  * META-INF/services/com.histmersion.plugin.SourceFetcherPlugin. */
final class HelloPlugin extends SourceFetcherPlugin[HelloConfig]:
  val info = PluginInfo("hello", PluginKind.SourceFetcher, "0.1.0", Spi.Version)

  def create(config: HelloConfig, host: HostServices): Either[String, SourceFetcher] =
    scala.util.Try(Class.forName(s"com.histmersion.spike.hello.${config.greeter}")
        .getDeclaredConstructor().newInstance().asInstanceOf[Greeter])
      .toEither.left.map(e => s"greeter '${config.greeter}': ${e.getClass.getSimpleName}: ${e.getMessage}")
      .map { greeter =>
        host.log(s"hello: ${greeter.greet(config.greeting)} max-items=${config.maxItems} mirrors=${config.mirrors.mkString(",")}")
        new SourceFetcher:
          val access = Access.Open
          def fetch(work: WorkKey) =
            work.doi.fold(Fetch.Miss("no DOI"))(d => Fetch.Miss(s"hello has no copy of $d (asked ${config.mirrors.size} mirrors)"))
      }
