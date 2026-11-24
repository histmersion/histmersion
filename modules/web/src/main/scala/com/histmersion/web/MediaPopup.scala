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
import com.histmersion.api.{Href, Html}
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{LintRule, tolerated}

/** The media layer's popup (#1446): a source's figure names its source; a provider's picture shows its local thumbnail
  * with the credit line and licence its licence requires — the licence's name, linked to its text. A click on the
  * thumbnail opens the picture full size ([[Lightbox]]). Its title leads with the picture icon, as every card leads
  * with its kind's ([[CardIcons]]). Pure.
  */
object MediaPopup:
  type PeriodText = PeriodText.T
  object PeriodText extends Newtype[String, Pure]

  /** Pure: what the picture's full-size view shows. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  /** `caption` is markup: the picture's caption line as [[PointKinds]] builds it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def html(h: MediaGeoHit, period: PeriodText, caption: Html): Html =
    val kind = Fmt.display(UiLabels.Text(h.kind.map(_.value).getOrElse("media")))
    def title(inner: Html) =
      markup"""<div class="cite-tip-title media-popup-title">${CardIcons.html(
          Glyphs.Name(CardIcons.Media)
        )}$inner</div>"""
    h.citekey match
      case Some(ck) =>
        // the source as the app's one source link: its card on hover, its window on a click
        title(markup"""${SourceLink.html(ck, None, UiLabels.Text(ck.value))} / ${h.assetName}""") ++
          markup"$kind · $period$caption"
      case None =>
        val zoom = full(h).fold(Html.empty)(i => markup""" title="open full size"${Lightbox.attrs(i)}""")
        // the thumbnail only from an address of an allowed scheme
        val img = h.thumbUrl
          .flatMap(u => Href.image(u.value))
          .fold(Html.empty)(u =>
            val zoomable = if zoom.nonEmpty then " is-zoomable" else ""
            markup"""<img class="media-popup-thumb$zoomable" src="$u" alt="${h.assetName}" loading="lazy"$zoom><br>"""
          )
        val credit = h.credit.fold(Html.empty)(c => markup"""<span class="media-credit">$c</span><br>""")
        val lic =
          h.license.fold(Html.empty)(l => markup"""${LicenseName.html(l.value, h.licenseUrl.map(_.value))} · """)
        title(Html.text(h.assetName.value)) ++
          markup"""$img$credit$lic${MediaProvider.title(MediaProvider.Id(h.provider.map(_.value).getOrElse("")))}""" ++
          markup"""<br>$kind · $period$caption"""
