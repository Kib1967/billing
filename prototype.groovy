import groovy.json.JsonSlurper
import java.util.regex.Pattern

class TaskInfo {
}

def taskInfos = [:]

def config = new ConfigSlurper().parse(new File("billing.properties").toURL())

def jsonSlurper = new JsonSlurper()

def trelloBoardsUrl = "https://trello.com/1/members/me/boards?key=${config.trello.appKey}&token=${config.trello.appToken}&fields=name"
def boards = jsonSlurper.parse trelloBoardsUrl.toURL()

def boardId
boards.each { boardInfo ->
  if( boardInfo.name.equals('MBIE All')) {
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
  if( listInfo.name.equals('Complete')) {
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
    taskInfos[jiraId] = new Object()
  }
}

println taskInfos