package com.histmersion.config.edit

import scala.annotation.tailrec

/** The file after a set of changes: its new text, the keys that changed and the ones that already said so. */
final case class EditedText(text: ConfigFileState.Text, changed: List[SettingKey], unchanged: List[SettingKey])
