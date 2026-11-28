package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Window(index: Window.Index, heading: Option[Window.Heading], text: Window.Text) derives JsonCodec

object Window:
  type Index = Index.T
  object Index extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
