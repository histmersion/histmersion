package com.histmersion.cli

import io.github.iltotore.iron.*

/** One file of a new project: its path relative to the project directory, and its content. */
final case class TemplateFile(path: TemplateFile.Path, content: TemplateFile.Content)

object TemplateFile:
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Content = Content.T
  object Content extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
