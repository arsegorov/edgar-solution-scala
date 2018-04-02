package edgar

import java.sql.Timestamp

import scala.util.{Failure, Success, Try}

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
  final case class Request(ip: String, timestamp: Long)
  
  /**
    * Converts a line from the log file to a [[edgar.RequestParser#Request Request]] instance.
    *
    * @param line A line from the log
    * @return A [[edgar.RequestParser#Request Request]] described by the `line`
    */
  def toRequest(line: String): Try[Request] = {
    // If we need the first `n` columns, set the split limit to `n + 1`,
    // since there might be some trailing fields
    val fields = line.split(",", 4)
    
    // If a line doesn't have enough fields, print a message,
    // print a detailed message, and exit
    if (fields.length < 3)
      Failure(
        new Exception("The line is incomplete. Found fields:\n\t" +
          fields.mkString("\n\t") +
          "\nExpecting at least 3 fields: IP, Date, Time")
      )
    else
    // Trying to create a Request from the fields
      Try(
        Request(fields(0), Timestamp.valueOf(fields(1) + " " + fields(2)).getTime
          // Add the 3 fields below if we'll need to be able to distinguish b/w different documents
          // , (fields(4), fields(5), fields(6))
        )
      ) match {
        // If the Date and Time fields don't comply with the specified format,
        // print a detailed message, and exit
        case Failure(_: IllegalArgumentException) =>
          Failure(
            new Exception("An improper Date/Time format in:\n\tDate: " +
              fields(1) + "\n\tTime: " + fields(2) +
              "\nExpecting `YYYY-MM-DD` for Date and `hh-mm-ss` for Time")
          )
        
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
