package com.histmersion
package cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** One file of a new project: its path relative to the project directory, and its content. */
final case class TemplateFile(path: TemplateFile.Path, content: TemplateFile.Content)

object TemplateFile:
  type Path = Path.T
  object Path extends Newtype[String, Pure]
  type Content = Content.T
  object Content extends Newtype[String, Pure]
