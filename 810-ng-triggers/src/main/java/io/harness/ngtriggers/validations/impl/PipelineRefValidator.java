/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.validations.TriggerValidator;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class PipelineRefValidator implements TriggerValidator {
  private final BuildTriggerHelper validationHelper;
  private static final String PIPELINE = "pipeline";
  private static final String TRIGGER = "trigger";
  private static final String INPUT_YAML = "inputYaml";

  @Override
  public ValidationResult validate(TriggerDetails triggerDetails) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    Optional<String> pipelineYmlOptional = validationHelper.fetchPipelineForTrigger(ngTriggerEntity);

    if (!pipelineYmlOptional.isPresent()) {
      String ref = new StringBuilder(128)
                       .append("Pipeline with Ref -> ")
                       .append(ngTriggerEntity.getAccountId())
                       .append(':')
                       .append(ngTriggerEntity.getOrgIdentifier())
                       .append(':')
                       .append(ngTriggerEntity.getProjectIdentifier())
                       .append(':')
                       .append(ngTriggerEntity.getTargetIdentifier())
                       .append(" does not exists")
                       .toString();
      builder.success(false).message(ref);
    } else {
      String pipelineYaml = pipelineYmlOptional.get();
      String templateYaml = createRuntimeInputForm(pipelineYaml);
      String triggerYaml = triggerDetails.getNgTriggerEntity().getYaml();
      String triggerPipelineYml = getPipelineComponent(triggerYaml);
      Map<FQN, String> invalidFQNs = getInvalidFQNsInTrigger(templateYaml, triggerPipelineYml);
      if (EmptyPredicate.isEmpty(invalidFQNs)) {
        builder.build();
      }
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("Invalid fields");
      for (Map.Entry<FQN, String> entry : invalidFQNs.entrySet()) {
        stringBuilder.append("\n");
        stringBuilder.append(entry.getKey().display());
        stringBuilder.append(": ");
        stringBuilder.append(entry.getValue());
      }
      builder.success(false).message(stringBuilder.toString());
    }

    return builder.build();
  }

  private String getPipelineComponent(String triggerYml) {
    try {
      if (EmptyPredicate.isEmpty(triggerYml)) {
        return triggerYml;
      }
      JsonNode node = YamlUtils.readTree(triggerYml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get(TRIGGER);
      if (innerMap == null) {
        throw new InvalidRequestException("Yaml provided is not an trigger yaml.");
      }
      JsonNode pipelineNode = innerMap.get(INPUT_YAML).get(PIPELINE);
      innerMap.removeAll();
      innerMap.putObject(PIPELINE);
      innerMap.set(PIPELINE, pipelineNode);
      return YamlUtils.write(innerMap).replace("---\n", "");
    } catch (IOException e) {
      throw new InvalidYamlException("Trigger yaml is invalid", e);
    }
  }

  public Map<FQN, String> getInvalidFQNsInTrigger(String templateYaml, String triggerPipelineCompYaml) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    YamlConfig triggerConfig = new YamlConfig(triggerPipelineCompYaml);
    Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
    if (EmptyPredicate.isEmpty(templateYaml)) {
      triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains any runtime input"));
      return errorMap;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);

    // Make sure everything in trigger exist in pipeline
    templateConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (triggerFQNs.contains(key)) {
        Object templateValue = templateConfig.getFqnToValueMap().get(key);
        Object value = triggerConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            errorMap.put(key,
                "The value for " + key.getExpressionFqn() + " is " + templateValue.toString()
                    + "in the pipeline yaml, but the trigger has it as " + value.toString());
          }
        } else {
          String error = validateStaticValues(templateValue, value);
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        triggerFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(triggerConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(triggerFQNs::remove);
      }
    });
    triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Field either not present in pipeline or not a runtime input"));

    // Make sure everything in pipeline exist in trigger
    Set<FQN> pipelineFQNs = new LinkedHashSet<>(templateConfig.getFqnToValueMap().keySet());
    if (EmptyPredicate.isEmpty(triggerPipelineCompYaml)) {
      pipelineFQNs.forEach(fqn -> errorMap.put(fqn, "Trigger does not contain any runtime input"));
      return errorMap;
    }

    triggerConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (pipelineFQNs.contains(key)) {
        pipelineFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(templateConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(pipelineFQNs::remove);
      }
    });
    pipelineFQNs.forEach(
        fqn -> errorMap.put(fqn, "Field present in pipeline as run time input but not set in trigger"));
    return errorMap;
  }

  public String createRuntimeInputForm(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (NGExpressionUtils.matchesInputSetPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }
}
