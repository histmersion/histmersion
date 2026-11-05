package com.histmersion.web

import scala.annotation.tailrec
import com.raquo.laminar.api.L.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.api.media.{MediaGeoHit, MediaProvider}

/** The media layer's popup (#1446): a source's figure names its source; a provider's picture shows its local thumbnail
  * with the credit line and licence its licence requires — the licence's name, linked to its text. A click on the
  * thumbnail opens the picture full size ([[Lightbox]]). Its title leads with the picture icon, as every card leads
  * with its kind's ([[CardIcons]]). Pure.
  */
object MediaPopup:
  /** Pure: what the picture's full-size view shows. */
  def full(h: MediaGeoHit): Option[Lightbox.Item] =
    h.fileUrl.map(u =>
      Lightbox.Item(
        u,
        h.assetName,
        Some(h.assetName),
        h.credit,
        h.author,
        h.license,
        h.licenseUrl,
        h.sourceUrl,
        h.provider
      )
    )

  def html(h: MediaGeoHit, period: String, caption: String): String =
    val kind = Fmt.display(h.kind.getOrElse("media"))
    def title(inner: String) =
      s"""<div class="cite-tip-title media-popup-title">${CardIcons.html(CardIcons.Media)}$inner</div>"""
    h.citekey match
      case Some(ck) =>
        // the source as the app's one source link: its card on hover, its window on a click
        title(s"""${SourceLink.html(ck, None, ck)} / ${Fmt.esc(h.assetName)}""") + s"$kind · $period$caption"
      case None =>
        val zoom = full(h).fold("")(i => s""" title="open full size"${Lightbox.attrs(i)}""")
        val img = h.thumbUrl.fold("")(u =>
          s"""<img class="media-popup-thumb${if zoom.nonEmpty then " is-zoomable" else ""}" src="${Fmt.esc(
              u
            )}" alt="${Fmt.esc(h.assetName)}" loading="lazy"$zoom><br>"""
        )
        val credit = h.credit.fold("")(c => s"""<span class="media-credit">${Fmt.esc(c)}</span><br>""")
        val lic = h.license.fold("")(l => s"""${LicenseName.html(l, h.licenseUrl)} · """)
        title(Fmt.esc(h.assetName)) +
          s"""$img$credit$lic${Fmt.esc(MediaProvider.title(h.provider.getOrElse("")))}<br>$kind · $period$caption"""
