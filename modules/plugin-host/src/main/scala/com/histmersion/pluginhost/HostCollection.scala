package com.histmersion
package pluginhost

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import com.histmersion.domain.Title
import io.github.iltotore.iron.constraint.any.Pure

/** A plugin's [[SourceCollection]] as the host calls it: every call guarded (an exception or a linkage error becomes
  * the operation's `Left`, naming the collection), and every write refused unless the collection declares the
  * capability it needs — whatever the plugin's own member would do.
  */
final class HostCollection(val name: HostCollection.Name, collection: SourceCollection):
  def title: Title = Title(guardedValue(collection.title.value, name.value))
  def description: HostCollection.Description =
    HostCollection.Description(guardedValue(collection.description.value, ""))
  def capabilities: Set[CollectionCapability] = guardedValue(collection.capabilities, Set(CollectionCapability.Read))

  def reach: Either[Problems, Unit] =
    try Option(collection.reach).getOrElse(Left(PluginMedia.answeredNothing))
    catch
      case e: LinkageError => Left(collectionThrew(e))
      case NonFatal(e)     => Left(collectionThrew(e))
  def search(query: CollectionQuery): Either[Problems, List[CollectionItem]] =
    guarded(collection.search(query))
  def item(id: CollectionId): Either[Problems, Option[CollectionItem]] =
    guarded(collection.item(id))
  def admits(item: CollectionItem): Either[Problems, Unit] = guarded(collection.admits(item))
  def file(
      item: CollectionItem,
      format: Option[SourceCollection.FileFormat]
  ): Either[Problems, CollectionFile] =
    guarded(collection.file(item, format))
  def original(item: CollectionItem, file: CollectionFile): Either[Problems, FetchedFile] =
    guarded(collection.original(item, file))
  def cover(item: CollectionItem): Either[Problems, Option[FetchedFile]] =
    guarded(collection.cover(item))
  def audit(records: List[CollectionRecord]): Either[Problems, List[CollectionFinding]] =
    guarded(collection.audit(records))

  def add(metadata: CollectionMetadata, file: FetchedFile): Either[Problems, CollectionId] =
    writing(CollectionCapability.Add)(collection.add(metadata, file))
  def replace(
      id: CollectionId,
      key: SourceCollection.ReplaceKey,
      file: FetchedFile
  ): Either[Problems, HostCollection.Id] =
    writing(CollectionCapability.Add)(collection.replace(id, key, file)).map(r => HostCollection.Id(r.value))
  def update(id: CollectionId, metadata: CollectionMetadata): Either[Problems, List[CollectionChange]] =
    writing(CollectionCapability.Update)(collection.update(id, metadata))
  def updateAll(
      links: List[CollectionLink],
      apply: SourceCollection.UpdateAllApply
  ): Either[Problems, List[CollectionUpdateEntry]] =
    if apply.value then writing(CollectionCapability.Update)(collection.updateAll(links, apply))
    else guarded(collection.updateAll(links, apply))
  def set(
      id: CollectionId,
      fields: List[(SourceCollection.FieldName, SourceCollection.FieldValue)]
  ): Either[Problems, Unit] =
    writing(CollectionCapability.Update)(collection.set(id, fields))

  /** Whether the collection declares `capability`. */
  def can(capability: CollectionCapability): Boolean = capabilities.contains(capability)

  /** The refusal of a write the collection does not declare: the collection's own reason, when it gives one. */
  def readOnly: Problem =
    guardedValue(collection.readOnlyReason, Problem.Refused.Policy(Problem.Refused.Rule.ReadOnly))

  private def writing[A](capability: CollectionCapability)(call: => Either[Problems, A]): Either[Problems, A] =
    if can(capability) then guarded(call) else Left(readOnly)

  private def guarded[A](call: => Either[Problems, A]): Either[Problems, A] =
    try Option(call).getOrElse(Left(PluginMedia.answeredNothing))
    catch
      case e: LinkageError => Left(collectionThrew(e))
      case NonFatal(e)     => Left(collectionThrew(e))

  /** The collection's call threw `e`: logged under its problem's id. */
  private def collectionThrew(e: Throwable): Problem =
    Problem.from(e, Problem.Where(s"source collection $name"), Problem.Internal.Id.mint())

  private def guardedValue[A](value: => A, otherwise: A): A =
    try Option(value).getOrElse(otherwise)
    catch
      case _: LinkageError => otherwise
      case NonFatal(_)     => otherwise

object HostCollection:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
