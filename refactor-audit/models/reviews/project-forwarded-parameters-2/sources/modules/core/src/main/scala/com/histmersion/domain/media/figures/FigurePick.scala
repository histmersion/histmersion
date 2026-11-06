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
  type PageScanSizesNearAPart1 = PageScanSizesNearAPart1.T
  object PageScanSizesNearAPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanSizesNearAPart2 = PageScanSizesNearAPart2.T
  object PageScanSizesNearAPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanSizesNearBPart1 = PageScanSizesNearBPart1.T
  object PageScanSizesNearBPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanSizesNearBPart2 = PageScanSizesNearBPart2.T
  object PageScanSizesNearBPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageName = PageName.T
  object PageName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResult = OrderResult.T
  object OrderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderName = OrderName.T
  object OrderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanSizesResultPart1 = PageScanSizesResultPart1.T
  object PageScanSizesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageScanSizesResultPart2 = PageScanSizesResultPart2.T
  object PageScanSizesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RejectResult = RejectResult.T
  object RejectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RejectScansPart1 = RejectScansPart1.T
  object RejectScansPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RejectScansPart2 = RejectScansPart2.T
  object RejectScansPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScoreResult = ScoreResult.T
  object ScoreResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResultPart1 = RefsResultPart1.T
  object RefsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResultPart2Part1 = RefsResultPart2Part1.T
  object RefsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResultPart2Part2 = RefsResultPart2Part2.T
  object RefsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsBody = RefsBody.T
  object RefsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithRefsBody = _root_.com.histmersion.domain.media.figures.FigurePick.RefsBody
  val WithRefsBody: _root_.com.histmersion.domain.media.figures.FigurePick.RefsBody.type = _root_.com.histmersion.domain.media.figures.FigurePick.RefsBody

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
  def page(name: com.histmersion.domain.media.figures.FigurePick.PageName): Option[com.histmersion.domain.media.figures.FigurePick.PageResult] = name match
    case PageName(p) => p.toIntOption
    case _           => None

  /** Pure: a figure's order in its document by its name alone — the page for `fig-…`, the sequence number for docling's
    * `image_…`.
    */
  def order(name: com.histmersion.domain.media.figures.FigurePick.OrderName): Option[com.histmersion.domain.media.figures.FigurePick.OrderResult] = page(name).orElse(name match
    case SeqName(n) => n.toIntOption
    case _          => None)

  /** Pure: the sizes that are page scans — `pageScanMin` or more portrait figures (height/width between 1.2 and 1.6)
    * whose sides agree within 2 %.
    */
  def pageScanSizes(figs: List[Figure], rules: Rules): Set[(com.histmersion.domain.media.figures.FigurePick.PageScanSizesResultPart1, com.histmersion.domain.media.figures.FigurePick.PageScanSizesResultPart2)] =
    val portrait = figs.flatMap(f =>
      for w <- f.width; h <- f.height if w > 0 && h.toDouble / w >= 1.2 && h.toDouble / w <= 1.6 yield (w, h)
    )
    def near(a: (com.histmersion.domain.media.figures.FigurePick.PageScanSizesNearAPart1, com.histmersion.domain.media.figures.FigurePick.PageScanSizesNearAPart2), b: (com.histmersion.domain.media.figures.FigurePick.PageScanSizesNearBPart1, com.histmersion.domain.media.figures.FigurePick.PageScanSizesNearBPart2)) =
      math.abs(a._1 - b._1) <= a._1 * 0.02 && math.abs(a._2 - b._2) <= a._2 * 0.02
    portrait.distinct.filter(s => portrait.count(near(s, _)) >= rules.pageScanMin.max(2)).toSet

  /** Pure: why a figure cannot illustrate its source, or `None` when it can. */
  def reject(f: Figure, rules: Rules, scans: Set[(com.histmersion.domain.media.figures.FigurePick.RejectScansPart1, com.histmersion.domain.media.figures.FigurePick.RejectScansPart2)] = Set.empty): Option[com.histmersion.domain.media.figures.FigurePick.RejectResult] =
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
  def score(f: Figure, rules: Rules): com.histmersion.domain.media.figures.FigurePick.ScoreResult =
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
  def refs(body: com.histmersion.domain.media.figures.FigurePick.RefsBody): Map[com.histmersion.domain.media.figures.FigurePick.RefsResultPart1, (com.histmersion.domain.media.figures.FigurePick.RefsResultPart2Part1, com.histmersion.domain.media.figures.FigurePick.RefsResultPart2Part2)] =
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
  def withRefs(figs: List[Figure], body: com.histmersion.domain.media.figures.FigurePick.WithRefsBody): List[Figure] =
    val r = refs(body)
    figs.map(f => r.get(f.name).fold(f.copy(refs = 0, firstAt = None))((n, at) => f.copy(refs = n, firstAt = Some(at))))
