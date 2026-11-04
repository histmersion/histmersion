package com.histmersion.domain.library.sources
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{IssueCondition, IssueDetail, MergeDecision, MergeIssue, MergePlan}
import com.histmersion.api.checks.Lint
import com.histmersion.domain.MergeFold
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergeSettings
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.data.exchange.PairQuestions
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.PlanRun
import com.histmersion.util.data.exchange.SourceFate
import com.histmersion.util.data.exchange.SourcePair
import com.histmersion.util.text.MinHash
import com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.api.library.MatchMethod
import com.histmersion.api.library.MatchOutcome

import hearth.kindlings.optics.syntax.*
import SourceMatcher.{Outcome, Pair, Side}
import java.util.UUID
import scala.collection.mutable
import com.histmersion.api.library.Source
import com.histmersion.api.exchange.Bundle

/** How a merge plan treats the sources themselves (docs/research-merge-import.md §5.2–§5.3): the bundle's sources
  * matched with the ones here ([[SourceMatcher]]), the question whether a pair is one work, the fields of a pair that
  * is (or may be) one — conditional under an open question —, a citekey taken here, the arguments that name a citekey,
  * a pair folded field by field, a source renamed, how each was matched, and the final lines.
  */
final class SourcePlan(in: MergePlanner.Input) extends PlanRun:
  import SourcePlan.*

  private val km = MergeDecision.Kind
  private val lr = List(km.KeepLeft, km.KeepRight)
  private val fes = in.foreign
  private val sourcesByUid = in.sources.map(s => s.uid -> s).toMap
  private val (rights, lefts) = matchSides(in)
  private val pairOf = mutable.LinkedHashMap.empty[UUID, Pair] // the bundle's source → its pair
  private val pairFieldIds = mutable.Map.empty[UUID, Map[MergeIssue.Kind, String]] // the fields' issues, by pair
  private val fatesSeen = mutable.Map.empty[UUID, SourceFate]
  private val sourceLine = mutable.Map.empty[UUID, BundleEntities => com.histmersion.api.exchange.Bundle.Source]

  private def leftRev(u: UUID): String = sourcesByUid.get(u).map(s => BundleFormat.stamp(s).rev).getOrElse("")
  private def rightRev(s: com.histmersion.api.exchange.Bundle.Source): String = BundleFormat.stamp(s).rev

  override def sketchesWanted(cfg: MergeSettings): Set[String] =
    val byKey = lefts.flatMap(l => SourceMatcher.blockingKeys(l).map(_ -> l)).groupMap(_._1)(_._2)
    rights.flatMap { r =>
      SourceMatcher.blockingKeys(r).toList.flatMap(k => byKey.getOrElse(k, Nil)).distinctBy(_.uid).flatMap { l =>
        (r.source.bodySha256, l.source.bodySha256) match
          case (Some(a), Some(b)) if a != b => List(a, b)
          case _                            => Nil
      }
    }.toSet

  override def matchSources(
      sketches: Map[String, MinHash],
      cfg: MergeSettings,
      editions: Option[EditionsPolicy]
  ): List[SourcePair] =
    val matching = SourceMatcher.assign(rights, lefts, sketches, cfg, editions)
    matching.pairs.map { p =>
      pairOf(p.right.uid) = p
      val gate = Option.when(p.outcome == Outcome.NeedsDecision)(
        MergePlanner.issueId(
          MergeIssue.Kind.SameWork,
          Bundle.Kind.Source,
          List(p.right.uid),
          List(p.left.uid),
          rightRev(p.right.source),
          leftRev(p.left.uid)
        )
      )
      val basis =
        if p.evidence.strongKey then p.evidence.strong.mkString(", ")
        else s"score ${SourceMatcher.signed(p.evidence.score)}"
      SourcePair(p.right.source, p.left.source, gate, p.evidence.strongKey, basis)
    }

  private def gateOf(p: Pair): String = MergePlanner.issueId(
    MergeIssue.Kind.SameWork,
    Bundle.Kind.Source,
    List(p.right.uid),
    List(p.left.uid),
    rightRev(p.right.source),
    leftRev(p.left.uid)
  )

  override def sourceQuestions(
      fates: Map[UUID, SourceFate],
      sketches: Map[String, MinHash],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit =
    fatesSeen ++= fates
    // the gates
    pairOf.values.filter(_.outcome == Outcome.NeedsDecision).foreach { p =>
      val ev = p.evidence
      val title =
        if p.flags.nonEmpty then s"the same work? ${p.flags.mkString("; ")} (score ${SourceMatcher.signed(ev.score)})"
        else s"possibly the same work (score ${SourceMatcher.signed(ev.score)})"
      questions.ask(
        MergeIssue(
          gateOf(p),
          MergeIssue.Kind.SameWork,
          MergeIssue.Severity.Blocking,
          Bundle.Kind.Source,
          title,
          Some(MergeIssue.Detail.side(p.left.source)),
          Some(MergeIssue.Detail.side(p.right.source)),
          ev.why ++ p.alsoLike.take(3).map((ck, sc) => s"also resembles $ck (score ${SourceMatcher.signed(sc)})"),
          List(km.Map, km.Both),
          Option.when(ev.score >= cfg.autoMerge)(MergeDecision.withArg(km.Map, p.left.source.citekey)),
          detail = Some(IssueDetail(MergeIssue.Detail.sourceFields(p.left.source, p.right.source)))
        ),
        Set(km.Both)
      )
    }
    // the fields of a pair that is (or may be) merged; a source that would be new under a citekey taken here
    fes.sources.foreach { s =>
      fates(s.uid) match
        case SourceFate.Auto(p) => fieldIssues(pairOf(p.right.uid), None, sketches).foreach(questions.ask(_, Set.empty))
        case SourceFate.Gate(p, g) =>
          fieldIssues(pairOf(p.right.uid), Some(IssueCondition(g, km.Map)), sketches)
            .foreach(questions.ask(_, Set.empty))
          collision(s, Some(IssueCondition(g, km.Both)), sketches, cfg).foreach(questions.ask(_, Set.empty))
        case SourceFate.New => collision(s, None, sketches, cfg).foreach(questions.ask(_, Set.empty))
        case _              => ()
    }

  /** The questions about the fields of a pair; their ids are kept by pair, for the fold. */
  private def fieldIssues(p: Pair, cond: Option[IssueCondition], sketches: Map[String, MinHash]): List[MergeIssue] =
    val (r, l) = (p.right.source, p.left.source)
    val (fu, lu) = (List(r.uid), List(l.uid))
    val (rr, ll) = (rightRev(r), leftRev(l.uid))
    def mk(
        kind: MergeIssue.Kind,
        title: String,
        opts: List[MergeDecision.Kind],
        suggested: Option[MergeDecision] = None,
        sev: MergeIssue.Severity = MergeIssue.Severity.Blocking,
        why: List[String] = Nil
    ): MergeIssue =
      MergeIssue(
        MergePlanner.issueId(kind, Bundle.Kind.Source, fu, lu, rr, ll),
        kind,
        sev,
        Bundle.Kind.Source,
        title,
        Some(MergeIssue.Detail.side(l)),
        Some(MergeIssue.Detail.side(r)),
        why,
        opts,
        suggested,
        cond,
        detail = Some(IssueDetail(MergeIssue.Detail.sourceFields(l, r)))
      )
    val doiR = r.doi.flatMap(MergeText.doiKey)
    val doiL = l.doi.flatMap(MergeText.doiKey)
    val sameDoi = doiR.isDefined && doiR == doiL
    val ds = List(
      Option.when(doiR.isDefined && doiL.isDefined && doiR != doiL)(
        mk(
          MergeIssue.Kind.DoiDiffers,
          s"DOI differs: ${l.doi.get} here, ${r.doi.get} in the bundle",
          lr,
          why = p.evidence.strong.map(k => s"matched by $k: one record has the wrong DOI")
        )
      ),
      r.year.zip(l.year).filter((a, b) => a != b && !(math.abs(a - b) == 1 && sameDoi)).map { (a, b) =>
        val earliest = if b <= a then km.KeepLeft else km.KeepRight
        mk(
          MergeIssue.Kind.YearDiffers,
          s"year differs: $b here, $a in the bundle",
          lr,
          Some(MergeDecision.of(earliest)),
          why = List("the suggestion keeps the earliest year (the first edition)")
        )
      },
      Option.when(r.status != l.status && r.status != Source.Status.Unassessed && l.status != Source.Status.Unassessed)(
        mk(MergeIssue.Kind.StatusDiffers, s"status differs: ${l.status.wire} here, ${r.status.wire} in the bundle", lr)
      ),
      Option.when(p.evidence.titleDiffers)(
        mk(MergeIssue.Kind.TitleDiffers, "title differs", lr, why = List(s"here: ${l.title}", s"bundle: ${r.title}"))
      ),
      Option.when(p.evidence.authors == MergeText.AuthorAgreement.Disjoint)(
        mk(
          MergeIssue.Kind.AuthorsDiffers,
          "no author in common",
          lr,
          why = List(s"here: ${l.authors.mkString("; ")}", s"bundle: ${r.authors.mkString("; ")}")
        )
      ),
      differs(r.summary, l.summary).map(_ =>
        mk(MergeIssue.Kind.SummaryDiffers, "summary differs", List(km.KeepLeft, km.KeepRight, km.Concat))
      ),
      differs(r.notes, l.notes).map(_ =>
        mk(MergeIssue.Kind.NotesDiffers, "notes differ", List(km.KeepLeft, km.KeepRight, km.Concat))
      ),
      r.period
        .zip(l.period)
        .filter((a, b) => a != b)
        .map((a, b) =>
          mk(
            MergeIssue.Kind.PeriodDiffers,
            s"period differs: ${MergeIssue.Detail.rangeText(Some(b))} here, ${MergeIssue.Detail.rangeText(Some(a))} in the bundle",
            List(km.KeepLeft, km.KeepRight, km.Hull),
            Some(MergeDecision.of(km.Hull))
          )
        ),
      r.bodySha256.zip(l.bodySha256).filter((a, b) => a != b).map { (a, b) =>
        val sim = sketches
          .get(a)
          .zip(sketches.get(b))
          .map((x, y) => s" (MinHash ${SourceMatcher.fixed(x.similarity(y), 2)})")
          .getOrElse("")
        mk(
          MergeIssue.Kind.BodyDiffers,
          s"OCR bodies differ$sim",
          lr,
          Some(MergeDecision.of(km.KeepLeft)),
          MergeIssue.Severity.Default,
          List("by default this collection's body stays; keep-right takes the bundle's (and its figures)")
        )
      }
    ).flatten
    pairFieldIds(r.uid) = ds.map(d => d.kind -> d.id).toMap
    ds

  /** A bundle source that would be new: is its citekey taken here? */
  private def collision(
      s: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      sketches: Map[String, MinHash],
      cfg: MergeSettings
  ): Option[MergeIssue] =
    val owner = in.labels.citekeys
      .get(s.citekey)
      .orElse(in.labels.aliases.get(s.citekey))
      .filterNot(in.tombstoned)
    owner.flatMap(sourcesByUid.get).map { o =>
      val asAlias = !in.labels.citekeys.contains(s.citekey)
      val score = lefts
        .find(_.uid == o.uid)
        .map(l => SourceMatcher.evidence(Side(s, Set.empty), l, sketches, cfg.weights).score)
      val title =
        if asAlias then s"citekey ${s.citekey} is an old citekey (alias) of another source here"
        else s"same citekey, different works" + score.fold("")(sc => s" (score ${SourceMatcher.signed(sc)})")
      val suggestion = renameSuggestion(s, in, fes)
      MergeIssue(
        MergePlanner.issueId(
          MergeIssue.Kind.CitekeyCollision,
          Bundle.Kind.Source,
          List(s.uid),
          List(o.uid),
          rightRev(s),
          leftRev(o.uid)
        ),
        MergeIssue.Kind.CitekeyCollision,
        MergeIssue.Severity.Blocking,
        Bundle.Kind.Source,
        title,
        Some(MergeIssue.Detail.side(o)),
        Some(MergeIssue.Detail.side(s)),
        if asAlias then List("rename-left cannot free an old citekey: the bundle's source takes a new one")
        else Nil,
        if asAlias then List(km.RenameRight) else List(km.RenameRight, km.RenameLeft),
        Some(MergeDecision.withArg(km.RenameRight, suggestion)),
        cond,
        detail = Some(IssueDetail(MergeIssue.Detail.sourceFields(o, s)))
      )
    }

  override def bodyIssue(right: UUID): Option[String] =
    pairFieldIds.get(right).flatMap(_.get(MergeIssue.Kind.BodyDiffers))

  override def rowDetail(kind: Bundle.Kind, u: UUID, localized: BundleEntities): Option[IssueDetail] =
    if kind != Bundle.Kind.Source then None
    else
      sourcesByUid
        .get(u)
        .zip(localized.sources.find(_.uid == u))
        .map((l, r) => IssueDetail(MergeIssue.Detail.sourceFields(l, r)))

  override def labelsTakenFirst(questions: PlanQuestions): Unit =
    questions.labelTaken(
      Bundle.Kind.Source,
      questions.localized.sources.filter(s => questions.here(s.uid)),
      (s: com.histmersion.api.exchange.Bundle.Source) => s.uid,
      (s: com.histmersion.api.exchange.Bundle.Source) => s.citekey,
      (s: com.histmersion.api.exchange.Bundle.Source) =>
        in.labels.citekeys.get(s.citekey).orElse(in.labels.aliases.get(s.citekey)),
      "citekey",
      lr,
      (_: com.histmersion.api.exchange.Bundle.Source, _: UUID) => None,
      (s: com.histmersion.api.exchange.Bundle.Source, o: UUID) =>
        sourcesByUid.get(o).map(l => IssueDetail(MergeIssue.Detail.sourceFields(l, s)))
    )

  override def citekeyArgument(i: MergeIssue, dec: MergeDecision): Option[Either[String, Unit]] =
    dec.kind match
      case km.Map =>
        val allowed = List(i.left, i.right).flatten.map(_.label)
        val ck = dec.argument.getOrElse("")
        val leftUid = i.left.map(_.uid)
        Some(
          if !allowed.contains(ck) then
            Left(s"map takes one of the two citekeys: ${allowed.map(c => s"map:$c").mkString(" or ")}")
          else if in.labels.citekeys.get(ck).orElse(in.labels.aliases.get(ck)).exists(o => !leftUid.contains(o))
          then Left(s"'$ck' is a citekey (or an old one) of another source here — merge under the other citekey")
          else Right(())
        )
      case km.RenameRight | km.RenameLeft =>
        val ck = dec.argument.getOrElse("")
        val labelsHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey)
        Some(
          if !Citekeys.valid(ck) then Left(s"'$ck' is not a citekey (lower-case letters, digits and _)")
          else if labelsHere(ck) then Left(s"'$ck' is already a citekey (or an old one) here or in the bundle")
          else Right(())
        )
      case _ => None

  override def pairIssue(kind: MergeIssue.Kind): Boolean = PairKinds(kind)

  override def foldSources(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      citekey: String,
      folds: PlanFolds
  ): Boolean =
    pairOf
      .get(right.uid)
      .filterNot(_.evidence.strongKey)
      .foreach(_ => folds.count(_.modify(_.sourcesByScore).using(_ + 1)))
    val fieldDec = pairFieldIds.getOrElse(right.uid, Map.empty).flatMap((k, id) => folds.decisionIn(id).map(k -> _))
    val merged = fold(left, right, fieldDec, citekey)
    sourceLine(left.uid) = _ => merged
    merged.bodySha256 != left.bodySha256

  override def renamed(source: UUID, citekey: String): Unit =
    sourceLine(source) = es => es.sources.find(_.uid == source).get.modify(_.citekey).setTo(citekey)

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.sources)
      .setTo(
        MergePlanner
          .replaced(es0.sources, _.uid, sourceLine, es0, (_: com.histmersion.api.exchange.Bundle.Source) => true)
      )

  override def planned(plan: MergePlan): MergePlan =
    plan.modify(_.sources).setTo(com.histmersion.api.library.Source.Match.Matches.matches(matches))

  /** How each of the bundle's sources was matched, by citekey; one known by its uid or the ledger is not listed. */
  private def matches: List[com.histmersion.api.library.Source.Match] = fes.sources.sortBy(_.citekey).flatMap { s =>
    fatesSeen.get(s.uid).flatMap {
      case SourceFate.Same(_, _) => None
      case SourceFate.Deleted    =>
        Some(
          com.histmersion.api.library.Source
            .Match(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.DeletedHere)
        )
      case SourceFate.New =>
        Some(
          com.histmersion.api.library.Source
            .Match(s.uid, s.citekey, None, None, MatchMethod.Unmatched, MatchOutcome.New)
        )
      case SourceFate.Auto(p) =>
        val ev = pairOf(s.uid).evidence
        Some(
          com.histmersion.api.library.Source.Match(
            s.uid,
            s.citekey,
            Some(p.left.uid),
            Some(p.left.citekey),
            if ev.strongKey then MatchMethod.StrongKey else MatchMethod.Score,
            MatchOutcome.AutoMerge,
            Some(ev.score),
            ev.why
          )
        )
      case SourceFate.Gate(p, g) =>
        val ev = pairOf(s.uid).evidence
        Some(
          com.histmersion.api.library.Source.Match(
            s.uid,
            s.citekey,
            Some(p.left.uid),
            Some(p.left.citekey),
            MatchMethod.Score,
            MatchOutcome.NeedsDecision,
            Some(ev.score),
            ev.why,
            Some(g)
          )
        )
    }
  }

