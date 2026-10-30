package com.histmersion.config.edit

import org.ekrich.config.{Config, ConfigException, ConfigObject, ConfigOrigin, ConfigValue}
import org.ekrich.config.parser.ConfigDocumentFactory

import scala.annotation.tailrec
import scala.util.Try

/** The file after a set of changes: its new text, the keys that changed and the ones that already said so. */
final case class EditedText(text: String, changed: List[SettingKey], unchanged: List[SettingKey])

/** Where a value of the config file, as read with its includes, was written: in the file itself, or in a
  * file it includes. */
enum Written:
  case Nowhere
  case Here(value: ConfigValue)
  case Elsewhere(file: String)
  /** Behind a `${…}` substitution, `+=`, or an object merged with one. */
  case Substituted(at: SettingKey)

/** Changes to the config file's TEXT that keep everything else as it is: its comments, its order, its layout,
  * its includes and its unknown keys (sconfig's `ConfigDocument`, which edits the parsed text, not a
  * rendering of the tree). A setting written with a substitution or `+=`, or one that comes from an
  * included file, is refused — never rewritten. Setting a value the file already holds changes nothing.
  * Pure: `current` is the file as read with its includes, unresolved, and `here` tells its own origins. */
object ConfigTextEdit:

  def apply(text: String, current: Config, here: ConfigOrigin => Boolean, edits: List[SettingEdit]): Either[SettingError, EditedText] =
    @tailrec def loop(rest: List[SettingEdit], doc: org.ekrich.config.parser.ConfigDocument,
        changed: List[SettingKey], unchanged: List[SettingKey]): Either[SettingError, EditedText] =
      rest match
        case Nil => Right(EditedText(doc.render, changed.reverse, unchanged.reverse))
        case edit :: tail =>
          (written(current, edit.key, here), edit) match
            case (Written.Substituted(_), _)     => Left(SettingError.Substitution(edit.key))
            case (Written.Elsewhere(f), _)       => Left(SettingError.FromInclude(edit.key, f))
            case (Written.Here(v), SettingEdit.Set(k, want)) if SettingValue.fromConfig(v).exists(SettingValue.same(_, want)) =>
              loop(tail, doc, changed, k :: unchanged)
            case (Written.Nowhere, SettingEdit.Unset(k)) =>
              loop(tail, doc, changed, k :: unchanged)
            case (_, SettingEdit.Set(k, want)) =>
              val next = doc.withValueText(k.hoconPath, want.hocon)
              if dropsVerbatim(doc.render, next.render) then Left(SettingError.Substitution(k))
              else loop(tail, next, k :: changed, unchanged)
            case (_, SettingEdit.Unset(k)) =>
              val next = doc.withoutPath(k.hoconPath)
              if dropsVerbatim(doc.render, next.render) then Left(SettingError.Substitution(k))
              else loop(tail, next, k :: changed, unchanged)
    try
      val doc = ConfigDocumentFactory.parseString(text)
      // a key changed twice in one write: the last change wins, the first is not written at all
      val last = edits.reverse.distinctBy(_.key.value).reverse
      loop(last, doc, Nil, Nil)
    catch case e: ConfigException => Left(SettingError.Unreadable(e.getMessage))

  /** Did an edit take out a substitution or an include the file had (a key written twice, once with
    * `${…}`, its plain value being the one that counts)? Those are kept exactly, so such a change is refused. */
  private def dropsVerbatim(before: String, after: String): Boolean =
    count(after, "${") < count(before, "${") || count(after, "+=") < count(before, "+=") ||
      includes(after) < includes(before)

  private def count(text: String, what: String): Int =
    @tailrec def loop(from: Int, n: Int): Int = text.indexOf(what, from) match
      case -1 => n
      case i  => loop(i + what.length, n + 1)
    loop(0, 0)

  private def includes(text: String): Int = text.linesIterator.count(_.trim.startsWith("include"))

  /** Where the file (read with its includes, unresolved) writes `key`. */
  def written(current: Config, key: SettingKey, here: ConfigOrigin => Boolean): Written =
    @tailrec def down(obj: ConfigObject, path: List[String], seen: List[String]): Written =
      path match
        case Nil => Written.Nowhere
        case word :: rest =>
          val at = SettingKey.either((seen :+ word).mkString(".")).toOption
          Try(Option(obj.get(word))) match
            case scala.util.Failure(_)    => Written.Substituted(at.getOrElse(key))
            case scala.util.Success(None) => Written.Nowhere
            case scala.util.Success(Some(v)) =>
              if Try(v.valueType).isFailure then Written.Substituted(at.getOrElse(key))
              else if rest.isEmpty then (if here(v.origin) then Written.Here(v) else Written.Elsewhere(fileOf(v.origin)))
              else v match
                case o: ConfigObject if Try(o.keySet).isSuccess => down(o, rest, seen :+ word)
                case o: ConfigObject => Written.Substituted(at.getOrElse(key))
                // a single value where the block of `key` belongs: setting `key` replaces it
                case other => if here(other.origin) then Written.Nowhere else Written.Elsewhere(fileOf(other.origin))
    down(current.root, key.segments, Nil)

  private def fileOf(o: ConfigOrigin): String =
    Option(o.filename).orElse(Option(o.url).map(_.toString)).getOrElse(o.description)
