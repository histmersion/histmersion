package com.histmersion
package api.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Window(index: Window.Index, text: Window.Text) derives JsonCodec

object Window:
  type Index = Index.T
  object Index extends Newtype[Int, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
