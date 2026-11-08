package com.histmersion
package config.edit

/** Where the value that applies to a setting comes from: environment > settings file > default. */
enum SettingSource derives CanEqual:
  case Default, File, Environment
