package com.histmersion
package web

import scala.annotation.tailrec
import com.raquo.laminar.api.L.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.api.media.{MediaGeoHit, MediaProvider}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels

/** The media layer's popup (#1446): a source's figure names its source; a provider's picture shows its local thumbnail
  * with the credit line and licence its licence requires — the licence's name, linked to its text. A click on the
  * thumbnail opens the picture full size ([[Lightbox]]). Its title leads with the picture icon, as every card leads
  * with its kind's ([[CardIcons]]). Pure.
  */
object MediaPopup:
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  type Html = Html.T
  object Html extends Newtype[String, Pure]
  type PeriodText = PeriodText.T
  object PeriodText extends Newtype[String, Pure]
  /** Pure: what the picture's full-size view shows. */


  def full(h: MediaGeoHit): Option[Lightbox.Item] =
    h.fileUrl.map(u =>
      Lightbox.Item(
        u.value,
        h.assetName.value,
        Some(h.assetName.value),
        h.credit.map(_.value),
        h.author.map(_.value),
        h.license.map(_.value),
        h.licenseUrl.map(_.value),
        h.sourceUrl.map(_.value),
        h.provider.map(_.value)
      )
    )

  def html(h: MediaGeoHit, period: PeriodText, caption: Caption): Html =
    val kind = Fmt.display(UiLabels.Text(h.kind.map(_.value).getOrElse("media")))
    def title(inner: String) =
      s"""<div class="cite-tip-title media-popup-title">${CardIcons.html(Glyphs.Name(CardIcons.Media))}$inner</div>"""
    Html(h.citekey match
      case Some(ck) =>
        // the source as the app's one source link: its card on hover, its window on a click
        title(s"""${SourceLink.html(ck, None, UiLabels.Text(ck.value))} / ${Fmt.esc(h.assetName.value)}""") + s"$kind · $period$caption"
      case None =>
        val zoom = full(h).fold("")(i => s""" title="open full size"${Lightbox.attrs(i)}""")
        val img = h.thumbUrl.fold("")(u =>
          s"""<img class="media-popup-thumb${if zoom.nonEmpty then " is-zoomable" else ""}" src="${Fmt.esc(
              u.value
            )}" alt="${Fmt.esc(h.assetName.value)}" loading="lazy"$zoom><br>"""
        )
        val credit = h.credit.fold("")(c => s"""<span class="media-credit">${Fmt.esc(c.value)}</span><br>""")
        val lic = h.license.fold("")(l => s"""${LicenseName.html(l.value, h.licenseUrl.map(_.value))} · """)
        title(Fmt.esc(h.assetName.value)) +
          s"""$img$credit$lic${Fmt.esc(MediaProvider.title(MediaProvider.Id(h.provider.map(_.value).getOrElse(""))).value)}<br>$kind · $period$caption"""
    )
