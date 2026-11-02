package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.{
  BundleAttestation,
  BundleSource,
  IssueCondition,
  IssueDetail,
  IssueField,
  IssueSide,
  MergeIssue,
  MergeIssueDetail
}
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.AttestationFix
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.{PairQuestions, PlanFolds, PlanRun}
import com.histmersion.util.text.Anchor
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind, IssueSeverity}

import hearth.kindlings.optics.syntax.*
import java.util.UUID

/** How a merge plan treats attestations: they travel with the sources they quote. When a pair of sources that are one
  * keeps one OCR body, the attestations that point into the other are found again in it (re-anchored) — under each
  * alternative of the body question, so nothing new appears after it is answered; one whose quote is not there is a
  * question (drop it, or keep it without a place). One already here (the same target and quote) is one.
  */
final class AttestationPlan(in: MergePlanner.Input) extends PlanRun:
  import AttestationPlan.*

  private val km = DecisionKind

  // per pair and per alternative of the body question (the body issue's decision, or None when there is no
  // question): the attestations tried against the body it keeps
  private val anchors = scala.collection.mutable.Map.empty[UUID, Map[Option[DecisionKind], List[AnchorTry]]]
  private val attLine = scala.collection.mutable.Map.empty[UUID, BundleEntities => BundleAttestation]
  private val droppedUids = scala.collection.mutable.LinkedHashSet.empty[UUID] // bundle uids left out for good
  private val attFixes = List.newBuilder[AttestationFix]
  private val attSource = in.foreign.attestations.map(t => t.uid -> t.sourceUid).toMap
  private val localAttsOf = in.local.attestations.groupBy(_.sourceUid)
  private def attIn(es: BundleEntities, u: UUID) = es.attestations.find(_.uid == u).get

  /** The bundle's attestations that have no uid here yet, by their source. */
  private def foreignAttsOf(here: UUID => Boolean, toLocal: UUID => UUID): Map[UUID, List[BundleAttestation]] =
    in.foreign.attestations.filterNot(t => here(toLocal(t.uid))).groupBy(_.sourceUid)

  override def ownerSource(foreign: UUID, kind: BundleKind): Option[UUID] =
    if kind == BundleKind.Attestation then attSource.get(foreign) else None

  override def bodiesWanted(pairs: List[(BundleSource, BundleSource)]): Set[String] =
    val withAtts = (in.foreign.attestations ++ in.local.attestations).filter(anchored).map(_.sourceUid).toSet
    pairs.flatMap { (r, l) =>
      val (a, b) = (r.bodySha256, l.bodySha256)
      if a != b && (withAtts(r.uid) || withAtts(l.uid)) then a.toList ++ b.toList else Nil
    }.toSet

  override def pair(
      r: BundleSource,
      l: BundleSource,
      cond: Option[IssueCondition],
      bodyIssue: Option[String],
      q: PairQuestions
  ): Unit =
    val alternatives: List[(Option[DecisionKind], Option[String], Option[IssueCondition])] = bodyIssue match
      case Some(b) =>
        List(
          (Some(km.KeepLeft), l.bodySha256, Some(IssueCondition(b, km.KeepLeft))),
          (Some(km.KeepRight), r.bodySha256, Some(IssueCondition(b, km.KeepRight)))
        )
      case None => List((None, l.bodySha256.orElse(r.bodySha256), cond))
    val atts = foreignAttsOf(q.here, q.toLocal).getOrElse(r.uid, Nil).map(_ -> true) ++
      localAttsOf.getOrElse(l.uid, Nil).map(_ -> false)
    anchors(r.uid) = alternatives.map { (alt, kept, c) =>
      val tries = atts.sortBy((t, _) => t.uid.toString).flatMap { (t, foreign) =>
        val ownBody = if foreign then r.bodySha256 else l.bodySha256
        if kept.isEmpty || ownBody == kept || !anchored(t) then None
        else
          val body = kept.flatMap(in.bodies.get)
          val found = for
            txt <- body
            quote <- t.quote.filter(_.nonEmpty)
            a <- TextAnchor.reanchor(txt, quote, t.prefix, t.suffix, t.charStart)
          yield a
          found match
            case Some(a) =>
              Some(AnchorTry(t, foreign, Some(a), body.flatMap(TextAnchor.chunkOf(_, a, in.chunkChars)), None))
            case None =>
              val tag = alt.fold("")(DecisionKind.wireOf)
              val id = MergePlanner.issueId(
                IssueKind.AttestationUnanchored,
                BundleKind.Attestation,
                List(t.uid),
                List(l.uid),
                if foreign then q.rightRev(t.uid) else q.localRev(t.uid),
                kept.getOrElse(""),
                tag
              )
              val att = IssueSide(
                s"${t.targetType} ${t.targetId}",
                t.uid,
                t.quote.map(quote => s"«${MergeIssueDetail.preview(quote)}»").toList
              )
              val whose = if foreign then "the bundle's" else "this collection's"
              q.ask(
                MergeIssue(
                  id,
                  IssueKind.AttestationUnanchored,
                  IssueSeverity.Blocking,
                  BundleKind.Attestation,
                  s"$whose attestation on ${l.citekey}: its quote is not in the body the merge keeps" +
                    alt.fold("")(k => s" (if the body is ${DecisionKind.wireOf(k)})"),
                  if foreign then Some(MergeIssueDetail.side(l)) else Some(att),
                  if foreign then Some(att) else Some(MergeIssueDetail.side(r)),
                  List(if t.quote.exists(_.nonEmpty) then
                    "the quote (with its prefix and suffix) was looked for, exactly and with spacing ignored"
                  else "it has a position but no quote, so it cannot be found again"),
                  List(km.Drop, km.KeepUnanchored),
                  None,
                  c,
                  detail = Some(
                    IssueDetail(
                      List(
                        IssueField("quote", if foreign then None else t.quote, if foreign then t.quote else None),
                        IssueField(
                          "before it",
                          if foreign then None else t.prefix,
                          if foreign then t.prefix else None
                        ),
                        IssueField(
                          "after it",
                          if foreign then None else t.suffix,
                          if foreign then t.suffix else None
                        )
                      )
                    )
                  )
                ),
                Set.empty
              )
              Some(AnchorTry(t, foreign, None, None, Some(id)))
      }
      alt -> tries
    }.toMap

  override def pairIssue(kind: IssueKind): Boolean = kind == IssueKind.AttestationUnanchored

  // attestations: re-anchored into the body kept, or as decided; one already here is one
  override def foldPair(r: BundleSource, l: BundleSource, alt: Option[DecisionKind], f: PlanFolds): Unit =
    val tries = anchors.get(r.uid).flatMap(_.get(alt)).getOrElse(Nil)
    val localAtts = localAttsOf.getOrElse(l.uid, Nil)
    def targetKey(t: BundleAttestation): String =
      t.targetUid.map(u => f.foldedTo(u).getOrElse(f.toLocal(u)).toString).getOrElse(t.targetId)
    foreignAttsOf(f.here, f.toLocal).getOrElse(r.uid, Nil).foreach { t =>
      localAtts.find(x => x.targetType == t.targetType && targetKey(x) == targetKey(t) && x.quote == t.quote) match
        case Some(x) => f.fold(t.uid, x.uid, BundleKind.Attestation); attLine(x.uid) = _ => x
        case None    =>
          tries.find(a => a.foreign && a.att.uid == t.uid) match
            case None                                  => ()
            case Some(AnchorTry(_, _, Some(a), co, _)) =>
              attLine(t.uid) =
                es => attIn(es, t.uid).copy(charStart = Some(a.start), charEnd = Some(a.end), chunkOrd = co)
              f.count(_.modify(_.attestationsReanchored).using(_ + 1))
            case Some(AnchorTry(_, _, None, _, issue)) =>
              issue.flatMap(f.decisionIn).map(_.kind) match
                case Some(km.KeepUnanchored) =>
                  attLine(t.uid) = es => attIn(es, t.uid).copy(charStart = None, charEnd = None, chunkOrd = None)
                case _ =>
                  droppedUids += t.uid
                  f.count(_.modify(_.attestationsDropped).using(_ + 1))
    }
    tries.filterNot(_.foreign).foreach {
      case AnchorTry(t, _, Some(a), co, _) =>
        attFixes += AttestationFix(t.uid, Some(a), co, drop = false)
        f.count(_.modify(_.attestationsReanchored).using(_ + 1))
      case AnchorTry(t, _, None, _, issue) =>
        issue.flatMap(f.decisionIn).map(_.kind) match
          case Some(km.KeepUnanchored) => attFixes += AttestationFix(t.uid, None, None, drop = false)
          case _                       =>
            attFixes += AttestationFix(t.uid, None, None, drop = true)
            f.count(_.modify(_.attestationsDropped).using(_ + 1))
    }

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.attestations)
      .setTo(MergePlanner.replaced(es0.attestations, _.uid, attLine, es0, t => !droppedUids(t.uid)))

  override def dropped: Set[UUID] = droppedUids.toSet

  override def fixes: List[AttestationFix] = attFixes.result()

  override def tombstones: List[(UUID, BundleKind, UUID)] =
    droppedUids.toList.flatMap(u =>
      in.foreign.attestations.find(_.uid == u).map(t => (u, BundleKind.Attestation, t.origin))
    )

object AttestationPlan:

  /** An attestation that points into the text (a quote, or offsets): it must move with a body. */
  def anchored(t: BundleAttestation): Boolean = t.quote.exists(_.nonEmpty) || t.charStart.isDefined

  /** An attestation of a pair tried against the body one alternative keeps. */
  final private case class AnchorTry(
      att: BundleAttestation,
      foreign: Boolean,
      anchor: Option[Anchor],
      chunkOrd: Option[Int],
      issue: Option[String]
  )
