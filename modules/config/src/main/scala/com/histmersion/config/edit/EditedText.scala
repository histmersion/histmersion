package com.histmersion
package config.edit

import scala.annotation.tailrec
import com.histmersion.lint.{FeaturePlace, movesTo}

/** The file after a set of changes: its new text, the keys that changed and the ones that already said so. */
@movesTo(FeaturePlace.UtilSettings)
final case class EditedText(text: ConfigFileState.Text, changed: List[SettingKey], unchanged: List[SettingKey])
