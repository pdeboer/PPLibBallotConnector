package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot

import java.io.File

import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.persistence.Permutation
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.report.Report
import com.typesafe.config.ConfigFactory
import org.junit.{Test, Assert}

import scala.io.Source

/**
  * Created by mattia on 16.09.15.
  */
class ReportTest {

	case class Result(snippet: String, yes1: String, no1: String, yes1Cleaned: String, no1Cleaned: String, yes2: String, no2: String, yes2Cleaned: String, no2Cleaned: String, feedback: String)

	//@Test
	//TODO please fix
	def testReportGeneration: Unit = {
		val dao = new DAOTest()
		val ballotPortalAdapter = new BallotPortalAdapter(new PortalAdapterTest(), dao, "http://www.andreas.ifi.uzh.ch:9000/")
		val alg = Algorithm250(dao, ballotPortalAdapter)

		val permutation1 = Permutation(1, "pdfFile1/Assumption/15:555", "METHOD_15:9898", getClass.getResource("/pngFile.png").getPath, getClass.getResource("/pdfFile.pdf").getPath, false, 0, 0, 0.0, 0.0, 10)
		val permutation2 = Permutation(2, "pdfFile1/Assumption/7:123", "METHOD_15:9898", getClass.getResource("/pngFile.png").getPath, getClass.getResource("/pdfFile.pdf").getPath, false, 0, 0, 0.0, 0.0, 10)
		val permutation3 = Permutation(3, "pdfFile1/Assumption/15:555", "AnotherMethod_1:123123", getClass.getResource("/pngFile.png").getPath, getClass.getResource("/pdfFile.pdf").getPath, false, 0, 0, 0.0, 0.0, 10)
		val permutation4 = Permutation(4, "pdfFileTest/Assumption/12:7878", "SpecialMethod_1:1", getClass.getResource("/pngFileTest.png").getPath, getClass.getResource("/pdfFileTest.pdf").getPath, false, 0, 0, 0.0, 0.0, 10)

		dao.createPermutation(permutation1)
		dao.createPermutation(permutation2)
		dao.createPermutation(permutation3)
		dao.createPermutation(permutation4)

		alg.executePermutation(permutation1)
		Assert.assertEquals(dao.getPermutationById(1).get.state, 1)
		Assert.assertEquals(dao.getPermutationById(1).get.excluded_step, 0)
		Assert.assertEquals(dao.getPermutationById(2).get.state, 1)
		Assert.assertEquals(dao.getPermutationById(2).get.excluded_step, 2)
		Assert.assertEquals(dao.getPermutationById(3).get.state, 1)
		Assert.assertEquals(dao.getPermutationById(3).get.excluded_step, 1)

		alg.executePermutation(permutation4)

		Assert.assertEquals(dao.allAnswers().size, 8)

		Report.writeCSVReport(dao)

		val config = ConfigFactory.load()
		val RESULT_CSV_FILENAME = config.getString("resultFilename")

		val result = new File(System.getProperty("user.dir"), RESULT_CSV_FILENAME)
		Assert.assertTrue(result.exists())

		val src = Source.fromFile(result)
		val parsedResults: List[Result] = src.getLines().drop(1).map(r => {
			val result = r.split(",")
			Result(result(0), result(1), result(2), result(3), result(4), result(5), result(6), result(7), result(8), result(9))
		}).toList

		Assert.assertEquals(4, parsedResults.head.yes1Cleaned.toInt)
		Assert.assertEquals(4, parsedResults.head.yes2Cleaned.toInt)
		Assert.assertEquals(0, parsedResults.head.no1Cleaned.toInt)
		Assert.assertEquals(0, parsedResults.head.no2Cleaned.toInt)
		Assert.assertEquals("test;test;test;test", parsedResults.head.feedback)

		Assert.assertEquals(4, parsedResults.last.yes1Cleaned.toInt)
		Assert.assertEquals(4, parsedResults.last.yes2Cleaned.toInt)
		Assert.assertEquals(0, parsedResults.last.no1Cleaned.toInt)
		Assert.assertEquals(0, parsedResults.last.no2Cleaned.toInt)
		Assert.assertEquals("test;test;test;test", parsedResults.last.feedback)

		Assert.assertEquals(dao.assets.size, 3)


		Report.writeCSVReportAllAnswers(dao)
		val resultDetails = new File(System.getProperty("user.dir"), RESULT_CSV_FILENAME.substring(0, RESULT_CSV_FILENAME.length - 4) + "_DETAILS.csv")
		Assert.assertTrue(resultDetails.exists())
	}

}
