package edgar

import java.io.File

import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.Checkers

import scala.io.Source
import scala.math.random

/**
  * A testing suite for the `edgar` application.
  */
@RunWith(classOf[JUnitRunner])
class EdgarSuite extends FunSuite with Checkers
{
  val poolSize = 50
  val ipPool = IPPool(poolSize)
  
  test("IP pool works") {
    assert(ipPool.size == 50, s"The IP pool contains ${ipPool.size} elements, not $poolSize")
  }
  
  test("The IPs in the pool are properly formatted") {
    check {
      val ip = ipPool.draw().split("\\.")
      ip.length == 4 && ip.take(3).map(_.toInt).forall(n => n >=0 && n < 256) && ip(3).length == 3
    }
  }
  
  test("Different items in the pool represent different IPs") {
    val gen = for {
      n <- Gen.choose(0, poolSize - 1)
      m <- Gen.choose(0, poolSize - 1)
    } yield (m, n)
    
    check {
      forAll(gen) {
        case (m, n) => (ipPool.get(m) equals ipPool.get(n)) == (m == n)
      }
    }
  }
  
  test("Session produces a sequence of different numbers") {
    val gen = for {
      t <- Gen.choose(0L, 100000000L)
      n <- Gen.choose(1, 20)
      m <- Gen.choose(1, 2000)
    } yield (t, m, n)
    
    check(
      forAll(gen) {
        case (t, m, n) =>
          (Session("", t, m, n).tLast == t) == (n == 1)
      }
    )
  }
  
  test("Session generator produces a sequence of correct length") {
    val gen = for {
      t <- Gen.choose(0L, 100000000L)
      m <- Gen.choose(1, 20)
    } yield (t, m)
    
    check(
      forAll(gen) {
        case (t, n) =>
          val session = Session("", t, 3, n)
          var count = 0
          
          while (session.hasNext) {
            count += 1
            session.pop
          }
          
          count == n
      }
    )
  }
  
  test("Zero-timeout files") {
    var success = true
    try {
      val logGenerator = LogGenerator(
        0,
        50,
        new File("test_log_0.csv"),
        new File("test_inactivity_0.txt"),
        random())
      
      logGenerator.writeInactivity()
      logGenerator.generateLog()
    }
    catch {
      case _: Throwable =>
        success = false
    }
    
    assert(success, "Couldn't generate the log file or the inactivity period file")
  }
  
  test("Large files") {
    var success = true
    try {
      val logGenerator = LogGenerator(
        1200,
        5000,
        new File("test_log_large.csv"),
        new File("test_inactivity_large.txt"),
        0)

      logGenerator.writeInactivity()
      logGenerator.generateLog()
    }
    catch {
      case _: Throwable =>
        success = false
    }

    assert(success, "Couldn't generate the log file or the inactivity period file")
  }

  test("The number of sessions is correct") {
    val gen = for {
      t <- Gen.choose(0, 300)
      n <- Gen.choose(0, 200)
    } yield (t, n)

    check(
      forAll(gen) {
        case (t, n) =>
          val logGenerator = LogGenerator(
            t,                  // Timeout
            n,                  // The number of sessions
            new File("test_log.csv"),
            new File("test_inactivity.txt"),
            random())

          logGenerator.writeInactivity()
          logGenerator.generateLog()

          Main.main(Array("test_log.csv", "test_inactivity.txt", "test_sessions.txt"))
          Source.fromFile("test_sessions.txt").getLines.length == n
      }
    )
  }
}
