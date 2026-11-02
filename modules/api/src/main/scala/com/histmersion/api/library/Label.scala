package com.histmersion.api.library
import com.histmersion.api.given
object Label:
  def display(s: String): String = s.replace("_", " ")
