package com.histmersion
package web

import com.histmersion.api.instance.BuildId
import com.histmersion.api.instance.BuildId.Verdict
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The page and the server agree on a build (G109, review finding W6). The server names its web bundle's build in
  * `/api/config`, in the bundle's address and in a header of every answer ([[BuildId.Header]]); a page of the build
  * `page` compares it with its own. When the two differ — the server was upgraded while the page stayed open — the page
  * says so and offers a reload, instead of failing later on an answer whose shape it does not know.
  */
final class PageBuild(page: BuildId):

  /** Whether the server was seen running another build. Once set, it stays set until the page reloads. */
  val updated: Var[Boolean] = Var(false)

  /** The server's build as an answer named it (the config, a response header). */
  def check(server: BuildId): Unit =
    if !updated.now() && page.against(server) == Verdict.Updated then updated.set(true)

  /** The prompt: a line over the page with a reload button. What is shown stays readable under it. */
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

  /** This page's build. The build (build.sbt, `webBuild`) writes the linked bundle's id over this text when it copies
    * the bundle into the server; a bundle linked any other way (the development loop, the tests) keeps it, and then
    * compares nothing ([[BuildId.known]]). Read as a value, never compared with the literal: the linker would fold that
    * comparison before the id is written.
    */
  val compiled: BuildId = BuildId("HISTMERSION-BUILD-ID-UNSET-00000")

  /** This page's: the one prompt the app and the server's pages show. */
  val current: PageBuild = PageBuild(compiled)
