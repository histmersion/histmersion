package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** A button that starts a WRITE. While the server wants an API key and none is held it is drawn locked and opens the
  * key window instead ([[Auth]]) — it asks for the key rather than failing.
  */
object WriteButton:
  def apply(icon: Icon, label: String, tip: String, action: () => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost",
      cls("locked") <-- Auth.writable.map(!_),
      title <-- Auth.writable.map(w => if w then tip else s"$tip — enter the API key first"),
      Icon(icon),
      span(label),
      onClick --> { _ => if Auth.writableNow then action() else WindowManager.open(WindowKey.ApiKey) }
    )
