package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Excerpt(heading: Option[Excerpt.Heading], text: Excerpt.Text) derives JsonCodec

object Excerpt:
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
