/**
  * Programs - pipelines / processes / programs that need to be executed
  */
package lsa

import cats.data.Kleisli
import cats.implicits.catsStdInstancesForEither
import org.apache.spark.sql.Dataset

object Programs {

  import NLP._
  import Prelude._
  import SVD._
  import Stats._
  import TFIDF._
  import Types._  

  def runLSAFindTop[T <: DomainDataType]
    : Dataset[T] => Kleisli[AppEffect, BoilerPlate, TopTerms] = {
    ds =>
      for {
        sw <- loadStopWords
        lemmas <- lemmatisation(sw)(ds)
        tfidf <- tf_idf(lemmas)
        svd <- svd(tfidf.idf)
        top <- topTermsInTopConcepts(tfidf.termIds)(svd)
      } yield top
  }  

  def transcriptsProg: Kleisli[AppEffect, BoilerPlate, TopTerms] =
    for {
      top <- transcripts flatMap runLSAFindTop
      _ <- save(MultiArray(top))
    } yield top

  def faqsProg =
    for {      
      top <- faqs flatMap runLSAFindTop
      _ <- save(MultiArray(top))
    } yield top

  def adhocProg =
    for {
      top <- adhocContent flatMap runLSAFindTop
      _ <- save(MultiArray(top))
    } yield top

}
