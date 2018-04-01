package edgar

import java.io.FileNotFoundException
import java.sql.Timestamp

import scala.collection.mutable
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

object Main {
  
  def main(args: Array[String]): Unit = {
    if (args.length < 3) println("Please specify two input files and one output file")
    else {
      /** The strings with the paths to the input and output files:
        * (log.csv, inactivity_period.txt, sessionization.txt) */
      val (logArg, inactivityArg, outputArg) = (args(0), args(1), args(2))
  
      readTimeout(inactivityArg)
      match {
        case Failure(_) =>
          println(s"Couldn't read a timeout value from '$inactivityArg'")
    
        case Success(timeout) =>
          accessData(logArg)
          match {
            case Failure(_) =>
              println(s"Couldn't read from '$logArg'")

            case Success((data, s)) =>
              processData(data, timeout*1000, outputArg, s)
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
  private def readTimeout(file: String): Try[Int] = {
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
  private def accessData(file: String): Try[(Iterator[String], BufferedSource)] = {
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
    * Converts a line from the log file to a [[edgar.Main#Request Request]] instance.
    *
    * @param line A line from the log
    * @return A request instance described by the `line`
    */
  private def toRequest(line: String): Try[Request] = {
    val fields = line.split(",", 8)
    
    if (fields.length < 7)
      Failure(
        new Exception("Error: The log line is incomplete:\n\t'" +
          line +
          "'\nExpected at least 7 fields: IP, Date, Time, Zone, CIK, accession, extension")
      )
    else
      Try(Request(
        fields(0),
        Timestamp.valueOf(fields(1) + " " + fields(2)).getTime,
        (fields(4), fields(5), fields(6))
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
  
  /**
    * A helper object for sorting the captured sessions before output.
    *
    * Two sessions,
    * {{{ s1 = (ip1, idx1, tFirst1, tLast1, count1) }}}
    * and
    * {{{ s2 = (ip2, idx2, tFirst2, tLast2, count2) }}}
    * are first compared by their starting time, '''`tFirst1`''' vs. '''`tFirst2`'''.
    * {{{}}}
    * If the two sessions have started at the same time, then we look at the order in which they appear in the log,
    * '''`idx1`''' vs. '''`idx2`'''.
    */
  final private object ReportingOrder extends Ordering[(String, Int, Long, Long, Int)] {
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
    * @param oFile The path and name of the output file
    * @param s The log file resource, to be closed at the end
    */
  private def processData(data: Iterator[String], toutMil: Int, oFile: String, s: BufferedSource): Unit = {
    val requests = data.map(toRequest)
    
    /** A map containing the current sessions' information:
      * ip -> (index, firstRequestTime, lastRequestTime, requestCount) */
    val ipMap: mutable.HashMap[String, (Int, Long, Long, Int)] = mutable.HashMap()
    
    /** An ordered set for collecting the detected sessions */
    val dump: mutable.SortedSet[(String, Int, Long, Long, Int)] = mutable.SortedSet()(ReportingOrder)
    
    /** The time of the last request in the log */
    var endTime: Long = 0
    
    /** The current line's number, used for ordering the output */
    var idx = 0
    
    while (requests.hasNext) {
      requests.next match
      {
        case Failure(e) =>
          println("Unexpected record format: " + e.getMessage)
        
        case Success(Request(ip, timestamp, _)) =>
          endTime = timestamp
          val cutoff = timestamp - toutMil
  
          ipMap.get(ip) match
          {
            case None =>
              ipMap.put(ip, (idx, timestamp, timestamp, 1))
    
            case Some((oldIdx, tFirst, tLast, count)) =>
              if (tLast >= cutoff)
                ipMap.update(ip, (oldIdx, tFirst, timestamp, count + 1))
              else {
                dump.add((ip, oldIdx, tFirst, tLast, count))
                ipMap.update(ip, (idx, timestamp, timestamp, 1))
              }
          }
          
          val timedOut =
            ipMap
              .keys
              .filter(k => ipMap(k)._3 < cutoff)  // ie, tLast < cutoff time
          timedOut.foreach {
            ip =>
              val Some((oldIdx, tFirst, tLast, count)) = ipMap.remove(ip)
              dump.add((ip, oldIdx, tFirst, tLast, count))
          }
          
          idx += 1
      }
      
      dump.foreach {
        case (ip, _, tFirst, tLast, count) => output(ip, tFirst, tLast, count, oFile)
      }
      dump.clear
    }
  
    ipMap.foreach {
      case (ip, (oldIdx, tFirst, tLast, count)) =>
        dump.add((ip, oldIdx, tFirst, tLast, count))
    }
  
    dump.foreach {
      case (ip, _, tFirst, tLast, count) => output(ip, tFirst, tLast, count, oFile)
    }
    
    s.close
  }
  
  private def output(ip: String, tFirst: Long, tLast: Long, count: Int, oFile: String): Unit = {
    val t1String = new Timestamp(tFirst).toString.substring(0, 19)
    val t2String = new Timestamp(tLast).toString.substring(0, 19)
  
    println(ip + "," + t1String + "," + t2String + "," + (tLast + 1000 - tFirst)/1000 + "," + count)
  }
  
  /**
    * Represents a single request from the log file.
    *
    * @param ip The IP address from which the request was made
    * @param timestamp The data amd time of the request
    * @param docID The key of the requested document
    */
  final private case class Request (ip: String,
                      timestamp: Long,
                      docID: (String, String, String))
}
