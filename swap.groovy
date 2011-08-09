import com.eviware.soapui.support.types.StringToStringsMap 
import com.eviware.soapui.impl.rest.RestRequestInterface.RequestMethod

def username = 'user'
def password = 'secret'

def clientId    = 999
def targetIndex = 0 // Index on name [0, 1, 2]

def baseUri   = "https://routing.dial800.com"

def request     = testRunner.testCase.testSteps["Request Step"].getTestRequest()
def credentials = new StringToStringsMap()
credentials.put("username",username)
credentials.put("access-key",password)
request.setRequestHeaders(credentials)

def String createPayload(groovy.util.slurpersupport.GPathResult root){ 
        return new groovy.xml.StreamingMarkupBuilder().bind{ 
            out << root 
        } 
} 

def resource = { m, r -> 
     request.setMethod(m)
    	request.setEndpoint(baseUri + r)
    	def response = testRunner.runTestStepByName("Request Step")
    	return new XmlSlurper().parseText(response.responseContent)
}

// New targets filename
def TARGETS_FILE_NAME = "path/to/csv"; //dnis, new target

//load and split the file
def rows    = new File(TARGETS_FILE_NAME).readLines()
	   						    .collect {it.split(',')}

log.info " Requesting collection of DNIS "
def dnisCollection = resource(RequestMethod.GET,
						"/api/client($clientId)/dnis").DNIS

rows.each { row -> 
  dnisCollection.each { dnis ->
    if (dnis.Number == row[0].toLong()) {
    	log.info " Fetching profile for DNIS ${dnis.Number}"
	profile = resource(RequestMethod.GET,
	                   "/api/client($clientId)/profile(${dnis.ProfileID})")
	if (profile.Rules.Rule.any { it.ResultType == "DistributionGroup" }) {
	  groupId = profile.Rules
	     	   	 .Rule
	    			 .find { it.ResultType == "DistributionGroup" }
	    			 .Target
	  log.info " Fetching group for profile ${profile.Name}"
	  group = resource(RequestMethod.GET,
	                   "/api/client($clientId)/group($groupId)")
	  oldTarget = group.Name.toString().substring(1 + 12 * targetIndex,11 + 12 * targetIndex)
	  newTarget = row[1]
	  log.info " ${dnis.Number} => ${group.Name} => ${group.Name.toString().replace(oldTarget,newTarget)}"
	  group.Name = group.Name.toString().replace(oldTarget,newTarget)
	  target = group.Targets
	                .Target
	                .find { it.RingTo == oldTarget }
	  target.RingTo = newTarget
	  group.IsQueueHoldTimeEnabled.replaceNode { }
	  group.Targets.Target.each {t -> t.QueueHoldTimeMax.replaceNode{ }}
	  request.setMethod(RequestMethod.PUT)
	  request.setRequestContent(createPayload(group))
	  resource(RequestMethod.PUT,
	           "/api/client($clientId)/group($groupId)")
     }
   }
 }
}

result = 0