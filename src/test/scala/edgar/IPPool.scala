package edgar

import scala.math.random

/**
  * A pool of mock IP addresses of the form<br/>
  * `000.000.000.xxx`,<br/>
  * where<br/>
  * `000` is in `0..255`, and<br/>
  * `xxx` is a 3-character string.
  *
  * @param poolSize the number of IPs in the pool
  */
case class IPPool(poolSize: Int) {
  /**
    * A sequence of size `poolSize` of unique random IPs
    */
  private val ips = {
    var suffixes = Set[String]()
    
    /**
      * Generates a string of 3 lower case latin letters.
      *
      * @return the generated string
      */
    def threeLowerLetters: String = (
      for {
        _ <- 0 until 3
      } yield
        ((random() * 26).floor.toInt + 97).asInstanceOf[Char]
      ).mkString
    
    // Generate unique 3-letter octets
    for (_ <- 0 until poolSize) {
      var suffix = threeLowerLetters
      
      while (suffixes(suffix)) suffix = threeLowerLetters
      
      suffixes = suffixes + suffix
    }
    
    // Prepend each octet with 3 integers in the range 0..255
    suffixes.map {
      (for (_ <- 0 until 3) yield (random() * 256).floor.toInt).mkString(".") + "." + _
    }.toIndexedSeq
  }
  
  /**
    * Drawing an IP from the pool at random.
    *
    * @return the drawn IP
    */
  def draw(): String = ips((random() * poolSize).floor.toInt)
  
  /**
    * @return the size of the pool
    */
  def size: Int = ips.size
  
  /**
    * Checks whether the pool is empty.
    *
    * @return `true` if the pool is empty<br/>
    *         `false` otherwise
    */
  def isEmpty: Boolean = ips.isEmpty
  
  /**
    * Gets the IP at a specific position in the pool.
    *
    * @param idx the IP's position in the pool
    * @return the IP at position `idx`, if `idx` is between 0 and `poolSize-1`<br/>
    *         `null` otherwise
    */
  def get(idx: Int): String = if (idx < 0 || idx >= poolSize) null else ips(idx)
}
