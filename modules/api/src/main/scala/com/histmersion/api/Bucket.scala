package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One node of the aggregation tree (plan §1.6).
  *
  *   - `kind = "time"` → `start`/`end` are the bucket's inclusive signed years, `placeId` is empty.
  *   - `kind = "region"` → `placeId` is the gazetteer id and `start`/`end` are the PLACE's own validity (empty for a
  *     place valid always, and for the trailing `unlocated` bucket, whose `key` is `"unlocated"` and which has no
  *     `placeId`).
  *
  * `count` is distinct units (sources, or findings for `unit=findings`) in the whole subtree — NOT the sum of the
  * children, because a unit belongs to every bucket it overlaps. `overlap` is how many of them are in more than one
  * child ("12 sources, 3 in more than one subregion"); it is 0 where the question is meaningless (finer time buckets).
  * `key` is unique among siblings and is what the UI sends back to fetch the subtree lazily.
  */
final case class Bucket(
    label: String,
    key: String,
    kind: String,
    start: Option[Long] = None,
    end: Option[Long] = None,
    placeId: Option[String] = None,
    count: Int = 0,
    overlap: Int = 0,
    contested: Boolean = false,
    /** Other parents this node also sits under, by label. The gazetteer is a DAG — "Anatolia" is part of Asia AND of
      * Europe — and a node is drawn under ONE of them, so this is how the other paths stay visible ("(also under
      * Europe)") instead of the subtree being silently duplicated.
      */
    alsoUnder: List[String] = Nil,
    children: List[Bucket] = Nil,
    samples: List[BucketSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Bucket:
  /** [[Bucket]] is RECURSIVE (`children: List[Bucket]`), and that is why its schema is exposed as a
    * `KindlingsSchema[Bucket]` (through the `derives` clause) rather than as the `given Schema[…]` every other DTO in
    * this codebase carries.
    *
    * Kindlings derives recursive types by itself — it emits a tapir `SRef` for the back-edge — but only as long as its
    * "use the implicit already in scope" rule does not fire first. A Scala 3 `given` with an ascribed type is visible
    * inside its own right-hand side, so a `given Schema[Bucket] = …derived…` here is found by the derivation when it
    * reaches the `children` field, and the emitted code refers to the very lazy val being initialised: the result
    * compiles and then HANGS on first use (a lazy val awaiting its own latch — tapir's own `Schema.derived` has the
    * same failure, softwaremill/tapir#2749).
    *
    * With only the `KindlingsSchema[Bucket]` instance in scope there is no `Schema[Bucket]` for that rule to find, the
    * self-reference rule fires as intended, and every enclosing DTO ([[TimeHistogram]]) picks this up through the same
    * Kindlings derivation. The schema is still DERIVED from this case class, so the JSON on the wire and the
    * OpenAPI/MCP schema cannot drift.
    *
    * Ask for it with `Bucket.schema` (or `summon[KindlingsSchema[Bucket]].schema`); do NOT add a `given Schema[Bucket]`
    * here.
    */
  def schema: Schema[Bucket] = summon[KindlingsSchema[Bucket]].schema
