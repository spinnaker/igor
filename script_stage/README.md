# Spinnaker Script Stage Configuration.

Right now, the Spinnaker script stage consists of a jenkins script that calls a remote git repository. This directory helps you set up the script stage in Spinnaker.

## Script Repository

You will need to set up a git repository to keep track of your files. This directory contains a sample directory of scripts. 

The groovy directory contains an example of how to interact with Spinnaker pipelines to retrieve a property from a stage.

## Jenkins Job Import

The directory contains a jenkins job definition that you can import via:

`curl -X POST 'http://NEW_JENKINS/createItem?name=JOBNAME' --header "Content-Type: application/xml" --data-binary @scriptJobConfig.xml`

Once you have imported the script, modify the job to point to the script repository.

## Orca Configuration

To enable the script stage, you need to set the following properties in orca's orca.yml

```
script:
   master: [igor alias for your jenkins master]
   job: SPINNAKER-TASKS
```

