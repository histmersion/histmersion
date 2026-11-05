package com.histmersion.config.edit

import scala.annotation.tailrec

/** The file after a set of changes: its new text, the keys that changed and the ones that already said so. */
final case class EditedText(text: EditedText.Text, changed: List[SettingKey], unchanged: List[SettingKey])

object EditedText:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
