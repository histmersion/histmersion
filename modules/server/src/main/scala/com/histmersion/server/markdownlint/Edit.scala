package com.histmersion
package server.markdownlint

final case class Edit(start: Int, end: Int, replacement: String)
