package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot

import java.io.{File, FileInputStream, InputStream}
import javax.activation.MimetypesFileTypeMap

import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.dao.BallotDAO
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.persistence.Permutation
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.report.{ParsedAnswer, SummarizedAnswersFormat}
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.snippet.SnippetHTMLQueryBuilder
import ch.uzh.ifi.pdeboer.pplib.hcomp.{HCompPortalAdapter, HCompQueryProperties, HTMLQueryAnswer}
import ch.uzh.ifi.pdeboer.pplib.process.entities.IndexedPatch
import ch.uzh.ifi.pdeboer.pplib.process.stdlib.ContestWithBeatByKVotingProcess
import com.typesafe.config.ConfigFactory

import scala.xml.NodeSeq


/**
 * Created by mattia on 01.09.15.
 */
case class Algorithm250(dao: BallotDAO, ballotPortalAdapter: HCompPortalAdapter) {

  val config = ConfigFactory.load()

  val LIKERT_VALUE_CLEANED_ANSWERS = config.getInt("likertCleanedAnswers")

  def executePermutationWith250(p: Permutation) = {
    val answers: List[ParsedAnswer] = questionBuilder(new File(p.pdfPath), new File(p.snippetFilename), p.id)

    if (shouldOtherSnippetsBeDisabled(answers)) {
      dao.updateStateOfPermutationId(p.id, p.id)
      dao.getAllOpenByGroupName(p.groupName).foreach(g => {
        dao.updateStateOfPermutationId(g.id, p.id, 1)
      })
      val groupName = p.groupName.split("/")
      dao.getAllOpenGroupsStartingWith(groupName.slice(0, 2).mkString("/")).filter(_.methodIndex == p.methodIndex).foreach(g => {
        dao.updateStateOfPermutationId(g.id, p.id, 2)
      })
    } else {
      dao.updateStateOfPermutationId(p.id, -1)
    }
  }

  def questionBuilder(pdfFile: File, snippetFile: File, permutationId: Long): List[ParsedAnswer] = {

    val base64Image = Utils.getBase64String(snippetFile)
    val permutation = dao.getPermutationById(permutationId).get
    val ballotHtmlPage: NodeSeq = HTMLTemplate.createPage(base64Image, permutation.methodOnTop, config.getString("hcomp.ballot.baseURL"), permutation.relativeHeightTop, permutation.relativeHeightBottom)
    val pdfInputStream: InputStream = new FileInputStream(pdfFile)
    val pdfBinary = Stream.continually(pdfInputStream.read).takeWhile(-1 !=).map(_.toByte).toArray

    val contentType = new MimetypesFileTypeMap().getContentType(pdfFile.getName)

    val properties = new BallotProperties(Batch(allowedAnswersPerTurker = 1),
      List(Asset(pdfBinary, contentType, pdfFile.getName)), permutationId, propertiesForDecoratedPortal = new HCompQueryProperties(50, qualifications = Nil))

    import ContestWithBeatByKVotingProcess._
    import ch.uzh.ifi.pdeboer.pplib.process.entities.DefaultParameters._
    val process = new ContestWithBeatByKVotingProcess(Map(
      K.key -> 2,
      PORTAL_PARAMETER.key -> ballotPortalAdapter,
      MAX_ITERATIONS.key -> 20,
      QUESTION_PRICE.key -> properties,
      QUERY_BUILDER_KEY -> new SnippetHTMLQueryBuilder(ballotHtmlPage)
    ))

    process.process(IndexedPatch.from(List(SnippetHTMLQueryBuilder.POSITIVE, SnippetHTMLQueryBuilder.NEGATIVE)))

    ballotPortalAdapter.queries.map(_.answer.get.is[HTMLQueryAnswer]).map(a => {
      ParsedAnswer(a.answers.get("isRelated"), a.answers.get("isCheckedBefore"), a.answers.get("confidence").get.toInt, a.answers.get("descriptionIsRelated").get)
    })
  }

  def shouldOtherSnippetsBeDisabled(answers: List[ParsedAnswer]): Boolean = {
    val cleanedAnswers = answers.filter(_.likert >= LIKERT_VALUE_CLEANED_ANSWERS)
    val summary = SummarizedAnswersFormat.count(cleanedAnswers)
    summary.yesQ1 > summary.noQ1 && summary.yesQ2 > summary.noQ2
  }

}
