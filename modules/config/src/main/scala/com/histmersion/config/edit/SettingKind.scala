package com.histmersion.config.edit

import org.ekrich.config.{ConfigValue, ConfigValueType}

/** What kind of value a setting holds, as its default in reference.conf has it. */
enum SettingKind(val wire: SettingKind.Wire, val describe: SettingKind.Describe):
  case Text extends SettingKind(SettingKind.Wire("text"), SettingKind.Describe("text"))
  case Number extends SettingKind(SettingKind.Wire("number"), SettingKind.Describe("a number"))
  case Flag extends SettingKind(SettingKind.Wire("flag"), SettingKind.Describe("true or false"))
  case Items extends SettingKind(SettingKind.Wire("list"), SettingKind.Describe("a list [ … ]"))

  /** A block of settings (`backups { … }`): never one value. */
  case Block extends SettingKind(SettingKind.Wire("block"), SettingKind.Describe("a block of settings"))

  /** A key of an open block (a plugin's own settings): any single value. */
  case Any extends SettingKind(SettingKind.Wire("any"), SettingKind.Describe("a value"))

object SettingKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Describe = Describe.T
  object Describe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(v: ConfigValue): SettingKind = v.valueType match
    case ConfigValueType.STRING  => Text
    case ConfigValueType.NUMBER  => Number
    case ConfigValueType.BOOLEAN => Flag
    case ConfigValueType.LIST    => Items
    case ConfigValueType.OBJECT  => Block
    case _                       => Any

  def parse(s: com.histmersion.config.edit.SettingKind.Wire): Option[SettingKind] = values.find(_.wire.value == s.value.trim.toLowerCase)
