package com.histmersion.config

import org.ekrich.config.Config

/** The process environment, read once by the entry point and passed to whatever needs a variable. */
final case class Environment(variables: Map[Environment.VariablesKey, Environment.VariablesValue]):
  def get(name: String): Option[String] = variables.get(name)

  /** A non-blank value, trimmed. */
  def nonBlank(name: String): Option[String] = get(name).map(_.trim).filter(_.nonEmpty)

  def lookup: String => Option[String] = get

object Environment:
  type VariablesKey = VariablesKey.T
  object VariablesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VariablesValue = VariablesValue.T
  object VariablesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: Environment = Environment(Map.empty)

  /** The environment as the settings were loaded with it ([[LoadedConfig.env]]). */
  def of(env: Config): Environment =
    val read = AppConfig.lookup(env)
    Environment(env.root.keySet.toArray(Array.empty[String]).toList.flatMap(k => read(k).map(k -> _)).toMap)
