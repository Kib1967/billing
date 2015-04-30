import groovy.json.JsonSlurper
import groovy.xml.*
import groovyx.net.http.*
import java.util.regex.Pattern
import javax.xml.transform.*
import javax.xml.transform.stream.*

class TaskInfo {
  String jiraId
  String jiraStatus
  String sdId
  String taskId
  double totalHours
}

def taskInfos = []

def warnings = []

def config = new ConfigSlurper().parse(new File(args[0]).toURL())

def jsonSlurper = new JsonSlurper()

def trelloBoardsUrl = "https://trello.com/1/members/me/boards?key=${config.trello.appKey}&token=${config.trello.appToken}&fields=name"
def boards = jsonSlurper.parse trelloBoardsUrl.toURL()

def boardId
boards.each { boardInfo ->
  if( boardInfo.name.equals(config.trello.boardName)) {
    boardId = boardInfo.id
  }
}

if( boardId == null ) {
  return
}

def trelloListsUrl = "https://trello.com/1/boards/${boardId}/lists?key=${config.trello.appKey}&token=${config.trello.appToken}&fields=name"
def lists = jsonSlurper.parse trelloListsUrl.toURL()

def listId
lists.each { listInfo ->
  if( listInfo.name.equals(config.trello.completeListName)) {
    listId = listInfo.id
  }
}

if( listId == null ) {
  return
}

def trelloCardsUrl = "https://trello.com/1/lists/${listId}/cards?key=${config.trello.appKey}&token=${config.trello.appToken}&fields=name"
def cards = jsonSlurper.parse trelloCardsUrl.toURL()

def jiraIdPattern = Pattern.compile "(${config.trello.jiraProjectPrefixRegEx}-[0-9]+)\\s.*"
cards.each { cardInfo ->
  def jiraIdMatcher = jiraIdPattern.matcher cardInfo.name
  if(jiraIdMatcher.matches()) {
    def jiraId = jiraIdMatcher[0][1]
	def taskInfo = new TaskInfo()
	taskInfo.jiraId = jiraId
	taskInfos += taskInfo
  }
}

// We're using a VERY old version of JIRA, with no RESTful API. Have to parse comments out of a webpage
// First we have to authenticate with JIRA

def http = new HTTPBuilder(config.jira.rootUrl)

def postBody = [
  os_username: config.jira.user,
  os_password: config.jira.pwd
]

def success = false
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
    success = true
  }
}

if(!success) {
  return
}

taskInfos.each { taskInfo ->

  def jiraIssueUri = "/jira/browse/${taskInfo.jiraId}"
  
  def http2 = new HTTPBuilder( config.jira.rootUrl + jiraIssueUri )
  def html = http2.get(['headers' : ['Cookie': jiraLoginCookie]])
  
  // Comments are easy to find because the element has a specific class
  html."**".findAll { it.@class.toString().equals("action-body")}.each { commentNode ->
    def commentText = commentNode.text().trim()
	def matcher = commentText =~ /@task\s([0-9]+).*/
	if(matcher.matches()) {
	  taskInfo.taskId = matcher[0][1]
	}
  }
  
  // Service Desk Number is a little more messy, but there is a predictable element id
  // for the table row that contains the cell we want
  def rowNode = html."**".find {it.@id.toString().equals("rowForcustomfield_10391")}
  if(rowNode != null) {
    // There must be a better way...
    def iter = rowNode.childNodes()
	iter.next()
    def sdIdText = iter.next().text()
    def sdIdMatcher = sdIdText =~ /\s*([0-9]+)\s*/
    if(sdIdMatcher.matches()) {
      taskInfo.sdId = sdIdMatcher[0][1]
    }
  }
  
  // Status is even worse. But we can find the label for the adjacent field and navigate
  // to the field we want
  def statusLabelNode = html."**".find { it.text().equals("Status:")}
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
    taskInfo.jiraStatus = statusText.trim()
  }
  
  if(taskInfo.taskId == null ) {
    warnings += "Can't find @task in JIRA comments for ${taskInfo.jiraId}"
  }
  else if(taskInfo.jiraStatus != 'Closed') {
    warnings += "${taskInfo.jiraId} is marked complete in Trello, but has JIRA status '${taskInfo.jiraStatus}'"
  }
}

// TODO JIRA logout

def attaskLoginUrl = "${config.attask.rootUrl}/attask/api/login?username=${config.attask.user}&password=${config.attask.pwd}"
def response = jsonSlurper.parse attaskLoginUrl.toURL()
def attaskSessionID = response.data.sessionID

taskInfos.each { taskInfo ->

  if(taskInfo.taskId != null) {
    def attaskGetTaskIDUrl = "${config.attask.rootUrl}/attask/api/task/search?referenceNumber=${taskInfo.taskId}&fields=hours:hours&sessionID=${attaskSessionID}"
	
    def getTaskIDResponse = jsonSlurper.parse attaskGetTaskIDUrl.toURL()
	
	// It looks succinct, but it really only demonstrates that I REALLY don't understand JSON
	taskInfo.totalHours = getTaskIDResponse.data.hours.hours[0].sum(0)
  }
}


def attaskLogoutUrl = "${config.attask.rootUrl}/attask/api/logout?sessionID=${attaskSessionID}"
//def dummy = attaskLogoutUrl.toURL().text()

//println dummy

def writer = new StringWriter()
def xml = new MarkupBuilder(writer) 

xml.billableItems() { 
  taskInfos.findAll { it.taskId != null }.each { taskInfo ->
    billableItem {
	  serviceDesk( taskInfo.sdId )
	  jira( taskInfo.jiraId )
	  attask( taskInfo.taskId )
	  hours( taskInfo.totalHours )
	}
  }
  
  warnings.each { w ->
    warning( w )
  }
}

// Now use XSLT to make HTML from that
def xslt= new File("template.xsl").getText()
def transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(xslt)))

// Without strong typing here Groovy can't decide which FileOutputStream constructor to call
String output = config.output
transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(new FileOutputStream(output)))
