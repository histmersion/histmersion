package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.TheMap
import com.histmersion.api.media.DataCredits

/** Settings › Credits: what the base map shown now credits, then every dataset Histmersion draws or reads with its
  * licence and the credit it asks for ([[DataCredits]]), and the software's licences.
  */
object CreditsView:
  def pane: HtmlElement =
    div(
      cls := "tool-pane credits-pane",
      h2(cls := "credits-title", "The base map shown now"),
      p(
        cls := "credits-now",
        onMountCallback { ctx =>
          val credits = if TheMap.isUp then TheMap.surface.credits else Nil
          ctx.thisNode.ref.innerHTML =
            if credits.isEmpty then "No base map is drawn." else BaseMaps.creditsHtml(credits)
        }
      ),
      h2(cls := "credits-title", "Data sources"),
      p(
        cls := "muted",
        "The places, boundaries, events and base maps the map draws come from these datasets, under these licences."
      ),
      table(
        cls := "credits-table",
        thead(tr(th("Dataset"), th("Used for"), th("Licence"), th("Credit"))),
        tbody(
          DataCredits.all.map(c =>
            tr(
              td(a(href := c.url.value, target := "_blank", rel := "noopener", c.name.value)),
              td(c.usedFor.value),
              td(
                c.licenceUrl.fold(span(c.licence.value))(u => a(href := u.value, target := "_blank", rel := "noopener", c.licence.value))
              ),
              td(c.credit.value)
            )
          )
        )
      ),
      h2(cls := "credits-title", "Pictures"),
      p("Each picture shows its own author, licence and source beside it."),
      h2(cls := "credits-title", "Software"),
      p(
        "Histmersion is free software under the AGPL 3.0. The libraries, fonts and icons it ships are listed with their " +
          "licences in its NOTICE and THIRD-PARTY files."
      )
    )
