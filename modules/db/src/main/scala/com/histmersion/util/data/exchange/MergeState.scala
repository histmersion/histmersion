package com.histmersion
package util.data.exchange
import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.MergePlanner.{Input, Labels, LedgerEntry}
import com.histmersion.util.text.MinHash

import com.augustnagro.magnum.*
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.util.data.TableName
import com.histmersion.util.data.exchange.{
  BundleFormat,
  BundleReader,
  BundleScope,
  BundleTar,
  CarriedKinds,
  MergePlans,
  MergeRuleSet
}
import com.histmersion.util.storage.{ByteSource, ContentStore}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.api.exchange.{Bundle, IssuePlace, MergeDecision, MergeIssue, MergePlan, MergeResolutions}
import com.histmersion.Sha256
import com.histmersion.api.library.{Citekey, Finding}
import com.histmersion.api.research.ReviewGoal
import com.histmersion.domain.catalogue.evidence.TextAnchor

/** What this collection knows that a merge plan needs (docs/research-merge-import.md §5–§6, milestone M3): the plan's
  * input stage — its rows, labels, ledger, redirects, article histories and OCR bodies, and what each kind's merge
  * rules add (the catalogue's matched places and periods). [[MergePlanner]] is the pure rest; [[MergePlans]] puts the
  * two together.
  */
