package edgar

import java.io.{BufferedWriter, File, FileWriter}
import java.sql.Timestamp

import scala.collection.mutable
import scala.math.random

/**
  * A generator of mock EDGAR logs.
  *
  * @param timeout      the timeout to use for the session detection
  * @param numSessions  the number of sessions to generate and put in the log
  * @param log          the log file
  * @param inactivity   the file containing the inactivity period value
  * @param pctMalformed the rough percentage of lines in the log to be malformed
  */
case class LogGenerator(timeout: Int, numSessions: Int, log: File, inactivity: File, pctMalformed: Double) {
  /**
    * Writes the inactivity period value into the corresponding file.
    */
  def writeInactivity(): Unit = {
    if (!inactivity.exists) inactivity.createNewFile()
    
    val bw = new BufferedWriter(new FileWriter(inactivity))
    bw.write(s"$timeout\n")
    bw.close()
  }
  
  /**
    * Generates mock sessions, orders the requests from the sessions,
    * and outputs them into the corresponding file.
    */
  def generateLog(): Unit = {
    val ipPool = IPPool(30)
    
    // The unique IPs in the log, with their respective `tLast`s
    val ipTLast = mutable.HashMap[String, Long]()
    
    // Generating random sessions
    val sessions =
      for {
        _ <- 0 until numSessions
      } yield {
        val nextIP = ipPool.draw()
        
        // The starting time for the next session
        val nextTFirst =
          (if (ipTLast.contains(nextIP)) // If the IP already had a session
             ipTLast(nextIP) + (timeout + 1) * 1000 // measure `timeout + 1` seconds from the end of the previous session
          
           else // Otherwise,
             31622400000L * 47 // Use the base time, about 47 years since 1970
            ) + (random() * timeout).floor.toLong * 1000 // (plus a random time up to timeout value, for a thorough mix)
        
        // The new session for the next IP
        val session =
          Session(
            nextIP,
            nextTFirst,
            timeout,
            (random() * 25).floor.toInt + 1 // a random count in 1..25
          )
        if (ipTLast.contains(nextIP))
          ipTLast.update(nextIP, session.tLast)
        else ipTLast.put(nextIP, session.tLast)
        
        session
      }
    
    // Sorting the sessions according to their starting times
    var sortedSessions = sessions.sortBy {
      case Session(_, tFirst, _, _) => tFirst
    }
    
    // Writing the log to a file
    if (!log.exists) log.createNewFile()
    val logWriter = new BufferedWriter(new FileWriter(log))
    
    logWriter.write("\n") // The header line, which will be skipped anyway
    
    while (sortedSessions.nonEmpty) // while any sessions remain
    {
      if (random() < pctMalformed / 100) { // occasionally, insert a malformed line
        val kind = random()
        if (kind < 0.33)
          logWriter.write(",x??\n")
        else if (kind < 0.67)
               logWriter.write("4.123.45.dd,2017-11-01,14:32:54,\n")
        else
          logWriter.write("4.123.45.did,207-11-01,14:32:54,\n")
      }
      
      val nextTime = sortedSessions.map(_.head).min // find the time of the earliest of the remaining requests
    val timestamp = new Timestamp(nextTime) // and split it into date and time
    val Array(date, time) =
      timestamp
        .toString
        .split("\\.")(0) // Stripping the nanoseconds
        .split(" ") // Splitting into date and time
      
      val Some(session) = sortedSessions.find(_.head == nextTime) // the first session that has a request at that time
    val Session(ip, _, _, _) = session // and its IP
      
      logWriter.write(s"$ip,$date,$time,\n") // writing the request's data to the log
      
      session.pop // moving the session to its next request
      sortedSessions = sortedSessions.filter(_.hasNext) // removing any completed sessions
    }
    
    logWriter.close()
  }
}
