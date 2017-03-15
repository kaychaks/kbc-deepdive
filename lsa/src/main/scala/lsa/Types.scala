/**
  * Types - major contracts that everyone should abide
  *
  * Ideally this should be part of Prelude but coz of Spark's
  * weird serialisation issues, it has to imported separately
  *
  * TODO:
  *   - try to make this part of Prelude
  */
package lsa

import org.apache.spark.SparkConf
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Matrix, Vector}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

object Types {

  type TermFreq = DataFrame
  type DocFreq = DataFrame
  type TermIDs = Array[String]
  type DocIDs = Array[String]
  type StopWords = Set[String]
  type AppError = Throwable
  type AppEffect[T] = Either[AppError, T]

  type TranscriptDataset = Dataset[Transcript]
  type FAQDataset = Dataset[FAQ]

  type LemmaDataset = Dataset[Lemma]

  type TermsWithScore = Array[(String, Double)]
  type TopTerms = Array[TermsWithScore]

  case class SVDConf(numConcepts: Int = 1000)
  case class TFIDFConf(vocabSize: Int = 5000)
  case class StatsConf(numConcepts: Int, numTerms: Int)
  case class LDAConf(topics: Int, iterations: Int, vocabSize: Int)
  case class Config(sparkConf: Option[SparkConf],
                    transcriptsFilePath: String,
                    faqsFilePath: String,
                    adhocFilePath: String,
                    stopwordsFilePath: String,
                    outputSavePath: String,
                    nlpAnnotators: List[String],
                    svd: SVDConf,
                    tfidf: TFIDFConf,
                    stats: StatsConf,
                    lda: LDAConf)

  case class BoilerPlate(config: Config, sess: SparkSession)

  sealed trait DomainDataType
  case class Transcript(id: String, text: String) extends DomainDataType
  case class FAQ(id: String, q: String, a: String) extends DomainDataType

  case class Lemma(id: String, l: List[String])

  case class TFIDFOut(tf: TermFreq,
                      idf: DocFreq,
                      termIds: TermIDs,
                      docIds: DocIDs)

  case class SVDOut(termMatrix: Matrix,
                    docMatrix: RowMatrix,
                    singularValues: Vector)

  sealed trait SaveTypes
  case class DF(v: DataFrame) extends SaveTypes
  case class MultiArray(v: Array[Array[(String, Double)]]) extends SaveTypes
  //  case class Rdd[T](v: RDD[T]) extends SaveTypes

}
