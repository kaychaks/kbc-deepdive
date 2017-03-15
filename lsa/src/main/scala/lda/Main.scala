package lda

import cats.data.ReaderT
import cats.implicits._
import lsa.Prelude._
import lsa.Types.{AppEffect, BoilerPlate, Config, StopWords}
import org.apache.spark.ml.clustering.{LDA, LDAModel}
import org.apache.spark.ml.feature.{
  CountVectorizer,
  CountVectorizerModel,
  RegexTokenizer,
  StopWordsRemover
}
import org.apache.spark.sql.DataFrame
import pureconfig.error.ConfigReaderFailures

import scala.collection.mutable

object Main extends App {

  def parseInput: ReaderT[AppEffect, BoilerPlate, DataFrame] =
    ReaderT { bp =>
      import bp.sess.implicits._
      Either.catchNonFatal {
        bp.sess.sparkContext
          .textFile(bp.config.adhocFilePath)
          .map(_.split("\t").toList match {
            case id :: text :: Nil => (text, id)
            case _                 => ("", "")
          })
          .filter(v => !v._1.isEmpty && !v._2.isEmpty)
          .zipWithIndex
          .map {
            case ((x, y), z) => (x, y, z)
          }
          .toDF("text", "id", "docId")
      }
    }

  def tokens: DataFrame => ReaderT[AppEffect, BoilerPlate, DataFrame] =
    df =>
      ReaderT { bp =>
        Either.catchNonFatal {
          new RegexTokenizer()
            .setGaps(false)
            .setPattern("\\p{L}+") // unicode regex for words
            .setMinTokenLength(4)
            .setInputCol("text")
            .setOutputCol("words")
            .transform(df)
        }
    }

  def filterStopwords: DataFrame => ReaderT[AppEffect, BoilerPlate, DataFrame] =
    df =>
      loadStopWords >>= { sw: StopWords =>
        ReaderT { bp =>
          Either.catchNonFatal {
            new StopWordsRemover()
              .setStopWords(sw.toArray)
              .setCaseSensitive(false)
              .setInputCol("words")
              .setOutputCol("filtered")
              .transform(df)
          }
        }
    }

  def cvModel
    : DataFrame => ReaderT[AppEffect, BoilerPlate, CountVectorizerModel] =
    df =>
      ReaderT { bp =>
        Either.catchNonFatal {
          new CountVectorizer()
            .setInputCol("filtered")
            .setOutputCol("features")
            .setVocabSize(bp.config.lda.vocabSize)
            .fit(df)
        }
    }

  def countVectors: DataFrame => CountVectorizerModel => ReaderT[AppEffect,
                                                                 BoilerPlate,
                                                                 DataFrame] = {
    df => cv =>
      ReaderT { bp =>
        Either.catchNonFatal {
          cv.transform(df)
            .select("docId", "id", "features")
            .cache()
        }
      }
  }

  def miniBatch: Long => ReaderT[AppEffect, BoilerPlate, Double] = { vecCount =>
    ReaderT { bp =>
      Right {
        2.0 / bp.config.lda.iterations + 1.0 / vecCount
      }
    }
  }

  def lda: Double => DataFrame => ReaderT[AppEffect, BoilerPlate, LDAModel] =
    mb =>
      vec =>
        ReaderT { bp =>
          Either.catchNonFatal {
            new LDA()
              .setOptimizer("online")
              .setSubsamplingRate(mb)
              .setK(bp.config.lda.topics)
              .setMaxIter(2)
              .fit(vec)
          }
    }

  def run
    : LDAModel => DataFrame => CountVectorizerModel => ReaderT[AppEffect,
                                                               BoilerPlate,
                                                               (DataFrame,
                                                                DataFrame)] = {
    lda => ds => cv =>
      ReaderT { bp =>
        import bp.sess.implicits._
        Either.catchNonFatal {
          val vocab = cv.vocabulary
          val topics = lda.describeTopics.rdd.map(
            r =>
              (r(0).asInstanceOf[Int],
               r(1)
                 .asInstanceOf[mutable.WrappedArray[Int]]
                 .map(vocab(_))
                 .mkString("|"),
               r(2).asInstanceOf[mutable.WrappedArray[Double]].mkString("|")))
          val transformed = lda
            .transform(ds)
            .rdd
            .map(
              t =>
                (t(1).asInstanceOf[String],
                 t(2)
                   .asInstanceOf[org.apache.spark.ml.linalg.Vector]
                   .toArray
                   .zipWithIndex
                   .map(di => (vocab(di._2), di._1))
                   .mkString("|"),
                 t(3)
                   .asInstanceOf[org.apache.spark.ml.linalg.Vector]
                   .toArray
                   .mkString("|"))
            )
          (topics.toDF("topics", "terms", "termWeights"),
           transformed.toDF("docId", "features", "topicDistribution"))
        }
      }
  }

  def prog: ReaderT[AppEffect, BoilerPlate, (DataFrame, DataFrame)] =
    for {
      input <- parseInput
      t <- tokens(input)
      fw <- filterStopwords(t)
      cv <- cvModel(fw)
      vec <- countVectors(fw)(cv)
      mb <- miniBatch(vec.count)
      l <- lda(mb)(vec)
      out <- run(l)(vec)(cv)
    } yield out

  pureconfig.loadConfig[Config] match {

    case Right(myConfig: Config) =>
      for {
        bs <- bootstrap(myConfig)
        out <- Either.catchNonFatal {
          for {
            o <- prog.run(bs)
            _ <- Either.catchNonFatal {
              o._1.write.mode("append").csv(bs.config.outputSavePath)
            }
            _ <- Either.catchNonFatal {
              o._2.write.mode("append").csv(bs.config.outputSavePath)
            }
          } yield ()
        }
      } yield
        out match {
          case Left(e)  => e.printStackTrace(); bs.sess.stop()
          case Right(_) => bs.sess.stop()
        }

    case Left(e: ConfigReaderFailures) =>
      println(e.toList.mkString("\n")); System.exit(1)
  }

}
