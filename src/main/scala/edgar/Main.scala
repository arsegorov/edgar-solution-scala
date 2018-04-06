package edgar

import java.io.File

import scala.util.{Failure, Success}

/**
  * The main class of the application.<br/>
  * Checks the arguments, input, and output, and then delegates the processing
  * to [[edgar.SessionReporter SessionReporter]].
  */
object Main extends IOSetup {
  
  val warn: String = "[" + Console.YELLOW + "warn" + Console.RESET + "]"
  val err: String = "[" + Console.RED + "error" + Console.RESET + "]"
  val info: String = "[" + Console.GREEN + "info" + Console.RESET + "]"
  
  /**
    * The app's entry point
    *
    * @param args the command line arguments
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
          println("Couldn't read a timeout value from\n" +
            s"\t$inactivityArg\n" +
            "Exiting\n")
        
        // If a timeout value has benn successfully read, continue
        case Success(timeout) =>
          
          // Trying to access the data in the log file
          accessData(logArg)
          match {
            // If data can't be read for some reason, print a message, and exit
            case Failure(_) =>
              println("Couldn't read data from\n" +
                s"\t$logArg\n" +
                "Exiting\n")
            
            // If the data can be read, continue
            // also saving the file handle so it can be closed after all else is done
            case Success((data, s)) =>
              val out = new File(outputArg)
              
              // Finally, trying to open the output file for writing
              setupOutput(out)
              match {
                // If failed to open the output file, print a message, and exit
                case Failure(_) =>
                  println("Unable to output the results to\n" +
                    s"\t$outputArg\n" +
                    "Exiting\n")
                
                // If succeeded, continue using the file handle
                case Success(bw) =>
                  println(s"$info Processing the data from ${Console.CYAN}$logArg${Console.RESET}\n" +
                    s"\tWriting output to ${Console.CYAN}$outputArg${Console.RESET}\n")
                  SessionReporter.processData(data, timeout * 1000, bw)
                  bw.close()
              }
              
              s.close()
          }
      }
    }
  }
  
}
