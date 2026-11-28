package com.histmersion
package domain.library.workflows

import io.github.iltotore.iron.constraint.any.Pure

final case class MetaChecked(
    checked: MetaChecked.Checked,
    differences: List[MetaChecked.Differences],
    note: MetaChecked.Note = MetaChecked.Note("")
)

object MetaChecked:
  type Checked = Checked.T
  object Checked extends Newtype[Boolean, Pure]
  type Differences = Differences.T
  object Differences extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
