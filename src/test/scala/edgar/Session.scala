package edgar

import java.sql.Timestamp

case class Session(ip: String, tFirst: Long, timeoutSeconds: Int, count: Int)
{
  private val timeout = timeoutSeconds*1000
  private var tLast = tFirst
  private val requests: List[Long] = tFirst ::
    (for {_ <- 1 until count} yield {
      tLast += (math.random()*timeout).floor.toLong
      tLast
    }).toList
  
  private var tail = requests
  
  def hasNext: Boolean = tail.nonEmpty
  
  def next: Long = {
    val res = tail.head
    tail = tail.tail
    res
  }
  
  override def toString: String =
    Seq(
      ip,
      new Timestamp(tFirst).toString.substring(0, 19),
      new Timestamp(tLast).toString.substring(0, 19),
      (tLast + 1000 - tFirst) / 1000,
      count
    ).mkString(",")
  
  def end: Long = tLast
}
