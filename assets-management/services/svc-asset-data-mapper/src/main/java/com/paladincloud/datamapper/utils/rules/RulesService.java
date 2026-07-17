package com.paladincloud.datamapper.utils.rules;

import com.amazonaws.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

// This is a singleton class that loads the rules from the rules file into a
// hashmap keyed by the rule name and value the rule itself
public class RulesService {
    private static RulesService instance = null;
    private Map<String, Object> rulesMap = null;
    private String ruleId;

    private RulesService() {

    }

    public static RulesService getInstance() {
        if (instance == null) {
            instance = new RulesService();
        }

        return instance;
    }

    // Load the rules from the s3 bucket ONLY once per lifetime of the lambda
    // This is done by the singleton pattern
    public void loadRules(S3Object rawRulesFile, String ruleId) throws IOException {
        if (rulesMap != null) {
            return;
        }

        rulesMap = new HashMap<>();

        // Load the rules from the s3 bucket
        // And for each item in the rules file, add it to the rulesMap
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(rawRulesFile.getObjectContent()))) {
            ObjectMapper objectMapper = new ObjectMapper();
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode inputNode;
                try {
                    inputNode = objectMapper.readTree(line);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing raw rules JSON", e);
                }

                if (inputNode.get(ruleId) != null) {
                    rulesMap.put(inputNode.get(ruleId).asText(), objectMapper.convertValue(inputNode, Object.class));
                }
            }
        }
    }

    public Object getRule(String ruleId) {
        return rulesMap.get(ruleId);
    }
}
