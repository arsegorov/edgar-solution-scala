package edgar

import java.io.{BufferedWriter, File, FileNotFoundException, FileWriter}
import java.sql.Timestamp

import scala.collection.mutable
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}



object Main {
  
  /*=======================*
     The main and IO setup
   *=======================*/
  
  def main(args: Array[String]): Unit = {
    if (args.length < 3) println("Please specify two input files and one output file")
    else {
      /* The strings with the paths to the input and output files:
       * (`log.csv`, `inactivity_period.txt`, `sessionization.txt`) */
      val (logArg, inactivityArg, outputArg) = (args(0), args(1), args(2))
  
      readTimeout(inactivityArg)
      match {
        case Failure(_) =>
          println(s"Couldn't read a timeout value from '$inactivityArg'")
    
        case Success(timeout) =>
          accessData(logArg)
          match {
            case Failure(_) =>
              println(s"Couldn't read the log from '$logArg'")

            case Success((data, s)) =>
              val out = new File(outputArg)
              
              setupOutput(out)
              match {
                case Failure(_) =>
                  println(s"Error: Unable to output the results to '$outputArg")
                  
                case Success(bw) =>
                  processData(data, timeout*1000, bw)
                  bw.close()
              }
              
              s.close()
          }
      }
    }
  }
  
  /**
    * Reads the timeout value from the specified file.
    *
    * @param file A string containing the file's path and name
    * @return The single integer value stored in the file
    */
  def readTimeout(file: String): Try[Int] = {
    Try(Source.fromFile(file)) match {
      case Failure(e: FileNotFoundException) =>
        println(s"Error: Can't find the file '$file'")
        Failure(e)

      case Failure(e) =>
        println(s"Error: {$e.getMessage}")
        Failure(e)

      case Success(s) =>
        val lines = s.getLines
  
        Try(lines.next.trim.toInt) match {
          case Failure(e: NumberFormatException) =>
            s.close
            println(s"Error: An unexpected number format. A single integer on line 1 is expected")
            Failure(e)
            
          case Failure(e: NoSuchElementException) =>
            s.close
            println(s"Error: The input file is empty. A single integer on line 1 is expected")
            Failure(e)
            
          case Failure(e) =>
            s.close
            println(s"Error: {$e.getMessage}")
            Failure(e)

          case Success(t) =>
            s.close
            Success(t)
        }
    }
  }
  
  /**
    * Opens the log file for reading and provides an iterator over the data lines.
    * Also returns the file resource so it can be closed later on.
    *
    * @param file A string containing the file's path and name
    * @return The iterator over the data lines and the file resource
    */
  def accessData(file: String): Try[(Iterator[String], BufferedSource)] = {
    Try(Source.fromFile(file)) match {
      case Failure(e: FileNotFoundException) =>
        println(s"Error: Can't find the file '$file'")
        Failure(e)
    
      case Failure(e) =>
        println(s"Error: {$e.getMessage}")
        Failure(e)
    
      case Success(s) =>
        Try(s.getLines.drop(1)) match {
          case Failure(e: NoSuchElementException) =>
            s.close
            println(s"Error: The input file is empty. The first line should contain the columns' headers")
            Failure(e)
        
          case Failure(e) =>
            s.close
            println(s"Error: {$e.getMessage}")
            Failure(e)
        
          case Success(dataLines) =>
            Success(dataLines, s)
        }
    }
  }
  
  /**
    * Deletes the old file, if any, and creates a new one.
    *
    * @param out The file object to prepare
    * @return [[scala.util.Success Success]]([[java.io.BufferedWriter BufferedWriter]]) if succeeds,<br/>
    *        [[scala.util.Failure Failure]]([[Exception]]) if fails
    */
  def setupOutput(out: File): Try[BufferedWriter] = {
    if (!out.exists) Try(out.createNewFile) match
    {
      case Failure(e) =>
        println(s"Error: Cannot create the output file '${out.getPath}'")
        return Failure(e)
  
      case Success(_) =>
        out.setWritable(true)
    }
    
    Try(new BufferedWriter(new FileWriter(out))) match
    {
      case Failure(e) =>
        println(s"Error: Cannot write to '${out.getPath}'")
        Failure(e)
        
      case Success(bw) =>
        Success(bw)
    }
  }
  
  
  
  /*=================================================*
     The Request class, String -> Request conversion
   *=================================================*/
  
  /**
    * Represents a single request from the log file.
    *
    * @param ip The IP address from which the request was made
    * @param timestamp The data and time of the request
    */
  private final case class Request (ip: String ,timestamp: Long)
  
