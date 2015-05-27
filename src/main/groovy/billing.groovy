import groovy.xml.*
import groovyx.net.http.*
import java.util.regex.Pattern
import javax.xml.transform.*
import javax.xml.transform.stream.*

class TaskInfo {
  String jiraId
  String jiraTitle
  String jiraStatus
  String sdId
  String taskId
  String totalHours
  String atTaskStatus
  
  String toString() {
    return "TaskInfo[JIRA ${jiraId} (SD ${sdId}) with task ${taskId} has ${totalHours} hours]"
  }
}

def taskInfos = []
def warnings = [:]

// Grouping warnings like this so they're ordered nicely in the output
warnings.cantFindTaskId = []
warnings.wrongStatus = []
warnings.genericCodeUsed = []

def config = new ConfigSlurper().parse(new File(args[0]).toURL())

def jiraIdPattern = Pattern.compile "(${config.trello.jiraProjectPrefixRegEx}-[0-9]+)\\s.*"

def trelloConn = new Trello( config.trello.appKey, config.trello.appToken )
def jiraConn = new Jira3p4p2(config.jira.rootUrl, config.jira.user, config.jira.pwd)
def atTaskConn = new AtTask(config.attask.rootUrl, config.attask.user, config.attask.pwd)

def boardId = trelloConn.getBoardIdFor('MBIE All')
if(boardId==null) {
  println 'Unable to find MBIE All board'
  return
}

def listId = trelloConn.getListIdFor('Complete', boardId)
if(listId==null) {
  println 'Unable to find Complete list'
  return
}

def cards = trelloConn.getCards(listId)
cards.each { cardInfo ->
  def jiraIdMatcher = jiraIdPattern.matcher cardInfo.name
  if(jiraIdMatcher.matches()) {
    def taskInfo = new TaskInfo()
	
    def jiraId = jiraIdMatcher[0][1]
    def jiraProperties = jiraConn.getProperties(jiraId)
	
    taskInfo.jiraId = jiraId
    taskInfo.jiraTitle = jiraProperties.title
    taskInfo.taskId = jiraProperties.atTaskId
    taskInfo.sdId = jiraProperties.sdId
    taskInfo.jiraStatus = jiraProperties.status

    if(taskInfo.jiraStatus == 'Closed') {
	  if(taskInfo.taskId == null) {
        warnings.cantFindTaskId += "Can't find @task in JIRA comments for ${taskInfo.jiraId} ('${taskInfo.jiraTitle}')"
      }
    }
    else {
      warnings.wrongStatus += "${taskInfo.jiraId} is marked complete in Trello, but has JIRA status '${taskInfo.jiraStatus}'"
    }

	// TODO massive hack to exclude inappropriate bookings
    if(taskInfo.taskId.equals('45135')) {
      warnings.genericCodeUsed += "${taskInfo.jiraId} is assigned to generic @Task 45135"
    }
	else {
	  if(taskInfo.taskId != null) {
        def atTaskProperties = atTaskConn.getPropertiesForTask(taskInfo.taskId)
        taskInfo.totalHours = atTaskProperties.totalHours
        // No idea why this ends up as an array?
        taskInfo.atTaskStatus = atTaskProperties.status[0]
      }

      taskInfos += taskInfo
    }
  }
}

def writer = new StringWriter()
def xml = new MarkupBuilder(writer) 

xml.billableItems() { 
  taskInfos.findAll { it.taskId != null && !it.atTaskStatus.equals('CPL')}.each { taskInfo ->

    billableItem {
	  serviceDesk( taskInfo.sdId )
	  jira( taskInfo.jiraId )
	  hours( taskInfo.totalHours )
	  atTask( taskInfo.taskId )
	  atTaskStatus( taskInfo.atTaskStatus )
	}
  }
  
  warnings.cantFindTaskId.each { w ->
    warning( w )
  }
  
  warnings.wrongStatus.each { w ->
    warning( w )
  }
  
  warnings.genericCodeUsed.each { w ->
    warning( w )
  }
}

// Now use XSLT to make HTML from that
def xslt= new File("billingTemplate.xsl").getText()
def transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(xslt)))

// Without strong typing here Groovy can't decide which FileOutputStream constructor to call
String output = config.output
transformer.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(new FileOutputStream(output)))
