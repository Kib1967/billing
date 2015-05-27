import groovy.json.JsonSlurper
import groovy.xml.*
import groovyx.net.http.*
import java.util.regex.Pattern
import javax.xml.transform.*
import javax.xml.transform.stream.*

warnings = []

def config = new ConfigSlurper().parse(new File(args[0]).toURL())

// First we have to authenticate with JIRA
jira = new Jira3p4p2(config.jira.rootUrl, config.jira.user, config.jira.pwd)

def trello = new Trello(config.trello.appKey, config.trello.appToken)
def boards = trello.getBoards()

def allowableStatusesByList = [:]
config.trello.lists.each { key, value ->
  allowableStatusesByList[key] = value.allowableStatuses.split ','
}

boards.each { boardInfo ->
  if( config.trello.boardName.toString().equals(boardInfo.name)) {
    println "=== Processing board ${boardInfo.name}"
    def results = processBoard(trello, boardInfo.id, allowableStatusesByList)
	
	def jiraBrowseUrl = "${config.jira.rootUrl}jira/browse/"
	writeOutput(results, config.stylesheet, config.output, jiraBrowseUrl)
  }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def processBoard(def trello, def boardId, def allowableStatusesByList) {

  def lists = trello.getLists(boardId)
  def resultsForBoard = [:]

  lists.each { listInfo ->
    println "=== Processing list ${listInfo.name}"
	def allowableStatuses = allowableStatusesByList[listInfo.name] ?: []
    resultsForBoard[listInfo.name] = processList(trello, listInfo.id, allowableStatuses)
  }
  
  return resultsForBoard
}

def processList(def trello, def listId, def allowableStatuses) {

  def cards = trello.getCards(listId)
  def resultsForList = [:]

  // Try to cope with the many and varied ways that SD write these titles
  def jiraIdPattern = Pattern.compile "([A-Za-z]+-[0-9]+):?\\s.*"
  
  cards.each { cardInfo ->
    def jiraIdMatcher = jiraIdPattern.matcher cardInfo.name
    if(jiraIdMatcher.matches()) {
      def jiraId = jiraIdMatcher[0][1]
	  
	  try {
	    def jiraProperties = jira.getProperties(jiraId)
        def jiraStatus = jiraProperties.status
	  
	    if(jiraStatus != null && !allowableStatuses.contains(jiraStatus)) {
	      resultsForList[jiraId] = jiraStatus
	    }
	  }
	  catch( Jira.NoSuchIssueException e ) {
        println "Unable to find JIRA issue ${jiraId}"
        warnings += "Unable to find JIRA issue ${jiraId}"
	  }
    }
    else {
	  println "Unable to extract JIRA ID from ${cardInfo.name}, ignoring"
      warnings += "Unable to extract JIRA ID from ${cardInfo.name}, ignoring"
    }
  }
  
  return resultsForList
}

def writeOutput(def results, def stylesheetFilePath, def outputFilePath, def jiraBrowseUrl) {
  def writer = new StringWriter()
  def xml = new MarkupBuilder(writer) 
  
  xml.lists() { 
    results.each { listName, resultsForList ->
	  list( name: listName ) {
		resultsForList.each { jiraId, s ->
		  card {
			jira( jiraId )
			jiraURL( "${jiraBrowseUrl}${jiraId}" )
			status( s )
		  }
		}
	  }
	}
	
	warnings.each { w ->
	  warning( w )
	}
  }
  
  // Now use XSLT to make HTML from that
  def xslt= new File(stylesheetFilePath).getText()
  def transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(xslt)))

  transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(new FileOutputStream((String)outputFilePath)))
}