/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.globalkms.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.NISHANT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.context.GlobalContext;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.globalkms.dto.ConnectorSecretResponseDTO;
import io.harness.ng.core.globalkms.services.NgConnectorManagerClientService;
import io.harness.request.RequestContext;
import io.harness.request.RequestContextData;
import io.harness.rule.Owner;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

@RunWith(PowerMockRunner.class)
@PrepareForTest(GlobalKmsServiceImpl.class)
public class GlobalKmsServiceImplTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private SecretCrudService ngSecretService;
  @Mock private NgConnectorManagerClientService ngConnectorManagerClientService;
  private GlobalKmsServiceImpl globalKmsService;
  @Captor ArgumentCaptor<String> accountIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> orgIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> projectIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> identifierArgumentCaptor;
  @Captor ArgumentCaptor<ConnectorDTO> connectorDTOArgumentCaptor;
  @Captor ArgumentCaptor<SecretDTOV2> secretDTOV2ArgumentCaptor;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  String accountId = UUIDGenerator.generateUuid();
  String userId = UUIDGenerator.generateUuid();
  String secretIdentifier = HARNESS_SECRET_MANAGER_IDENTIFIER + "_" + randomAlphabetic(10);
  SecretRefData secretRefData;
  GcpKmsConnectorDTO globalGcpKmsConnectorDto;
  ConnectorResponseDTO globalKmsConnector;
  UserPrincipal userPrincipal;
  GcpKmsConnectorDTO connectorConfig;
  ConnectorInfoDTO connectorInfoDTO;
  ConnectorDTO connectorDTO;
  SecretDTOV2 secretDTOV2;
  SecretResponseWrapper secretResponseWrapper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    globalKmsService =
        PowerMockito.spy(new GlobalKmsServiceImpl(connectorService, ngSecretService, ngConnectorManagerClientService));
    secretRefData = SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ACCOUNT).build();
    globalGcpKmsConnectorDto = GcpKmsConnectorDTO.builder().isDefault(true).credentials(secretRefData).build();
    globalGcpKmsConnectorDto.setHarnessManaged(true);
    globalKmsConnector = ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .connectorConfig(globalGcpKmsConnectorDto)
                                            .connectorType(ConnectorType.GCP_KMS)
                                            .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                            .build())
                             .build();
    userPrincipal = new UserPrincipal(userId, randomAlphabetic(10), userId, accountId);
    connectorConfig = GcpKmsConnectorDTO.builder().credentials(secretRefData).build();
    connectorInfoDTO = ConnectorInfoDTO.builder()
                           .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                           .connectorType(ConnectorType.GCP_KMS)
                           .connectorConfig(connectorConfig)
                           .build();
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
    secretDTOV2 = SecretDTOV2.builder().identifier(secretIdentifier).build();
    secretResponseWrapper =
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().identifier(secretIdentifier).build()).build();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetGlobalKmsConnector() {
    globalKmsService.getGlobalKmsConnector();
    verify(connectorService, times(1)).getGlobalKmsConnector();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKms() throws Exception {
    when(globalKmsService.getGlobalKmsConnector()).thenReturn(Optional.of(globalKmsConnector));
    PowerMockito.doReturn(userPrincipal).when(globalKmsService, "getUserPrincipalOrThrow");
    when(ngConnectorManagerClientService.isHarnessSupportUser(userId)).thenReturn(true);
    when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getProjectIdentifier(), secretDTOV2.getOrgIdentifier(),
             secretDTOV2.getIdentifier()))
        .thenReturn(Optional.of(secretResponseWrapper));
    when(ngSecretService.update(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
             secretDTOV2.getIdentifier(), secretDTOV2))
        .thenReturn(SecretResponseWrapper.builder().build());
    when(connectorService.update(connectorDTO, GLOBAL_ACCOUNT_ID)).thenReturn(ConnectorResponseDTO.builder().build());
    ConnectorSecretResponseDTO response = globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
    verifyPrivate(globalKmsService, times(1)).invoke("canUpdateGlobalKms", connectorDTO, secretDTOV2);
    verify(ngSecretService, times(1))
        .update(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
            secretDTOV2.getIdentifier(), secretDTOV2);
    verify(connectorService, times(1)).update(connectorDTO, GLOBAL_ACCOUNT_ID);
    verify(ngSecretService)
        .update(accountIdentifierArgumentCaptor.capture(), orgIdentifierArgumentCaptor.capture(),
            projectIdentifierArgumentCaptor.capture(), identifierArgumentCaptor.capture(),
            secretDTOV2ArgumentCaptor.capture());
    assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
    assertEquals(secretDTOV2.getOrgIdentifier(), orgIdentifierArgumentCaptor.getValue());
    assertEquals(secretDTOV2.getProjectIdentifier(), projectIdentifierArgumentCaptor.getValue());
    assertEquals(secretDTOV2, secretDTOV2ArgumentCaptor.getValue());
    verify(connectorService).update(connectorDTOArgumentCaptor.capture(), accountIdentifierArgumentCaptor.capture());
    assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
    assertEquals(connectorDTO, connectorDTOArgumentCaptor.getValue());
    ConnectorDTO connectorUpdateDto = connectorDTOArgumentCaptor.getValue();
    GcpKmsConnectorDTO connectorUpdateConfigDto =
        (GcpKmsConnectorDTO) connectorUpdateDto.getConnectorInfo().getConnectorConfig();
    assertEquals(globalGcpKmsConnectorDto.isDefault(), connectorUpdateConfigDto.isDefault());
    assertEquals(globalGcpKmsConnectorDto.isHarnessManaged(), connectorUpdateConfigDto.isHarnessManaged());
    assertEquals(globalGcpKmsConnectorDto.getDelegateSelectors(), connectorUpdateConfigDto.getDelegateSelectors());
    assertEquals(
        globalKmsConnector.getConnector().getOrgIdentifier(), connectorUpdateDto.getConnectorInfo().getOrgIdentifier());
    assertEquals(globalKmsConnector.getConnector().getProjectIdentifier(),
        connectorUpdateDto.getConnectorInfo().getProjectIdentifier());
    assertNotNull(response.getConnectorResponseDTO());
    assertNotNull(response.getSecretResponseWrapper());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKms_connector_not_present() throws Exception {
    PowerMockito.doNothing().when(globalKmsService, "canUpdateGlobalKms", connectorDTO, secretDTOV2);
    PowerMockito.doReturn(Optional.ofNullable(null)).when(globalKmsService, "getGlobalKmsConnector");
    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(String.format("Global connector of type %s not found", ConnectorType.GCP_KMS));
    globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
    verifyPrivate(globalKmsService, times(1)).invoke("getGlobalKmsConnector");
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCanUpdateGlobalKms() throws Exception {
    PowerMockito.doReturn(userPrincipal).when(globalKmsService, "getUserPrincipalOrThrow");
    PowerMockito.doNothing().when(globalKmsService, "checkForHarnessSupportUser", userPrincipal.getName());
    PowerMockito.doNothing().when(globalKmsService, "checkConnectorTypeAndCredentialsMatch", connectorDTO, secretDTOV2);
    PowerMockito.doNothing().when(globalKmsService, "checkConnectorHasOnlyAccountScopeInfo", connectorDTO);
    PowerMockito.doNothing().when(globalKmsService, "checkGlobalKmsSecretExists", secretDTOV2);
    Whitebox.invokeMethod(globalKmsService, "canUpdateGlobalKms", connectorDTO, secretDTOV2);
    verifyPrivate(globalKmsService, times(1)).invoke("checkForHarnessSupportUser", userPrincipal.getName());
    verifyPrivate(globalKmsService, times(1))
        .invoke("checkConnectorTypeAndCredentialsMatch", connectorDTO, secretDTOV2);
    verifyPrivate(globalKmsService, times(1)).invoke("checkConnectorHasOnlyAccountScopeInfo", connectorDTO);
    verifyPrivate(globalKmsService, times(1)).invoke("checkGlobalKmsSecretExists", secretDTOV2);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(GlobalContextManager.class)
  public void testGetUserPrincipalOrThrow() throws Exception {
    GlobalContext globalContext = new GlobalContext();
    globalContext.upsertGlobalContextRecord(PrincipalContextData.builder().principal(userPrincipal).build());
    PowerMockito.mockStatic(GlobalContextManager.class);
    when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
    Object userPrincipalResponse = Whitebox.invokeMethod(globalKmsService, "getUserPrincipalOrThrow");
    PowerMockito.verifyStatic(GlobalContextManager.class, times(1));
    assertNotNull(userPrincipalResponse);
    assertThat(userPrincipalResponse).isInstanceOf(UserPrincipal.class);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(GlobalContextManager.class)
  public void testGetUserPrincipalOrThrow_exception_global_context_null() throws Exception {
    PowerMockito.mockStatic(GlobalContextManager.class);
    when(GlobalContextManager.obtainGlobalContext()).thenReturn(null);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Not authorized to update in current context");
    Whitebox.invokeMethod(globalKmsService, "getUserPrincipalOrThrow");
    PowerMockito.verifyStatic(GlobalContextManager.class, times(1));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(GlobalContextManager.class)
  public void testGetUserPrincipalOrThrow_exception_not_principal_context() throws Exception {
    GlobalContext globalContext = new GlobalContext();
    globalContext.upsertGlobalContextRecord(
        RequestContextData.builder().requestContext(RequestContext.builder().build()).build());
    PowerMockito.mockStatic(GlobalContextManager.class);
    when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Not authorized to update in current context");
    Whitebox.invokeMethod(globalKmsService, "getUserPrincipalOrThrow");
    PowerMockito.verifyStatic(GlobalContextManager.class, times(1));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  @PrepareForTest(GlobalContextManager.class)
  public void testGetUserPrincipalOrThrow_not_user_principal() throws Exception {
    GlobalContext globalContext = new GlobalContext();
    globalContext.upsertGlobalContextRecord(PrincipalContextData.builder().principal(new ServicePrincipal()).build());
    PowerMockito.mockStatic(GlobalContextManager.class);
    when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Not authorized to update in current context");
    Whitebox.invokeMethod(globalKmsService, "getUserPrincipalOrThrow");
    PowerMockito.verifyStatic(GlobalContextManager.class, times(1));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckForHarnessSupportUser() throws Exception {
    when(ngConnectorManagerClientService.isHarnessSupportUser(any())).thenReturn(false);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("User is not authorized");
    Whitebox.invokeMethod(globalKmsService, "checkForHarnessSupportUser", UUIDGenerator.generateUuid());
    verify(ngConnectorManagerClientService, times(1)).isHarnessSupportUser(any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_not_harness_secret_manager() throws Exception {
    ConnectorDTO nonHarnessConnector =
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().identifier(UUIDGenerator.generateUuid()).build())
            .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Update operation not supported");
    Whitebox.invokeMethod(globalKmsService, "checkConnectorTypeAndCredentialsMatch", nonHarnessConnector, secretDTOV2);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_not_gcp_kms() throws Exception {
    ConnectorDTO nonHarnessConnector = ConnectorDTO.builder()
                                           .connectorInfo(ConnectorInfoDTO.builder()
                                                              .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                                              .connectorType(ConnectorType.LOCAL)
                                                              .build())
                                           .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Update operation not supported");
    Whitebox.invokeMethod(globalKmsService, "checkConnectorTypeAndCredentialsMatch", nonHarnessConnector, secretDTOV2);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_credential_secret_identifier_mismatch()
      throws Exception {
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Secret credential reference cannot be changed");
    SecretDTOV2 secretDTO = SecretDTOV2.builder().identifier(UUIDGenerator.generateUuid()).build();
    Whitebox.invokeMethod(globalKmsService, "checkConnectorTypeAndCredentialsMatch", connectorDTO, secretDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_credential_scope_not_global() throws Exception {
    ConnectorDTO connectorDTO =
        ConnectorDTO.builder()
            .connectorInfo(
                ConnectorInfoDTO.builder()
                    .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                    .connectorType(ConnectorType.GCP_KMS)
                    .connectorConfig(
                        GcpKmsConnectorDTO.builder()
                            .credentials(SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ORG).build())
                            .build())
                    .build())
            .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Invalid credential scope");
    Whitebox.invokeMethod(globalKmsService, "checkConnectorTypeAndCredentialsMatch", connectorDTO, secretDTOV2);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorHasOnlyAccountScopeInfo_has_org() throws Exception {
    ConnectorDTO connectorDTO =
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().orgIdentifier(UUIDGenerator.generateUuid()).build())
            .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Global connector cannot have org/project identifier");
    Whitebox.invokeMethod(globalKmsService, "checkConnectorHasOnlyAccountScopeInfo", connectorDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckGlobalKmsSecretExists() throws Exception {
    when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
             secretDTOV2.getIdentifier()))
        .thenReturn(Optional.of(secretResponseWrapper));
    Whitebox.invokeMethod(globalKmsService, "checkGlobalKmsSecretExists", secretDTOV2);
    verify(ngSecretService, times(1))
        .get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
            secretDTOV2.getIdentifier());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckGlobalKmsSecretExists_secret_not_exist() throws Exception {
    when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
             secretDTOV2.getIdentifier()))
        .thenReturn(Optional.ofNullable(null));
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(
        String.format("Secret with identifier %s does not exist in global scope", secretDTOV2.getIdentifier()));
    Whitebox.invokeMethod(globalKmsService, "checkGlobalKmsSecretExists", secretDTOV2);
    verify(ngSecretService, times(1))
        .get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
            secretDTOV2.getIdentifier());
  }
}
