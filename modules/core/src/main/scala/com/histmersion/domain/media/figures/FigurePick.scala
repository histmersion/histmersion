package com.histmersion.domain.media.figures

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

  /** One stored figure of a source. `refs`: how many times the text links it; `firstAt`: where the first link sits, as
    * a share of the text (0 = its start); both `None`/0 when the text has none.
    */
  final case class Figure(
      name: Figure.Name,
      width: Option[Figure.Width],
      height: Option[Figure.Height],
      refs: Figure.Refs = 1,
      firstAt: Option[Figure.FirstAt] = None
  )

  object Figure:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Width = Width.T
    object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Height = Height.T
    object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refs = Refs.T
    object Refs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstAt = FirstAt.T
    object FirstAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The thresholds (`media.illustrations`). */
  final case class Rules(
      minSide: Rules.MinSide = 160,
      minArea: Rules.MinArea = 40000,
      maxAspect: Rules.MaxAspect = 4.0,
      maxRefs: Rules.MaxRefs = 2,
      earlyPages: Rules.EarlyPages = 6,
      pageScanMin: Rules.PageScanMin = 5
  )

  object Rules:
    type MinSide = MinSide.T
    object MinSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinArea = MinArea.T
    object MinArea extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxAspect = MaxAspect.T
    object MaxAspect extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxRefs = MaxRefs.T
    object MaxRefs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EarlyPages = EarlyPages.T
    object EarlyPages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageScanMin = PageScanMin.T
    object PageScanMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val PageName = """^fig-(\d{1,5})-\d+(?:-\d+)?\.[A-Za-z0-9]+$""".r
  private val SeqName = """^image_(\d{1,7})(?:_.*)?\.[A-Za-z0-9]+$""".r

  /** Pure: the page a figure's name says it is on (pymupdf4llm's `fig-<page>-<n>`, pages counted from 1 as the
    * converter writes them), `None` for any other name.
    */
  def page(name: String): Option[Int] = name match
    case PageName(p) => p.toIntOption
    case _           => None

  /** Pure: a figure's order in its document by its name alone — the page for `fig-…`, the sequence number for docling's
    * `image_…`.
    */
  def order(name: String): Option[Int] = page(name).orElse(name match
    case SeqName(n) => n.toIntOption
    case _          => None)

  /** Pure: the sizes that are page scans — `pageScanMin` or more portrait figures (height/width between 1.2 and 1.6)
    * whose sides agree within 2 %.
    */
  def pageScanSizes(figs: List[Figure], rules: Rules): Set[(Int, Int)] =
    val portrait = figs.flatMap(f =>
      for w <- f.width; h <- f.height if w > 0 && h.toDouble / w >= 1.2 && h.toDouble / w <= 1.6 yield (w, h)
    )
    def near(a: (Int, Int), b: (Int, Int)) =
      math.abs(a._1 - b._1) <= a._1 * 0.02 && math.abs(a._2 - b._2) <= a._2 * 0.02
    portrait.distinct.filter(s => portrait.count(near(s, _)) >= rules.pageScanMin.max(2)).toSet

  /** Pure: why a figure cannot illustrate its source, or `None` when it can. */
  def reject(f: Figure, rules: Rules, scans: Set[(Int, Int)] = Set.empty): Option[String] =
    (f.width, f.height) match
      case (Some(w), Some(h)) if w > 0 && h > 0 =>
        val aspect = math.max(w, h).toDouble / math.min(w, h)
        if aspect > rules.maxAspect then Some("a strip")
        else if math.min(w, h) < rules.minSide then Some("too small")
        else if w.toLong * h < rules.minArea then Some("too small")
        else if f.refs > rules.maxRefs then Some("repeated in the text")
        else if scans.contains((w, h)) then Some("a page scan")
        else None
      case _ => Some("no size")

  /** Pure: the figure's weight — its area, halved past the early pages (or, without a page in its name, past the first
    * third of the text).
    */
  def score(f: Figure, rules: Rules): Double =
    val area = f.width.getOrElse(0).toDouble * f.height.getOrElse(0)
    val early = page(f.name) match
      case Some(p) => p <= rules.earlyPages
      case None    => f.firstAt.forall(_ <= 1.0 / 3)
    if early then area else area / 2

  /** Pure: the illustration — the best-scoring acceptable figure; ties go to the earlier one. */
  def choose(figs: List[Figure], rules: Rules): Option[Figure] =
    val scans = pageScanSizes(figs, rules)
    figs
      .filter(reject(_, rules, scans).isEmpty)
      .sortBy(f => (-score(f, rules), order(f.name).getOrElse(Int.MaxValue), f.firstAt.getOrElse(1.0), f.name))
      .headOption

  private val AssetLink = """assets/([A-Za-z0-9._-]+)""".r

  /** Pure: every figure a converted text links (`assets/<name>`, the form the converter writes), with how many times
    * and where first (a share of the text) — one pass, whatever the number of figures.
    */
  def refs(body: String): Map[String, (Int, Double)] =
    if body.isEmpty then Map.empty
    else
      val len = body.length.toDouble
      AssetLink
        .findAllMatchIn(body)
        .toList
        .groupBy(_.group(1))
        .view
        .mapValues(ms => (ms.size, ms.map(_.start).min / len))
        .toMap

  /** Pure: the figures with their links counted in `body`. */
  def withRefs(figs: List[Figure], body: String): List[Figure] =
    val r = refs(body)
    figs.map(f => r.get(f.name).fold(f.copy(refs = 0, firstAt = None))((n, at) => f.copy(refs = n, firstAt = Some(at))))
