package com.histmersion
package web.settings

import com.histmersion.web.{Route, RouteLink, SettingsSection}
import com.raquo.laminar.api.L.*

/** Settings › People and access: one person for now. */
object PeopleSection:
  def pane: HtmlElement =
    div(
      cls := "settings-section",
      sectionTag(
        cls := "settings-card",
        div(cls := "settings-card-head", h2(cls := "settings-card-title", "One person, one key")),
        div(
          cls := "settings-card-body",
          p(
            "Histmersion has no accounts yet: whoever can open this page can read everything. When the server is set to " +
              "ask for a key, every change needs it — see ",
            RouteLink(Route.Settings(SettingsSection.Keys), "Keys and passwords"),
            "."
          ),
          p(
            cls := "muted",
            "People with their own sign-in and roles will be managed here once Histmersion can share a collection."
          )
        )
      )
    )
