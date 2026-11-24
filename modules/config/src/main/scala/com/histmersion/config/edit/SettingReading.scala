package com.histmersion
package config.edit

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** One setting as it stands: what the settings file writes for it, what applies (the environment, the file, the
  * default), and where that comes from. For a credential, `credential` is how it is held (`shown` the reference, where
  * it may be shown); `inFile` and `effective` are then for the server's own use only and are never shown. `locked`: why
  * it cannot be changed from Settings (a substitution, an included file).
  */
@movesTo(FeaturePlace.UtilSettings)
final case class SettingReading(
    field: SettingField,
    inFile: Option[SettingValue],
    effective: Option[SettingValue],
    source: SettingSource,
    credential: Option[SecretForm],
    shown: Option[SettingReading.Shown],
    locked: Option[SettingError]
)

object SettingReading:
  type Shown = Shown.T
  object Shown extends Newtype[String, Pure]
