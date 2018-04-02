package edgar

import java.io.File

import scala.util.{Failure, Success}


object Main extends IOSetup {
  
  /**
    * The app's entry point
    *
    * @param args The command line arguments
    */
  def main(args: Array[String]): Unit = {
    if (args.length < 3) println("Please specify two input files and one output file")
    else {
      /* The strings with the paths to the input and output files:
       * (`log.csv`, `inactivity_period.txt`, `sessionization.txt`) */
      val (logArg, inactivityArg, outputArg) = (args(0), args(1), args(2))
      
      // Trying to read the timeout value
      readTimeout(inactivityArg)
      match {
        // If a timeout value can't be read, print a message, then exit
        case Failure(_) =>
          println(s"Couldn't read a timeout value from\t\n$inactivityArg")
        
        // If a timeout value has benn successfully read, continue
        case Success(timeout) =>
          
          // Trying to access the data in the log file
          accessData(logArg)
          match {
            // If data can't be read for some reason, print a message, and exit
            case Failure(_) =>
              println(s"Couldn't read data from\n\t$logArg")
            
            // If the data can be read, continue
            // also saving the file handle so it can be closed after all else is done
            case Success((data, s)) =>
              val out = new File(outputArg)
              
              // Finally, trying to open the output file for writing
              setupOutput(out)
              match {
                // If failed to open the output file, print a message, and exit
                case Failure(_) =>
                  println(s"Error: Unable to output the results to\n\t$outputArg")
                
                // If succeeded, continue using the file handle
                case Success(bw) =>
                  SessionReporter.processData(data, timeout * 1000, bw)
                  bw.close()
              }
              
              s.close()
          }
      }
    }
  }
  
}
