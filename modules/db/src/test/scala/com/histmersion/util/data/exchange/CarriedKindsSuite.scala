package com.histmersion
package util.data.exchange

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.storage.ContentStore
import com.histmersion.lint.{LintRule, tolerated}

/** The carried kinds and merge rules an export, an import and a plan run over: every entry of the format once, in its
  * order, whoever registered first; the import's steps in stage and rank order; each kind of record's table.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CarriedKindsSuite extends munit.FunSuite:

  private val kinds = TestExchange.kinds

  test("today's kinds carry every entry of the bundle format once, in the format's order"):
    assertEquals(kinds.inBundleOrder.map(_.entry.value), api.exchange.Bundle.Files.all)

  test("the order of the entries does not depend on the order the kinds were registered in"):
    assertEquals(
      CarriedKinds(TestExchange.carried.reverse).inBundleOrder.map(_.entry.value),
      kinds.inBundleOrder.map(_.entry.value)
    )

  test("a registry missing an entry, carrying one twice or one the format does not have is refused, each named"):
    val sources = TestExchange.carried.find(_.entry.value == api.exchange.Bundle.Kind.Source.file).get
    val odd = new CarriedKind:
      def owner: DataOwner = DataOwner("x")
      def entry: BundleEntry = BundleEntry("entities/odd.ndjson")
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def out(scope: BundleScope, files: ContentStore)(using
          com.augustnagro.magnum.DbCon
      ): CarriedOut = CarriedOut(CarriedOut.Payload(Array.emptyByteArray), None)
      def into(scope: BundleScope, files: ContentStore)(using
          com.augustnagro.magnum.DbCon
      ): BundleEntities => BundleEntities =
        identity
    val withoutTombstones =
      TestExchange.carried.filterNot(_.entry.value == api.exchange.Bundle.Files.Tombstones)
    assertEquals(
      CarriedKinds.of(sources :: odd :: withoutTombstones).left.toOption.map(_.map(_.sentence)),
      Some(
        List(
          s"the bundle holds no ${api.exchange.Bundle.Files.Tombstones}",
          s"${api.exchange.Bundle.Kind.Source.file} appears twice in the bundle",
          "entities/odd.ndjson is not a file a bundle holds"
        )
      )
    )

  test("every kind of record has its table"):
    assertEquals(kinds.tableOf(api.exchange.Bundle.Kind.Source), TableName("sources"))
    assertEquals(kinds.tableOf(api.exchange.Bundle.Kind.ArticleVersion), TableName("article_versions"))
    assertEquals(kinds.tableOf(api.exchange.Bundle.Kind.QueueItem), TableName("queue_items"))
    assertEquals(
      api.exchange.Bundle.Kind.values.toList.map(kinds.tableOf).distinct.size,
      api.exchange.Bundle.Kind.values.length
    )

  test("the import's steps: tombstones first, the rows before the blobs, links after them, derived state last"):
    val rows = kinds.steps(ImportStage.Rows).map(_.rank.value: Int)
    assertEquals(rows, rows.sorted)
    assertEquals(rows.head, 0)
    assertEquals(rows.distinct.size, rows.size)
    assertEquals(kinds.steps(ImportStage.AfterBlobs).map(_.rank.value: Int), List(100, 101, 102, 103))
    assertEquals(kinds.steps(ImportStage.Derived).map(_.rank.value: Int), List(200, 201))

  test("every carried kind and merge rule is registered by one of the owners"):
    val owners = Set("automation", "catalogue", "media", "library", "research", "util.identity")
    assertEquals(TestExchange.carried.map(_.owner.value).filterNot(owners), Nil)
    assertEquals(TestExchange.rules.all.map(_.owner.value).filterNot(owners), Nil)
    assertEquals(
      owners.toList.sorted.map(o => o -> TestExchange.carried.count(_.owner == DataOwner(o))),
      List("automation" -> 3, "catalogue" -> 11, "library" -> 6, "media" -> 2, "research" -> 3, "util.identity" -> 1)
    )

  test("merge rules: the ledger names the records of every kind it has a label for; lines point at five kinds"):
    val rules = TestExchange.rules
    val label = rules.ledgerLabelSql
    val kindsNamed = List("source", "finding", "article", "asset", "event", "goal", "place", "period", "queue-item")
    // each kind bound through its codec, one `WHEN ? THEN (…)` per kind, in the parameters' order
    kindsNamed.foreach(k => assert(label.params.map(_.toString).contains(k), k))
    assertEquals(label.sqlString.split("WHEN \\? THEN \\(", -1).length - 1, label.params.size)
    assert(label.sqlString.startsWith("CASE l.kind WHEN") && label.sqlString.endsWith(" END"))
    assertEquals(
      rules.referableTables(kinds).map(_.value).sorted,
      List("articles", "assets", "events", "goals", "sources")
    )
    assertEquals(rules.all.map(_.kind).sortBy(_.ordinal), api.exchange.Bundle.Kind.values.toList)
