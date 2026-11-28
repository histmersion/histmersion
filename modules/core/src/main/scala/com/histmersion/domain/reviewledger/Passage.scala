package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Passage(heading: Option[Passage.Heading], text: Passage.Text) derives JsonCodec

object Passage:
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
