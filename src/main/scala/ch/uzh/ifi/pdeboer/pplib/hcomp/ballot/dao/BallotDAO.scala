package ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.dao

import java.util.UUID

import ch.uzh.ifi.pdeboer.pplib.hcomp.ballot.persistence.{Answer, Permutation, Question}
import org.joda.time.DateTime
import scalikejdbc._

/**
 * Created by mattia on 06.07.15.
 */
class BallotDAO extends DAO{

  override def countAllAnswers() : Int = {
    DB readOnly { implicit session =>
      sql"select count(*) as count from answer".map(rs => rs.int("count")).single().apply().get
    }
  }

  override def countAllBatches() : Int = {
    DB readOnly { implicit session =>
      sql"select count(*) as count from batch".map(rs => rs.int("count")).single().apply().get
    }
  }

  override def countAllQuestions() : Int = {
    DB readOnly { implicit session =>
      sql"select count(*) as count from question".map(rs => rs.int("count")).single().apply().get
    }
  }

  override def createBatch(allowedAnswersPerTurker: Int, uuid: UUID): Long = {
    DB localTx { implicit session =>
      sql"insert into batch(allowed_answers_per_turker, uuid) values(${allowedAnswersPerTurker}, ${uuid.toString})".updateAndReturnGeneratedKey().apply()
    }
  }

  override def getAnswer(questionId: Long): Option[String] = {
    DB readOnly { implicit session =>
      sql"select answer_json from answer where question_id = ${questionId}".map(rs => rs.string("answer_json")).single.apply()
    }
  }

  override def getAnswerIdByOutputCode(insertOutput: String): Option[Long] = {
    DB readOnly { implicit session =>
      sql"select id from answer where expected_output_code = ${insertOutput}".map(rs => rs.long("id")).single().apply()
    }
  }

  override def getExpectedOutputCodeFromAnswerId(ansId: Long) : Option[Long] = {
    DB readOnly { implicit session =>
      sql"select expected_output_code from answer where id = ${ansId}".map(rs => rs.long("expected_output_code")).single().apply()
    }
  }

  override def createQuestion(html: String, batchId: Long, uuid: UUID = UUID.randomUUID(), dateTime: DateTime = new DateTime(), permutationId: Long): Long = {
    DB localTx { implicit session =>
      sql"insert into question(batch_id, html, create_time, uuid, permutation) values(${batchId}, ${html}, ${dateTime}, ${uuid.toString}, ${permutationId})".updateAndReturnGeneratedKey().apply()
    }
  }

  override def getQuestionIdByUUID(uuid: String) : Option[Long] = {
    DB readOnly { implicit session =>
      sql"select id from question where uuid = ${uuid}".map(rs => rs.long("id")).single().apply()
    }
  }

  override def getQuestionUUID(questionId: Long): Option[String] = {
    DB readOnly { implicit session =>
      sql"select uuid from question where id = ${questionId}".map(rs => rs.string("uuid")).single().apply()
    }
  }

  override def getBatchIdByUUID(uuid: UUID): Option[Long] = {
    DB readOnly { implicit session =>
      sql"select id from batch where uuid = ${uuid.toString}".map(rs => rs.long("id")).single().apply()
    }
  }

  override def createAsset(binary: Array[Byte], contentType: String, questionId: Long, filename: String): Long = {
    DB localTx { implicit session =>
      sql"insert into assets(byte_array, content_type, question_id, filename) values(${binary}, ${contentType}, ${questionId}, ${filename})"
        .updateAndReturnGeneratedKey().apply()
    }
  }

  override def updateAnswer(answerId: Long, accepted: Boolean) = {
    DB localTx { implicit session =>
      sql"update answer SET accepted = ${accepted} WHERE id = ${answerId}"
        .update().apply()
    }
  }

  override def getAssetIdsByQuestionId(questionId: Long): List[Long] = {
    DB readOnly { implicit session =>
      sql"select * from assets where question_id = ${questionId}".map(rs => rs.long("id")).list().apply()
    }
  }

  def loadPermutationsCSV(csv: String) : Boolean = {
    DB localTx { implicit session =>

      sql"SET FOREIGN_KEY_CHECKS = 0".update().apply()
      sql"truncate table permutations".update().apply()
      sql"SET FOREIGN_KEY_CHECKS = 1".update().apply()

      sql"""LOAD DATA LOCAL INFILE ${csv}
      INTO TABLE permutations
      COLUMNS TERMINATED BY ','
      OPTIONALLY ENCLOSED BY '"'
      ESCAPED BY '"'
      LINES TERMINATED BY '\n'
      IGNORE 1 LINES
        (group_name, method_index, snippet_filename, pdf_path, method_on_top ,relative_height_top, relative_height_bottom)""".update().apply()
    }

    true
  }

