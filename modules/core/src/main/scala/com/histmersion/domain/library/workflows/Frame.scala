package com.histmersion
package domain.library.workflows

import io.github.iltotore.iron.constraint.any.Pure

final case class Frame(index: Frame.Index, heading: Option[Frame.Heading], text: Frame.Text)

object Frame:
  type Index = Index.T
  object Index extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
