package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one source link: `<a class="src-ref" data-ck data-doi href="/source/ck">`, the shape the server renders too.
  * Hovering previews it ([[Preview]]'s delegated handler); clicking opens the source in a window (plain navigation with
  * a modifier key, as any link).
  */
object SourceLink:

  /** The identifier label: the DOI when there is one, else our citekey (the owner's rule). */
  def idLabel(citekey: String, doi: Option[String]): String =
    doi.map(_.trim).filter(_.nonEmpty).getOrElse(citekey)

  def href(citekey: String): String = s"/source/${HtmlAtoms.segment(citekey)}"

  def apply(citekey: String, doi: Option[String] = None, label: Option[String] = None): HtmlElement =
    a(
      cls := "src-ref",
      dataAttr("ck") := citekey,
      doi.filter(_.trim.nonEmpty).map(d => dataAttr("doi") := d),
      L.href := href(citekey),
      label.getOrElse(idLabel(citekey, doi)),
      onClick --> { ev =>
        if Router.plain(ev) then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.open(citekey, UiState.q.now().map("q" -> _).toMap)
      }
    )

  /** The same anchor as an HTML string, for map popups (which take markup, not elements). */
  def html(citekey: String, doi: Option[String], label: String): String =
    HtmlAtoms.sourceLink(citekey, doi, label)
