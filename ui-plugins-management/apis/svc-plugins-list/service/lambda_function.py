#  Copyright (c) 2024 Paladin Cloud, Inc. or its affiliates. All Rights Reserved.
#
#  Licensed under the Apache License, Version 2.0 (the "License"); you may not
#  use this file except in compliance with the License. You may obtain a copy
#  of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
#  License for the specific language governing permissions and limitations under
#  the License.

import http.client
import json
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

EXTENSION_HOST = 'localhost'
EXTENSION_HOST_PORT = 4567


def lambda_handler(event, context):
    logger.info("Received event: %s", json.dumps(event, indent=2))

    try:
        tenant_id = event['requestContext']['authorizer']['lambda']['tenantId']
        logger.info(f"Extracted Tenant ID: {tenant_id}")

        # Construct the path
        path = f"/plugins/{tenant_id}"

        # Create a connection object
        connection = http.client.HTTPConnection(EXTENSION_HOST, EXTENSION_HOST_PORT)

        # Make the GET request
        connection.request("GET", path)

        # Get the response
        response = connection.getresponse()

        # Read and decode the response data
        data = response.read().decode()

        # Check if the response is in JSON format
        try:
            # Attempt to parse the JSON response
            result = json.loads(data)
        except json.JSONDecodeError:
            # Handle cases where the response is not JSON
            logger.error(f"Invalid JSON response: {data}")
            result = data

        # Close the connection
        connection.close()

        # Return the parsed result
        return result

    except Exception as e:
        # Handle any exceptions (e.g., network issues, invalid response, etc.)
        print(f"Error while making GET request: {e}")
        return None
