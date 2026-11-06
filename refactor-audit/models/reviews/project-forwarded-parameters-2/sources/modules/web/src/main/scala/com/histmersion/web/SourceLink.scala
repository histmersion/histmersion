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
  type IdLabelResult = IdLabelResult.T
  object IdLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdLabelCitekey = _root_.com.histmersion.web.SourceLink.HrefCitekey
  val IdLabelCitekey: _root_.com.histmersion.web.SourceLink.HrefCitekey.type = _root_.com.histmersion.web.SourceLink.HrefCitekey
  type IdLabelDoi = IdLabelDoi.T
  object IdLabelDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefResult = HrefResult.T
  object HrefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefCitekey = HrefCitekey.T
  object HrefCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyCitekey = _root_.com.histmersion.web.SourceLink.HrefCitekey
  val ApplyCitekey: _root_.com.histmersion.web.SourceLink.HrefCitekey.type = _root_.com.histmersion.web.SourceLink.HrefCitekey
  type ApplyDoi = _root_.com.histmersion.web.SourceLink.IdLabelDoi
  val ApplyDoi: _root_.com.histmersion.web.SourceLink.IdLabelDoi.type = _root_.com.histmersion.web.SourceLink.IdLabelDoi
  type ApplyLabel = ApplyLabel.T
  object ApplyLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlResult = HtmlResult.T
  object HtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlCitekey = HtmlCitekey.T
  object HtmlCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlDoi = HtmlDoi.T
  object HtmlDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlLabel = HtmlLabel.T
  object HtmlLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def idLabel(citekey: com.histmersion.web.SourceLink.IdLabelCitekey, doi: Option[com.histmersion.web.SourceLink.IdLabelDoi]): com.histmersion.web.SourceLink.IdLabelResult =
    doi.map(_.trim).filter(_.nonEmpty).getOrElse(citekey)

  def href(citekey: com.histmersion.web.SourceLink.HrefCitekey): com.histmersion.web.SourceLink.HrefResult = s"/source/${HtmlAtoms.segment(citekey)}"

  def apply(citekey: com.histmersion.web.SourceLink.ApplyCitekey, doi: Option[com.histmersion.web.SourceLink.ApplyDoi] = None, label: Option[com.histmersion.web.SourceLink.ApplyLabel] = None): HtmlElement =
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
  def html(citekey: com.histmersion.web.SourceLink.HtmlCitekey, doi: Option[com.histmersion.web.SourceLink.HtmlDoi], label: com.histmersion.web.SourceLink.HtmlLabel): com.histmersion.web.SourceLink.HtmlResult =
    HtmlAtoms.sourceLink(citekey, doi, label)
