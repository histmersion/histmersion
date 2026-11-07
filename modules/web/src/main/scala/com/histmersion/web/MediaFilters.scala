package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.media.MediaCount

/** The media layer's two filter menus (#1446): provider and licence, each "all" or one value, their options the
  * server's counts (`/api/media/facets`, fetched once when the layer is first shown).
  */
object MediaFilters:
  type Choice = Choice.T
  object Choice extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceLabel = ChoiceLabel.T
  object ChoiceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Disabled = Disabled.T
  object Disabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val providers: Var[List[MediaCount]] = Var(Nil)
  val licences: Var[List[MediaCount]] = Var(Nil)
  // `var`: the facets are fetched once per page
  private var asked = false

  private def ask(): Unit =
    if !asked then
      asked = true
      ApiClient
        .mediaFacets()
        .foreach { f =>
          providers.set(f.providers)
          licences.set(f.licences)
        }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** Pure: a menu's options — "all", then each value with its count, the one chosen kept even when the counts no longer
    * list it.
    */
  def options(counts: List[MediaCount], chosen: Option[com.histmersion.web.MediaFilters.Choice], label: com.histmersion.web.MediaFilters.Choice => com.histmersion.web.MediaFilters.ChoiceLabel): List[(com.histmersion.web.MediaFilters.Choice, com.histmersion.web.MediaFilters.ChoiceLabel)] =
    val listed = counts.map(c => c.key -> s"${label(c.key)} (${c.n})")
    val kept = chosen.filterNot(c => counts.exists(_.key == c)).map(c => c -> label(c)).toList
    ("" -> "all") :: (listed ++ kept)

  def select(
      name: com.histmersion.web.MediaFilters.FieldName,
      field: Field[Option[com.histmersion.web.MediaFilters.FieldName]],
      counts: Signal[List[MediaCount]],
      label: com.histmersion.web.MediaFilters.Choice => com.histmersion.web.MediaFilters.ChoiceLabel,
      disabled: Signal[com.histmersion.web.MediaFilters.Disabled] = Val(com.histmersion.web.MediaFilters.Disabled(false)),
      why: com.histmersion.web.MediaFilters.Reason = com.histmersion.web.MediaFilters.Reason("")
  ): HtmlElement =
    IconSelect(
      s"media $name",
      field,
      counts.combineWith(field.signal).map { (cs, chosen) =>
        options(cs, chosen, label).map((v, t) =>
          IconSelect.Opt(
            v,
            t,
            if v.isEmpty then LayerFilters.layerIcon(PointKinds.Media)
            else LayerFilters.badge(Shape.Photo, CardIcons.Media)
          )
        )
      },
      disabled,
      why
    ).amend(onMountCallback(_ => ask()))
