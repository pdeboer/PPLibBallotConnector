package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.report


/**
 * Created by mattia on 01.09.15.
 */
case class SummarizedAnswersFormat(yesQ1: Int, noQ1: Int, yesQ2: Int, noQ2: Int)

object SummarizedAnswersFormat {
  def count(answers: List[ParsedAnswer]) : SummarizedAnswersFormat = {
    val yesQ1 = answers.count(ans => AnswerParser.isPositive(ans.q1).get)
    val yesQ2 = answers.count(ans => AnswerParser.isPositive(ans.q2).isDefined && AnswerParser.isPositive(ans.q2).get)
    val noQ1 = answers.count(ans => AnswerParser.isNegative(ans.q1).get)
    val noQ2 = answers.count(ans => AnswerParser.isNegative(ans.q2).isDefined && AnswerParser.isNegative(ans.q2).get)
    SummarizedAnswersFormat(yesQ1, noQ1, yesQ2, noQ2)
  }
}