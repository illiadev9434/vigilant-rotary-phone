// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.ui.server.registrar;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.difference;
import static google.registry.export.sheet.SyncRegistrarsSheetAction.enqueueRegistrarSheetSync;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.security.JsonResponseHelper.Status.ERROR;
import static google.registry.security.JsonResponseHelper.Status.SUCCESS;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registrar.RegistrarContact.Builder;
import google.registry.model.registrar.RegistrarContact.Type;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.JsonActionRunner;
import google.registry.request.auth.Auth;
import google.registry.request.auth.AuthResult;
import google.registry.security.JsonResponseHelper;
import google.registry.ui.forms.FormException;
import google.registry.ui.forms.FormFieldException;
import google.registry.ui.server.RegistrarFormFields;
import google.registry.util.AppEngineServiceUtils;
import google.registry.util.CollectionUtils;
import google.registry.util.DiffUtils;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;

/**
 * Admin servlet that allows creating or updating a registrar. Deletes are not allowed so as to
 * preserve history.
 */
@Action(
  path = RegistrarSettingsAction.PATH,
  method = Action.Method.POST,
  auth = Auth.AUTH_PUBLIC_LOGGED_IN
)
public class RegistrarSettingsAction implements Runnable, JsonActionRunner.JsonAction {

  public static final String PATH = "/registrar-settings";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String OP_PARAM = "op";
  static final String ARGS_PARAM = "args";
  static final String ID_PARAM = "id";

  @Inject HttpServletRequest request;
  @Inject JsonActionRunner jsonActionRunner;
  @Inject AppEngineServiceUtils appEngineServiceUtils;
  @Inject AuthResult authResult;
  @Inject SendEmailUtils sendEmailUtils;
  @Inject SessionUtils sessionUtils;
  @Inject @Config("registrarChangesNotificationEmailAddresses") ImmutableList<String>
      registrarChangesNotificationEmailAddresses;
  @Inject RegistrarSettingsAction() {}

