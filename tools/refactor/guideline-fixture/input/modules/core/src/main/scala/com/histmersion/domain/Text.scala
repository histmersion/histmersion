package com.histmersion
package domain

object Text:
  def shout(text: String, times: Int): String = text * times
  private def quiet(text: String): String = text
  def whisper(text: String): String = quiet(text)
