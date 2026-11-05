package com.histmersion.cli

import io.github.iltotore.iron.*

/** One file of a new project: its path relative to the project directory, and its content. */
final case class TemplateFile(path: String, content: String)
