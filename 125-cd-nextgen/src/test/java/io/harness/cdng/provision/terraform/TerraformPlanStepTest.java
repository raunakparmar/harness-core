/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TerraformPlanStep terraformPlanStep;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }
  @Data
  @Builder
  private static class GitStoreConfig {
    private String branch;
    private FetchType fetchType;
    private ParameterField<String> folderPath;
    private ParameterField<String> connectoref;
  }

  @Data
  @Builder
  private static class ArtifactoryStoreConfig {
    private String repositoryPath;
    private String artifactoryName;
    private String version;
    private String connectorRef;
  }

  private TerraformPlanStepParameters GenerateStepPlan(
      StoreConfigType storeType, Object storeConfigFilesParam, Object varStoreConfigFilesParam) {
    StoreConfig storeConfigFiles;
    StoreConfig storeVarFiles;
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    switch (storeType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        // Create the store file for the terraform files
        GitStoreConfig gitStoreConfigFiles = (GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(gitStoreConfigFiles.branch))
                .gitFetchType(gitStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(gitStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        GitStoreConfig gitStoreVarFiles = (GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GithubStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.branch))
                            .gitFetchType(gitStoreVarFiles.fetchType)
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.connectoref.getValue()))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      case ARTIFACTORY:
        // Create the store file for the terraform files
        ArtifactoryStoreConfig artifactoryStoreConfigFiles = (ArtifactoryStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                .repositoryPath(ParameterField.createValueField(artifactoryStoreConfigFiles.repositoryPath))
                .artifactName(ParameterField.createValueField(artifactoryStoreConfigFiles.artifactoryName))
                .version(ParameterField.createValueField(artifactoryStoreConfigFiles.version))
                .connectorRef(ParameterField.createValueField(artifactoryStoreConfigFiles.connectorRef))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        ArtifactoryStoreConfig artifactoryStoreVarFiles = (ArtifactoryStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                            .repositoryPath(ParameterField.createValueField(artifactoryStoreVarFiles.repositoryPath))
                            .artifactName(ParameterField.createValueField(artifactoryStoreVarFiles.artifactoryName))
                            .version(ParameterField.createValueField(artifactoryStoreVarFiles.version))
                            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.connectorRef))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      default:
        break;
    }
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec.setContent(ParameterField.createValueField("var-content"));
    InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
    inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    TerraformBackendConfig terraformBackendConfig = new TerraformBackendConfig();
    terraformBackendConfig.setTerraformBackendConfigSpec(inlineTerraformBackendConfigSpec);
    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerraformVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerraformVarFileSpec).build());
    varFilesMap.put("var-file-02",
        TerraformVarFile.builder().identifier("var-file-02").type("Remote").spec(remoteTerraformVarFileSpec).build());
    return TerraformPlanStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("id"))
        .configuration(TerraformPlanExecutionDataParameters.builder()
                           .configFiles(configFilesWrapper)
                           .command(TerraformPlanCommand.APPLY)
                           .secretManagerRef(ParameterField.createValueField("secret"))
                           .varFiles(varFilesMap)
                           .environmentVariables(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
                           .backendConfig(terraformBackendConfig)
                           .build())
        .build();
  }

  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    GitStoreConfig gitStoreConfigFiles = GitStoreConfig.builder()
                                             .branch("master")
                                             .fetchType(FetchType.BRANCH)
                                             .folderPath(ParameterField.createValueField("Config/"))
                                             .connectoref(ParameterField.createValueField("terraform"))
                                             .build();
    GitStoreConfig gitStoreVarFiles = GitStoreConfig.builder()
                                          .branch("master")
                                          .fetchType(FetchType.BRANCH)
                                          .folderPath(ParameterField.createValueField("VarFiles/"))
                                          .connectoref(ParameterField.createValueField("terraform"))
                                          .build();
    TerraformPlanStepParameters planStepParameters =
        GenerateStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    terraformPlanStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("secret");
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    ArtifactoryStoreConfig artifactoryStoreConfigFiles = ArtifactoryStoreConfig.builder()
                                                             .artifactoryName("artifactoryConfigFiles")
                                                             .connectorRef("connectorRef")
                                                             .repositoryPath("repositoryPath")
                                                             .version("1.0.0")
                                                             .build();
    ArtifactoryStoreConfig artifactoryStoreVarFiles = ArtifactoryStoreConfig.builder()
                                                          .artifactoryName("artifactoryVarFiles")
                                                          .connectorRef("connectorRef2")
                                                          .repositoryPath("repositoryPathtoVars")
                                                          .version("1.0.2")
                                                          .build();
    TerraformPlanStepParameters planStepParameters =
        GenerateStepPlan(StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    terraformPlanStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("connectorRef2");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("secret");
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    GitStoreConfig gitStoreConfigFiles = GitStoreConfig.builder()
                                             .branch("master")
                                             .fetchType(FetchType.BRANCH)
                                             .folderPath(ParameterField.createValueField("Config/"))
                                             .connectoref(ParameterField.createValueField("terraform"))
                                             .build();
    GitStoreConfig gitStoreVarFiles = GitStoreConfig.builder()
                                          .branch("master")
                                          .fetchType(FetchType.BRANCH)
                                          .folderPath(ParameterField.createValueField("VarFiles/"))
                                          .connectoref(ParameterField.createValueField("terraform"))
                                          .build();
    TerraformPlanStepParameters planStepParameters =
        GenerateStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformPlanStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    ArtifactoryStoreConfig artifactoryStoreConfigFiles = ArtifactoryStoreConfig.builder()
                                                             .artifactoryName("artifactoryConfigFiles")
                                                             .connectorRef("connectorRef")
                                                             .repositoryPath("repositoryPath")
                                                             .version("1.0.0")
                                                             .build();
    ArtifactoryStoreConfig artifactoryStoreVarFiles = ArtifactoryStoreConfig.builder()
                                                          .artifactoryName("artifactoryVarFiles")
                                                          .connectorRef("connectorRef2")
                                                          .repositoryPath("repositoryPathtoVars")
                                                          .version("1.0.2")
                                                          .build();
    TerraformPlanStepParameters planStepParameters =
        GenerateStepPlan(StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);

    // Create auth with user and password
    char[] password = {'r', 's', 't', 'u', 'v'};
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO =
        ArtifactoryAuthenticationDTO.builder()
            .authType(ArtifactoryAuthType.USER_PASSWORD)
            .credentials(ArtifactoryUsernamePasswordAuthDTO.builder()
                             .username("username")
                             .passwordRef(SecretRefData.builder().decryptedValue(password).build())
                             .build())
            .build();

    // Create DTO connector
    ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                          .artifactoryServerUrl("http://artifactory.com")
                                                          .auth(artifactoryAuthenticationDTO)
                                                          .delegateSelectors(Collections.singleton("delegateSelector"))
                                                          .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.ARTIFACTORY)
                                            .identifier("connectorRef")
                                            .name("connectorName")
                                            .connectorConfig(artifactoryConnectorDTO)
                                            .build();

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig = ArtifactoryStoreDelegateConfig.builder()
                                                                        .artifactName("artifactName")
                                                                        .repositoryPath("repositoryPath")
                                                                        .version("1.0.0")
                                                                        .connectorDTO(connectorInfoDTO)
                                                                        .succeedIfFileNotFound(false)
                                                                        .build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileFactoryFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformPlanStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformPlanStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformPlanStepParameters planStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("id"))
            .configuration(TerraformPlanExecutionDataParameters.builder().command(TerraformPlanCommand.APPLY).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    StepResponse stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    verify(terraformStepHelper, times(1)).saveTerraformInheritOutput(any(), any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test // Different Status
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextDifferentStatus() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformPlanStepParameters planStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("id"))
            .configuration(TerraformPlanExecutionDataParameters.builder().command(TerraformPlanCommand.APPLY).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponseFailure = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    StepResponse stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseQueued);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    String message =
        String.format("Unhandled type CommandExecutionStatus: " + CommandExecutionStatus.SKIPPED, WingsException.USER);
    try {
      TerraformTaskNGResponse terraformTaskNGResponseSkipped =
          TerraformTaskNGResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SKIPPED)
              .unitProgressData(unitProgressData)
              .build();
      terraformPlanStep.handleTaskResultWithSecurityContext(
          ambiance, stepElementParameters, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }
}
