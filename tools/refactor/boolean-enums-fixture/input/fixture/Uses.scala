package com.histmersion
package fixture

object Uses:
  private val asked = Flags.Json(true)
  private val text = Flags.Json(false)
  def parsed(flag: Boolean): Flags.Json = Flags.Json(flag)
  def render(json: Flags.Json, body: String): String = if json.value then s"{\"body\":\"$body\"}" else body
  def both: List[Flags.Json] = List(asked, text).filter(j => !j.value)
  def quiet(flag: Boolean): Boolean = Flags.Quiet(flag).value
