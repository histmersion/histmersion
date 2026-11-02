package com.histmersion.plugin

/** A collection of sources — a local Calibre library, archive.org's texts, a folder of PDFs — that the host searches
  * and imports from, and may write back to. A plugin adds one or more to [[Plugin.Context.sourceCollections]], each
  * under its own name; `histmersion calibre …` and `histmersion archive …` then run it.
  *
  * The host defines and runs every operation (the import into the library, the gaps check, the sync, the audit, their
  * surface); the collection only reads and writes its own items. Failures are values (`Left` with a sentence for a
  * person); an exception is caught by the host and reported as the operation's failure.
  *
  * Writing members are refused by default, and the host calls one only when [[capabilities]] declares it (`add` and
  * `replace` need [[CollectionCapability.Add]]; `update`, `updateAll` and `set` need [[CollectionCapability.Update]]).
  * A collection may launch processes (a library's own command-line tool) and read its own files.
  */
trait SourceCollection:
  /** What the collection is called in answers: "Calibre", "archive.org". */
  def title: String

  /** One sentence: what it holds and on what terms. */
  def description: String = ""

  /** What the host may do with it; [[CollectionCapability.Read]] alone by default. */
  def capabilities: Set[CollectionCapability] = Set(CollectionCapability.Read)

  /** Why the collection does not write, for the host's refusal of a write it did not declare: the setting that turns
    * writing on, when there is one.
    */
  def readOnlyReason: String = "it is read-only"

  /** Whether the collection can be reached from here: `Left` says what is missing (the library's folder, a permission
    * the operating system has not granted, a mount). The host asks before it runs an operation and in its diagnostics.
    */
  def reach: Either[String, Unit] = Right(())

  /** The items a search answers, in the collection's order. */
  def search(query: CollectionQuery): Either[String, List[CollectionItem]]

  /** One item by its id; `Right(None)` when the collection has no such item. */
  def item(id: CollectionId): Either[String, Option[CollectionItem]]

  /** Whether `item`'s metadata is good enough to make a source of; `Left` says what to fix first. */
  def admits(item: CollectionItem): Either[String, Unit] = Right(())

  /** The file of `item` an import takes: `format` when asked for and present, else the collection's own preference;
    * `Left` when the item has no file the host can convert. Reads nothing.
    */
  def file(item: CollectionItem, format: Option[String]): Either[String, CollectionFile]

  /** The bytes of `file` of `item`. */
  def original(item: CollectionItem, file: CollectionFile): Either[String, FetchedFile]

  /** The cover the collection keeps for `item`, when it keeps one; `Right(None)` when it has none. */
  def cover(item: CollectionItem): Either[String, Option[FetchedFile]] = Right(None)

  /** Add `file` as a new item described by `metadata`; the new item's id. */
  def add(metadata: CollectionMetadata, file: FetchedFile): Either[String, CollectionId] =
    Left(s"$title does not take new items: $readOnlyReason")

  /** Replace the file of the same format in item `id` with `file` (the original of the host's source `key`), keeping
    * the item and every link to it; the format replaced.
    */
  def replace(id: CollectionId, key: String, file: FetchedFile): Either[String, String] =
    Left(s"$title does not take new files: $readOnlyReason")

  /** Bring item `id` in line with `metadata`; the fields written (none when it already agrees). */
  def update(id: CollectionId, metadata: CollectionMetadata): Either[String, List[CollectionChange]] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Plan the update of every linked item at once (against one reading of the collection), and with `apply` write the
    * items that would change; one entry per item, in id order.
    */
  def updateAll(links: List[CollectionLink], apply: Boolean): Either[String, List[CollectionUpdateEntry]] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Set named fields on item `id`, as `name -> value` pairs in the collection's own field names. */
  def set(id: CollectionId, fields: List[(String, String)]): Either[String, Unit] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Compare every linked item with the host's record of it; the items that disagree, in id order. */
  def audit(records: List[CollectionRecord]): Either[String, List[CollectionFinding]] = Right(Nil)
