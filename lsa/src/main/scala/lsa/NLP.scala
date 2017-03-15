/**
  * NLP - contains everything related to natural language processing of the input corpus
  */
package lsa

import java.util.Properties

import cats.Id
import cats.data.Kleisli
import cats.implicits.{
  catsStdInstancesForOption,
  catsSyntaxApplicativeId,
  catsSyntaxEitherObject
}
import edu.stanford.nlp.ling.CoreAnnotations.{
  LemmaAnnotation,
  SentencesAnnotation,
  TokensAnnotation
}
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import org.apache.spark.sql.Dataset

import scala.collection.JavaConverters._
import scala.language.postfixOps

object NLP {
  import lsa.Types._
  import lsa.Prelude._

  def prepareNLPPipeline: Kleisli[Id, Config, StanfordCoreNLP] =
    Kleisli { conf =>
      val props = new Properties()
      props.setProperty("annotators", conf.nlpAnnotators.mkString(","))
      new StanfordCoreNLP(props).pure[Id]
    }

  def annotate: String => Kleisli[Id, StanfordCoreNLP, Annotation] =
    text =>
      Kleisli { pipeline =>
        val a = new Annotation(text)
        pipeline.annotate(a)
        a.pure[Id]
    }

  def lemmas: Annotation => Kleisli[Id, Set[String], List[String]] =
    a =>
      Kleisli { stopwords =>
        val sentences = a.get(classOf[SentencesAnnotation]).asScala

        for {
          s <- sentences.toList
          t <- s.get(classOf[TokensAnnotation]).asScala.toList
          l = t.get(classOf[LemmaAnnotation])
          if (l.length > 2) &&
            !stopwords.contains(l) &&
            l.forall(Character.isLetter)
        } yield l.toLowerCase.pure[Id]
    }

  type LemmaContinuation = Kleisli[Id, Set[String], Lemma]
  def strToLemmas
    : String => String => Kleisli[Id, StanfordCoreNLP, LemmaContinuation] =
    id =>
      text =>
        (annotate(text) andThen lemmas) map { lsk =>
          for {
            ls <- lsk
          } yield Lemma(id, ls)
    }

  def transcripts: Kleisli[AppEffect, BoilerPlate, TranscriptDataset] =
    Kleisli { bp =>
      import bp.sess.implicits._

      Either.catchNonFatal[TranscriptDataset] {

        bp.sess.sparkContext
          .textFile(bp.config.transcriptsFilePath)
          .map {
            _ split "\t" toList match {
              case x :: _ :: _ :: _ :: xs => Transcript(x, xs.head)
              case x :: _ => Transcript(x, "")
              case _ => Transcript("", "")
            }
          }
          .filter {
            case Transcript("", _) => false
            case Transcript(_, "") => false
            case _ => true
          }
          .toDS

      }
    }

  def faqs: Kleisli[AppEffect, BoilerPlate, FAQDataset] =
    Kleisli { bp =>
      import bp.sess.implicits._

      Either.catchNonFatal[FAQDataset] {
        bp.sess.sparkContext
          .textFile(bp.config.faqsFilePath)
          .map {
            _ split "\t" toList match {
              case id :: q :: a :: Nil => Option(FAQ(id, q, a))
              case _ => None
            }
          }
          .filter {
            case None => false
            case _ => true
          }
          .map(_.get)
          .toDS
      }
    }

  def adhocContent: Kleisli[AppEffect, BoilerPlate, TranscriptDataset] =
    Kleisli {bp =>

      import bp.sess.implicits._

      Either.catchNonFatal[TranscriptDataset] {

        bp.sess.sparkContext
          .textFile(bp.config.adhocFilePath)
          .map {
            _ split "\t" toList match {
              case x :: Nil => Transcript(x, "")
              case x :: xs => Transcript(x, xs.head)
              case _ => Transcript("", "")
            }
          }
          .filter {
            case Transcript("", _) => false
            case Transcript(_, "") => false
            case _ => true
          }
          .toDS

      }
    }

  def lemmatisation[T <: DomainDataType]
    : Set[String] => Dataset[T] => Kleisli[AppEffect,
                                           BoilerPlate,
                                           LemmaDataset] =
    w =>
      ds =>
        Kleisli { bp =>
          import bp.sess.implicits._

          Either.catchNonFatal[LemmaDataset] {

            ds.mapPartitions {
              _ map { d =>
                val (id, text) = dataToProcess(d)
                val nlp = prepareNLPPipeline.run(bp.config)
                strToLemmas(id)(text)
                  .run(nlp)
                  .run(w)
              }
            }
          }
    }

}
