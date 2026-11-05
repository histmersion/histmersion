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
  type OptionsResultPart1 = OptionsResultPart1.T
  object OptionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionsResultPart2 = OptionsResultPart2.T
  object OptionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionsChosen = OptionsChosen.T
  object OptionsChosen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionsLabelArg1 = OptionsLabelArg1.T
  object OptionsLabelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionsLabelResult = OptionsLabelResult.T
  object OptionsLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectName = SelectName.T
  object SelectName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectField = SelectField.T
  object SelectField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectLabelArg1 = SelectLabelArg1.T
  object SelectLabelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectLabelResult = SelectLabelResult.T
  object SelectLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectDisabled = SelectDisabled.T
  object SelectDisabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectWhy = SelectWhy.T
  object SelectWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def options(counts: List[MediaCount], chosen: Option[com.histmersion.web.MediaFilters.OptionsChosen], label: com.histmersion.web.MediaFilters.OptionsLabelArg1 => com.histmersion.web.MediaFilters.OptionsLabelResult): List[(com.histmersion.web.MediaFilters.OptionsResultPart1, com.histmersion.web.MediaFilters.OptionsResultPart2)] =
    val listed = counts.map(c => c.key -> s"${label(c.key)} (${c.n})")
    val kept = chosen.filterNot(c => counts.exists(_.key == c)).map(c => c -> label(c)).toList
    ("" -> "all") :: (listed ++ kept)

  def select(
      name: com.histmersion.web.MediaFilters.SelectName,
      field: Field[Option[com.histmersion.web.MediaFilters.SelectField]],
      counts: Signal[List[MediaCount]],
      label: com.histmersion.web.MediaFilters.SelectLabelArg1 => com.histmersion.web.MediaFilters.SelectLabelResult,
      disabled: Signal[com.histmersion.web.MediaFilters.SelectDisabled] = Val(false),
      why: com.histmersion.web.MediaFilters.SelectWhy = ""
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
