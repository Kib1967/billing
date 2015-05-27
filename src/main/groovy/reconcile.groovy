import groovy.json.JsonSlurper
import groovy.xml.*
import groovyx.net.http.*
import java.util.regex.Pattern
import javax.xml.transform.*
import javax.xml.transform.stream.*

warnings = []

def config = new ConfigSlurper().parse(new File(args[0]).toURL())

// First we have to authenticate with JIRA
def jiraLoginCookie = doJiraLogin(config.jira.rootUrl, config.jira.user, config.jira.pwd)
if(jiraLoginCookie == null) {
  println 'JIRA login failed; exiting'
  return
}

def trelloBoardsUrl = "https://trello.com/1/members/me/boards?key=${config.trello.appKey}&token=${config.trello.appToken}&fields=name"
def jsonSlurper = new JsonSlurper()
def boards = jsonSlurper.parse trelloBoardsUrl.toURL()

def boardNames = config.trello.boardNames.toString().split ','

def boardId
def results = [:]
boards.each { boardInfo ->
  if( boardNames.contains(boardInfo.name)) {
    println "=== Processing board ${boardInfo.name}"
    results[boardInfo.name] = processBoard(config.trello.appKey, config.trello.appToken, config.jira.rootUrl, jiraLoginCookie, boardInfo.id)
  }
}

writeOutput(results, config.output)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def doJiraLogin(def rootUrl, def user, def pwd) {
  def http = new HTTPBuilder(rootUrl)
  
  def postBody = [
    os_username: user,
    os_password: pwd
  ]

  def jiraLoginCookie = null

  http.request(Method.POST) {
    uri.path = '/jira/login.jsp'
    requestContentType = ContentType.URLENC
    body = postBody
    contentType = ContentType.TEXT

    response.failure = { resp ->
      println 'request failed'
      println "Response status ${resp.status}"
    }

    response.success = { resp ->
      jiraLoginCookie = resp.headers['Set-Cookie'].value
    }
  }
  
  return jiraLoginCookie
}

def processBoard(def appKey, def appToken, def jiraRootUrl, def jiraLoginCookie, def boardId) {

  def trelloListsUrl = "https://trello.com/1/boards/${boardId}/lists?key=${appKey}&token=${appToken}&fields=name"
  def jsonSlurper = new JsonSlurper()
  def lists = jsonSlurper.parse trelloListsUrl.toURL()
  def resultsForBoard = [:]

  lists.each { listInfo ->
    println "=== Processing list ${listInfo.name}"
	def isTerminalList = (listInfo.name == 'Closed')
    resultsForBoard[listInfo.name] = processList(appKey, appToken, jiraRootUrl, jiraLoginCookie, listInfo.id, isTerminalList)
  }
  
  return resultsForBoard
}

def processList(def appKey, def appToken, def jiraRootUrl, def jiraLoginCookie, def listId, def isTerminalList) {

  def trelloCardsUrl = "https://trello.com/1/lists/${listId}/cards?key=${appKey}&token=${appToken}&fields=name"
  def jsonSlurper = new JsonSlurper()
  def cards = jsonSlurper.parse trelloCardsUrl.toURL()
  def resultsForList = [:]

  def jiraIdPattern = Pattern.compile "([A-Za-z]+-[0-9]+)\\s.*"
  cards.each { cardInfo ->
    def jiraIdMatcher = jiraIdPattern.matcher cardInfo.name
    if(jiraIdMatcher.matches()) {
      def jiraId = jiraIdMatcher[0][1]
      def jiraStatus = processJira(jiraRootUrl, jiraLoginCookie, jiraId)
	  
	  // Special: ignore things that don't need reconciliation
	  if(jiraStatus != 'Closed' || !isTerminalList) {
        resultsForList[jiraId] = jiraStatus
	  }
    }
    else {
	  println "Unable to extract JIRA ID from ${cardInfo.name}, ignoring"
      warnings += "Unable to extract JIRA ID from ${cardInfo.name}, ignoring"
    }
  }
  
  return resultsForList
}

def processJira(def rootUrl, def jiraLoginCookie, def jiraId) {

  // We're using a VERY old version of JIRA, with no RESTful API. Have to parse comments out of a webpage
  def jiraIssueUri = "/jira/browse/${jiraId}"
    
  def http2 = new HTTPBuilder( rootUrl + jiraIssueUri )
  def html = http2.get(['headers' : ['Cookie': jiraLoginCookie]])
    
  // Status is not an easy thing to find. But we can find the label for the adjacent field and navigate
  // to the field we want
  def statusLabelNode = html."**".find { it.text().equals('Status:')}
  String status
  if(statusLabelNode != null) {

    /*
      I know it's easier than this. Can't get more sensible ways working, so this will do
      for proof-of concept.  What I'm doing is navigating a structure like this (ASCII art incoming):
      <tr>
        <td>
          <b> <- found this node
        <td>
        <img>
          [text] <- want this one
    */
    def x = statusLabelNode.parent().parent()
    def iter = x.childNodes()
    iter.next()
    iter.next()
    def y = iter.next()
    def z = y.childNodes()
    z.next()
    def statusText = z.next().text()
    status = statusText.trim()
  }
	
  return status
}

def writeOutput(def results, String outputFilePath) {
  def writer = new StringWriter()
  def xml = new MarkupBuilder(writer) 
  
  println "${warnings.size()} warnings were raised"

  xml.boards() { 
    results.each { boardName, resultsForBoard ->
	  board( name: boardName ) {
		resultsForBoard.each { listName, resultsForList ->
		  list( name: listName ) {
		    resultsForList.each { jiraId, s ->
			  card {
			    jira( jiraId )
			    status( s )
			  }
			}
		  }
		}
	  }
	}
	
	warnings.each { w ->
	  warning( w )
	}
  }
  
  println writer.toString()

  // Now use XSLT to make HTML from that
  def xslt= new File("reconcileTemplate.xsl").getText()
  def transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(xslt)))

  transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(new FileOutputStream(outputFilePath)))
}