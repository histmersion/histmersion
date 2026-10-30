package com.histmersion.config

import org.ekrich.config.Config

/** The process environment, read once by the entry point and passed to whatever needs a variable. */
final case class Environment(variables: Map[String, String]):
  def get(name: String): Option[String] = variables.get(name)

  /** A non-blank value, trimmed. */
  def nonBlank(name: String): Option[String] = get(name).map(_.trim).filter(_.nonEmpty)

  def lookup: String => Option[String] = get

object Environment:
  val empty: Environment = Environment(Map.empty)

  /** The environment as the settings were loaded with it ([[LoadedConfig.env]]). */
  def of(env: Config): Environment =
    val read = AppConfig.lookup(env)
    Environment(env.root.keySet.toArray(Array.empty[String]).toList.flatMap(k => read(k).map(k -> _)).toMap)
