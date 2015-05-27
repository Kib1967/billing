<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:template match="/billableItems">
    <HTML>
      <HEAD>
        <TITLE></TITLE>
        <link href="bootstrap.min.css" rel="stylesheet"></link>
      </HEAD>
      <BODY>
      
        <div class="container">
        
          <div class="page-header">
            <h1>Billable items</h1>
          </div>
		  
		  <h3>The following items are Complete in Trello and Closed in JIRA, but not ComPLete in @Task:</h3>
          
          <table class="table">
          
            <thead>
              <th>Service desk</th>
              <th>JIRA</th>
              <th>Total hours</th>
              <th>@Task</th>
              <th>@Task status</th>
            </thead>
            
            <tbody>
              <xsl:for-each select="billableItem" >
                <tr>
                  <td><xsl:value-of select="serviceDesk" /></td>
                  <td><xsl:value-of select="jira" /></td>
                  <td><xsl:value-of select="hours" /></td>
                  <td><xsl:value-of select="atTask" /></td>
                  <td><xsl:value-of select="atTaskStatus" /></td>
                </tr>
              </xsl:for-each>
            </tbody>
            
          </table>
		  
          <div class="alert alert-warning" role="alert">
		    <div>
              <strong>The following warnings were raised:</strong>
			</div>
		    <div class="bs-component">
			  <div class="well">
                &quot;Can't find @task&quot; entries may be benign, but should be checked. If necessary, add a comment to the JIRA
			    issue with the text &quot;@task <em>number</em>&quot; and regenerate this report.<br/>
			    &quot;COMP-xxxx is marked complete in Trello, but has JIRA status...&quot; can be resolved by fixing the JIRA status
			    (if appropriate) and regenerating this report.<br/>
			    &quot;COMP-xxxx is assigned to generic @Task&quot; is an abomination that turns up from time-to-time. The JIRA comment
			    should be changed to reference the correct @task code.<br/><br/>
				
                <strong>Every item in this list is a potential missed billing opportunity. All should be followed up.</strong>
              </div>
			</div>
			<div>
              <xsl:for-each select="warning" >
			    <xsl:value-of select="." /><br/>
              </xsl:for-each>
			</div>
          </div>
          
        </div>
      </BODY>
    </HTML>
  </xsl:template>
</xsl:stylesheet>