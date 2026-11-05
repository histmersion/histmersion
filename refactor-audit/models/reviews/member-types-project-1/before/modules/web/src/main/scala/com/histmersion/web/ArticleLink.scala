package com.histmersion.web

import com.histmersion.api.HtmlAtoms
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one article link: `<a class="art-ref" data-slug href="/article/slug">` — the shape the server renders
  * ([[HtmlAtoms.articleLink]]), so [[Preview]] previews it the same everywhere.
  */
object ArticleLink:
  def href(slug: String): String = s"/article/${HtmlAtoms.segment(slug)}"

  def apply(slug: String, label: String): HtmlElement =
    a(
      cls := "art-ref",
      dataAttr("slug") := slug,
      L.href := href(slug),
      label,
      onClick --> { ev =>
        if Router.plain(ev) then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.openSlug(slug)
      }
    )
