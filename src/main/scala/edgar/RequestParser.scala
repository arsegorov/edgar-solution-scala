package edgar

import java.sql.Timestamp

import scala.util.{Failure, Success, Try}

/**
  * Converts strings to instances of [[edgar.RequestParser.Request RequestParser.Request]].
  */
trait RequestParser {
  
  /**
    * Represents a single request from the log file.
    *
    * @param ip        The IP address from which the request was made
    * @param timestamp The data and time of the request
    * @note Two requests from the same IP at the same time are identical under this model.
    *       <br/>
    *       Since we don't use the contents of the requests to count them,
    *       it's fine, but if we were to count ''unique'' requests,
    *       we'd need to introduce another parameter.
    */
  case class Request(ip: String, timestamp: Long)
  
  /**
    * Converts a line from the log file to a [[edgar.RequestParser#Request Request]] instance.
    *
    * @param line a line from the log
    * @return an instance of [[edgar.RequestParser#Request Request]] described by the `line`
    */
  def toRequest(line: String): Try[Request] = {
    // If we need the first `n` columns, set the split limit to `n + 1`,
    // since there might be some trailing fields
    val fields = line.split(",", 4)
    val len = fields.length
    
    // If a line doesn't have enough fields, print a message,
    // print a detailed message, and exit
    if (len < 3)
      Failure(
        new Exception(
          s"\tThe line is incomplete. Found $len field${if (len > 1) "s" else ""}:\n" +
            fields.zip(1 to len).map {
              case (str, i) => s"$i. ${fieldValue(str)}"
            }.mkString("\t\t", "\n\t\t", "\n") +
            "\tExpecting at least 3 fields: IP, Date, Time")
      )
    else {
      // Trying to create a Request from the fields
      Try {
        val ip = fields(0).split("\\.")
        val ipFormatException =
          "\tAn improper IP format:\n" +
            s"\t\tIP: ${fieldValue(fields(0))}\n" +
            "\tExpecting 000.000.000.xxx, where 000 is in 0..255, xxx is a 3-character string"
        
        
        // Checking the IP format
        // The IP should have 4 octets
        if (ip.length != 4)
          return Failure(new Exception(ipFormatException))
        
        // The first 3 octets should be numbers, in the 0..255 range
        ip.take(3).foreach {
          str =>
            Try(str.toInt) match {
              case Failure(_: NumberFormatException) =>
                return Failure(new Exception(ipFormatException))
              
              case Failure(e) =>
                return Failure(e)
              
              case Success(n) =>
                if (n < 0 || n > 255)
                  return Failure(new Exception(ipFormatException))
            }
        }
        
        // The last octet should be a 3-character string
        if (ip(3).length != 3)
          return Failure(new Exception(ipFormatException))
        
        
        // If the IP format is valid, try forming the Timestamp, and the request
        Request(fields(0), Timestamp.valueOf(fields(1) + " " + fields(2)).getTime)
        // Add the 3 fields below if we'll need to be able to distinguish b/w different documents
        // , (fields(4), fields(5), fields(6))
      } match {
        // If the Date and Time fields don't comply with the specified format,
        // print a detailed message, and exit
        case Failure(_: IllegalArgumentException) =>
          Failure(
            new Exception(
              "\tAn improper Date/Time format in\n" +
                s"\t\tDate: ${fieldValue(fields(1))}\n" +
                s"\t\tTime: ${fieldValue(fields(2))}\n" +
                "\tExpecting `YYYY-MM-DD` for Date and `hh-mm-ss` for Time"))
        
        // If the Request cannot be created for another, unforeseen reason,
        // exit, passing the Exception upstream
        case Failure(e) =>
          Failure(e)
        
        // If succeeded, return the Success wrapper of the created Request
        case Success(r) =>
          Success(r)
      }
    }
  }
  
  private def fieldValue(s: String): String = if (s.trim == "") "<empty>" else s
}
