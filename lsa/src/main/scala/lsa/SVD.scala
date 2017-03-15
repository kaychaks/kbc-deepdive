/**
  * SVD - contains everything related to singular value decomposition of the tf-idf matrix
  */
package lsa

import cats.data.Kleisli
import cats.implicits.{catsSyntaxEither, catsSyntaxEitherObject}
import org.apache.spark.ml.linalg.{Vector => MLVector}
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Vectors, Vector => MLLibVector}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row}

object SVD {
  import lsa.Types._
  def svd: DataFrame => Kleisli[AppEffect, BoilerPlate, SVDOut] =
    df =>
      Kleisli { bp: BoilerPlate =>
        lazy val dataRdd: RDD[MLLibVector] =
          df.select("tfidfVec")
            .rdd
            .map { r: Row =>
              val vec = r.getAs[MLVector]("tfidfVec").toSparse
              Vectors.sparse(vec.size, vec.indices, vec.values)
            }

        for {
          cachedSVDRdd <- Either.catchNonFatal { dataRdd.cache() }
          svd <- Either.catchNonFatal {

            new RowMatrix(cachedSVDRdd)
              .computeSVD(bp.config.svd.numConcepts, computeU = true)

          }
        } yield SVDOut(svd.V, svd.U, svd.s)

    }

}
