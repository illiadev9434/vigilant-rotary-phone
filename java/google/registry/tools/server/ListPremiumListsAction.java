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

package google.registry.tools.server;

import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;

import com.google.common.collect.ImmutableSet;

import google.registry.model.registry.label.PremiumList;
import google.registry.request.Action;

import javax.inject.Inject;

/** An action that lists premium lists, for use by the registry_tool list_premium_lists command. */
@Action(path = ListPremiumListsAction.PATH, method = {GET, POST})
public final class ListPremiumListsAction extends ListObjectsAction<PremiumList> {

  public static final String PATH = "/_dr/admin/list/premiumLists";

  @Inject ListPremiumListsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("name");
  }

  @Override
  public ImmutableSet<PremiumList> loadObjects() {
    return ImmutableSet.copyOf(
        ofy().load().type(PremiumList.class).ancestor(getCrossTldKey()).list());
  }
}