  /**
    * Converts a line from the log file to a [[edgar.Main#Request Request]] instance.
    *
    * @param line A line from the log
    * @return A request instance described by the `line`
    */
  private def toRequest(line: String): Try[Request] = {
    // If we need the first `n` columns, set the split limit to `n + 1`, since there might be some trailing fields
    val fields = line.split(",", 4)
    
    if (fields.length < 3)
      Failure(
        new Exception("Error: The log line is incomplete:\n\t'" +
          line +
          "'\nExpecting at least 3 fields: IP, Date, Time")
      )
    else
      Try(Request(
        fields(0)
        ,Timestamp.valueOf(fields(1) + " " + fields(2)).getTime
//        // Add the 3 fields below if we'll need to be able to distinguish b/w different documents
//        ,(fields(4), fields(5), fields(6))
      )) match
      {
        case Failure(_: IllegalArgumentException) =>
          Failure(new Exception("Error: An improper Date/Time format in:\n\t'" +
            line +
            "'\nThe expected format for the Date: `YYYY-MM-DD`, for the Time: `hh-mm-ss`")
          )
        
        case Failure(e) =>
          Failure(e)
        
        case Success(r) =>
          Success(r)
      }
  }
  
  
  
  /*====================*
     Processing, output
   *====================*/
  
  /**
    * A helper object for ordering the captured sessions before output.
    *<br/>
    * Two sessions,
    * {{{ s1 = (ip1, idx1, tFirst1, tLast1, count1) }}}
    * and
    * {{{ s2 = (ip2, idx2, tFirst2, tLast2, count2) }}}
    * are first compared by their starting time, '''`tFirst`'''.
    * <br/>
    * If the two sessions have started at the same time, then we look at the order in which they appear in the log,
    * given by '''`idx`'''.
    */
  private final object ReportingOrder extends Ordering[(String, Int, Long, Long, Int)] {
    /**
      * Compares the tuples based first on the 3rd component, then on the 2nd component.
      *
      * @param s1 The first tuple to compare
      * @param s2 The second tuple to compare
      * @return ` 1`, if `s1 > s2`<br/>
      *         ` 0`, if `s1 == s2`<br/>
      *         `-1`, if `s1 < s2`
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
    * @param data The iterator over the log data lines (should not include the header line)
    * @param toutMil The session timeout, in milliseconds
    * @param bw The path and name of the output file
    */
  private def processData(data: Iterator[String], toutMil: Int, bw: BufferedWriter): Unit = {
    val requests = data.map(toRequest)
  
    /* A map containing the current sessions' information:
     * ip -> (index, firstRequestTime, lastRequestTime, requestCount) */
    val ipMap: mutable.HashMap[String, (Int, Long, Long, Int)] = mutable.HashMap()
  
    /* An ordered set for collecting the detected sessions */
    val outputDump: mutable.SortedSet[(String, Int, Long, Long, Int)] = mutable.SortedSet()(ReportingOrder)
  
    /* The time of the last request in the log */
    var endTime: Long = 0
  
    /* The current line's index, for ordering the output */
    var idx = 0
  
    while (requests.hasNext) {
      requests.next match {
        case Failure(e) =>
          println("Unexpected record format: " + e.getMessage)
      
        case Success(Request(ip, timestamp)) =>
          endTime = timestamp
          val cutoff = timestamp - toutMil
        
          ipMap.get(ip) match {
            case None =>
              ipMap.put(ip, (idx, timestamp, timestamp, 1))
          
            case Some((oldIdx, tFirst, tLast, count)) =>
              if (tLast >= cutoff)
                ipMap.update(ip, (oldIdx, tFirst, timestamp, count + 1))
              else {
                outputDump.add((ip, oldIdx, tFirst, tLast, count))
                ipMap.update(ip, (idx, timestamp, timestamp, 1))
              }
          }
        
          val timedOut =
            ipMap
              .keys
              .filter(k => ipMap(k)._3 < cutoff) // ie, tLast < cutoff time
          timedOut.foreach {
            ip =>
              val Some((oldIdx, tFirst, tLast, count)) = ipMap.remove(ip)
              outputDump.add((ip, oldIdx, tFirst, tLast, count))
          }
        
          idx += 1
      }
    
      outputDump.foreach {
        case (ip, _, tFirst, tLast, count) => outputSession(ip, tFirst, tLast, count, bw)
      }
      outputDump.clear
    }
  
    ipMap.foreach {
      case (ip, (oldIdx, tFirst, tLast, count)) =>
        outputDump.add((ip, oldIdx, tFirst, tLast, count))
    }
  
    outputDump.foreach {
      case (ip, _, tFirst, tLast, count) => outputSession(ip, tFirst, tLast, count, bw)
    }
  }
  
  /**
    * Outputs session data to a [[java.io.BufferedWriter BufferedWriter]].
    * @param ip The IP address from which the session has been initiated
    * @param tFirst The time of the first request in the session
    * @param tLast The time of the last request in the session
    * @param count The number of requests in the session
    * @param bw The [[java.io.BufferedWriter BufferedWriter]] to output to
    */
  private def outputSession(ip: String, tFirst: Long, tLast: Long, count: Int, bw: BufferedWriter): Unit = {
    // These strings are used instead of the default Timestamp.toString
    // because the default includes the decimal point with nanoseconds
    val t1String = new Timestamp(tFirst).toString.substring(0, 19)
    val t2String = new Timestamp(tLast).toString.substring(0, 19)
  
    bw.write(ip + "," + t1String + "," + t2String + "," + (tLast + 1000 - tFirst)/1000 + "," + count + "\n")
  }
}
