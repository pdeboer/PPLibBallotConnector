package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.report

import java.io.File

import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.dao.BallotDAO
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.persistence.Answer
import com.github.tototoshi.csv.CSVWriter
import com.typesafe.config.ConfigFactory

/**
 * Created by mattia on 31.08.15.
 */
object Report {

	val config = ConfigFactory.load()
	val LIKERT_VALUE_CLEANED_ANSWERS = config.getInt("likertCleanedAnswers")

	def writeCSVReport(dao: BallotDAO) = {
		val reportWriter = ReportWriter
		reportWriter.init()

		dao.allAnswers.groupBy(g => {
			dao.getAssetFileNameByQuestionId(g.questionId).get
		}).map(answersForSnippet => {

			val permutationId = dao.getPermutationIdByQuestionId(answersForSnippet._2.head.questionId).get
			val allPermutationsDisabledByActualAnswer = dao.getAllPermutationsWithStateEquals(permutationId).filterNot(_.excluded_step == 0)
			val snippetName = answersForSnippet._1
			val allAnswerOfSnippet: List[Answer] = dao.getAllAnswersBySnippet(snippetName)

			val allAnswersParsed: List[ParsedAnswer] = AnswerParser.parseJSONAnswers(allAnswerOfSnippet)

			val overallSummary = SummarizedAnswersFormat.summarizeAnswers(allAnswersParsed)

			val cleanedAnswers = allAnswersParsed.filter(_.likert >= LIKERT_VALUE_CLEANED_ANSWERS)
			val cleanedSummary = SummarizedAnswersFormat.summarizeAnswers(cleanedAnswers)

			val feedback = allAnswersParsed.map(_.feedback).mkString(";")

			val firstExclusion = allPermutationsDisabledByActualAnswer.filter(_.excluded_step == 1).map(_.snippetFilename).mkString(";")
			val secondExclusion = allPermutationsDisabledByActualAnswer.filter(_.excluded_step == 2).map(_.snippetFilename).mkString(";")

			reportWriter.appendResult(snippetName, overallSummary, cleanedSummary, feedback, firstExclusion, secondExclusion)
		})

		reportWriter.close()
	}

}

object ReportWriter {
  val config = ConfigFactory.load()
  val RESULT_CSV_FILENAME = config.getString("resultFilename")

  val writer = CSVWriter.open(new File(RESULT_CSV_FILENAME))

  def init() = {
    writer.writeRow("snippet,yes answers,no answers,cleaned yes,cleaned no,yes answers,no answers,cleaned yes,cleaned no,feedbacks,firstExclusion,secondExclusion")
  }

  def appendResult(snippetName: String, overallSummary: SummarizedAnswersFormat, cleanedSummary: SummarizedAnswersFormat,
                   feedback: String, firstExcluded: String, secondExcluded: String) = {
    writer.writeRow(snippetName + "," + overallSummary.yesQ1 + "," + overallSummary.noQ1 + "," + cleanedSummary.yesQ1 + "," +
      cleanedSummary.noQ1 + "," + overallSummary.yesQ2 + "," + overallSummary.noQ2 + "," + cleanedSummary.yesQ2 + "," +
      cleanedSummary.noQ2 + "," + feedback + "," + firstExcluded + "," + secondExcluded)
  }

  def close() = {
    writer.close()
  }
}
