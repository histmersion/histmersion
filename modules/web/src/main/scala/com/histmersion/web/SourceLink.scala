package com.histmersion
package web

import com.histmersion.api.Html
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.UiLabels
import com.histmersion.lint.{LintRule, tolerated}

/** The one source link: `<a class="src-ref" data-ck data-doi href="/source/ck">`, the shape the server renders too.
  * Hovering previews it ([[Preview]]'s delegated handler); clicking opens the source in a window (plain navigation with
  * a modifier key, as any link).
  */
object SourceLink:
  /** The identifier label: the DOI when there is one, else our citekey (the owner's rule). */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def idLabel(citekey: Citekey, doi: Option[Doi]): UiLabels.Text =
    UiLabels.Text(doi.map(_.value.trim).filter(_.nonEmpty).getOrElse(citekey.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def href(citekey: Citekey): String = s"/source/${Html.Atoms.segment(citekey.value)}"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(citekey: Citekey, doi: Option[Doi] = None, label: Option[UiLabels.Text] = None): HtmlElement =
    a(
      cls := "src-ref",
      dataAttr("ck") := citekey,
      doi.filter(_.value.trim.nonEmpty).map(d => dataAttr("doi") := d),
      L.href := href(citekey),
      label.getOrElse(idLabel(citekey, doi)).value,
      onClick --> { ev =>
        if Router.plain(ev) then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.open(citekey, UiState.q.now().map("q" -> _).toMap)
      }
    )

  /** The same anchor as an HTML string, for map popups (which take markup, not elements). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def html(citekey: Citekey, doi: Option[Doi], label: UiLabels.Text): Html =
    Html.Atoms.sourceLink(citekey, doi, label.value)
