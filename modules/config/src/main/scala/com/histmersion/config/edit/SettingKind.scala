package com.histmersion
package config.edit

import com.histmersion.util.problem.Problem

import org.ekrich.config.{ConfigValue, ConfigValueType}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** What kind of value a setting holds, as its default in reference.conf has it. */
@movesTo(FeaturePlace.UtilSettings)
enum SettingKind(val wire: SettingKind.Wire, val describe: SettingKind.Describe) derives CanEqual:
  case Text extends SettingKind(SettingKind.Wire("text"), SettingKind.Describe("text"))
  case Number extends SettingKind(SettingKind.Wire("number"), SettingKind.Describe("a number"))
  case Flag extends SettingKind(SettingKind.Wire("flag"), SettingKind.Describe("true or false"))
  case Items extends SettingKind(SettingKind.Wire("list"), SettingKind.Describe("a list [ … ]"))

  /** A block of settings (`backups { … }`): never one value. */
  case Block extends SettingKind(SettingKind.Wire("block"), SettingKind.Describe("a block of settings"))

  /** A key of an open block (a plugin's own settings): any single value. */
  case Any extends SettingKind(SettingKind.Wire("any"), SettingKind.Describe("a value"))

@tolerated(LintRule.CanEqualHome, "pending G67 (a foreign type's CanEqual in one file; its users share no module yet)")
object SettingKind:

  /** Two kinds of HOCON value compare with `==` and `!=`, also under `-language:strictEquality`: a setting's kind is
    * checked against the kind its key expects, by the config layers and by the plugin host. The one such instance on
    * the server's side, imported by name (`SettingKind.valueTypeEquality`) where kinds are compared.
    */
  given valueTypeEquality: CanEqual[ConfigValueType, ConfigValueType] = CanEqual.derived
  extension (kind: SettingKind)
    /** The kind as a problem names it. */
    def valueKind: Problem.Config.ValueKind = kind match
      case Number => Problem.Config.ValueKind.Number
      case Flag   => Problem.Config.ValueKind.Flag
      case Items  => Problem.Config.ValueKind.List
      case Block  => Problem.Config.ValueKind.Block
      case _      => Problem.Config.ValueKind.Text

  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Describe = Describe.T
  object Describe extends Newtype[String, Pure]

  def of(v: ConfigValue): SettingKind = v.valueType match
    case ConfigValueType.STRING  => Text
    case ConfigValueType.NUMBER  => Number
    case ConfigValueType.BOOLEAN => Flag
    case ConfigValueType.LIST    => Items
    case ConfigValueType.OBJECT  => Block
    case _                       => Any

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[SettingKind] = values.find(_.wire.value == s.value.trim.toLowerCase)
