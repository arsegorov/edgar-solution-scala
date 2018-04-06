package edgar

import java.sql.Timestamp

import scala.math.random

/**
  * An abstraction for sessions extracted from EDGAR log files.
  *
  * @param ip      the IP address from which the session is initiated
  * @param tFirst  the time of the first request in the session
  * @param timeout the time of the last request in the session
  * @param count   the total count of requests in the session
  */
case class Session(ip: String, tFirst: Long, timeout: Int, count: Int) {
  private var tL = tFirst
  
  /**
    * The list of times of all the requests made during this session, in increasing order
    */
  private val requests: List[Long] = tFirst ::
    (for {_ <- 1 until count} yield {
      tL += (random() * timeout).floor.toLong * 1000 // The time b/w requests shouldn't exceed the timeout
      tL
    }).toList
  
  /**
    * The pointer to currently processed request time
    */
  private var t = requests
  
  /**
    * @return the time of the currently processed request
    */
  def head: Long = t.head
  
  /**
    * @return `true` if there are still other requests later in this session<br/>
    *         `false` otherwise
    */
  def hasNext: Boolean = t.nonEmpty
  
  /**
    * Returns the time of the current request and moves to the next request.
    *
    * @return the time of the current request
    */
  def pop: Long = {
    val res = t.head
    t = t.tail
    res
  }
  
  /**
    * @return the form in which the session would be reported in the sessionization file
    */
  override def toString: String =
    Seq(
      ip,
      new Timestamp(tFirst).toString.substring(0, 19),
      new Timestamp(tL).toString.substring(0, 19),
      (tL - tFirst + 1000) / 1000,
      count
    ).mkString(",")
  
  /**
    * @return the time of the last request in this session
    */
  def tLast: Long = tL
}
