package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Frame(index: Frame.Index, heading: Option[Frame.Heading], text: Frame.Text) derives JsonCodec

object Frame:
  type Index = Index.T
  object Index extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
