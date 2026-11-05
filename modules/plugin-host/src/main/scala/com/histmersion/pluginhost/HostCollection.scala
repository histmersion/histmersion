package com.histmersion.pluginhost

import com.histmersion.plugin.*

import scala.util.control.NonFatal

/** A plugin's [[SourceCollection]] as the host calls it: every call guarded (an exception or a linkage error becomes
  * the operation's `Left`, naming the collection), and every write refused unless the collection declares the
  * capability it needs — whatever the plugin's own member would do.
  */
final class HostCollection(val name: String, collection: SourceCollection):
  def title: com.histmersion.pluginhost.HostCollection.TitleResult = guardedValue(collection.title, name)
  def description: com.histmersion.pluginhost.HostCollection.DescriptionResult = guardedValue(collection.description, "")
  def capabilities: Set[CollectionCapability] = guardedValue(collection.capabilities, Set(CollectionCapability.Read))

  def reach: Either[com.histmersion.pluginhost.HostCollection.ReachResultPart1, Unit] = guarded(collection.reach)
  def search(query: CollectionQuery): Either[com.histmersion.pluginhost.HostCollection.SearchResultPart1, List[CollectionItem]] = guarded(collection.search(query))
  def item(id: CollectionId): Either[com.histmersion.pluginhost.HostCollection.ItemResultPart1, Option[CollectionItem]] = guarded(collection.item(id))
  def admits(item: CollectionItem): Either[com.histmersion.pluginhost.HostCollection.AdmitsResultPart1, Unit] = guarded(collection.admits(item))
  def file(item: CollectionItem, format: Option[com.histmersion.pluginhost.HostCollection.FileFormat]): Either[com.histmersion.pluginhost.HostCollection.FileResultPart1, CollectionFile] =
    guarded(collection.file(item, format))
  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.pluginhost.HostCollection.OriginalResultPart1, FetchedFile] =
    guarded(collection.original(item, file))
  def cover(item: CollectionItem): Either[com.histmersion.pluginhost.HostCollection.CoverResultPart1, Option[FetchedFile]] = guarded(collection.cover(item))
  def audit(records: List[CollectionRecord]): Either[com.histmersion.pluginhost.HostCollection.AuditResultPart1, List[CollectionFinding]] =
    guarded(collection.audit(records))

  def add(metadata: CollectionMetadata, file: FetchedFile): Either[com.histmersion.pluginhost.HostCollection.AddResultPart1, CollectionId] =
    writing(CollectionCapability.Add)(collection.add(metadata, file))
  def replace(id: CollectionId, key: com.histmersion.pluginhost.HostCollection.ReplaceKey, file: FetchedFile): Either[com.histmersion.pluginhost.HostCollection.ReplaceResultPart1, com.histmersion.pluginhost.HostCollection.ReplaceResultPart2] =
    writing(CollectionCapability.Add)(collection.replace(id, key, file))
  def update(id: CollectionId, metadata: CollectionMetadata): Either[com.histmersion.pluginhost.HostCollection.UpdateResultPart1, List[CollectionChange]] =
    writing(CollectionCapability.Update)(collection.update(id, metadata))
  def updateAll(links: List[CollectionLink], apply: com.histmersion.pluginhost.HostCollection.UpdateAllApply): Either[com.histmersion.pluginhost.HostCollection.UpdateAllResultPart1, List[CollectionUpdateEntry]] =
    if apply then writing(CollectionCapability.Update)(collection.updateAll(links, apply))
    else guarded(collection.updateAll(links, apply))
  def set(id: CollectionId, fields: List[(com.histmersion.pluginhost.HostCollection.SetFieldsPart1, com.histmersion.pluginhost.HostCollection.SetFieldsPart2)]): Either[com.histmersion.pluginhost.HostCollection.SetResultPart1, Unit] =
    writing(CollectionCapability.Update)(collection.set(id, fields))

  /** Whether the collection declares `capability`. */
  def can(capability: CollectionCapability): com.histmersion.pluginhost.HostCollection.CanResult = capabilities.contains(capability)

  /** The refusal of a write the collection does not declare. */
  def readOnly: com.histmersion.pluginhost.HostCollection.ReadOnlyResult = s"$title is read-only here: ${guardedValue(collection.readOnlyReason, "it is read-only")}"

  private def writing[A](capability: CollectionCapability)(call: => Either[String, A]): Either[com.histmersion.pluginhost.HostCollection.WritingResultPart1, A] =
    if can(capability) then guarded(call) else Left(readOnly)

  private def guarded[A](call: => Either[String, A]): Either[com.histmersion.pluginhost.HostCollection.GuardedResultPart1, A] =
    try
      call match
        case null => Left(s"source collection '$name' answered nothing")
        case r    => r
    catch
      case e: LinkageError => Left(s"source collection '$name' failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
      case NonFatal(e)     => Left(s"source collection '$name' failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  private def guardedValue[A](value: => A, otherwise: A): A =
    try Option(value).getOrElse(otherwise)
    catch
      case _: LinkageError => otherwise
      case NonFatal(_)     => otherwise

object HostCollection:
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescriptionResult = DescriptionResult.T
  object DescriptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type AuditResultPart1 = AuditResultPart1.T
  object AuditResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type CanResult = CanResult.T
  object CanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadOnlyResult = ReadOnlyResult.T
  object ReadOnlyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WritingResultPart1 = WritingResultPart1.T
  object WritingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedResultPart1 = GuardedResultPart1.T
  object GuardedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
