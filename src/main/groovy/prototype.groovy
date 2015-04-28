import groovy.json.JsonSlurper
import groovyx.net.http.*
import java.util.regex.Pattern

class TaskInfo {
  String jiraId
  String taskId
}

def taskInfos = []

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
  html."**".findAll { it.@class.toString().equals("action-body")}.each { commentNode ->
    def commentText = commentNode.text().trim()
	def matcher = commentText =~ /@task\s([0-9]+).*/
	if(matcher.matches()) {
	  taskInfo.taskId = matcher[0][1]
	}
  }
}

taskInfos.each { taskInfo ->
  println "${taskInfo.jiraId} -> ${taskInfo.taskId}"
}