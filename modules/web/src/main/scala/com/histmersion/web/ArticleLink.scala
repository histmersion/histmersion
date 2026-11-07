package com.histmersion.web

import com.histmersion.api.HtmlAtoms
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one article link: `<a class="art-ref" data-slug href="/article/slug">` — the shape the server renders
  * ([[HtmlAtoms.articleLink]]), so [[Preview]] previews it the same everywhere.
  */
object ArticleLink:
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySlug = _root_.com.histmersion.web.ApiClient.ArticleSlug
  val ApplySlug: _root_.com.histmersion.web.ApiClient.ArticleSlug.type = _root_.com.histmersion.web.ApiClient.ArticleSlug

  def href(slug: com.histmersion.web.ApiClient.ArticleSlug): com.histmersion.web.ArticleLink.Href = s"/article/${HtmlAtoms.segment(slug)}"

  def apply(slug: com.histmersion.web.ArticleLink.ApplySlug, label: String): HtmlElement =
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
