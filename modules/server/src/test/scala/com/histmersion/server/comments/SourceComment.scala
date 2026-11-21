package com.histmersion
package server
package comments

final case class SourceComment(start: Int, end: Int, firstLine: Int, lastLine: Int, text: String) derives CanEqual:
  def lines: Int = lastLine - firstLine + 1
