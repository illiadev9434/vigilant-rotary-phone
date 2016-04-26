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

package google.registry.flows.async;

import static google.registry.model.ofy.ObjectifyService.ofy;

import google.registry.dns.DnsQueue;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.ReferenceUnion;
import google.registry.model.host.HostResource;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.reporting.HistoryEntry.Type;
import google.registry.request.Action;

import org.joda.time.DateTime;

import javax.inject.Inject;

/**
 * A mapreduce to delete the specified HostResource, but ONLY if it is not referred to by any
 * existing DomainBase entity.
 */
@Action(path = "/_dr/task/deleteHostResource")
public class DeleteHostResourceAction extends DeleteEppResourceAction<HostResource> {

  @Inject
  public DeleteHostResourceAction() {
    super(
        new DeleteHostResourceMapper(),
        new DeleteHostResourceReducer());
  }

  /** An async deletion mapper for {@link HostResource}. */
  public static class DeleteHostResourceMapper extends DeleteEppResourceMapper<HostResource> {

    private static final long serialVersionUID = 1941092742903217194L;

    @Override
    protected boolean isLinked(
        DomainBase domain, ReferenceUnion<HostResource> targetResourceRef) {
      return domain.getNameservers().contains(targetResourceRef);
    }
  }

  /** An async deletion reducer for {@link HostResource}. */
  public static class DeleteHostResourceReducer extends DeleteEppResourceReducer<HostResource> {

    private static final long serialVersionUID = 555457935288867324L;

    @Override
    protected Type getHistoryType(boolean successfulDelete) {
      return successfulDelete
          ? HistoryEntry.Type.HOST_DELETE
          : HistoryEntry.Type.HOST_DELETE_FAILURE;
    }

    @Override
    protected void performDeleteTasks(
        HostResource targetResource,
        HostResource deletedResource,
        DateTime deletionTime,
        HistoryEntry historyEntryForDelete) {
      if (targetResource.getSuperordinateDomain() != null) {
        DnsQueue.create().addHostRefreshTask(targetResource.getFullyQualifiedHostName());
        ofy().save().entity(
            targetResource.getSuperordinateDomain().get().asBuilder()
                .removeSubordinateHost(targetResource.getFullyQualifiedHostName())
                .build());
      }
    }
  }
}
