package com.histmersion
package config

import org.ekrich.config.{
  Config,
  ConfigFactory,
  ConfigResolveOptions,
  ConfigResolver,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}
import com.histmersion.config.edit.SettingKind.valueTypeEquality

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The environment as a layer of HOCON: for every key of `defaults` whose value is exactly a `${?VAR}` the environment
  * sets, that variable's value. Laid OVER a user's settings, it makes the precedence environment > user file > defaults
  * hold although the defaults carry their overrides as substitutions (see [[ConfigLayers]] for why
  * `user.withFallback(defaults)` alone loses them). Used for `reference.conf` and for each plugin's defaults resource.
  * A variable is text; where the default it overrides is a list (`host = ["127.0.0.1"]`), the text is that list's
  * elements, comma-separated (`HISTMERSION_BIND=127.0.0.1,100.101.102.103`), and a config file may give that list as
  * the same text ([[textAsLists]]). The defaults are read without the process's own environment, which only `env`
  * stands for. Pure.
  */
object EnvOverrides:
  private val Marker = "\u0001histmersion-env\u0001"

  final private class EveryVariable extends ConfigResolver:
    def lookup(path: String): ConfigValue = ConfigValueFactory.fromAnyRef(Marker + path)
    def withFallback(fallback: ConfigResolver): ConfigResolver = this

  def layer(defaults: Config, env: Config): Config =
    val listed = lists(defaults)
    variables(defaults).foldLeft(ConfigFactory.empty) { case (acc, (key, variable)) =>
      Option(env.root.get(variable)) match
        case Some(value) => acc.withValue(key, if listed.contains(key) then elements(value) else value)
        case None        => acc
    }

  /** `settings` (resolved) with the text at each list setting of `defaults` that a variable can set read as that list:
    * `host = "127.0.0.1,100.101.102.103"` in a file is `host = ["127.0.0.1", "100.101.102.103"]`, as from the variable.
    */
  def textAsLists(defaults: Config, settings: Config): Config =
    lists(defaults).foldLeft(settings) { (acc, key) =>
      Try(acc.getValue(key)).toOption match
        case Some(text) if text.valueType == ConfigValueType.STRING => acc.withValue(key, elements(text))
        case _                                                      => acc
    }

  private def variables(defaults: Config): List[(String, String)] =
    Try(defaults.resolve(ConfigResolveOptions.noSystem.appendResolver(EveryVariable()))).toOption.toList
      .flatMap(_.entrySet.asScala.toList)
      .flatMap(e =>
        e.getValue.unwrapped match
          case s: String if s.startsWith(Marker) => List(e.getKey -> s.drop(Marker.length))
          case _                                 => Nil
      )

  private def lists(defaults: Config): Set[String] =
    Try(defaults.resolve(ConfigResolveOptions.noSystem)).toOption.toList
      .flatMap(plain =>
        variables(defaults)
          .map(_._1)
          .filter(key => Try(plain.getValue(key).valueType == ConfigValueType.LIST).getOrElse(false))
      )
      .toSet

  /** A variable's text as the list it gives: its comma-separated elements, trimmed, the empty ones left out. */
  private def elements(value: ConfigValue): ConfigValue =
    value.unwrapped match
      case text: String =>
        ConfigValueFactory.fromIterable(
          text.split(',').toList.map(_.trim).filter(_.nonEmpty).asJava,
          value.origin.description
        )
      case _ => value
