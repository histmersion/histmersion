package com.histmersion
package config.edit

import com.histmersion.lint.{movesTo, FeaturePlace}

/** Where the value that applies to a setting comes from: environment > settings file > default. */
@movesTo(FeaturePlace.Config)
enum SettingSource:
  case Default, File, Environment
