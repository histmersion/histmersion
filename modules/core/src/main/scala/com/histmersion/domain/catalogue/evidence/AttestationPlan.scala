package com.histmersion.domain.catalogue.evidence
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{IssueCondition, IssueDetail, IssueField, IssueSide, MergeIssue}
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergePlanner.AttestationFix
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.PairQuestions
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanRun
import com.histmersion.util.text.Anchor

import hearth.kindlings.optics.syntax.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** How a merge plan treats attestations: they travel with the sources they quote. When a pair of sources that are one
  * keeps one OCR body, the attestations that point into the other are found again in it (re-anchored) — under each
  * alternative of the body question, so nothing new appears after it is answered; one whose quote is not there is a
  * question (drop it, or keep it without a place). One already here (the same target and quote) is one.
  */
final class AttestationPlan(in: MergePlanner.Input) extends PlanRun:
  import AttestationPlan.*

  private val km = MergeDecision.Kind

  // per pair and per alternative of the body question (the body issue's decision, or None when there is no
  // question): the attestations tried against the body it keeps
  private val anchors = scala.collection.mutable.Map.empty[UUID, Map[Option[MergeDecision.Kind], List[AnchorTry]]]
  private val attLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => com.histmersion.api.exchange.Bundle.Attestation]
  private val droppedUids = scala.collection.mutable.LinkedHashSet.empty[UUID] // bundle uids left out for good
  private val attFixes = List.newBuilder[AttestationFix]
  private val attSource = in.foreign.attestations.map(t => t.uid -> t.sourceUid).toMap
  private val localAttsOf = in.local.attestations.groupBy(_.sourceUid)
  private def attIn(es: BundleEntities, u: UUID) = es.attestations.find(_.uid == u).get

  /** The bundle's attestations that have no uid here yet, by their source. */
  private def foreignAttsOf(
      here: UUID => Boolean,
      toLocal: UUID => UUID
  ): Map[UUID, List[com.histmersion.api.exchange.Bundle.Attestation]] =
    in.foreign.attestations.filterNot(t => here(toLocal(t.uid))).groupBy(_.sourceUid)

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] =
    if kind == Bundle.Kind.Attestation then attSource.get(foreign) else None

  override def bodiesWanted(
      pairs: List[(com.histmersion.api.exchange.Bundle.Source, com.histmersion.api.exchange.Bundle.Source)]
  ): Set[String] =
    val withAtts = (in.foreign.attestations ++ in.local.attestations).filter(anchored).map(_.sourceUid).toSet
    pairs.flatMap { (r, l) =>
      val (a, b) = (r.bodySha256, l.bodySha256)
      if a != b && (withAtts(r.uid) || withAtts(l.uid)) then a.toList ++ b.toList else Nil
    }.toSet

  override def pair(
      r: com.histmersion.api.exchange.Bundle.Source,
      l: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      bodyIssue: Option[String],
      q: PairQuestions
  ): Unit =
    val alternatives: List[(Option[MergeDecision.Kind], Option[String], Option[IssueCondition])] = bodyIssue match
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
              val tag = alt.fold("")(MergeDecision.Kind.wireOf)
              val id = MergePlanner.issueId(
                MergeIssue.Kind.AttestationUnanchored,
                Bundle.Kind.Attestation,
                List(t.uid),
                List(l.uid),
                if foreign then q.rightRev(t.uid) else q.localRev(t.uid),
                kept.getOrElse(""),
                tag
              )
              val att = IssueSide(
                s"${t.targetType} ${t.targetId}",
                t.uid,
                t.quote.map(quote => s"«${MergeIssue.Detail.preview(quote)}»").toList
              )
              val whose = if foreign then "the bundle's" else "this collection's"
              q.ask(
                MergeIssue(
                  id,
                  MergeIssue.Kind.AttestationUnanchored,
                  MergeIssue.Severity.Blocking,
                  Bundle.Kind.Attestation,
                  s"$whose attestation on ${l.citekey}: its quote is not in the body the merge keeps" +
                    alt.fold("")(k => s" (if the body is ${MergeDecision.Kind.wireOf(k)})"),
                  if foreign then Some(MergeIssue.Detail.side(l)) else Some(att),
                  if foreign then Some(att) else Some(MergeIssue.Detail.side(r)),
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

  override def pairIssue(kind: MergeIssue.Kind): Boolean = kind == MergeIssue.Kind.AttestationUnanchored

  // attestations: re-anchored into the body kept, or as decided; one already here is one
  override def foldPair(
      r: com.histmersion.api.exchange.Bundle.Source,
      l: com.histmersion.api.exchange.Bundle.Source,
      alt: Option[MergeDecision.Kind],
      f: PlanFolds
  ): Unit =
    val tries = anchors.get(r.uid).flatMap(_.get(alt)).getOrElse(Nil)
    val localAtts = localAttsOf.getOrElse(l.uid, Nil)
    def targetKey(t: com.histmersion.api.exchange.Bundle.Attestation): String =
      t.targetUid.map(u => f.foldedTo(u).getOrElse(f.toLocal(u)).toString).getOrElse(t.targetId)
    foreignAttsOf(f.here, f.toLocal).getOrElse(r.uid, Nil).foreach { t =>
      localAtts.find(x => x.targetType == t.targetType && targetKey(x) == targetKey(t) && x.quote == t.quote) match
        case Some(x) => f.fold(t.uid, x.uid, Bundle.Kind.Attestation); attLine(x.uid) = _ => x
        case None    =>
          tries.find(a => a.foreign && a.att.uid == t.uid) match
            case None                                  => ()
            case Some(AnchorTry(_, _, Some(a), co, _)) =>
              attLine(t.uid) =
                es => attIn(es, t.uid).modify(_.charStart).setTo(Some(a.start)).modify(_.charEnd).setTo(Some(a.end)).modify(_.chunkOrd).setTo(co)
              f.count(_.modify(_.attestationsReanchored).using(_ + 1))
            case Some(AnchorTry(_, _, None, _, issue)) =>
              issue.flatMap(f.decisionIn).map(_.kind) match
                case Some(km.KeepUnanchored) =>
                  attLine(t.uid) = es => attIn(es, t.uid).modify(_.charStart).setTo(None).modify(_.charEnd).setTo(None).modify(_.chunkOrd).setTo(None)
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

  override def tombstones: List[(UUID, Bundle.Kind, UUID)] =
    droppedUids.toList.flatMap(u =>
      in.foreign.attestations.find(_.uid == u).map(t => (u, Bundle.Kind.Attestation, t.origin))
    )

object AttestationPlan:

  /** An attestation that points into the text (a quote, or offsets): it must move with a body. */
  def anchored(t: com.histmersion.api.exchange.Bundle.Attestation): Boolean =
    t.quote.exists(_.nonEmpty) || t.charStart.isDefined

  /** An attestation of a pair tried against the body one alternative keeps. */
  final private case class AnchorTry(
      att: com.histmersion.api.exchange.Bundle.Attestation,
      foreign: Boolean,
      anchor: Option[Anchor],
      chunkOrd: Option[Int],
      issue: Option[String]
  )
