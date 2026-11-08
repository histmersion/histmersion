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
  * Pure.
  */
object EnvOverrides:
  private val Marker = "\u0001histmersion-env\u0001"

  def layer(defaults: Config, env: Config): Config =
    val names = env.root.keySet.asScala.toList
    val markers = ConfigValueFactory.fromMap(names.map(n => n -> (Marker + n)).toMap.asJava).toConfig
    val own = defaults.root.keySet.asScala.toSet
    val marked = defaults.withFallback(markers).resolve()
    marked.entrySet.asScala.foldLeft(ConfigFactory.empty) { (acc, e) =>
      // the markers' own keys (the variable names at the root) are no setting of `defaults`
      val first = ConfigUtil.splitPath(e.getKey).asScala.headOption
      e.getValue.unwrapped match
        case s: String if first.exists(own) && s.startsWith(Marker) && env.root.containsKey(s.drop(Marker.length)) =>
          acc.withValue(e.getKey, env.root.get(s.drop(Marker.length)))
        case _ => acc
    }
