package com.histmersion
package domain.media.figures

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Which of a paper's own figures illustrates it on a hover card (#1446). Pure: the figures come from the OCR
  * conversion (the converter names them after their page, `fig-0004-03.png` = page 4, picture 3; docling's scans
  * `image_000003_<sha>.png`, in reading order), with their size, how often the text links each, and where the text
  * first does.
  *
  * Never a picture that is small (a logo, an icon), a strip (a journal masthead, a rule), one linked from the text
  * again and again (the converter stores a repeated picture ONCE, so a running header shows up as one figure with many
  * links), or a page scan (a scanned PDF hands every page over as a picture of one portrait size). Of the rest: the
  * largest, preferring the first pages — the title page's figure or the first map is what a reader recognises the paper
  * by.
  */
object FigurePick:

  type Page = Page.T
  object Page extends RefinedType[Int, Pure]
  type Order = Order.T
  object Order extends RefinedType[Int, Pure]
  type Rejection = Rejection.T
  object Rejection extends RefinedType[String, Pure]
  type Score = Score.T
  object Score extends RefinedType[Double, Pure]
  type ConvertedText = ConvertedText.T
  object ConvertedText extends RefinedType[String, Pure]

  /** One stored figure of a source. `refs`: how many times the text links it; `firstAt`: where the first link sits, as
    * a share of the text (0 = its start); both `None`/0 when the text has none.
    */
  final case class Figure(
      name: Figure.Name,
      width: Option[Figure.Width],
      height: Option[Figure.Height],
      refs: Figure.Refs = Figure.Refs(1),
      firstAt: Option[Figure.FirstAt] = None
  )

  object Figure:
    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Width = Width.T
    object Width extends RefinedType[Int, Pure]
    type Height = Height.T
    object Height extends RefinedType[Int, Pure]
    type Refs = Refs.T
    object Refs extends RefinedType[Int, Pure]
    type FirstAt = FirstAt.T
    object FirstAt extends RefinedType[Double, Pure]

  /** The thresholds (`media.illustrations`). */
  final case class Rules(
      minSide: Rules.MinSide = Rules.MinSide(160),
      minArea: Rules.MinArea = Rules.MinArea(40000),
      maxAspect: Rules.MaxAspect = Rules.MaxAspect(4.0),
      maxRefs: Rules.MaxRefs = Rules.MaxRefs(2),
      earlyPages: Rules.EarlyPages = Rules.EarlyPages(6),
      pageScanMin: Rules.PageScanMin = Rules.PageScanMin(5)
  )

  object Rules:
    type MinSide = MinSide.T
    object MinSide extends RefinedType[Int, Pure]
    type MinArea = MinArea.T
    object MinArea extends RefinedType[Int, Pure]
    type MaxAspect = MaxAspect.T
    object MaxAspect extends RefinedType[Double, Pure]
    type MaxRefs = MaxRefs.T
    object MaxRefs extends RefinedType[Int, Pure]
    type EarlyPages = EarlyPages.T
    object EarlyPages extends RefinedType[Int, Pure]
    type PageScanMin = PageScanMin.T
    object PageScanMin extends RefinedType[Int, Pure]

  private val PageName = """^fig-(\d{1,5})-\d+(?:-\d+)?\.[A-Za-z0-9]+$""".r
  private val SeqName = """^image_(\d{1,7})(?:_.*)?\.[A-Za-z0-9]+$""".r

  /** Pure: the page a figure's name says it is on (pymupdf4llm's `fig-<page>-<n>`, pages counted from 1 as the
    * converter writes them), `None` for any other name.
    */
  def page(name: Figure.Name): Option[Page] = name.value match
    case PageName(p) => p.toIntOption.map(Page(_))
    case _           => None

  /** Pure: a figure's order in its document by its name alone — the page for `fig-…`, the sequence number for docling's
    * `image_…`.
    */
  def order(name: Figure.Name): Option[Order] = page(name).map(p => Order(p.value)).orElse(name.value match
    case SeqName(n) => n.toIntOption.map(Order(_))
    case _          => None)

  /** Pure: the sizes that are page scans — `pageScanMin` or more portrait figures (height/width between 1.2 and 1.6)
    * whose sides agree within 2 %.
    */
  def pageScanSizes(figs: List[Figure], rules: Rules): Set[(Figure.Width, Figure.Height)] =
    val portrait = figs.flatMap(f =>
      for w <- f.width; h <- f.height if w.value > 0 && h.value.toDouble / w.value >= 1.2 && h.value.toDouble / w.value <= 1.6 yield (w, h)
    )
    def near(a: (Figure.Width, Figure.Height), b: (Figure.Width, Figure.Height)) =
      math.abs(a._1.value - b._1.value) <= a._1.value * 0.02 && math.abs(a._2.value - b._2.value) <= a._2.value * 0.02
    portrait.distinct.filter(s => portrait.count(near(s, _)) >= rules.pageScanMin.value.max(2)).toSet

  /** Pure: why a figure cannot illustrate its source, or `None` when it can. */
  def reject(f: Figure, rules: Rules, scans: Set[(Figure.Width, Figure.Height)] = Set.empty): Option[Rejection] =
    (f.width, f.height) match
      case (Some(w), Some(h)) if w.value > 0 && h.value > 0 =>
        val aspect = math.max(w.value, h.value).toDouble / math.min(w.value, h.value)
        if aspect > rules.maxAspect.value then Some(Rejection("a strip"))
        else if math.min(w.value, h.value) < rules.minSide.value then Some(Rejection("too small"))
        else if w.value.toLong * h.value < rules.minArea.value then Some(Rejection("too small"))
        else if f.refs.value > rules.maxRefs.value then Some(Rejection("repeated in the text"))
        else if scans.contains((w, h)) then Some(Rejection("a page scan"))
        else None
      case _ => Some(Rejection("no size"))

  /** Pure: the figure's weight — its area, halved past the early pages (or, without a page in its name, past the first
    * third of the text).
    */
  def score(f: Figure, rules: Rules): Score =
    val area = f.width.fold(0)(_.value).toDouble * f.height.fold(0)(_.value)
    val early = page(f.name) match
      case Some(p) => p.value <= rules.earlyPages.value
      case None    => f.firstAt.forall(_.value <= 1.0 / 3)
    Score(if early then area else area / 2)

  /** Pure: the illustration — the best-scoring acceptable figure; ties go to the earlier one. */
  def choose(figs: List[Figure], rules: Rules): Option[Figure] =
    val scans = pageScanSizes(figs, rules)
    figs
      .filter(reject(_, rules, scans).isEmpty)
      .sortBy(f => (-score(f, rules).value, order(f.name).fold(Int.MaxValue)(_.value), f.firstAt.fold(1.0)(_.value), f.name.value))
      .headOption

  private val AssetLink = """assets/([A-Za-z0-9._-]+)""".r

  /** Pure: every figure a converted text links (`assets/<name>`, the form the converter writes), with how many times
    * and where first (a share of the text) — one pass, whatever the number of figures.
    */
  def refs(body: ConvertedText): Map[Figure.Name, (Figure.Refs, Figure.FirstAt)] =
    if body.value.isEmpty then Map.empty
    else
      val len = body.value.length.toDouble
      AssetLink
        .findAllMatchIn(body.value)
        .toList
        .groupBy(_.group(1))
        .view
        .map((name, ms) => Figure.Name(name) -> (Figure.Refs(ms.size), Figure.FirstAt(ms.map(_.start).min / len)))
        .toMap

  /** Pure: the figures with their links counted in `body`. */
  def withRefs(figs: List[Figure], body: ConvertedText): List[Figure] =
    val r = refs(body)
    figs.map(f => r.get(f.name).fold(f.copy(refs = Figure.Refs(0), firstAt = None))((n, at) => f.copy(refs = n, firstAt = Some(at))))
