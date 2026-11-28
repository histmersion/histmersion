package com.histmersion
package fixture

object Uses:
  private val asked = Flags.Json.Asked
  private val text = Flags.Json.Text
  def parsed(flag: Boolean): Flags.Json = (if flag then Flags.Json.Asked else Flags.Json.Text)
  def render(json: Flags.Json, body: String): String = if json.asked then s"{\"body\":\"$body\"}" else body
  def both: List[Flags.Json] = List(asked, text).filter(j => !j.asked)
  def quiet(flag: Boolean): Boolean = Flags.Quiet(flag).value
  def described(json: Flags.Json): String = s"json ${json.asked}, ${json.asked.toString}"
  def blocked(json: Flags.Json): String = s"json ${json.asked}!"
  val Asks: Flags.Json.type = Flags.Json
  def aliased: Flags.Json = Asks.Asked
  def mirrored(write: Flags.Write): Flags.Json = (if write.changes && !text.asked then Flags.Json.Asked else Flags.Json.Text)