object SourcePlan:

  /** The questions of a pair of sources: their decisions are carried out with the pair's fold. */
  private val PairKinds: Set[MergeIssue.Kind] = Set(
    MergeIssue.Kind.DoiDiffers,
    MergeIssue.Kind.YearDiffers,
    MergeIssue.Kind.StatusDiffers,
    MergeIssue.Kind.TitleDiffers,
    MergeIssue.Kind.AuthorsDiffers,
    MergeIssue.Kind.SummaryDiffers,
    MergeIssue.Kind.NotesDiffers,
    MergeIssue.Kind.PeriodDiffers,
    MergeIssue.Kind.BodyDiffers,
    MergeIssue.Kind.SameWork
  )

  /** The bundle's sources that need matching (no identity here), and the sources here they may match (not already some
    * bundle source's own, not deleted).
    */
  private def matchSides(in: MergePlanner.Input): (List[Side], List[Side]) =
    val idOf = MergePlanner.mapUid(in.uidAliases, in.ledger)
    val localUids = in.local.sources.map(_.uid).toSet ++ in.sources.map(_.uid)
    val unknown = in.foreign.sources.filter { s =>
      val l = idOf(s.uid)
      l == s.uid && !localUids(l) && !in.tombstoned(l)
    }
    val claimed = in.foreign.sources.map(s => idOf(s.uid)).toSet
    val foreignAliases = in.foreign.aliases.groupMap(_.sourceUid)(_.alias)
    val localAliases = in.labels.aliases.toList.groupMap(_._2)(_._1)
    val rights = unknown.map(s => Side(s, foreignAliases.getOrElse(s.uid, Nil).toSet))
    val lefts = in.sources
      .filterNot(s => claimed(s.uid) || in.tombstoned(s.uid))
      .map(s => Side(s, localAliases.getOrElse(s.uid, Nil).toSet))
    (rights, lefts)

  private def trimmed(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** Both texts present and different (after trimming). */
  private def differs(a: Option[String], b: Option[String]): Option[Unit] =
    trimmed(a).zip(trimmed(b)).filter((x, y) => x != y).map(_ => ())

  /** A new citekey for a bundle source whose own is taken: its key plus the next significant title word
    * (`smith2019_ritual` → `smith2019_ritualkingship`), else a letter (`smith2019_ritualb`). Never one in use here or
    * in the bundle. Pure.
    */
  def renameSuggestion(
      s: com.histmersion.api.exchange.Bundle.Source,
      in: MergePlanner.Input,
      fes: BundleEntities
  ): String =
    val usedHere = in.labels.citekeys.keySet ++ in.labels.aliases.keySet ++ fes.sources.map(_.citekey) ++
      fes.aliases.map(_.alias)
    val suffix = s.citekey.dropWhile(_ != '_').drop(1)
    val words = MergeText
      .significant(MergeText.tokens(s.title))
      .map(_.filter(c => c.isLetterOrDigit && c < 128))
      .filter(w => w.nonEmpty && !suffix.contains(w))
    val byWord = words.headOption.map(w => s.citekey + w).filterNot(usedHere)
    byWord.getOrElse(Iterator.from(0).map(i => s.citekey + ('b' + i).toChar).find(c => !usedHere(c)).get)

  /** The richer author list: as many names, then the longer written-out names (`Hans G.` over `H. G.`). */
  def richerAuthors(l: List[String], r: List[String]): List[String] =
    def score(as: List[String]) =
      (
        as.size,
        as.flatMap(MergeText.person).map(p => p.forenames.map(_.length).sum).sum,
        as.map(_.count(c => c.isLetter && c > 127)).sum
      )
    if summon[Ordering[(Int, Int, Int)]].gt(score(r), score(l)) then r else l

  /** One source out of a pair, field by field (§5.3). `decided` holds the decision of each field issue of the pair
    * (doi, year, status, title, authors, summary, notes, period, body); a field with no issue follows §5.3's automatic
    * rule. `citekey` is the label the survivor keeps (`map:<citekey>`). The survivor is this collection's (left).
    */
  def fold(
      l: com.histmersion.api.exchange.Bundle.Source,
      r: com.histmersion.api.exchange.Bundle.Source,
      decided: Map[MergeIssue.Kind, MergeDecision],
      citekey: String
  ): com.histmersion.api.exchange.Bundle.Source =
    def pick[A](k: MergeIssue.Kind, auto: => A, left: => A, right: => A): A = decided.get(k).map(_.kind) match
      case Some(MergeDecision.Kind.KeepRight) => right
      case Some(MergeDecision.Kind.KeepLeft)  => left
      case _                                  => auto
    def prose(k: MergeIssue.Kind, a: Option[String], b: Option[String]): Option[String] =
      (trimmed(a), trimmed(b)) match
        case (Some(x), Some(y)) if x != y =>
          decided.get(k).map(_.kind) match
            case Some(MergeDecision.Kind.KeepRight) => b
            case Some(MergeDecision.Kind.Concat)    => Some(s"$x\n\n$y")
            case _                                  => a
        case (Some(_), _) => a
        case (_, Some(_)) => b
        case _            => a
    val keepRightBody = decided.get(MergeIssue.Kind.BodyDiffers).exists(_.kind == MergeDecision.Kind.KeepRight) ||
      (l.bodySha256.isEmpty && r.bodySha256.isDefined)
    val period: Option[com.histmersion.api.exchange.Bundle.Range] = (l.period, r.period) match
      case (Some(a), Some(b)) if a != b =>
        decided.get(MergeIssue.Kind.PeriodDiffers).map(_.kind) match
          case Some(MergeDecision.Kind.KeepRight) => Some(b)
          case Some(MergeDecision.Kind.Hull)      => Some(MergeFold.hull(a, b))
          case _                                  => Some(a)
      case (a, b) => a.orElse(b)
    val status =
      if l.status == r.status || r.status == Source.Status.Unassessed then l.status
      else if l.status == Source.Status.Unassessed then r.status
      else pick(MergeIssue.Kind.StatusDiffers, l.status, l.status, r.status)
    l.modify(
      _.citekey).setTo(citekey).modify(_.title).setTo(pick(MergeIssue.Kind.TitleDiffers, MergeFold.richer(l.title, r.title), l.title, r.title)).modify(_.authors).setTo(pick(MergeIssue.Kind.AuthorsDiffers, richerAuthors(l.authors, r.authors), l.authors, r.authors)).modify(_.doi).setTo(pick(MergeIssue.Kind.DoiDiffers, l.doi.orElse(r.doi), l.doi.orElse(r.doi), r.doi.orElse(l.doi))).modify(_.year).setTo(pick(MergeIssue.Kind.YearDiffers, l.year.orElse(r.year), l.year.orElse(r.year), r.year.orElse(l.year))).modify(_.venue).setTo((trimmed(l.venue), trimmed(r.venue)) match
        case (Some(a), Some(b)) if MergeText.fingerprint(a) == MergeText.fingerprint(b) => Some(MergeFold.richer(a, b))
        case (a, b)                                                                     => a.orElse(b)).modify(_.status).setTo(status).modify(_.topics).setTo(MergeFold.unionNames(l.topics, r.topics)).modify(_.regions).setTo(MergeFold.unionNames(l.regions, r.regions)).modify(_.tags).setTo(MergeFold.unionTags(l.tags, r.tags)).modify(_.period).setTo(period).modify(_.summary).setTo(prose(MergeIssue.Kind.SummaryDiffers, l.summary, r.summary)).modify(_.notes).setTo(prose(MergeIssue.Kind.NotesDiffers, l.notes, r.notes)).modify(_.provenance).setTo(l.provenance.orElse(r.provenance)).modify(_.citedByCount).setTo(l.citedByCount.zip(r.citedByCount).map((a, b) => math.max(a, b)).orElse(l.citedByCount).orElse(r.citedByCount)).modify(_.influentialCitations).setTo(l.influentialCitations
        .zip(r.influentialCitations)
        .map((a, b) => math.max(a, b))
        .orElse(l.influentialCitations)
        .orElse(r.influentialCitations)).modify(_.addedDate).setTo(if r.addedDate < l.addedDate then r.addedDate else l.addedDate).modify(_.bodySha256).setTo(if keepRightBody then r.bodySha256 else l.bodySha256.orElse(r.bodySha256)).modify(_.originalSha256).setTo(if keepRightBody then r.originalSha256.orElse(l.originalSha256) else l.originalSha256.orElse(r.originalSha256)).modify(_.originalExt).setTo(if keepRightBody then r.originalExt.orElse(l.originalExt) else l.originalExt.orElse(r.originalExt)).modify(_.isbns).setTo(Option(MergeFold.unionNames(l.isbns.getOrElse(Nil), r.isbns.getOrElse(Nil)).sorted).filter(_.nonEmpty)).modify(_.rev).setTo(""
    )
