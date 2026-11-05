package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.AgentStateEntry
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*
import io.circe.{Json, JsonObject}

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/** The agents' scratch state: a JSON value under a key, by key. */
trait AgentStateService:
  def list(): List[AgentStateEntry]
  def get(key: String): Option[AgentStateEntry]

  /** Set a key's value; a value that is not JSON fails, as the database's cast to `jsonb` does. */
  def set(key: String, value: String): Unit

  /** Remove a key; false when there was none. */
  def delete(key: String): Boolean

object AgentStateService:
  def apply(db: Transactor): AgentStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends AgentStateService:
    def list(): List[AgentStateEntry] = db.read(AgentStateRepo.list())
    def get(key: String): Option[AgentStateEntry] = db.read(AgentStateRepo.get(key))
    def set(key: String, value: String): Unit = db.transact(AgentStateRepo.set(key, value))
    def delete(key: String): Boolean = db.transact(AgentStateRepo.delete(key))

  /** The values in memory, printed back as `jsonb::text` prints them (an object's keys shortest first, then in byte
    * order, the last of a repeated key kept; `", "` and `": "` between); `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends AgentStateService:
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    private def updatedText(at: Instant): String =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      s"${Updated.format(at)}$fraction+00"

    /** jsonb's order of an object's keys: the shorter first, then by their UTF-8 bytes (unsigned, as `memcmp`). */
    private def keyOrder(k: String): (Int, String) =
      val b = k.getBytes("UTF-8")
      (b.length, b.map(x => f"${x & 0xff}%02x").mkString)

    private def jsonb(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(jsonb)),
        o =>
          Json.fromJsonObject(
            JsonObject.fromIterable(
              o.toList.sortBy((k, _) => keyOrder(k)).map((k, v) => k -> jsonb(v))
            )
          )
      )

    private val printer =
      io.circe.Printer.noSpaces.copy(colonRight = " ", objectCommaRight = " ", arrayCommaRight = " ")

    private def entry(key: String, r: AgentStateRecord) = AgentStateEntry(key, r.value, Some(updatedText(r.updatedAt)))

    def list(): List[AgentStateEntry] = state.get.agentState.toList.sortBy(_._1).map(entry)
    def get(key: String): Option[AgentStateEntry] = state.get.agentState.get(key).map(entry(key, _))
    def set(key: String, value: String): Unit =
      val j = io.circe.parser
        .parse(value)
        .fold(e => throw IllegalArgumentException(s"invalid input syntax for type json: $e"), identity)
      val at = Instant.now(clock)
      val _ =
        state.updateAndGet(_.modify(_.agentState).using(_.updated(key, AgentStateRecord(printer.print(jsonb(j)), at))))
    def delete(key: String): Boolean =
      state.getAndUpdate(_.modify(_.agentState).using(_ - key)).agentState.contains(key)
