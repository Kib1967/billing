<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:template match="/boards">
    <HTML>
      <HEAD>
        <TITLE></TITLE>
        <link href="bootstrap.min.css" rel="stylesheet"></link>
      </HEAD>
      <BODY>
      
        <div class="container">
        
          <div class="page-header">
            <h1>Trello/JIRA reconciliation</h1>
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
          
          <xsl:for-each select="board" >
		    <h2><xsl:value-of select="@name" /></h2>
		  
            <table class="table">
          
              <thead>
                <th>JIRA</th>
                <th>Status</th>
                <th>Trello list</th>
              </thead>
            
              <tbody>
                <xsl:for-each select="list/card" >
                  <tr>
                    <td><xsl:value-of select="./jira" /></td>
                    <td><xsl:value-of select="./status" /></td>
                    <td><xsl:value-of select="../@name" /></td>
                  </tr>
                </xsl:for-each>
              </tbody>
            
            </table>
		  </xsl:for-each>
        </div>
      </BODY>
    </HTML>
  </xsl:template>
</xsl:stylesheet>