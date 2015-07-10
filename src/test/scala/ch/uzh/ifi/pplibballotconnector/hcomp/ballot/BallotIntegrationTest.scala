package ch.uzh.ifi.pplibballotconnector.hcomp.ballot

import java.util

import ch.uzh.ifi.pdeboer.pplib.hcomp._
import ch.uzh.ifi.pplibballotconnector.dao.BallotDAO
import ch.uzh.ifi.pplibballotconnector.persistence.DBSettings
import junit.framework.Assert
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.client.protocol.ClientContext
import org.apache.http.impl.client.{BasicCookieStore, DefaultHttpClient}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils
import org.junit.Test
import scala.xml.NodeSeq

/**
 * Created by mattia on 10.07.15.
 */
class BallotIntegrationTest {
  
  @Test
  def test(): Unit ={
    DBSettings.initialize()

    val dao = new BallotDAO()
    val countQuestions = dao.countAllQuestions()
    val countAnswers = dao.countAllAnswers()
    val countBatches = dao.countAllBatches()

    val decoratedPortalAdapter = new IntegrationPortalAdapter()
    val ballotPortalAdapter = new BallotPortalAdapter(decoratedPortalAdapter, dao, "http://localhost:9000/")

    val ballotHtmlPage: NodeSeq =
      <div>
        <p>
          Some text
        </p>
        <hr style="width:100%"/>
        <p>
          Test question ?
          <br />
        </p>
        <form>
          <input type="submit" name="answer" value="Yes" style="width:100px;height:50px;" />
          <input type="submit" name="answer" value="No" style="width:100px;height:50px;"/>
        </form>
      </div>

    val query = HTMLQuery(ballotHtmlPage)
    val properties = new BallotProperties(Batch(), 1)

    ballotPortalAdapter.processQuery(query, properties) match {
      case ans : Option[HTMLQueryAnswer] => {
        Assert.assertEquals(ans.get.answers.get("answer").get, "Yes")
        Assert.assertTrue(dao.countAllQuestions().get == countQuestions.get+1)
        Assert.assertTrue(dao.countAllAnswers().get == countAnswers.get+1)
        Assert.assertTrue(dao.countAllBatches().get == countBatches.get+1)
      }
    }

  }

}

class IntegrationPortalAdapter extends HCompPortalAdapter with AnswerRejection {
  override def processQuery(query: HCompQuery, properties: HCompQueryProperties): Option[HCompAnswer] = {

    val freeTextQuery = query match {
      case p: FreetextQuery => p
      case _ => FreetextQuery(query.question)
    }
    Some(FreetextAnswer(freeTextQuery, simulateClientRequestsOnFrontend(query)))
  }

  def simulateClientRequestsOnFrontend(query: HCompQuery): String = {
    val httpClient = new DefaultHttpClient()
    val cookieStore = new BasicCookieStore()
    val httpContext = new BasicHttpContext()
    httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore)

    val questionURL = query.question.substring(0, query.question.indexOf("<br>"))
    logger.debug("Question URL: " + questionURL)

    //1. login to initialize the session
    val loginParams = new util.ArrayList[NameValuePair]()
    loginParams.add(new BasicNameValuePair("TurkerID", "integrationTest"))
    performAndConsumePostRequest(httpClient, httpContext, "http://localhost:9000/login", loginParams)

    //2. get question
    val questionPage = performAndConsumeGetRequest(httpClient, httpContext, questionURL)

    //3. send answer
    val questionId = questionPage.substring(questionPage.indexOf("name=\\\"questionId\\\" value=\\\"") + 28, questionPage.indexOf("\\\">"))
    logger.debug("Question id: " + questionId)

    val answerParams = new util.ArrayList[NameValuePair]()
    answerParams.add(new BasicNameValuePair("questionId", questionId))
    answerParams.add(new BasicNameValuePair("answer", "Yes"))
    val codePage = performAndConsumePostRequest(httpClient, httpContext, "http://localhost:9000/storeAnswer", answerParams)

    httpClient.close()

    //4. get code
    codePage.substring(codePage.indexOf("<h1>") + 4, codePage.indexOf("</h1>"))
  }

  def performAndConsumePostRequest(httpClient: DefaultHttpClient, httpContext: BasicHttpContext, url: String, params: util.ArrayList[NameValuePair]): String = {
    val request = new HttpPost(url)
    request.setEntity(new UrlEncodedFormEntity(params))
    val response = httpClient.execute(request, httpContext)
    val content = EntityUtils.toString(response.getEntity)
    EntityUtils.consumeQuietly(response.getEntity)
    content
  }

  def performAndConsumeGetRequest(httpClient: DefaultHttpClient, httpContext: BasicHttpContext, url: String): String = {
    val request = new HttpGet(url)
    val response = httpClient.execute(request, httpContext).getEntity
    val content = EntityUtils.toString(response)
    EntityUtils.consumeQuietly(response)
    content
  }

  override def getDefaultPortalKey: String = "consolePortalAdapter"

  override def cancelQuery(query: HCompQuery): Unit = ???
}