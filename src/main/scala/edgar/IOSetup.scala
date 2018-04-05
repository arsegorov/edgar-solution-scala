package edgar
import java.io.{BufferedWriter, File, FileNotFoundException, FileWriter}

import edgar.Main.err

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

/**
  * Provides functions for reading the timeout data and setting up the input and output.
  */
trait IOSetup {
  
  /**
    * Reads the timeout value from the specified file.
    *
    * @param file A string containing the file's path and name
    * @return The single integer value stored in the file
    */
  def readTimeout(file: String): Try[Int] = {
    // Trying to open the file for reading
    Try(Source.fromFile(file)) match {
      // If the file isn't there, print a message, and exit
      case Failure(e: FileNotFoundException) =>
        println(
          s"$err Can't find the file\n" +
            s"\t\t$file\n")
        Failure(e)

      // If an unforeseen exception occurs, print its message, and exit
      case Failure(e) =>
        println(s"$err ${e.getMessage}")
        Failure(e)

      // If the file opens successfully
      case Success(s) =>
        val lines = s.getLines
  
        // Parse the integer on the first line
        Try(lines.next.trim.toInt) match {
          // If the first line isn't a properly formatted integer, print a message, close the file, and exit
          case Failure(e: NumberFormatException) =>
            s.close
            println(s"$err An unexpected number format in\n" +
              s"\t\t$file\n" +
              "\tA single integer on line 1 is expected\n")
            Failure(e)

          // If the file doesn't have the first line (is empty), print a message, close the file, and exit
          case Failure(e: NoSuchElementException) =>
            s.close
            println(s"$err The file\n" +
              s"\t\t$file\n" +
              "is empty. A single integer on line 1 is expected\n")
            Failure(e)

          // If an unforeseen exception occurs, print its message, close the file, and exit
          case Failure(e) =>
            s.close
            println(s"$err ${e.getMessage}")
            Failure(e)

          // If the number was successfully parsed, close the file, and return its Success wrapper
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
    // Trying to open the file for reading
    Try(Source.fromFile(file)) match {
      // If the file isn't there, print a message, and exit
      case Failure(e: FileNotFoundException) =>
        println(
          s"$err Can't find the file\n" +
            s"\t\t$file\n")
        Failure(e)

      // If an unforeseen exception occurs, print its message, and exit
      case Failure(e) =>
        println(s"$err \t${e.getMessage}\n")
        Failure(e)

      // If the file opens successfully
      case Success(s) =>
        // Try skipping the first line (the headers)
        Try(s.getLines.drop(1)) match {
          // If the file doesn't have the first line (is empty), print a message, close the file, and exit
          case Failure(e: NoSuchElementException) =>
            s.close
            println(s"$err The file\n" +
              s"\t\t$file\n" +
              "\tis empty. Expecting column headers on line 1\n")
            Failure(e)

          // If an unforeseen exception occurs, print its message, and exit
          case Failure(e) =>
            s.close
            println(s"$err ${e.getMessage}")
            Failure(e)

          // If successfully skipped the headers, return the iterator over the remaining lines,
          // and the file handle to close it once finished
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
    *         [[scala.util.Failure Failure]]([[Exception]]) if fails
    */
  def setupOutput(out: File): Try[BufferedWriter] = {
    // Checking if the file already exists, and trying to create one if not
    if (!out.exists) {
      Try(out.createNewFile) match {
        case Failure(e) =>
          // If can't create the file, print a message, and exit
          println(s"$err Cannot create\n" +
            s"\t\t${out.getPath}\n")
          return Failure(e)

        // If successfully created the file, set its write permissions on.
        // This could also raise an exception, but if it does, creating the writer below will too,
        // and we'll just catch that one
        case Success(_) =>
          out.setWritable(true)
      }
    }
  
    // Trying to open the file for writing and to create the writer
    Try(new BufferedWriter(new FileWriter(out))) match {
      // If failed, print a message, and exit
      case Failure(e) =>
        println(s"$err Cannot write to\n" +
          s"\t\t${out.getPath}\n")
        Failure(e)

      // If succeeded, return the writer
      case Success(bw) =>
        Success(bw)
    }
  }
}
