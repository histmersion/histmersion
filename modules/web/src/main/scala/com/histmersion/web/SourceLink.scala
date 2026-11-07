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
  type Doi = Doi.T
  object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The identifier label: the DOI when there is one, else our citekey (the owner's rule). */
  type IdLabelCitekey = _root_.com.histmersion.web.Preview.Target.Source.Citekey
  val IdLabelCitekey: _root_.com.histmersion.web.Preview.Target.Source.Citekey.type = _root_.com.histmersion.web.Preview.Target.Source.Citekey
  type ApplyCitekey = _root_.com.histmersion.web.Preview.Target.Source.Citekey
  val ApplyCitekey: _root_.com.histmersion.web.Preview.Target.Source.Citekey.type = _root_.com.histmersion.web.Preview.Target.Source.Citekey
  type ApplyDoi = _root_.com.histmersion.web.SourceLink.Doi
  val ApplyDoi: _root_.com.histmersion.web.SourceLink.Doi.type = _root_.com.histmersion.web.SourceLink.Doi

  def idLabel(citekey: com.histmersion.web.SourceLink.IdLabelCitekey, doi: Option[com.histmersion.web.SourceLink.Doi]): com.histmersion.api.UiLabels.Text =
    doi.map(_.trim).filter(_.nonEmpty).getOrElse(citekey)

  def href(citekey: com.histmersion.web.Preview.Target.Source.Citekey): String = s"/source/${HtmlAtoms.segment(citekey)}"

  def apply(citekey: com.histmersion.web.SourceLink.ApplyCitekey, doi: Option[com.histmersion.web.SourceLink.ApplyDoi] = None, label: Option[com.histmersion.api.UiLabels.Text] = None): HtmlElement =
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
  def html(citekey: com.histmersion.web.Preview.Target.Source.Citekey, doi: Option[com.histmersion.web.SourceLink.Doi], label: com.histmersion.api.UiLabels.Text): String =
    HtmlAtoms.sourceLink(citekey, doi, label)
