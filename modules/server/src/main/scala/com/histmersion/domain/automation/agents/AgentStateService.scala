package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

import com.histmersion.api.automation.AgentStateEntry
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure

/** The agents' scratch state: a JSON value under a key, by key. */
trait AgentStateService:
  def list(): List[AgentStateEntry]
  def get(key: AgentStateRecord.Key): Option[AgentStateEntry]

  /** Set a key's value; a value that is not JSON fails, as the database's cast to `jsonb` does. */
  def set(key: AgentStateRecord.Key, value: AgentStateService.SetValue): Unit

  /** Remove a key; false when there was none. */
  def delete(key: AgentStateRecord.Key): Boolean

object AgentStateService:
  type SetValue = SetValue.T
  object SetValue extends Newtype[String, Pure]:
    extension (v: SetValue)
      /** The value as JSON, or why it is not JSON. */
      def json: Either[String, Json] = RawJson.parse(v.value)

  def apply(db: Transactor): AgentStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends AgentStateService:
    def list(): List[AgentStateEntry] = db.read(AgentStateRepo.list())
    def get(key: AgentStateRecord.Key): Option[AgentStateEntry] =
      db.read(AgentStateRepo.get(AgentStateRepo.StateKey(key.value)))
    def set(key: AgentStateRecord.Key, value: SetValue): Unit =
      db.transact(AgentStateRepo.set(AgentStateRepo.StateKey(key.value), AgentStateRepo.SetValue(value.value)))
    def delete(key: AgentStateRecord.Key): Boolean =
      db.transact(AgentStateRepo.delete(AgentStateRepo.StateKey(key.value)))

  /** The values in memory, printed back as `jsonb::text` prints them (an object's keys shortest first, then in byte
    * order, the last of a repeated key kept; `", "` and `": "` between); `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends AgentStateService:
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    private def updatedText(at: Instant): AgentStateEntry.UpdatedAt =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      AgentStateEntry.UpdatedAt(s"${Updated.format(at)}$fraction+00")

    /** jsonb's order of an object's keys: the shorter first, then by their UTF-8 bytes (unsigned, as `memcmp`). */
    private def keyOrder(k: AgentStateRecord.Key): (Int, String) =
      val b = k.value.getBytes("UTF-8")
      (b.length, b.map(x => f"${x & 0xff}%02x").mkString)

    private def jsonb(j: Json): Json = j match
      case Json.Arr(a) => Json.fromValues(a.map(jsonb))
      case Json.Obj(o) =>
        Json.fromJsonObject(
          JsonObject.fromIterable(
            o.toList.sortBy((k, _) => keyOrder(AgentStateRecord.Key(k))).map((k, v) => k -> jsonb(v))
          )
        )
      case other => other

    /** `jsonb::text`: compact, with `", "` and `": "` between. */
    private def print(j: Json): String = j match
      case Json.Arr(a) => a.map(print).mkString("[", ", ", "]")
      case Json.Obj(o) =>
        o.fields.map((k, v) => s"${Json.fromString(k).noSpaces}: ${print(v)}").mkString("{", ", ", "}")
      case other => other.noSpaces

    private def entry(key: AgentStateRecord.Key, r: AgentStateRecord) = AgentStateEntry(
      AgentStateEntry.Key(key.value),
      AgentStateEntry.Value(r.value.value),
      Some(updatedText(r.updatedAt))
    )

    def list(): List[AgentStateEntry] = state.get.agentState.toList.sortBy(_._1.value).map(entry)
    def get(key: AgentStateRecord.Key): Option[AgentStateEntry] = state.get.agentState.get(key).map(entry(key, _))
    def set(key: AgentStateRecord.Key, value: SetValue): Unit =
      val j = value.json
        .fold(e => throw IllegalArgumentException(s"invalid input syntax for type json: $e"), identity)
      val at = Instant.now(clock)
      val _ =
        state.updateAndGet(
          _.modify(_.agentState)
            .using(_.updated(key, AgentStateRecord(AgentStateRecord.Value(print(jsonb(j))), at)))
        )
    def delete(key: AgentStateRecord.Key): Boolean =
      state.getAndUpdate(_.modify(_.agentState).using(_ - key)).agentState.contains(key)
