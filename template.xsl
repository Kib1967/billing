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
          
          <div class="alert alert-warning" role="alert">
		    <div>
              <strong>The following warnings were raised:</strong>
			</div>
			<div>
              <xsl:for-each select="warning" >
			    <xsl:value-of select="." /><br/>
              </xsl:for-each>
			</div>
          </div>
          
          <table class="table">
          
            <thead>
              <th>Service desk</th>
              <th>JIRA</th>
              <th>Total hours</th>
            </thead>
            
            <tbody>
              <xsl:for-each select="billableItem" >
                <tr>
                  <td><xsl:value-of select="serviceDesk" /></td>
                  <td><xsl:value-of select="jira" /></td>
                  <td><xsl:value-of select="hours" /></td>
                </tr>
              </xsl:for-each>
            </tbody>
            
          </table>
        </div>
      </BODY>
    </HTML>
  </xsl:template>
</xsl:stylesheet>