  private static final Predicate<RegistrarContact> HAS_PHONE =
      contact -> contact.getPhoneNumber() != null;

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> input) {
    if (input == null) {
      throw new BadRequestException("Malformed JSON");
    }

    Registrar initialRegistrar = sessionUtils.getRegistrarForAuthResult(request, authResult);
    // Check that the clientId requested is the same as the one we get in the
    // getRegistrarForAuthResult.
    // TODO(b/113925293): remove this check, and instead use the requested clientId to select the
    // registrar (in a secure way making sure authResult has access to that registrar!)
    String clientId = (String) input.get(ID_PARAM);
    if (Strings.isNullOrEmpty(clientId)) {
      throw new BadRequestException(String.format("Missing key for resource ID: %s", ID_PARAM));
    }
    if (!clientId.equals(initialRegistrar.getClientId())) {
      throw new BadRequestException(
          String.format(
              "User's clientId changed from %s to %s. Please reload page",
              clientId, initialRegistrar.getClientId()));
    }
    // Process the operation.  Though originally derived from a CRUD
    // handler, registrar-settings really only supports read and update.
    String op = Optional.ofNullable((String) input.get(OP_PARAM)).orElse("read");
    @SuppressWarnings("unchecked")
    Map<String, ?> args = (Map<String, Object>)
        Optional.<Object>ofNullable(input.get(ARGS_PARAM)).orElse(ImmutableMap.of());
    logger.atInfo().log(
        "Received request '%s' on registrar '%s' with args %s",
        op, initialRegistrar.getClientId(), args);
    try {
      switch (op) {
        case "update":
          return update(args, initialRegistrar.getClientId());
        case "read":
          return JsonResponseHelper.create(SUCCESS, "Success", initialRegistrar.toJsonMap());
        default:
          return JsonResponseHelper.create(ERROR, "Unknown or unsupported operation: " + op);
      }
    } catch (FormFieldException e) {
      logger.atWarning().withCause(e).log(
          "Failed to perform operation '%s' on registrar '%s' for args %s",
          op, initialRegistrar.getClientId(), args);
      return JsonResponseHelper.createFormFieldError(e.getMessage(), e.getFieldName());
    } catch (FormException e) {
      logger.atWarning().withCause(e).log(
          "Failed to perform operation '%s' on registrar '%s' for args %s",
          op, initialRegistrar.getClientId(), args);
      return JsonResponseHelper.create(ERROR, e.getMessage());
    }
  }

  Map<String, Object> update(final Map<String, ?> args, String clientId) {
    return ofy()
        .transact(
            () -> {
              // We load the registrar here rather than use the initialRegistrar above - to make
              // sure we have the latest version. This one is loaded inside the transaction, so it's
              // guaranteed to not change before we update it.
              Registrar registrar = Registrar.loadByClientId(clientId).get();
              // Verify that the registrar hasn't been changed.
              // To do that - we find the latest update time (or null if the registrar has been
              // deleted) and compare to the update time from the args. The update time in the args
              // comes from the read that gave the UI the data - if it's out of date, then the UI
              // had out of date data.
              DateTime latest = registrar.getLastUpdateTime();
              DateTime latestFromArgs =
                  RegistrarFormFields.LAST_UPDATE_TIME.extractUntyped(args).get();
              if (!latestFromArgs.equals(latest)) {
                logger.atWarning().log(
                    "registrar changed since reading the data! "
                        + " Last updated at %s, but args data last updated at %s",
                    latest, latestFromArgs);
                return JsonResponseHelper.create(
                    ERROR, "registrar has been changed by someone else. Please reload and retry.");
              }

              // Keep the current contacts so we can later check that no required contact was
              // removed, email the changes to the contacts
              ImmutableSet<RegistrarContact> contacts = registrar.getContacts();

              // Update the registrar from the request.
              Registrar.Builder builder = registrar.asBuilder();
              changeRegistrarFields(registrar, builder, args);

              // read the contacts from the request.
              ImmutableSet<RegistrarContact> updatedContacts = readContacts(registrar, args);
              if (!updatedContacts.isEmpty()) {
                builder.setContactsRequireSyncing(true);
              }

              // Save the updated registrar
              Registrar updatedRegistrar = builder.build();
              if (!updatedRegistrar.equals(registrar)) {
                ofy().save().entity(updatedRegistrar);
              }

              // Save the updated contacts
              if (!updatedContacts.isEmpty()) {
                checkContactRequirements(contacts, updatedContacts);
                RegistrarContact.updateContacts(updatedRegistrar, updatedContacts);
              }

              // Email and return update.
              sendExternalUpdatesIfNecessary(
                  registrar, contacts, updatedRegistrar, updatedContacts);
              return JsonResponseHelper.create(
                  SUCCESS, "Saved " + clientId, updatedRegistrar.toJsonMap());
            });
  }

  private Map<String, Object> expandRegistrarWithContacts(Iterable<RegistrarContact> contacts,
                                                          Registrar registrar) {
    ImmutableSet<Map<String, Object>> expandedContacts =
        Streams.stream(contacts)
            .map(RegistrarContact::toDiffableFieldMap)
            .collect(toImmutableSet());
    // Use LinkedHashMap here to preserve ordering; null values mean we can't use ImmutableMap.
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.putAll(registrar.toDiffableFieldMap());
    result.put("contacts", expandedContacts);
    return result;
  }

  /**
   * Updates a registrar builder with the supplied args from the http request;
   */
  public static void changeRegistrarFields(
      Registrar existingRegistrarObj, Registrar.Builder builder, Map<String, ?> args) {

    // BILLING
    RegistrarFormFields.PREMIUM_PRICE_ACK_REQUIRED
        .extractUntyped(args)
        .ifPresent(builder::setPremiumPriceAckRequired);

    // WHOIS
    builder.setWhoisServer(
        RegistrarFormFields.WHOIS_SERVER_FIELD.extractUntyped(args).orElse(null));
    builder.setUrl(RegistrarFormFields.URL_FIELD.extractUntyped(args).orElse(null));

    // If the email is already null / empty - we can keep it so. But if it's set - it's required to
    // remain set.
    (Strings.isNullOrEmpty(existingRegistrarObj.getEmailAddress())
            ? RegistrarFormFields.EMAIL_ADDRESS_FIELD_OPTIONAL
            : RegistrarFormFields.EMAIL_ADDRESS_FIELD_REQUIRED)
        .extractUntyped(args)
        .ifPresent(builder::setEmailAddress);
    builder.setPhoneNumber(
        RegistrarFormFields.PHONE_NUMBER_FIELD.extractUntyped(args).orElse(null));
    builder.setFaxNumber(
        RegistrarFormFields.FAX_NUMBER_FIELD.extractUntyped(args).orElse(null));
    builder.setLocalizedAddress(
        RegistrarFormFields.L10N_ADDRESS_FIELD.extractUntyped(args).orElse(null));

    // Security
    builder.setIpAddressWhitelist(
        RegistrarFormFields.IP_ADDRESS_WHITELIST_FIELD
            .extractUntyped(args)
            .orElse(ImmutableList.of()));
    RegistrarFormFields.CLIENT_CERTIFICATE_FIELD
        .extractUntyped(args)
        .ifPresent(
            certificate -> builder.setClientCertificate(certificate, ofy().getTransactionTime()));
    RegistrarFormFields.FAILOVER_CLIENT_CERTIFICATE_FIELD
        .extractUntyped(args)
        .ifPresent(
            certificate ->
                builder.setFailoverClientCertificate(certificate, ofy().getTransactionTime()));
  }

  /** Reads the contacts from the supplied args. */
  public static ImmutableSet<RegistrarContact> readContacts(
      Registrar registrar, Map<String, ?> args) {

    ImmutableSet.Builder<RegistrarContact> contacts = new ImmutableSet.Builder<>();
    Optional<List<Builder>> builders = RegistrarFormFields.CONTACTS_FIELD.extractUntyped(args);
    if (builders.isPresent()) {
      builders.get().forEach(c -> contacts.add(c.setParent(registrar).build()));
    }

    return contacts.build();
  }

  /**
   * Enforces business logic checks on registrar contacts.
   *
   * @throws FormException if the checks fail.
   */
  void checkContactRequirements(
      Set<RegistrarContact> existingContacts, Set<RegistrarContact> updatedContacts) {
    // Check that no two contacts use the same email address.
    Set<String> emails = new HashSet<>();
    for (RegistrarContact contact : updatedContacts) {
      if (!emails.add(contact.getEmailAddress())) {
        throw new ContactRequirementException(String.format(
            "One email address (%s) cannot be used for multiple contacts",
            contact.getEmailAddress()));
      }
    }
    // Check that required contacts don't go away, once they are set.
    Multimap<RegistrarContact.Type, RegistrarContact> oldContactsByType = HashMultimap.create();
    for (RegistrarContact contact : existingContacts) {
      for (RegistrarContact.Type t : contact.getTypes()) {
        oldContactsByType.put(t, contact);
      }
    }
    Multimap<RegistrarContact.Type, RegistrarContact> newContactsByType = HashMultimap.create();
    for (RegistrarContact contact : updatedContacts) {
      for (RegistrarContact.Type t : contact.getTypes()) {
        newContactsByType.put(t, contact);
      }
    }
    for (RegistrarContact.Type t
        : difference(oldContactsByType.keySet(), newContactsByType.keySet())) {
      if (t.isRequired()) {
        throw new ContactRequirementException(t);
      }
    }
    ensurePhoneNumberNotRemovedForContactTypes(oldContactsByType, newContactsByType, Type.TECH);
    Optional<RegistrarContact> domainWhoisAbuseContact =
        getDomainWhoisVisibleAbuseContact(updatedContacts);
    // If the new set has a domain WHOIS abuse contact, it must have a phone number.
    if (domainWhoisAbuseContact.isPresent()
        && domainWhoisAbuseContact.get().getPhoneNumber() == null) {
      throw new ContactRequirementException(
          "The abuse contact visible in domain WHOIS query must have a phone number");
    }
    // If there was a domain WHOIS abuse contact in the old set, the new set must have one.
    if (getDomainWhoisVisibleAbuseContact(existingContacts).isPresent()
        && !domainWhoisAbuseContact.isPresent()) {
      throw new ContactRequirementException(
          "An abuse contact visible in domain WHOIS query must be designated");
    }
  }

  /**
   * Ensure that for each given registrar type, a phone number is present after update, if there was
   * one before.
   */
  private static void ensurePhoneNumberNotRemovedForContactTypes(
      Multimap<RegistrarContact.Type, RegistrarContact> oldContactsByType,
      Multimap<RegistrarContact.Type, RegistrarContact> newContactsByType,
      RegistrarContact.Type... types) {
    for (RegistrarContact.Type type : types) {
      if (oldContactsByType.get(type).stream().anyMatch(HAS_PHONE)
          && newContactsByType.get(type).stream().noneMatch(HAS_PHONE)) {
        throw new ContactRequirementException(
            String.format(
                "Please provide a phone number for at least one %s contact",
                type.getDisplayName()));
      }
    }
  }

  /**
   * Retrieves the registrar contact whose phone number and email address is visible in domain WHOIS
   * query as abuse contact (if any).
   *
   * <p>Frontend processing ensures that only one contact can be set as abuse contact in domain
   * WHOIS record. Therefore it is possible to return inside the loop once one such contact is
   * found.
   */
  private static Optional<RegistrarContact> getDomainWhoisVisibleAbuseContact(
      Set<RegistrarContact> contacts) {
    return contacts.stream().filter(RegistrarContact::getVisibleInDomainWhoisAsAbuse).findFirst();
  }

  /**
   * Determines if any changes were made to the registrar besides the lastUpdateTime, and if so,
   * sends an email with a diff of the changes to the configured notification email address and
   * enqueues a task to re-sync the registrar sheet.
   */
  private void sendExternalUpdatesIfNecessary(
      Registrar existingRegistrar,
      ImmutableSet<RegistrarContact> existingContacts,
      Registrar updatedRegistrar,
      ImmutableSet<RegistrarContact> updatedContacts) {
    if (registrarChangesNotificationEmailAddresses.isEmpty()) {
      return;
    }

    Map<?, ?> diffs =
        DiffUtils.deepDiff(
            expandRegistrarWithContacts(existingContacts, existingRegistrar),
            expandRegistrarWithContacts(updatedContacts, updatedRegistrar),
            true);
    @SuppressWarnings("unchecked")
    Set<String> changedKeys = (Set<String>) diffs.keySet();
    if (CollectionUtils.difference(changedKeys, "lastUpdateTime").isEmpty()) {
      return;
    }
    enqueueRegistrarSheetSync(appEngineServiceUtils.getCurrentVersionHostname("backend"));
    if (!registrarChangesNotificationEmailAddresses.isEmpty()) {
      sendEmailUtils.sendEmail(
          registrarChangesNotificationEmailAddresses,
          String.format("Registrar %s updated", existingRegistrar.getRegistrarName()),
          "The following changes were made to the registrar:\n"
              + DiffUtils.prettyPrintDiffedMap(diffs, null));
    }
  }

  /** Thrown when a set of contacts doesn't meet certain constraints. */
  private static class ContactRequirementException extends FormException {
    ContactRequirementException(String msg) {
      super(msg);
    }

    ContactRequirementException(RegistrarContact.Type type) {
      super("Must have at least one " + type.getDisplayName() + " contact");
    }
  }
}
