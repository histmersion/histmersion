package fixture

import fixture.Codec.read

object Codec:
  def read(s: String): Int = s.length

object Plain:
  val limit = 3
  def over(n: Int): Boolean = n > Plain.limit

object Console:
  val out = new StringBuilder
  def report(dir: String): Unit =
    val out = dir + "/out"
    Console.out.append(out)

object Planner:
  def replaced(as: List[Int], keep: Int => Boolean): List[Int] = as.filter(keep)
  def plan(as: List[Int]): List[Int] =
    def replaced(as: List[Int]): List[Int] = Planner.replaced(as, _ => true)
    replaced(as)

object Jsonb:
  def read(s: String): Option[Int] = s.toIntOption
  def parse(s: String): Option[Int] = Jsonb.read(s).orElse(Some(Codec.read(s)))

object Tools:
  def checkEdits(n: Int): String = n.toString
  def apply(): () => String =
    val checkEdits = () => Tools.checkEdits(1)
    checkEdits

abstract class Decoder:
  def decode(s: String): Int = 0

object Rows:
  def decode(s: String): Int = s.length
  def plain(s: String): Int = Rows.decode(s)
  val twice: Decoder = new Decoder:
    def both(s: String): Int = Rows.decode(s) + this.decode(s)
