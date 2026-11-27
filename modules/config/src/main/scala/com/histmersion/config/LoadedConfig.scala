package com.histmersion
package config

import scala.annotation.tailrec
import org.ekrich.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.*
import com.histmersion.lint.{movesTo, FeaturePlace}

/** @param user
  *   the user config file as parsed (empty when there is none): the plugin host cuts each plugin's block,
  *   `histmersion.plugins.<id>`, out of it
  * @param env
  *   the environment the load read (main thread: a native image reads it through StackValue)
  * @param merged
  *   the resolved settings `config` was decoded from (`histmersion { … }` only): what a config reload compares key by
  *   key ([[ConfigReload.changed]])
  */
@movesTo(FeaturePlace.Config)
final case class LoadedConfig(
    config: AppConfig,
    file: UserConfigFile,
    user: Config = ConfigFactory.empty,
    env: Config = ConfigFactory.empty,
    merged: Config = ConfigFactory.empty
)
