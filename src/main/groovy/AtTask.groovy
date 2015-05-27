import groovy.json.JsonSlurper

class AtTask {

  String rootUrl
  String sessionId
  
  AtTask(def rootUrl, def user, def pwd) {
  
    this.rootUrl = rootUrl
  
    def attaskLoginUrl = "${rootUrl}/attask/api/login?username=${user}&password=${pwd}"
    def jsonSlurper = new JsonSlurper()
    def response = jsonSlurper.parse attaskLoginUrl.toURL()
    sessionId = response.data.sessionID
  }
  
  Map<String, String> getPropertiesForTask(def taskId) {
  
    def properties = [:]

	// Need to augment list of fields here if you want to get more properties
	// See https://developers.attask.com/api-docs/#Fields
    def getTaskIdUrl = "${rootUrl}/attask/api/task/search?referenceNumber=${taskId}&fields=hours:hours,status&sessionID=${sessionId}"
    def jsonSlurper = new JsonSlurper()
    def getTaskIdResponse = jsonSlurper.parse getTaskIdUrl.toURL()
	
	// It looks succinct, but it really only demonstrates that I REALLY don't understand JSON
	def taskData = getTaskIdResponse.data
	def h = taskData.hours.hours[0]
	if(h != null) {
	  properties.totalHours = h.sum(0)
    }
	
	properties.status = taskData.status
	
	return properties
  }
}