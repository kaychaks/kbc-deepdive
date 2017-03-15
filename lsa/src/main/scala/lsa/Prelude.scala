/**
  * Prelude: bootstrapping Main
  */
package lsa

import java.nio.file.{Files, Path, Paths}

import cats.Show
import cats.data.Kleisli
import cats.implicits.{catsSyntaxEitherObject, toShowOps}
import com.typesafe.config.{ConfigValue, ConfigValueType}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import pureconfig.{AllowMissingKey, ConfigReader}
import pureconfig.ConvertHelpers.catchReadError
import pureconfig.error.{ConfigReaderFailures, WrongType}

import scala.collection.JavaConverters._

object Prelude {
  import lsa.Types._

  implicit val sparkConfReader: pureconfig.ConfigReader[Option[SparkConf]] =
    new ConfigReader[Option[SparkConf]] with AllowMissingKey {
      override def from(config: ConfigValue)
        : Either[ConfigReaderFailures, Option[SparkConf]] = {
        config match {
          case null => Right(None)
          case _ if config.atKey("spark-conf").isEmpty => Right(None)
          case _ if config.valueType == ConfigValueType.OBJECT =>
            Right {
              val c = config.atKey("spark-conf")
              val master = c.getString("spark-conf.master")
              val appName = c.getString("spark-conf.app-name")
              Option(new SparkConf().setAppName(appName).setMaster(master))
            }
          case _ =>
            Left(
              ConfigReaderFailures(
                WrongType(config.valueType,
                          Set(ConfigValueType.OBJECT),
                          None,
                          None)))
        }
      }
    }

  implicit val filePathReader: pureconfig.ConfigReader[Path] =
    ConfigReader.fromString[Path](catchReadError(s => Paths.get(s)))

  implicit def tupleShow: Show[(String, Double)] =
    new Show[(String, Double)] {
      override def show(f: (String, Double)): String = s"${f._1}(${f._2})"
    }

  implicit def arrShow: Show[Array[(String, Double)]] =
    new Show[Array[(String, Double)]] {
      override def show(f: Array[(String, Double)]): String = f match {
        case Array() => ""
        case _ =>
          f.foldLeft("")((b, a) =>
            if (b.nonEmpty) s"$b, ${a.show}" else s"${a.show}")
      }
    }

  implicit def arrayShow: Show[Array[Array[(String, Double)]]] =
    new Show[Array[Array[(String, Double)]]] {
      override def show(f: Array[Array[(String, Double)]]): String = f match {
        case Array() => ""
        case _ =>
          f.foldLeft("")((b, a) =>
            if (b.nonEmpty) s"$b\n${a.show}" else s"${a.show}")
      }
    }

  def bootstrap: Config => AppEffect[BoilerPlate] =
    conf =>
      Either.catchNonFatal {
        val session = conf.sparkConf
          .map(c => SparkSession.builder.config(c).getOrCreate)
          .getOrElse(SparkSession.builder().getOrCreate())

        BoilerPlate(conf, session)
    }

  def loadStopWords: Kleisli[AppEffect, BoilerPlate, StopWords] =
    Kleisli { bp =>
      Either.catchNonFatal {
        val stopwords =
          Files
            .readAllLines(Paths.get(bp.config.stopwordsFilePath))
            .asScala
            .toSet
        bp.sess.sparkContext.broadcast(stopwords).value
      }
    }

  def saveDataframe: DataFrame => Kleisli[AppEffect, BoilerPlate, Unit] =
    df =>
      Kleisli { bp =>
        Either.catchNonFatal {
          df.write.mode("append").option("sep", "\t").csv(bp.config.outputSavePath.toString)
        }
    }

  def saveMultiArray: TopTerms => Kleisli[AppEffect, BoilerPlate, Unit] =
    m =>
      Kleisli { bp =>
        Either.catchNonFatal {
          Files.write(Paths.get(bp.config.outputSavePath), m.show.getBytes)
          ()
        }
    }

  def save: SaveTypes => Kleisli[AppEffect, BoilerPlate, Unit] = {
    case DF(d) => saveDataframe(d)
    case MultiArray(m) => saveMultiArray(m)
  }

  type ID = String
  type Text = String
  def dataToProcess: DomainDataType => (ID, Text) = {
    case Transcript(id, text) => (id, text)
    case FAQ(id, q, a) => (id, s"$q >>> $a")
  }
}
