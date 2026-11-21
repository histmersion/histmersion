package com.histmersion
package web

import com.histmersion.api.instance.BuildId
import com.histmersion.api.instance.BuildId.Verdict
import com.raquo.laminar.api.L.*
import org.scalajs.dom

final class PageBuild(page: BuildId):

  val updated: Var[Boolean] = Var(false)

  def check(server: BuildId): Unit =
    if !updated.now() && page.against(server) == Verdict.Updated then updated.set(true)

  lazy val element: HtmlElement =
    div(
      cls := "build-updated",
      role := "alert",
      cls("is-on") <-- updated.signal,
      span("Histmersion was updated — reload to use the new version. "),
      button(
        typ := "button",
        cls := "build-reload",
        "Reload",
        onClick --> (_ => dom.window.location.reload())
      )
    )

object PageBuild:

  val compiled: BuildId = BuildId("HISTMERSION-BUILD-ID-UNSET-00000")

  val current: PageBuild = PageBuild(compiled)
