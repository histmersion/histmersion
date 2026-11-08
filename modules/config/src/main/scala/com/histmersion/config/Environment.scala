package com.histmersion
package config

import org.ekrich.config.Config
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The process environment, read once by the entry point and passed to whatever needs a variable. */
final case class Environment(variables: Map[Environment.VariablesKey, Environment.VariablesValue]):
  def get(name: Environment.VariablesKey): Option[Environment.VariablesValue] = variables.get(name)

  /** A non-blank value, trimmed. */
  def nonBlank(name: Environment.VariablesKey): Option[Environment.VariablesValue] = get(name).map(v => Environment.VariablesValue(v.value.trim)).filter(_.value.nonEmpty)

  def lookup: Environment.VariablesKey => Option[Environment.VariablesValue] = get

object Environment:

  type VariablesKey = VariablesKey.T
  object VariablesKey extends RefinedType[String, Pure]
  type VariablesValue = VariablesValue.T
  object VariablesValue extends RefinedType[String, Pure]

  val empty: Environment = Environment(Map.empty)

  /** The environment as the settings were loaded with it ([[LoadedConfig.env]]). */
  def of(env: Config): Environment =
    val read = AppConfig.lookup(env)
    Environment(env.root.keySet.toArray(Array.empty[String]).toList.flatMap(k => read(k).map(v => VariablesKey(k) -> VariablesValue(v))).toMap)
