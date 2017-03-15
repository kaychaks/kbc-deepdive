/**
  * TFIDF - contains everything related to extracting term frequencies & inverse document freq from the NLP text
  */
package lsa

import cats.data.Kleisli
import cats.implicits.{catsSyntaxEither, catsSyntaxEitherObject}
import org.apache.spark.ml.feature.{
  CountVectorizer,
  CountVectorizerModel,
  IDF,
  IDFModel
}
import org.apache.spark.sql.DataFrame

object TFIDF {
  import lsa.Types._

  def tf: Int => LemmaDataset => AppEffect[(CountVectorizerModel, DataFrame)] =
    sz =>
      ds =>
        Either.catchNonFatal {

          val df = ds.toDF("id", "text")
          val model: CountVectorizerModel =
            new CountVectorizer()
              .setInputCol("text")
              .setOutputCol("termFreq")
              .setVocabSize(sz)
              .setMinDF(2)
              .fit(df)

          (model, model.transform(df))

    }

  def idf: DataFrame => AppEffect[(IDFModel, DataFrame)] =
    df =>
      Either.catchNonFatal {

        val model: IDFModel =
          new IDF()
            .setInputCol("termFreq")
            .setOutputCol("tfidfVec")
            .fit(df)

        (model, model.transform(df))

    }

  def tf_idf: LemmaDataset => Kleisli[AppEffect, BoilerPlate, TFIDFOut] =
    d =>
      Kleisli { bp =>
        import bp.sess.implicits._

        for {
          tfsE <- tf(bp.config.tfidf.vocabSize)(d)
          (tModel, tfs) = tfsE
          cachedTfs <- Either.catchNonFatal { tfs.cache } //caching as it will be used later
          idfsE <- idf(cachedTfs)
          (_, idfs) = idfsE
          termIds = tModel.vocabulary
          docIds = tfs.select("id").as[String].collect()
        } yield TFIDFOut(tfs, idfs, termIds, docIds)
    }

}
