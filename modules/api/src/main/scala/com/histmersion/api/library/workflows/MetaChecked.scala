package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class MetaChecked(
    checked: MetaChecked.Checked,
    differences: List[MetaChecked.Differences],
    note: MetaChecked.Note = MetaChecked.Note("")
) derives JsonCodec

object MetaChecked:
  type Checked = Checked.T
  object Checked extends Newtype[Boolean, Pure]
  type Differences = Differences.T
  object Differences extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
