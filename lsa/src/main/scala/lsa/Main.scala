/**
  * Main - only entry point that run the programs
  */
package lsa

import cats.implicits.{catsSyntaxEither, catsSyntaxEitherObject}
import pureconfig.error.ConfigReaderFailures

import scala.language.postfixOps

object Main extends App {
  import Prelude._
  import Programs._
  import Types._

  pureconfig.loadConfig[Config] match {

    case Right(myConfig: Config) =>
      for {
        bs <- bootstrap(myConfig)
        out <- Either.catchNonFatal {
          adhocProg.run(bs)
        }
      } yield
        out match {
          case Left(e) => e.printStackTrace(); bs.sess.stop()
          case Right(_) => bs.sess.stop()
        }

    case Left(e: ConfigReaderFailures) =>
      println(e.toList.mkString("\n")); System.exit(1)
  }

}
