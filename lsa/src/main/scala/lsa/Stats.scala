/**
  * Stats - contains everything related to statistical analysis of the output data
  */
package lsa

import cats.data.Kleisli
import cats.implicits.catsSyntaxEitherObject

object Stats {

  import Types._

  type NumTerms = Int
  type NumConcepts = Int

  def topTermsInTopConcepts
    : TermIDs => SVDOut => Kleisli[AppEffect, BoilerPlate, TopTerms] =
    tIds =>
      svd =>
        Kleisli { bp =>
          // array being built here is in column major mode
          val svdResult: Array[Double] = svd.termMatrix.toArray

          def topTermInAConcept: Stream[Double] => TermsWithScore =
            _.zipWithIndex
              .sortBy(-_._1)
              .map {
                case (score, id) => (tIds(id), score)
              }
              .toArray

          Either.catchNonFatal {

            // parsing the column major mode array to extract scores of terms(rows) in a concept(column)
            // streams are used to make the whole process lazy

            svdResult.toStream
              .sliding(bp.config.stats.numTerms, bp.config.stats.numTerms)
              .toStream
              .map(topTermInAConcept)
              .take(bp.config.stats.numConcepts)
              .toArray

          }
    }

}
