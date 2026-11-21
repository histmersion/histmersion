package com.histmersion
package web

import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js

/** The entry point of the standalone `/source/…` and `/article/…` pages: the same bundle as the web UI, wiring only
  * what a rendered page needs — link previews and in-page navigation.
  */
object StandalonePage:

  /** The server marks the page kind on `<body data-page>`; the web UI's own page has none. */

  def isStandalone: Boolean = Option(document.body).exists(_.hasAttribute("data-page"))

  def init(): Unit =
    Theme.install()
    PageTools.install()
    Preview.install()
    Option(document.getElementById("page-content")).foreach { root =>
      InPage.wire(root)
      InPage.wireShortcut(root)
    }
    // the prompt when the server runs another build than this page's (PageBuild)
    val host = document.createElement("div")
    val _ = document.body.appendChild(host)
    val _ = com.raquo.laminar.api.L.render(host, PageBuild.current.element)
    topBar()

  /** The web UI's top bar in place of the page's "← Histmersion" line: the four places stay one click away from a
    * source or an article opened in a tab of its own. Its links load the web UI.
    */
  private def topBar(): Unit =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    Option(document.querySelector("body > .nav")).foreach { nav =>
      ApiClient.config().foreach { ui =>
        PageBuild.current.check(ui.buildId)
        nav.textContent = ""
        nav.classList.add("has-top-bar")
        // a source and an article are both read in Library
        val _ = com.raquo.laminar.api.L.render(nav, TopBar.element(ui, TopBar.Host.Standalone(PlaceTab.Library)))
        Notices.refresh()
      }
    }
