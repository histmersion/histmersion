package com.histmersion
package web

import com.histmersion.api.Html.Atoms
import com.histmersion.api.research.Article
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.lint.{LintRule, tolerated}

/** The one article link: `<a class="art-ref" data-slug href="/article/slug">` — the shape the server renders
  * ([[Atoms.articleLink]]), so [[Preview]] previews it the same everywhere.
  */
object ArticleLink:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def href(slug: Article.Slug): String = s"/article/${Atoms.segment(slug.value)}"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(slug: Article.Slug, label: String): HtmlElement =
    a(
      cls := "art-ref",
      dataAttr("slug") := slug.value,
      L.href := href(slug),
      label,
      onClick --> { ev =>
        if Router.plain(ev) then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.openSlug(slug)
      }
    )
