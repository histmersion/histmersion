package com.histmersion
package config

import scala.annotation.tailrec
import org.ekrich.config.{
  Config,
  ConfigException,
  ConfigFactory,
  ConfigObject,
  ConfigOrigin,
  ConfigParseOptions,
  ConfigRenderOptions,
  ConfigUtil,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}
import hearth.kindlings.sconfigderivation.ConfigDecodingError

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The environment as a layer of HOCON: for every key of `defaults` whose value is exactly a `${?VAR}` the environment
  * sets, that variable's value. Laid OVER a user's settings, it makes the precedence environment > user file > defaults
  * hold although the defaults carry their overrides as substitutions (see [[ConfigLayers]] for why
  * `user.withFallback(defaults)` alone loses them). Used for `reference.conf` and for each plugin's defaults resource.
  * A variable is text; where the default it overrides is a list (`host = ["127.0.0.1"]`), the text is that list's
  * elements, comma-separated (`HISTMERSION_BIND=127.0.0.1,100.101.102.103`). Pure.
  */
object EnvOverrides:
  private val Marker = "\u0001histmersion-env\u0001"

  def layer(defaults: Config, env: Config): Config =
    val names = env.root.keySet.asScala.toList
    val markers = ConfigValueFactory.fromMap(names.map(n => n -> (Marker + n)).toMap.asJava).toConfig
    val own = defaults.root.keySet.asScala.toSet
    val marked = defaults.withFallback(markers).resolve()
    // the defaults with no variable set: an unset `${?VAR}` leaves the value written before it
    val plain = Try(defaults.resolve()).toOption
    def isList(key: String): Boolean =
      plain.exists(p =>
        Try(p.getValue(key).valueType match
          case ConfigValueType.LIST => true
          case _                    => false).getOrElse(false)
      )
    marked.entrySet.asScala.foldLeft(ConfigFactory.empty) { (acc, e) =>
      // the markers' own keys (the variable names at the root) are no setting of `defaults`
      val first = ConfigUtil.splitPath(e.getKey).asScala.headOption
      e.getValue.unwrapped match
        case s: String if first.exists(own) && s.startsWith(Marker) && env.root.containsKey(s.drop(Marker.length)) =>
          val value = env.root.get(s.drop(Marker.length))
          acc.withValue(e.getKey, if isList(e.getKey) then elements(value) else value)
        case _ => acc
    }

  /** A variable's text as the list it gives: its comma-separated elements, trimmed, the empty ones left out. */
  private def elements(value: ConfigValue): ConfigValue =
    value.unwrapped match
      case text: String =>
        ConfigValueFactory.fromIterable(
          text.split(',').toList.map(_.trim).filter(_.nonEmpty).asJava,
          value.origin.description
        )
      case _ => value
