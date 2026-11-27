package com.histmersion
package api.research

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.{Agents, Goal, Pipeline, Queue}
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

import java.time.Instant
import scala.util.Try
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

object OpenWork:

  object Terms:
    type At = At.T
    object At extends Newtype[String, Pure]:
      def of(instant: Instant): At = At(instant.toString)

      extension (a: At)
        def instant: Option[Instant] = Try(Instant.parse(a.value)).toOption

        def shown: String = a.value.take(16).replace('T', ' ')

    type Count = Count.T
    object Count extends Newtype[Int, Pure]:
      def of(n: Int): Count = Count(n)

    type Who = Who.T
    object Who extends Newtype[String, Pure]:
      val all: List[Who] = List(Who("owner"), Who("agents"))

      extension (w: Who)
        def names(label: String): Boolean = w.value.trim.equalsIgnoreCase(label)

        def label: String = w.value

    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]:
      val all: List[Kind] = List(Kind("decision"), Kind("acquisition"), Kind("task"), Kind("run"))

      extension (k: Kind)
        def names(label: String): Boolean = k.value.trim.equalsIgnoreCase(label)

        def label: String = k.value

    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]:
      extension (f: Failures) def tries: String = if f.value == 1 then "1 try" else s"${f.value} tries"

    type Tries = Tries.T
    object Tries extends Newtype[Int, Pure]:
      extension (t: Tries) def shown: String = if t.value == 1 then "1 try" else s"${t.value} tries"

    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type LegacyNote = LegacyNote.T
    object LegacyNote extends Newtype[String, Pure]
    type Fetcher = Fetcher.T
    object Fetcher extends Newtype[String, Pure]
    type Generation = Generation.T
    object Generation extends Newtype[String, Pure]
    type OaStatus = OaStatus.T
    object OaStatus extends Newtype[String, Pure]
    type OaUrl = OaUrl.T
    object OaUrl extends Newtype[String, Pure]
    type OaLicence = OaLicence.T
    object OaLicence extends Newtype[String, Pure]

  import Terms.{At, Count, Failures, Tries}

  enum Who derives CanEqual:
    case Owner, Agents

    def label: String = this match
      case Owner  => "owner"
      case Agents => "agents"

    def wording: String = this match
      case Owner  => "wait for you"
      case Agents => "the agents will handle"

  object Who:
    def from(raw: Terms.Who): Option[Who] = values.find(w => raw.names(w.label))

  enum Kind derives CanEqual:
    case Decision, Acquisition, Task, Run

    def label: String = this match
      case Decision    => "decision"
      case Acquisition => "acquisition"
      case Task        => "task"
      case Run         => "run"

  object Kind:
    def from(raw: Terms.Kind): Option[Kind] = values.find(k => raw.names(k.label))

  enum NextStep derives CanEqual:
    case Decide, DownloadInBrowser, Borrow, BuyOrBorrow, AddIdentifier, LookAtStuckProvider, ResumeRun, DoTask

    def label: String = this match
      case Decide              => "decide"
      case DownloadInBrowser   => "download"
      case Borrow              => "borrow"
      case BuyOrBorrow         => "buy-or-borrow"
      case AddIdentifier       => "add-identifier"
      case LookAtStuckProvider => "stuck-provider"
      case ResumeRun           => "resume-run"
      case DoTask              => "task"

    def wording: String = this match
      case Decide              => "Decide"
      case DownloadInBrowser   => "Download in the browser"
      case Borrow              => "Borrow"
      case BuyOrBorrow         => "No free legal copy found: buy or borrow"
      case AddIdentifier       => "Add an identifier"
      case LookAtStuckProvider => "Look at a stuck provider"
      case ResumeRun           => "Resume a paused run"
      case DoTask              => "Do a task"

  enum When derives CanEqual:
    case Now, Today, Later

    def label: String = this match
      case Now   => "now"
      case Today => "today"
      case Later => "later"

    def wording: String = this match
      case Now   => "Now"
      case Today => "Today"
      case Later => "Later"

  object When:
    def of(next: Option[At], now: Instant): When =
      next.flatMap(_.instant) match
        case None                                           => Now
        case Some(t) if !t.isAfter(now)                     => Now
        case Some(t) if t.isBefore(now.plusSeconds(86400L)) => Today
        case Some(_)                                        => Later

  enum Verdict derives CanEqual:
    case Unchecked(due: At)
    case Converting(source: Option[Terms.Source])
    case Retrying(failures: Failures, next: At, last: Vector[Problem])
    case Stuck(failures: Failures, since: At, last: Vector[Problem])
    case ManualDownload(url: Option[Terms.OaUrl])
    case Borrow
    case NoFreeCopy(recheck: At)
    case NoIdentifier

    def who: Who = this match
      case _: Unchecked | _: Converting | _: Retrying                           => Who.Agents
      case _: Stuck | _: ManualDownload | Borrow | _: NoFreeCopy | NoIdentifier => Who.Owner

    def step: Option[NextStep] = this match
      case _: Unchecked | _: Converting | _: Retrying => None
      case _: Stuck                                   => Some(NextStep.LookAtStuckProvider)
      case _: ManualDownload                          => Some(NextStep.DownloadInBrowser)
      case Borrow                                     => Some(NextStep.Borrow)
      case _: NoFreeCopy                              => Some(NextStep.BuyOrBorrow)
      case NoIdentifier                               => Some(NextStep.AddIdentifier)

    def nextCheck: Option[At] = this match
      case Unchecked(due)       => Some(due)
      case Retrying(_, next, _) => Some(next)
      case NoFreeCopy(recheck)  => Some(recheck)
      case _                    => None

    def label: String = this match
      case _: Unchecked      => "not checked yet"
      case _: Converting     => "converting"
      case _: Retrying       => "retrying"
      case _: Stuck          => "stuck provider"
      case _: ManualDownload => "download in the browser"
      case Borrow            => "borrow"
      case _: NoFreeCopy     => "buy or borrow"
      case NoIdentifier      => "no identifier"

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wording: String = this match
      case Unchecked(due)     => s"Not checked under the current providers yet; due ${due.shown}"
      case Converting(source) =>
        s"A copy was found${source.fold("")(s => s" ($s)")}; it is being converted"
      case Retrying(failures, next, last) =>
        s"No answer from a provider (${failures.tries}); tried again ${next.shown}${lastSaid(last)}"
      case Stuck(failures, since, last) =>
        s"No answer from a provider since ${since.shown} (${failures.tries})${lastSaid(last)}"
      case ManualDownload(_)   => "Open access exists, but a script may not download it: download it in the browser"
      case Borrow              => "Only a lending copy was found: borrow it"
      case NoFreeCopy(recheck) =>
        s"No free legal copy found: buy or borrow (checked again ${recheck.shown})"
      case NoIdentifier => "No DOI or arXiv id: add one so the providers can be asked"

    private def lastSaid(last: Vector[Problem]): String =
      last.headOption.fold("")(p => s": ${p.sentence}")

  enum OpenItem derives CanEqual:
    case Decision(approval: Agents.ApprovalInfo, actor: Who)
    case Acquisition(
        item: Queue.QueueItem.Id,
        text: Queue.QueueItem.Text,
        verdict: Verdict,
        last: Option[At],
        tries: Tries,
        creator: Option[Creation],
        goal: Option[Goal.Slug]
    )
    case Task(
        item: Queue.QueueItem.Id,
        text: Queue.QueueItem.Text,
        actor: Who,
        creator: Option[Creation],
        goal: Option[Goal.Slug]
    )
    case PausedRun(
        run: Agents.RunInfo.Id,
        pipeline: Pipeline.Wire,
        cause: Option[Problem],
        resumes: Option[At],
        actor: Who
    )

    def who: Who = this match
      case Decision(_, actor)  => actor
      case a: Acquisition      => a.verdict.who
      case Task(_, _, a, _, _) => a
      case r: PausedRun        => r.actor

    def kind: Kind = this match
      case _: Decision    => Kind.Decision
      case _: Acquisition => Kind.Acquisition
      case _: Task        => Kind.Task
      case _: PausedRun   => Kind.Run

    def step: Option[NextStep] =
      if who != Who.Owner then None
      else
        this match
          case _: Decision    => Some(NextStep.Decide)
          case a: Acquisition => a.verdict.step
          case _: Task        => Some(NextStep.DoTask)
          case _: PausedRun   => Some(NextStep.ResumeRun)

    def next: Option[At] = this match
      case a: Acquisition => a.verdict.nextCheck
      case r: PausedRun   => r.resumes
      case _              => None

    def creatorNotRecorded: Boolean = this match
      case a: Acquisition => a.creator.exists(_.kind == Creation.Unattributed)
      case t: Task        => t.creator.exists(_.kind == Creation.Unattributed)
      case _              => false

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String = this match
      case Decision(a, _) =>
        s"decision #${a.id}: ${a.kind}${a.subject.fold("")(s => s" ($s)")}, waiting since ${a.createdAt}"
      case a: Acquisition =>
        s"acquisition #${a.item}: ${firstLine(a.text)} — ${a.verdict.wording}" +
          a.last.fold("")(l => s" · last check ${l.shown}") + s" · ${a.tries.shown}" + notRecorded
      case t: Task      => s"task #${t.item}: ${firstLine(t.text)}$notRecorded"
      case r: PausedRun =>
        s"run ${r.run} (${r.pipeline}) paused${r.cause.fold("")(c => s": ${c.sentence}")}" +
          r.resumes.fold("")(t => s"; resumes ${t.shown}")

    def mentions(words: String): Boolean =
      val wanted = words.trim.toLowerCase
      wanted.isEmpty || line.toLowerCase.contains(wanted)

    private def notRecorded: String = if creatorNotRecorded then " · creator not recorded" else ""

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def firstLine(text: Queue.QueueItem.Text): String = s"$text".linesIterator.nextOption().getOrElse("")

  final case class Totals(owner: Count, agents: Count) derives JsonCodec, KindlingsSchema, api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wording: String = s"$owner ${Who.Owner.wording} · $agents ${Who.Agents.wording}"

  object Totals:
    def of(items: Vector[OpenItem]): Totals =
      Totals(Count.of(items.count(_.who == Who.Owner)), Count.of(items.count(_.who == Who.Agents)))

  final case class State(totals: Totals, items: Vector[OpenItem], at: At)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    def owner: Vector[(NextStep, Vector[OpenItem])] =
      NextStep.values.toVector.flatMap(s => Option(items.filter(_.step.contains(s))).filter(_.nonEmpty).map(s -> _))

    def agents: Vector[(When, Vector[OpenItem])] =
      val now = at.instant.getOrElse(Instant.EPOCH)
      val mine = items.filter(_.who == Who.Agents)
      When.values.toVector.flatMap(w =>
        Option(mine.filter(i => When.of(i.next, now) == w)).filter(_.nonEmpty).map(w -> _)
      )

    def markdown: String =
      val own = owner.map((s, is) => s"\n\n**${s.wording}**\n" + is.map(i => s"- ${i.line}").mkString("\n"))
      val theirs = agents.map((w, is) => s"\n\n**${w.wording}**\n" + is.map(i => s"- ${i.line}").mkString("\n"))
      s"### Research state: ${totals.wording}" +
        (if own.isEmpty then "\n\n_Nothing waits for you._" else "\n\n#### Waiting for you" + own.mkString) +
        (if theirs.isEmpty then "" else "\n\n#### The agents will handle" + theirs.mkString)

  object State:
    def of(items: Vector[OpenItem], scope: Scope.Parsed, at: At): State =
      val chosen = items.filter(i => scope.kind.forall(_ == i.kind))
      State(Totals.of(chosen), chosen.filter(i => scope.who.forall(_ == i.who)), at)

  final case class Scope(
      who: Option[Terms.Who] = None,
      goal: Option[Goal.Slug] = None,
      kind: Option[Terms.Kind] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Scope:
    final case class Parsed(who: Option[Who], goal: Option[Goal.Slug], kind: Option[Kind])

    extension (s: Scope)
      def parsed: Parsed = Parsed(s.who.flatMap(Who.from), s.goal, s.kind.flatMap(Kind.from))

      def unknownWho: Option[Terms.Who] = s.who.filter(w => Who.from(w).isEmpty)

      def unknownKind: Option[Terms.Kind] = s.kind.filter(k => Kind.from(k).isEmpty)

  enum Trigger derives CanEqual:
    case PersonIngest, WatcherBacklog, Recheck, OldCheck

    def wording: String = this match
      case PersonIngest   => "a person's ingest"
      case WatcherBacklog => "the watchers' backlog"
      case Recheck        => "the legal re-check"
      case OldCheck       => "an older check"

  enum Said derives CanEqual:
    case NoIdentifier, NotHeld, Unanswered, RateLimited, Unconfigured, BotChallenge, LendingOnly, NoEntitlement,
      LicenceForbids

    def wording: String = this match
      case NoIdentifier   => "needs an identifier the work does not have"
      case NotHeld        => "does not have it"
      case Unanswered     => "did not answer"
      case RateLimited    => "asked us to slow down"
      case Unconfigured   => "is not set up"
      case BotChallenge   => "refused a script (a bot challenge)"
      case LendingOnly    => "lends it only"
      case NoEntitlement  => "has it, but not for us"
      case LicenceForbids => "has it, but its licence forbids the download"

  final case class Answer(fetcher: Terms.Fetcher, said: Said, why: Problem)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String = s"$fetcher ${said.wording}"

  enum Outcome derives CanEqual:
    case Closed(citekey: Citekey)
    case Fetched(source: Terms.Source)
    case NoCopy
    case Failed(why: Vector[Problem])
    case OldCheck(note: Terms.LegacyNote, checks: Tries)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wording: String = this match
      case Closed(citekey)   => s"closed: the corpus holds it as $citekey"
      case Fetched(source)   => s"a copy was fetched ($source)"
      case NoCopy            => "no copy"
      case Failed(why)       => s"no answer${why.headOption.fold("")(p => s": ${p.sentence}")}"
      case OldCheck(note, n) => s"an older check (${n.shown}): $note"

  enum Scripts derives CanEqual:
    case Allowed, Refused

  final case class Oa(
      status: Option[Terms.OaStatus],
      url: Option[Terms.OaUrl],
      licence: Option[Terms.OaLicence],
      scripts: Scripts
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String =
      List(
        status.map(s => s"open access: $s"),
        url.map(u => s"at $u"),
        licence.map(l => s"licence $l"),
        Option.when(scripts == Scripts.Refused)("scripts refused")
      ).flatten.mkString(", ")

  final case class Attempt(
      at: At,
      trigger: Trigger,
      generation: Terms.Generation,
      outcome: Outcome,
      answers: Vector[Answer],
      oa: Option[Oa]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    def markdown: String =
      s"- ${at.shown} · ${trigger.wording} · ${outcome.wording}" +
        oa.map(o => o.line).filter(_.nonEmpty).fold("")(l => s" · $l") +
        answers.map(a => s"\n  - ${a.line}: ${a.why.sentence}").mkString

  final case class History(
      item: Queue.QueueItem.Id,
      text: Queue.QueueItem.Text,
      verdict: Verdict,
      attempts: Vector[Attempt]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown: String =
      s"### Acquisition #$item: ${s"$text".linesIterator.nextOption().getOrElse("")}\n\n${verdict.wording}\n\n" +
        (if attempts.isEmpty then "_Not checked yet._" else attempts.map(_.markdown).mkString("\n"))
