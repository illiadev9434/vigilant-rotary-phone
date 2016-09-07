// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.flows.domain;

import google.registry.flows.EppException;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.fee.BaseFee;
import google.registry.model.eppinput.EppInput;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Interface for classes which provide extra registry logic for things like TLD-specific rules and
 * discounts.
 */
public interface RegistryExtraFlowLogic {

  /** Gets the flags to be used in the EPP flags extension. This is used for EPP info commands. */
  public List<String> getExtensionFlags(
      DomainResource domainResource, String clientIdentifier, DateTime asOfDate);

  /** Computes the expected creation fee, for use in fee challenges and the like. */  
  public BaseFee getCreateFeeOrCredit(
      String domainName,
      String clientIdentifier,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a create command. Any changes should not be persisted to
   * Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainCreateLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a delete command. Any changes should not be persisted to
   * Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainDeleteLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /** Computes the expected renewal fee, for use in fee challenges and the like. */  
  public BaseFee getRenewFeeOrCredit(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a renew command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainRenewLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a restore command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainRestoreLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a transfer command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainTransferLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /** Computes the expected update fee, for use in fee challenges and the like. */  
  public BaseFee getUpdateFeeOrCredit(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for an update command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  public void performAdditionalDomainUpdateLogic(
      DomainResource domain,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /** Commits any changes made as a result of a call to one of the performXXX methods. */
  public void commitAdditionalLogicChanges();
}
