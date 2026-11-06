package com.histmersion.config

import org.ekrich.config.Config

/** The process environment, read once by the entry point and passed to whatever needs a variable. */
final case class Environment(variables: Map[Environment.VariablesKey, Environment.VariablesValue]):
  def get(name: com.histmersion.config.Environment.VariablesKey): Option[com.histmersion.config.Environment.VariablesValue] = variables.get(name)

  /** A non-blank value, trimmed. */
  def nonBlank(name: com.histmersion.config.Environment.VariablesKey): Option[com.histmersion.config.Environment.VariablesValue] = get(name).map(v => Environment.VariablesValue(v.value.trim)).filter(_.value.nonEmpty)

  def lookup: com.histmersion.config.Environment.VariablesKey => Option[com.histmersion.config.Environment.VariablesValue] = get

object Environment:

  type VariablesKey = VariablesKey.T
  object VariablesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VariablesValue = VariablesValue.T
  object VariablesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: Environment = Environment(Map.empty)

  /** The environment as the settings were loaded with it ([[LoadedConfig.env]]). */
  def of(env: Config): Environment =
    val read = AppConfig.lookup(env)
    Environment(env.root.keySet.toArray(Array.empty[String]).toList.flatMap(k => read(k).map(v => VariablesKey(k) -> VariablesValue(v))).toMap)
