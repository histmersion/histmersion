package com.histmersion.config.edit

/** One change to the config file: a setting written, or taken out (back to its default). */
enum SettingEdit(val key: SettingKey):
  case Set(setting: SettingKey, value: SettingValue) extends SettingEdit(setting)
  case Unset(setting: SettingKey) extends SettingEdit(setting)