  def createPermutation(permutation: Permutation) : Long = {
    DB localTx { implicit session =>
      sql"""insert into permutations(group_name, method_index, snippet_filename, pdf_path, method_on_top, relative_height_top, relative_height_bottom)
      values (group_name = ${permutation.groupName}, method_index = ${permutation.methodIndex},
      snippet_filename = ${permutation.snippetFilename}, pdf_path = ${permutation.pdfPath}, method_on_top = ${permutation.methodOnTop},
      relative_height_top = ${permutation.relativeHeightTop}, relative_height_bottom = ${permutation.relativeHeightBottom})"""
        .updateAndReturnGeneratedKey().apply()
    }
  }

  def getAllPermutations() : List[Permutation] = {
    DB readOnly { implicit session =>
      sql"select * from permutations".map(rs =>
        Permutation(rs.long("id"), rs.string("group_name"), rs.string("method_index"), rs.string("snippet_filename"), rs.string("pdf_path"), rs.boolean("method_on_top"), rs.long("state"), rs.int("excluded_step"), rs.double("relative_height_top"), rs.double("relative_height_bottom"))
      ).list().apply()
    }
  }

  def getPermutationById(id: Long) : Option[Permutation] = {
    DB readOnly { implicit session =>
      sql"select * from permutations where id = ${id}".map(rs =>
        Permutation(rs.long("id"), rs.string("group_name"), rs.string("method_index"), rs.string("snippet_filename"), rs.string("pdf_path"), rs.boolean("method_on_top"), rs.long("state"), rs.int("excluded_step"), rs.double("relative_height_top"), rs.double("relative_height_bottom"))
      ).single().apply()
    }
  }

  def getAllOpenByGroupName(groupName: String) : List[Permutation] = {
    DB readOnly { implicit session =>
      sql"select * from permutations where group_name = ${groupName} and state = 0".map(rs =>
        Permutation(rs.long("id"), rs.string("group_name"), rs.string("method_index"), rs.string("snippet_filename"), rs.string("pdf_path"), rs.boolean("method_on_top"), rs.long("state"), rs.int("excluded_step"), rs.double("relative_height_top"), rs.double("relative_height_bottom"))
      ).list().apply()
    }
  }

  def updateStateOfPermutationId(id: Long, becauseOfId: Long, excludedByStep: Int = 0) {
    DB localTx { implicit session =>
      sql"update permutations SET state = ${becauseOfId}, excluded_step = ${excludedByStep} WHERE id = ${id}"
        .update().apply()
    }
  }

  def getAllOpenGroupsStartingWith(partialGroupName: String) : List[Permutation] = {
    val result : List[Permutation] = getAllPermutationsWithStateEquals(0)
    result.filter(r => r.groupName.startsWith(partialGroupName)).map(m => m)
  }

  override def getAllQuestions : List[Question] = {
    DB readOnly { implicit session =>
      sql"select * from question".map(rs => Question(rs.long("id"), rs.long("permutation"))).list().apply()
    }
  }

  override def getAssetFileNameByQuestionId(qId: Long) : Option[String] = {
    DB readOnly { implicit session =>
      sql"select filename from assets where question_id = ${qId}".map(rs => rs.string("filename")).single().apply()
    }
  }

  def getAllPermutationsWithStateEquals(state: Long): List[Permutation] = {
    DB readOnly { implicit session =>
      sql"select * from permutations where state = ${state}".map(rs =>
        Permutation(rs.long("id"), rs.string("group_name"), rs.string("method_index"), rs.string("snippet_filename"), rs.string("pdf_path"), rs.boolean("method_on_top"), rs.long("state"), rs.int("excluded_step"), rs.double("relative_height_top"), rs.double("relative_height_bottom"))      ).list().apply()
    }
  }

  override def allAnswers: List[Answer] = {
    DB readOnly { implicit session =>
      sql"select * from answer where accepted = 1".map(rs =>
        Answer(rs.long("id"), rs.long("question_id"), rs.string("answer_json"), rs.boolean("accepted"))
      ).list().apply()
    }
  }

  def getPermutationIdByQuestionId(qId: Long) : Option[Long] = {
    DB readOnly { implicit session =>
      sql"select permutation from question where id = ${qId}".map(rs =>
        rs.long("permutation")).single().apply()
    }
  }

  def getAllAnswersBySnippet(fileName: String) : List[Answer] = {
    allAnswers.filter(f => f.answerJson.contains(fileName))
  }

}
