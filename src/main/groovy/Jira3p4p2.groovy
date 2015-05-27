import groovyx.net.http.*

class Jira3p4p2 implements Jira {

  String loginCookie
  String rootUrl

  Jira3p4p2(String rootUrl, String user, String pwd) {
    loginCookie = doLogin(rootUrl, user, pwd)
	
	this.rootUrl = rootUrl + ((rootUrl.endsWith('/')) ? '' : '/')
  }
  
  def doLogin(def rootUrl, def user, def pwd) {
    def http = new HTTPBuilder(rootUrl)
  
    def postBody = [
      os_username: user,
      os_password: pwd
    ]

    def jiraLoginCookie = null
	def success = true
	def responseStatus

    http.request(Method.POST) {
      uri.path = '/jira/login.jsp'
      requestContentType = ContentType.URLENC
      body = postBody
      contentType = ContentType.TEXT

      response.failure = { resp ->
	    success = false
	    responseStatus = resp.status
      }

      response.success = { resp ->
        jiraLoginCookie = resp.headers['Set-Cookie'].value
      }
    }
	
	if(!success) {
	  throw new RuntimeException("JIRA connection failed with status ${responseStatus}")
	}
  
    return jiraLoginCookie
  }
  
  Map<String, String> getProperties(String issueId) {
  
    def properties = [:]

    // We're using a VERY old version of JIRA, with no RESTful API. Have to screenscrape the information
	// from the webpages
    def issueUri = "/jira/browse/${issueId}"
    
    def success = true
	def responseStatus
	
    def http = new HTTPBuilder( rootUrl + issueUri )
    http.handler.failure = { resp ->
      success = false
      responseStatus = resp.status
    }
    def html = http.get(
      ['headers' : ['Cookie': loginCookie]]
    )
  
    if(success) {
	
	  // This is a terrible way to achieve what I'm trying to do here - after all, this is supposed to be a 'core library'.
	  // Picking out very specific JIRA fields isn't the way to go.	
      // These properties are not easy things to find. These expressions represent the result of looking at JIRA pages
	  // with the Chrome developer tools. That's my life, right there.
	  // It should give you enough of a clue to find others, anyway.
      properties['status'] = html.BODY.TABLE[2].TBODY.TR.TD.TABLE[1].TBODY.TR[2].TD[1].text().trim()
      properties['assignee'] = html.BODY.TABLE[2].TBODY.TR.TD.TABLE[1].TBODY.TR[5].TD[1].text().trim()
      properties['title'] = html.BODY.TABLE[2].TBODY.TR.TD[1].TABLE.TBODY.TR.TD.TABLE.TBODY.TR.TD.TABLE.TBODY.TR.TD.TABLE.TBODY.TR.TD.H3.text().trim()
	  
	  // rowForcustomfield_10391 is the DOM id for the Service Desk number. Might not be present
      def sdIdRow = html.'**'.find { it.@id == 'rowForcustomfield_10391' }
	  if( sdIdRow != null ) {
        properties['sdId'] = sdIdRow.TD[1].text().trim()
      }
	  
	  def atTaskPattern = /\s*@[Tt]ask ([0-9]+)\s*/
	  // action-body is the class for comments
      html.'**'.findAll { it.@class == 'action-body' }.each { node ->
	    def m = node.text() =~ atTaskPattern
		if(m.matches()) {
		  properties['atTaskId'] = m.group(1)
		  return true
		}
	  }
    }
	else {
	  if(responseStatus == 404) {
	    throw new Jira.NoSuchIssueException()
	  }
	  else {
	    throw new RuntimeException("JIRA connection failed with status ${responseStatus}")
      }
	}
	
    return properties
  }

}