object MergeState:
  private given CanEqual[UUID, UUID] = CanEqual.derived

  private def ids(us: Iterable[UUID]): List[String] = us.iterator.map(_.toString).toList.distinct

  def load(
      es: BundleEntities,
      m: Bundle.Manifest,
      readProblems: List[Input.ReadProblems],
      originals: ContentStore,
      chunkChars: ImportOptions.ChunkChars,
      cfg: MergeSettings,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  )(using DbCon): Input =
    val inst = BundleRepo.instance
    val schema = BundleRepo.schemaVersion
    // the bundle's own uids and those it refers to (an incremental bundle names records it does not carry)
    val foreignUids = (es.revs.keySet ++ es.tombstones.map(_.uid) ++ es.references.map(_.uid)).toList.distinct
    // the redirects are few (one per merged-away record): all of them, so chains resolve
    val uidAliases = sql"SELECT alias_uid, uid FROM uid_aliases".query[(UUID, UUID)].run().toMap
    val ledger = sql"""SELECT foreign_uid, foreign_rev, local_uid, local_rev_after FROM merge_ledger
                       WHERE foreign_uid = ANY(${ids(foreignUids)}::uuid[])"""
      .query[(UUID, String, UUID, Option[String])]
      .run()
      .map((f, r, l, a) =>
        f -> LedgerEntry(BundlePlan.Ledger.ForeignRev(r), l, a.map(x => BundlePlan.Ledger.LocalRevAfter(x)))
      )
      .toMap
    val idOf = MergePlanner.mapUid(uidAliases, ledger)
    val mapped = foreignUids.map(f => f -> idOf(f)).filter((f, l) => f != l).toMap
    val localUids = foreignUids.map(idOf)

    // who has the bundle's labels here (the owners that register their own merge rules add theirs to the input)
    def owners(q: String, labels: List[String]): Map[String, UUID] =
      if labels.isEmpty then Map.empty
      else Sql.of(Sql.Text(q), Param.A(labels.distinct.map(l => Param.A.V(l)))).frag.query[(String, UUID)].run().toMap
    def slugOwners(q: String, labels: List[ReviewGoal.Slug]): Map[ReviewGoal.Slug, UUID] =
      owners(q, labels.map(_.value)).map((s, u) => ReviewGoal.Slug(s) -> u)
    val localized = MergePlanner.localize(es, mapped, Map.empty)
    val labels = Labels(
      citekeys = sql"SELECT citekey, uid FROM sources".query[(String, UUID)].run().map((c, u) => Citekey(c) -> u).toMap,
      aliases = sql"SELECT a.alias, s.uid FROM source_aliases a JOIN sources s ON s.citekey = a.citekey"
        .query[(String, UUID)]
        .run()
        .map((c, u) => Citekey(c) -> u)
        .toMap,
      findings = owners("SELECT id, uid FROM findings WHERE id = ANY(?::text[])", es.findings.map(_.id.value))
        .map((f, u) => Finding.Id(f) -> u),
      slugs = slugOwners(
        "SELECT slug, uid FROM articles WHERE slug = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)
      ),
      goals = slugOwners(
        "SELECT slug, uid FROM goals WHERE slug = ANY(?::text[])",
        es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)
      ),
      articleAliases = slugOwners(
        "SELECT x.alias, a.uid FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE x.alias = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)
      ),
      goalAliases = slugOwners(
        "SELECT x.alias, g.uid FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE x.alias = ANY(?::text[])",
        es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)
      )
    )
    // every row here: a merged pair needs the survivor's findings, assets, attestations and edges, a
    // label taken needs the record that has it (only metadata — bodies and bytes stay in the database)
    val local = kinds.entities(BundleScope.All, originals)

    val referenced = localized.references.map(_.uid)
    val asked = (localUids ++ referenced ++ labels.citekeys.values ++ labels.aliases.values).distinct
    val tombstoned = sql"SELECT uid FROM tombstones WHERE uid = ANY(${ids(asked)}::uuid[])".query[UUID].run().toSet
    val live = liveUids(referenced.distinct, rules.referableTables(kinds))
    // the recorded versions of the articles the bundle names (by uid or by slug): where a three-way
    // merge finds its common base
    val articleUids = (localUids ++ labels.slugs.values).distinct
    val history = sql"""SELECT a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'), v.body
                        FROM article_versions v JOIN articles a ON a.slug = v.slug
                        WHERE a.uid = ANY(${ids(articleUids)}::uuid[])
                        ORDER BY a.uid, v.created_at DESC, v.id DESC"""
      .query[(UUID, String, String)]
      .run()
      .toList
      .groupMap(_._1)((_, sha, body) => (Sha256.assume(sha), Bundle.Article.Body(body)))
    val remembered = sql"SELECT issue_id, decision FROM merge_decisions"
      .query[(String, String)]
      .run()
      .flatMap((id, d) => MergeDecision.parse(MergeDecision.Wire(d)).toOption.map(MergeIssue.Id(id) -> _))
      .toMap
    val known = Input(
      m,
      Bundle.Instance(inst.id, inst.label.map(l => Bundle.Instance.Label(l.value))),
      BundleFormat.compatible(m, schema).map(c => Input.Compat(c)),
      readProblems,
      es,
      uidAliases,
      ledger,
      tombstoned,
      local,
      local.sources,
      labels,
      live,
      Set.empty,
      BundleRepo.reference,
      // the same rendering as BundleSql.ts, so a bundle alias's createdAt compares as a string
      aliasTombstones = sql"""SELECT alias, to_char(deleted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
               FROM alias_tombstones"""
        .query[(String, String)]
        .run()
        .map((a, t) => Citekey(a) -> Input.AliasTombstonesValue(t))
        .toMap,
      articleHistory = history,
      remembered = remembered,
      chunkChars = TextAnchor.ChunkChars(chunkChars.value)
    )
    rules.gather(es, localized, known, cfg)

  /** The plan's issues with their geometries (hex EWKB from the planner) turned into GeoJSON, which the review screen
    * draws — one query for every geometry of the plan.
    */
  def withGeoJson(p: MergePlan)(using DbCon): MergePlan =
    val hexes: List[String] =
      p.issues
        .flatMap(_.detail.flatMap(_.place))
        .flatMap(x => x.leftGeometry.toList.map(_.value) ++ x.rightGeometry.toList.map(_.value))
        .distinct
    if hexes.isEmpty then p
    else
      val json = Sql
        .of(
          Sql.Text("""SELECT h, ST_AsGeoJSON(ST_GeomFromEWKB(decode(h, 'hex')), 6) FROM unnest(?::text[]) AS h"""),
          Param.A(hexes.map(h => Param.A.V(h)))
        )
        .frag
        .query[(String, Option[String])]
        .run()
        .flatMap((h, j) => j.map(h -> _))
        .toMap
      def conv(g: Option[String]) = g.flatMap(json.get)
      p.modify(_.issues)
        .setTo(
          p.issues.map(i =>
            i.modify(_.detail)
              .setTo(
                i.detail.map(d =>
                  d.modify(_.place)
                    .setTo(
                      d.place
                        .map(x =>
                          x.modify(_.leftGeometry)
                            .setTo(conv(x.leftGeometry.map(_.value)).map(g => IssuePlace.LeftGeometry(g)))
                            .modify(_.rightGeometry)
                            .setTo(conv(x.rightGeometry.map(_.value)).map(g => IssuePlace.RightGeometry(g)))
                        )
                    )
                )
              )
          )
        )

  /** The uids, among `uids`, of rows that exist here, in the tables of the kinds a line can refer to. */
  private[histmersion] def liveUids(uids: List[UUID], tables: List[TableName])(using DbCon): Set[UUID] =
    if uids.isEmpty || tables.isEmpty then Set.empty
    else
      val us = ids(uids)
      Sql
        .of(
          Sql.Text(tables.map(t => s"SELECT uid FROM ${t.value} WHERE uid = ANY(?::uuid[])").mkString(" UNION ALL ")),
          tables.map(_ => Param.A(us.map(u => Param.A.V(u))))*
        )
        .frag
        .query[UUID]
        .run()
        .toSet

  /** MinHash signatures of the OCR bodies `wanted` names (by sha256): this collection's from the database, the bundle's
    * from one pass over its blobs. Only bodies of pairs whose score they can change are asked for
    * ([[MergePlanner.sketchesWanted]]).
    */
  def sketches(bundle: ByteSource, in: Input, wanted: Set[Bundle.Source.BodySha256], size: MinHash.Size)(using
      DbCon
  ): Map[Bundle.Source.BodySha256, MinHash] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources
        .filter(s => s.bodySha256.exists(wanted))
        .distinctBy(_.bodySha256)
        .flatMap { s =>
          sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}"
            .query[Option[String]]
            .run()
            .headOption
            .flatten
            .map(b => s.bodySha256.get -> MinHash.of(MinHash.Text(b), size))
        }
        .toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else bundleSketches(bundle, there, size))

  /** The OCR bodies `wanted` names (by sha256), as text: this collection's from the database, the bundle's from one
    * pass over its blobs ([[MergePlanner.bodiesWanted]]).
    */
  def bodies(bundle: ByteSource, in: Input, wanted: Set[Bundle.Source.BodySha256])(using
      DbCon
  ): Map[Bundle.Source.BodySha256, TextAnchor.Text] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources
        .filter(s => s.bodySha256.exists(wanted))
        .distinctBy(_.bodySha256)
        .flatMap { s =>
          sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}"
            .query[Option[String]]
            .run()
            .headOption
            .flatten
            .map(b => s.bodySha256.get -> TextAnchor.Text(b))
        }
        .toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else blobTexts(bundle, there))

  private def blobTexts(
      bundle: ByteSource,
      wanted: Set[Bundle.Source.BodySha256]
  ): Map[Bundle.Source.BodySha256, TextAnchor.Text] =
    bundle.read { in =>
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[Bundle.Source.BodySha256, TextAnchor.Text]): Map[Bundle.Source.BodySha256, TextAnchor.Text] =
        if acc.size == wanted.size then acc
        else
          tar.next() match
            case Left(_) | Right(None) => acc
            case Right(Some(e))        =>
              Bundle.blobSha(e.name.value).map(s => Bundle.Source.BodySha256(s.value)).filter(wanted) match
                case Some(sha) => loop(acc + (sha -> TextAnchor.Text(String(tar.readAll(), UTF_8))))
                case None      => loop(acc)
      loop(Map.empty)
    }

  private def bundleSketches(
      bundle: ByteSource,
      wanted: Set[Bundle.Source.BodySha256],
      size: MinHash.Size
  ): Map[Bundle.Source.BodySha256, MinHash] =
    bundle.read { in =>
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[Bundle.Source.BodySha256, MinHash]): Map[Bundle.Source.BodySha256, MinHash] =
        if acc.size == wanted.size then acc
        else
          tar.next() match
            case Left(_) | Right(None) => acc
            case Right(Some(e))        =>
              Bundle.blobSha(e.name.value).map(s => Bundle.Source.BodySha256(s.value)).filter(wanted) match
                case Some(sha) => loop(acc + (sha -> MinHash.of(MinHash.Text(String(tar.readAll(), UTF_8)), size)))
                case None      => loop(acc)
      loop(Map.empty)
    }

  /** `merge plan` of a bundle on disk ([[MergePlans.planFile]]). */
  def planFile(
      ds: DataSource,
      file: Path,
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      originals: ContentStore,
      chunkChars: ImportOptions.ChunkChars,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  ): Either[MergePlans.PlanFileText, MergePlan] =
    MergePlans.planFile(ds, ByteSource.file(file), resolutions, cfg, originals, chunkChars, kinds, rules)

  private[histmersion] def withFile[A](file: Path)(f: InputStream => A): A = ByteSource.file(file).read(f)
