package com.histmersion.config

import org.ekrich.config.Config

/** The process environment, read once by the entry point and passed to whatever needs a variable. */
final case class Environment(variables: Map[Environment.VariablesKey, Environment.VariablesValue]):
  def get(name: com.histmersion.config.Environment.GetName): Option[com.histmersion.config.Environment.GetResult] = variables.get(name)

  /** A non-blank value, trimmed. */
  def nonBlank(name: com.histmersion.config.Environment.NonBlankName): Option[com.histmersion.config.Environment.NonBlankResult] = get(name).map(_.trim).filter(_.nonEmpty)

  def lookup: com.histmersion.config.Environment.LookupResultArg1 => Option[com.histmersion.config.Environment.LookupResultResult] = get

object Environment:
  type GetResult = GetResult.T
  object GetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetName = GetName.T
  object GetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NonBlankResult = NonBlankResult.T
  object NonBlankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NonBlankName = NonBlankName.T
  object NonBlankName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupResultArg1 = LookupResultArg1.T
  object LookupResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupResultResult = LookupResultResult.T
  object LookupResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type VariablesKey = VariablesKey.T
  object VariablesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VariablesValue = VariablesValue.T
  object VariablesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: Environment = Environment(Map.empty)

  /** The environment as the settings were loaded with it ([[LoadedConfig.env]]). */
  def of(env: Config): Environment =
    val read = AppConfig.lookup(env)
    Environment(env.root.keySet.toArray(Array.empty[String]).toList.flatMap(k => read(k).map(k -> _)).toMap)
