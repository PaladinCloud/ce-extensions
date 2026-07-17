# Overview
This is the Data Mapper lambda, which is responsible for mapping raw files 
from plugins into a more general structure.

SQS Events trigger this lambda, the event contains a reference to the S3-based
source folder and some other housekeeping data. Each file in that folder is 
loaded and mapped.

The event has a `source` type that determines which mapper template will be 
used. These templates are in kept in the `resources.mapping-legacy` folder
in this project and are copied to a `mappings-legacy` when deployed.

Completed mappings are emitted to the S3 destination (bucket & folder). This
will be something like `mapper-results` with each `source` in separate folders, 
and each run in a separate time-stamped folder.

Once mapping is completed, this lambda emits an SQS event with the `source` 
and folder of the mapped files.

# Running locally
To improve feedback while developing the mapper template file, use the
AWS `sam` command line app (see [sam cli](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-getting-started.html)).

Once you have `sam` running, validated with `sam --version`, it's time to 
setup your environment.

## Setup
### S3 folders & files
Decide which account, bucket and folder you are going to use. I used the 
dev account and an existing bucket. I created a folder, `kvt`, 
to contain all my files during testing - the mapping template, raw 
(incoming) data and mapped (outgoing) data.

`kvt/mappings-legacy/xxx` will contain `vulnerabilities.json`, the 
template file I will be checking in. I upload the file after changes with:
```@shell
aws s3 cp src/main/resources/mappings-legacy/xxx/vulnerabilities.json s3://<bucket>/kvt/mappings-legacy/xxx/
```

`kvt/xxx/raw/` will contain the incoming data file. I uploaded the file 
manually as well (it's only done once, most likely).
```@shell
aws s3 cp <..../some/local/file.json> s3://<bucket>/kvt/xxx/raw/
```

`kvt/xxx/mapped` will receive the final mapped file (the result of the 
lambda).

### The environment file
Because we're running locally, we need to provide some info the deployer
provides. These are environment variables that, with a little help in the
lambda template file, become accessible to the locally run lambda.

These are the the buckets and folders you've decided on. `DEV_MODE` instructs the
lambda to NOT send an SQS event at the end (which I don't have setup anyway,
so it'll always fail).
```@json
{
    "Parameters": {
        "MAPPER_BUCKET_NAME": "<bucket>",
        "MAPPER_FOLDER_NAME": "kvt/mappings-legacy",
        "DESTINATION_BUCKET": "<bucket>",
        "DESTINATION_FOLDER": "kvt/xxx/mapped",
        "DEV_MODE": "yup"
    }
}
```

If you need more environment variables setup, note they also need to be 
referenced in `local-template.yaml`, which is what `sam` uses.

### The event file
The lambda is invoked for SQS events, and we need to send an event with `sam`.
I copied this from some AWS documentation - the only interesting part here is
`body`. Also, note there's a single record; this could have multiple records 
as well.

```json
{
    "Records": [
        {
            "messageId": "059f36b4-87a3-44ab-83d2-661975830a7d",
            "receiptHandle": "AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a...",
            "body": "{\"bucketName\":\"<bucket>\",\"path\":\"kvt/xxx/raw\",\"source\":\"xxx\",\"accounts\":[\"<accountId>\"],\"paladinCloudTenantId\":\"kevin-test\",\"scanTime\":\"2024-04-29T20:40:10.333Z\"}",
            "attributes": {
                "ApproximateReceiveCount": "1",
                "SentTimestamp": "1545082649183",
                "SenderId": "AIDAIENQZJOLO23YVJ4VO",
                "ApproximateFirstReceiveTimestamp": "1545082649185"
            },
            "messageAttributes": {},
            "md5OfBody": "e4e68fb7bd0e697a0ae8f1bb342846b3",
            "eventSource": "aws:sqs",
            "eventSourceARN": "arn:aws:sqs:us-east-2:123456789012:my-queue",
            "awsRegion": "us-east-2"
        }
    ]
}
```

The `body` field is a string from the events perspective, but needs to be JSON
for the lambda. The only gross part is it needs to be stringified. I used a
VSCode tool to do it; I bet it could be done in a browser console as well.

Here's the easier to read version of the `body`:
```json
{
    "bucketName": "<bucket>",
    "path": "kvt/xxx/raw",
    "source": "xxx",
    "accounts": [
        "<accountId>"
    ],
    "paladinCloudTenantId": "kevin-test",
    "scanTime": "2024-04-29T20:40:10.333Z"
}
```

## Running the lambda
You need a current build of the lambda. This means any code changes need to
be rebuilt before running. Run maven:
```shell
mvn package
```

And run `sam` with (update locations to your environment and event files):
```shell
sam local invoke -t local-template.yaml --env-vars ../../../mapper-env.json  -e ../../../local-mapper-sqs-event.json
```

FYI: When the lambda exits, a status message including **Billed Duration: 2969 ms** 
will be printed. We are NOT being charged for this as it's running locally. 

## Development loop
Now your development loop is editing either code or the mapper template,
either building the code (`mvn ...`) or uploading the mapper template 
(`s3 cp ...`) and running `sam` as above.

It's easy to forget to build or upload, so keep that in mind when your change
doesn't actually change anything.
