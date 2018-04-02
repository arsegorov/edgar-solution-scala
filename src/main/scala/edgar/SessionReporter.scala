package edgar

import java.io.BufferedWriter
import java.sql.Timestamp

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
  private final object ReportingOrder extends Ordering[(String, Int, Long, Long, Int)] {
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
    // This map will contain the unexpired sessions' information, in the format:
    // ip -> (line index, first request time, last request time, requests count)
    val ipMap: mutable.HashMap[String, (Int, Long, Long, Int)] = mutable.HashMap()
    
    // An ordered set, for collecting and sorting the expired sessions before sending them to output
    val outputBatch: mutable.SortedSet[(String, Int, Long, Long, Int)] = mutable.SortedSet()(ReportingOrder)
    
    // The time of the latest of the seen requests
    var endingTime: Long = 0
  
    // Replacing the line iterator with a request iterator
    val requests = lines.map(toRequest)
    // The current line's index, for ordering the output
    // Setting to 2 as we skipped the first line already
    // Ideally, would be passed as a parameter to the function
    var idx = 2
    
    // Read the requests from the log one at a time
    while (requests.hasNext) {
      requests.next match {
        // If converting the current log line to a request fails,
        // print a message, and continue to the next log line
        case Failure(e) =>
          println(s"Unexpected format in line $idx\n" + e.getMessage + "\n")
        
        // If the line is well-formed, continue with the request data
        case Success(Request(ip, timestamp)) =>
          // Updating the time of the last request
          endingTime = timestamp
          
          val cutoffTime = timestamp - tOutMil               // The cutoff time for determining expired sessions
          
          ipMap.get(ip) match {                              // First, check, if the IP already has a session
            case None =>                                     // If no,
              ipMap.put(ip, (idx, timestamp, timestamp, 1))  // store a new session
            
            case Some((oldIdx, tFirst, tLast, count)) =>     // If yes, check if that session got extended
              
              if (tLast >= cutoffTime)                                    // If the last access time is past the cutoff
                ipMap.update(ip, (oldIdx, tFirst, timestamp, count + 1))  // extend the session to the current time.
                
              else {                                                      // Otherwise,
                outputBatch.add((ip, oldIdx, tFirst, tLast, count))       // report that session as expired,
                ipMap.update(ip, (idx, timestamp, timestamp, 1))          // and start a new one.
              }
          }
          
          // Collect the expired sessions' IPs
          val expiredIPs =
            ipMap
              .filter {  // picking the sessions whose last request was before the cutoff time
                case (_, (_, _, tLast, _)) => tLast < cutoffTime
              }
              .keys
          
          // Removing the expires sessions and queueing them for output
          expiredIPs.foreach {
            ip1 =>
              val Some((oldIdx, tFirst, tLast, count)) = ipMap.remove(ip1)
              outputBatch.add((ip1, oldIdx, tFirst, tLast, count))
          }
  
          // Outputting each expired session, and clearing the queue
          outputBatch.foreach {
            case (ip1, _, tFirst, tLast, count) => outputSession(ip1, tFirst, tLast, count, bw)
          }
          outputBatch.clear
          
          idx += 1
      }
    }
    
    // Reached the end og the log
    
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
