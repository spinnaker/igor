#!/usr/bin/env groovy

/* this script retrieves a value from an pipeline stage based using the Spinnaker API. Change SPINNAKER_URL to your url */

import groovy.json.JsonSlurper

String SPINNAKER_URL = "localhost:8084"

def cli = new CliBuilder(usage:'pipelineContextValue PIPELINEID STAGETYPE STAGENAME CONTEXTVALUE')
def options = cli.parse(args)

def args = options.arguments()

def pipeline = args[0]
def stageType = args[1]
def stageName = args[2]
def contextValue = args[3]

def retries = 5

String result = null

while( retries > 0 && result == null){
  try{
    result = new JsonSlurper().parseText( new URL("http://${SPINNAKER_URL}/pipelines/${pipeline}").text ).stages.find{ it.type == stageType && it.name == stageName }.context[contextValue]
    println result
  } catch( e ){
    e.printStackTrace()  
  }
  retries--
}

if( result.contains('.0') ){
    result = result.replace('.0', '')
}

new File('value.properties').newWriter().withWriter { w ->
  w << "${contextValue}=${result}"
}