package com.histmersion.web

import com.histmersion.api.HtmlAtoms
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one article link: `<a class="art-ref" data-slug href="/article/slug">` — the shape the server renders
  * ([[HtmlAtoms.articleLink]]), so [[Preview]] previews it the same everywhere.
  */
object ArticleLink:
  type HrefResult = HrefResult.T
  object HrefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefSlug = HrefSlug.T
  object HrefSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySlug = ApplySlug.T
  object ApplySlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLabel = ApplyLabel.T
  object ApplyLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def href(slug: com.histmersion.web.ArticleLink.HrefSlug): com.histmersion.web.ArticleLink.HrefResult = s"/article/${HtmlAtoms.segment(slug)}"

  def apply(slug: com.histmersion.web.ArticleLink.ApplySlug, label: com.histmersion.web.ArticleLink.ApplyLabel): HtmlElement =
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
