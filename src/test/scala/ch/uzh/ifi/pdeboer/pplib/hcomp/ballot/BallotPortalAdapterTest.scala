package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot

import java.util.UUID

import ch.uzh.ifi.pdeboer.pplib.hcomp._
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.dao.DAO
import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.persistence.{Answer, Question}
import ch.uzh.ifi.pdeboer.pplib.util.LazyLogger
import org.joda.time.DateTime
import org.junit.{Assert, Test}

import scala.collection.mutable

/**
 * Created by mattia on 07.07.15.
 */
class BallotPortalAdapterTest {

	@Test
	def testProcessQuery: Unit = {
		val b = new BallotPortalAdapter(new PortalAdapterTest(), new DAOTest(), "http://www.andreas.ifi.uzh.ch:9000/")
		val query = HTMLQuery(<div>
			<h1>test</h1> <form>
				<input type="submit" name="answer" value="yes"/>
			</form>
		</div>)
		val prop = new BallotProperties(Batch(), List(Asset(Array.empty[Byte], "application/pdf", "empty filename")), 1)

		val ans = b.processQuery(query, prop)

		Assert.assertEquals(ans.asInstanceOf[Option[HTMLQueryAnswer]].get.answers.get("answer").get, "yes")
	}

	@Test
	def testWithoutBallotProperties: Unit = {
		val daoTest = new DAOTest()
		val b = new BallotPortalAdapter(new PortalAdapterTest(), daoTest, "http://www.andreas.ifi.uzh.ch:9000/")
		val ans = b.processQuery(HTMLQuery(<div>
			<h1>test</h1> <form>
				<input type="submit" name="answer" value="yes"/>
			</form>
		</div>),
			new HCompQueryProperties())

		// Outputcode will be generated by the portal adapter and won't match the value sent by the PortalAdapterTest (fix value = 123)
    Assert.assertEquals(ans.asInstanceOf[Option[HTMLQueryAnswer]].get.answers.get("answer").get, "yes")

		Assert.assertEquals(daoTest.questions.size, 1)
		Assert.assertEquals(daoTest.batches.size, 1)
		Assert.assertEquals(daoTest.answers.size, 1)
	}

	@Test
	def testWithoutForm: Unit = {
		val b = new BallotPortalAdapter(new PortalAdapterTest(), new DAOTest(), "http://www.andreas.ifi.uzh.ch:9000/")
		val ans = b.processQuery(HTMLQuery(<h1>test</h1>), new BallotProperties(Batch(), List(Asset(Array.empty[Byte], "application/pdf", "empty filename")), 1))

		Assert.assertEquals(ans, None)
	}

	@Test
	def testDeepFormStructure: Unit = {
		val b = new BallotPortalAdapter(new PortalAdapterTest(), new DAOTest(), "http://www.andreas.ifi.uzh.ch:9000/")
		val ans = b.processQuery(HTMLQuery(<div>
			<h1>test</h1> <div>
				<form>
					<p>
						<input type="submit" name="answer" value="yes"/>
					</p>
				</form>
			</div>
		</div>), new BallotProperties(Batch(), List(Asset(Array.empty[Byte], "application/pdf", "empty filename")), 1))
		Assert.assertEquals(ans.asInstanceOf[Option[HTMLQueryAnswer]].get.answers.get("answer").get, "yes")
	}

	@Test
	def testWithInvalidInput: Unit = {
		val b = new BallotPortalAdapter(new PortalAdapterTest(), new DAOTest(), "http://www.andreas.ifi.uzh.ch:9000/")
		val ans = b.processQuery(HTMLQuery(<div>
			<h1>test</h1> <div>
				<form>
					<p>
						<input type="button" name="answer" value="yes"/>
					</p>
				</form>
			</div>
		</div>), new BallotProperties(Batch(), List(Asset(Array.empty[Byte], "application/pdf", "empty filename")), 1))
		Assert.assertEquals(ans, None)
	}

	@Test
	def testWithoutFormsInput: Unit = {
		val b = new BallotPortalAdapter(new PortalAdapterTest(), new DAOTest(), "http://www.andreas.ifi.uzh.ch:9000/")
		val ans = b.processQuery(HTMLQuery(<div>
			<h1>test</h1> <form></form>
		</div>), new BallotProperties(Batch(), List(Asset(Array.empty[Byte], "application/pdf", "empty filename")), 1))
		Assert.assertEquals(ans, None)
	}

}

class PortalAdapterTest() extends HCompPortalAdapter with AnswerRejection {
	override def processQuery(query: HCompQuery, properties: HCompQueryProperties): Option[HCompAnswer] = {
		Some(FreetextAnswer(FreetextQuery("<div>Awesome question</div>"), "123"))
	}

	override def getDefaultPortalKey: String = "test"

	override def cancelQuery(query: HCompQuery): Unit = false
}

class DAOTest extends DAO with LazyLogger {

  val assets = new mutable.HashMap[Long, Long]
	val batches = new mutable.HashMap[Long, String]
	val questions = new mutable.HashMap[Long, String]
	val answers = new mutable.HashMap[Long, String]

	override def createBatch(allowedAnswerPerTurker: Int, uuid: UUID): Long = {
		logger.debug("Adding new Batch: " + uuid)
		batches += ((batches.size + 1).toLong -> uuid.toString)
		batches.size.toLong
	}

	override def getAnswer(questionId: Long): List[String] = {
		answers.get(questionId).toList
	}

	override def getBatchIdByUUID(uuid: UUID): Option[Long] = {
		batches.foreach(b => {
			if (b._2.equals(uuid)) {
				logger.debug("Found batch by UUID: " + b._1)
				return Some(b._1)
			}
		})
		Some(-1)
	}

	override def getQuestionUUID(questionId: Long): Option[String] = {
		questions.get(questionId)
	}

	override def createQuestion(html: String, batchId: Long, uuid: UUID = UUID.randomUUID(), dateTime: DateTime = new DateTime(), hints: Long): Long = {
		questions += ((questions.size + 1).toLong -> UUID.randomUUID().toString)
		answers += ((questions.size).toLong -> "{\"answer\":\"yes\"}")
		questions.size.toLong
	}

  override def getAssetIdsByQuestionId(questionId: Long): List[Long] = {
    var res = List.empty[Long]
    assets.foreach(b => {
      if (b._2  == questionId) {
        logger.debug("Found assetId : " + b._1)
        res ::= b._1
      }
    })
    res
  }

  override def createAsset(binary: Array[Byte], contentType: String, questionId: Long, filename: String): Long = {
    assets += ((assets.size + 1).toLong -> questionId)
    assets.size.toLong
  }

  override def updateAnswer(answerId: Long, accepted: Boolean): Unit = {
    true
  }


  override def getAnswerIdByOutputCode(insertOutput: String): Option[Long] = {
    Some(answers.head._1)
  }

  override def getExpectedOutputCodeFromAnswerId(ansId: Long): Option[Long] = {
    Some(123)
  }

  override def getQuestionIdByUUID(uuid: String): Option[Long] = {
    Some(1)
  }

  override def countAllAnswers(): Int = ???

	override def allAnswers(): List[Answer] = ???

  override def countAllBatches(): Int = ???

  override def countAllQuestions(): Int = ???

  override def getAllQuestions: List[Question] = ???

  override def getAssetFileNameByQuestionId(qId: Long): Option[String] = ???
}
