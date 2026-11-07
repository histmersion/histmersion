package com.histmersion.web

import com.histmersion.api.HtmlAtoms
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one source link: `<a class="src-ref" data-ck data-doi href="/source/ck">`, the shape the server renders too.
  * Hovering previews it ([[Preview]]'s delegated handler); clicking opens the source in a window (plain navigation with
  * a modifier key, as any link).
  */
object SourceLink:
  /** The identifier label: the DOI when there is one, else our citekey (the owner's rule). */

  def idLabel(citekey: com.histmersion.api.library.Citekey, doi: Option[com.histmersion.api.library.Doi]): com.histmersion.api.UiLabels.Text =
    com.histmersion.api.UiLabels.Text(doi.map(_.value.trim).filter(_.nonEmpty).getOrElse(citekey.value))

  def href(citekey: com.histmersion.api.library.Citekey): String = s"/source/${HtmlAtoms.segment(citekey.value)}"

  def apply(citekey: com.histmersion.api.library.Citekey, doi: Option[com.histmersion.api.library.Doi] = None, label: Option[com.histmersion.api.UiLabels.Text] = None): HtmlElement =
    a(
      cls := "src-ref",
      dataAttr("ck") := citekey.value,
      doi.filter(_.value.trim.nonEmpty).map(d => dataAttr("doi") := d.value),
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
  def html(citekey: com.histmersion.api.library.Citekey, doi: Option[com.histmersion.api.library.Doi], label: com.histmersion.api.UiLabels.Text): String =
    HtmlAtoms.sourceLink(citekey, doi, label.value)
