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
  def title: com.histmersion.plugin.SourceCollection.TitleResult

  /** One sentence: what it holds and on what terms. */
  def description: com.histmersion.plugin.SourceCollection.DescriptionResult = ""

  /** What the host may do with it; [[CollectionCapability.Read]] alone by default. */
  def capabilities: Set[CollectionCapability] = Set(CollectionCapability.Read)

  /** Why the collection does not write, for the host's refusal of a write it did not declare: the setting that turns
    * writing on, when there is one.
    */
  def readOnlyReason: com.histmersion.plugin.SourceCollection.ReadOnlyReasonResult = "it is read-only"

  /** Whether the collection can be reached from here: `Left` says what is missing (the library's folder, a permission
    * the operating system has not granted, a mount). The host asks before it runs an operation and in its diagnostics.
    */
  def reach: Either[com.histmersion.plugin.SourceCollection.ReachResultPart1, Unit] = Right(())

  /** The items a search answers, in the collection's order. */
  def search(query: CollectionQuery): Either[com.histmersion.plugin.SourceCollection.SearchResultPart1, List[CollectionItem]]

  /** One item by its id; `Right(None)` when the collection has no such item. */
  def item(id: CollectionId): Either[com.histmersion.plugin.SourceCollection.ItemResultPart1, Option[CollectionItem]]

  /** Whether `item`'s metadata is good enough to make a source of; `Left` says what to fix first. */
  def admits(item: CollectionItem): Either[com.histmersion.plugin.SourceCollection.AdmitsResultPart1, Unit] = Right(())

  /** The file of `item` an import takes: `format` when asked for and present, else the collection's own preference;
    * `Left` when the item has no file the host can convert. Reads nothing.
    */
  def file(item: CollectionItem, format: Option[com.histmersion.plugin.SourceCollection.FileFormat]): Either[com.histmersion.plugin.SourceCollection.FileResultPart1, CollectionFile]

  /** The bytes of `file` of `item`. */
  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.plugin.SourceCollection.OriginalResultPart1, FetchedFile]

  /** The cover the collection keeps for `item`, when it keeps one; `Right(None)` when it has none. */
  def cover(item: CollectionItem): Either[com.histmersion.plugin.SourceCollection.CoverResultPart1, Option[FetchedFile]] = Right(None)

  /** Add `file` as a new item described by `metadata`; the new item's id. */
  def add(metadata: CollectionMetadata, file: FetchedFile): Either[com.histmersion.plugin.SourceCollection.AddResultPart1, CollectionId] =
    Left(s"$title does not take new items: $readOnlyReason")

  /** Replace the file of the same format in item `id` with `file` (the original of the host's source `key`), keeping
    * the item and every link to it; the format replaced.
    */
  def replace(id: CollectionId, key: com.histmersion.plugin.SourceCollection.ReplaceKey, file: FetchedFile): Either[com.histmersion.plugin.SourceCollection.ReplaceResultPart1, com.histmersion.plugin.SourceCollection.ReplaceResultPart2] =
    Left(s"$title does not take new files: $readOnlyReason")

  /** Bring item `id` in line with `metadata`; the fields written (none when it already agrees). */
  def update(id: CollectionId, metadata: CollectionMetadata): Either[com.histmersion.plugin.SourceCollection.UpdateResultPart1, List[CollectionChange]] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Plan the update of every linked item at once (against one reading of the collection), and with `apply` write the
    * items that would change; one entry per item, in id order.
    */
  def updateAll(links: List[CollectionLink], apply: com.histmersion.plugin.SourceCollection.UpdateAllApply): Either[com.histmersion.plugin.SourceCollection.UpdateAllResultPart1, List[CollectionUpdateEntry]] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Set named fields on item `id`, as `name -> value` pairs in the collection's own field names. */
  def set(id: CollectionId, fields: List[(com.histmersion.plugin.SourceCollection.SetFieldsPart1, com.histmersion.plugin.SourceCollection.SetFieldsPart2)]): Either[com.histmersion.plugin.SourceCollection.SetResultPart1, Unit] =
    Left(s"$title does not rewrite its items: $readOnlyReason")

  /** Compare every linked item with the host's record of it; the items that disagree, in id order. */
  def audit(records: List[CollectionRecord]): Either[com.histmersion.plugin.SourceCollection.AuditResultPart1, List[CollectionFinding]] = Right(Nil)

object SourceCollection:
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescriptionResult = DescriptionResult.T
  object DescriptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadOnlyReasonResult = ReadOnlyReasonResult.T
  object ReadOnlyReasonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReachResultPart1 = ReachResultPart1.T
  object ReachResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResultPart1 = ItemResultPart1.T
  object ItemResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdmitsResultPart1 = AdmitsResultPart1.T
  object AdmitsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileFormat = FileFormat.T
  object FileFormat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalResultPart1 = OriginalResultPart1.T
  object OriginalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverResultPart1 = CoverResultPart1.T
  object CoverResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddResultPart1 = AddResultPart1.T
  object AddResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceResultPart1 = ReplaceResultPart1.T
  object ReplaceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceResultPart2 = ReplaceResultPart2.T
  object ReplaceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceKey = ReplaceKey.T
  object ReplaceKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateResultPart1 = UpdateResultPart1.T
  object UpdateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllResultPart1 = UpdateAllResultPart1.T
  object UpdateAllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetResultPart1 = SetResultPart1.T
  object SetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFieldsPart1 = SetFieldsPart1.T
  object SetFieldsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFieldsPart2 = SetFieldsPart2.T
  object SetFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuditResultPart1 = AuditResultPart1.T
  object AuditResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
