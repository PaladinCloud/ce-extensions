#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { ServiceStack } from '../lib/service-stack';
import * as fs from 'fs';
import * as yaml from 'js-yaml';

// Load multiple YAML configuration files
const yamlFiles = ['config/config.yaml', 'config/config3.yaml'];

// Create a new CDK App
const app = new cdk.App();

// Loop through YAML files and create a stack for each one
yamlFiles.forEach((yamlFile, index) => {
  const config = yaml.load(fs.readFileSync(yamlFile, 'utf8')) as any[];

  // Iterate over each service defined in the YAML file
  config.forEach(serviceConfig => {
    const stackId = `${serviceConfig.serviceName}-stack`;
    
    // Create a separate stack for each service
    new ServiceStack(app, stackId, serviceConfig, {
      stackName: `${serviceConfig.serviceName}-stack`,
      env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
    });
  });
});
