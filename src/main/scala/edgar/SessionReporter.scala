package edgar

import java.io.BufferedWriter
import java.sql.Timestamp

import edgar.Main.warn

import scala.collection.mutable
import scala.util.{Failure, Success}

object SessionReporter extends RequestParser {
  
  /**
    * A helper object for ordering the captured sessions before output.
    * <br/>
    * Given two sessions,
    * {{{ s1 = (ip1, idx1, tFirst1, tLast1, count1) }}}
    * and
    * {{{ s2 = (ip2, idx2, tFirst2, tLast2, count2) }}}
    * they are first compared by the starting time, '''`tFirst`'''.
    * <br/>
    * If the sessions started at the same time, then we use the order in which they appear in the log,
    * given by '''`idx`'''.
    */
  private final object AppearanceOrder extends Ordering[(String, Int, Long, Long, Int)] {
    /**
      * Compares two tuples first on their 3rd component, then on their 2nd component.
      *
      * @param s1 The first tuple
      * @param s2 The second tuple
      * @return `&nbsp;1`, if `s1 > s2`<br/>
      *         `&nbsp;0`, if `s1 == s2`<br/>
      *         `-1`,      if `s1 < s2`
      */
    def compare(s1: (String, Int, Long, Long, Int), s2: (String, Int, Long, Long, Int)): Int =
      if (s1._3 > s2._3) 1
      else if (s1._3 == s2._3)
             if (s1._2 > s2._2) 1 else if (s1._2 == s2._2) 0 else -1
      else -1
  }
  
  /**
    * Goes over the log file line by line to identify sessions,
    * and sends information about detected sessions to the output file.
    *
    * @param lines   The iterator over the log data lines (should not include the header line)
    * @param tOutMil The session timeout, in milliseconds
    * @param bw      The path and name of the output file
    */
  def processData(lines: Iterator[String], tOutMil: Int, bw: BufferedWriter): Unit =
  {
    // Session data
    // IP -> line index, first request time, last request time, request count
    val ipMap: mutable.HashMap[String, (Int, Long, Long, Int)] = mutable.HashMap()
    // A helper map
    // time -> the set of the IPs with the last request recorded at that time
    val timeMap: mutable.HashMap[Long, mutable.Set[String]] = mutable.HashMap()
    // the oldest last request time of a saved session
    var oldestTLast: Long = -1L
    
    // The output queue for expired sessions, sorting the sessions according to the first appearance
    val outputBatch: mutable.SortedSet[(String, Int, Long, Long, Int)] = mutable.SortedSet()(AppearanceOrder)
    
    // tracking the log line's number
    var idx = 2
    
    // The requests iterator
    // (the lines are read from the file one at a time, only when we're asking for the next request)
    val requests = lines.map(toRequest)
    
    while (requests.hasNext)
    {
      // Trying to read the next log line to get the request information
      requests.next match
      {
        // If the current log line is malformed,
        // print a message, and continue to the next log line
        case Failure(e) =>
          println(
            s"$warn Unexpected format on line $idx\n" +
            s"${e.getMessage}\n" +
            s"Skipping line $idx\n")
  
        // If the current line is well-formed, continue
        case Success(Request(ip, timestamp)) =>
          
          // The cutoff time for determining expired sessions
          val cutoffTime = timestamp - tOutMil
          // See if this one is a renewed session
          val renewed = ipMap.contains(ip) && ipMap(ip)._3 >= cutoffTime
          
          // If the session is renewed, update the last request time and the request count
          if (renewed) {
            val (firstIdx, tFirst, tLast, count) = ipMap(ip)
            ipMap.update(ip, (firstIdx, tFirst, timestamp, count + 1))
            
            // If the previous request was at a strictly earlier time,
            // remove the IP from the list for that time
            if (tLast < timestamp)
              timeMap(tLast).remove(ip)
          }
          
          // Now, see if any sessions have expired
          
          if (oldestTLast < cutoffTime) {                          // Only detect expired sessions when there are some
            val expiredTimes = timeMap.keys.filter(_ < cutoffTime) // All the expired times
  
            expiredTimes.foreach {
              expiredTime =>
                val Some(expiredIPs) = timeMap.remove(expiredTime) // Remove the expired times, one at a time
                expiredIPs.foreach {
                  expiredIP =>                                     // For each expired time,
                                                                   // queue the corresponding sessions for output
                    val Some((firstIdx, tFirst, tLast, count)) = ipMap.remove(expiredIP)
                    outputBatch.add((expiredIP, firstIdx, tFirst, tLast, count))
                }
            }
            
            oldestTLast =                                          // Update the time of the earliest saved session
              if (timeMap.nonEmpty) timeMap.keys.min
              else -1L
  
            outputBatch.foreach {                                  // Output the queued sessions
              case (ip1, _, tFirst, tLast, count) => outputSession(ip1, tFirst, tLast, count, bw)
            }
            outputBatch.clear                                      // Then clear the queue
          }
          
          // If a session for this IP hasn't been renewed
          // there's no record of this IP at this point, so we add a new record
          if (!renewed) ipMap.put(ip, (idx, timestamp, timestamp, 1))
  
          // Save this request's IP to the set of requests for the current timestamp
          if(timeMap.contains(timestamp)) timeMap(timestamp).add(ip) else timeMap.put(timestamp, mutable.Set(ip))
      }
      
      idx += 1
    }
  
    // Reached the end of the log
  
    // Queueing any remaining sessions for output
    ipMap.foreach {
      case (ip, (oldIdx, tFirst, tLast, count)) =>
        outputBatch.add((ip, oldIdx, tFirst, tLast, count))
    }
  
    // Outputting each expired session, and clearing the queue
    outputBatch.foreach {
      case (ip, _, tFirst, tLast, count) => outputSession(ip, tFirst, tLast, count, bw)
    }
  }
  
  /**
    * Outputs session data to a [[java.io.BufferedWriter BufferedWriter]].
    *
    * @param ip     The IP address from which the session has been initiated
    * @param tFirst The time of the first request in the session
    * @param tLast  The time of the last request in the session
    * @param count  The number of requests in the session
    * @param bw     The [[java.io.BufferedWriter BufferedWriter]] to output to
    */
  private def outputSession(ip: String, tFirst: Long, tLast: Long, count: Int, bw: BufferedWriter): Unit =
  {
    // Generating the Data/Time strings from the timestamps
    // These strings are used instead of the default Timestamp.toString
    // because the default includes the decimal point with nanoseconds
    val t1String = new Timestamp(tFirst).toString.substring(0, 19)
    val t2String = new Timestamp(tLast).toString.substring(0, 19)
    
    bw.write(
      ip + "," +
        t1String + "," +
        t2String + "," +
        (tLast + 1000 - tFirst) / 1000 + "," +
        count + "\n")
  }
}
